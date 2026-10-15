import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  computed,
  input,
} from '@angular/core';
import { DailyActivity } from '../core/models';
import { counted } from '@common/shared/formatting';
import { DsoChart } from '@common/ui/chart';

const dayFormat = new Intl.DateTimeFormat('en', {
  day: 'numeric',
  month: 'short',
  timeZone: 'UTC',
});

export function dayLabel(date: string): string {
  return dayFormat.format(new Date(`${date}T00:00:00Z`));
}

export function dayTitle(day: DailyActivity): string {
  return `${dayLabel(day.date)}: ${counted(day.runs, 'run')}, ${counted(day.deployments, 'deployment')}, ${counted(day.failures, 'failed deployment')}`;
}

export function activitySummary(days: readonly DailyActivity[]): string {
  const runs = days.reduce((sum, day) => sum + day.runs, 0);
  return `${counted(runs, 'run')} over ${counted(days.length, 'day')}`;
}

export function activityOptions(days: readonly DailyActivity[]): Record<string, unknown> {
  const titles = days.map(dayTitle);
  return {
    chart: { type: 'column', height: 190, spacing: [12, 8, 8, 4] },
    xAxis: {
      categories: days.map((day) => dayLabel(day.date)),
      tickLength: 0,
      labels: { step: Math.max(1, Math.ceil(days.length / 8)) },
    },
    yAxis: { min: 0, minRange: 2, allowDecimals: false, title: { text: null } },
    tooltip: {
      formatter(this: { point: { index: number } }) {
        return titles[this.point.index];
      },
    },
    plotOptions: {
      series: { animation: false, borderWidth: 0, groupPadding: 0.1, pointPadding: 0.05 },
      column: { stacking: 'normal' },
    },
    series: [
      {
        name: 'Other runs',
        className: 'runs',
        data: days.map((day) => Math.max(0, day.runs - day.failures)),
      },
      {
        name: 'Failed deployments',
        className: 'failure',
        data: days.map((day) => day.failures),
      },
      {
        name: 'Deployed that day',
        type: 'scatter',
        className: 'deployment',
        marker: { symbol: 'square', radius: 3 },
        data: days.map((day) => (day.deployments > 0 ? day.runs : null)),
      },
    ],
  };
}

@Component({
  selector: 'dso-activity-chart',
  imports: [DsoChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  styleUrl: './activity-chart.css',
  host: { class: 'dso-activity-chart' },
  template: `
    <dso-chart [options]="options()" [label]="summary()" />
    <div class="legend">
      <span><i class="swatch runs"></i>Other runs</span>
      <span><i class="swatch failure"></i>Failed deployments</span>
      <span><i class="swatch deployment"></i>Deployed that day</span>
    </div>
  `,
})
export class ActivityChart {
  readonly daily = input.required<DailyActivity[]>();

  protected readonly options = computed(() => activityOptions(this.daily()));
  protected readonly summary = computed(() => activitySummary(this.daily()));
}
