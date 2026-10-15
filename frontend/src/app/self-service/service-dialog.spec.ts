import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { inputOf, text } from '@common/testing/dom';
import { serviceTemplate } from '../testing/fixtures';
import { WizardDefaults, WizardService } from './self-service-model';
import { ServiceDialog, ServiceDialogData } from './service-dialog';

const APP_ID = '7d1f3a52-9c4b-4e8a-b2d6-0f5e1c9a8b31';
const REPOSITORY = 'https://bitbucket.bbh.com/projects/PAY/repos/gateway';
const NO_DEFAULTS: WizardDefaults = { tool: null, target: null, template: null, productCode: '' };
const BBH_DEFAULTS: WizardDefaults = {
  tool: 'GRADLE',
  target: 'VM',
  template: serviceTemplate(),
  productCode: 'PAY',
};

describe('ServiceDialog', () => {
  let fixture: ComponentFixture<ServiceDialog>;
  const close = vi.fn();

  async function open(data: Partial<ServiceDialogData> = {}) {
    TestBed.configureTestingModule({
      imports: [ServiceDialog],
      providers: [
        { provide: DialogRef, useValue: { close } },
        {
          provide: DIALOG_DATA,
          useValue: {
            pipeline: 'FULL',
            service: null,
            takenNames: ['gui'],
            defaults: NO_DEFAULTS,
            ...data,
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ServiceDialog);
    await fixture.whenStable();
  }

  afterEach(() => close.mockReset());

  const page = () => fixture.nativeElement as HTMLElement;

  async function type(label: string, value: string) {
    const input = inputOf(page(), label);
    input.value = value;
    input.dispatchEvent(new Event('input'));
    await fixture.whenStable();
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  async function choose(label: string) {
    page().querySelector<HTMLButtonElement>(`[role=radio][aria-label="${label}"]`)!.click();
    await fixture.whenStable();
  }

  const errors = () => [...page().querySelectorAll('dso-error, .choice-error')].map(text);

  it('asks about the service first and refuses a taken name and a malformed AppScan ID', async () => {
    await open();

    expect(text(page().querySelector('.page-count'))).toBe('Part 1 of 2 · About the service');
    expect(text(page().querySelector('button[type=submit]'))).toBe('Next: Build and run');

    await type('Service name', 'GUI');
    await type('AppScan application ID', 'not-an-id');
    await submit();

    expect(errors()).toEqual([
      'Another service of this product already has this name',
      'Paste the ID as it is, for example 109f44ac-cc06-4ca0-884e-d944904f7019',
    ]);
    expect(text(page().querySelector('.page-count'))).toContain('Part 1 of 2');
  });

  it('adds an OpenShift service once its build tool, platform and project are chosen', async () => {
    await open();
    await type('Service name', 'gateway');
    await type('AppScan application ID', APP_ID.toUpperCase());
    await submit();

    expect(text(page().querySelector('.page-count'))).toBe('Part 2 of 2 · Build and run');

    await submit();
    expect(errors()).toEqual(['Choose Gradle or Maven', 'Choose where the service runs']);

    await choose('Maven');
    await choose('OpenShift');
    await submit();
    expect(errors()).toEqual(['Required']);
    expect(close).not.toHaveBeenCalled();

    await type('OpenShift project', 'pay-payhub');
    await submit();

    expect(close).toHaveBeenCalledWith({
      id: null,
      name: 'gateway',
      description: '',
      appScanId: APP_ID,
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: 'pay-payhub',
      nexusIqApplication: '',
      repositoryUrl: '',
    } satisfies WizardService);
  });

  it('starts a new service on the default build tool and platform with the names the template gives', async () => {
    await open({ defaults: BBH_DEFAULTS });
    await type('Service name', 'gateway');
    await type('AppScan application ID', APP_ID);
    await submit();

    expect(errors()).toEqual([]);
    await choose('OpenShift');
    expect(inputOf(page(), 'OpenShift project').value).toBe('pay-gateway');

    await submit();

    expect(close).toHaveBeenCalledWith(
      expect.objectContaining({
        tool: 'GRADLE',
        target: 'OPENSHIFT',
        openShiftProject: 'pay-gateway',
      }),
    );
  });

  it('fills the Nexus IQ application and the repository in from the template and renames them with the service', async () => {
    await open({ pipeline: 'NEXUS_IQ', defaults: BBH_DEFAULTS });
    await type('Service name', 'gateway');
    await type('AppScan application ID', APP_ID);
    await submit();

    expect(inputOf(page(), 'Nexus IQ application').value).toBe('pay-gateway');
    expect(inputOf(page(), 'Bitbucket repository').value).toBe(
      'https://bitbucket.bbh.com/projects/PAY/repos/pay-gateway',
    );

    await type('Bitbucket repository', REPOSITORY);
    page().querySelector<HTMLButtonElement>('.modal-footer button[type=button]')!.click();
    await fixture.whenStable();
    await type('Service name', 'ledger');
    await submit();

    expect(inputOf(page(), 'Nexus IQ application').value).toBe('pay-ledger');
    expect(inputOf(page(), 'Bitbucket repository').value).toBe(REPOSITORY);
  });

  it('asks only for the build tool of a static scan and goes back to the first part', async () => {
    await open({ pipeline: 'SAST' });
    await type('Service name', 'scanner');
    await type('AppScan application ID', APP_ID);
    await submit();

    expect(page().querySelectorAll('[role=radiogroup]')).toHaveLength(1);
    expect(inputOf(page(), 'Nexus IQ application')).toBeUndefined();
    expect(inputOf(page(), 'Bitbucket repository')).toBeUndefined();

    page().querySelector<HTMLButtonElement>('.modal-footer button[type=button]')!.click();
    await fixture.whenStable();
    expect(inputOf(page(), 'Service name').value).toBe('scanner');

    await submit();
    await choose('Gradle');
    await submit();

    expect(close).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'scanner',
        tool: 'GRADLE',
        target: null,
        openShiftProject: '',
      }),
    );
  });

  it('changes the build tool and platform of a service of the portal and asks for its project', async () => {
    const service: WizardService = {
      id: 10,
      name: 'gui',
      description: 'Angular front end',
      appScanId: APP_ID,
      tool: 'GRADLE',
      target: 'VM',
      openShiftProject: '',
      nexusIqApplication: 'cert-gui',
      repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
    };
    await open({ service, takenNames: ['api'] });

    expect(text(page().querySelector('h2'))).toBe('Change gui');
    expect(text(page().querySelector('.page-count'))).toBe('Part 1 of 2 · About the service');

    await type('What it does', 'Web front end');
    await submit();

    expect(text(page().querySelector('.page-count'))).toBe('Part 2 of 2 · Build and run');
    expect(
      page().querySelector('[role=radio][aria-checked=true][aria-label=Gradle]'),
    ).not.toBeNull();
    expect(text(page().querySelector('.note'))).toBe(
      'If you change how it is built or where it runs, its build or deployment settings go back to the BBH defaults.',
    );
    expect(inputOf(page(), 'OpenShift project')).toBeUndefined();

    await choose('Maven');
    await choose('OpenShift');
    await submit();

    expect(errors()).toEqual(['Required']);

    await type('OpenShift project', 'cert-gui');
    await submit();

    expect(close).toHaveBeenCalledWith({
      ...service,
      description: 'Web front end',
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: 'cert-gui',
    });
  });

  it('keeps a service of the portal on OpenShift without asking for its project again', async () => {
    const service: WizardService = {
      id: 10,
      name: 'gui',
      description: '',
      appScanId: APP_ID,
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: '',
      nexusIqApplication: '',
      repositoryUrl: '',
    };
    await open({ service, pipeline: 'SAST', takenNames: [] });
    await submit();

    expect(page().querySelectorAll('[role=radiogroup]')).toHaveLength(1);
    expect(text(page().querySelector('.note'))).toBe(
      'If you change how it is built, its build settings go back to the BBH defaults.',
    );

    await submit();

    expect(close).toHaveBeenCalledWith(service);
  });

  it('says how a service of the portal is built when no tile matches and keeps it that way', async () => {
    const service: WizardService = {
      id: 12,
      name: 'mobile-app',
      description: '',
      appScanId: APP_ID,
      tool: 'FLUTTER',
      target: 'VM',
      openShiftProject: '',
      nexusIqApplication: '',
      repositoryUrl: '',
    };
    await open({ service, pipeline: 'SAST', takenNames: [] });
    await submit();

    expect(page().querySelector('[role=radio][aria-checked=true]')).toBeNull();
    expect([...page().querySelectorAll('.help')].map(text)).toContain(
      'It is built with Flutter today. Choose Gradle or Maven only if that changes.',
    );

    await submit();

    expect(close).toHaveBeenCalledWith(service);
  });

  it('asks for the Nexus IQ application and the Bitbucket repository of a Nexus IQ GoldenFix service', async () => {
    await open({ pipeline: 'NEXUS_IQ' });
    await type('Service name', 'gateway');
    await type('AppScan application ID', APP_ID);
    await submit();

    expect(page().querySelectorAll('[role=radiogroup]')).toHaveLength(1);
    expect([...page().querySelectorAll('h3')].map(text)).toEqual([
      'What builds the code?',
      'Nexus IQ and Bitbucket',
    ]);

    await submit();

    expect(errors()).toEqual(['Choose Gradle or Maven', 'Required', 'Required']);

    await choose('Maven');
    await type('Nexus IQ application', ' payhub-gateway ');
    await type('Bitbucket repository', 'bitbucket.bbh.com/projects/PAY');
    await submit();

    expect(errors()).toEqual([
      'An http or https URL without spaces, double quotes, backslashes, $ or backticks',
    ]);
    expect(close).not.toHaveBeenCalled();

    await type('Bitbucket repository', REPOSITORY);
    await submit();

    expect(close).toHaveBeenCalledWith({
      id: null,
      name: 'gateway',
      description: '',
      appScanId: APP_ID,
      tool: 'MAVEN',
      target: null,
      openShiftProject: '',
      nexusIqApplication: 'payhub-gateway',
      repositoryUrl: REPOSITORY,
    } satisfies WizardService);
  });

  it('starts from the Nexus IQ application and the repository of a service of the portal', async () => {
    const service: WizardService = {
      id: 10,
      name: 'gui',
      description: '',
      appScanId: APP_ID,
      tool: 'GRADLE',
      target: 'VM',
      openShiftProject: '',
      nexusIqApplication: 'cert-gui',
      repositoryUrl: 'https://bitbucket.bbh.com/projects/CERT/repos/gui',
    };
    await open({ service, pipeline: 'NEXUS_IQ', takenNames: [] });
    await submit();

    expect(inputOf(page(), 'Nexus IQ application').value).toBe('cert-gui');
    expect(inputOf(page(), 'Bitbucket repository').value).toBe(service.repositoryUrl);
    expect(inputOf(page(), 'OpenShift project')).toBeUndefined();

    await type('Nexus IQ application', '');
    await submit();

    expect(errors()).toEqual(['Required']);

    await type('Nexus IQ application', 'cert-web');
    await submit();

    expect(close).toHaveBeenCalledWith({ ...service, nexusIqApplication: 'cert-web' });
  });
});
