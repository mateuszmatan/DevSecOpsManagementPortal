import { DepartmentGroup } from '@common/departments/departments';
import { counted } from '@common/shared/formatting';
import { Department, ProductSummary } from '../core/models';

export function invalidatedKeys(pipelines: number, active: number): string | null {
  const invalidated = pipelines - active;
  return invalidated > 0 ? `${counted(invalidated, 'key')} invalidated` : null;
}

export function pipelineTally(pipelines: number, active: number): string {
  if (pipelines === 0) {
    return 'None yet';
  }
  const invalidated = invalidatedKeys(pipelines, active);
  return `${pipelines}, ${invalidated ?? (pipelines === 1 ? 'active' : 'all active')}`;
}

export function tally(group: DepartmentGroup<ProductSummary, Department>): string | null {
  const sum = (count: (product: ProductSummary) => number) =>
    group.products.reduce((total, product) => total + count(product), 0);
  const { productCount, pipelineCount, activePipelineCount } = group.department ?? {
    productCount: group.products.length,
    pipelineCount: sum((product) => product.pipelineCount),
    activePipelineCount: sum((product) => product.activePipelineCount),
  };
  if (productCount === 0) {
    return null;
  }
  const invalidated = invalidatedKeys(pipelineCount, activePipelineCount);
  const text = `${counted(productCount, 'product')}, ${counted(pipelineCount, 'pipeline')}`;
  return invalidated ? `${text}, ${invalidated}` : text;
}
