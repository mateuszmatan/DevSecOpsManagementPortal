import { ClipboardModule } from '@angular/cdk/clipboard';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, forkJoin, map, of } from 'rxjs';
import { MonitoringApi, PipelinesApi, ProductsApi, SettingsApi } from '../core/api';
import { PIPELINE_TYPES, Pipeline, PipelineType, pipelineTypeLabel } from '../core/models';
import { PIPELINES, adminProduct, monitoringPipeline } from '../core/sections';
import { MetricsBanner } from '../monitoring/metrics-banner';
import { RunGrid } from '../monitoring/run-grid';
import { jenkinsfile, repositoryGroup, sharedJobReason } from '../products/jenkinsfile';
import { bitbucketRepositoryUrl } from '../shared/bitbucket';
import { CountedPipe, TIME_ZONE_NOTE, capitalized, listed } from '@common/shared/formatting';
import { StatusChip } from '../shared/status-chip';
import { LoadError } from '@common/ui/load-error';
import { DsoLoading } from '@common/ui/loading';
import { MENU_AT_END } from '@common/ui/menu';
import { PipelineActions } from './pipeline-actions';
import { JENKINSFILE_HELP, KEY_MEANING, pipelineName } from './pipeline-texts';

const RECENT_RUNS = 5;

@Component({
  selector: 'dso-pipeline-page',
  imports: [
    ClipboardModule,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    DatePipe,
    RouterLink,
    DsoLoading,
    LoadError,
    MetricsBanner,
    CountedPipe,
    RunGrid,
    StatusChip,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page">
      <nav class="breadcrumb" aria-label="Breadcrumb">
        <a [routerLink]="section.path">{{ section.heading }}</a>
        <span class="sep" aria-hidden="true">/</span>
        @if (pipeline.hasValue()) {
          @let p = pipeline.value();
          <span>{{ p.productName }}</span>
          <span class="sep" aria-hidden="true">/</span>
          <span>{{ name(p) }}</span>
        } @else {
          <span>Pipeline</span>
        }
      </nav>

      @if (pipeline.isLoading() && !pipeline.hasValue()) {
        <dso-loading />
      }

      @if (pipeline.error(); as error) {
        <dso-load-error what="The pipeline" [error]="error" (retry)="pipeline.reload()">
          <a class="btn btn-link" [routerLink]="section.path">Back to pipelines</a>
        </dso-load-error>
      } @else if (pipeline.hasValue()) {
        @let p = pipeline.value();
        <header class="page-header">
          <div>
            <div class="title row-wrap">
              <h1>
                <span class="mono">{{ p.serviceName }}</span> · {{ typeLabel(p.type) }} pipeline
              </h1>
              @if (health(); as h) {
                <span class="last-run">
                  @if (h.status !== 'DISABLED') {
                    <span class="muted">Last run</span>
                  }
                  <dso-status-chip [status]="h.status" />
                </span>
              }
            </div>
            <p class="page-description">
              The automated build, test and security checks Jenkins runs for
              <span class="mono">{{ p.serviceName }}</span
              >, a service of {{ p.productName }} <span class="mono muted">{{ p.productCode }}</span
              >.
            </p>
          </div>
          <div class="actions">
            @if (p.jenkinsJobUrl; as job) {
              <a class="btn btn-outline-primary" [href]="job" target="_blank" rel="noopener"
                >Open in Jenkins</a
              >
            }
            <button
              type="button"
              class="btn btn-outline-primary"
              [cdkMenuTriggerFor]="more"
              [cdkMenuPosition]="menuAtEnd"
            >
              More
            </button>
            <ng-template #more>
              <div cdkMenu class="dropdown-menu dso-menu">
                <button cdkMenuItem class="dropdown-item" (click)="editServiceConfig(p)">
                  Configure service
                </button>
                <a cdkMenuItem class="dropdown-item" [routerLink]="productLink(p.productId)"
                  >Open {{ p.productName }} in Admin</a
                >
                <hr class="dropdown-divider" />
                <button cdkMenuItem class="dropdown-item" (click)="actions.showConfig(p)">
                  Settings sent to Jenkins (config.yaml)
                </button>
                <button cdkMenuItem class="dropdown-item" (click)="keyHistory(p)">
                  Key history
                </button>
                <hr class="dropdown-divider" />
                @if (p.activeKey) {
                  <button cdkMenuItem class="dropdown-item" (click)="replaceKey(p)">
                    Replace key
                  </button>
                  <button cdkMenuItem class="dropdown-item danger" (click)="revokeKey(p)">
                    Invalidate key
                  </button>
                }
                <button cdkMenuItem class="dropdown-item danger" (click)="deletePipeline(p)">
                  Delete pipeline
                </button>
              </div>
            </ng-template>
            <button
              type="button"
              class="btn"
              [class.btn-primary]="p.activeKey"
              [class.btn-outline-primary]="!p.activeKey"
              (click)="edit(p)"
            >
              Edit settings
            </button>
          </div>
        </header>

        @if (!p.activeKey) {
          <div class="banner danger" role="status">
            <span class="banner-text">
              <strong>The pipeline key is invalidated.</strong> The pipeline is refused its settings
              and stops at its next start. Regenerate the key, then put the new Jenkinsfile in the
              service's repository.
            </span>
            <button
              type="button"
              class="btn btn-primary"
              [disabled]="regenerating()"
              (click)="regenerateKey(p)"
            >
              {{ regenerating() ? 'Regenerating…' : 'Regenerate key' }}
            </button>
          </div>
        }

        <div class="columns">
          <section class="card panel">
            <header class="card-header"><h2>Pipeline key</h2></header>
            <p class="section-help">
              {{ keyHelp }}. Keep it private: it belongs only in the Jenkinsfile below.
            </p>
            @if (p.activeKey; as key) {
              <div class="key row-wrap">
                <span class="mono key-value">{{
                  revealed() && key.value ? key.value : key.hint
                }}</span>
                @if (key.value; as value) {
                  <button type="button" class="text-link" (click)="revealed.set(!revealed())">
                    {{ revealed() ? 'Hide the key' : 'Show the key' }}
                  </button>
                  <button
                    type="button"
                    class="text-link"
                    [cdkCopyToClipboard]="value"
                    (cdkCopyToClipboardCopied)="actions.copied()"
                  >
                    Copy the key
                  </button>
                }
              </div>
              <dl class="rows">
                <div>
                  <dt>Issued</dt>
                  <dd>{{ key.issuedAt | date: dateTime }}</dd>
                </div>
                <div>
                  <dt>Last used by Jenkins</dt>
                  <dd>{{ key.lastUsedAt ? (key.lastUsedAt | date: dateTime) : 'Not yet' }}</dd>
                </div>
                <div>
                  <dt>Earlier keys</dt>
                  <dd>
                    @if (earlierKeys(p); as earlier) {
                      {{ earlier | counted: 'key' }}, all invalidated ·
                      <button type="button" class="text-link" (click)="keyHistory(p)">
                        Key history
                      </button>
                    } @else {
                      None
                    }
                  </dd>
                </div>
              </dl>
            } @else {
              <p class="no-key">
                The pipeline has no working key.
                <button type="button" class="text-link" (click)="keyHistory(p)">Key history</button>
                shows when and why it was invalidated.
              </p>
            }
          </section>

          <section class="card panel">
            <header class="card-header"><h2>Pipeline settings</h2></header>
            <p class="section-help">Where and how Jenkins runs this pipeline.</p>
            <dl class="rows">
              <div>
                <dt>What it does</dt>
                <dd>{{ typeDescription(p.type) }}</dd>
              </div>
              <div>
                <dt>Jenkins job</dt>
                <dd class="mono">{{ p.jenkinsJob ?? 'Not set' }}</dd>
              </div>
              <div>
                <dt>Jenkins agents</dt>
                <dd>
                  <span class="mono">{{ p.agentLabels.join(', ') }}</span>
                  <span class="explain">The machines the pipeline runs on</span>
                </dd>
              </div>
              @if (p.extendedPipelineJob) {
                <div>
                  <dt>Extended pipeline</dt>
                  <dd>
                    <span class="mono">{{ p.extendedPipelineJob }}</span>
                    <span class="explain">Started after the scans of this pipeline</span>
                  </dd>
                </div>
              }
              @if (p.securityPipelineJob) {
                <div>
                  <dt>Security pipeline</dt>
                  <dd>
                    <span class="mono">{{ p.securityPipelineJob }}</span>
                    <span class="explain">Whose build this pipeline deploys and tests</span>
                  </dd>
                </div>
              }
              <div>
                <dt>Monitoring tags</dt>
                <dd>
                  <span class="mono">{{ p.influxProjectTag }} · {{ p.influxEnv }}</span>
                  <span class="explain"
                    >The names the results of this pipeline are stored under</span
                  >
                </dd>
              </div>
              @if (p.description) {
                <div>
                  <dt>Description</dt>
                  <dd>{{ p.description }}</dd>
                </div>
              }
              <div>
                <dt>Last changed</dt>
                <dd>{{ p.updatedAt | date: dateTime }}</dd>
              </div>
            </dl>
          </section>
        </div>

        <section class="card jenkinsfile">
          <header class="card-header">
            <h2>Jenkinsfile</h2>
            <button
              type="button"
              class="btn btn-link"
              [cdkCopyToClipboard]="jenkinsfileText()"
              (cdkCopyToClipboardCopied)="actions.copied('Jenkinsfile')"
            >
              Copy the Jenkinsfile
            </button>
          </header>
          <p class="section-help">{{ jenkinsfileHelp() }}</p>
          <pre class="code-block">{{ jenkinsfileText() }}</pre>
        </section>

        <section class="card runs">
          <header class="card-header">
            <h2>Latest runs</h2>
            <a class="text-link" [routerLink]="monitoringPipeline(p.id)"
              >See every run and the delivery performance (DORA)</a
            >
          </header>
          <p class="section-help">
            Up to {{ recentRuns }} of the latest runs Jenkins reported in the past 30 days.
            {{ timeZoneNote }}
          </p>
          @if (monitoring.error(); as error) {
            <dso-load-error what="The latest runs" [error]="error" (retry)="monitoring.reload()" />
          } @else if (!monitoring.hasValue()) {
            <dso-loading />
          } @else {
            <dso-metrics-banner [metricsError]="monitoring.value().metricsError" />
            @if (runs().length === 0) {
              <div class="empty-state small-empty">
                <h3>No runs in the past 30 days</h3>
                <p>Runs show here once Jenkins runs this pipeline with its Jenkinsfile.</p>
              </div>
            } @else {
              <dso-run-grid label="Latest runs" [runs]="runs()" />
            }
          }
        </section>
      }
    </div>
  `,
  styles: `
    .title {
      gap: 6px 10px;
    }

    .last-run {
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
    }

    .columns {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
      gap: 12px;
      margin-bottom: 12px;
    }

    .card {
      margin-bottom: 12px;
      padding: 10px 14px;
    }

    .columns .card {
      margin-bottom: 0;
    }

    .card-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 8px;
    }

    .key {
      gap: 4px 12px;
      margin: 0 0 8px;
    }

    .key-value {
      font-size: 13px;
      overflow-wrap: anywhere;
    }

    .rows > div {
      display: contents;
    }

    .rows dd {
      overflow-wrap: anywhere;
    }

    .explain {
      display: block;
      color: var(--dso-muted);
      font-size: 11.5px;
    }

    .no-key {
      margin: 0;
      color: var(--dso-muted);
    }

    .small-empty {
      padding: 12px 8px;
    }

    .small-empty p {
      margin: 0;
    }
  `,
})
export class PipelinePage {
  readonly id = input.required<string>();

  private readonly api = inject(PipelinesApi);
  private readonly products = inject(ProductsApi);
  private readonly monitoringApi = inject(MonitoringApi);
  private readonly router = inject(Router);
  protected readonly actions = inject(PipelineActions);

  protected readonly section = PIPELINES;
  protected readonly productLink = adminProduct;
  protected readonly monitoringPipeline = monitoringPipeline;
  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly name = pipelineName;
  protected readonly keyHelp = capitalized(KEY_MEANING);
  protected readonly timeZoneNote = TIME_ZONE_NOTE;
  protected readonly dateTime = 'd MMM y, HH:mm';
  protected readonly recentRuns = RECENT_RUNS;
  protected readonly menuAtEnd = MENU_AT_END;

  private readonly pipelineId = computed(() => Number(this.id()));
  protected readonly pipeline = rxResource({
    params: () => this.pipelineId(),
    stream: ({ params }) => this.api.get(params),
  });
  protected readonly monitoring = rxResource({
    params: () => this.pipelineId(),
    stream: ({ params }) => this.monitoringApi.pipeline(params, '30d'),
  });
  private readonly library = toSignal(
    inject(SettingsApi)
      .get()
      .pipe(
        map((settings) => settings.platform.jenkinsLibrary),
        catchError(() => of(null)),
      ),
    { initialValue: null },
  );

  protected readonly health = computed(() =>
    this.monitoring.hasValue() ? this.monitoring.value() : null,
  );
  protected readonly runs = computed(() => this.health()?.recentRuns.slice(0, RECENT_RUNS) ?? []);
  private readonly productId = computed(() =>
    this.pipeline.hasValue() ? this.pipeline.value().productId : undefined,
  );
  private readonly product = rxResource({
    params: this.productId,
    stream: ({ params }) =>
      forkJoin([this.products.get(params), this.api.listForProduct(params)]).pipe(
        catchError(() => of(null)),
      ),
  });
  /** The pipelines that share one Jenkinsfile with this one, because their services share a repository. */
  private readonly together = computed(() => {
    if (!this.pipeline.hasValue()) {
      return null;
    }
    const pipeline = this.pipeline.value();
    const [product, services] = (this.product.hasValue() && this.product.value()) || [null, []];
    const repositories = new Map(
      product?.services.map((service) => [service.id, bitbucketRepositoryUrl(service.scm)]),
    );
    return {
      pipelines: repositoryGroup(
        pipeline,
        services.flatMap((service) => service.pipelines),
        (other) => repositories.get(other.serviceId),
      ),
      repository: repositories.get(pipeline.serviceId),
    };
  });
  protected readonly jenkinsfileText = computed(() => {
    const together = this.together();
    return together ? jenkinsfile(together.pipelines, this.library()) : '';
  });
  protected readonly jenkinsfileHelp = computed(() => {
    const together = this.together();
    if (!together || together.pipelines.length < 2) {
      return JENKINSFILE_HELP;
    }
    const names = listed(together.pipelines.map((pipeline) => pipeline.serviceName));
    return (
      `One Jenkinsfile with the keys of ${names} goes into ${together.repository}. Ask for one ` +
      `Jenkins job for the repository, not one per service${sharedJobReason(together.pipelines[0].type)}.`
    );
  });
  protected readonly revealed = signal(false);
  protected readonly regenerating = signal(false);

  protected typeDescription(type: PipelineType): string {
    return PIPELINE_TYPES.find((option) => option.value === type)?.description ?? '';
  }

  protected earlierKeys(pipeline: Pipeline): number {
    return Math.max((pipeline.keys?.length ?? 1) - 1, 0);
  }

  protected edit(pipeline: Pipeline): void {
    this.actions.edit(pipeline).subscribe((updated) => this.changed(updated));
  }

  protected editServiceConfig(pipeline: Pipeline): void {
    this.actions.editService(pipeline).subscribe((saved) => {
      if (saved) {
        this.pipeline.reload();
      }
    });
  }

  protected keyHistory(pipeline: Pipeline): void {
    this.actions.showKeyHistory(pipeline).subscribe((updated) => this.keyIssued(updated));
  }

  protected replaceKey(pipeline: Pipeline): void {
    this.actions.replaceKey(pipeline).subscribe((updated) => this.keyIssued(updated));
  }

  protected revokeKey(pipeline: Pipeline): void {
    this.actions.revokeKey(pipeline).subscribe((updated) => this.changed(updated));
  }

  protected regenerateKey(pipeline: Pipeline): void {
    this.regenerating.set(true);
    this.actions
      .regenerateKey(pipeline)
      .pipe(finalize(() => this.regenerating.set(false)))
      .subscribe((updated) => this.keyIssued(updated));
  }

  protected deletePipeline(pipeline: Pipeline): void {
    this.actions.deletePipeline(pipeline).subscribe(() => this.router.navigate([PIPELINES.path]));
  }

  private keyIssued(pipeline: Pipeline): void {
    this.changed(pipeline);
    this.revealed.set(true);
  }

  private changed(pipeline: Pipeline): void {
    this.pipeline.set(pipeline);
    this.monitoring.reload();
  }
}
