import { TestBed } from '@angular/core/testing';
import { FormGroup } from '@angular/forms';
import { applyFieldProblems, controlAt, revalidateAll } from '@common/shared/form-controls';
import { ServiceDefaults } from '../core/models';
import {
  anotherService,
  command,
  globalSettings,
  product,
  service,
  serviceTemplate,
} from '../testing/fixtures';
import {
  BADGE_TWICE,
  NO_COMMAND,
  ONE_APPLICATION,
  OPENSHIFT_RD_REQUIRED,
  PRODUCT_WIDE_FIELD,
  SAME_NAME,
  applyProductProblems,
  REMOTE_JENKINS_MESSAGE,
  SERVICE_SECTIONS,
  createNexusIqApplicationForm,
  createOpenShiftTargetForm,
  createProductForm,
  createServiceForm,
  createServiceGoldenFixForm,
  createTestJobForm,
  createToolCommandForm,
  createUrbanCodeApplicationForm,
  deploysToDv,
  duplicateService,
  dvApplication,
  firstInvalidSection,
  firstServiceWithProblem,
  followToolDefaults,
  NO_GOLDEN_FIX_OVERRIDES,
  createGlobalGoldenFixForm,
  goldenFixControls,
  inheritsGoldenFix,
  toGlobalGoldenFixPolicy,
  isJobUrl,
  isRemoteJob,
  patchProduct,
  prefillNewService,
  sectionInvalid,
  sectionTouched,
  toProductRequest,
  toServiceRequest,
  toTestJob,
  toToolCommand,
  unitTestsConfigured,
  visibleSections,
  visibleSectionsForPipeline,
} from './product-form-model';

const DEFAULTS: ServiceDefaults = {
  buildTool: 'MAVEN',
  deployTarget: 'OPENSHIFT',
  sourceDir: 'app',
  testsMaxParallel: 20,
};

