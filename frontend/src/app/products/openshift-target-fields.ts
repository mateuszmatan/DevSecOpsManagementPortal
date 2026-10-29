import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { REGIONS, Region } from '../core/models';
import { Field, Fields, check, formRevision, mono } from '@common/shared/fields';
import { IMAGE_TAG_ERROR, SHELL_SAFE_ERROR } from '@common/shared/form-controls';
import { TOGGLES } from '@common/ui/toggle-group';
import { OPENSHIFT_IMAGE_DELIVERY, OpenShiftTargetForm, ServiceForm } from './product-form-model';

const SAFE = { error: SHELL_SAFE_ERROR };
const TAG = { error: IMAGE_TAG_ERROR };

export const IMAGE_BUILD: Field[] = [
  mono('projectBuild', 'Build project', 'projectBuildR', 4, {
    help: 'The OpenShift project the image is built in.',
  }),
  mono('buildConfigPath', 'BuildConfig file', 'buildConfigPath', 4, {
    placeholder: 'openshift/buildconfig.yaml',
    help: 'The BuildConfig template in the repository, used while the build project has no BuildConfig for the application.',
    ...SAFE,
  }),
  mono('dockerFilePath', 'Dockerfile', 'dockerFilePath', 4, {
    placeholder: 'openshift/Dockerfile',
    help: 'The Dockerfile in the repository the image is built from.',
    ...SAFE,
  }),
  mono('buildContext', 'Build context', 'buildContext', 6, {
    placeholder: 'target/docker',
    help: 'The folder sent to OpenShift for the image build. The built file is copied into it.',
    ...SAFE,
  }),
  mono('addFile', 'File added to the image', 'addFile', 6, {
    help: 'A further file of the repository that is copied into the build context.',
    ...SAFE,
  }),
];

const REGISTRY: Field[] = [
  mono('dockerRepoPush', 'Image pushed to', 'qcDockerRepoPush', 6, {
    help: 'The Nexus image repository the built image is copied to, tagged with the build tag, the build number and latest.',
    ...SAFE,
  }),
  mono('dockerRepoPull', 'Image pulled from', 'qcDockerRepoPull', 6, {
    help: 'The image repository the deployment takes the image from, with the build tag.',
  }),
  mono('certDir', 'OpenShift certificates folder', 'openshiftCertDir', 6, {
    help: 'The folder on the Jenkins agent with the certificates of the OpenShift registry.',
    ...SAFE,
  }),
  mono('nexusAuthFile', 'Nexus auth file', 'nexus.authfile', 6, {
    help: 'The registry authentication file on the Jenkins agent used to push the image to Nexus.',
    ...SAFE,
  }),
];

const DEPLOYMENT: Field[] = [
  mono('projectDeployment', 'Deployment project', 'projectDeploymentR', 4, {
    help: 'The OpenShift project the service is deployed to in this region.',
  }),
  mono('deployConfigPath', 'Deployment file', 'deployConfigPath', 4, {
    placeholder: 'openshift/deployment.yaml',
    help: 'The deployment template in the repository, applied in the deployment project.',
  }),
  mono('configPath', 'Configuration file', 'configPathR', 4, {
    help: 'The configuration template in the repository, applied before the deployment.',
  }),
  mono('healthCheckUrl', 'Health check path', 'healthCheckUrl', 4, {
    placeholder: '/actuator/health',
    help: 'Passed to the deployment template as HEALTH_URL.',
  }),
  mono('routeHostname', 'Route host name', 'routeHostnameR', 4, {
    help: 'Passed to the deployment template as ROUTE_HOST.',
  }),
  mono('deploymentPath', 'Deployment path', 'deploymentPath', 4, {
    help: 'RD only: the folder the deployment repository is cloned into.',
  }),
  check('skipConfigDeploy', 'Skip deploying the configuration', 'skipConfigDeploy', 12, {
    help: 'Deploys the image without applying the configuration template.',
  }),
];

const REPOSITORY: Field[] = [
  mono('deploymentRepoUrl', 'Repository URL', 'deploymentRepo.url', 6, {
    help: 'RD only: a repository with deployment files, cloned into the deployment path.',
    error: 'Must be an http, https, ssh or git@ URL',
  }),
  mono('deploymentRepoBranch', 'Branch', 'deploymentRepo.branch', 3, {
    help: 'The branch of the deployment repository.',
  }),
  mono('deploymentRepoCredentialsId', 'Credentials ID', 'deploymentRepo.credentials', 3, {
    help: 'The ID of the Jenkins credential that reads the deployment repository.',
  }),
];

const buildTag = (help: string) =>
  mono('buildTag', 'Build tag', 'buildTag', 4, { placeholder: '1.4.2-20261006', help, ...TAG });

