import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DailyActivity } from '../core/models';
import { chartOptions } from '@common/testing/highcharts';
import { ActivityChart } from './activity-chart';

describe('ActivityChart', () => {
  let fixture: ComponentFixture<ActivityChart>;

  beforeEach(() => {
    fixture = TestBed.createComponent(ActivityChart);
  });

  const chart = () => (fixture.nativeElement as HTMLElement).querySelector('dso-chart')!;
  const options = () => chartOptions(chart());
  const series = (name: string) =>
    options().series.find((entry: { name: string }) => entry.name === name).data;

  async function render(daily: DailyActivity[]) {
    fixture.componentRef.setInput('daily', daily);
    await fixture.whenStable();
  }

  const day = (date: string, runs: number, failures = 0, deployments = 0): DailyActivity => ({
    date,
    runs,
    failures,
    deployments,
  });

  it('stacks the failed deployments on the other runs and marks deployments on top', async () => {
    await render([day('2026-10-01', 3, 1, 1), day('2026-10-02', 0), day('2026-10-03', 4, 0, 2)]);

    expect(chart().getAttribute('aria-label')).toBe('7 runs over 3 days');
    expect(options().chart.type).toBe('column');
    expect(options().plotOptions.column.stacking).toBe('normal');
    expect(options().xAxis.categories).toEqual(['Oct 1', 'Oct 2', 'Oct 3']);
    expect(series('Other runs')).toEqual([2, 0, 4]);
    expect(series('Failed deployments')).toEqual([1, 0, 0]);
    expect(series('Deployed that day')).toEqual([3, null, 4]);
    expect(options().series.map((entry: { className: string }) => entry.className)).toEqual([
      'runs',
      'failure',
      'deployment',
    ]);
    expect(
      [0, 1, 2].map((index) => options().tooltip.formatter.call({ point: { index } })),
    ).toEqual([
      'Oct 1: 3 runs, 1 deployment, 1 failed deployment',
      'Oct 2: 0 runs, 0 deployments, 0 failed deployments',
      'Oct 3: 4 runs, 2 deployments, 0 failed deployments',
    ]);
  });

  it('keeps an axis of at least two whole runs and thins the day labels of long ranges', async () => {
    await render(
      Array.from({ length: 30 }, (_, index) =>
        day(`2026-09-${String(index + 1).padStart(2, '0')}`, 0),
      ),
    );

    expect(options().yAxis).toEqual(
      expect.objectContaining({ min: 0, minRange: 2, allowDecimals: false }),
    );
    expect(options().xAxis.labels.step).toBe(4);
    expect(chart().getAttribute('aria-label')).toBe('0 runs over 30 days');
  });

  it('draws an empty chart with its legend when no day is reported', async () => {
    await render([]);

    expect(chart().getAttribute('aria-label')).toBe('0 runs over 0 days');
    expect(chart().classList).toContain('drawn');
    expect(options().xAxis.categories).toEqual([]);
    expect(
      [...(fixture.nativeElement as HTMLElement).querySelectorAll('.legend span')].map(
        (entry) => entry.textContent,
      ),
    ).toEqual(['Other runs', 'Failed deployments', 'Deployed that day']);
  });
});