describe('createServiceForm', () => {
  it('starts a new service with the defaults of the DevSecOps library', () => {
    const value = createServiceForm().getRawValue();

    expect(value.build).toEqual({
      tool: 'GRADLE',
      sourceDir: '.',
      javaPath: '',
      autoSetup: false,
      buildPath: '',
      command: {
        tasks: '',
        flags: '',
        directory: '',
        mavenHome: '',
        environment: '',
        label: '',
        returnStdout: false,
      },
    });
    expect(value.deployment.target).toBe('VM');
    expect(value.testJobs).toEqual([]);
    expect(value.nexusIqApplications).toEqual([]);
    expect(createNexusIqApplicationForm().getRawValue().stage).toBe('build');
    expect(value.tests.smokeRequired && value.tests.regressionRequired).toBe(true);
    expect(value.tests.performanceRequired).toBe(true);
    expect(value.goldenFix.inherit).toBe(true);
    expect(value.goldenFix.enabled).toBeNull();
    expect(value.metrics).toEqual({
      enabled: true,
      influxProject: '',
      influxEnv: 'test',
      influxUrl: '',
      influxCredentialsId: '',
    });
  });

  it('starts a new service from the service defaults of the global settings', () => {
    const value = createServiceForm(undefined, DEFAULTS).getRawValue();

    expect(value.build.tool).toBe('MAVEN');
    expect(value.build.sourceDir).toBe('app');
    expect(value.deployment.target).toBe('OPENSHIFT');
  });

  it('keeps the values of a stored service over the service defaults', () => {
    const value = createServiceForm(service(), DEFAULTS).getRawValue();

    expect(value.build.tool).toBe('GRADLE');
    expect(value.build.sourceDir).toBe('.');
    expect(value.deployment.target).toBe('VM');
  });

  it('needs the JDK path unless a Gradle or Maven build sets it up automatically', () => {
    const form = createServiceForm();
    const { javaPath, autoSetup, tool } = form.controls.build.controls;
    expect(javaPath.hasError('required')).toBe(true);

    autoSetup.setValue(true);
    expect(javaPath.valid).toBe(true);

    tool.setValue('MAVEN');
    expect(javaPath.valid).toBe(true);

    tool.setValue('FLUTTER');
    expect(autoSetup.value).toBe(false);
    expect(autoSetup.disabled).toBe(true);
    expect(javaPath.hasError('required')).toBe(true);

    tool.setValue('GRADLE');
    expect(autoSetup.enabled).toBe(true);
    expect(javaPath.hasError('required')).toBe(true);
  });

  it('needs the Flutter modules and the delivery coordinates on virtual machines', () => {
    const form = createServiceForm();
    form.controls.build.controls.tool.setValue('FLUTTER');
    const flutter = form.controls.flutter.controls;
    expect(flutter.modules.hasError('required')).toBe(true);
    expect(flutter.testModules.hasError('required')).toBe(true);
    expect(flutter.testSubmodules.valid).toBe(true);
    expect(flutter.deliveryGroup.hasError('required')).toBe(true);
    expect(flutter.deliveryArtifact.hasError('required')).toBe(true);
    expect(flutter.deliveryPlugin.hasError('required')).toBe(true);

    form.controls.deployment.controls.target.setValue('OPENSHIFT');
    expect(flutter.deliveryGroup.valid).toBe(true);
    expect(flutter.deliveryArtifact.valid).toBe(true);
    expect(flutter.deliveryPlugin.valid).toBe(true);

    flutter.deliveryPlugin.setValue('deploy:deploy-file $(id)');
    expect(flutter.deliveryPlugin.hasError('pattern')).toBe(true);
  });

  it('accepts only the AppScan client path, folders and Maven home the shell can take', () => {
    const form = createServiceForm();
    form.controls.build.controls.tool.setValue('MAVEN');
    const { clientPath, includedDirs } = form.controls.appScan.controls;
    const { mavenHome } = form.controls.build.controls.command.controls;

    clientPath.setValue('\\SAClientUtil\\bin\\appscan.bat');
    includedDirs.setValue('src/main');
    mavenHome.setValue('/opt/maven-3.9');
    expect([clientPath.valid, includedDirs.valid, mavenHome.valid]).toEqual([true, true, true]);

    clientPath.setValue('bin/appscan.bat; id');
    includedDirs.setValue("src/main\nit's");
    mavenHome.setValue('/opt/maven;id');
    expect(clientPath.hasError('pattern')).toBe(true);
    expect(includedDirs.hasError('item')).toBe(true);
    expect(mavenHome.hasError('pattern')).toBe(true);
  });

  it('needs a DAST target URL once DAST is switched on', () => {
    const form = createServiceForm();
    const { dastEnabled, dastTargetUrl } = form.controls.appScan.controls;
    expect(dastTargetUrl.disabled).toBe(true);

    dastEnabled.setValue(true);
    expect(dastTargetUrl.hasError('required')).toBe(true);

    dastTargetUrl.setValue('ftp://host');
    expect(dastTargetUrl.hasError('pattern')).toBe(true);

    for (const unsafe of ['$(id)', '`id`', '"', '\\']) {
      dastTargetUrl.setValue(`https://cert-uat.testbbh.com/${unsafe}`);
      expect(dastTargetUrl.hasError('pattern')).toBe(true);
    }

    dastTargetUrl.setValue('https://cert-uat.testbbh.com');
    expect(dastTargetUrl.valid).toBe(true);
  });

  it('needs the SonarQube tasks with a project key and the Bitbucket credentials with a repository', () => {
    const form = createServiceForm();
    const { sonar, scm } = form.controls;
    expect(sonar.controls.command.controls.tasks.valid).toBe(true);
    expect(scm.controls.credentialsId.valid).toBe(true);

    sonar.controls.projectKey.setValue('cert-api');
    scm.controls.repositoryUrl.setValue('https://bitbucket.bbh.com/projects/CERT/repos/api');

    expect(sonar.controls.command.controls.tasks.hasError('required')).toBe(true);
    expect(scm.controls.credentialsId.hasError('required')).toBe(true);
  });

  it('takes the pinned image tags the library accepts from a security pipeline', () => {
    const { buildTag, internalDockerUrl } = createOpenShiftTargetForm().controls;
    buildTag.setValue('1.4.2+20261006');
    internalDockerUrl.setValue('registry.svc:5000/cert/gui@sha256:4f2a');
    expect([buildTag.valid, internalDockerUrl.valid]).toEqual([true, true]);

    buildTag.setValue('1.4.2,rc');
    internalDockerUrl.setValue('registry/~cert');
    expect(buildTag.hasError('pattern')).toBe(true);
    expect(internalDockerUrl.hasError('pattern')).toBe(true);
  });

  it('needs the tasks of an analysis or compile command that sets anything else', () => {
    const form = createServiceForm();
    const { sonar, appScan } = form.controls;
    const compile = appScan.controls.compileCommand.controls;

    sonar.controls.command.controls.label.setValue('Analyse');
    compile.returnStdout.setValue(true);
    expect(sonar.controls.command.controls.tasks.hasError('required')).toBe(true);
    expect(compile.tasks.hasError('required')).toBe(true);

    compile.tasks.setValue('classes');
    expect(compile.tasks.valid).toBe(true);
  });

  it('checks names, the AppScan application id and the number of scan patterns', () => {
    const form = createServiceForm({ name: 'Backend API' });
    expect(form.controls.name.hasError('pattern')).toBe(true);
    expect(form.controls.appScan.controls.applicationId.hasError('required')).toBe(true);

    form.controls.appScan.controls.applicationId.setValue(' 109f44ac-cc06-4ca0-884e-d944904f7019 ');
    expect(form.controls.appScan.controls.applicationId.valid).toBe(true);

    const nexusIq = createNexusIqApplicationForm();
    expect(nexusIq.controls.application.hasError('required')).toBe(true);
    expect(nexusIq.controls.scanPatterns.hasError('required')).toBe(true);
    nexusIq.controls.scanPatterns.setValue(
      Array.from({ length: 21 }, (_, i) => `p${i}`).join('\n'),
    );
    expect(nexusIq.controls.scanPatterns.hasError('maxLines')).toBe(true);
  });
});