const PINNED_IMAGE: Record<Region, Field[]> = {
  RD: [
    buildTag(
      'The image tag RD deploys when the run builds no image. A run that builds the image replaces it.',
    ),
    mono('internalDockerUrl', 'Internal image URL', 'internalDockerUrl', 8, {
      help: 'The image in the OpenShift registry that is copied to Nexus.',
      ...TAG,
    }),
  ],
  QC: [
    buildTag(
      'Pins the image tag QC deploys, also when the run builds a new image. Left empty, QC deploys the RD build tag, which is the image the run built.',
    ),
  ],
};

const PINNED_NOTE: Record<Region, { delivered: string; undelivered: string }> = {
  RD: {
    delivered: 'Optional; a run that builds the image replaces these values.',
    undelivered:
      'Nexus delivery is disabled, so the run builds no image: the RD deployment uses this build tag and is skipped without one.',
  },
  QC: {
    delivered: 'Optional; a build tag set here wins over the image the run built.',
    undelivered:
      'Nexus delivery is disabled, so the run builds no image: the QC deployment uses this build tag or, without one, the RD build tag, and is skipped when neither is set.',
  },
};

@Component({
  selector: 'dso-openshift-target-fields',
  imports: [TOGGLES, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  template: `
    <dso-toggle-group class="regions" [(value)]="region" aria-label="Region">
      @for (option of regions; track option) {
        <button [dsoToggle]="option">
          {{ option }} region
          @if (target(option).invalid) {
            <span class="problem-mark" role="img" aria-label="Needs your attention"></span>
          }
        </button>
      }
    </dso-toggle-group>
    <p class="region-note">
      @if (region() === 'RD') {
        The lower test region, where the run builds the image. Keys under
        <code>deploy.openshift.rd</code>.
      } @else {
        The higher test region, which deploys the image the run built. Optional; once one of its
        fields is set, the deployment project, the deployment file, the image source and, unless it
        is skipped, the configuration file are required. Keys under
        <code>deploy.openshift.qc</code>.
      }
    </p>

    @let t = target(region());
    <div class="form-fields">
      @if (region() === 'RD' ? delivers() : hasImageBuild(t)) {
        <h5 class="sub-heading span-12">Image build</h5>
        <dso-fields [group]="t" [fields]="imageBuild" />
      }
      <h5 class="sub-heading span-12">Image registry</h5>
      <dso-fields [group]="t" [fields]="shown(registry, region())" />
      <h5 class="sub-heading span-12">Deployment</h5>
      <dso-fields [group]="t" [fields]="deployment" />
      <h5 class="sub-heading span-12">Deployment repository</h5>
      <dso-fields [group]="t" [fields]="repository" />
      <h5 class="sub-heading span-12">Pinned image</h5>
      <p class="note span-12">{{ pinnedNote(region()) }}</p>
      <dso-fields [group]="t" [fields]="shown(pinnedImage[region()], region())" />
    </div>
  `,
  styles: `
    :host {
      display: block;
    }
    code {
      font-size: 11.5px;
    }
    .regions .problem-mark {
      margin-left: 6px;
      vertical-align: middle;
    }
    .region-note {
      margin: 6px 0 2px;
      font-size: 12px;
      color: var(--dso-muted);
    }
  `,
})
export class OpenShiftTargetFields {
  readonly form = input.required<ServiceForm>();

  private readonly changes = formRevision(this.form);

  protected readonly regions = REGIONS;
  protected readonly region = signal<Region>('RD');
  protected readonly imageBuild = IMAGE_BUILD;
  protected readonly registry = REGISTRY;
  protected readonly deployment = DEPLOYMENT;
  protected readonly repository = REPOSITORY;

  protected target(region: Region): OpenShiftTargetForm {
    this.changes();
    return this.form().controls.openShiftTargets.controls[region];
  }

  protected readonly pinnedImage = PINNED_IMAGE;

  protected pinnedNote(region: Region): string {
    const note = PINNED_NOTE[region];
    return this.delivers() ? note.delivered : note.undelivered;
  }

  protected delivers(): boolean {
    this.changes();
    return !this.form().controls.deployment.controls.nexusDeliveryDisabled.value;
  }

  protected shown(fields: Field[], region: Region): Field[] {
    return region === 'QC' || this.delivers()
      ? fields
      : fields.filter((field) => !OPENSHIFT_IMAGE_DELIVERY.some((key) => key === field.key));
  }

  protected hasImageBuild(target: OpenShiftTargetForm): boolean {
    return IMAGE_BUILD.some((field) => !!target.get(field.key)?.value.trim());
  }
}
