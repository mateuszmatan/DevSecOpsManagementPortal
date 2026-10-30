import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  Injector,
  OnInit,
  afterNextRender,
  computed,
  inject,
  input,
  signal,
  viewChildren,
} from '@angular/core';
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { Dialog } from '@angular/cdk/dialog';
import { CdkMenu, CdkMenuItem, CdkMenuTrigger } from '@angular/cdk/menu';
import { ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import {
  Observable,
  catchError,
  debounceTime,
  distinctUntilChanged,
  filter,
  finalize,
  forkJoin,
  map,
  of,
  startWith,
  switchMap,
} from 'rxjs';
import { PipelinesApi, ProductsApi, ServiceTemplateApi, SettingsApi } from '../core/api';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { Department, GlobalSettings, Product, ServiceTemplate } from '../core/models';
import { ADMIN, ADMIN_PRODUCTS, adminProduct } from '../core/sections';
import { Notifier } from '@common/core/notifier';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { ConfirmDialogData, askToConfirm } from '@common/shared/confirm-dialog';
import { Field, Fields, area, choice, line, mono } from '@common/shared/fields';
import {
  VALUES_NOT_ACCEPTED,
  addItem,
  joinWords,
  moveItem,
  removeItem,
  revalidateAll,
} from '@common/shared/form-controls';
import { CountedPipe } from '@common/shared/formatting';
import { buildDefaults } from '../shared/service-template';
import { LoadError } from '@common/ui/load-error';
import { DsoLoading, DsoSpinner } from '@common/ui/loading';
import { MENU_AT_END } from '@common/ui/menu';
import { PANEL } from '@common/ui/panel';
import { TARGET_LABELS, TOOL_LABELS } from '../shared/build-options';
import {
  ServiceForm,
  applyProductProblems,
  createProductForm,
  createServiceForm,
  duplicateService,
  dvApplication,
  firstServiceWithProblem,
  followName,
  followToolDefaults,
  patchProduct,
  prefillNewService,
  toProductRequest,
} from './product-form-model';
import {
  ConfigImport,
  ImportNotes,
  ImportedConfig,
  importedServiceForm,
  prefillAgents,
} from './config-import';
import { GeneratedKeys } from './generated-keys';
import { MissingField, MissingFields } from './missing-fields';
import { NamedProduct, ProductNameDialog, ProductNameDialogData } from './product-name-dialog';
import { ServiceFields } from './service-fields';
import { DepartmentsApi } from '@common/core/api';
import { FieldProblem } from '@common/core/models';

/** A new service nobody has changed yet, such as the blank one a new product starts with: a copy or an import fills it. */
const isPlaceholder = (service: ServiceForm) =>
  service.controls.id.value === null && service.pristine;

const productFields = (departments: readonly Department[], creating: boolean): Field[] => [
  mono('code', 'Code', '', 3, {
    placeholder: 'CERT',
    maxLength: 50,
    hint: 'The short unique name used in job names and reports, for example PAYHUB',
    help: "Two to 50 upper case letters, digits, '-' or '_', starting with a letter.",
    error: "Start with a letter; use A-Z, 0-9, '-' or '_'",
  }),
  line('name', 'Name', '', 5, {
    placeholder: 'CertScanner',
    help: 'The full name of the product as the portal shows it. No other product may use it.',
  }),
  choice(
    'departmentId',
    'Business Service',
    departments.map(({ id, name }) => ({ value: id, label: name })),
    '',
    4,
    { help: 'The business service the product belongs to. The portal groups products by it.' },
  ),
  line('ownerTeam', 'Owner team', '', 4, {
    placeholder: 'Security Engineering',
    help: 'The team that looks after the product. Shown in the product list, on the product page and in Pipeline Monitoring, where products can also be searched by it.',
  }),
  area('description', 'Description', '', 8, {
    placeholder: 'What the product does and who uses it',
    help: 'Shown in the product list and on the product page of the portal.',
  }),
  line('contactEmail', 'Contact e-mail', '', 4, {
    placeholder: 'team@bbh.com',
    type: 'email',
    help: 'Where the DevSecOps team can reach the owners of the product; shown as a mail link on the product page. A team mailbox is preferable to a personal address.',
  }),
  ...(creating
    ? [
        mono('agentLabels', 'Jenkins agents', 'pipeline.agentNames', 8, {
          placeholder: 'linux-agent',
          hint: 'separated by commas; the first is the default',
          help: 'Jenkins agent labels or label expressions for the pipelines created with the product. A run uses the one chosen in its AGENT_NAME parameter.',
        }),
      ]
    : []),
];

@Component({
  selector: 'dso-product-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    CdkMenu,
    CdkMenuItem,
    CdkMenuTrigger,
    PANEL,
    DsoLoading,
    DsoSpinner,
    LoadError,
    CountedPipe,
    Fields,
    ConfigImport,
    ImportNotes,
    MissingFields,
    ServiceFields,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-editor.html',
  styleUrl: './product-editor.scss',
})
export class ProductEditor implements OnInit, HasUnsavedChanges {
  readonly id = input<string>();
  readonly department = input<string>();

  private readonly products = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly pipelines = inject(PipelinesApi);
  private readonly settingsApi = inject(SettingsApi);
  private readonly router = inject(Router);
  private readonly notifier = inject(Notifier);
  private readonly dialog = inject(Dialog);
  private readonly generatedKeys = inject(GeneratedKeys);
  private readonly injector = inject(Injector);
  private readonly destroyRef = inject(DestroyRef);

  protected readonly form = createProductForm();
  protected readonly product = signal<Product | null>(null);
  protected readonly settings = signal<GlobalSettings | null>(null);
  private readonly templateApi = inject(ServiceTemplateApi);
  private readonly template = signal<ServiceTemplate | null>(null);
  protected readonly departments = signal<Department[]>([]);
  protected readonly loading = signal(false);
  protected readonly loadError = signal<{ what: string; error: unknown } | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly unmatchedProblems = signal<FieldProblem[]>([]);
  protected readonly submitted = signal(false);
  protected readonly expanded = signal<number | null>(null);
  protected readonly imported = signal<ImportedConfig | null>(null);

  private readonly formEvent = toSignal(this.form.events);
  protected readonly services = computed<ServiceForm[]>(() => {
    this.formEvent();
    return [...this.form.controls.services.controls];
  });
  protected readonly copySources = computed(() =>
    this.services()
      .map((service, index) => ({ service, index }))
      .filter(({ service }) => !isPlaceholder(service)),
  );
  private readonly serviceFields = viewChildren(ServiceFields);
  protected readonly blockers = computed(() => {
    this.formEvent();
    return this.serviceFields()
      .filter((fields) => fields.form().invalid && !isPlaceholder(fields.form()))
      .map((fields) => ({
        fields,
        name: fields.form().controls.name.value || 'new-service',
        missing: fields.missing(),
      }));
  });
  protected readonly blocked = computed(() => this.blockers().length > 0);

  private readonly pipelineCounts = signal(new Map<number, number>());
  private saved = false;
  private generatedCode = '';

  protected readonly productFields = computed(() =>
    productFields(this.departments(), this.id() === undefined),
  );
  protected readonly menuAtEnd = MENU_AT_END;
  protected readonly section = ADMIN;
  protected readonly tab = ADMIN_PRODUCTS;
  protected readonly adminProduct = adminProduct;
  protected readonly toolLabels = TOOL_LABELS;
  protected readonly targetLabels = TARGET_LABELS;

  ngOnInit(): void {
    this.load();
  }

  protected load(): void {
    const id = this.id();
    this.loading.set(true);
    this.loadError.set(null);
    if (id === undefined) {
      this.departmentsApi
        .list<Department>()
        .pipe(
          switchMap((departments) => {
            this.departments.set(departments);
            return this.dialog.open<NamedProduct, ProductNameDialogData, ProductNameDialog>(
              ProductNameDialog,
              { data: { departments, departmentId: Number(this.department()) || null } },
            ).closed;
          }),
          switchMap((named) =>
            named
              ? forkJoin({
                  settings: this.loadSettings(),
                  template: this.loadTemplate(),
                  code: this.suggestCode(named.name),
                }).pipe(map((loaded) => ({ ...loaded, ...named })))
              : of(null),
          ),
          finalize(() => this.loading.set(false)),
          takeUntilDestroyed(this.destroyRef),
        )
        .subscribe({
          next: (started) => {
            if (!started) {
              this.router.navigate([ADMIN_PRODUCTS.path]);
              return;
            }
            this.settings.set(started.settings);
            this.template.set(started.template);
            this.generatedCode = started.code;
            this.form.patchValue({
              name: started.name,
              code: started.code,
              departmentId: started.departmentId,
            });
            this.form.controls.agentLabels.enable();
            this.form.controls.agentLabels.setValue(joinWords(started.template?.agentLabels, ', '));
            this.addService();
            this.form.markAsPristine();
            this.followNameWithCode();
          },
          error: (error) => this.loadError.set({ what: 'The business services', error }),
        });
      return;
    }
    forkJoin({
      product: this.products.get(Number(id)),
      pipelines: this.pipelines.listForProduct(Number(id)),
      settings: this.loadSettings(),
      template: this.loadTemplate(),
      departments: this.departmentsApi.list<Department>(),
    })
      .pipe(
        finalize(() => this.loading.set(false)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe({
        next: ({ product, pipelines, settings, template, departments }) => {
          patchProduct(this.form, product);
          const full = new Set(
            pipelines
              .filter((service) => service.pipelines.some(({ type }) => type === 'FULL'))
              .map((service) => service.serviceId),
          );
          this.form.controls.services.controls
            .filter((service) => full.has(service.controls.id.value!))
            .forEach((service) => this.withFullPipeline(service));
          this.form.controls.services.controls
            .filter((service) => service.invalid)
            .forEach((service) => service.markAllAsTouched());
          this.form.markAsPristine();
          this.product.set(product);
          this.settings.set(settings);
          this.template.set(template);
          this.departments.set(departments);
          this.pipelineCounts.set(
            new Map(pipelines.map((service) => [service.serviceId, service.pipelines.length])),
          );
          this.expanded.set(product.services.length === 1 ? 0 : null);
        },
        error: (error) => this.loadError.set({ what: 'The product', error }),
      });
  }

  hasUnsavedChanges(): boolean {
    return !this.saved && this.form.dirty;
  }

  protected pipelinesOf(service: ServiceForm): number {
    const id = service.controls.id.value;
    return id === null ? 0 : (this.pipelineCounts().get(id) ?? 0);
  }

  protected showsErrors(service: ServiceForm): boolean {
    return service.invalid && (service.touched || this.submitted());
  }

  protected addService(): void {
    const service = createServiceForm(undefined, this.settings()?.serviceDefaults);
    const template = this.template();
    const tool = service.controls.build.controls.tool.value;
    const build = buildDefaults(template, tool);
    service.patchValue({
      build: {
        autoSetup: tool !== 'FLUTTER',
        buildPath: build.artifact,
        command: { tasks: build.tasks },
      },
      delivery: { tasks: template?.deliveryTasks ?? '' },
    });
    this.add(this.prepared(service));
  }

  protected importService(imported: ImportedConfig): void {
    this.fill(this.prepared(importedServiceForm(imported, this.settings()?.serviceDefaults)));
    prefillAgents(this.form.controls.agentLabels, imported.agentLabels);
    this.imported.set(imported);
  }

  protected duplicate(index: number): void {
    const source = this.form.controls.services.at(index);
    const copy = duplicateService(source, this.settings());
    this.followServiceName(copy, source.controls.name.value);
    this.fill(this.withFullPipeline(copy), index + 1, source);
  }

  protected move(index: number, offset: -1 | 1): void {
    const target = index + offset;
    moveItem(this.form.controls.services, index, target);
    if (this.expanded() === index) {
      this.expanded.set(target);
    } else if (this.expanded() === target) {
      this.expanded.set(index);
    }
  }

  protected remove(index: number): void {
    const service = this.form.controls.services.at(index);
    if (service.controls.id.value === null) {
      this.removeAt(index);
      return;
    }
    const pipelines = this.pipelinesOf(service);
    const data: ConfirmDialogData = {
      title: `Remove the service ${service.controls.name.value}?`,
      message: pipelines
        ? `Saving the product deletes the service's ${pipelines === 1 ? 'pipeline' : `${pipelines} pipelines`} and keys. ` +
          'The Jenkins jobs that use those keys stop working.'
        : 'The service is removed when you save the product.',
      confirmLabel: 'Remove service',
      danger: true,
    };
    askToConfirm(this.dialog, data).subscribe((confirmed) => confirmed && this.removeAt(index));
  }

  protected reveal(fields: ServiceFields, missing: MissingField): void {
    this.expanded.set(this.form.controls.services.controls.indexOf(fields.form()));
    fields.reveal(missing);
  }

  protected panelToggled(index: number, open: boolean): void {
    if (open) {
      this.expanded.set(index);
    } else if (this.expanded() === index) {
      this.expanded.set(null);
    }
  }

  protected save(): void {
    this.submitted.set(true);
    this.saveError.set(null);
    this.unmatchedProblems.set([]);
    revalidateAll(this.form);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.saveError.set('Some fields need your attention.');
      this.revealProblem(
        this.form.controls.services.controls.findIndex((service) => service.invalid),
      );
      return;
    }
    const stored = this.product();
    const request = toProductRequest(this.form, stored?.version ?? null);
    const newServices = request.services
      .filter((service) => service.id === null)
      .map((service) => service.name);
    this.saving.set(true);
    (stored ? this.products.update(stored.id, request) : this.products.create(request))
      .pipe(finalize(() => this.saving.set(false)))
      .subscribe({
        next: (product) => {
          this.saved = true;
          this.generatedKeys.record(product.id, newServices);
          this.notifier.success(
            stored
              ? `${product.name} saved. Its pipelines get the new settings the next time they run.`
              : `${product.name} added. Each of its services got a Full pipeline with its own key.`,
          );
          this.router.navigate(adminProduct(product.id));
        },
        error: (error) => this.showSaveError(error),
      });
  }

  protected cancel(): void {
    const stored = this.product();
    this.router.navigate(stored ? adminProduct(stored.id) : [ADMIN_PRODUCTS.path]);
  }

  private showSaveError(error: unknown): void {
    const problems = fieldProblems(error);
    if (problems.length === 0) {
      this.saveError.set(`The product could not be saved. ${errorMessage(error)}`);
      return;
    }
    this.unmatchedProblems.set(applyProductProblems(this.form, problems));
    this.saveError.set(VALUES_NOT_ACCEPTED);
    this.revealProblem(firstServiceWithProblem(problems));
  }

  private revealProblem(serviceIndex: number | null): void {
    const service =
      serviceIndex !== null && serviceIndex >= 0
        ? this.form.controls.services.at(serviceIndex)
        : null;
    if (service) {
      this.expanded.set(serviceIndex);
      this.serviceFields()
        .find((fields) => fields.form() === service)
        ?.revealFirstProblem();
    }
    afterNextRender(
      () => {
        const field = document.querySelector(
          '.product-fields .dso-form-field.has-error, .dso-panel.expanded .dso-form-field.has-error',
        );
        field?.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
      },
      { injector: this.injector },
    );
  }

  /**
   * Prefills a new service with the Library defaults and the AppScan key, its build with the tool's defaults and what
   * the service template takes from its name.
   */
  private prepared(service: ServiceForm): ServiceForm {
    prefillNewService(
      service,
      this.settings(),
      this.form.controls.services.controls.map((other) => other.controls.appScan.getRawValue()),
    );
    followToolDefaults(service, this.template(), this.destroyRef);
    this.followServiceName(service);
    return this.withFullPipeline(service);
  }

  private followServiceName(service: ServiceForm, copiedFrom?: string): void {
    const code = this.form.controls.code;
    const codes = code.valueChanges.pipe(
      startWith(null),
      map(() => code.value),
    );
    followName(service, this.template(), codes, this.destroyRef, copiedFrom);
  }

  private add(service: ServiceForm, index = this.form.controls.services.length): void {
    addItem(this.form.controls.services, service, index);
    this.expanded.set(index);
  }

  /**
   * Adds a service filled from another or from a file, in place of a blank placeholder other than its source, and
   * marks it at once, so the problems it brings show without a save.
   */
  private fill(
    service: ServiceForm,
    index = this.form.controls.services.length,
    source?: ServiceForm,
  ): void {
    const services = this.form.controls.services;
    const placeholder = services.controls.findIndex(
      (other) => other !== source && isPlaceholder(other),
    );
    if (placeholder >= 0) {
      removeItem(services, placeholder);
      index = placeholder;
    }
    service.markAsDirty();
    service.markAllAsTouched();
    this.add(service, index);
  }

  private withFullPipeline(service: ServiceForm): ServiceForm {
    service.controls.urbanCodeApplications.addValidators(dvApplication);
    service.controls.urbanCodeApplications.updateValueAndValidity();
    return service;
  }

  private followNameWithCode(): void {
    const code = this.form.controls.code;
    this.form.controls.name.valueChanges
      .pipe(
        map((name) => name.trim()),
        debounceTime(300),
        distinctUntilChanged(),
        filter((name) => name !== '' && code.value === this.generatedCode),
        switchMap((name) => this.suggestCode(name)),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((suggested) => {
        if (suggested && code.value === this.generatedCode) {
          this.generatedCode = suggested;
          code.setValue(suggested);
        }
      });
  }

  private suggestCode(name: string): Observable<string> {
    return this.products.suggestCode(name).pipe(catchError(() => of('')));
  }

  private loadSettings(): Observable<GlobalSettings | null> {
    return this.settingsApi.get().pipe(catchError(() => of(null)));
  }

  private loadTemplate(): Observable<ServiceTemplate | null> {
    return this.templateApi.get().pipe(catchError(() => of(null)));
  }

  private removeAt(index: number): void {
    removeItem(this.form.controls.services, index);
    const expanded = this.expanded();
    if (expanded === index) {
      this.expanded.set(null);
    } else if (expanded !== null && expanded > index) {
      this.expanded.set(expanded - 1);
    }
  }
}