describe('sections that apply to the build tool and deployment target', () => {
  it('disables the commands and enables the Flutter section for Flutter', () => {
    const form = createServiceForm(service());
    const c = form.controls;
    expect(c.flutter.disabled).toBe(true);

    c.build.controls.tool.setValue('FLUTTER');

    expect(c.build.controls.command.disabled).toBe(true);
    expect(c.unitTests.controls.command.disabled).toBe(true);
    expect(c.sonar.controls.command.disabled).toBe(true);
    expect(c.appScan.controls.compileCommand.disabled).toBe(true);
    expect(c.flutter.enabled).toBe(true);
    expect(c.flutter.controls.signingPasswordCredentialsId.hasError('required')).toBe(true);
    expect(form.invalid).toBe(true);
  });

  it('switches between the virtual machine and the OpenShift sections', () => {
    const form = createServiceForm(service());
    const c = form.controls;
    expect([c.urbanCode, c.urbanCodeApplications, c.sshTargets].every((s) => s.enabled)).toBe(true);
    expect(c.openShiftTargets.disabled).toBe(true);

    c.deployment.controls.target.setValue('OPENSHIFT');

    expect([c.urbanCode, c.urbanCodeApplications, c.sshTargets].every((s) => s.disabled)).toBe(
      true,
    );
    expect(c.openShiftTargets.enabled).toBe(true);
  });

  it('keeps problems of sections that do not apply out of the validity', () => {
    const form = createServiceForm(service());
    form.controls.urbanCodeApplications.at(0).controls.applicationName.setValue('');
    expect(form.invalid).toBe(true);

    form.controls.deployment.controls.target.setValue('OPENSHIFT');
    form.patchValue({ deployment: { appName: 'gui', artifactName: 'gui.jar' } });
    expect(form.invalid).toBe(true);

    form.controls.openShiftTargets.controls.RD.patchValue({
      projectBuild: 'cert-build',
      buildConfigPath: 'openshift/build.yaml',
      dockerFilePath: 'Dockerfile',
      buildContext: '.',
      dockerRepoPush: 'nexus.bbh.com:18444',
      nexusAuthFile: '/etc/containers/auth.json',
    });

    expect(form.valid).toBe(true);
  });

  it('finds the first section holding an invalid value', () => {
    expect(firstInvalidSection(createServiceForm())).toBe('general');

    const form = createServiceForm(service());
    expect(firstInvalidSection(form)).toBeNull();

    form.controls.sonar.controls.command.controls.tasks.setValue('');
    expect(firstInvalidSection(form)).toBe('sonar');
  });

  it.each([
    ['SAST', ['general', 'build', 'appScan', 'scm']],
    ['NEXUS_IQ', ['general', 'build', 'appScan', 'nexusIq', 'scm', 'goldenFix']],
    [
      'SECURITY',
      [
        'general',
        'build',
        'unitTests',
        'deployment',
        'appScan',
        'sonar',
        'nexusIq',
        'scm',
        'goldenFix',
        'metrics',
      ],
    ],
  ] as const)('shows the %s pipeline the sections it reads', (pipeline, sections) => {
    const form = createServiceForm(service());

    expect(visibleSectionsForPipeline(form, pipeline).map((section) => section.id)).toEqual(
      sections,
    );
  });

  it('shows the SECURITY pipeline of a service on OpenShift its OpenShift targets and stops at their problems', () => {
    const form = createServiceForm(service());
    form.controls.deployment.controls.target.setValue('OPENSHIFT');
    form.patchValue({ deployment: { appName: 'gui', artifactName: 'gui.jar' } });

    expect(visibleSectionsForPipeline(form, 'SECURITY').map((section) => section.id)).toContain(
      'openShift',
    );
    expect(firstInvalidSection(form, 'SECURITY')).toBe('openShift');
    expect(firstInvalidSection(form, 'SAST')).toBeNull();
  });

  it('lets a pipeline save past problems in sections it does not read', () => {
    const form = createServiceForm(service());
    form.controls.sonar.controls.command.controls.tasks.setValue('');

    expect(firstInvalidSection(form, 'NEXUS_IQ')).toBeNull();
    expect(firstInvalidSection(form, 'SECURITY')).toBe('sonar');
  });

  it('needs the AppScan API key ID of every service, as every pipeline signs in with it', () => {
    const form = createServiceForm(service());
    form.controls.appScan.controls.keyId.setValue(' ');

    expect(form.controls.appScan.controls.keyId.hasError('required')).toBe(true);
    expect(firstInvalidSection(form, 'NEXUS_IQ')).toBe('appScan');
  });
});

describe('fields hidden while they do not apply', () => {
  it('turns the DAST fields off with DAST and sends only a valid hidden value', () => {
    const form = createServiceForm(service());
    const { dastEnabled, dastTargetUrl, dastPresenceId } = form.controls.appScan.controls;

    dastEnabled.setValue(true);
    dastTargetUrl.setValue('cert-scanner.testbbh.com');
    dastPresenceId.setValue('presence-7');
    expect(form.invalid).toBe(true);

    dastEnabled.setValue(false);

    expect(dastTargetUrl.disabled && dastPresenceId.disabled).toBe(true);
    expect(form.valid).toBe(true);
    expect(toServiceRequest(form).appScan).toMatchObject({
      dastEnabled: false,
      dastTargetUrl: null,
      dastPresenceId: 'presence-7',
    });

    dastEnabled.setValue(true);
    expect(dastTargetUrl.hasError('pattern')).toBe(true);
  });

  it('turns the compile command off with compiling and drops it when it is invalid', () => {
    const form = createServiceForm(service());
    const { compile, compileCommand } = form.controls.appScan.controls;
    compileCommand.patchValue({ tasks: 'compileJava', environment: 'not a variable' });
    expect(form.invalid).toBe(true);

    compile.setValue(false);

    expect(compileCommand.disabled).toBe(true);
    expect(form.valid).toBe(true);
    expect(toServiceRequest(form).appScan.compileCommand).toEqual(NO_COMMAND);

    compileCommand.patchValue({ environment: 'CI=true' });
    expect(toServiceRequest(form).appScan.compileCommand.tasks).toEqual(['compileJava']);

    compile.setValue(true);
    expect(compileCommand.enabled).toBe(true);
  });

  it('turns the remote Jenkins fields of a test job off while it runs on this Jenkins', () => {
    const job = createTestJobForm({ type: 'REMOTE', job: 'CERT/smoke' });
    const { type, remoteJenkinsUrl, remoteJenkins, credentialsId } = job.controls;
    remoteJenkinsUrl.setValue('jenkins-qa.bbh.com');
    expect(job.invalid).toBe(true);

    type.setValue('LOCAL');

    expect([remoteJenkins, remoteJenkinsUrl, credentialsId].every((c) => c.disabled)).toBe(true);
    expect(job.valid).toBe(true);
    expect(toTestJob(job).remoteJenkinsUrl).toBeNull();

    job.controls.job.setValue('https://jenkins-qa.bbh.com/job/smoke/');
    expect(remoteJenkinsUrl.enabled).toBe(true);
    expect(remoteJenkinsUrl.hasError('pattern')).toBe(true);
  });
});

