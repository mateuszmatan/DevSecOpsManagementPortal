import { ComponentFixture, TestBed } from '@angular/core/testing';
import { chartOptions } from '@common/testing/highcharts';
import { BarChart, BarRow } from './bar-chart';

describe('BarChart', () => {
  let fixture: ComponentFixture<BarChart>;

  async function render(rows: BarRow[]) {
    fixture = TestBed.createComponent(BarChart);
    fixture.componentRef.setInput('rows', rows);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  }

  it('stacks one series per segment kind with every row as a category and its note opposite', async () => {
    const page = await render([
      {
        label: 'Custody',
        note: '8 pipelines',
        segments: [
          { swatch: 'success', label: 'success', count: 6 },
          { swatch: 'failure', label: 'failed', count: 2 },
        ],
      },
      {
        label: 'AI Lab',
        note: '2 pipelines',
        segments: [
          { swatch: 'success', label: 'success', count: 2 },
          { swatch: 'failure', label: 'failed', count: 0 },
        ],
      },
    ]);

    const chart = page.querySelector('dso-chart');
    const options = chartOptions(chart);
    expect(options.chart.type).toBe('bar');
    expect(options.plotOptions.series.stacking).toBe('normal');
    expect(options.xAxis[0].categories).toEqual(['Custody', 'AI Lab']);
    expect(options.xAxis[1].categories).toEqual(['8 pipelines', '2 pipelines']);
    expect(options.series).toEqual([
      { name: 'success', className: 'success', data: [6, 2] },
      { name: 'failed', className: 'failure', data: [2, 0] },
    ]);
    expect(chart?.getAttribute('role')).toBe('img');
    expect(chart?.getAttribute('aria-label')).toBe(
      'Custody: 6 success, 2 failed; AI Lab: 2 success',
    );
  });

  it('names a row without any count and redraws when the rows change', async () => {
    const page = await render([{ label: 'Fund Services', note: '0 pipelines', segments: [] }]);

    expect(page.querySelector('dso-chart')?.getAttribute('aria-label')).toBe('Fund Services: none');
    expect(chartOptions(page.querySelector('dso-chart')).series).toEqual([]);

    fixture.componentRef.setInput('rows', [
      {
        label: 'Custody',
        note: '1 pipeline',
        segments: [{ swatch: 'active', label: 'active', count: 1 }],
      },
    ]);
    await fixture.whenStable();

    expect(chartOptions(page.querySelector('dso-chart')).xAxis[0].categories).toEqual(['Custody']);
  });
});
