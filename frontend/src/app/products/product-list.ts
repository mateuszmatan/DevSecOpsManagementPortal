import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, map } from 'rxjs';
import { ProductsApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import { Department, ProductSummary } from '../core/models';
import { ADMIN_PRODUCTS, adminProduct } from '../core/sections';
import { counted } from '@common/shared/formatting';
import { DsoInput } from '@common/ui/form-field';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';
import { byDepartment } from '@common/departments/departments';
import { pipelineTally, tally } from './pipeline-tally';
import { DepartmentsApi } from '@common/core/api';

@Component({
  selector: 'dso-product-list',
  imports: [ReactiveFormsModule, RouterLink, DsoInput, GRID, DsoLoading],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './product-list.html',
  styleUrl: './product-list.scss',
})
export class ProductList {
  private readonly api = inject(ProductsApi);
  private readonly departmentsApi = inject(DepartmentsApi);
  private readonly router = inject(Router);

  protected readonly newProduct = `${ADMIN_PRODUCTS.path}/new`;
  protected readonly adminProduct = adminProduct;
  protected readonly productId = (product: ProductSummary) => product.id;
  protected readonly clickable = () => 'clickable';
  protected readonly columns: GridColumn<ProductSummary>[] = [
    {
      key: 'product',
      header: 'Product',
      value: (product) => product.name,
      minWidth: 200,
      wrap: true,
    },
    {
      key: 'ownerTeam',
      header: 'Owner team',
      value: (product) => product.ownerTeam ?? '–',
      width: 180,
    },
    {
      key: 'services',
      header: 'Services',
      value: (product) => product.serviceCount,
      numeric: true,
      width: 100,
    },
    {
      key: 'pipelines',
      header: 'Pipelines',
      value: (product) => product.pipelineCount,
      width: 170,
    },
  ];
  protected readonly search = new FormControl('', { nonNullable: true });
  protected readonly query = toSignal(
    this.search.valueChanges.pipe(
      debounceTime(250),
      map((value) => value.trim()),
      distinctUntilChanged(),
    ),
    { initialValue: '' },
  );

  protected readonly products = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.api.list(params),
  });
  protected readonly departments = rxResource({
    stream: () => this.departmentsApi.list<Department>(),
  });

  protected readonly groups = computed(() =>
    this.products.hasValue() && this.departments.hasValue()
      ? byDepartment(this.departments.value(), this.products.value()).filter(
          (group) => group.products.length || !this.query(),
        )
      : null,
  );
  protected readonly empty = computed(() =>
    (this.groups() ?? []).every((group) => group.products.length === 0),
  );
  protected readonly total = computed(() => {
    const groups = this.groups() ?? [];
    const placed = groups.filter((group) => group.department);
    const products = placed.reduce((sum, group) => sum + group.products.length, 0);
    const unassigned = groups.find((group) => !group.department)?.products.length ?? 0;
    const total = `${counted(products, 'product')} in ${counted(placed.length, 'department')}`;
    return unassigned ? `${total}, ${unassigned} not in a department` : total;
  });

  protected readonly errorMessage = errorMessage;
  protected readonly tally = tally;
  protected readonly pipelineTally = pipelineTally;

  protected reload(): void {
    this.products.reload();
    this.departments.reload();
  }

  protected clearSearch(): void {
    this.search.setValue('');
  }

  protected open(product: ProductSummary): void {
    this.router.navigate(adminProduct(product.id));
  }
}
