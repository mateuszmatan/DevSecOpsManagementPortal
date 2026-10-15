import { NOT_IN_A_DEPARTMENT } from '@common/departments/departments';
import { department, productSummary } from '../testing/fixtures';
import { pipelineTally, tally } from './pipeline-tally';

describe('pipeline tally', () => {
  it('words the pipelines of a product', () => {
    expect(pipelineTally(0, 0)).toBe('None yet');
    expect(pipelineTally(1, 1)).toBe('1, active');
    expect(pipelineTally(3, 3)).toBe('3, all active');
    expect(pipelineTally(3, 1)).toBe('3, 2 keys invalidated');
  });

  it('words the tally of a department in the singular and plural', () => {
    const group = (overrides: Parameters<typeof department>[0]) => ({
      department: department(overrides),
      name: 'Custody',
      products: [],
    });

    expect(tally(group({ productCount: 1, pipelineCount: 1, activePipelineCount: 1 }))).toBe(
      '1 product, 1 pipeline',
    );
    expect(tally(group({ productCount: 3, pipelineCount: 7, activePipelineCount: 6 }))).toBe(
      '3 products, 7 pipelines, 1 key invalidated',
    );
    expect(tally(group({ productCount: 2, pipelineCount: 6, activePipelineCount: 4 }))).toBe(
      '2 products, 6 pipelines, 2 keys invalidated',
    );
    expect(tally(group({ productCount: 0, pipelineCount: 0, activePipelineCount: 0 }))).toBeNull();
  });

  it('sums the tally of the products without a department from their rows', () => {
    const products = [
      productSummary({ pipelineCount: 2, activePipelineCount: 2 }),
      productSummary({ pipelineCount: 0, activePipelineCount: 0 }),
    ];

    expect(tally({ department: null, name: NOT_IN_A_DEPARTMENT, products })).toBe(
      '2 products, 2 pipelines',
    );
  });
});
