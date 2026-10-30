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
import { DsoHelp } from '@common/ui/help';
import {
  ServiceForm,
  UrbanCodeApplicationForm,
  createUrbanCodeApplicationForm,
  createUrbanCodeComponentForm,
} from './product-form-model';

const HELP = {
  deployWithSnapshot:
    'Deploys the snapshot the run creates for the application instead of the individual component versions.',
  includeOnlyDeployVersions:
    'Cleared, the snapshot the run creates also records the component versions already in the environment.',
  updateSnapshotComponents:
    'Lets UrbanCode Deploy update the component versions of an existing snapshot of the same name.',
  deployOnlyChanged:
    'Deploys only the component versions that are not yet installed in the environment.',
  skipWait:
    'The stage ends once the deployment is requested, without waiting for UrbanCode Deploy to finish it.',
  requestProperties:
    'Properties passed to the deployment process request, one NAME=value per line.',
};

const SWITCHES: Field[] = [
  check('deployWithSnapshot', 'Deploy with a snapshot', 'deployWithSnapshot', 6, {
    help: HELP.deployWithSnapshot,
  }),
  check(
    'includeOnlyDeployVersions',
    'Snapshot holds only the deployed versions',
    'includeOnlyDeployVersions',
    6,
    { help: HELP.includeOnlyDeployVersions },
  ),
  check('updateSnapshotComponents', "Update the snapshot's components", 'updateSnapshotComp', 6, {
    help: HELP.updateSnapshotComponents,
  }),
  check('deployOnlyChanged', 'Deploy only changed versions', 'deployOnlyChanged', 6, {
    help: HELP.deployOnlyChanged,
  }),
];

const TEXTS: Field[] = [
  check('skipWait', 'Do not wait for the deployment result', 'skipWait', 12, {
    help: HELP.skipWait,
  }),
  area('deployDescription', 'Deployment description', 'deploy.vm.dod.deployDescription', 6, {
    help: "The description of the deployment request in UrbanCode Deploy. An application's own description takes precedence.",
  }),
  area('requestProperties', 'Request properties', 'deploy.vm.dod.requestProperties', 6, {
    mono: true,
    help: HELP.requestProperties,
  }),
];

const SERVICE_SETTING = 'left empty: the setting above';

const threeState = (key: keyof typeof HELP, label: string, code: string) =>
  choice(key, label, tristate('Setting above', 'Yes', 'No'), code, 4, { help: HELP[key] });

export const URBAN_CODE_APPLICATION: Field[] = [
  mono('applicationName', 'Application name', 'applicationName', 5, {
    help: 'The UrbanCode Deploy application that receives the components and is deployed.',
  }),
  count('order', 'Order', 'order', 2, {
    help: 'Applications are deployed in ascending order of this number; those without one come last.',
  }),
  mono('environments', 'Environments', 'environments', 5, {
    placeholder: 'DV, RD',
    hint: 'left empty: all of them',
    help: 'The UrbanCode Deploy environments the application is deployed to, separated by commas. The Full pipeline deploys only to DV.',
  }),
  line('snapshotName', 'Snapshot name', 'snapshotName', 4, {
    help: 'Names the snapshot each run creates, followed by _ and the build ID. Left empty, the application name followed by _snapshot is used.',
  }),
  line('siteName', 'Site name', 'siteName', 4, {
    hint: SERVICE_SETTING,
    help: 'The UrbanCode Deploy site configured in Jenkins that this application uses.',
  }),
  line('deployProcess', 'Deployment process', 'deployProcess', 4, {
    hint: SERVICE_SETTING,
    help: 'The application process UrbanCode Deploy runs to deploy this application.',
  }),
  threeState('deployWithSnapshot', 'Deploy with a snapshot', 'deployWithSnapshot'),
  threeState(
    'includeOnlyDeployVersions',
    'Snapshot holds only the deployed versions',
    'includeOnlyDeployVersions',
  ),
  threeState('updateSnapshotComponents', "Update the snapshot's components", 'updateSnapshotComp'),
  threeState('deployOnlyChanged', 'Deploy only changed versions', 'deployOnlyChanged'),
  threeState('skipWait', 'Do not wait for the result', 'skipWait'),
  area('description', 'Description', 'description', 4, {
    help: 'A description of the application. The deployment request uses it when no deployment description is given.',
  }),
  area('deployDescription', 'Deployment description', 'deployDescription', 6, {
    hint: 'left empty: the description, then the setting above',
    help: 'The description of the deployment request of this application in UrbanCode Deploy.',
  }),
  area('requestProperties', 'Request properties', 'requestProperties', 6, {
    mono: true,
    hint: SERVICE_SETTING,
    help: HELP.requestProperties,
  }),
];

