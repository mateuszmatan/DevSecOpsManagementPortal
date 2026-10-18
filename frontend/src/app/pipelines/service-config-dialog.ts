import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ReactiveFormsModule } from '@angular/forms';
import { rxResource } from '@angular/core/rxjs-interop';
import { ProductsApi } from '../core/api';
import { Pipeline, pipelineTypeLabel } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { errorMessage } from '@common/core/errors';
import { DIALOG } from '@common/ui/dialog';
import { LoadError } from '@common/ui/load-error';
import { DsoLoading, DsoSpinner } from '@common/ui/loading';
import { revalidateAll } from '@common/shared/form-controls';
import {
  ServiceForm,
  createProductForm,
  createServiceForm,
  patchProduct,
  toProductRequest,
  toServiceRequest,
} from '../products/product-form-model';
import { ServiceFields } from '../products/service-fields';

export interface ServiceConfigDialogData {
  pipeline: Pipeline;
  allSections?: boolean;
}

@Component({
  selector: 'dso-service-config-dialog',
  imports: [ReactiveFormsModule, DIALOG, DsoLoading, DsoSpinner, LoadError, ServiceFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>Configure {{ data.pipeline.serviceName }}</h2>
    </div>

    <div class="modal-body">
      <p class="subtitle">
        {{ data.pipeline.productName }} ({{ data.pipeline.productCode }})
        @if (!data.allSections) {
          <span class="tag">{{ typeLabel() }} pipeline</span>
        }
      </p>
      @if (productResource.isLoading()) {
        <dso-loading />
      } @else if (productResource.error(); as error) {
        <dso-load-error
          what="The service settings"
          [error]="error"
          (retry)="productResource.reload()"
        />
      } @else if (serviceForm(); as form) {
        @if (saveError()) {
          <div class="banner danger" role="alert">{{ saveError() }}</div>
        }
        <dso-service-fields
          #fields
          [form]="form"
          [pipeline]="data.allSections ? null : data.pipeline.type"
          [productCode]="data.pipeline.productCode"
          [submitted]="submitted()"
        />
      } @else {
        <div class="banner danger" role="alert">
          The service of this pipeline is no longer part of {{ data.pipeline.productName }}.
        </div>
      }
    </div>

    <div class="modal-footer">
      <button type="button" class="btn btn-link" [disabled]="saving()" dsoDialogClose>Cancel</button>
      <button
        type="button"
        class="btn btn-primary"
        [disabled]="saving() || productResource.isLoading()"
        (click)="save()"
      >
        @if (saving()) {
          <dso-spinner />
        }
        Save service configuration
      </button>
    </div>
  `,
  styles: `
    :host {
      display: block;
      width: min(980px, 92vw);
    }
  `,
})
export class ServiceConfigDialog {
  protected readonly data = inject<ServiceConfigDialogData>(DIALOG_DATA);
  private readonly dialogRef = inject<DialogRef<boolean, ServiceConfigDialog>>(DialogRef);
  private readonly productsApi = inject(ProductsApi);
  private readonly notifier = inject(Notifier);
  private readonly fieldsComponent = viewChild(ServiceFields);

  protected readonly submitted = signal(false);
  protected readonly saving = signal(false);
  protected readonly saveError = signal<string | null>(null);

  protected readonly typeLabel = computed(() => pipelineTypeLabel(this.data.pipeline.type));

  protected readonly productResource = rxResource({
    params: () => this.data.pipeline.productId,
    stream: ({ params }) => this.productsApi.get(params),
  });

  protected readonly product = computed(() =>
    this.productResource.hasValue() ? this.productResource.value() : null,
  );

  protected readonly service = computed(
    () => this.product()?.services.find((s) => s.id === this.data.pipeline.serviceId) ?? null,
  );

  protected readonly serviceForm = computed<ServiceForm | null>(() => {
    const s = this.service();
    return s ? createServiceForm(s) : null;
  });

  protected save(): void {
    const form = this.serviceForm();
    const prod = this.product();
    if (!form || !prod) {
      return;
    }

    this.submitted.set(true);
    revalidateAll(form);
    if (this.fieldsComponent()?.revealFirstProblem()) {
      return;
    }

    this.saving.set(true);
    this.saveError.set(null);

    const changed = toServiceRequest(form);
    const productForm = createProductForm();
    patchProduct(productForm, prod);
    const stored = toProductRequest(productForm, prod.version);
    const request = {
      ...stored,
      services: stored.services.map((s) => (s.id === changed.id ? changed : s)),
    };

    this.productsApi.update(prod.id, request).subscribe({
      next: () => {
        this.saving.set(false);
        this.notifier.success(`The configuration of ${changed.name} was saved.`);
        this.dialogRef.close(true);
      },
      error: (err) => {
        this.saving.set(false);
        this.saveError.set(errorMessage(err));
      },
    });
  }
}
