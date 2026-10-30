import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import {
  buttonOf,
  checkboxOf,
  fieldOf,
  helpOf,
  text,
  toast,
  unexplained,
} from '@common/testing/dom';
import { globalSettings } from '../testing/fixtures';
import { GlobalSettingsPage } from './global-settings';

describe('GlobalSettingsPage', () => {
  let fixture: ComponentFixture<GlobalSettingsPage>;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [GlobalSettingsPage],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(GlobalSettingsPage);
  });

  afterEach(() => http.verify());

  const settingsPage = () => fixture.componentInstance;
  const form = () => settingsPage()['form'];
  const page = () => fixture.nativeElement as HTMLElement;

  async function load() {
    await fixture.whenStable();
    http.expectOne({ method: 'GET', url: '/api/settings' }).flush(globalSettings());
    await fixture.whenStable();
  }

  async function submit() {
    page().querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    await fixture.whenStable();
  }

  it('says who should change the defaults, what a service may replace and lists its sections', async () => {
    await load();

    expect(page().querySelector('h1')).toBeNull();
    expect(text(page().querySelector('.tab-header .lead'))).toBe(
      'Only change these settings if the DevSecOps team asks you to. They apply to every pipeline from its next run.',
    );
    expect(text(page().querySelector('.tab-header .meta'))).toBe(
      'The BBH tools every pipeline uses, the security limits it must meet and the defaults a new service starts with. A service can set its own SonarQube, Nexus IQ, InfluxDB, deployment, service and GoldenFix values in the product editor. Fields marked * are required.',
    );
    expect(
      [...page().querySelectorAll('.toc-item')].map((item) => item.textContent?.trim()),
    ).toEqual([
      'Tools and servers',
      'Deployment defaults',
      'Security limits',
      'Scans and coverage',
      'Release gate',
      'Service defaults',
      'GoldenFix defaults',
    ]);
    expect(
      [...page().querySelectorAll('section.section')].map((section) => [
        text(section.querySelector('h2')),
        !!text(section.querySelector('.section-head .section-help')),
      ]),
    ).toEqual([...page().querySelectorAll('.toc-item')].map((item) => [text(item), true]));
    const hint = (label: string) => text(fieldOf(page(), label)?.querySelector('dso-hint'));
    expect(hint('Jenkins URL')).toBe(
      'The portal links the Jenkins job of each pipeline from it · platform.jenkinsUrl',
    );
    expect(hint('OIS host')).toBe('platform.oisHost');
    expect(hint('Result file')).toBe(
      'Kept with each build as proof of the check; the library reads only this name · releaseGate.stateFile',
    );
    expect(page().querySelector(':not(.dso-help-trigger) > svg-icon')).toBeNull();
    expect(text(page().querySelector('.save-bar .saved'))).toMatch(/^Last saved .+ \(version 4\)$/);
    expect(form().controls.platform.controls.jenkinsUrl.value).toBe('https://jenkins.bbh.com');
    expect(settingsPage().hasUnsavedChanges()).toBe(false);
  });

  it('explains every field, scanner and the gate scans with a help text', async () => {
    await load();

    expect(unexplained(page())).toEqual([]);
    expect(helpOf(fieldOf(page(), 'OIS host')!)).toBe(
      'The host name of the Safeguard appliance the pipelines fetch the proxy password from. Left empty, the library uses oisapi.bbh.com.',
    );
    expect(
      [...page().querySelectorAll('.limits-row .scanner')].map((scanner) => !!helpOf(scanner)),
    ).toEqual([true, true, true, true]);
    expect(helpOf(page().querySelector('.gate .field-label')!)).toContain(
      'The scans whose findings the gate compares with their security limits.',
    );
  });

  it('measures the line coverage of a service with several coverage reports by their average', async () => {
    await load();

    expect(helpOf(fieldOf(page(), 'Minimum line coverage (%)')!)).toContain(
      'A service with several coverage reports is measured by the average of its reports.',
    );
    expect(text(page().querySelector('#settings-releaseGate .section-help'))).toContain(
      'when the line coverage of a service is below the minimum',
    );
    expect(
      helpOf(checkboxOf(page(), 'Also require the minimum line coverage').closest('dso-checkbox')!),
    ).toContain(
      'Lists each service whose line coverage, the average of its coverage reports, is below the minimum',
    );
  });

  it('runs Nexus IQ and SonarQube while AppScan scans unless the box under Speed is unticked', async () => {
    await load();
    const section = page().querySelector('#settings-scans')!;
    const block = [...section.querySelectorAll('.form-block')].find(
      (candidate) => text(candidate.querySelector('h3')) === 'Speed',
    )!;
    const box = checkboxOf(block, 'Run Nexus IQ and SonarQube while AppScan scans');

    expect(text(section.querySelector('.section-help'))).toBe(
      'How much of the code the unit tests must cover, how long the pipelines wait for each scan before they give up, and the order of the static scans. The waiting times rarely need to change.',
    );
    expect(text(block.querySelector('header p'))).toBe(
      'How the full and security pipelines order their static scans.',
    );
    expect(text(block)).toContain('staticAnalysis.parallel');
    expect(helpOf(box.closest('dso-checkbox')!)).toBe(
      'Ticked, the full and security pipelines first send the code of every service to AppScan. Nexus IQ and SonarQube then run while AppScan scans it, and the SAST results are read last. The pipeline finishes sooner; the results, the report and the release gate stay the same. Jenkins shows the three scans as one stage, Static analysis, and the build log lists them in this order. If SAST stops before the code is sent, Nexus IQ and SonarQube do not run in that build. Unticked, Nexus IQ, SAST and SonarQube run one after another, as before.',
    );
    expect(box.checked).toBe(true);

    box.click();
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/settings' });
    expect(request.request.body.scans).toEqual({
      ...globalSettings().scans,
      parallelStaticAnalysis: false,
    });
    request.flush(globalSettings());
    await fixture.whenStable();
  });

  it('shows why the settings cannot be read and loads them again on request', async () => {
    await fixture.whenStable();
    http
      .expectOne('/api/settings')
      .flush({ detail: 'Database unavailable' }, { status: 503, statusText: 'Unavailable' });
    await fixture.whenStable();

    expect(page().querySelector('.banner')?.textContent).toContain('Database unavailable');
    expect(page().querySelector('form')).toBeNull();

    expect(text(page().querySelector('.banner .banner-text'))).toBe(
      'The library defaults could not be loaded. Database unavailable',
    );
    buttonOf(page().querySelector('.banner')!, 'Try again').click();
    await load();
    expect(page().querySelector('form')).not.toBeNull();
  });

  it('saves the changes with the version they were made at', async () => {
    await load();
    form().controls.scans.controls.coverageMinLine.setValue(70);
    form().markAsDirty();
    expect(settingsPage().hasUnsavedChanges()).toBe(true);

    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/settings' });
    expect(request.request.body.version).toBe(4);
    expect(request.request.body.scans.coverageMinLine).toBe(70);
    request.flush(
      globalSettings({
        version: 5,
        scans: { ...globalSettings().scans, coverageMinLine: 70 },
      }),
    );
    await fixture.whenStable();

    expect(settingsPage().hasUnsavedChanges()).toBe(false);
    expect(text(page().querySelector('.save-bar .saved'))).toContain('(version 5)');
    expect(text(toast())).toContain(
      'Library defaults saved. Every pipeline gets them the next time it runs.',
    );
  });

  it('offers the Node.js build defaults with the service defaults and sends a blank one as null', async () => {
    await load();
    const section = page().querySelector('#settings-serviceDefaults')!;
    const hint = (label: string) => text(fieldOf(section, label)?.querySelector('dso-hint'));

    expect(text(section.querySelector('.form-block h3'))).toBe('Node.js build');
    expect(hint('Node.js installation')).toBe('build.node.installationName');
    expect(hint('npm registry')).toBe('build.node.registry');
    expect(hint('.npmrc credentials ID')).toBe('build.node.npmrcCredentialsId');
    expect(helpOf(fieldOf(section, '.npmrc credentials ID')!)).toContain(
      'which npm reads as its user configuration',
    );

    form().controls.serviceDefaults.patchValue({
      nodeInstallationName: ' ',
      npmRegistry: 'https://tools.bbh.com/nexus/repository/npm-group/',
    });
    await submit();

    const request = http.expectOne({ method: 'PUT', url: '/api/settings' });
    expect(request.request.body.serviceDefaults).toMatchObject({
      nodeInstallationName: null,
      npmRegistry: 'https://tools.bbh.com/nexus/repository/npm-group/',
      npmrcCredentialsId: null,
    });
    request.flush(globalSettings());
    await fixture.whenStable();
  });

  it('sends nothing while values are invalid and marks their section', async () => {
    await load();
    form().controls.deployment.controls.rdHost.setValue('');

    await submit();

    http.expectNone({ method: 'PUT', url: '/api/settings' });
    expect(page().querySelector('.save-error')?.textContent).toContain(
      'Some fields need your attention.',
    );
    expect(page().querySelector('.toc-item.problem')?.textContent).toContain('Deployment defaults');
  });

  it('explains a concurrent change and reloads the current settings', async () => {
    await load();
    form().controls.scans.controls.coverageMinLine.setValue(70);
    form().markAsDirty();
    await submit();

    http
      .expectOne({ method: 'PUT', url: '/api/settings' })
      .flush(
        { detail: 'The record was changed by someone else.' },
        { status: 409, statusText: 'Conflict' },
      );
    await fixture.whenStable();

    const banner = page().querySelector('.banner.conflict');
    expect(banner?.textContent).toContain(
      'Someone else saved the settings after you opened this page.',
    );
    expect(page().querySelector<HTMLButtonElement>('button[type=submit]')!.disabled).toBe(true);

    banner!.querySelector<HTMLButtonElement>('button')!.click();
    await fixture.whenStable();
    http.expectOne({ method: 'GET', url: '/api/settings' }).flush(globalSettings({ version: 6 }));
    await fixture.whenStable();

    expect(page().querySelector('.banner.conflict')).toBeNull();
    expect(form().controls.scans.controls.coverageMinLine.value).toBe(60);
    expect(text(page().querySelector('.save-bar .saved'))).toContain('(version 6)');
  });

  it('marks the values the API refused and lists the problems without a field', async () => {
    await load();
    form().markAsDirty();
    await submit();

    http.expectOne({ method: 'PUT', url: '/api/settings' }).flush(
      {
        detail: 'The request has invalid values',
        errors: [
          { field: 'limits[DAST].maxHigh', message: 'must be at most 100000' },
          { field: 'unknownField', message: 'is not supported' },
        ],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(form().controls.limits.controls.DAST.controls.maxHigh.errors).toEqual({
      server: 'must be at most 100000',
    });
    expect(page().querySelector('.problems')?.textContent).toContain(
      'unknownField: is not supported',
    );
    expect(page().querySelector('.toc-item.problem')?.textContent).toContain('Security limits');
    expect(page().querySelector('.save-error')?.textContent).toContain(
      'The portal did not accept some values. They are marked in the form.',
    );
  });

  it('toggles the scanners the release gate checks and needs at least one', async () => {
    await load();
    const group = page().querySelector<HTMLElement>(
      'dso-toggle-group[aria-label="Scans the release gate checks"]',
    )!;
    const toggles = () => [...group.querySelectorAll('button')];
    const pressed = () => toggles().map((toggle) => toggle.getAttribute('aria-pressed'));

    expect(toggles().map(text)).toEqual(['SAST', 'SCA', 'Nexus IQ', 'DAST']);
    expect(pressed()).toEqual(['true', 'true', 'true', 'true']);

    toggles().forEach((toggle) => toggle.click());
    await fixture.whenStable();

    expect(form().controls.releaseGate.controls.scanners.value).toEqual([]);
    expect(pressed()).toEqual(['false', 'false', 'false', 'false']);
    expect(text(page().querySelector('.gate .field-error'))).toBe('Select at least one scanner');
    expect(settingsPage().hasUnsavedChanges()).toBe(true);

    buttonOf(group, 'Nexus IQ').click();
    await fixture.whenStable();

    expect(form().controls.releaseGate.controls.scanners.value).toEqual(['NEXUS_IQ']);
    expect(pressed()).toEqual(['false', 'false', 'true', 'false']);
    expect(page().querySelector('.gate .field-error')).toBeNull();
  });

  it('discards the changes made on the page', async () => {
    await load();
    form().controls.platform.controls.jenkinsLibrary.setValue('Other');
    form().markAsDirty();
    await fixture.whenStable();

    page().querySelectorAll<HTMLButtonElement>('.save-bar button')[0].click();
    await fixture.whenStable();

    expect(form().controls.platform.controls.jenkinsLibrary.value).toBe('DevSecOpsJenkinsLibrary');
    expect(settingsPage().hasUnsavedChanges()).toBe(false);
  });

  it('previews the generated global configuration', async () => {
    await load();
    const open = vi
      .spyOn(TestBed.inject(Dialog), 'open')
      .mockReturnValue({ closed: of(undefined) } as unknown as DialogRef<unknown>);

    buttonOf(page().querySelector('.tab-header')!, 'Show settings sent to Jenkins').click();
    await fixture.whenStable();
    const request = http.expectOne((r) => r.url === '/api/settings/config');
    expect(request.request.params.get('format')).toBe('yaml');
    request.flush('platform:\n  jenkinsLibrary: DevSecOpsJenkinsLibrary\n');
    await fixture.whenStable();

    expect(open).toHaveBeenCalledTimes(1);
    expect(open.mock.calls[0][1]?.data).toMatchObject({
      title: 'Shared settings sent to Jenkins (devsecops_config.yaml format)',
      subtitle:
        'The part of the settings every pipeline with a key receives along with the settings of its own ' +
        'service, built from the saved library defaults. A Jenkinsfile without a key receives only the ' +
        'defaults part, from the copy kept in the DevSecOps library; it never receives the platform part.',
      code: 'platform:\n  jenkinsLibrary: DevSecOpsJenkinsLibrary\n',
      fileName: 'devsecops-global.yaml',
    });
  });

  it('scrolls to a section chosen in its table of contents', async () => {
    await load();
    const section = page().querySelector<HTMLElement>('#settings-releaseGate')!;
    const scroll = vi.fn();
    section.scrollIntoView = scroll;

    [...page().querySelectorAll<HTMLButtonElement>('.toc-item')]
      .find((item) => item.textContent?.includes('Release gate'))!
      .click();

    expect(scroll).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
  });
});
