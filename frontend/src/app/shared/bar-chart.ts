import {
  ChangeDetectionStrategy,
  Component,
  ViewEncapsulation,
  computed,
  input,
} from '@angular/core';
import { DsoChart } from '@common/ui/chart';

export interface BarSegment {
  swatch: string;
  label: string;
  count: number;
}

export interface BarRow {
  label: string;
  note: string;
  segments: BarSegment[];
}

const ROW_HEIGHT = 24;

export function barSummary(row: BarRow): string {
  const counts = row.segments
    .filter((segment) => segment.count > 0)
    .map((segment) => `${segment.count} ${segment.label}`);
  return `${row.label}: ${counts.join(', ') || 'none'}`;
}

export function barOptions(rows: readonly BarRow[]): Record<string, unknown> {
  const kinds = [
    ...new Map(
      rows.flatMap((row) => row.segments).map((segment) => [segment.label, segment.swatch]),
    ),
  ];
  return {
    chart: { type: 'bar', height: rows.length * ROW_HEIGHT + 16, spacing: [4, 4, 4, 4] },
    xAxis: [
      { categories: rows.map((row) => row.label), lineWidth: 0, tickLength: 0 },
      {
        categories: rows.map((row) => row.note),
        linkedTo: 0,
        opposite: true,
        lineWidth: 0,
        tickLength: 0,
      },
    ],
    yAxis: { visible: false, min: 0, allowDecimals: false },
    tooltip: { pointFormat: '{point.y} {series.name}', headerFormat: '' },
    plotOptions: {
      series: {
        stacking: 'normal',
        animation: false,
        borderWidth: 0,
        groupPadding: 0.12,
        pointPadding: 0,
      },
    },
    series: kinds.map(([label, swatch]) => ({
      name: label,
      className: swatch,
      data: rows.map((row) =>
        row.segments
          .filter((segment) => segment.label === label)
          .reduce((sum, segment) => sum + segment.count, 0),
      ),
    })),
  };
}

@Component({
  selector: 'dso-bar-chart',
  imports: [DsoChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
  styleUrl: './bar-chart.css',
  host: { class: 'dso-bar-chart' },
  template: `<dso-chart [options]="options()" [label]="summary()" />`,
})
export class BarChart {
  readonly rows = input.required<BarRow[]>();

  protected readonly options = computed(() => barOptions(this.rows()));
  protected readonly summary = computed(() => this.rows().map(barSummary).join('; '));
}
