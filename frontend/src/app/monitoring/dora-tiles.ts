import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { DoraSummary } from '../core/models';
import { DoraLevelBadge } from '../shared/dora-level';
import { CountedPipe, counted, formatDuration } from '@common/shared/formatting';

const moment = new Intl.DateTimeFormat('en-GB', {
  day: 'numeric',
  month: 'short',
  hour: '2-digit',
  minute: '2-digit',
  timeZoneName: 'short',
});

@Component({
  selector: 'dso-dora-tiles',
  imports: [CountedPipe, DoraLevelBadge],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'card' },
  template: `
    <header class="card-header">
      <h2>Delivery performance (DORA)</h2>
      <span class="muted">Over the last {{ dora().rangeDays | counted: 'day' }}</span>
    </header>
    <p class="section-help">
      Four industry measures of how often and how safely changes reach production. Each is rated
      Elite, High, Medium or Low; Elite is best.
    </p>
    <div class="tiles">
      @for (tile of tiles(); track tile.title) {
        <div class="tile">
          <div class="tile-head">
            <span class="tile-title">{{ tile.title }}</span>
            <dso-dora-level [level]="tile.level" />
          </div>
          <div class="tile-meaning">{{ tile.meaning }}</div>
          <div class="tile-value">{{ tile.value }}</div>
          <div class="muted tile-detail">{{ tile.detail }}</div>
          @if (tile.alert) {
            <div class="tile-alert">{{ tile.alert }}</div>
          }
        </div>
      }
    </div>
    <ng-content />
  `,
  styles: `
    :host {
      margin-bottom: 10px;
      padding: 10px 14px 12px;
    }
    .tiles {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      border-top: 1px solid var(--dso-border);
    }
    .tile {
      padding: 8px 14px 4px;
      border-left: 1px solid var(--dso-border);
    }
    .tile:first-child {
      padding-left: 0;
      border-left: 0;
    }
    .tile-head {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }
    .tile-title {
      font-size: 12.5px;
      font-weight: 600;
    }
    .tile-meaning {
      color: var(--dso-muted);
      font-size: 11.5px;
    }
    .tile-value {
      margin-top: 4px;
      font-family: var(--dso-serif);
      font-size: 22px;
      font-weight: 500;
      color: var(--dso-navy);
    }
    .tile-detail {
      font-size: 11.5px;
    }
    .tile-alert {
      margin-top: 4px;
      color: var(--dso-danger);
      font-size: 11.5px;
      font-weight: 600;
    }
    @media (max-width: 1100px) {
      .tiles {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
      .tile:nth-child(3) {
        padding-left: 0;
        border-left: 0;
      }
      .tile:nth-child(n + 3) {
        border-top: 1px solid var(--dso-border);
      }
    }
    @media (max-width: 560px) {
      .tiles {
        grid-template-columns: minmax(0, 1fr);
      }
      .tile {
        padding-left: 0;
        border-left: 0;
      }
      .tile:nth-child(n + 2) {
        border-top: 1px solid var(--dso-border);
      }
    }
  `,
})
export class DoraTiles {
  readonly dora = input.required<DoraSummary>();

  protected readonly tiles = computed(() => doraTiles(this.dora()));
}

interface DoraTile {
  title: string;
  meaning: string;
  value: string;
  detail: string;
  level: DoraSummary['deploymentFrequencyLevel'];
  alert?: string;
}

export function doraTiles(dora: DoraSummary): DoraTile[] {
  const deployments = counted(dora.deployments, 'deployment');
  return [
    {
      title: 'Deployment frequency',
      meaning: 'How often a change reaches production',
      value: frequency(dora.deploymentsPerWeek),
      detail: `${deployments} in ${counted(dora.rangeDays, 'day')}`,
      level: dora.deploymentFrequencyLevel,
    },
    {
      title: 'Lead time for changes',
      meaning: 'How long a change takes from commit to production',
      value: formatDuration(dora.leadTimeMedianSeconds),
      detail: 'Typical value (median) in the period',
      level: dora.leadTimeLevel,
    },
    {
      title: 'Change failure rate',
      meaning: 'Share of deployments that failed',
      value:
        dora.changeFailureRatePercent === null
          ? '–'
          : `${dora.changeFailureRatePercent.toFixed(1)}%`,
      detail: `Of ${deployments} in the period`,
      level: dora.changeFailureRateLevel,
    },
    {
      title: 'Time to restore',
      meaning: 'How long it takes to recover after a failed deployment',
      value: formatDuration(dora.meanTimeToRestoreSeconds),
      detail: `Average of ${counted(dora.restores, 'recovery', 'recoveries')}`,
      level: dora.timeToRestoreLevel,
      alert: dora.failingSince
        ? `Not recovered yet: a deployment failed on ${moment.format(new Date(dora.failingSince))} and its pipeline has not deployed successfully since`
        : undefined,
    },
  ];
}

function frequency(perWeek: number | null): string {
  if (perWeek === null) {
    return '–';
  }
  if (perWeek >= 7) {
    return `${(perWeek / 7).toFixed(1)} / day`;
  }
  if (perWeek >= 1) {
    return `${perWeek.toFixed(1)} / week`;
  }
  return `${((perWeek * 30) / 7).toFixed(1)} / month`;
}
