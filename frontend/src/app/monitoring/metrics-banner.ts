import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RETRY } from '@common/core/errors';
import { MonitoringStatus } from '../core/models';

@Component({
  selector: 'dso-metrics-banner',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (status(); as s) {
      @if (!s.influxConfigured) {
        <div class="banner info">
          <span>
            Run results are not shown: the portal is not connected to InfluxDB, where the pipelines
            report their runs, so it only knows whether each pipeline's key is valid.
            <span class="admin"
              >For the administrator: set <code>INFLUX_URL</code> and
              <code>INFLUX_TOKEN</code>.</span
            >
          </span>
        </div>
      } @else if (!s.influxReachable) {
        <div class="banner">
          <span
            >Run results could not be loaded: InfluxDB, where the pipelines report their runs, does
            not answer{{ s.influxError ? ' (' + s.influxError + ')' : '' }}. {{ retry }}</span
          >
        </div>
      }
    }
    @if (metricsError(); as error) {
      <div class="banner">
        <span
          >Run results could not be loaded, so the statuses below may be incomplete ({{ error }}).
          {{ retry }}</span
        >
      </div>
    }
  `,
  styles: `
    .admin {
      color: var(--dso-muted);
    }
  `,
})
export class MetricsBanner {
  readonly status = input<MonitoringStatus | undefined>();
  readonly metricsError = input<string | null>(null);

  protected readonly retry = RETRY;
}
