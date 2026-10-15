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
  pairwise,
  startWith,
  switchMap,
} from 'rxjs';
import { PipelinesApi, ProductsApi, ServiceTemplateApi, SettingsApi } from '../core/api';
import { errorMessage, fieldProblems } from '@common/core/errors';
import { Department, GlobalSettings, Product, ServiceTemplate } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { HasUnsavedChanges } from '@common/core/unsaved-changes';
import { ConfirmDialog, ConfirmDialogData } from '@common/shared/confirm-dialog';
import { Field, Fields, area, choice, line, mono } from '@common/shared/fields';
import { addItem, moveItem, removeItem, revalidateAll } from '@common/shared/form-controls';
import { CountedPipe } from '@common/shared/formatting';
import { buildDefaults } from '../shared/service-template';
import { DsoLoading, DsoSpinner } from '@common/ui/loading';
import { PANEL } from '@common/ui/panel';
import { TARGET_LABELS, TOOL_LABELS } from '../shared/build-options';
import {
  ServiceForm,
  applyProductProblems,
  createProductForm,
  createServiceForm,
  duplicateService,
  firstServiceWithProblem,
  patchProduct,
  toProductRequest,
} from './product-form-model';
import { GeneratedKeys } from './generated-keys';
import { NamedProduct, ProductNameDialog, ProductNameDialogData } from './product-name-dialog';
import { ServiceFields } from './service-fields';
import { DepartmentsApi } from '@common/core/api';
import { FieldProblem } from '@common/core/models';

const productFields = (departments: readonly Department[]): Field[] => [
  mono('code', 'Code', '', 3, {
    placeholder: 'CERT',
    maxLength: 50,
    hint: 'The short unique name used in job names and reports, for example PAYHUB',
    error: "Start with a letter; use A-Z, 0-9, '-' or '_'",
  }),
  line('name', 'Name', '', 5, { placeholder: 'CertScanner' }),
  choice(
    'departmentId',
    'Department',
    departments.map(({ id, name }) => ({ value: id, label: name })),
    '',
    4,
  ),
  line('ownerTeam', 'Owner team', '', 4, { placeholder: 'Security Engineering' }),
  area('description', 'Description', '', 8, {
    placeholder: 'What the product does and who uses it',
  }),
  line('contactEmail', 'Contact e-mail', '', 4, { placeholder: 'team@bbh.com', type: 'email' }),
];

const APP_SCAN_ACCOUNT: Field[] = [
  mono('keyId', 'API key ID', 'asoc.keyId', 6, {
    placeholder: 'bbh_...',
    hint: 'The ID of the API key AppScan on Cloud gave the product; it starts with bbh_',
  }),
  mono('secretCredentialsId', 'Secret text credentials ID', 'asoc.token', 6, {
    placeholder: 'hcl-app-scan-account',
    hint: "The Jenkins credential that holds the key's secret",
  }),
];

@Component({
  selector: 'dso-product-editor',
  imports: [
    ReactiveFormsModule,
    RouterLink,
    PANEL,
    DsoLoading,
    DsoSpinner,
    CountedPipe,
    Fields,
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
  protected readonly loadError = signal<string | null>(null);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);
  protected readonly unmatchedProblems = signal<FieldProblem[]>([]);
  protected readonly submitted = signal(false);
  protected readonly expanded = signal<number | null>(null);

  private readonly formEvent = toSignal(this.form.events);
  protected readonly services = computed<ServiceForm[]>(() => {
    this.formEvent();
    return [...this.form.controls.services.controls];
  });

  private readonly pipelineCounts = signal(new Map<number, number>());
  private readonly serviceFields = viewChildren(ServiceFields);
  private saved = false;
  private generatedCode = '';

  protected readonly productFields = computed(() => productFields(this.departments()));
  protected readonly appScanFields = APP_SCAN_ACCOUNT;
  protected readonly toolLabels = TOOL_LABELS;
  protected readonly targetLabels = TARGET_LABELS;

  ngOnInit(): void {
    const id = this.id();
    this.loading.set(true);
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
              this.router.navigate(['/admin/products']);
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
            this.addService();
            this.form.markAsPristine();
            this.followNameWithCode();
          },
          error: (error) =>
            this.loadError.set(`The departments could not be loaded. ${errorMessage(error)}`),
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
        error: (error) =>
          this.loadError.set(`The product could not be loaded. ${errorMessage(error)}`),
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
    const { tool, buildPath, command } = service.controls.build.controls;
    const build = buildDefaults(template, tool.value);
    service.patchValue({
      build: { buildPath: build.artifact, command: { tasks: build.tasks } },
      delivery: { tasks: template?.deliveryTasks ?? '' },
    });
    tool.valueChanges
      .pipe(startWith(tool.value), pairwise(), takeUntilDestroyed(this.destroyRef))
      .subscribe(([previous, next]) => {
        const before = buildDefaults(template, previous);
        const after = buildDefaults(template, next);
        if (buildPath.value === before.artifact) {
          buildPath.setValue(after.artifact);
        }
        if (command.controls.tasks.value === before.tasks) {
          command.controls.tasks.setValue(after.tasks);
        }
      });
    addItem(this.form.controls.services, service);
    this.expanded.set(this.form.controls.services.length - 1);
  }

  protected duplicate(index: number): void {
    const services = this.form.controls.services;
    addItem(services, duplicateService(services.at(index)), index + 1);
    this.expanded.set(index + 1);
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
    this.dialog
      .open<boolean, ConfirmDialogData, ConfirmDialog>(ConfirmDialog, { data })
      .closed.subscribe((confirmed) => confirmed && this.removeAt(index));
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
          this.router.navigate(['/admin/products', product.id]);
        },
        error: (error) => this.showSaveError(error),
      });
  }

  protected cancel(): void {
    const stored = this.product();
    this.router.navigate(stored ? ['/admin/products', stored.id] : ['/admin/products']);
  }

  private showSaveError(error: unknown): void {
    const problems = fieldProblems(error);
    if (problems.length === 0) {
      this.saveError.set(`The product could not be saved. ${errorMessage(error)}`);
      return;
    }
    this.unmatchedProblems.set(applyProductProblems(this.form, problems));
    this.saveError.set('The portal did not accept some values. They are marked below.');
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
        field?.scrollIntoView({ behavior: 'smooth', block: 'center' });
      },
      { injector: this.injector },
    );
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
