import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { GlobalSettings } from '../core/models';
import { addItem, removeItem } from '@common/shared/form-controls';
import {
  Field,
  Fields,
  area,
  check,
  choice,
  count,
  defaulted,
  formRevision,
  line,
  mono,
  tristate,
} from '@common/shared/fields';
import {
  ServiceForm,
  UrbanCodeApplicationForm,
  createUrbanCodeApplicationForm,
  createUrbanCodeComponentForm,
} from './product-form-model';

const SWITCHES: Field[] = [
  check('deployWithSnapshot', 'Deploy with a snapshot', 'deployWithSnapshot', 6),
  check(
    'includeOnlyDeployVersions',
    'Snapshot holds only the deployed versions',
    'includeOnlyDeployVersions',
    6,
  ),
  check('updateSnapshotComponents', "Update the snapshot's components", 'updateSnapshotComp', 6),
  check('deployOnlyChanged', 'Deploy only changed versions', 'deployOnlyChanged', 6),
];

const TEXTS: Field[] = [
  check('skipWait', 'Do not wait for the deployment result', 'skipWait'),
  area('deployDescription', 'Deployment description', 'deploy.vm.dod.deployDescription'),
  area('requestProperties', 'Request properties', 'deploy.vm.dod.requestProperties', 6, {
    mono: true,
  }),
];

const SERVICE_SETTING = 'left empty: the setting above';

const threeState = (key: string, label: string, code: string) =>
  choice(key, label, tristate('Setting above', 'Yes', 'No'), code, 4);

const APPLICATION: Field[] = [
  mono('applicationName', 'Application name', 'applicationName', 5),
  count('order', 'Order', 'order', 2),
  mono('environments', 'Environments', 'environments', 5, {
    placeholder: 'DV, RD',
    hint: 'left empty: all of them',
  }),
  line('snapshotName', 'Snapshot name', 'snapshotName', 4),
  line('siteName', 'Site name', 'siteName', 4, { hint: SERVICE_SETTING }),
  line('deployProcess', 'Deployment process', 'deployProcess', 4, { hint: SERVICE_SETTING }),
  threeState('deployWithSnapshot', 'Deploy with a snapshot', 'deployWithSnapshot'),
  threeState(
    'includeOnlyDeployVersions',
    'Snapshot holds only the deployed versions',
    'includeOnlyDeployVersions',
  ),
  threeState('updateSnapshotComponents', "Update the snapshot's components", 'updateSnapshotComp'),
  threeState('deployOnlyChanged', 'Deploy only changed versions', 'deployOnlyChanged'),
  threeState('skipWait', 'Do not wait for the result', 'skipWait'),
  area('description', 'Description', 'description', 4),
  area('deployDescription', 'Deployment description', 'deployDescription', 6, {
    hint: 'left empty: the description, then the setting above',
  }),
  area('requestProperties', 'Request properties', 'requestProperties', 6, {
    mono: true,
    hint: SERVICE_SETTING,
  }),
];

const COMPONENT: Field[] = [
  mono('componentName', 'Component name', 'componentName', 4),
  mono('baseDir', 'Base folder', 'baseDir', 4, { placeholder: 'build/libs' }),
  mono('versionPrefix', 'Version prefix', 'versionPrefix', 2),
  mono('version', 'Version', 'version', 2),
  mono('fileIncludePatterns', 'Files to include', 'fileIncludePatterns', 6, {
    placeholder: '*.jar',
  }),
  mono('fileExcludePatterns', 'Files to exclude', 'fileExcludePatterns', 6),
  mono('extensions', 'Extensions', 'extensions', 4, { placeholder: 'jar,war' }),
  mono('charset', 'Charset', 'charset', 2, { placeholder: 'UTF-8' }),
  line('pushDescription', 'Push description', 'pushDescription', 6),
  line('versionDescription', 'Version description', 'versionDescription', 6),
  area('versionProperties', 'Version properties', 'versionProperties', 6, { mono: true }),
  check('incrementalVersion', 'Incremental version', 'incrementalVersion'),
];

@Component({
  selector: 'dso-urban-code-fields',
  imports: [ReactiveFormsModule, Fields],
  changeDetection: ChangeDetectionStrategy.Eager,
  templateUrl: './urban-code-fields.html',
  styles: `
    :host {
      display: block;
    }
    code {
      font-size: 11.5px;
    }
    .component {
      position: relative;
      margin-top: 6px;
      padding: 8px 88px 6px 10px;
      border: 1px solid var(--dso-border);
      background: #fbfcfd;

      .remove {
        position: absolute;
        top: 6px;
        right: 4px;
      }
    }
    .add-component {
      margin-top: 6px;
    }
    @media (max-width: 700px) {
      .component {
        padding: 36px 10px 6px;
      }
    }
  `,
})
export class UrbanCodeFields {
  readonly form = input.required<ServiceForm>();
  readonly defaults = input<GlobalSettings | null>(null);

  private readonly changes = formRevision(this.form);

  protected readonly applications = computed(() => {
    this.changes();
    return [...this.form().controls.urbanCodeApplications.controls];
  });

  protected readonly applicationFields = APPLICATION;
  protected readonly componentFields = COMPONENT;

  protected settingsFields(): Field[] {
    const deployment = this.defaults()?.deployment;
    const global = 'left empty: the global default';
    return [
      line(
        'siteName',
        'Site name',
        'deploy.vm.dod.siteName',
        6,
        defaulted(deployment?.urbanCodeSiteName, global),
      ),
      line(
        'deployProcess',
        'Deployment process',
        'deploy.vm.dod.deployProcess',
        6,
        defaulted(deployment?.urbanCodeDeployProcess, global),
      ),
      ...SWITCHES,
      ...TEXTS,
    ];
  }

  protected addApplication(): void {
    addItem(this.form().controls.urbanCodeApplications, createUrbanCodeApplicationForm());
  }

  protected removeApplication(index: number): void {
    removeItem(this.form().controls.urbanCodeApplications, index);
  }

  protected addComponent(application: UrbanCodeApplicationForm): void {
    addItem(application.controls.components, createUrbanCodeComponentForm());
  }

  protected removeComponent(application: UrbanCodeApplicationForm, index: number): void {
    removeItem(application.controls.components, index);
  }
}
