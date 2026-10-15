import {
  AppScanAccount,
  BuildTool,
  DeployTarget,
  OpenShiftTarget,
  PIPELINE_TYPES,
  Pipeline,
  PipelineType,
  Product,
  ProductRequest,
  Region,
  Service,
  ServiceDefaults,
  ServicePipelines,
  ServiceTemplateValues,
  pipelineTypeLabel,
  pipelineTypeSlug,
} from '../core/models';
import { RETRY, UNREACHABLE } from '@common/core/errors';
import {
  ServiceForm,
  createNexusIqApplicationForm,
  createServiceForm,
  toServiceRequest,
} from '../products/product-form-model';
import { Choice } from '../shared/choice-tiles';
import { buildDefaults, fillTemplate } from '../shared/service-template';
import { FieldProblem } from '@common/core/models';

export type WizardPipeline = Extract<PipelineType, 'SAST' | 'NEXUS_IQ' | 'SECURITY' | 'FULL'>;

export interface PipelineChoice extends Choice<WizardPipeline> {
  name: string;
}

export const PIPELINES: readonly PipelineChoice[] = [
  {
    value: 'SAST',
    name: 'SAST',
    label: 'SAST (Static Application Security Tests) - HCL AppScan',
  },
  {
    value: 'NEXUS_IQ',
    name: 'OSA',
    label: 'OSA (Open Source Analysis) (NexusIQ with Golden Fix and Golden Pull Request)',
  },
  {
    value: 'SECURITY',
    name: 'Security',
    label: 'Security',
    description: 'Unit Tests, NexusIQ, SAST, SonarQube',
  },
  {
    value: 'FULL',
    name: 'Full',
    label: 'Full',
    description:
      'Static Security (unit test, NexusIQ, SAST, SonarQube) + Extended (lower test region deployment, regression, performance, smoke, DAST, *higher test region deployment)',
  },
];

export const TOOLS: readonly Choice<BuildTool>[] = [
  { value: 'GRADLE', label: 'Gradle', description: 'The code has a build.gradle file' },
  { value: 'MAVEN', label: 'Maven', description: 'The code has a pom.xml file' },
];

const TOOL_NAMES: readonly Choice<BuildTool>[] = [
  ...TOOLS,
  { value: 'FLUTTER', label: 'Flutter', description: 'The code has a pubspec.yaml file' },
];

export function toolName(tool: BuildTool): string {
  return choiceLabel(TOOL_NAMES, tool);
}

export const TARGETS: readonly Choice<DeployTarget>[] = [
  {
    value: 'VM',
    label: 'Virtual machines',
    description: 'Installed on BBH servers with UrbanCode Deploy',
  },
  {
    value: 'OPENSHIFT',
    label: 'OpenShift',
    description: 'Runs as a container on the BBH OpenShift platform',
  },
];

export type ProductMode = 'new' | 'existing';

export const PRODUCT_MODES: readonly Choice<ProductMode>[] = [
  { value: 'new', label: 'A new product', description: 'It is not in the portal yet' },
  {
    value: 'existing',
    label: 'A product in the portal',
    description: 'Add a pipeline or change its services',
  },
];

export const OPENSHIFT_PROJECT = /^[a-z0-9]([-a-z0-9]{0,48}[a-z0-9])?$/;

export function pipelineLabel(pipeline: WizardPipeline): string {
  return PIPELINES.find((option) => option.value === pipeline)!.name;
}

function typeLabel(type: PipelineType): string {
  return PIPELINES.find((option) => option.value === type)?.name ?? pipelineTypeLabel(type);
}

export function pipelineNames(service: ServicePipelines): string {
  const order = (type: PipelineType) => PIPELINE_TYPES.findIndex((option) => option.value === type);
  return service.pipelines
    .map((pipeline) => pipeline.type)
    .sort((one, other) => order(one) - order(other))
    .map(typeLabel)
    .join(', ');
}

