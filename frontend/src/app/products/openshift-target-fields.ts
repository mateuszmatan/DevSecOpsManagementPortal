import { ChangeDetectionStrategy, Component, input, signal } from '@angular/core';
import { REGIONS, Region } from '../core/models';
import { Field, Fields, check, formRevision, mono } from '@common/shared/fields';
import { IMAGE_TAG_ERROR, SHELL_SAFE_ERROR } from '@common/shared/form-controls';
import { TOGGLES } from '@common/ui/toggle-group';
import { OpenShiftTargetForm, ServiceForm } from './product-form-model';

const SAFE = { error: SHELL_SAFE_ERROR };
const TAG = { error: IMAGE_TAG_ERROR };

export const IMAGE_BUILD: Field[] = [
  mono('projectBuild', 'Build project', 'projectBuildR', 4),
  mono('buildConfigPath', 'BuildConfig file', 'buildConfigPath', 4, {
    placeholder: 'openshift/buildconfig.yaml',
    ...SAFE,
  }),
  mono('dockerFilePath', 'Dockerfile', 'dockerFilePath', 4, {
    placeholder: 'openshift/Dockerfile',
    ...SAFE,
  }),
  mono('buildContext', 'Build context', 'buildContext', 6, {
    placeholder: 'target/docker',
    ...SAFE,
  }),
  mono('addFile', 'File added to the image', 'addFile', 6, SAFE),
];

const REGISTRY: Field[] = [
  mono('dockerRepoPush', 'Image pushed to', 'qcDockerRepoPush', 6, SAFE),
  mono('dockerRepoPull', 'Image pulled from', 'qcDockerRepoPull', 6),
  mono('certDir', 'OpenShift certificates folder', 'openshiftCertDir', 6, SAFE),
  mono('nexusAuthFile', 'Nexus auth file', 'nexus.authfile', 6, SAFE),
];

const DEPLOYMENT: Field[] = [
  mono('projectDeployment', 'Deployment project', 'projectDeploymentR', 4),
  mono('deployConfigPath', 'Deployment file', 'deployConfigPath', 4, {
    placeholder: 'openshift/deployment.yaml',
  }),
  mono('configPath', 'Configuration file', 'configPathR', 4),
  mono('healthCheckUrl', 'Health check path', 'healthCheckUrl', 4, {
    placeholder: '/actuator/health',
  }),
  mono('routeHostname', 'Route host name', 'routeHostnameR', 4),
  mono('deploymentPath', 'Deployment path', 'deploymentPath', 4),
  check('skipConfigDeploy', 'Skip deploying the configuration', 'skipConfigDeploy'),
];

const REPOSITORY: Field[] = [
  mono('deploymentRepoUrl', 'Repository URL', 'deploymentRepo.url', 6, {
    error: 'Must be an http, https, ssh or git@ URL',
  }),
  mono('deploymentRepoBranch', 'Branch', 'deploymentRepo.branch', 3),
  mono('deploymentRepoCredentialsId', 'Credentials ID', 'deploymentRepo.credentials', 3),
];

const PINNED_IMAGE: Field[] = [
  mono('buildTag', 'Build tag', 'buildTag', 4, { placeholder: '1.4.2-20261006', ...TAG }),
  mono('internalDockerUrl', 'Internal image URL', 'internalDockerUrl', 8, TAG),
];

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
        The higher test region, which deploys the image the run built. Keys under
        <code>deploy.openshift.qc</code>.
      }
    </p>

    @let t = target(region());
    <div class="form-fields">
      @if (region() === 'RD' || hasImageBuild(t)) {
        <h5 class="sub-heading span-12">Image build</h5>
        <dso-fields [group]="t" [fields]="imageBuild" />
      }
      <h5 class="sub-heading span-12">Image registry</h5>
      <dso-fields [group]="t" [fields]="registry" />
      <h5 class="sub-heading span-12">Deployment</h5>
      <dso-fields [group]="t" [fields]="deployment" />
      <h5 class="sub-heading span-12">Deployment repository</h5>
      <dso-fields [group]="t" [fields]="repository" />
      <h5 class="sub-heading span-12">Pinned image</h5>
      <p class="note span-12">Optional; a run that builds the image replaces these values.</p>
      <dso-fields [group]="t" [fields]="pinnedImage(region())" />
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

  protected pinnedImage(region: Region): Field[] {
    return region === 'RD' ? PINNED_IMAGE : PINNED_IMAGE.slice(0, 1);
  }

  protected hasImageBuild(target: OpenShiftTargetForm): boolean {
    return IMAGE_BUILD.some((field) => !!target.get(field.key)?.value.trim());
  }
}