describe('switches between dependent settings', () => {
  it('hides and keeps the values only the Nexus delivery uses while it is disabled on virtual machines', () => {
    const form = createServiceForm(service({ build: { ...service().build, tool: 'MAVEN' } }));
    const { delivery, build, deployment } = form.controls;
    delivery.controls.tasks.setValue('deploy:deploy-file');
    build.controls.buildPath.setValue('');

    expect(build.controls.buildPath.hasError('required')).toBe(true);

    deployment.controls.nexusDeliveryDisabled.setValue(true);

    expect(delivery.disabled).toBe(true);
    expect(build.controls.buildPath.valid).toBe(true);
    expect(deployment.controls.artifactName.disabled).toBe(true);
    expect(toServiceRequest(form).deployment.nexusDeliveryDisabled).toBe(true);

    deployment.controls.nexusDeliveryDisabled.setValue(false);

    expect(delivery.enabled).toBe(true);
    expect(delivery.controls.tasks.value).toBe('deploy:deploy-file');
    expect(build.controls.buildPath.hasError('required')).toBe(true);
  });

  it('drops the OpenShift image build and push of RD from validation while Nexus delivery is disabled', () => {
    const form = createServiceForm(service());
    form.patchValue({
      deployment: { target: 'OPENSHIFT', appName: 'gui', artifactName: 'gui.jar' },
      openShiftTargets: { RD: { dockerRepoPull: 'nexus/gui', projectDeployment: 'cert-rd' } },
    });
    const rd = form.controls.openShiftTargets.controls.RD.controls;
    rd.addFile.setValue('kept.txt');

    expect(rd.projectBuild.hasError('required')).toBe(true);

    form.controls.deployment.controls.nexusDeliveryDisabled.setValue(true);

    expect(form.controls.openShiftTargets.valid).toBe(true);
    expect(
      [rd.projectBuild, rd.dockerRepoPush, rd.internalDockerUrl].every((c) => c.disabled),
    ).toBe(true);
    expect(rd.projectDeployment.enabled).toBe(true);
    expect(toServiceRequest(form).openShiftTargets.RD?.addFile).toBe('kept.txt');
  });

  it('hides the Flutter delivery coordinates while Nexus delivery is disabled', () => {
    const form = createServiceForm(service());
    form.controls.build.controls.tool.setValue('FLUTTER');
    const flutter = form.controls.flutter.controls;

    expect(flutter.deliveryGroup.hasError('required')).toBe(true);

    form.controls.deployment.controls.nexusDeliveryDisabled.setValue(true);

    expect(flutter.deliveryGroup.disabled).toBe(true);
    expect(flutter.modules.enabled).toBe(true);
  });

  it('takes the badge token or its credentials ID, not both', () => {
    const sonar = createServiceForm(service()).controls.sonar.controls;
    sonar.badgeCredentialsId.setValue('sonar-badge');

    expect(sonar.badgeCredentialsId.valid).toBe(true);

    sonar.badgeToken.setValue('sqb_1a2b');

    expect(sonar.badgeCredentialsId.errors).toEqual({ rule: BADGE_TWICE });
    expect(sonar.badgeToken.valid).toBe(true);

    sonar.badgeToken.setValue('');

    expect(sonar.badgeCredentialsId.valid).toBe(true);
  });

  it('leaves the QC region optional and needs what its deployment reads once it is started', () => {
    const form = createServiceForm(service());
    form.controls.deployment.controls.target.setValue('OPENSHIFT');
    const qc = form.controls.openShiftTargets.controls.QC;

    expect(qc.valid).toBe(true);

    qc.controls.healthCheckUrl.setValue('/health');

    expect(
      ['projectDeployment', 'deployConfigPath', 'dockerRepoPull', 'configPath'].filter((key) =>
        qc.get(key)!.hasError('required'),
      ),
    ).toEqual(['projectDeployment', 'deployConfigPath', 'dockerRepoPull', 'configPath']);

    qc.patchValue({
      projectDeployment: 'cert-qc',
      deployConfigPath: 'openshift/deployment.yaml',
      dockerRepoPull: 'nexus/cert',
      skipConfigDeploy: true,
    });

    expect(qc.valid).toBe(true);

    qc.patchValue({ projectDeployment: '', deployConfigPath: '', dockerRepoPull: '' });
    qc.controls.healthCheckUrl.setValue('');
    qc.controls.skipConfigDeploy.setValue(false);

    expect(qc.valid).toBe(true);
  });
});

describe('tool commands', () => {
  it('reads tasks as words and flags and variables as lines, keeping repeats', () => {
    const form = createToolCommandForm(
      command({ tasks: ['clean', 'build'], flags: ['-x', 'test'], environment: ['A=1'] }),
    );
    expect(form.getRawValue()).toEqual({
      tasks: 'clean build',
      flags: '-x\ntest',
      directory: '',
      mavenHome: '',
      environment: 'A=1',
      label: '',
      returnStdout: false,
    });

    form.patchValue({
      tasks: ' clean  test test ',
      flags: '-B\n\n -U ',
      directory: ' app ',
      mavenHome: ' ',
      environment: 'JAVA_OPTS=-Xmx2g\nCI=true',
      label: ' Unit tests ',
      returnStdout: true,
    });

    expect(toToolCommand(form)).toEqual({
      tasks: ['clean', 'test', 'test'],
      flags: ['-B', '-U'],
      directory: 'app',
      mavenHome: null,
      environment: ['JAVA_OPTS=-Xmx2g', 'CI=true'],
      label: 'Unit tests',
      returnStdout: true,
    });
  });
});