function coverage(services: readonly ServicePipelines[], type: PipelineType): string {
  const count = services.filter((service) =>
    service.pipelines.some((pipeline) => pipeline.type === type),
  ).length;
  if (!count) {
    return 'No service has it yet';
  }
  return count === services.length
    ? 'Every service has it'
    : `${count} of ${services.length} services ${count === 1 ? 'has' : 'have'} it`;
}

export function pipelineChoices(
  services: readonly ServicePipelines[] | null,
): readonly PipelineChoice[] {
  return services
    ? PIPELINES.map((option) => ({ ...option, note: coverage(services, option.value) }))
    : PIPELINES;
}

export function choiceLabel<T>(choices: readonly Choice<T>[], value: T): string {
  return choices.find((option) => option.value === value)?.label ?? String(value);
}

export function deploys(pipeline: WizardPipeline): boolean {
  return pipeline === 'SECURITY' || pipeline === 'FULL';
}

export function aPipeline(pipeline: WizardPipeline): string {
  return `${pipeline === 'NEXUS_IQ' ? 'an' : 'a'} ${pipelineLabel(pipeline)} pipeline`;
}

export function preparation(pipeline: WizardPipeline): string[] {
  const needs = [
    'The name of each service and its AppScan application ID, from the Application Security team',
    'Whether each service is built with Gradle or Maven; a developer of the service knows',
  ];
  if (pipeline === 'NEXUS_IQ') {
    return [...needs, 'The Nexus IQ application and the Bitbucket repository of each service'];
  }
  return deploys(pipeline)
    ? [...needs, 'Whether each service runs on virtual machines or on OpenShift']
    : needs;
}

export interface WizardService {
  id: number | null;
  name: string;
  description: string;
  appScanId: string;
  tool: BuildTool;
  target: DeployTarget | null;
  openShiftProject: string;
  nexusIqApplication: string;
  repositoryUrl: string;
}

export interface WizardDefaults {
  tool: BuildTool | null;
  target: DeployTarget | null;
  template: ServiceTemplateValues | null;
  productCode: string;
}

export interface NamedDefaults {
  openShiftProject: string;
  nexusIqApplication: string;
  repositoryUrl: string;
}

export function namedDefaults(defaults: WizardDefaults, serviceName: string): NamedDefaults {
  const { template, productCode } = defaults;
  const filled = (pattern: string | null | undefined) =>
    productCode && serviceName ? fillTemplate(pattern, productCode, serviceName) : '';
  return {
    openShiftProject: filled(template?.openShiftProject).toLowerCase(),
    nexusIqApplication: filled(template?.nexusIqApplication),
    repositoryUrl: filled(template?.repositoryUrl),
  };
}

const nexusIqApplicationOf = (service?: Service) =>
  service?.nexusIqApplications[0]?.application ?? '';

const repositoryOf = (service?: Service) => service?.scm.repositoryUrl ?? '';

export function fromService(service: Service): WizardService {
  return {
    id: service.id,
    name: service.name,
    description: service.description ?? '',
    appScanId: service.appScan.applicationId,
    tool: service.build.tool,
    target: service.deployment.target,
    openShiftProject: '',
    nexusIqApplication: nexusIqApplicationOf(service),
    repositoryUrl: repositoryOf(service),
  };
}

export function deploysWith(pipeline: WizardPipeline, service: WizardService): DeployTarget | null {
  return !deploys(pipeline) && service.id === null ? 'VM' : service.target;
}

function place(target: DeployTarget | null): string {
  return target === 'VM' ? 'virtual machines' : choiceLabel(TARGETS, target);
}

function runsOn(target: DeployTarget | null, project: string): string {
  return `runs on ${place(target)}${project ? ` in project ${project}` : ''}`;
}

function sentence(text: string): string {
  return `${text.charAt(0).toUpperCase()}${text.slice(1)}.`;
}

