import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { Dialog } from '@angular/cdk/dialog';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  debounceTime,
  distinctUntilChanged,
  finalize,
  map,
  of,
  switchMap,
  tap,
} from 'rxjs';
import { MyDepartment } from '@common/departments/my-department';
import { PipelinesApi, ProductsApi, ServiceTemplateApi, SettingsApi } from '../core/api';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { Department, Product } from '../core/models';
import { Notifier } from '@common/core/notifier';
import {
  ADMIN,
  SELF_SERVICE,
  adminProduct,
  monitoringProduct,
  pipelinePage,
} from '../core/sections';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { byDepartment } from '@common/departments/departments';
import { jenkinsfile, sharedJobReason, sharedRunsNote } from '../products/jenkinsfile';
import { deploysToDv } from '../products/product-form-model';
import { commaItems, filled, joinWords, max, text } from '@common/shared/form-controls';
import { errorText } from '@common/shared/form-errors';
import { counted } from '@common/shared/formatting';
import { agentLabels } from '../shared/build-options';
import { ChoiceTiles } from '../shared/choice-tiles';
import { FORM_FIELD } from '@common/ui/form-field';
import { DsoLoading } from '@common/ui/loading';
import {
  NewProduct,
  WizardPipeline,
  WizardService,
  PRODUCT_MODES,
  ProductMode,
  JenkinsJob,
  WizardDefaults,
  deploys,
  fromService,
  jenkinsJobs,
  jobName,
  notLoaded,
  pipelineChoices,
  pipelineLabel,
  pipelineNames,
  preparation,
  problemText,
  productRequest,
  reviewEntries,
  serviceSummary,
  servicesToStart,
  takesDefaults,
} from './self-service-model';
import { ServiceDialog, ServiceDialogData } from './service-dialog';
import { DepartmentsApi } from '@common/core/api';

interface Onboarded {
  product: Product;
  jobs: JenkinsJob[];
  shared: boolean;
}

const STEPS = ['Product', 'Pipeline', 'Services', 'Review', 'Next steps'];