describe('test jobs', () => {
  it('needs the remote Jenkins of a remote job given as a path', () => {
    const job = createTestJobForm({ type: 'REMOTE', job: 'CERT/regression' });
    expect(job.controls.remoteJenkins.errors).toEqual({ rule: REMOTE_JENKINS_MESSAGE });

    job.controls.remoteJenkinsUrl.setValue('https://jenkins-qa.bbh.com');
    expect(job.controls.remoteJenkins.valid).toBe(true);

    job.controls.remoteJenkinsUrl.setValue('');
    expect(job.controls.remoteJenkins.invalid).toBe(true);

    job.controls.job.setValue('https://jenkins-qa.bbh.com/job/CERT/job/regression/');
    expect(job.controls.remoteJenkins.valid).toBe(true);
  });

  it('drops the remote values of a local job', () => {
    const job = createTestJobForm({
      stage: 'REGRESSION',
      name: ' nightly ',
      type: 'LOCAL',
      job: ' CERT/regression ',
      timeoutMinutes: 90,
      remoteJenkins: 'qa',
      credentialsId: 'jenkins-qa',
      pollIntervalSec: 20,
      tokenCredentialsId: 'qa-trigger-token',
      useCrumbCache: true,
    });

    expect(toTestJob(job)).toEqual({
      stage: 'REGRESSION',
      name: 'nightly',
      type: 'LOCAL',
      job: 'CERT/regression',
      timeoutMinutes: 90,
      parameters: null,
      remoteJenkins: null,
      remoteJenkinsUrl: null,
      credentialsId: null,
      pollIntervalSec: null,
      tokenCredentialsId: null,
      abortTriggeredJob: false,
      overrideTrustAllCertificates: false,
      preventRemoteBuildQueue: false,
      trustAllCertificates: false,
      useCrumbCache: false,
      useJobInfoCache: false,
    });
    expect(job.controls.tokenCredentialsId.disabled).toBe(true);

    job.controls.type.setValue('REMOTE');
    expect(toTestJob(job)).toMatchObject({
      remoteJenkins: 'qa',
      credentialsId: 'jenkins-qa',
      pollIntervalSec: 20,
      tokenCredentialsId: 'qa-trigger-token',
      useCrumbCache: true,
    });
  });

  it('sends blank parameters as null', () => {
    const job = createTestJobForm({ job: 'CERT/regression' });
    job.controls.parameters.setValue(' \n ');

    expect(job.controls.parameters.valid).toBe(true);
    expect(toTestJob(job).parameters).toBeNull();
  });

  it.each([
    ['ENV=rd,SUITE=critical', true],
    ['ENV=rd\n\nSUITE=', true],
    ['_flag=1', true],
    ['ENV rd', false],
    ['1ENV=rd', false],
    [' ENV=rd', false],
    ['ENV=rd\n=critical', false],
  ])('checks that every parameter line %j is NAME=value: %s', (parameters, valid) => {
    const job = createTestJobForm({ job: 'CERT/regression', parameters });
    expect(job.controls.parameters.valid).toBe(valid);
  });
});

describe('UrbanCode applications', () => {
  it('lists the environments separated by commas and sends them without repeats', () => {
    const form = createServiceForm(service());
    const application = form.controls.urbanCodeApplications.at(0);
    expect(application.controls.environments.value).toBe('DV, RD');

    application.controls.environments.setValue('DV, RD QC,RD');

    expect(toServiceRequest(form).urbanCodeApplications[0]).toMatchObject({
      applicationName: 'CERT-GUI',
      environments: ['DV', 'RD', 'QC'],
      components: [
        { componentName: 'CERT-GUI-app', baseDir: 'build/libs', fileExcludePatterns: null },
      ],
    });
  });

  it('needs at least one component with its base folder and include patterns', () => {
    const stored = service().urbanCodeApplications[0];
    const application = createUrbanCodeApplicationForm({
      ...stored,
      components: [{ ...stored.components[0], baseDir: null, fileIncludePatterns: '' }],
    });
    const component = application.controls.components.at(0).controls;
    expect(component.baseDir.hasError('required')).toBe(true);
    expect(component.fileIncludePatterns.hasError('required')).toBe(true);
    expect(component.fileExcludePatterns.valid).toBe(true);

    application.controls.components.removeAt(0);
    expect(application.controls.components.errors).toEqual({ rule: 'Add at least one component' });
  });

  it('takes an application without environments or with DV as one that deploys to DV', () => {
    expect(deploysToDv([])).toBe(true);
    expect(deploysToDv(['RD', ' dv '])).toBe(true);
    expect(deploysToDv(['RD', 'QC'])).toBe(false);
  });

  it('needs an application for DV while the service deploys to virtual machines', () => {
    const form = createServiceForm(service());
    const applications = form.controls.urbanCodeApplications;
    applications.addValidators(dvApplication);
    applications.at(0).controls.environments.setValue('rd, qc');

    expect(applications.errors).toEqual({
      rule: 'Add an UrbanCode Deploy application for DV: the Full pipeline uses it to deploy to the lower test region',
    });
    expect(firstInvalidSection(form)).toBe('urbanCode');

    applications.push(
      createUrbanCodeApplicationForm({ ...service().urbanCodeApplications[0], environments: [] }),
    );
    expect(applications.valid).toBe(true);

    applications.clear();
    expect(form.invalid).toBe(true);

    form.controls.deployment.controls.target.setValue('OPENSHIFT');
    expect(applications.errors).toBeNull();
    expect(firstInvalidSection(form)).not.toBe('urbanCode');
  });
});