export function serviceSummary(pipeline: WizardPipeline, service: WizardService): string {
  const target = deploysWith(pipeline, service);
  const parts = [`built with ${toolName(service.tool)}`];
  if (target && (deploys(pipeline) || service.id !== null)) {
    parts.push(runsOn(target, target === 'OPENSHIFT' ? service.openShiftProject : ''));
  }
  if (pipeline === 'NEXUS_IQ' && service.nexusIqApplication) {
    parts.push(`Nexus IQ application ${service.nexusIqApplication}`);
  }
  return sentence(parts.join(', '));
}

export function changesOf(
  pipeline: WizardPipeline,
  service: WizardService,
  stored: Service,
): string[] {
  const changes: string[] = [];
  if (service.name !== stored.name) {
    changes.push(`renamed from ${stored.name}`);
  }
  if (service.description !== (stored.description ?? '')) {
    changes.push(service.description ? 'new description' : 'no description');
  }
  if (service.appScanId.toLowerCase() !== stored.appScan.applicationId.toLowerCase()) {
    changes.push('new AppScan application ID');
  }
  if (service.tool !== stored.build.tool) {
    changes.push(
      `built with ${toolName(service.tool)} instead of ${toolName(stored.build.tool)}, with the default build settings`,
    );
  }
  if (service.target !== stored.deployment.target) {
    changes.push(
      `${runsOn(service.target, service.openShiftProject)} instead of ${place(stored.deployment.target)}, with the default deployment settings`,
    );
  }
  if (pipeline === 'NEXUS_IQ' && service.nexusIqApplication !== nexusIqApplicationOf(stored)) {
    changes.push('new Nexus IQ application');
  }
  if (pipeline === 'NEXUS_IQ' && service.repositoryUrl !== repositoryOf(stored)) {
    changes.push('new Bitbucket repository');
  }
  return changes;
}

export type ReviewTag = 'New' | 'Changed' | 'Removed';

export interface ReviewEntry {
  name: string;
  tag: ReviewTag | null;
  text: string;
}

function reviewEntry(
  pipeline: WizardPipeline,
  service: WizardService,
  stored: readonly Service[],
  removed: readonly number[],
  current: readonly ServicePipelines[] | null,
): ReviewEntry {
  const name = service.name;
  const before = stored.find((candidate) => candidate.id === service.id);
  if (!before) {
    return {
      name,
      tag: 'New',
      text: `Added with ${aPipeline(pipeline)} and its own key. ${serviceSummary(pipeline, service)}`,
    };
  }
  if (removed.includes(before.id)) {
    return { name, tag: 'Removed', text: 'Deleted, with its pipelines and their keys.' };
  }
  const has = !!current?.some(
    (candidate) =>
      candidate.serviceId === before.id &&
      candidate.pipelines.some((existing) => existing.type === pipeline),
  );
  const changes = changesOf(pipeline, service, before);
  if (!changes.length) {
    return {
      name,
      tag: null,
      text: has
        ? `Already has ${aPipeline(pipeline)}; nothing changes.`
        : `Gets ${aPipeline(pipeline)} with its own key; nothing else changes.`,
    };
  }
  const pipelineText = has
    ? `Keeps its ${pipelineLabel(pipeline)} pipeline.`
    : `Gets ${aPipeline(pipeline)} with its own key.`;
  return { name, tag: 'Changed', text: `${pipelineText} ${sentence(changes.join('; '))}` };
}

export function reviewEntries(
  pipeline: WizardPipeline,
  services: readonly WizardService[],
  stored: readonly Service[],
  removed: readonly number[],
  current: readonly ServicePipelines[] | null,
): ReviewEntry[] {
  return services.map((service) => reviewEntry(pipeline, service, stored, removed, current));
}

type TargetText = Partial<Omit<Record<keyof OpenShiftTarget, string>, 'skipConfigDeploy'>>;

const dockerContext = (tool: BuildTool) => (tool === 'MAVEN' ? 'target/docker' : 'build/docker');

