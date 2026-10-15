import { ComponentFixture, TestBed } from '@angular/core/testing';
import { GlobalSettings } from '../core/models';
import { buttonOf, fieldOf, text } from '@common/testing/dom';
import { globalSettings, service } from '../testing/fixtures';
import { applyFieldProblems } from '@common/shared/form-controls';
import { ServiceForm, createServiceForm, toServiceRequest } from './product-form-model';
import { UrbanCodeFields } from './urban-code-fields';

describe('UrbanCodeFields', () => {
  let fixture: ComponentFixture<UrbanCodeFields>;
  let form: ServiceForm;

  async function render(defaults: GlobalSettings | null = globalSettings()) {
    TestBed.configureTestingModule({ imports: [UrbanCodeFields] });
    form = createServiceForm(service());
    fixture = TestBed.createComponent(UrbanCodeFields);
    fixture.componentRef.setInput('form', form);
    fixture.componentRef.setInput('defaults', defaults);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;
  const applications = () => [...page().querySelectorAll<HTMLElement>('.list-item')];

  async function click(element: HTMLElement) {
    element.click();
    await fixture.whenStable();
  }

  it('shows the stored application with its component and the global defaults', async () => {
    await render();

    expect(applications().length).toBe(1);
    expect(applications()[0].querySelector('strong')?.textContent).toBe('CERT-GUI');
    expect(page().querySelector('.count')?.textContent).toBe('1');
    expect(applications()[0].querySelectorAll('.component').length).toBe(1);
    expect(text(page())).toContain('Left empty: deploy.bbh.com · deploy.vm.dod.siteName');
  });

  it('adds and removes applications and components', async () => {
    await render(null);

    await click(buttonOf(page(), 'Add application'));
    expect(applications().length).toBe(2);
    expect(applications()[1].querySelector('strong')?.textContent).toBe('New application');
    expect(form.dirty).toBe(true);

    await click(buttonOf(applications()[1], 'Add component'));
    expect(applications()[1].querySelectorAll('.component').length).toBe(2);

    const removeComponent =
      applications()[1].querySelector<HTMLButtonElement>('.component .remove')!;
    expect(removeComponent.getAttribute('aria-label')).toBe('Remove the new component');
    await click(removeComponent);
    await click(applications()[1].querySelector<HTMLButtonElement>('.component .remove')!);

    const empty = applications()[1].querySelector('.list-empty')!;
    expect(empty.textContent?.trim()).toBe('No component yet. Add at least one.');
    expect(empty.classList).not.toContain('missing');
    form.markAllAsTouched();
    await fixture.whenStable();
    expect(empty.classList).toContain('missing');
    expect(form.controls.urbanCodeApplications.at(1).controls.components.errors).toEqual({
      rule: 'Add at least one component',
    });

    await click(buttonOf(applications()[1], 'Remove'));
    await click(buttonOf(applications()[0], 'Remove'));
    expect(page().querySelector('.list-empty')?.textContent).toContain('No application');
    expect(text(page())).toContain('Left empty: the global default');
  });

  it('lets an application override the settings above and sends its component details', async () => {
    await render();
    const application = form.controls.urbanCodeApplications.at(0);
    expect(text(fieldOf(applications()[0], 'Deploy with a snapshot'))).toContain('Setting above');
    expect(text(fieldOf(applications()[0], 'Site name'))).toContain(
      'Left empty: the setting above',
    );
    expect(fieldOf(applications()[0], 'Charset')).not.toBeNull();

    application.patchValue({ siteName: ' deploy-qa.bbh.com ', skipWait: false });
    application.controls.components.at(0).patchValue({ charset: 'UTF-8', extensions: 'jar' });

    expect(toServiceRequest(form).urbanCodeApplications[0]).toMatchObject({
      siteName: 'deploy-qa.bbh.com',
      skipWait: false,
      deployWithSnapshot: null,
      components: [{ charset: 'UTF-8', extensions: 'jar', versionProperties: null }],
    });
  });

  it('shows a problem the API reports for the whole list', async () => {
    await render();

    applyFieldProblems(form, [
      { field: 'urbanCodeApplications', message: 'application names must be unique' },
    ]);
    fixture.componentRef.changeDetectorRef.markForCheck();
    await fixture.whenStable();

    expect(page().querySelector('.list-error')?.textContent).toBe(
      'application names must be unique',
    );
  });
});