describe('GoldenFix', () => {
  const inherited = (enabled: boolean | null) => ({ enabled, ...NO_GOLDEN_FIX_OVERRIDES });

  it('lets a service follow the global policy, with only its own switch', () => {
    const form = createServiceGoldenFixForm(inherited(false));
    expect(form.controls.inherit.value).toBe(true);
    expect(form.controls.enabled.value).toBe(false);
    expect(form.controls.enabled.enabled).toBe(true);
    expect(form.controls.minThreatLevel.disabled).toBe(true);

    form.controls.inherit.setValue(false);
    expect(form.controls.minThreatLevel.enabled).toBe(true);
  });

  it.each([
    [null, null],
    [true, true],
    [false, false],
  ])('sends the switch %s as %s', (stored, sent) => {
    const form = createServiceForm(service({ goldenFix: inherited(stored) }));
    expect(toServiceRequest(form).goldenFix.enabled).toBe(sent);
  });

  it('sends the inherited policy, or the values the service overrides', () => {
    const form = createServiceForm(service());
    expect(toServiceRequest(form).goldenFix).toEqual(inherited(null));

    form.controls.goldenFix.patchValue({
      inherit: false,
      enabled: true,
      minThreatLevel: 5,
      ecosystems: ['npm', 'npm'],
      excludeDirs: 'legacy\n\nlegacy\ndocs',
    });

    expect(toServiceRequest(form).goldenFix).toEqual({
      ...inherited(true),
      minThreatLevel: 5,
      ecosystems: ['npm'],
      excludeDirs: ['legacy', 'docs'],
    });
  });

  it('requires every value of the global policy, which always switches GoldenFix on or off', () => {
    const group = createGlobalGoldenFixForm(null);
    const c = group.controls;

    expect(c.enabled.value).toBe(true);
    expect(c.onlyDirectDependencies.hasError('required')).toBe(true);
    expect(c.minThreatLevel.hasError('required')).toBe(true);
    expect(c.ecosystems.hasError('required')).toBe(true);
    expect(c.goldenVersionTypes.hasError('required')).toBe(true);
    expect(c.commitAuthorEmail.hasError('required')).toBe(true);
    expect(c.timeZone.valid).toBe(true);
    expect(new FormGroup(goldenFixControls(null)).valid).toBe(true);

    c.enabled.setValue(false);
    expect(toGlobalGoldenFixPolicy(group).enabled).toBe(false);
  });
});

describe('toServiceRequest', () => {
  it('trims values, sends blanks as null and splits the scan patterns into distinct lines', () => {
    const form = createServiceForm(service());
    form.patchValue({
      description: '  ',
      build: { sourceDir: ' ' },
      nexusIqApplications: [{ scanPatterns: ' **/*.jar \n\n**/*.war\n**/*.jar', stage: '' }],
    });

    const request = toServiceRequest(form);

    expect(request.id).toBe(10);
    expect(request.description).toBeNull();
    expect(request.build.sourceDir).toBe('.');
    expect(request.nexusIqApplications[0].scanPatterns).toEqual(['**/*.jar', '**/*.war']);
    expect(request.nexusIqApplications[0].stage).toBe('build');
    expect(request.build.command).toEqual(
      command({ tasks: ['clean', 'build'], flags: ['--refresh-dependencies'] }),
    );
  });

  it('round-trips the stored service of the fixtures', () => {
    const stored = service();

    expect(toServiceRequest(createServiceForm(stored))).toEqual({ ...stored, flutter: null });
  });

  it('round-trips the Bitbucket repository GoldenFix raises its pull requests against', () => {
    const stored = service();
    stored.scm = {
      ...stored.scm,
      apiUrl: 'https://bitbucket.bbh.com',
      workspace: 'bbh-technology',
      projectKey: '~jsmith',
      repoSlug: 'cert-scanner',
    };

    const form = createServiceForm(stored);

    expect(form.controls.scm.valid).toBe(true);
    expect(toServiceRequest(form).scm).toEqual(stored.scm);
  });

  it('accepts only an http or https Bitbucket API URL and names without spaces or slashes', () => {
    const { scm } = createServiceForm(service()).controls;

    scm.controls.apiUrl.setValue('ssh://git@bitbucket.bbh.com');
    scm.controls.workspace.setValue('bbh technology');
    scm.controls.projectKey.setValue('TA/CERT');
    scm.controls.repoSlug.setValue('cert scanner');

    expect(scm.controls.apiUrl.hasError('pattern')).toBe(true);
    expect(scm.controls.workspace.hasError('pattern')).toBe(true);
    expect(scm.controls.projectKey.hasError('pattern')).toBe(true);
    expect(scm.controls.repoSlug.hasError('pattern')).toBe(true);

    scm.controls.repoSlug.setValue('r'.repeat(201));
    expect(scm.controls.repoSlug.hasError('maxlength')).toBe(true);
  });

  it('keeps the deployment names whatever the target, since the library reads them for both', () => {
    const form = createServiceForm(
      service({
        deployment: {
          target: 'VM',
          appName: 'gui',
          artifactName: 'gui.jar',
          baseArtifactName: null,
          nexusDeliveryDisabled: false,
        },
      }),
    );

    expect(toServiceRequest(form).deployment).toEqual({
      target: 'VM',
      appName: 'gui',
      artifactName: 'gui.jar',
      baseArtifactName: null,
      nexusDeliveryDisabled: false,
    });
  });

  it('sends the commands and sections that do not apply empty, keeping the JDK of Flutter', () => {
    const form = createServiceForm(service());
    form.controls.build.controls.tool.setValue('FLUTTER');
    form.controls.flutter.patchValue({
      platform: 'APK',
      modules: 'app\npackages/core',
      signingPasswordCredentialsId: 'sign',
      prodLicenseCredentialsId: 'prod',
      testLicenseCredentialsId: 'test',
    });

    const request = toServiceRequest(form);

    expect(request.build.command).toEqual(NO_COMMAND);
    expect(request.build.javaPath).toBe(service().build.javaPath);
    expect(request.build.autoSetup).toBe(false);
    expect(request.unitTests.command).toEqual(NO_COMMAND);
    expect(request.sonar.command).toEqual(NO_COMMAND);
    expect(request.appScan.compileCommand).toEqual(NO_COMMAND);
    expect(request.delivery).toEqual(NO_COMMAND);
    expect(request.flutter).toMatchObject({
      platform: 'APK',
      modules: ['app', 'packages/core'],
      signingPasswordCredentialsId: 'sign',
      deliveryGroup: null,
    });
  });

  it('sends only the targets of the deployment target, without regions that set nothing', () => {
    const form = createServiceForm(service());
    expect(toServiceRequest(form).sshTargets).toEqual({
      QC: {
        host: null,
        user: null,
        deployDir: '/opt/cert/gui',
        deployScript: null,
        versionFile: null,
      },
    });
    expect(toServiceRequest(form).openShiftTargets).toEqual({});

    form.controls.deployment.controls.target.setValue('OPENSHIFT');
    form.controls.openShiftTargets.controls.RD.patchValue({ projectBuild: ' cert-build ' });
    const request = toServiceRequest(form);

    expect(request.sshTargets).toEqual({});
    expect(request.urbanCodeApplications).toEqual([]);
    expect(Object.keys(request.openShiftTargets)).toEqual(['RD']);
    expect(request.openShiftTargets.RD).toMatchObject({
      projectBuild: 'cert-build',
      skipConfigDeploy: false,
      dockerRepoPush: null,
    });
  });
});

