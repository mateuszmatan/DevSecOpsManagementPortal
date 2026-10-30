import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA, Dialog, DialogRef } from '@angular/cdk/dialog';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { BuildTool, DeployTarget, Service, TestJob } from './core/models';
import {
  department,
  globalSettings,
  nodeBuild,
  service,
  servicePipelines,
  serviceTemplate,
} from './testing/fixtures';
import { GoldenFixFields } from './products/golden-fix-fields';
import { PipelineDialog } from './products/pipeline-dialog';
import { ProductEditor } from './products/product-editor';
import { createGlobalGoldenFixForm, createServiceForm } from './products/product-form-model';
import { ServiceFields } from './products/service-fields';
import {
  GOLDEN_FIX_ENABLED,
  LIMIT_FIELDS,
  RELEASE_GATE_FIELDS,
  SETTINGS_PAGE,
} from './settings/settings-fields';

declare const process: {
  getBuiltinModule(id: 'node:fs'): { readFileSync(path: string, encoding: 'utf8'): string };
};

/** The user guide, read from the frontend folder the unit tests run in. */
const GUIDE = process
  .getBuiltinModule('node:fs')
  .readFileSync('../docs/documentation.md', 'utf8');

/** The codes the guide does not name as inline code, such as `build.node.enabled`. */
const undocumented = (codes: Iterable<string>) =>
  [...new Set(codes)].filter((code) => !GUIDE.includes(`\`${code}\``)).sort();

const chipsIn = (root: ParentNode) =>
  [...root.querySelectorAll('code')].map((code) => code.textContent?.trim() ?? '').filter(Boolean);

async function settle(fixture: ComponentFixture<unknown>): Promise<void> {
  fixture.detectChanges();
  await fixture.whenStable();
}

describe('docs/documentation.md', () => {
  const job = (stage: TestJob['stage'], name: string, overrides: Partial<TestJob> = {}) => ({
    ...service().testJobs[0],
    stage,
    name,
    job: `CERT/${name}`,
    ...overrides,
  });

  /** A service that shows every field of its build tool and deployment target. */
  function everyField(tool: BuildTool, target: DeployTarget): Service {
    const stored = service();
    return service({
      build: { ...stored.build, tool, autoSetup: false },
      nodeBuild: nodeBuild({ enabled: true }),
      deployment: { ...stored.deployment, target },
      appScan: { ...stored.appScan, compile: true, dastEnabled: true },
      goldenFix: { ...stored.goldenFix, minThreatLevel: 7 },
      testJobs: [
        job('SMOKE', 'smoke'),
        job('REGRESSION', 'regression', { type: 'REMOTE', remoteJenkins: 'qa' }),
        job('PERFORMANCE', 'performance', {
          type: 'REMOTE',
          job: 'https://perf-jenkins.bbh.com/job/cert-performance',
        }),
      ],
    });
  }

  /** The codes of every section of the service form, Advanced settings and both regions included. */
  async function serviceCodes(stored: Service): Promise<string[]> {
    TestBed.configureTestingModule({ imports: [ServiceFields] });
    const fixture = TestBed.createComponent(ServiceFields);
    fixture.componentRef.setInput('form', createServiceForm(stored));
    fixture.componentRef.setInput('productCode', 'CERT');
    fixture.componentRef.setInput('defaults', globalSettings());
    await settle(fixture);
    const page = fixture.nativeElement as HTMLElement;
    const pane = () => page.querySelector<HTMLElement>('.pane')!;
    const codes: string[] = [];
    const sections = page.querySelectorAll('.rail-item').length;
    for (let index = 0; index < sections; index++) {
      page.querySelectorAll<HTMLButtonElement>('.rail-item')[index].click();
      await settle(fixture);
      pane()
        .querySelectorAll<HTMLButtonElement>('dso-advanced-settings .accordion-button.collapsed')
        .forEach((toggle) => toggle.click());
      await settle(fixture);
      codes.push(...chipsIn(pane()));
      for (const region of pane().querySelectorAll<HTMLButtonElement>('.regions button')) {
        region.click();
        await settle(fixture);
        codes.push(...chipsIn(pane()));
      }
    }
    return codes;
  }

  it.each<[BuildTool, DeployTarget]>([
    ['GRADLE', 'VM'],
    ['GRADLE', 'OPENSHIFT'],
    ['MAVEN', 'VM'],
    ['MAVEN', 'OPENSHIFT'],
    ['FLUTTER', 'VM'],
    ['FLUTTER', 'OPENSHIFT'],
  ])(
    'names every devsecops_config.yaml key of a %s service on %s',
    async (tool, target) => {
      const codes = await serviceCodes(everyField(tool, target));

      expect(codes.length).toBeGreaterThan(100);
      expect(codes).toContain('goldenFix.versionCatalogs');
      expect(undocumented(codes)).toEqual([]);
    },
    30_000,
  );

  it('names every devsecops_config.yaml key of the Library defaults', async () => {
    TestBed.configureTestingModule({ imports: [GoldenFixFields] });
    const fixture = TestBed.createComponent(GoldenFixFields);
    fixture.componentRef.setInput('group', createGlobalGoldenFixForm(globalSettings().goldenFix));
    fixture.componentRef.setInput('complete', true);
    await settle(fixture);
    const fields = [
      ...SETTINGS_PAGE.flatMap((section) => [
        ...(section.fields ?? []),
        ...(section.blocks ?? []).flatMap((block) => block.fields),
      ]),
      ...LIMIT_FIELDS,
      ...RELEASE_GATE_FIELDS,
      ...GOLDEN_FIX_ENABLED,
    ];
    const codes = [
      ...fields.flatMap((field) => (field.code ? [field.code] : [])),
      ...chipsIn(fixture.nativeElement as HTMLElement),
    ];

    expect(codes).toContain('build.node.installationName');
    expect(codes).toContain('verify.enabled');
    expect(undocumented(codes)).toEqual([]);
  });

  it('names every devsecops_config.yaml key of a product and its pipelines', async () => {
    TestBed.configureTestingModule({
      imports: [ProductEditor, PipelineDialog],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: DIALOG_DATA, useValue: { service: servicePipelines({ pipelines: [] }) } },
        { provide: DialogRef, useValue: { close: vi.fn() } },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    const editor = TestBed.createComponent(ProductEditor);
    vi.spyOn(TestBed.inject(Dialog), 'open').mockReturnValue({
      closed: of({ name: 'CertScanner', departmentId: 3 }),
    } as unknown as DialogRef<unknown>);
    await settle(editor);
    http.expectOne('/api/departments').flush([department()]);
    http.expectOne('/api/settings').flush(globalSettings());
    http.expectOne('/api/service-template').flush(serviceTemplate());
    http
      .expectOne((request) => request.url === '/api/products/code-suggestion')
      .flush({ code: 'CERTSCANNER' });
    await settle(editor);
    const codes = chipsIn((editor.nativeElement as HTMLElement).querySelector('.product-fields')!);

    const dialog = TestBed.createComponent(PipelineDialog);
    http.expectOne('/api/service-template').flush(serviceTemplate());
    await settle(dialog);
    for (const type of ['FULL', 'SECURITY', 'EXTENDED', 'SAST', 'NEXUS_IQ'] as const) {
      dialog.componentInstance['form'].controls.type.setValue(type);
      await settle(dialog);
      codes.push(...chipsIn(dialog.nativeElement as HTMLElement));
    }
    http.verify();

    expect(codes).toEqual(expect.arrayContaining(['pipeline.agentNames', 'pipeline.securityPipeline']));
    expect(undocumented(codes)).toEqual([]);
  });
});
