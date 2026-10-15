import { ChangeDetectionStrategy, Component, computed, signal, viewChildren } from '@angular/core';
import { DepartmentUsage, DepartmentsAdmin } from '@common/admin/departments-admin';
import { counted } from '@common/shared/formatting';
import { DsoCell } from '@common/ui/grid';
import { Department } from '../core/models';
import { invalidatedKeys } from '../products/pipeline-tally';
import { BarChart, BarRow } from '../shared/bar-chart';

export const DEPARTMENT_USAGE: DepartmentUsage<Department> = {
  subject: 'its products and pipelines',
  counts: [{ noun: 'service', count: (department) => department.serviceCount }],
  columns: [
    {
      key: 'services',
      header: 'Services',
      value: (department) => department.serviceCount,
      numeric: true,
      width: 100,
    },
    {
      key: 'pipelines',
      header: 'Pipelines',
      value: (department) => department.pipelineCount,
      minWidth: 150,
    },
  ],
};

@Component({
  selector: 'dso-pipeline-departments',
  imports: [DepartmentsAdmin, DsoCell, BarChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline-departments.html',
  styleUrl: './pipeline-departments.scss',
})
export class PipelineDepartments {
  protected readonly usage = DEPARTMENT_USAGE;
  protected readonly cells = viewChildren<DsoCell<Department>>(DsoCell);
  protected readonly departments = signal<readonly Department[]>([]);
  protected readonly chart = computed<BarRow[]>(() =>
    this.departments().map((department) => ({
      label: department.name,
      note: [
        counted(department.pipelineCount, 'pipeline'),
        invalidatedKeys(department.pipelineCount, department.activePipelineCount),
      ]
        .filter(Boolean)
        .join(', '),
      segments: [
        { swatch: 'active', label: 'active', count: department.activePipelineCount },
        { swatch: 'disabled', label: 'key invalidated', count: this.invalidated(department) },
      ],
    })),
  );

  protected invalidated(department: Department): number {
    return department.pipelineCount - department.activePipelineCount;
  }

  protected keysInvalidated(department: Department): string | null {
    return invalidatedKeys(department.pipelineCount, department.activePipelineCount);
  }
}
