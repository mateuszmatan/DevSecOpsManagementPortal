import { ComponentFixture, TestBed } from '@angular/core/testing';
import { fieldOf, helpOf } from '@common/testing/dom';
import { service } from '../testing/fixtures';
import { OpenShiftTargetFields } from './openshift-target-fields';
import {
  ServiceForm,
  createServiceForm,
  sectionInvalid,
  visibleSections,
} from './product-form-model';

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

  it('keeps an empty QC region valid while the regions are switched', async () => {
    form.controls.openShiftTargets.controls.RD.patchValue({
      projectBuild: 'cert-build',
      buildConfigPath: 'openshift/buildconfig.yaml',
      dockerFilePath: 'openshift/Dockerfile',
      buildContext: 'target/docker',
      dockerRepoPush: 'nexus.bbh.com:18444/cert',
      nexusAuthFile: '/etc/containers/auth.json',
    });
    const section = visibleSections(form).find(({ label }) => label === 'OpenShift targets')!;

    for (const region of ['QC region', 'RD region', 'QC region']) {
      await choose(region);

      expect(page().querySelectorAll('.problem-mark'), region).toHaveLength(0);
      expect(form.controls.openShiftTargets.controls.QC.valid, region).toBe(true);
      expect(sectionInvalid(form, section), region).toBe(false);
    }
  });

  it('marks what a started QC region needs as required, and only while it is needed', async () => {
    const qc = form.controls.openShiftTargets.controls.QC;
    const needed = [
      'Deployment project',
      'Deployment file',
      'Image pulled from',
      'Configuration file',
    ];
    const marked = () => needed.filter((label) => field(label)?.querySelector('.required-marker'));
    await choose('QC region');

    expect(marked()).toEqual([]);

    qc.controls.healthCheckUrl.setValue('/health');
    await fixture.whenStable();

    expect(marked()).toEqual(needed);
    expect(field('Deployment project')!.querySelector('input')!.required).toBe(true);

    qc.controls.skipConfigDeploy.setValue(true);
    await fixture.whenStable();

    expect(marked()).toEqual(needed.slice(0, 3));

    qc.patchValue({ healthCheckUrl: '', skipConfigDeploy: false });
    await fixture.whenStable();

    expect(marked()).toEqual([]);
    expect(qc.valid).toBe(true);
  });

  it('explains which build tag each region deploys, with and without Nexus delivery', async () => {
    const note = () => page().querySelector('.note')?.textContent?.trim();
    const helpText = () => helpOf(field('Build tag')!);

    expect(note()).toBe('Optional; a run that builds the image replaces these values.');
    expect(helpText()).toBe(
      'The image tag RD deploys when the run builds no image. A run that builds the image replaces it.',
    );

    await choose('QC region');

    expect(note()).toBe('Optional; a build tag set here wins over the image the run built.');
    expect(helpText()).toBe(
      'Pins the image tag QC deploys, also when the run builds a new image. Left empty, QC deploys the RD build tag, which is the image the run built.',
    );

    form.controls.deployment.controls.nexusDeliveryDisabled.setValue(true);
    await fixture.whenStable();

    expect(note()).toBe(
      'Nexus delivery is disabled, so the run builds no image: the QC deployment uses this build tag or, without one, the RD build tag, and is skipped when neither is set.',
    );

    await choose('RD region');

    expect(note()).toBe(
      'Nexus delivery is disabled, so the run builds no image: the RD deployment uses this build tag and is skipped without one.',
    );
  });

  it('explains a deployment repository that is not a URL', async () => {
    const url = form.controls.openShiftTargets.controls.RD.controls.deploymentRepoUrl;
    url.setValue('not a url');
    url.markAsTouched();
    await fixture.whenStable();

    expect(page().textContent).toContain('Must be an http, https, ssh or git@ URL');
  });
});
