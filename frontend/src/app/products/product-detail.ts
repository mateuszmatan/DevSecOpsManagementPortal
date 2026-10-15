import { ClipboardModule } from '@angular/cdk/clipboard';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { catchError, finalize, of, switchMap, tap } from 'rxjs';
import { PipelinesApi, ProductsApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import {
  PIPELINE_TYPES,
  Pipeline,
  Product,
  ServicePipelines,
  pipelineTypeLabel,
} from '../core/models';
import { Notifier } from '@common/core/notifier';
import { pipelinePage } from '../core/sections';
import { PipelineActions, typeName } from '../pipelines/pipeline-actions';
import { bitbucketRepositoryUrl } from '../shared/bitbucket';
import { RelativeTimePipe, counted } from '@common/shared/formatting';
import { DsoLoading } from '@common/ui/loading';
import { MENU_AT_END } from '@common/ui/menu';
import { TARGET_LABELS, TOOL_LABELS } from '../shared/build-options';
import { GeneratedKeys } from './generated-keys';
import { pipelineTally } from './pipeline-tally';
import { DepartmentsApi } from '@common/core/api';

@Component({
  selector: 'dso-product-detail',
  imports: [
    ClipboardModule,
    RouterLink,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    DatePipe,
    DsoLoading,
    RelativeTimePipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-detail.html',
  styleUrl: './product-detail.scss',
})
export class ProductDetail {
  readonly id = input.required<string>();

  private readonly products = inject(ProductsApi);
  private readonly pipelines = inject(PipelinesApi);
  private readonly actions = inject(PipelineActions);
  private readonly notifier = inject(Notifier);
  private readonly router = inject(Router);
  private readonly generatedKeys = inject(GeneratedKeys);

  private readonly productId = computed(() => Number(this.id()));
  protected readonly product = rxResource({
    params: () => this.productId(),
    stream: ({ params }) => this.products.get(params),
  });
  private readonly departments = toSignal(
    inject(DepartmentsApi)
      .list()
      .pipe(catchError(() => of([]))),
    { initialValue: [] },
  );
  protected readonly departmentName = computed(() => {
    const id = this.product.hasValue() ? this.product.value().departmentId : null;
    return this.departments().find((department) => department.id === id)?.name;
  });
  protected readonly services = rxResource({
    params: () => this.productId(),
    stream: ({ params }) => {
      const newServices = this.generatedKeys.take(params);
      return this.pipelines
        .listForProduct(params)
        .pipe(tap((services) => this.showGeneratedKeys(services, newServices)));
    },
  });

  protected readonly counts = computed(() => {
    if (!this.services.hasValue()) {
      return null;
    }
    const pipelines = this.services.value().flatMap((service) => service.pipelines);
    const active = pipelines.filter((pipeline) => pipeline.activeKey !== null).length;
    return {
      services: this.services.value().length,
      pipelines: pipelineTally(pipelines.length, active),
      invalidated: active < pipelines.length,
    };
  });

  private readonly repositories = computed(
    () =>
      new Map(
        (this.product.hasValue() ? this.product.value().services : []).map((service) => [
          service.id,
          bitbucketRepositoryUrl(service.scm),
        ]),
      ),
  );

  protected readonly revealed = signal<ReadonlySet<number>>(new Set());
  protected readonly regenerating = signal<ReadonlySet<number>>(new Set());
  protected readonly generated = signal<readonly string[]>([]);
  protected readonly generatedNotice = computed(() => {
    const names = this.generated();
    return names.length === 1
      ? `Pipeline key generated for the new service ${names[0]}.`
      : `Pipeline keys generated for ${names.length} new services: ${names.join(', ')}.`;
  });
  protected readonly errorMessage = errorMessage;
  protected readonly menuAtEnd = MENU_AT_END;

  protected readonly typeLabel = pipelineTypeLabel;
  protected readonly typeName = typeName;
  protected readonly pipelinePage = pipelinePage;
  protected readonly toolLabels = TOOL_LABELS;
  protected readonly targetLabels = TARGET_LABELS;

  protected repositoryOf(service: ServicePipelines): string | null {
    return this.repositories().get(service.serviceId) ?? null;
  }

  protected canAddPipeline(service: ServicePipelines): boolean {
    return service.pipelines.length < PIPELINE_TYPES.length;
  }

  protected toggleKey(pipeline: Pipeline): void {
    this.revealed.update((ids) => withId(ids, pipeline.id, !ids.has(pipeline.id)));
  }

  protected dismissGenerated(): void {
    this.generated.set([]);
  }

  protected copied(): void {
    this.actions.copied();
  }

  protected reload(): void {
    this.product.reload();
    this.services.reload();
  }

  protected showProductConfig(product: Product): void {
    this.products.config(product.id).subscribe({
      next: (code) =>
        this.actions.openCode({
          title: `Settings sent to Jenkins (config.yaml) for ${product.name}`,
          subtitle:
            'What the pipelines of every service receive from the portal, in the format of the DevSecOps library, with the BBH defaults filled in.',
          code,
          fileName: `${product.code.toLowerCase()}-config.yaml`,
        }),
      error: (error) => this.notifier.error(error),
    });
  }

  protected showPipelineConfig(pipeline: Pipeline): void {
    this.actions.showConfig(pipeline);
  }

  protected sameTypeElsewhere(pipeline: Pipeline): Pipeline[] {
    return (this.services.value() ?? [])
      .flatMap((service) => service.pipelines)
      .filter(
        (other) =>
          other.type === pipeline.type &&
          other.serviceId !== pipeline.serviceId &&
          other.activeKey &&
          (pipeline.type !== 'EXTENDED' ||
            other.securityPipelineJob === pipeline.securityPipelineJob),
      );
  }

  protected showJenkinsfile(pipeline: Pipeline, together = false): void {
    this.actions.showJenkinsfile(
      together ? [pipeline, ...this.sameTypeElsewhere(pipeline)] : [pipeline],
    );
  }

  protected addPipeline(product: Product, service: ServicePipelines): void {
    this.actions
      .add({ service, productCode: product.code })
      .subscribe((pipeline) => this.replacePipeline(pipeline));
  }

  protected editPipeline(pipeline: Pipeline): void {
    this.actions.edit(pipeline).subscribe((updated) => this.replacePipeline(updated));
  }

  protected revokeKey(pipeline: Pipeline): void {
    this.actions.revokeKey(pipeline).subscribe((updated) => this.replacePipeline(updated));
  }

  protected replaceKey(pipeline: Pipeline): void {
    this.actions.replaceKey(pipeline).subscribe((updated) => this.keyIssued(updated));
  }

  protected regenerateKey(pipeline: Pipeline): void {
    this.regenerating.update((ids) => withId(ids, pipeline.id));
    this.actions
      .regenerateKey(pipeline)
      .pipe(finalize(() => this.regenerating.update((ids) => withId(ids, pipeline.id, false))))
      .subscribe((updated) => this.keyIssued(updated));
  }

  protected showKeyHistory(pipeline: Pipeline): void {
    this.actions.showKeyHistory(pipeline).subscribe((updated) => this.keyIssued(updated));
  }

  protected deletePipeline(pipeline: Pipeline): void {
    this.actions.deletePipeline(pipeline).subscribe(() =>
      this.services.update((services) =>
        services?.map((service) => ({
          ...service,
          pipelines: service.pipelines.filter((p) => p.id !== pipeline.id),
        })),
      ),
    );
  }

  protected deleteProduct(product: Product): void {
    const pipelines = this.services.hasValue()
      ? this.services.value().flatMap((service) => service.pipelines).length
      : 0;
    this.actions
      .confirm({
        title: `Delete the product ${product.name}?`,
        message:
          `This deletes ${product.name} with its ${counted(product.services.length, 'service')}, ${counted(pipelines, 'pipeline')} and their keys. ` +
          'The Jenkins jobs that use those keys stop working. This cannot be undone.',
        confirmLabel: 'Delete product',
        danger: true,
      })
      .pipe(switchMap(() => this.products.delete(product.id)))
      .subscribe({
        next: () => {
          this.notifier.success(`${product.name} was deleted with its services and pipelines.`);
          this.router.navigate(['/admin/products']);
        },
        error: (error) => this.notifier.error(error),
      });
  }

  private keyIssued(pipeline: Pipeline): void {
    this.replacePipeline(pipeline);
    this.revealed.update((ids) => withId(ids, pipeline.id));
  }

  private showGeneratedKeys(services: ServicePipelines[], newServices: readonly string[]): void {
    const names = new Set(newServices);
    const generated = services.filter(
      (service) =>
        names.has(service.serviceName) &&
        service.pipelines.some((pipeline) => pipeline.activeKey !== null),
    );
    this.generated.set(generated.map((service) => service.serviceName));
    this.revealed.set(
      new Set([
        ...this.revealed(),
        ...generated.flatMap((service) =>
          service.pipelines
            .filter((pipeline) => pipeline.activeKey?.value)
            .map((pipeline) => pipeline.id),
        ),
      ]),
    );
  }

  private replacePipeline(pipeline: Pipeline): void {
    this.services.update((services) =>
      services?.map((service) => {
        if (service.serviceId !== pipeline.serviceId) {
          return service;
        }
        const exists = service.pipelines.some((p) => p.id === pipeline.id);
        const pipelines = exists
          ? service.pipelines.map((p) => (p.id === pipeline.id ? pipeline : p))
          : [...service.pipelines, pipeline].sort(
              (a, b) =>
                PIPELINE_TYPES.findIndex((t) => t.value === a.type) -
                PIPELINE_TYPES.findIndex((t) => t.value === b.type),
            );
        return { ...service, pipelines };
      }),
    );
  }
}

function withId(ids: ReadonlySet<number>, id: number, member = true): ReadonlySet<number> {
  const next = new Set(ids);
  if (member) {
    next.add(id);
  } else {
    next.delete(id);
  }
  return next;
}
