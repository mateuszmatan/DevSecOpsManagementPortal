import { RETRY, UNREACHABLE } from '@common/core/errors';
import {
  globalSettings,
  pipeline,
  product,
  service,
  servicePipelines,
  serviceTemplate,
} from '../testing/fixtures';
import {
  WizardService,
  changesOf,
  deploys,
  fromService,
  jobName,
  namedDefaults,
  notLoaded,
  pipelineChoices,
  pipelineNames,
  preparation,
  problemText,
  productRequest,
  reviewEntries,
  serviceRequest,
  serviceSummary,
  servicesToStart,
} from './self-service-model';

const APP_ID = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b31';
const TEMPLATE = serviceTemplate();
const REPOSITORY = 'https://bitbucket.bbh.com/projects/PAY/repos/gateway';

function added(overrides: Partial<WizardService> = {}): WizardService {
  return {
    id: null,
    name: 'gateway',
    description: 'Public payment API',
    appScanId: APP_ID,
    tool: 'GRADLE',
    target: 'VM',
    openShiftProject: '',
    nexusIqApplication: '',
    repositoryUrl: '',
    ...overrides,
  };
}

describe('self-service model', () => {
  it('asks only what the chosen pipeline needs', () => {
    expect(preparation('SAST')).toHaveLength(2);
    expect(preparation('FULL')).toContain(
      'Whether each service runs on virtual machines or on OpenShift',
    );
    expect(preparation('NEXUS_IQ')).toEqual([
      'The name of each service and its AppScan application ID, from the Application Security team',
      'Whether each service is built with Gradle or Maven; a developer of the service knows',
      'The Nexus IQ application and the Bitbucket repository of each service',
    ]);
  });

  it('deploys the services only with the Security and Full pipelines', () => {
    expect(deploys('SAST')).toBe(false);
    expect(deploys('NEXUS_IQ')).toBe(false);
    expect(deploys('SECURITY')).toBe(true);
    expect(deploys('FULL')).toBe(true);
  });

  it('scans the libraries of a new service with Nexus IQ and raises its pull requests in Bitbucket', () => {
    const gradle = serviceRequest(
      added({ target: null, nexusIqApplication: 'payhub-gateway', repositoryUrl: REPOSITORY }),
      'NEXUS_IQ',
      TEMPLATE,
    );

    expect(gradle.nexusIqApplications).toEqual([
      {
        application: 'payhub-gateway',
        scanPatterns: ['**/build/libs/*.jar'],
        stage: 'build',
        failOnNetworkError: false,
      },
    ]);
    expect(gradle.scm).toEqual(
      expect.objectContaining({
        repositoryUrl: REPOSITORY,
        credentialsId: 'bitbucket-http-credentials',
        authType: 'BASIC',
      }),
    );
    expect(gradle.deployment.target).toBe('VM');
    expect(gradle.appScan.applicationId).toBe(APP_ID);

    const maven = serviceRequest(
      added({ tool: 'MAVEN', nexusIqApplication: 'payhub-gateway', repositoryUrl: REPOSITORY }),
      'NEXUS_IQ',
      TEMPLATE,
    );
    expect(maven.nexusIqApplications[0].scanPatterns).toEqual(['**/target/*.jar']);
  });

  it('renames the first Nexus IQ application of a service in the portal and keeps the rest', () => {
    const stored = service({
      nexusIqApplications: [
        { ...service().nexusIqApplications[0], scanPatterns: ['web/build/libs/*.jar'] },
        {
          application: 'cert-gui-tools',
          scanPatterns: ['tools/*.jar'],
          stage: 'release',
          failOnNetworkError: true,
        },
      ],
      scm: { ...service().scm, credentialsId: 'ta-bitbucket' },
    });
    const request = serviceRequest(
      { ...fromService(stored), nexusIqApplication: 'cert-web', repositoryUrl: REPOSITORY },
      'NEXUS_IQ',
      TEMPLATE,
      stored,
    );

    expect(request.nexusIqApplications).toEqual([
      { ...stored.nexusIqApplications[0], application: 'cert-web' },
      stored.nexusIqApplications[1],
    ]);
    expect(request.scm).toEqual({
      ...stored.scm,
      repositoryUrl: REPOSITORY,
      credentialsId: 'ta-bitbucket',
    });
    expect(request.build).toEqual(stored.build);
    expect(request.deployment).toEqual(stored.deployment);
    expect(request.testJobs).toEqual(stored.testJobs);
    expect(request.goldenFix).toEqual(stored.goldenFix);
  });

  it('adds the first Nexus IQ application and the repository to a service in the portal that has none', () => {
    const stored = service({
      build: { ...service().build, tool: 'FLUTTER' },
      nexusIqApplications: [],
      scm: { ...service().scm, repositoryUrl: null, credentialsId: null },
    });
    const request = serviceRequest(
      { ...fromService(stored), nexusIqApplication: 'cert-mobile', repositoryUrl: REPOSITORY },
      'NEXUS_IQ',
      TEMPLATE,
      stored,
    );

    expect(request.nexusIqApplications).toEqual([
      {
        application: 'cert-mobile',
        scanPatterns: ['**/pubspec.lock'],
        stage: 'build',
        failOnNetworkError: false,
      },
    ]);
    expect(request.scm.repositoryUrl).toBe(REPOSITORY);
    expect(request.scm.credentialsId).toBe('bitbucket-http-credentials');
  });

  it('keeps the Nexus IQ applications and the repository of a service the answers leave alone', () => {
    const stored = service();

    for (const pipeline of ['NEXUS_IQ', 'SAST', 'FULL'] as const) {
      const request = serviceRequest(fromService(stored), pipeline, TEMPLATE, stored);
      expect(request.nexusIqApplications).toEqual(stored.nexusIqApplications);
      expect(request.scm).toEqual(stored.scm);
    }
    expect(serviceRequest(added(), 'SECURITY', TEMPLATE).nexusIqApplications).toEqual([]);
    expect(serviceRequest(added(), 'SECURITY', TEMPLATE).scm.repositoryUrl).toBeNull();
  });

  it('writes the Nexus IQ answers only for the Nexus IQ GoldenFix pipeline', () => {
    const stored = service();
    const answers = { nexusIqApplication: 'payhub-gateway', repositoryUrl: REPOSITORY };

    for (const pipeline of ['SAST', 'SECURITY', 'FULL'] as const) {
      const fresh = serviceRequest(added(answers), pipeline, TEMPLATE);
      expect(fresh.nexusIqApplications).toEqual([]);
      expect(fresh.scm.repositoryUrl).toBeNull();
      expect(fresh.scm.credentialsId).toBeNull();
      const kept = serviceRequest(
        { ...fromService(stored), ...answers },
        pipeline,
        TEMPLATE,
        stored,
      );
      expect(kept.nexusIqApplications).toEqual(stored.nexusIqApplications);
      expect(kept.scm).toEqual(stored.scm);
    }
  });

  it('builds a Gradle service on a virtual machine with the automatic build tool set-up', () => {
    const request = serviceRequest(added(), 'SECURITY', TEMPLATE);

    expect(request.id).toBeNull();
    expect(request.name).toBe('gateway');
    expect(request.description).toBe('Public payment API');
    expect(request.appScan.applicationId).toBe(APP_ID);
    expect(request.build).toEqual(
      expect.objectContaining({
        tool: 'GRADLE',
        autoSetup: true,
        javaPath: null,
        buildPath: 'build/libs/*.jar',
      }),
    );
    expect(request.build.command.tasks).toEqual(['clean', 'build']);
    expect(request.deployment.target).toBe('VM');
    expect(request.delivery.tasks).toEqual([]);
    expect(request.openShiftTargets).toEqual({});
  });

  it('fills a new service in from the service template the DevSecOps team set', () => {
    const template = serviceTemplate({
      gradleTasks: 'build -x test',
      gradleArtifact: 'out/*.jar',
      gradleScanPattern: '**/out/*.jar',
      deliveryTasks: null,
      bitbucketCredentialsId: 'team-bitbucket',
      imageRegistry: 'registry.bbh.com',
      healthCheckUrl: '/health',
    });
    const request = serviceRequest(
      added({ target: 'OPENSHIFT', openShiftProject: 'pay-gateway' }),
      'FULL',
      template,
    );
    const scanned = serviceRequest(
      added({ nexusIqApplication: 'pay-gateway', repositoryUrl: REPOSITORY }),
      'NEXUS_IQ',
      template,
    );

    expect(request.build.command.tasks).toEqual(['build', '-x', 'test']);
    expect(request.build.buildPath).toBe('out/*.jar');
    expect(request.delivery.tasks).toEqual([]);
    expect(request.openShiftTargets.RD).toEqual(
      expect.objectContaining({
        dockerRepoPush: 'registry.bbh.com/pay-gateway/gateway',
        healthCheckUrl: '/health',
      }),
    );
    expect(scanned.nexusIqApplications[0].scanPatterns).toEqual(['**/out/*.jar']);
    expect(scanned.scm.credentialsId).toBe('team-bitbucket');
  });

  it('leaves the build of a new service empty when the template could not be read', () => {
    const request = serviceRequest(added({ tool: 'MAVEN' }), 'FULL', null);

    expect(request.build.command.tasks).toEqual([]);
    expect(request.build.buildPath).toBeNull();
    expect(request.delivery.tasks).toEqual([]);
  });

  it('names the OpenShift project, Nexus IQ application and repository of a service by the template', () => {
    const defaults = { tool: null, target: null, template: TEMPLATE, productCode: 'PAY' };

    expect(namedDefaults(defaults, 'Gateway')).toEqual({
      openShiftProject: 'pay-gateway',
      nexusIqApplication: 'pay-Gateway',
      repositoryUrl: 'https://bitbucket.bbh.com/projects/PAY/repos/pay-Gateway',
    });
    expect(namedDefaults({ ...defaults, productCode: '' }, 'gateway')).toEqual({
      openShiftProject: '',
      nexusIqApplication: '',
      repositoryUrl: '',
    });
    expect(namedDefaults({ ...defaults, template: null }, 'gateway').repositoryUrl).toBe('');
  });

  it('publishes a Maven service on a virtual machine from target with deploy-file', () => {
    const request = serviceRequest(added({ tool: 'MAVEN' }), 'FULL', TEMPLATE);

    expect(request.build.command.tasks).toEqual(['clean', 'verify']);
    expect(request.build.buildPath).toBe('target/*.jar');
    expect(request.delivery.tasks).toEqual(['deploy:deploy-file']);
  });

  it('names everything of an OpenShift service after its project', () => {
    const request = serviceRequest(
      added({
        name: 'Gateway',
        tool: 'MAVEN',
        target: 'OPENSHIFT',
        openShiftProject: 'pay-payhub',
      }),
      'FULL',
      TEMPLATE,
    );

    expect(request.deployment).toEqual(
      expect.objectContaining({
        target: 'OPENSHIFT',
        appName: 'Gateway',
        artifactName: 'Gateway.jar',
      }),
    );
    expect(request.delivery.tasks).toEqual([]);
    expect(request.openShiftTargets.RD).toEqual(
      expect.objectContaining({
        projectBuild: 'pay-payhub-build',
        projectDeployment: 'pay-payhub-rd',
        buildContext: 'target/docker',
        dockerRepoPush: 'docker-qc.tools.bbh.com/pay-payhub/gateway',
        nexusAuthFile: '/home/jenkins/.docker/nexus-auth.json',
      }),
    );
    expect(request.openShiftTargets.QC).toEqual(
      expect.objectContaining({
        projectDeployment: 'pay-payhub-qc',
        configPath: 'openshift/config-qc.yaml',
      }),
    );
  });

  it('keeps a static scan service on virtual machines whatever was chosen before', () => {
    const request = serviceRequest(
      added({ target: 'OPENSHIFT', openShiftProject: 'pay-payhub' }),
      'SAST',
      TEMPLATE,
    );

    expect(request.deployment.target).toBe('VM');
    expect(request.openShiftTargets).toEqual({});
    expect(serviceRequest(added({ target: null }), 'SAST', TEMPLATE).deployment.target).toBe('VM');
  });

  it('changes only the name, description and AppScan application of a service in the portal', () => {
    const stored = service();
    const request = serviceRequest(
      { ...fromService(stored), name: 'web', description: '', appScanId: APP_ID },
      'SAST',
      TEMPLATE,
      stored,
    );

    expect(request.id).toBe(stored.id);
    expect(request.name).toBe('web');
    expect(request.description).toBeNull();
    expect(request.appScan.applicationId).toBe(APP_ID);
    expect(request.build).toEqual(stored.build);
    expect(request.deployment).toEqual(stored.deployment);
    expect(request.testJobs).toEqual(stored.testJobs);
  });

  it('puts a service of the portal on another build tool with the build settings of a new one', () => {
    const stored = service();
    const request = serviceRequest(
      { ...fromService(stored), tool: 'MAVEN' },
      'FULL',
      TEMPLATE,
      stored,
    );

    expect(request.build).toEqual(
      expect.objectContaining({
        tool: 'MAVEN',
        sourceDir: '.',
        javaPath: null,
        autoSetup: true,
        buildPath: 'target/*.jar',
      }),
    );
    expect(request.build.command.tasks).toEqual(['clean', 'verify']);
    expect(request.build.command.flags).toEqual([]);
    expect(request.delivery.tasks).toEqual(['deploy:deploy-file']);
    expect(request.deployment).toEqual(stored.deployment);
    expect(request.urbanCodeApplications).toEqual(stored.urbanCodeApplications);
    expect(request.unitTests).toEqual(stored.unitTests);
    expect(request.testJobs).toEqual(stored.testJobs);
  });

  it('moves a service of the portal to OpenShift with the deployment settings of a new one', () => {
    const stored = service();
    const request = serviceRequest(
      { ...fromService(stored), target: 'OPENSHIFT', openShiftProject: 'cert-gui' },
      'SECURITY',
      TEMPLATE,
      stored,
    );

    expect(request.build).toEqual(stored.build);
    expect(request.deployment).toEqual({
      target: 'OPENSHIFT',
      appName: 'gui',
      artifactName: 'gui.jar',
      baseArtifactName: null,
    });
    expect(request.openShiftTargets.RD?.projectDeployment).toBe('cert-gui-rd');
    expect(request.sshTargets).toEqual({});
    expect(request.testJobs).toEqual(stored.testJobs);
  });

  it('moves a service of the portal from OpenShift back to virtual machines', () => {
    const stored = service({
      deployment: {
        target: 'OPENSHIFT',
        appName: 'gui',
        artifactName: 'gui.jar',
        baseArtifactName: null,
      },
    });
    const request = serviceRequest(
      { ...fromService(stored), target: 'VM' },
      'FULL',
      TEMPLATE,
      stored,
    );

    expect(request.deployment).toEqual({
      target: 'VM',
      appName: null,
      artifactName: null,
      baseArtifactName: null,
    });
    expect(request.openShiftTargets).toEqual({});
    expect(request.build).toEqual(stored.build);
  });

  it('builds a new service, and one that gets another build tool, from the source folder of the library defaults', () => {
    const defaults = globalSettings().serviceDefaults;
    const stored = service();
    const created = serviceRequest(added(), 'SECURITY', TEMPLATE, undefined, defaults);
    const retooled = serviceRequest(
      { ...fromService(stored), tool: 'MAVEN' },
      'FULL',
      TEMPLATE,
      stored,
      defaults,
    );

    expect(defaults.sourceDir).toBe('app');
    expect(created.build).toEqual(expect.objectContaining({ tool: 'GRADLE', sourceDir: 'app' }));
    expect(created.deployment.target).toBe('VM');
    expect(retooled.build).toEqual(expect.objectContaining({ tool: 'MAVEN', sourceDir: 'app' }));
    expect(serviceRequest(fromService(stored), 'FULL', TEMPLATE, stored, defaults).build).toEqual(
      stored.build,
    );
  });

  it('gives a service of the portal on OpenShift the image build context of its new build tool', () => {
    const stored = service({
      ...serviceRequest(
        added({ name: 'gui', target: 'OPENSHIFT', openShiftProject: 'cert-gui' }),
        'FULL',
        TEMPLATE,
      ),
      id: 10,
    });
    const request = serviceRequest(
      { ...fromService(stored), tool: 'MAVEN' },
      'FULL',
      TEMPLATE,
      stored,
    );

    expect(stored.openShiftTargets.RD?.buildContext).toBe('build/docker');
    expect(request.deployment).toEqual(stored.deployment);
    expect(request.openShiftTargets).toEqual({
      ...stored.openShiftTargets,
      RD: { ...stored.openShiftTargets.RD, buildContext: 'target/docker' },
    });
  });

  it('gives a Maven service of the portal without a build path one when it moves to virtual machines', () => {
    const stored = service({
      build: { ...service().build, tool: 'MAVEN', buildPath: null },
      deployment: {
        target: 'OPENSHIFT',
        appName: 'gui',
        artifactName: 'gui.jar',
        baseArtifactName: null,
      },
    });
    const request = serviceRequest(
      { ...fromService(stored), target: 'VM' },
      'FULL',
      TEMPLATE,
      stored,
    );

    expect(request.build).toEqual({ ...stored.build, buildPath: 'target/*.jar' });
  });

  it('reads a service of the portal back for the wizard', () => {
    expect(fromService(service({ description: null }))).toEqual({
      id: 10,
      name: 'gui',
      description: '',
      appScanId: service().appScan.applicationId,
      tool: 'GRADLE',
      target: 'VM',
      openShiftProject: '',
      nexusIqApplication: 'cert-gui',
      repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
    });
    expect(
      fromService(
        service({ nexusIqApplications: [], scm: { ...service().scm, repositoryUrl: null } }),
      ),
    ).toEqual(expect.objectContaining({ nexusIqApplication: '', repositoryUrl: '' }));
  });

  it('makes a new product from the answers, leaving empty answers out', () => {
    const request = productRequest(
      {
        departmentId: 5,
        name: ' Payments Hub ',
        code: 'PAYMENTSHUB',
        ownerTeam: ' ',
        contactEmail: 'pay@bbh.com',
        appScanKeyId: ' bbh_key ',
      },
      [added()],
      'SECURITY',
    );

    expect(request).toEqual(
      expect.objectContaining({
        code: 'PAYMENTSHUB',
        name: 'Payments Hub',
        description: null,
        ownerTeam: null,
        contactEmail: 'pay@bbh.com',
        departmentId: 5,
        appScan: { keyId: 'bbh_key', secretCredentialsId: null },
        version: null,
      }),
    );
    expect(request.services.map((entry) => entry.name)).toEqual(['gateway']);
    expect(request.services[0].build.command.tasks).toEqual([]);
    expect(
      productRequest(product(), [added()], 'SECURITY', null, TEMPLATE).services[0].build.command
        .tasks,
    ).toEqual(['clean', 'build']);
  });

  it('keeps every detail of a product in the portal and adds the new services after its own', () => {
    const stored = product();
    const request = productRequest(stored, [fromService(stored.services[0]), added()], 'FULL', 5);

    expect(request).toEqual(
      expect.objectContaining({
        code: 'CERT',
        name: 'CertScanner',
        description: 'TLS certificate scanner',
        ownerTeam: 'Technology Architecture',
        departmentId: 3,
        appScan: stored.appScan,
        version: 3,
      }),
    );
    expect(request.services.map((entry) => [entry.id, entry.name])).toEqual([
      [10, 'gui'],
      [null, 'gateway'],
    ]);
  });

  it('puts a product in the portal without a department into the chosen one', () => {
    const stored = product({ departmentId: null });

    expect(productRequest(stored, [], 'SAST', 5).departmentId).toBe(5);
    expect(productRequest(stored, [], 'SAST').departmentId).toBeNull();
  });

  it('names the service a problem of the portal belongs to', () => {
    const services = [added(), added({ name: 'ledger' })];

    expect(
      problemText(
        { field: 'services[1].appScan.applicationId', message: 'belongs to CertScanner' },
        services,
      ),
    ).toBe('ledger, AppScan application ID: belongs to CertScanner');
    expect(
      problemText({ field: 'services[4].build.javaPath', message: 'is required' }, services),
    ).toBe('A service, build.javaPath: is required');
    expect(problemText({ field: 'code', message: 'is taken' }, services)).toBe(
      'product code: is taken',
    );
    expect(
      problemText(
        { field: 'services[0].nexusIqApplications[1].application', message: 'is listed twice' },
        services,
      ),
    ).toBe('gateway, Nexus IQ application: is listed twice');
    expect(
      problemText({ field: 'services[1].scm.credentialsId', message: 'is required' }, services),
    ).toBe('ledger, Bitbucket credentials ID: is required');
    expect(
      problemText({ field: 'services[1].scm.repositoryUrl', message: 'is not a URL' }, services),
    ).toBe('ledger, Bitbucket repository: is not a URL');
  });

  it('sums up a service for the chosen pipeline', () => {
    const openShift = added({ target: 'OPENSHIFT', openShiftProject: 'pay-payhub' });

    expect(serviceSummary('FULL', openShift)).toBe(
      'Built with Gradle, runs on OpenShift in project pay-payhub.',
    );
    expect(serviceSummary('SAST', openShift)).toBe('Built with Gradle.');
    expect(serviceSummary('SAST', fromService(service()))).toBe(
      'Built with Gradle, runs on virtual machines.',
    );
    expect(
      serviceSummary(
        'SECURITY',
        fromService(service({ build: { ...service().build, tool: 'FLUTTER' } })),
      ),
    ).toBe('Built with Flutter, runs on virtual machines.');
    expect(serviceSummary('NEXUS_IQ', { ...openShift, nexusIqApplication: 'payhub-gateway' })).toBe(
      'Built with Gradle, Nexus IQ application payhub-gateway.',
    );
    expect(serviceSummary('NEXUS_IQ', fromService(service()))).toBe(
      'Built with Gradle, runs on virtual machines, Nexus IQ application cert-gui.',
    );
    expect(serviceSummary('NEXUS_IQ', added())).toBe('Built with Gradle.');
  });

  it('says what changes for a service of the portal', () => {
    const stored = service();
    const same = fromService(stored);

    expect(changesOf('FULL', { ...same, appScanId: same.appScanId.toUpperCase() }, stored)).toEqual(
      [],
    );
    expect(
      changesOf(
        'SECURITY',
        {
          ...same,
          name: 'web',
          description: '',
          appScanId: APP_ID,
          tool: 'MAVEN',
          target: 'OPENSHIFT',
          openShiftProject: 'cert-web',
        },
        stored,
      ),
    ).toEqual([
      'renamed from gui',
      'no description',
      'new AppScan application ID',
      'built with Maven instead of Gradle, with the default build settings',
      'runs on OpenShift in project cert-web instead of virtual machines, with the default deployment settings',
    ]);
    expect(changesOf('SAST', { ...same, description: 'Web front end' }, stored)).toEqual([
      'new description',
    ]);
    const nexusIq = { ...same, nexusIqApplication: 'cert-web', repositoryUrl: REPOSITORY };
    expect(changesOf('NEXUS_IQ', nexusIq, stored)).toEqual([
      'new Nexus IQ application',
      'new Bitbucket repository',
    ]);
    expect(changesOf('FULL', nexusIq, stored)).toEqual([]);
  });

  it('says in plain words what saving does to each service', () => {
    const stored = [
      service(),
      service({ id: 11, name: 'api' }),
      service({ id: 12, name: 'batch' }),
      service({ id: 13, name: 'web' }),
    ];
    const current = [
      servicePipelines({ serviceId: 10, pipelines: [pipeline({ type: 'SECURITY' })] }),
      servicePipelines({ serviceId: 11, pipelines: [pipeline()] }),
      servicePipelines({ serviceId: 13, pipelines: [pipeline({ type: 'SECURITY' })] }),
    ];
    const [gui, api, batch, web] = stored.map(fromService);

    expect(
      reviewEntries(
        'SECURITY',
        [gui, { ...api, tool: 'MAVEN' }, batch, { ...web, description: '' }, added()],
        stored,
        [12],
        current,
      ),
    ).toEqual([
      { name: 'gui', tag: null, text: 'Already has a Security pipeline; nothing changes.' },
      {
        name: 'api',
        tag: 'Changed',
        text: 'Gets a Security pipeline with its own key. Built with Maven instead of Gradle, with the default build settings.',
      },
      { name: 'batch', tag: 'Removed', text: 'Deleted, with its pipelines and their keys.' },
      {
        name: 'web',
        tag: 'Changed',
        text: 'Keeps its Security pipeline. No description.',
      },
      {
        name: 'gateway',
        tag: 'New',
        text: 'Added with a Security pipeline and its own key. Built with Gradle, runs on virtual machines.',
      },
    ]);
    expect(reviewEntries('NEXUS_IQ', [gui], stored, [], null)).toEqual([
      {
        name: 'gui',
        tag: null,
        text: 'Gets an OSA pipeline with its own key; nothing else changes.',
      },
    ]);
  });

  it('names the pipelines of a service and counts the services that have each one', () => {
    const services = [
      servicePipelines({
        pipelines: [
          pipeline({ type: 'NEXUS_IQ' }),
          pipeline({ type: 'SAST' }),
          pipeline({ type: 'EXTENDED' }),
          pipeline(),
          pipeline({ type: 'SECURITY' }),
        ],
      }),
      servicePipelines({ serviceName: 'api', pipelines: [pipeline({ type: 'SECURITY' })] }),
    ];

    expect(pipelineNames(services[0])).toBe('Full, Security, Extended, SAST, OSA');
    expect(pipelineNames(servicePipelines({ pipelines: [] }))).toBe('');
    expect(pipelineChoices(services).map((choice) => [choice.name, choice.note])).toEqual([
      ['SAST', '1 of 2 services has it'],
      ['OSA', '1 of 2 services has it'],
      ['Security', 'Every service has it'],
      ['Full', '1 of 2 services has it'],
    ]);
    expect(pipelineChoices([]).map((choice) => choice.note)).toEqual([
      'No service has it yet',
      'No service has it yet',
      'No service has it yet',
      'No service has it yet',
    ]);
    expect(pipelineChoices(null).every((choice) => choice.note === undefined)).toBe(true);
  });

  it('says what could not be loaded and what to do', () => {
    expect(notLoaded('The products', 'Database unavailable')).toBe(
      `The products could not be loaded. Database unavailable. ${RETRY}`,
    );
    expect(notLoaded('The products', `The portal could not complete the request. ${RETRY}`)).toBe(
      `The products could not be loaded. The portal could not complete the request. ${RETRY}`,
    );
    expect(notLoaded('The products', UNREACHABLE)).toBe(
      `The products could not be loaded. ${UNREACHABLE}`,
    );
    expect(notLoaded('The service template', 'Database unavailable', true)).toBe(
      'The service template could not be loaded. Database unavailable.',
    );
  });

  it('finds the pipeline of the chosen type for every service', () => {
    const security = pipeline({ id: 101, type: 'SECURITY' });
    const starts = servicesToStart(
      [
        servicePipelines({ serviceName: 'gui', pipelines: [pipeline(), security] }),
        servicePipelines({ serviceId: 11, serviceName: 'api', pipelines: [pipeline()] }),
      ],
      'SECURITY',
      product(),
    );

    expect(starts).toEqual([
      { serviceName: 'gui', repository: service().scm.repositoryUrl, pipeline: security },
      { serviceName: 'api', repository: '', pipeline: null },
    ]);
    expect(jobName(security)).toBe('DevSecOps/CERT/gui-full');
    expect(jobName({ ...security, jenkinsJob: null })).toBe('DevSecOps/CERT/gui-security');
    expect(jobName(pipeline({ type: 'NEXUS_IQ', jenkinsJob: null }))).toBe(
      'DevSecOps/CERT/gui-nexusiq',
    );
  });
});