export const URBAN_CODE_COMPONENT: Field[] = [
  mono('componentName', 'Component name', 'componentName', 4, {
    help: 'The UrbanCode Deploy component the files are published to as a new version.',
  }),
  mono('baseDir', 'Base folder', 'baseDir', 4, {
    placeholder: 'build/libs',
    help: 'The workspace folder the component files are taken from. The deployment stops when it does not exist.',
  }),
  mono('versionPrefix', 'Version prefix', 'versionPrefix', 2, {
    help: 'Starts the name of the new version, followed by _ and the build ID. Left empty, the pattern of the files to include is used.',
  }),
  mono('version', 'Version', 'version', 2, {
    help: 'A fixed name for the new component version, used instead of the prefix and the build ID.',
  }),
  mono('fileIncludePatterns', 'Files to include', 'fileIncludePatterns', 6, {
    placeholder: '*.jar',
    help: 'A file name pattern for the files under the base folder that are published. The deployment stops when no file matches.',
  }),
  mono('fileExcludePatterns', 'Files to exclude', 'fileExcludePatterns', 6, {
    help: 'The files under the base folder that are left out of the new version.',
  }),
  mono('extensions', 'Extensions', 'extensions', 4, {
    placeholder: 'jar,war',
    help: 'File extensions passed to the UrbanCode Deploy push of the version, separated by commas.',
  }),
  mono('charset', 'Charset', 'charset', 2, {
    placeholder: 'UTF-8',
    help: 'The character set passed to the UrbanCode Deploy push of the version.',
  }),
  line('pushDescription', 'Push description', 'pushDescription', 6, {
    help: 'Passed to UrbanCode Deploy as the description of the push.',
  }),
  line('versionDescription', 'Version description', 'versionDescription', 6, {
    help: 'Passed to UrbanCode Deploy as the description of the new component version.',
  }),
  area('versionProperties', 'Version properties', 'versionProperties', 6, {
    mono: true,
    help: 'Properties set on the new component version, one NAME=value per line.',
  }),
  check('incrementalVersion', 'Incremental version', 'incrementalVersion', 12, {
    help: 'Publishes an incremental version, which holds part of the component, instead of a full version.',
  }),
];

@Component({
  selector: 'dso-urban-code-fields',
  imports: [ReactiveFormsModule, Fields, DsoHelp],
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

  protected readonly applicationFields = URBAN_CODE_APPLICATION;
  protected readonly componentFields = URBAN_CODE_COMPONENT;

  protected settingsFields(): Field[] {
    const deployment = this.defaults()?.deployment;
    const missing = 'left empty: the Library default';
    const c = this.form().controls.urbanCode.controls;
    return [
      line('siteName', 'Site name', 'deploy.vm.dod.siteName', 6, {
        ...defaulted(deployment?.urbanCodeSiteName, c.siteName.value, missing),
        help: 'The UrbanCode Deploy site configured in Jenkins that receives the components and runs the deployments.',
      }),
      line('deployProcess', 'Deployment process', 'deploy.vm.dod.deployProcess', 6, {
        ...defaulted(deployment?.urbanCodeDeployProcess, c.deployProcess.value, missing),
        help: 'The application process UrbanCode Deploy runs to deploy the applications.',
      }),
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
