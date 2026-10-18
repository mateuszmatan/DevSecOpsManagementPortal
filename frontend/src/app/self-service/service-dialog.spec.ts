import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { text } from '@common/testing/dom';
import { service, serviceTemplate } from '../testing/fixtures';
import { createUrbanCodeApplicationForm } from '../products/product-form-model';
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
  let component: ServiceDialog;
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
            defaults: BBH_DEFAULTS,
            ...data,
          },
        },
      ],
    });
    fixture = TestBed.createComponent(ServiceDialog);
    component = fixture.componentInstance;
    await fixture.whenStable();
  }

  afterEach(() => close.mockReset());

  it('initializes form for FULL pipeline with all sections', async () => {
    await open({ pipeline: 'FULL' });
    expect(component).toBeTruthy();
    expect(component['serviceForm']).toBeTruthy();
    expect(component['serviceForm'].controls.name).toBeTruthy();
    expect(component['serviceForm'].controls.appScan).toBeTruthy();
    expect(component['serviceForm'].controls.nexusIqApplications).toBeTruthy();
    expect(component['serviceForm'].controls.testJobs).toBeTruthy();
  });

  it('filters visible sections for NEXUS_IQ pipeline', async () => {
    await open({ pipeline: 'NEXUS_IQ' });
    expect(component).toBeTruthy();
    const sections = component['serviceForm'];
    expect(sections).toBeTruthy();
  });

  it('refuses invalid form and highlights problems', async () => {
    await open({ pipeline: 'FULL' });
    component['serviceForm'].controls.name.setValue('');
    component['serviceForm'].controls.appScan.controls.applicationId.setValue('');
    component['save']();
    expect(close).not.toHaveBeenCalled();
    expect(component['checked']()).toBe(true);
  });

  it('refuses duplicate service name', async () => {
    await open({ pipeline: 'FULL', takenNames: ['backend-api'] });
    component['serviceForm'].controls.name.setValue('backend-api');
    component['serviceForm'].controls.name.updateValueAndValidity();
    expect(component['serviceForm'].controls.name.invalid).toBe(true);
    expect(component['serviceForm'].controls.name.errors).toEqual({
      rule: 'Another service of this product already has this name',
    });
  });

  it('saves valid service and closes dialog with service data', async () => {
    await open({ pipeline: 'FULL' });
    component['serviceForm'].controls.name.setValue('new-service');
    component['serviceForm'].controls.appScan.controls.applicationId.setValue(APP_ID);
    component['serviceForm'].controls.scm.controls.repositoryUrl.setValue(REPOSITORY);
    component['serviceForm'].controls.urbanCodeApplications.push(
      createUrbanCodeApplicationForm(service().urbanCodeApplications[0]),
    );
    component['save']();

    expect(close).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'new-service',
        appScanId: APP_ID,
        repositoryUrl: REPOSITORY,
      }),
    );
  });

  it('asks for an UrbanCode Deploy application for DV before adding a service with a Full pipeline', async () => {
    await open({ pipeline: 'FULL' });
    const form = component['serviceForm'];
    form.patchValue({ name: 'archive-api', appScan: { applicationId: APP_ID } });
    component['save']();
    fixture.detectChanges();

    expect(close).not.toHaveBeenCalled();
    expect(text(fixture.nativeElement.querySelector('.rail-item.active.problem'))).toBe(
      'UrbanCode Deploy',
    );

    form.controls.urbanCodeApplications.push(
      createUrbanCodeApplicationForm({ ...service().urbanCodeApplications[0], environments: [] }),
    );
    component['save']();

    expect(close).toHaveBeenCalledOnce();
  });

  it('needs no UrbanCode Deploy application for a Security pipeline', async () => {
    await open({ pipeline: 'SECURITY' });
    component['serviceForm'].patchValue({ name: 'archive-api', appScan: { applicationId: APP_ID } });
    component['save']();

    expect(close).toHaveBeenCalledOnce();
  });

  it.each([
    ['SAST', 'VM'],
    ['NEXUS_IQ', 'VM'],
    ['SECURITY', 'OPENSHIFT'],
    ['FULL', 'OPENSHIFT'],
  ] as const)('starts a new service of the %s pipeline on %s when OpenShift is the default', async (pipeline, target) => {
    await open({ pipeline, defaults: { ...BBH_DEFAULTS, target: 'OPENSHIFT' } });

    expect(component['serviceForm'].controls.deployment.controls.target.value).toBe(target);
  });

  it('loads existing service settings into form', async () => {
    const existing: WizardService = {
      id: 10,
      name: 'existing-service',
      description: 'Some desc',
      appScanId: APP_ID,
      tool: 'MAVEN',
      target: 'OPENSHIFT',
      openShiftProject: 'pay-hub',
      nexusIqApplication: 'pay-app',
      repositoryUrl: REPOSITORY,
    };
    await open({ service: existing });
    expect(component['serviceForm'].controls.name.value).toBe('existing-service');
    expect(component['serviceForm'].controls.build.controls.tool.value).toBe('MAVEN');
    expect(component['serviceForm'].controls.deployment.controls.target.value).toBe('OPENSHIFT');
  });
});
