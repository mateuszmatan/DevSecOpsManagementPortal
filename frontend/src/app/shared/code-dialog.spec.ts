import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { buttonOf, toast } from '@common/testing/dom';
import { CodeDialog, CodeDialogData } from './code-dialog';

describe('CodeDialog', () => {
  let fixture: ComponentFixture<CodeDialog>;

  async function render(data: CodeDialogData) {
    TestBed.configureTestingModule({
      imports: [CodeDialog],
      providers: [{ provide: DIALOG_DATA, useValue: data }],
    });
    fixture = TestBed.createComponent(CodeDialog);
    await fixture.whenStable();
  }

  const page = () => fixture.nativeElement as HTMLElement;

  it('shows the code and downloads it under its file name', async () => {
    await render({
      title: 'config.yaml',
      subtitle: 'Every service',
      code: 'projects: {}',
      fileName: 'cert.yaml',
    });
    const createObjectURL = vi.fn(() => 'blob:config');
    const revokeObjectURL = vi.fn();
    vi.stubGlobal('URL', { ...URL, createObjectURL, revokeObjectURL });
    const click = vi
      .spyOn(HTMLAnchorElement.prototype, 'click')
      .mockImplementation(() => undefined);

    expect(page().querySelector('.subtitle')?.textContent).toBe('Every service');
    expect(page().querySelector('pre')?.textContent).toBe('projects: {}');
    buttonOf(page(), 'Download')!.click();

    expect(createObjectURL).toHaveBeenCalled();
    expect(click).toHaveBeenCalled();
    expect(revokeObjectURL).toHaveBeenCalledWith('blob:config');
    vi.unstubAllGlobals();
    click.mockRestore();
  });

  it('copies the code and says so, without a download for code without a file name', async () => {
    await render({ title: 'Jenkinsfile', code: "@Library('dso') _" });

    expect(page().querySelector('.subtitle')).toBeNull();
    expect(buttonOf(page(), 'Download')).toBeUndefined();
    buttonOf(page(), 'Copy')!.click();
    await fixture.whenStable();

    expect(toast()?.textContent).toContain('Copied to the clipboard');
  });
});
