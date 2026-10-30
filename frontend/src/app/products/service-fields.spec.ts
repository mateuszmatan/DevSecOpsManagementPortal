import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AbstractControl, FormArray, FormGroup } from '@angular/forms';
import { BuildTool, DeployTarget, GlobalSettings, Service } from '../core/models';
import { wholeNumber } from '@common/shared/form-controls';
import {
  buttonOf,
  checkboxOf,
  choose,
  fieldOf,
  helpOf,
  inputOf,
  optionsOf,
  selectOf,
  text,
  unexplained,
} from '@common/testing/dom';
import { globalSettings, nodeBuild, service } from '../testing/fixtures';
import {
  BADGE_TWICE,
  NODE_TWICE,
  ONE_APPLICATION,
  ServiceForm,
  createNexusIqApplicationForm,
  createServiceForm,
} from './product-form-model';
import { applyLibraryDefaults } from './library-defaults';
import { ServiceFields } from './service-fields';

describe('ServiceFields', () => {
  let fixture: ComponentFixture<ServiceFields>;
  let form: ServiceForm;

  async function render(
    stored: Partial<Service> | undefined = service(),
    defaults: GlobalSettings | null = globalSettings(),
  ) {
    TestBed.configureTestingModule({ imports: [ServiceFields] });
    form = createServiceForm(stored);
    fixture = TestBed.createComponent(ServiceFields);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('productCode', 'CERT');
    fixture.componentRef.setInput('defaults', defaults);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const pane = () => page().querySelector<HTMLElement>('.pane')!;
  const hints = () => [...pane().querySelectorAll('dso-hint')].map((hint) => text(hint));

  async function open(label: string) {
    const item = [...page().querySelectorAll<HTMLButtonElement>('.rail-item')].find(
      (button) => button.querySelector('span')?.textContent?.trim() === label,
    );
    item!.click();
    await fixture.whenStable();
  }

  const railLabels = () =>
    [...page().querySelectorAll('.rail-item span:not(.problem-mark)')].map(
      (label) => label.textContent?.trim() ?? '',
    );

  const SELECTS = new Set(['tool', 'target', 'stage', 'type', 'authType', 'platform']);

  function breakEveryField(control: AbstractControl, key = ''): void {
    if (control instanceof FormGroup || control instanceof FormArray) {
      Object.entries(control.controls).forEach(([name, child]) => breakEveryField(child, name));
    } else if (control.enabled && !SELECTS.has(key)) {
      if (typeof control.value === 'string') {
        control.setValue('x'.repeat(5001));
      } else if (control.hasValidator(wholeNumber)) {
        control.setValue(1.5);
      }
    }
  }

  function everything(tool: BuildTool, target: DeployTarget): Service {
    const stored = service();
    return service({
      build: { ...stored.build, tool },
      nodeBuild: nodeBuild({ enabled: true }),
      deployment: { ...stored.deployment, target },
      appScan: { ...stored.appScan, compile: true, dastEnabled: true },
      goldenFix: { ...stored.goldenFix, minThreatLevel: 7 },
      testJobs: [
        ...stored.testJobs,
        {
          ...stored.testJobs[0],
          stage: 'REGRESSION',
          name: 'remote',
          type: 'REMOTE',
          job: 'CERT/regression',
          parameters: 'ENV=rd',
          remoteJenkins: 'qa',
          pollIntervalSec: 20,
          tokenCredentialsId: 'qa-trigger-token',
          useCrumbCache: true,
        },
      ],
    });
  }

  const combinations: [BuildTool, DeployTarget][] = [
    ['GRADLE', 'VM'],
    ['MAVEN', 'VM'],
    ['MAVEN', 'OPENSHIFT'],
    ['FLUTTER', 'VM'],
    ['FLUTTER', 'OPENSHIFT'],
  ];

  describe('every section', () => {
    it.each(combinations)(
      'opens each section of a %s service on %s',
      async (tool, target) => {
        await render(everything(tool, target));

        for (const label of railLabels()) {
          await open(label);
          expect(pane().querySelector('h3')?.textContent).toBe(label);
        }
        expect(railLabels()).toContain(target === 'VM' ? 'SSH targets' : 'OpenShift targets');
        expect(railLabels().includes('Flutter')).toBe(tool === 'FLUTTER');
      },
      20_000,
    );

    it.each(combinations)(
      'explains every problem of a %s service on %s',
      async (tool, target) => {
        await render(everything(tool, target));
        breakEveryField(form);
        form.markAllAsTouched();
        fixture.componentRef.setInput('submitted', true);
        await fixture.whenStable();

        const marked = [...page().querySelectorAll('.rail-item.problem span:first-of-type')];
        expect(marked.length).toBeGreaterThan(5);
        for (const label of railLabels()) {
          await open(label);
          const errors = [...pane().querySelectorAll('dso-error')].map((e) =>
            e.textContent?.trim(),
          );
          expect(errors.every((error) => !!error)).toBe(true);
        }
      },
      45_000,
    );

    it.each(combinations)(
      'explains the fields of every section of a %s service on %s with a help text',
      async (tool, target) => {
        await render(everything(tool, target));

        for (const label of railLabels()) {
          await open(label);
          expect(unexplained(pane()), label).toEqual([]);
        }
      },
      45_000,
    );

    it('works without the global settings and reveals the first section with a problem', async () => {
      await render(service(), null);
      for (const label of railLabels()) {
        await open(label);
      }
      expect(fixture.componentInstance.revealFirstProblem()).toBe(false);

      form.controls.sonar.controls.command.controls.tasks.setValue('');
      expect(fixture.componentInstance.revealFirstProblem()).toBe(true);
      await fixture.whenStable();
      expect(page().querySelector('.rail-item.active span')?.textContent).toBe('SonarQube');
    }, 45_000);
  });

  describe('fields that need input', () => {
    it('names them by their labels in the order of the sections, and a rule of a list by its section', async () => {
      const app = service().nexusIqApplications[0];
      await render(service({ nexusIqApplications: [app, { ...app, application: 'cert-gui-2' }] }));
      form.patchValue({
        name: '',
        nodeBuild: { enabled: true, installationName: 'NodeJS-20', nodePath: '/opt/node-20' },
        appScan: { keyId: '' },
      });

      expect(
        fixture.componentInstance.missing().map(({ section, label }) => `${section}: ${label}`),
      ).toEqual([
        'general: Service name',
        'build: Node.js installation',
        'build: Node.js home on the agent',
        'appScan: AppScan API key ID',
        'nexusIq: Nexus IQ',
      ]);
    });

    it('opens the section of one, shows its error and puts the cursor in it, under Advanced settings too', async () => {
      await render();
      form.controls.build.controls.command.controls.label.setValue('x'.repeat(201));
      const [missing] = fixture.componentInstance.missing();

      expect(missing.label).toBe('Step label');

      fixture.componentInstance.reveal(missing);
      await fixture.whenStable();

      expect(pane().querySelector('h3')?.textContent).toBe('Build');
      expect(text(fieldOf(pane(), 'Step label')?.querySelector('dso-error'))).not.toBe('');
      expect(document.activeElement).toBe(inputOf(pane(), 'Step label'));
    });
  });

  describe('Build', () => {
    const field = (label: string) => fieldOf(pane(), label)!;

    it('starts with the build tool and how to tell it from the code, and ends with Node.js', async () => {
      await render(service({ build: { ...service().build, autoSetup: true, javaPath: null } }));
      await open('Build');
      const shown = () =>
        [...pane().querySelectorAll('dso-form-field dso-label, dso-checkbox label, h4')]
          .filter((label) => !label.closest('dso-advanced-settings'))
          .map((label) => text(label).replace(/ \(\S+\)$/, ''));

      expect(shown()).toEqual([
        'Build tool',
        'Gradle tasks',
        'Artifact path',
        'Set up the build tool automatically',
        'Node.js build (stage 0)',
        'Build the frontend with Node.js first',
      ]);
      expect(hints()[0]).toBe('The code has a build.gradle file · buildTool');

      form.controls.build.controls.tool.setValue('MAVEN');
      await fixture.whenStable();

      expect(hints()[0]).toBe('The code has a pom.xml file · buildTool');
    });

    it('tells a service in a subfolder of its repository where to give that folder', async () => {
      await render();
      await open('Build');

      expect(text(pane().querySelector('.pane-header p'))).toContain(
        'A service in a subfolder of its repository names that folder under Advanced settings.',
      );
      ['Source folder', 'Directory'].forEach((label) =>
        expect(text(field(label).querySelector('dso-hint')).toLowerCase(), label).toContain(
          'for a service in a subfolder: that folder',
        ),
      );
    });

    it('asks for the JDK path without automatic setup and always for a Flutter build', async () => {
      await render(service({ build: { ...service().build, autoSetup: true, javaPath: null } }));
      await open('Build');
      expect(fieldOf(pane(), 'JDK path')).toBeNull();

      checkboxOf(pane(), 'Set up the build tool automatically').click();
      await fixture.whenStable();
      expect(field('JDK path').textContent).toContain(
        'JAVA_HOME of the Gradle or Maven build and the later stages',
      );

      form.controls.build.controls.tool.setValue('FLUTTER');
      await fixture.whenStable();
      expect(field('JDK path').textContent).toContain('Must be set for Flutter builds');

      form.controls.build.controls.javaPath.markAsTouched();
      await fixture.whenStable();
      expect(field('JDK path').textContent).toContain('Required');
      expect(checkboxOf(pane(), 'Set up the build tool automatically').disabled).toBe(true);
      expect(pane().querySelector('.note')?.textContent).toContain('needs the JDK path');

      form.controls.build.controls.autoSetup.setValue(true);
      await fixture.whenStable();
      expect(field('JDK path').textContent).toContain('Required');
    });

    it('names the artifact the Nexus delivery uploads for Maven on virtual machines', async () => {
      await render();
      await open('Build');
      expect(field('Artifact path').textContent).toContain('What the build produces');

      form.controls.build.controls.tool.setValue('MAVEN');
      await fixture.whenStable();

      expect(field('Artifact path').textContent).toContain(
        'The artifact the Nexus snapshot delivery uploads',
      );

      form.controls.deployment.controls.nexusDeliveryDisabled.setValue(true);
      await fixture.whenStable();

      expect(field('Artifact path').textContent).toContain('What the build produces');
    });

    it('offers the Node.js build of stage 0 for Gradle and Maven and shows its fields once it is on', async () => {
      await render();
      await open('Build');
      const box = () => checkboxOf(pane(), 'Build the frontend with Node.js first');

      expect([...pane().querySelectorAll('h4')].map(text)).toEqual(['Node.js build (stage 0)']);
      expect(text(box().closest('dso-checkbox'))).toContain('build.node.enabled');
      expect(fieldOf(pane(), 'Folder with package.json')).toBeNull();

      box().click();
      await fixture.whenStable();

      expect(form.controls.nodeBuild.controls.enabled.value).toBe(true);
      expect(hints()).toEqual(
        expect.arrayContaining([
          'Left empty: the repository root · build.node.dir',
          'One per line, run in order; left empty: npm ci, then npm run build · build.node.commands',
          'Library default: NodeJS-20 · build.node.installationName',
          'Left empty: 30 · build.node.timeoutMin',
        ]),
      );
      expect(inputOf(pane(), 'Commands').placeholder).toBe('npm ci\nnpm run build');
      expect(checkboxOf(pane(), 'Scan the npm dependencies with Nexus IQ').checked).toBe(true);
      expect(text(pane().querySelector('dso-advanced-settings .panel-description'))).toBe(
        'Source folder, build command and Node.js options',
      );
      expect(field('Node.js home on the agent').closest('dso-advanced-settings')).not.toBeNull();
      form.controls.nodeBuild.controls.nodePath.setValue('/opt/node-20');
      await fixture.whenStable();
      expect(text(field('Node.js installation').querySelector('dso-hint'))).toBe(
        'Not used: the Node.js home on the agent is set · build.node.installationName',
      );
      expect(form.controls.nodeBuild.valid).toBe(true);
      expect(helpOf(field('.npmrc credentials ID'))).toContain(
        'A registry line of an .npmrc in the repository still wins over it.',
      );
      expect(helpOf(field('Source folder'))).toContain(
        'the Gradle, Maven and Node.js builds have their own folder fields',
      );

      form.controls.build.controls.tool.setValue('FLUTTER');
      await fixture.whenStable();

      expect(box()).toBeUndefined();
      expect(form.controls.nodeBuild.controls.enabled.value).toBe(false);
    });

    it.each([
      [null, ['In SAST scanning', 'In the Extended pipeline'], []],
      ['SAST', ['In SAST scanning'], ['In the Extended pipeline']],
      ['EXTENDED', ['In the Extended pipeline'], ['In SAST scanning']],
      ['FULL', [], ['In SAST scanning', 'In the Extended pipeline']],
    ] as const)(
      'tells where the %s pipeline runs the Node.js build',
      async (pipeline, told, untold) => {
        await render();
        fixture.componentRef.setInput('pipeline', pipeline);
        await open('Build');
        const help = helpOf(
          checkboxOf(pane(), 'Build the frontend with Node.js first').closest('dso-checkbox')!,
        );

        expect(help).toContain('extra stage 0, before the Gradle or Maven build');
        told.forEach((sentence) => expect(help).toContain(sentence));
        untold.forEach((sentence) => expect(help).not.toContain(sentence));
      },
    );

    it('leaves the npm scan out of the SAST pipeline, which runs no Nexus IQ, and keeps its value', async () => {
      await render(service({ nodeBuild: nodeBuild({ enabled: true }) }));
      fixture.componentRef.setInput('pipeline', 'SAST');
      await open('Build');
      const scan = () => checkboxOf(pane(), 'Scan the npm dependencies with Nexus IQ');

      expect(scan()).toBeUndefined();
      expect(form.controls.nodeBuild.controls.nexusIqScan.value).toBe(true);
      expect(
        helpOf(
          checkboxOf(pane(), 'Build the frontend with Node.js first').closest('dso-checkbox')!,
        ),
      ).toContain(
        'In SAST scanning it runs only while Compile before preparing the IRX is on, and not on Windows agents.',
      );

      fixture.componentRef.setInput('pipeline', 'NEXUS_IQ');
      await fixture.whenStable();

      expect(scan().checked).toBe(true);
    });

    it('marks Build for an invalid Node.js field and opens it first', async () => {
      await render(
        service({
          nodeBuild: nodeBuild({
            enabled: true,
            installationName: 'NodeJS-20',
            nodePath: '/opt/node-20',
          }),
        }),
      );
      fixture.componentRef.setInput('submitted', true);
      await fixture.whenStable();

      expect(
        [...page().querySelectorAll('.rail-item.problem span:first-of-type')].map(text),
      ).toEqual(['Build']);
      expect(fixture.componentInstance.revealFirstProblem()).toBe(true);
      await fixture.whenStable();

      expect(text(page().querySelector('.rail-item.active span'))).toBe('Build');
      expect(form.controls.nodeBuild.controls.nodePath.errors).toEqual({ rule: NODE_TWICE });
      expect(pane().querySelector('dso-advanced-settings .has-errors')).not.toBeNull();
    });
  });

  describe('Unit tests and coverage', () => {
    it('takes one coverage report per line, left empty to let the pipeline find them, for every build tool', async () => {
      await render();
      await open('Unit tests and coverage');
      const field = () => fieldOf(pane(), 'Coverage reports')!;

      expect(text(pane().querySelector('.pane-header p'))).toContain(
        'BBH policy requires 60% line coverage, the average of the coverage reports of the service.',
      );
      expect(field().querySelector('textarea')).not.toBeNull();
      expect(text(field().querySelector('dso-hint'))).toBe(
        'Usually left empty; one per line, *, ? and ** allowed · coverage.reportPath',
      );
      expect(helpOf(field())).toContain(
        'finds every JaCoCo XML and LCOV report of the service by itself',
      );
      expect(helpOf(field())).toContain(
        'only the reports named here count, so name the report of the frontend too',
      );

      const input = inputOf(pane(), 'Coverage reports');
      input.value = 'build/reports/jacoco/test/jacocoTestReport.xml\nweb/coverage/lcov.info';
      input.dispatchEvent(new Event('input'));
      expect(form.controls.unitTests.controls.coverageReportPath.value).toBe(
        'build/reports/jacoco/test/jacocoTestReport.xml\nweb/coverage/lcov.info',
      );

      form.controls.build.controls.tool.setValue('FLUTTER');
      await fixture.whenStable();
      expect(helpOf(field())).toContain('and for Flutter the lcov.info of each package');
    });
  });

  describe('Deployment', () => {
    it('switches the deployment target and its sections with the toggles', async () => {
      await render();
      await open('Deployment');
      const target = (label: string) => buttonOf(pane(), label);

      expect(pane().querySelector('dso-toggle-group')?.getAttribute('aria-label')).toBe(
        'Deployment target',
      );
      expect(target('Virtual machine').getAttribute('aria-checked')).toBe('true');
      expect(pane().querySelector('.pane-header p')?.textContent).toContain(
        'UrbanCode Deploy and SSH targets',
      );

      target('OpenShift').click();
      await fixture.whenStable();

      expect(form.controls.deployment.controls.target.value).toBe('OPENSHIFT');
      expect(target('OpenShift').getAttribute('aria-checked')).toBe('true');
      expect(railLabels()).toContain('OpenShift targets');
      expect(railLabels()).not.toContain('SSH targets');
    });

    it('hides the fields only the Nexus delivery uses while it is disabled and shows them again', async () => {
      await render(service({ build: { ...service().build, tool: 'MAVEN' } }));
      await open('Deployment');
      const labels = () =>
        [...pane().querySelectorAll('dso-form-field dso-label')].map((label) => text(label));
      const box = () => checkboxOf(pane(), 'Disable Nexus Delivery');

      expect(text(box().closest('dso-checkbox'))).toContain('deploy.vm.rd.nexusDeliveryEnabled');
      expect(labels()).toContain('Artifact name');
      expect(fieldOf(pane(), 'Built file name')?.closest('dso-advanced-settings')).not.toBeNull();
      expect(pane().querySelector('.block h4')?.textContent).toBe('Delivery');

      box().click();
      await fixture.whenStable();

      expect(form.controls.deployment.controls.nexusDeliveryDisabled.value).toBe(true);
      expect(labels()).not.toContain('Artifact name');
      expect(labels()).not.toContain('Built file name');
      expect(pane().querySelector('.block')).toBeNull();

      form.controls.deployment.controls.target.setValue('OPENSHIFT');
      await fixture.whenStable();

      expect(text(box().closest('dso-checkbox'))).toContain(
        'deploy.openshift.rd.nexusDeliveryEnabled',
      );
      await open('OpenShift targets');

      expect(text(pane())).not.toContain('Image build');
      expect(fieldOf(pane(), 'Image pushed to')).toBeNull();
      expect(fieldOf(pane(), 'Image pulled from')).not.toBeNull();
      expect(text(pane())).toContain(
        'the RD deployment uses this build tag and is skipped without one',
      );
    });
  });

  describe('Flutter', () => {
    it('marks the delivery coordinates required on virtual machines only', async () => {
      await render(service({ build: { ...service().build, tool: 'FLUTTER' } }));
      await open('Flutter');
      expect(hints()).toContain('Required on virtual machines · delivery.group');
      expect(hints()).toContain('One per line, at least one · tools.flutter.flutterModules');

      form.controls.deployment.controls.target.setValue('OPENSHIFT');
      await fixture.whenStable();

      expect(hints()).toContain('delivery.group');

      form.controls.deployment.controls.nexusDeliveryDisabled.setValue(true);
      await fixture.whenStable();

      expect(hints()).not.toContain('delivery.group');
    });
  });

  describe('Nexus IQ', () => {
    const items = () => [...pane().querySelectorAll<HTMLElement>('.list-item')];

    it('holds one application, offered again once it is removed, and names the global server', async () => {
      await render();
      await open('Nexus IQ');
      expect(hints()).toContain(
        'Library default: https://tools.bbh.com/IQ · tools.nexusIq.serverUrl',
      );
      expect(hints()).toContain('Library default: nexusiqP · tools.nexusIq.credentialsId');
      expect(items().map((item) => item.querySelector('strong')?.textContent)).toEqual([
        'cert-gui',
      ]);
      expect(buttonOf(pane(), 'Add application')).toBeUndefined();

      pane().querySelector<HTMLButtonElement>('button[aria-label="Remove cert-gui"]')!.click();
      await fixture.whenStable();

      expect(text(pane().querySelector('.list-empty'))).toBe(
        'No application: the Nexus IQ stage passes with no findings. A Nexus IQ GoldenFix pipeline requires an application.',
      );

      buttonOf(pane(), 'Add application').click();
      await fixture.whenStable();

      expect(items().length).toBe(1);
      expect(form.controls.nexusIqApplications.at(0).controls.stage.value).toBe('build');
      expect(form.dirty).toBe(true);
    });

    it('shows both applications of a legacy service until one is removed', async () => {
      await render();
      form.controls.nexusIqApplications.push(
        createNexusIqApplicationForm({ application: 'cert-api', scanPatterns: ['*.jar'] }),
      );
      await open('Nexus IQ');

      expect(items().length).toBe(2);
      expect(text(pane().querySelector('.list-error'))).toBe(ONE_APPLICATION);

      fixture.componentRef.setInput('submitted', true);
      await fixture.whenStable();

      expect(page().querySelector('.rail-item.active.problem')).not.toBeNull();

      pane().querySelector<HTMLButtonElement>('button[aria-label="Remove cert-api"]')!.click();
      await fixture.whenStable();

      expect(pane().querySelector('.list-error')).toBeNull();
      expect(form.controls.nexusIqApplications.valid).toBe(true);
    });
  });

  describe('servers and credentials of the service', () => {
    it('name the Library default, and whether the value still equals it', async () => {
      await render();
      await open('SonarQube');
      expect(hints()).toContain(
        'Library default: https://tools.bbh.com/sonar · tools.sonar.serverUrl',
      );

      form.controls.sonar.controls.installationName.setValue('SonarQube');
      await fixture.whenStable();

      expect(hints()).toContain('Library default · tools.sonar.installationName');
      await open('Monitoring');
      expect(hints()).toContain('Library default: influxdb-token · influx.credentialsId');
      await open('SSH targets');
      expect(hints()).toContain('Library default: taadmin · user');
    });

    it('hints at nothing without the global settings', async () => {
      await render(service(), null);
      await open('SonarQube');

      expect(hints()).toContain('tools.sonar.serverUrl');
    });
  });

  describe('AppScan', () => {
    it('asks for the key of the service with the application and keeps the scan name and folders under Advanced settings', async () => {
      await render();
      await open('AppScan SAST and DAST');
      const main = [...pane().querySelectorAll(':scope > .form-fields > dso-fields dso-label')].map(
        (label) => text(label),
      );

      expect(main).toEqual([
        'AppScan application ID',
        'AppScan API key ID',
        'API key secret credentials ID',
      ]);
      expect(text(fieldOf(pane(), 'API key secret credentials ID'))).toContain(
        'Left empty: the key ID · asoc.token',
      );
      expect(text(fieldOf(pane(), 'SAST scan name'))).toContain('sast.scanName');
      ['SAST scan name', 'Included folders', 'Excluded folders'].forEach((label) =>
        expect(fieldOf(pane(), label)?.closest('dso-advanced-settings'), label).not.toBeNull(),
      );
      expect(text(pane().querySelector('dso-advanced-settings .panel-description'))).toBe(
        'Scan name, scanned folders, AppScan client and the options of the static scan',
      );
    });
  });

  describe('SonarQube', () => {
    it('shows the project first, keeps the tokens and badges under Advanced settings and refuses both badge values', async () => {
      await render();
      await open('SonarQube');
      const main = [...pane().querySelectorAll(':scope > .form-fields > dso-fields dso-label')].map(
        (label) => text(label),
      );

      expect(main).toEqual(['Project name', 'Project key', 'Jenkins installation']);
      ['Token credentials ID', 'Badge token', 'Badge token credentials ID'].forEach((label) =>
        expect(fieldOf(pane(), label)?.closest('dso-advanced-settings'), label).not.toBeNull(),
      );
      expect(text(fieldOf(pane(), 'Project key'))).toContain(
        'Left empty, the scan still runs without dashboard link, issue counts and badges',
      );
      expect(
        text(checkboxOf(pane(), 'Badges in the Jenkins build description').closest('dso-checkbox')),
      ).toContain('tools.sonar.addBadges');

      form.controls.sonar.controls.badgeCredentialsId.setValue('sonar-badge');
      const token = inputOf(pane(), 'Badge token');
      token.value = 'sqb_1';
      token.dispatchEvent(new Event('input'));
      token.dispatchEvent(new Event('blur'));
      await fixture.whenStable();

      expect(text(fieldOf(pane(), 'Badge token')?.querySelector('dso-error'))).toBe(BADGE_TWICE);
      expect(pane().querySelector('dso-advanced-settings .has-errors')).not.toBeNull();
    });
  });

  describe('GoldenFix', () => {
    it('offers Library default, On and Off and names the Library default', async () => {
      await render();
      await open('GoldenFix');

      const select = selectOf(pane(), 'Run GoldenFix');
      expect(text(select.selectedOptions[0])).toBe('Library default');
      expect(hints()).toContain('Library default: on · goldenFix.enabled');
      expect(optionsOf(select)).toEqual(['Library default', 'On', 'Off']);

      choose(select, 'Off');
      await fixture.whenStable();

      expect(form.controls.goldenFix.controls.enabled.value).toBe(false);
    });

    it('turns GoldenFix on for the service only', async () => {
      const settings = globalSettings();
      await render(service(), {
        ...settings,
        goldenFix: { ...settings.goldenFix, enabled: false },
      });
      await open('GoldenFix');

      expect(hints()).toContain('Library default: off · goldenFix.enabled');
      choose(selectOf(pane(), 'Run GoldenFix'), 'On');
      await fixture.whenStable();

      expect(form.controls.goldenFix.controls.enabled.value).toBe(true);
    });

    it('describes the inherited policy or shows the fields the service overrides', async () => {
      await render();
      await open('GoldenFix');

      expect(pane().querySelector('.note')?.textContent).toContain(
        'maven, npm, pypi, threat level 2 and above, direct dependencies only, each fix verified by a build.',
      );
      expect(buttonOf(pane(), 'Inherit the Library defaults').getAttribute('aria-checked')).toBe(
        'true',
      );
      buttonOf(pane(), 'Override for this service').click();
      await fixture.whenStable();

      expect(form.controls.goldenFix.controls.inherit.value).toBe(false);
      expect(pane().querySelector('dso-golden-fix-fields')).not.toBeNull();
    });

    it('names the Library default in the override choices and in the hints of prefilled fields', async () => {
      await render();
      applyLibraryDefaults(form, globalSettings());
      await open('GoldenFix');
      buttonOf(pane(), 'Override for this service').click();
      await fixture.whenStable();
      const hintOf = (label: string) => text(fieldOf(pane(), label)?.querySelector('dso-hint'));

      expect(optionsOf(selectOf(pane(), 'Dependencies'))[0]).toBe('Library default');
      expect(optionsOf(selectOf(pane(), 'Verification'))[0]).toBe('Library default');
      expect(hintOf('Minimum threat level')).toContain('from 0 to 10 · Library default · ');
      expect(hintOf('Golden version types')).toContain('one per line · Library default · ');
      expect(hintOf('Excluded folders')).toContain('left empty: the Library default');
      expect(hintOf('Dependencies')).toContain(
        'transitive ones come with them · Library default · ',
      );

      form.controls.goldenFix.controls.minThreatLevel.setValue(7);
      choose(selectOf(pane(), 'Dependencies'), 'Library default');
      await fixture.whenStable();

      expect(hintOf('Minimum threat level')).toContain('· Library default: 2 · ');
      expect(hintOf('Dependencies')).toContain('· Library default: yes · ');
    });

    it('tells in which folders the verification commands run', async () => {
      await render();
      await open('GoldenFix');
      buttonOf(pane(), 'Override for this service').click();
      await fixture.whenStable();
      const help = (label: string) => helpOf(fieldOf(pane(), label)!);

      expect(help('Gradle command')).toContain(
        'for a version catalog, settings file or platform, in the folder of each build that uses it',
      );
      expect(help('Maven command')).toContain(
        'in the folder of the multi-module pom that builds them together, otherwise in the folder of the root pom.xml or of that pom',
      );
      expect(help('Maven command')).toContain(
        'When a shared pom with a name other than pom.xml is built in its own folder, the library adds -f and its file name at the end of the command. ' +
          'It does so for a command set here too, so write it as one mvn call, for example mvn -B -q -DskipTests verify. ' +
          'Exit code 0 passes the check. When no command is set, the library runs mvn -B -q -DskipTests compile.',
      );
      expect(help('npm command')).toContain(
        'A command set here runs as it is, without the refresh of package-lock.json',
      );
      expect(help('Timeout (minutes)')).toContain(
        'one for each folder a changed manifest or catalog is built in',
      );
    });

    it('lists the shared version catalogs of the service under Inherit and Override', async () => {
      await render(service({ versionCatalogs: ['gradle/libs.versions.toml'] }));
      await open('GoldenFix');

      expect(inputOf(pane(), 'Shared version catalogs').value).toBe('gradle/libs.versions.toml');
      expect(hints()).toContain(
        'Optional; one path per line, relative to the repository root · goldenFix.versionCatalogs',
      );
      expect(inputOf(pane(), 'Shared version catalogs').placeholder).toBe('platform/bom.xml');
      expect(helpOf(fieldOf(pane(), 'Shared version catalogs')!)).toContain(
        'without such an aggregator, the poms that use it are not built',
      );

      buttonOf(pane(), 'Override for this service').click();
      await fixture.whenStable();

      expect(inputOf(pane(), 'Shared version catalogs').value).toBe('gradle/libs.versions.toml');
      expect(form.controls.goldenFix.controls.inherit.value).toBe(false);
    });

    it('works without the global settings', async () => {
      await render(service(), null);
      await open('GoldenFix');

      expect(hints()).toContain('goldenFix.enabled');
      expect(pane().querySelector('.note')?.textContent).toBe(
        'The service follows the GoldenFix defaults on the Library defaults page.',
      );
    });
  });
});
