import { doraSummary } from '../testing/fixtures';
import { doraTiles } from './dora-tiles';

describe('doraTiles', () => {
  it('shows the four DORA metrics and that they are measured over deployments', () => {
    const tiles = doraTiles(doraSummary());

    expect(tiles.map((tile) => [tile.title, tile.value, tile.level])).toEqual([
      ['Deployment frequency', '2.8 / week', 'HIGH'],
      ['Lead time for changes', '1h 30m', 'ELITE'],
      ['Change failure rate', '12.5%', 'HIGH'],
      ['Time to restore', '2h', 'HIGH'],
    ]);
    expect(tiles.map((tile) => tile.meaning)).toEqual([
      'How often a change reaches production',
      'How long a change takes from commit to production',
      'Share of deployments that failed',
      'How long it takes to recover after a failed deployment',
    ]);
    expect(tiles[0].detail).toBe('12 deployments in 30 days');
    expect(tiles[1].detail).toBe('Typical value (median) in the period');
    expect(tiles[2].detail).toBe('Of 12 deployments in the period');
    expect(tiles[3].detail).toBe('Average of 3 recoveries');
    expect(tiles[3].alert).toBeUndefined();
  });

  it('switches the frequency unit to days or months', () => {
    expect(doraTiles(doraSummary({ deploymentsPerWeek: 14 }))[0].value).toBe('2.0 / day');
    expect(doraTiles(doraSummary({ deploymentsPerWeek: 0.5 }))[0].value).toBe('2.1 / month');
  });

  it('shows dashes without data and warns while the pipeline is failing', () => {
    const tiles = doraTiles(
      doraSummary({
        runs: 1,
        deployments: 1,
        deploymentsPerWeek: null,
        leadTimeMedianSeconds: null,
        changeFailureRatePercent: null,
        meanTimeToRestoreSeconds: null,
        restores: 1,
        failingSince: '2026-10-04T08:00:00Z',
      }),
    );

    expect(tiles.map((tile) => tile.value)).toEqual(['–', '–', '–', '–']);
    expect(tiles[0].detail).toBe('1 deployment in 30 days');
    expect(tiles[2].detail).toBe('Of 1 deployment in the period');
    expect(tiles[3].detail).toBe('Average of 1 recovery');
    expect(tiles[3].alert).toMatch(
      /^Not recovered yet: a deployment failed on 4 Oct, \d\d:00 \S+ and its pipeline has not deployed successfully since$/,
    );
  });
});