export function openShiftTargets(
  project: string,
  service: string,
  tool: BuildTool,
  template: ServiceTemplateValues | null,
): Record<Region, TargetText> {
  const registry = template?.imageRegistry;
  const image = `${registry ? `${registry}/` : ''}${project}/${service.toLowerCase()}`;
  const shared = {
    dockerRepoPull: image,
    deployConfigPath: 'openshift/deployment.yaml',
    healthCheckUrl: template?.healthCheckUrl ?? '',
  };
  return {
    RD: {
      ...shared,
      projectBuild: `${project}-build`,
      buildConfigPath: 'openshift/buildconfig.yaml',
      dockerFilePath: 'openshift/Dockerfile',
      buildContext: dockerContext(tool),
      dockerRepoPush: image,
      certDir: '/etc/pki/openshift',
      nexusAuthFile: '/home/jenkins/.docker/nexus-auth.json',
      projectDeployment: `${project}-rd`,
      configPath: 'openshift/config-rd.yaml',
    },
    QC: { ...shared, projectDeployment: `${project}-qc`, configPath: 'openshift/config-qc.yaml' },
  };
}

function resets(pipeline: WizardPipeline, service: WizardService, existing?: Service) {
  const target = deploysWith(pipeline, service) ?? 'VM';
  return {
    target,
    retooled: existing?.build.tool !== service.tool,
    moved: existing?.deployment.target !== target,
  };
}

export function takesDefaults(
  pipeline: WizardPipeline,
  service: WizardService,
  existing?: Service,
): boolean {
  const { retooled, moved } = resets(pipeline, service, existing);
  return retooled || moved;
}

export function serviceRequest(
  service: WizardService,
  pipeline: WizardPipeline,
  template: ServiceTemplateValues | null,
  existing?: Service,
  defaults: ServiceDefaults | null = null,
) {
  const { target, retooled, moved } = resets(pipeline, service, existing);
  const openShift = target === 'OPENSHIFT';
  const build = buildDefaults(template, service.tool);
  const form = createServiceForm(
    existing && {
      ...existing,
      build: retooled ? undefined : existing.build,
      delivery: retooled || moved ? undefined : existing.delivery,
      deployment: moved ? undefined : existing.deployment,
      openShiftTargets: moved ? undefined : existing.openShiftTargets,
    },
    defaults,
  );
  form.patchValue({
    name: service.name,
    description: service.description,
    appScan: { applicationId: service.appScanId },
  });
  if (retooled) {
    form.patchValue({
      build: {
        tool: service.tool,
        autoSetup: true,
        command: { tasks: build.tasks },
      },
    });
  }
  if (moved) {
    form.patchValue({
      deployment: {
        target,
        appName: openShift ? service.name : '',
        artifactName: openShift ? `${service.name}.jar` : '',
      },
      openShiftTargets: openShift
        ? openShiftTargets(service.openShiftProject, service.name, service.tool, template)
        : {},
    });
  }
  if (retooled || moved) {
    const { buildPath } = form.controls.build.controls;
    form.patchValue({ delivery: { tasks: template?.deliveryTasks ?? '' } });
    if (!buildPath.value.trim()) {
      buildPath.setValue(build.artifact);
    }
    if (openShift) {
      form.controls.openShiftTargets.controls.RD.controls.buildContext.setValue(
        dockerContext(service.tool),
      );
    }
  }
  if (pipeline === 'NEXUS_IQ') {
    scanWithNexusIq(form, service, build.scanPattern, template, existing);
  }
  return toServiceRequest(form);
}

function scanWithNexusIq(
  form: ServiceForm,
  service: WizardService,
  scanPattern: string,
  template: ServiceTemplateValues | null,
  existing?: Service,
): void {
  const applications = form.controls.nexusIqApplications;
  if (service.nexusIqApplication !== nexusIqApplicationOf(existing)) {
    if (applications.length) {
      applications.controls[0].controls.application.setValue(service.nexusIqApplication);
    } else {
      applications.push(
        createNexusIqApplicationForm({
          application: service.nexusIqApplication,
          scanPatterns: scanPattern ? [scanPattern] : [],
        }),
      );
    }
  }
  const scm = form.controls.scm.controls;
  if (service.repositoryUrl !== repositoryOf(existing)) {
    scm.repositoryUrl.setValue(service.repositoryUrl);
    if (!scm.credentialsId.value.trim()) {
      scm.credentialsId.setValue(template?.bitbucketCredentialsId ?? '');
    }
  }
}

