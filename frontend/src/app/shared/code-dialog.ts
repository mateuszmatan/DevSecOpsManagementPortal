import { DIALOG_DATA } from '@angular/cdk/dialog';
import { ClipboardModule } from '@angular/cdk/clipboard';
import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Notifier } from '@common/core/notifier';
import { DIALOG } from '@common/ui/dialog';

export interface CodeDialogData {
  title: string;
  subtitle?: string;
  code: string;
  fileName?: string;
}

@Component({
  selector: 'dso-code-dialog',
  imports: [DIALOG, ClipboardModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>{{ data.title }}</h2>
    </div>
    <div class="modal-body">
      @if (data.subtitle) {
        <p class="subtitle">{{ data.subtitle }}</p>
      }
      <pre class="code-block">{{ data.code }}</pre>
    </div>
    <div class="modal-footer">
      @if (data.fileName) {
        <button type="button" class="btn btn-link" (click)="download()">Download</button>
      }
      <button
        type="button"
        class="btn btn-link"
        [cdkCopyToClipboard]="data.code"
        (cdkCopyToClipboardCopied)="copied()"
      >
        Copy
      </button>
      <button type="button" class="btn btn-primary" dsoDialogClose>Close</button>
    </div>
  `,
  styles: `
    .subtitle {
      margin: 0 0 8px;
      color: var(--dso-muted);
    }
    .code-block {
      max-height: 60vh;
    }
  `,
})
export class CodeDialog {
  protected readonly data = inject<CodeDialogData>(DIALOG_DATA);
  private readonly notifier = inject(Notifier);

  protected copied(): void {
    this.notifier.info('Copied to the clipboard');
  }

  protected download(): void {
    const url = URL.createObjectURL(new Blob([this.data.code], { type: 'application/yaml' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = this.data.fileName ?? 'config.yaml';
    link.click();
    URL.revokeObjectURL(url);
  }
}