describe('values unique within a product', () => {
  function twoServices() {
    const form = createProductForm();
    patchProduct(form, product({ services: [service(), anotherService({ name: 'api' })] }));
    return { form, first: form.controls.services.at(0), second: form.controls.services.at(1) };
  }

  it('flags a name another service already uses on the later service only', () => {
    const { form, first, second } = twoServices();
    expect(form.valid).toBe(true);

    second.controls.name.setValue('GUI');

    expect(second.controls.name.errors).toEqual({ rule: SAME_NAME });
    expect(first.controls.name.valid).toBe(true);
    const statuses: string[] = [];
    second.controls.name.statusChanges.subscribe((status) => statuses.push(status));

    first.controls.name.setValue('web');

    expect(second.controls.name.valid).toBe(true);
    expect(statuses).toEqual(['VALID']);
    expect(form.valid).toBe(true);
  });

  it('lets a service scan one Nexus IQ application, and a legacy second one fails until removed', () => {
    const { form, first } = twoServices();
    const applications = first.controls.nexusIqApplications;
    applications.push(
      createNexusIqApplicationForm({ application: 'cert-gui', scanPatterns: ['*.jar'] }),
    );

    expect(applications.errors).toEqual({ rule: ONE_APPLICATION });
    expect(applications.controls.every((application) => application.valid)).toBe(true);
    expect(form.valid).toBe(false);

    applications.removeAt(1);

    expect(applications.valid).toBe(true);
  });

  it('rejects a product name of spaces only', () => {
    const form = createProductForm();
    form.controls.name.setValue('   ');

    expect(form.controls.name.hasError('required')).toBe(true);
  });

  it('lets services share a metrics tag and a SonarQube key', () => {
    const { form, first, second } = twoServices();
    for (const each of [first, second]) {
      each.controls.metrics.controls.influxProject.setValue('Cert Scanner');
      each.controls.metrics.controls.influxEnv.setValue('test');
      each.controls.sonar.controls.projectKey.setValue('cert-scanner');
    }

    expect(form.valid).toBe(true);
  });

  it('clears a product-wide problem of the API once anything in the product changes', () => {
    const { form, first, second } = twoServices();

    const unmatched = applyProductProblems(form, [
      { field: 'services[1].name', message: SAME_NAME },
      { field: 'services[1].build.javaPath', message: 'is not a JDK' },
      { field: 'serviceTemplate', message: 'is not configured' },
    ]);

    expect(unmatched).toEqual([{ field: 'serviceTemplate', message: 'is not configured' }]);
    expect(second.controls.name.errors).toEqual({ server: SAME_NAME });
    expect(second.controls.build.controls.javaPath.errors).toEqual({ server: 'is not a JDK' });

    first.controls.name.setValue('web');
    revalidateAll(form);

    expect(second.controls.name.valid).toBe(true);
    expect(second.controls.build.controls.javaPath.errors).toEqual({ server: 'is not a JDK' });
    expect(PRODUCT_WIDE_FIELD.test('services[3].name')).toBe(true);
    expect(PRODUCT_WIDE_FIELD.test('services[3].metrics.influxProject')).toBe(false);
  });
});