export interface NewProduct {
  departmentId: number | null;
  name: string;
  code: string;
  ownerTeam: string;
  contactEmail: string;
  appScanKeyId: string;
}

export function appScanAccount(keyId: string): AppScanAccount {
  return { keyId: keyId.trim(), secretCredentialsId: null };
}

export function productRequest(
  product: NewProduct | Product,
  services: readonly WizardService[],
  pipeline: WizardPipeline,
  departmentId: number | null = null,
  template: ServiceTemplateValues | null = null,
  defaults: ServiceDefaults | null = null,
): ProductRequest {
  const stored = 'id' in product ? product : null;
  const requests = services.map((service) =>
    serviceRequest(
      service,
      pipeline,
      template,
      stored?.services.find((candidate) => candidate.id === service.id),
      defaults,
    ),
  );
  if (stored) {
    return {
      code: stored.code,
      name: stored.name,
      description: stored.description,
      ownerTeam: stored.ownerTeam,
      contactEmail: stored.contactEmail,
      departmentId: stored.departmentId ?? departmentId,
      appScan: stored.appScan,
      version: stored.version,
      services: requests,
    };
  }
  const fresh = product as NewProduct;
  return {
    code: fresh.code,
    name: fresh.name.trim(),
    description: null,
    ownerTeam: fresh.ownerTeam.trim() || null,
    contactEmail: fresh.contactEmail.trim() || null,
    departmentId: fresh.departmentId,
    appScan: appScanAccount(fresh.appScanKeyId),
    version: null,
    services: requests,
  };
}

const FIELD_NAMES: Record<string, string> = {
  name: 'name',
  'appScan.applicationId': 'AppScan application ID',
  'appScan.keyId': 'AppScan API key ID',
  'nexusIqApplications.application': 'Nexus IQ application',
  'nexusIqApplications.scanPatterns': 'Nexus IQ scan patterns',
  'scm.repositoryUrl': 'Bitbucket repository',
  'scm.credentialsId': 'Bitbucket credentials ID',
  code: 'product code',
};

const fieldName = (field: string) => FIELD_NAMES[field.replace(/\[\d+]/g, '')] ?? field;

export function problemText(problem: FieldProblem, services: readonly WizardService[]): string {
  const match = /^services\[(\d+)]\.(.+)$/.exec(problem.field);
  if (!match) {
    return `${fieldName(problem.field)}: ${problem.message}`;
  }
  const service = services[Number(match[1])]?.name ?? 'A service';
  return `${service}, ${fieldName(match[2])}: ${problem.message}`;
}

export function notLoaded(what: string, reason: string, reloadable = false): string {
  const told = reloadable || reason.includes(RETRY) || reason === UNREACHABLE;
  const ended = /[.!?]$/.test(reason) ? reason : `${reason}.`;
  return `${what} could not be loaded. ${ended}${told ? '' : ` ${RETRY}`}`;
}

export interface ServiceStart {
  serviceName: string;
  repository: string;
  pipeline: Pipeline | null;
}

export function servicesToStart(
  services: readonly ServicePipelines[],
  pipeline: WizardPipeline,
  product: Product,
): ServiceStart[] {
  return services.map((service) => ({
    serviceName: service.serviceName,
    repository: repositoryOf(product.services.find((stored) => stored.id === service.serviceId)),
    pipeline: service.pipelines.find((candidate) => candidate.type === pipeline) ?? null,
  }));
}

export function jobName(pipeline: Pipeline): string {
  return (
    pipeline.jenkinsJob ??
    `DevSecOps/${pipeline.productCode}/${pipeline.serviceName}-${pipelineTypeSlug(pipeline.type)}`
  );
}
