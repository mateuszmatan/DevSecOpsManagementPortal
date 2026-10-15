import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fieldOf } from '@common/testing/dom';
import { service } from '../testing/fixtures';
import { OpenShiftTargetFields } from './openshift-target-fields';
import { ServiceForm, createServiceForm } from './product-form-model';

describe('OpenShiftTargetFields', () => {
  let fixture: ComponentFixture<OpenShiftTargetFields>;
  let form: ServiceForm;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [OpenShiftTargetFields] });
    form = createServiceForm(
      service({ deployment: { ...service().deployment, target: 'OPENSHIFT' } }),
    );
    fixture = TestBed.createComponent(OpenShiftTargetFields);
    fixture.componentRef.setInput('form', form);
    await fixture.whenStable();
  });

  const page = () => fixture.nativeElement as HTMLElement;
  const headings = () =>
    [...page().querySelectorAll('.sub-heading')].map((heading) => heading.textContent?.trim());
  const toggle = (label: string) =>
    [...page().querySelectorAll<HTMLButtonElement>('dso-toggle-group button')].find((button) =>
      button.textContent?.includes(label),
    )!;

  const field = (label: string) => fieldOf(page(), label);

  async function choose(label: string) {
    toggle(label).click();
    await fixture.whenStable();
  }

  it('asks for the image build of the RD region and marks it until it is filled', async () => {
    expect(page().querySelector('.region-note')?.textContent).toContain('deploy.openshift.rd');
    expect(headings()).toEqual([
      'Image build',
      'Image registry',
      'Deployment',
      'Deployment repository',
      'Pinned image',
    ]);
    expect(field('Internal image URL')).not.toBeNull();
    expect(toggle('RD region').querySelector('.problem-mark')).not.toBeNull();
    expect(toggle('RD region').getAttribute('aria-checked')).toBe('true');

    const projectBuild = field('Build project')!;
    projectBuild.querySelector('input')!.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    expect(projectBuild.querySelector('dso-error')?.textContent).toBe('Required');
    expect(field('File added to the image')!.querySelector('dso-error')).toBeNull();

    form.controls.openShiftTargets.controls.RD.patchValue({
      projectBuild: 'cert-build',
      buildConfigPath: 'openshift/buildconfig.yaml',
      dockerFilePath: 'openshift/Dockerfile',
      buildContext: 'target/docker',
      dockerRepoPush: 'nexus.bbh.com:18444/cert',
      nexusAuthFile: '/etc/containers/auth.json',
    });
    await fixture.whenStable();
    expect(toggle('RD region').querySelector('.problem-mark')).toBeNull();
  });

  it('shows the image build of the QC region only once one of its fields is set', async () => {
    await choose('QC region');

    expect(page().querySelector('.region-note')?.textContent).toContain('deploy.openshift.qc');
    expect(headings()).not.toContain('Image build');
    expect(toggle('QC region').getAttribute('aria-checked')).toBe('true');
    expect(field('Build tag')).not.toBeNull();
    expect(field('Internal image URL')).toBeNull();

    form.controls.openShiftTargets.controls.QC.controls.dockerFilePath.setValue('Dockerfile.qc');
    await choose('RD region');
    await choose('QC region');

    expect(headings()).toContain('Image build');
  });

  it('explains a deployment repository that is not a URL', async () => {
    const url = form.controls.openShiftTargets.controls.RD.controls.deploymentRepoUrl;
    url.setValue('not a url');
    url.markAsTouched();
    await fixture.whenStable();

    expect(page().textContent).toContain('Must be an http, https, ssh or git@ URL');
  });
});