describe('product form', () => {
  it('upper-cases the product code as it is typed', () => {
    const form = createProductForm();
    const values: string[] = [];
    form.valueChanges.subscribe((value) => values.push(value.code ?? ''));

    form.controls.code.setValue('cert-2');

    expect(form.controls.code.value).toBe('CERT-2');
    expect(form.controls.code.valid).toBe(true);
    expect(values).toEqual(['CERT-2']);
  });

  it('round-trips a stored product into the request the API takes', () => {
    const form = createProductForm();
    patchProduct(form, product({ services: [service(), anotherService({ name: 'backend-api' })] }));

    const request = toProductRequest(form, 3);

    expect(form.valid).toBe(true);
    expect(request).toMatchObject({
      code: 'CERT',
      name: 'CertScanner',
      ownerTeam: 'Technology Architecture',
      departmentId: 3,
      version: 3,
    });
    expect(request).not.toHaveProperty('appScan');
    expect(request).not.toHaveProperty('agentLabels');
    expect(request.services.map((s) => [s.id, s.name])).toEqual([
      [10, 'gui'],
      [11, 'backend-api'],
    ]);
  });

  it('needs the department of a product that is not in one yet', () => {
    const form = createProductForm();
    patchProduct(form, product({ departmentId: null }));

    expect(form.controls.departmentId.hasError('required')).toBe(true);

    form.controls.departmentId.setValue(5);

    expect(form.valid).toBe(true);
    expect(toProductRequest(form, 3).departmentId).toBe(5);
  });

  it('sends the Jenkins agents of a new product only, as a list', () => {
    const form = createProductForm();
    patchProduct(form, product({ services: [] }));

    expect(form.controls.agentLabels.disabled).toBe(true);

    form.controls.agentLabels.enable();
    form.controls.agentLabels.setValue(' linux-agent, linux && docker ,');

    expect(form.valid).toBe(true);
    expect(toProductRequest(form, null).agentLabels).toEqual(['linux-agent', 'linux && docker']);

    form.controls.agentLabels.setValue('a,b');
    form.controls.agentLabels.setValue('');

    expect(form.controls.agentLabels.valid).toBe(true);
    expect(toProductRequest(form, null).agentLabels).toEqual([]);
  });

  it('replaces the services of an earlier product when patched again', () => {
    const form = createProductForm();
    patchProduct(form, product({ services: [service(), anotherService({ name: 'api' })] }));
    patchProduct(
      form,
      product({ description: null, services: [service({ id: 12, name: 'worker' })] }),
    );

    expect(form.controls.services.length).toBe(1);
    expect(form.controls.description.value).toBe('');
  });

  it('duplicates a service as a new one without the values that identify the source', () => {
    const copy = duplicateService(createServiceForm(service()), null);
    const value = copy.getRawValue();

    expect(value.id).toBeNull();
    expect(value.name).toBe('gui-copy');
    expect(value.sonar.projectName).toBe('');
    expect(value.sonar.projectKey).toBe('');
    expect(value.metrics.influxProject).toBe('');
    expect(value.nexusIqApplications).toEqual([
      expect.objectContaining({ application: '', scanPatterns: '**/build/libs/*.jar' }),
    ]);
    expect(value.metrics.influxEnv).toBe('test');
    expect(value.appScan.keyId).toBe('bbh_key');
    expect(value.build.javaPath).toBe('/usr/lib/jvm/java-17-openjdk');
    expect(value.testJobs.map((job) => job.job)).toEqual(['CERT/gui-smoke']);
    expect(value.sshTargets.QC.deployDir).toBe('/opt/cert/gui');
    expect(copy.controls.nexusIqApplications.at(0).controls.application.hasError('required')).toBe(
      true,
    );
  });

  it('gives a copy the Library defaults for what the source inherits', () => {
    const copy = duplicateService(createServiceForm(service()), globalSettings());

    expect(copy.controls.sonar.controls.serverUrl.value).toBe('https://tools.bbh.com/sonar');
    expect(toServiceRequest(copy).sonar.serverUrl).toBeNull();
  });

  it('gives a new service the AppScan key of the first service that has one', () => {
    const form = createServiceForm();
    prefillNewService(form, null, [
      { keyId: ' ', secretCredentialsId: 'unused' },
      { keyId: 'bbh_key ', secretCredentialsId: 'hcl-app-scan-account' },
    ]);

    expect(form.controls.appScan.getRawValue()).toMatchObject({
      keyId: 'bbh_key',
      secretCredentialsId: 'hcl-app-scan-account',
    });

    const typed = createServiceForm({ appScan: { keyId: 'bbh_own' } as never });
    prefillNewService(typed, null, [{ keyId: 'bbh_key', secretCredentialsId: 'secret' }]);

    expect(typed.controls.appScan.controls.keyId.value).toBe('bbh_own');
    expect(typed.controls.appScan.controls.secretCredentialsId.value).toBe('');
  });
});

describe('field problems reported by the API', () => {
  function formWithServices() {
    const form = createProductForm();
    patchProduct(form, product({ services: [service(), anotherService({ name: 'api' })] }));
    return form;
  }

  it('finds the control a field path names', () => {
    const form = formWithServices();
    const second = form.controls.services.at(1).controls;
    expect(controlAt(form, 'services[1].build.javaPath')).toBe(second.build.controls.javaPath);
    expect(controlAt(form, 'services[0].appScan.keyId')).toBe(
      form.controls.services.at(0).controls.appScan.controls.keyId,
    );
    expect(controlAt(form, 'services[1].testJobs[0].job')).toBe(second.testJobs.at(0).controls.job);
  });

  it('shows each problem on its control and returns those without one', () => {
    const form = formWithServices();
    const unmatched = applyFieldProblems(form, [
      { field: 'services[1].name', message: 'is already used by another service' },
      { field: 'version', message: 'is stale' },
    ]);

    const name = form.controls.services.at(1).controls.name;
    expect(name.errors).toEqual({ server: 'is already used by another service' });
    expect(name.touched).toBe(true);
    expect(unmatched).toEqual([{ field: 'version', message: 'is stale' }]);

    name.setValue('api-2');
    expect(name.valid).toBe(true);
  });

  it('opens the first service holding a problem', () => {
    expect(
      firstServiceWithProblem([
        { field: 'name', message: 'x' },
        { field: 'services[3].name', message: 'x' },
        { field: 'services[1].sonar.projectKey', message: 'x' },
      ]),
    ).toBe(1);
    expect(firstServiceWithProblem([{ field: 'code', message: 'x' }])).toBeNull();
  });
});

describe('followToolDefaults', () => {
  it('swaps the template build for that of the new build tool unless it was changed', () => {
    const form = createServiceForm();
    const build = form.controls.build.controls;
    build.tool.setValue('GRADLE');
    build.buildPath.setValue('build/libs/*.jar');
    build.command.controls.tasks.setValue('clean build');
    TestBed.runInInjectionContext(() => followToolDefaults(form, serviceTemplate()));
    const values = () => [build.command.controls.tasks.value, build.buildPath.value];

    build.tool.setValue('MAVEN');
    expect(values()).toEqual(['clean verify', 'target/*.jar']);

    build.command.controls.tasks.setValue('clean package');
    build.buildPath.setValue('');
    build.tool.setValue('GRADLE');
    expect(values()).toEqual(['clean package', 'build/libs/*.jar']);
  });
});
