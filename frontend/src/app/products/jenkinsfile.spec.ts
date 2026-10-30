import { Pipeline } from '../core/models';
import { pipeline } from '../testing/fixtures';
import {
  byRepository,
  jenkinsfile,
  repositoryGroup,
  sharedJobReason,
  sharingWith,
} from './jenkinsfile';

describe('jenkinsfile', () => {
  it('loads the library the global settings name and passes the active key', () => {
    expect(jenkinsfile([pipeline({ entryPoint: 'devSecOpsSecurityPipeline' })], 'BBHLibrary')).toBe(
      "@Library('BBHLibrary') _\n\n" +
        "devSecOpsSecurityPipeline(pipelineKey: '6f1c2d3e-0000-4abc-9def-123456789abc')\n",
    );
  });

  it('falls back to the default library name when the settings give none', () => {
    expect(jenkinsfile([pipeline()], null)).toContain("@Library('DevSecOpsJenkinsLibrary') _");
    expect(jenkinsfile([pipeline()], '  ')).toContain("@Library('DevSecOpsJenkinsLibrary') _");
  });

  it('asks for a new key when the pipeline has none', () => {
    expect(jenkinsfile([pipeline({ activeKey: null })], 'DevSecOpsJenkinsLibrary')).toContain(
      "devSecOpsPipeline(pipelineKey: '<issue a new key first>')",
    );
  });

  it('passes the keys of several services with the primary one first', () => {
    const other = pipeline({
      serviceName: 'api',
      activeKey: { ...pipeline().activeKey!, value: 'a1b2c3d4-0000-4abc-9def-123456789abc' },
    });
    expect(jenkinsfile([pipeline(), other], 'BBHLibrary')).toBe(
      "@Library('BBHLibrary') _\n\n" +
        'devSecOpsPipeline(pipelineKeys: [\n' +
        "    '6f1c2d3e-0000-4abc-9def-123456789abc',\n" +
        "    'a1b2c3d4-0000-4abc-9def-123456789abc',\n" +
        '])\n',
    );
  });
});

describe('byRepository', () => {
  const run = (id: number, serviceName: string, overrides: Partial<Pipeline> = {}) =>
    pipeline({ id, serviceId: id, serviceName, ...overrides });
  const repositories: Record<string, string | null> = {
    gui: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
    api: ' https://bitbucket.bbh.com/projects/cert/repos/cert.git/ ',
    batch: 'https://bitbucket.bbh.com/projects/CERT/repos/batch',
    worker: 'https://bitbucket.bbh.com/projects/CERT/repos/cert',
    tool: null,
  };
  const names = (pipelines: readonly Pipeline[]) =>
    byRepository(
      pipelines,
      (p) => repositories[p.serviceName],
      (p) => p,
    ).map((group) => group.map((p) => p.serviceName));

  it('puts the active pipelines of one type that share a repository together, in their order', () => {
    expect(
      names([run(1, 'gui'), run(2, 'batch'), run(3, 'api'), run(4, 'tool'), run(5, 'worker')]),
    ).toEqual([['gui', 'api', 'worker'], ['batch'], ['tool']]);
  });

  it('keeps apart other types, invalidated keys and other linked jobs', () => {
    expect(
      names([
        run(1, 'gui'),
        run(2, 'api', { type: 'NEXUS_IQ' }),
        run(3, 'worker', { activeKey: null }),
        run(4, 'api', { securityPipelineJob: 'CERT/api-security' }),
      ]),
    ).toEqual([['gui'], ['api'], ['worker'], ['api']]);
  });

  it('keeps apart pipelines that run on other Jenkins agents, whatever their order', () => {
    expect(
      names([
        run(1, 'gui', { agentLabels: ['linux', 'docker'] }),
        run(2, 'api', { agentLabels: ['docker', 'linux'] }),
        run(3, 'worker', { agentLabels: ['windows'] }),
      ]),
    ).toEqual([['gui', 'api'], ['worker']]);
  });

  it('joins only the pipelines that already run in one Jenkins job, whatever its case', () => {
    expect(
      names([
        run(1, 'gui', { jenkinsJob: 'DevSecOps/CERT/gui-full' }),
        run(2, 'api', { jenkinsJob: 'DevSecOps/CERT/api-full' }),
        run(3, 'worker', { jenkinsJob: ' devsecops/cert/GUI-full ' }),
        run(4, 'gui', { jenkinsJob: null }),
        run(5, 'worker', { jenkinsJob: null }),
      ]),
    ).toEqual([['gui', 'worker'], ['api'], ['gui'], ['worker']]);
  });
});

describe('repositoryGroup', () => {
  const repository = () => 'https://bitbucket.bbh.com/projects/CERT/repos/cert';

  it('gives the pipelines of the Jenkinsfile of a pipeline, with the pipeline as it is now', () => {
    const gui = pipeline({ activeKey: null });
    const api = pipeline({ id: 200, serviceId: 11, serviceName: 'api' });
    const renewed = pipeline();

    expect(repositoryGroup(gui, [gui, api], repository)).toEqual([gui]);
    expect(repositoryGroup(renewed, [gui, api], repository)).toEqual([renewed, api]);
    expect(repositoryGroup(renewed, [], repository)).toEqual([renewed]);
  });
});

describe('sharingWith', () => {
  const repository = () => 'https://bitbucket.bbh.com/projects/CERT/repos/cert';
  const api = pipeline({ id: 200, serviceId: 11, serviceName: 'api' });

  it('gives the other pipelines of the Jenkinsfile of a pipeline, also while its key is invalidated', () => {
    const gui = pipeline({ activeKey: null });
    const separate = pipeline({ ...api, jenkinsJob: 'DevSecOps/CERT/api-full' });

    expect(sharingWith(gui, [gui, api], repository)).toEqual([api]);
    expect(sharingWith(pipeline(), [gui, { ...api, activeKey: null }], repository)).toEqual([]);
    expect(sharingWith(gui, [gui, separate], repository)).toEqual([]);
    expect(sharingWith(gui, [gui, api], () => null)).toEqual([]);
  });
});

describe('sharedJobReason', () => {
  it('names the shared GoldenFix pull request only where GoldenFix runs', () => {
    expect(sharedJobReason('FULL')).toBe(
      ': one GoldenFix pull request then covers all the services of the job',
    );
    expect(sharedJobReason('NEXUS_IQ')).toBe(sharedJobReason('SECURITY'));
    expect(sharedJobReason('SAST')).toBe('');
    expect(sharedJobReason('EXTENDED')).toBe('');
  });
});
