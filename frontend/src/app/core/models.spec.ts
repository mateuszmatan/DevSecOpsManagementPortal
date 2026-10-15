import { PIPELINE_TYPES, pipelineTypeLabel, pipelineTypeName, pipelineTypeSlug } from './models';

describe('pipeline types', () => {
  it('lists the Nexus IQ GoldenFix pipeline after the original four', () => {
    expect(PIPELINE_TYPES.map((type) => type.value)).toEqual([
      'FULL',
      'SECURITY',
      'EXTENDED',
      'SAST',
      'NEXUS_IQ',
    ]);
    expect(pipelineTypeLabel('NEXUS_IQ')).toBe('Nexus IQ GoldenFix');
  });

  it('names job and file names by a slug and sentences by a name', () => {
    expect(PIPELINE_TYPES.map((type) => pipelineTypeSlug(type.value))).toEqual([
      'full',
      'security',
      'extended',
      'sast',
      'nexusiq',
    ]);
    expect(PIPELINE_TYPES.map((type) => pipelineTypeName(type.value))).toEqual([
      'full',
      'security',
      'extended',
      'sast',
      'Nexus IQ GoldenFix',
    ]);
  });
});