@Component({
  selector: 'dso-self-service',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    NgTemplateOutlet,
    ClipboardModule,
    FORM_FIELD,
    DsoLoading,
    ChoiceTiles,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './self-service.html',
  styleUrls: ['../../common/shared/wizard.scss', './self-service.scss'],
})
export class SelfService implements HasUnsavedChanges {
  private readonly productsApi = inject(ProductsApi);
  private readonly pipelinesApi = inject(PipelinesApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly myDepartment = inject(MyDepartment);

  protected readonly section = SELF_SERVICE;
  protected readonly admin = ADMIN.heading;
  protected readonly adminProduct = adminProduct;
  protected readonly monitoringProduct = monitoringProduct;
  protected readonly pipelinePage = pipelinePage;
  protected readonly steps = STEPS;
  protected readonly modes = PRODUCT_MODES;
  protected readonly errorText = errorText;
  protected readonly errorMessage = errorMessage;
  protected readonly notLoaded = notLoaded;
  protected readonly preparation = preparation;
  protected readonly jobName = jobName;
  protected readonly sharedJobReason = sharedJobReason;
  protected readonly sharedRunsNote = sharedRunsNote;
  protected readonly pipelineNames = pipelineNames;

  protected readonly step = signal(0);
  protected readonly checked = signal(false);
  protected readonly changed = signal(false);
  protected readonly pipeline = signal<WizardPipeline | null>(null);
  protected readonly pipelineLabel = computed(() => {
    const pipeline = this.pipeline();
    return pipeline ? pipelineLabel(pipeline) : '';
  });
  protected readonly mode = signal<ProductMode>('new');
  protected readonly services = signal<WizardService[]>([]);
  protected readonly removed = signal<readonly number[]>([]);
  protected readonly kept = computed(() =>
    this.services().filter((service) => !this.isRemoved(service)),
  );
  protected readonly unplaced = computed(() =>
    deploys(this.pipeline()!)
      ? this.kept()
          .filter(
            (service) =>
              service.id === null &&
              (service.target === null ||
                (service.target === 'OPENSHIFT' && !service.openShiftProject)),
          )
          .map((service) => service.name)
      : [],
  );
  protected readonly duplicated = computed(() =>
    this.kept()
      .filter(
        (service, index, kept) =>
          service.id === null &&
          kept.some(
            (other, position) =>
              position !== index && other.name.toLowerCase() === service.name.toLowerCase(),
          ),
      )
      .map((service) => service.name),
  );
  protected readonly unscanned = computed(() =>
    this.pipeline() === 'NEXUS_IQ'
      ? this.kept()
          .filter((service) => !service.nexusIqApplication || !service.repositoryUrl)
          .map((service) => service.name)
      : [],
  );
  protected readonly undeployable = computed(() =>
    this.pipeline() === 'FULL'
      ? this.kept()
          .filter(
            (service) =>
              service.target === 'VM' &&
              !this.settingsOf(service)?.urbanCodeApplications.some(({ environments }) =>
                deploysToDv(environments),
              ),
          )
          .map((service) => service.name)
      : [],
  );
  /** The services whose stored or changed settings lack the AppScan API key ID every pipeline signs in with. */
  protected readonly keyless = computed(() =>
    this.kept()
      .filter((service) => {
        const settings = this.settingsOf(service);
        return settings && !settings.appScan.keyId?.trim();
      })
      .map((service) => service.name),
  );
  protected readonly existing = signal<Product | null>(null);
  protected readonly productError = signal<string | null>(null);
  protected readonly code = signal('');
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly saveProblems = signal<string[]>([]);
  protected readonly result = signal<Onboarded | null>(null);

  protected readonly catalogue = rxResource({ stream: () => this.productsApi.list() });
  protected readonly products = computed(() =>
    this.catalogue.hasValue() ? this.catalogue.value() : [],
  );
  protected readonly current = rxResource({
    params: () => this.existing()?.id,
    stream: ({ params: id }) => this.pipelinesApi.listForProduct(id),
  });
  protected readonly pipelines = computed(() =>
    pipelineChoices(this.current.hasValue() ? this.current.value() : null),
  );
  protected readonly departmentsError = signal<string | null>(null);
  protected readonly departments = toSignal(
    inject(DepartmentsApi)
      .list<Department>()
      .pipe(
        tap((departments) => this.forgetUnlistedDepartment(departments)),
        catchError((error) => {
          this.departmentsError.set(errorMessage(error));
          return of([]);
        }),
      ),
    { initialValue: [] },
  );
  private readonly settings = toSignal(
    inject(SettingsApi)
      .get()
      .pipe(catchError(() => of(null))),
    { initialValue: null },
  );
  private readonly library = computed(() => this.settings()?.platform.jenkinsLibrary);
  private readonly serviceDefaults = computed(() => this.settings()?.serviceDefaults ?? null);
  private readonly templateApi = inject(ServiceTemplateApi);
  protected readonly serviceTemplate = rxResource({ stream: () => this.templateApi.get() });
  private readonly template = computed(() =>
    this.serviceTemplate.hasValue() ? this.serviceTemplate.value() : null,
  );
  protected readonly untemplated = computed(() =>
    this.template()
      ? []
      : this.kept()
          .filter((service) =>
            takesDefaults(
              this.pipeline()!,
              service,
              this.existing()?.services.find((stored) => stored.id === service.id),
            ),
          )
          .map((service) => service.name),
  );
  private readonly defaults = computed<WizardDefaults>(() => ({
    tool: this.serviceDefaults()?.buildTool ?? null,
    target: this.serviceDefaults()?.deployTarget ?? null,
    template: this.template(),
    productCode: this.existing()?.code ?? this.code(),
    settings: this.settings(),
  }));

  protected readonly productForm = new FormGroup({
    departmentId: new FormControl<number | null>(
      this.myDepartment.departmentId(),
      Validators.required,
    ),
    name: text('', filled, max(200), (control: AbstractControl) => this.nameInUse(control)),
    ownerTeam: text('', max(200)),
    contactEmail: text('', Validators.email, max(320)),
  });
  protected readonly productId = new FormControl<number | null>(null);
  protected readonly agents = agentLabels('', false);
  private readonly agentList = toSignal(this.agents.valueChanges.pipe(map(commaItems)), {
    initialValue: commaItems(this.agents.value),
  });

  private readonly typedName = toSignal(
    this.productForm.controls.name.valueChanges.pipe(map((name) => name.trim() || null)),
    { initialValue: null },
  );
  protected readonly productName = computed(
    () => this.existing()?.name ?? this.typedName() ?? 'your product',
  );
  private readonly chosenDepartment = toSignal(
    this.productForm.controls.departmentId.valueChanges,
    { initialValue: this.productForm.controls.departmentId.value },
  );
  protected readonly productGroups = computed(() => this.groupsOf(this.chosenDepartment()));
  protected readonly choosable = computed(() =>
    counted(
      this.productGroups().reduce((count, group) => count + group.products.length, 0),
      'product',
    ),
  );
  protected readonly needsDepartment = computed(() => this.existing()?.departmentId === null);
  protected readonly departmentName = computed(() => {
    const product = this.existing();
    const id = product?.departmentId ?? this.chosenDepartment();
    return (
      this.departments().find((department) => department.id === id)?.name ??
      this.products().find((summary) => summary.id === product?.id)?.departmentName ??
      'not set'
    );
  });
  protected readonly review = computed(() =>
    reviewEntries(
      this.pipeline()!,
      this.services(),
      this.existing()?.services ?? [],
      this.removed(),
      this.current.hasValue() ? this.current.value() : null,
      this.agentList().length ? this.agentList() : (this.template()?.agentLabels ?? []),
    ),
  );
  protected readonly doomed = computed(() =>
    this.services()
      .filter((service) => this.isRemoved(service))
      .flatMap((service) => {
        const names = this.pipelinesOf(service.id);
        return names === '' ? [] : [`${service.name} · ${names ?? 'every pipeline it has'}`];
      }),
  );

  protected readonly nextLabel = computed(() =>
    this.step() < 3
      ? `Next: ${STEPS[this.step() + 1]}`
      : this.existing()
        ? 'Save the changes'
        : 'Create the pipelines',
  );

  constructor() {
    effect(() => {
      const labels = this.template()?.agentLabels;
      if (labels && !this.agents.dirty) {
        this.agents.setValue(joinWords(labels, ', '));
      }
    });
    this.productForm.controls.name.valueChanges
      .pipe(
        map((name) => name.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        tap(() => this.code.set('')),
        switchMap((name) => (name ? this.suggestCode(name) : of(''))),
        takeUntilDestroyed(),
      )
      .subscribe((code) => this.code.set(code));
    this.productId.valueChanges
      .pipe(
        tap(() => {
          this.productError.set(null);
          this.useProduct(null);
        }),
        switchMap((id) =>
          id === null
            ? of(null)
            : this.productsApi.get(id).pipe(
                catchError((error) => {
                  this.productError.set(errorMessage(error));
                  return of(null);
                }),
              ),
        ),
        takeUntilDestroyed(),
      )
      .subscribe((product) => this.useProduct(product));
    this.productForm.controls.departmentId.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe((id) => {
        const chosen = this.productId.value;
        const listed = this.groupsOf(id).some((group) =>
          group.products.some((product) => product.id === chosen),
        );
        if (chosen !== null && !listed) {
          this.productId.setValue(null);
        }
      });
  }

  hasUnsavedChanges(): boolean {
    return !this.result() && (this.changed() || this.productForm.dirty);
  }

  protected canRevisit(index: number): boolean {
    return index < this.step() && !this.result() && !this.saving();
  }

  protected goTo(index: number): void {
    if (this.canRevisit(index)) {
      this.checked.set(false);
      this.step.set(index);
    }
  }

  protected back(): void {
    this.goTo(this.step() - 1);
  }

  protected next(): void {
    this.checked.set(true);
    if (this.step() === 0) {
      this.productForm.controls.name.updateValueAndValidity();
      this.productForm.markAllAsTouched();
    }
    if (this.step() === 1) {
      this.agents.markAsTouched();
    }
    if (!this.ready()) {
      return;
    }
    if (this.step() === 3) {
      this.save();
      return;
    }
    this.checked.set(false);
    this.step.update((step) => step + 1);
  }

  protected chooseMode(mode: ProductMode): void {
    this.mode.set(mode);
    if (mode !== 'existing') {
      this.productId.setValue(null);
    }
  }

  protected isRemoved(service: WizardService): boolean {
    return service.id !== null && this.removed().includes(service.id);
  }

  protected removeService(index: number): void {
    const id = this.services()[index].id;
    if (id === null) {
      this.services.update((services) => services.filter((_, position) => position !== index));
    } else {
      this.removed.update((ids) => [...ids, id]);
    }
    this.changed.set(true);
  }

  protected keepService(service: WizardService): void {
    this.removed.update((ids) => ids.filter((id) => id !== service.id));
  }

  protected serviceSummary(service: WizardService): string {
    return serviceSummary(this.pipeline()!, service);
  }

  protected removalText(service: WizardService): string {
    const names = this.pipelinesOf(service.id);
    return `Saving deletes ${service.name}, its pipelines${names ? ` (${names})` : ''} and their keys. Jenkins jobs that use these keys stop working.`;
  }

  protected jenkinsfileOf(job: JenkinsJob): string {
    const pipelines = job.starts.flatMap((start) => (start.pipeline ? [start.pipeline] : []));
    return pipelines.length ? jenkinsfile(pipelines, this.library()) : '';
  }

  protected copied(): void {
    this.notifier.success('Jenkinsfile copied to the clipboard');
  }

  protected restart(): void {
    this.result.set(null);
    this.pipeline.set(null);
    this.chooseMode('new');
    this.productForm.reset({ departmentId: this.myDepartment.departmentId() });
    this.forgetUnlistedDepartment(this.departments());
    this.services.set([]);
    this.removed.set([]);
    this.agents.reset(joinWords(this.template()?.agentLabels, ', '));
    this.changed.set(false);
    this.checked.set(false);
    this.saveError.set(null);
    this.saveProblems.set([]);
    this.step.set(0);
    this.catalogue.reload();
  }

  private ready(): boolean {
    switch (this.step()) {
      case 0:
        return this.mode() === 'new'
          ? this.productForm.valid
          : this.existing()?.id === this.productId.value &&
              (this.productForm.controls.departmentId.valid ||
                (this.departmentsError() !== null && !this.needsDepartment()));
      case 1:
        return this.pipeline() !== null && this.agents.valid;
      case 2:
        return (
          this.kept().length > 0 &&
          [
            this.unplaced(),
            this.unscanned(),
            this.undeployable(),
            this.keyless(),
            this.duplicated(),
            this.untemplated(),
          ].every((names) => names.length === 0)
        );
      default:
        return !this.saving();
    }
  }

  protected openService(index: number | null): void {
    const services = this.services();
    const current = index === null ? null : services[index];
    const stored = current ? this.storedOf(current) : undefined;
    const others = services.filter(
      (service, position) => position !== index && !this.isRemoved(service),
    );
    this.dialog
      .open<WizardService, ServiceDialogData, ServiceDialog>(ServiceDialog, {
        data: {
          pipeline: this.pipeline()!,
          service:
            current && stored && !current.serviceRequest
              ? { ...current, serviceRequest: stored }
              : current,
          takenNames: services
            .filter((_, position) => position !== index)
            .map((service) => service.name),
          defaults: this.defaults(),
          sources: others.map((service) => ({
            name: service.name,
            request: service.serviceRequest ?? this.storedOf(service)!,
          })),
          agents: this.agents,
        },
      })
      .closed.subscribe((service) => {
        if (!service) {
          return;
        }
        this.services.update((list) =>
          index === null
            ? [...list, service]
            : list.map((current, position) => (position === index ? service : current)),
        );
        this.changed.set(true);
      });
  }

  /** The settings of a service: as changed in this request, or as stored. */
  private settingsOf(service: WizardService) {
    return service.serviceRequest ?? this.storedOf(service);
  }

  private storedOf(service: WizardService) {
    return service.id === null
      ? undefined
      : this.existing()?.services.find((candidate) => candidate.id === service.id);
  }

  private forgetUnlistedDepartment(departments: readonly Department[]): void {
    const control = this.productForm.controls.departmentId;
    if (
      this.departmentsError() === null &&
      !departments.some((department) => department.id === control.value)
    ) {
      control.setValue(null);
    }
  }

  private useProduct(product: Product | null): void {
    this.existing.set(product);
    const added = this.services().filter((service) => service.id === null);
    this.services.set([...(product?.services.map(fromService) ?? []), ...added]);
    this.removed.set([]);
  }

  private groupsOf(departmentId: number | null) {
    return byDepartment(this.departments(), this.products()).filter(
      (group) =>
        group.products.length &&
        (group.department === null || group.department.id === departmentId),
    );
  }

  private pipelinesOf(id: number | null): string | null {
    if (!this.current.hasValue()) {
      return null;
    }
    const service = this.current.value().find((candidate) => candidate.serviceId === id);
    return service ? pipelineNames(service) : '';
  }

  private save(): void {
    const pipeline = this.pipeline()!;
    const existing = this.existing();
    const services = this.kept();
    this.saving.set(true);
    this.saveError.set(null);
    this.saveProblems.set([]);
    const saved: Observable<Product> = existing
      ? this.productsApi.update(
          existing.id,
          productRequest(
            existing,
            services,
            pipeline,
            this.productForm.controls.departmentId.value,
            this.template(),
            this.serviceDefaults(),
            commaItems(this.agents.value),
          ),
          pipeline,
        )
      : this.suggestCode(this.productForm.controls.name.value.trim()).pipe(
          switchMap((code) =>
            this.productsApi.create(
              productRequest(
                this.newProduct(code),
                services,
                pipeline,
                null,
                this.template(),
                this.serviceDefaults(),
                commaItems(this.agents.value),
              ),
              pipeline,
            ),
          ),
        );
    saved
      .pipe(
        switchMap((product) =>
          this.pipelinesApi.listForProduct(product.id).pipe(
            catchError(() => of([])),
            map((services) => {
              const jobs = jenkinsJobs(servicesToStart(services, pipeline, product));
              return { product, jobs, shared: jobs.some((job) => job.starts.length > 1) };
            }),
          ),
        ),
        finalize(() => this.saving.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: (onboarded) => {
          this.result.set(onboarded);
          this.checked.set(false);
          this.step.set(4);
        },
        error: (error) => {
          this.saveError.set(errorMessage(error));
          this.saveProblems.set(
            fieldProblems(error).map((problem) => problemText(problem, services)),
          );
        },
      });
  }

  private newProduct(code: string): NewProduct {
    const value = this.productForm.getRawValue();
    return { ...value, code };
  }

  private suggestCode(name: string): Observable<string> {
    return this.productsApi.suggestCode(name).pipe(catchError(() => of('')));
  }

  private nameInUse(control: AbstractControl): ValidationErrors | null {
    const name = String(control.value ?? '')
      .trim()
      .toLowerCase();
    const taken = this.products().some((product) => product.name.toLowerCase() === name);
    return taken
      ? { rule: 'This product is already in the portal: choose "A product in the portal" above' }
      : null;
  }
}
