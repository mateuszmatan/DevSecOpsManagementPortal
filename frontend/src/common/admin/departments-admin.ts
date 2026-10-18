import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { Dialog } from '@angular/cdk/dialog';
import { outputFromObservable, rxResource, toObservable } from '@angular/core/rxjs-interop';
import { filter, switchMap } from 'rxjs';
import { DepartmentsApi } from '../core/api';
import { Department } from '../core/models';
import { Notifier } from '../core/notifier';
import { DepartmentDialog } from '../departments/department-dialog';
import { askToConfirm } from '../shared/confirm-dialog';
import { counted } from '../shared/formatting';
import { DsoCell, GRID, GridColumn } from '../ui/grid';
import { LoadError } from '../ui/load-error';
import { DsoLoading } from '../ui/loading';

export interface DepartmentCount<D> {
  noun: string;
  count: (department: D) => number;
}

export interface DepartmentUsage<D extends Department = Department> {
  subject: string;
  counts?: readonly DepartmentCount<D>[];
  columns?: readonly GridColumn<D>[];
}

@Component({
  selector: 'dso-departments-admin',
  imports: [GRID, DsoLoading, LoadError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './departments-admin.html',
  styleUrl: './departments-admin.scss',
})
export class DepartmentsAdmin<D extends Department = Department> {
  readonly usage = input.required<DepartmentUsage<D>>();
  readonly cells = input<readonly DsoCell<D>[]>([]);

  private readonly api = inject(DepartmentsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);

  protected readonly departments = rxResource({ stream: () => this.api.list<D>() });
  private readonly listed = computed(() =>
    this.departments.hasValue() ? this.departments.value() : [],
  );

  readonly loaded = outputFromObservable(toObservable(this.listed));

  protected readonly departmentId = (department: D) => department.id;
  protected readonly columns = computed<GridColumn<D>[]>(() => [
    {
      key: 'name',
      header: 'Business Service',
      value: (department) => department.name,
      minWidth: 150,
    },
    {
      key: 'products',
      header: 'Products',
      value: (department) => department.productCount,
      numeric: true,
      width: 100,
    },
    ...(this.usage().columns ?? []),
    { key: 'actions', header: '', width: 140 },
  ]);
  protected readonly empty = computed(
    () => this.departments.hasValue() && this.departments.value().length === 0,
  );
  protected readonly summary = computed(() => {
    const departments = this.listed();
    const counts: DepartmentCount<D>[] = [
      { noun: 'product', count: (department) => department.productCount },
      ...(this.usage().counts ?? []),
    ];
    const contents = counts.map(({ noun, count }) =>
      counted(
        departments.reduce((sum, department) => sum + count(department), 0),
        noun,
      ),
    );
    return `${counted(departments.length, 'business service')} with ${contents.join(' and ')}`;
  });
  protected readonly help = computed(
    () =>
      'Every product belongs to one business service, and people choose their business service to see ' +
      `${this.usage().subject}. Only an empty business service can be deleted.`,
  );

  protected deleteHint(department: D): string | null {
    if (department.productCount) {
      const them = department.productCount === 1 ? 'it' : 'them';
      return (
        `Only an empty business service can be deleted. ${department.name} still has ` +
        `${counted(department.productCount, 'product')}: move ${them} to another business service first.`
      );
    }
    return null;
  }

  protected add(): void {
    this.edit(null, (saved) => `${saved.name} added. Add its products on the Products tab.`);
  }

  protected rename(department: D): void {
    this.edit(department, (saved) => `${department.name} renamed to ${saved.name}`);
  }

  protected delete(department: D): void {
    askToConfirm(this.dialog, {
      title: `Delete the business service ${department.name}?`,
      message:
        `${department.name} has no products, so nothing else is deleted with it. ` +
        'It disappears from every list of business services in the portal. This cannot be undone.',
      confirmLabel: 'Delete business service',
      danger: true,
    })
      .pipe(
        filter(Boolean),
        switchMap(() => this.api.delete(department.id)),
      )
      .subscribe({
        next: () => {
          this.notifier.success(`${department.name} deleted`);
          this.departments.reload();
        },
        error: (error) => this.notifier.error(error),
      });
  }

  private edit(department: D | null, message: (saved: Department) => string): void {
    this.dialog
      .open<Department, Department | null, DepartmentDialog>(DepartmentDialog, { data: department })
      .closed.pipe(filter((saved): saved is Department => !!saved))
      .subscribe((saved) => {
        this.notifier.success(message(saved));
        this.departments.reload();
      });
  }
}
