import { ClipboardModule } from '@angular/cdk/clipboard';
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, inject, output, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { finalize } from 'rxjs';
import { PipelinesApi } from '../core/api';
import { errorMessage } from '@common/core/errors';
import { Pipeline, PipelineKey } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { pipelineName } from '../pipelines/pipeline-texts';
import { RelativeTimePipe } from '@common/shared/formatting';
import { DIALOG } from '@common/ui/dialog';
import { GRID, GridColumn } from '@common/ui/grid';
import { DsoLoading } from '@common/ui/loading';

const status = (key: PipelineKey) => (key.status === 'ACTIVE' ? 'Active' : 'Invalidated');

@Component({
  selector: 'dso-key-history-dialog',
  imports: [ClipboardModule, DatePipe, DIALOG, GRID, DsoLoading, RelativeTimePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="modal-header">
      <h2 dsoDialogTitle>Key history of the pipeline {{ name }}</h2>
    </div>
    <div class="modal-body">
      <p class="intro">
        Every key the pipeline of <strong class="mono">{{ data.serviceName }}</strong> in
        {{ data.productName }} has had, by its first and last characters. Only the active key works;
        an invalidated key cannot be used again.
      </p>
      @if (pipeline.isLoading()) {
        <dso-loading />
      }
      @if (pipeline.error(); as error) {
        <div class="banner">{{ errorMessage(error) }}</div>
      } @else if (pipeline.hasValue()) {
        @if (pipeline.value().activeKey === null) {
          <div class="key-status row-wrap">
            <span class="chip danger">Key invalidated</span>
            <span class="muted"
              >The pipeline is refused its settings until its key is regenerated.</span
            >
            <button
              type="button"
              class="text-link"
              (click)="regenerate()"
              [disabled]="regenerating()"
            >
              {{ regenerating() ? 'Regenerating…' : 'Regenerate key' }}
            </button>
          </div>
        } @else if (issuedKey(); as value) {
          <div class="key-status row-wrap" role="status">
            <span class="chip success">New key</span>
            <span class="mono key-value">{{ value }}</span>
            <button
              type="button"
              class="text-link"
              [cdkCopyToClipboard]="value"
              (cdkCopyToClipboardCopied)="copied()"
            >
              Copy the key
            </button>
            <span class="muted">Put it in the service's Jenkinsfile.</span>
          </div>
        }
        @if (regenerateError(); as error) {
          <div class="banner">{{ error }}</div>
        }
        <dso-grid
          label="Keys"
          [rows]="pipeline.value().keys ?? []"
          [columns]="columns"
          [rowId]="keyId"
        >
          <ng-template dsoCell="key" let-key>
            <span class="mono">{{ key.hint }}</span>
          </ng-template>
          <ng-template dsoCell="status" let-key>
            <span class="chip" [class]="key.status === 'ACTIVE' ? 'success' : 'danger'">{{
              status(key)
            }}</span>
          </ng-template>
          <ng-template dsoCell="issuedAt" let-key>
            <span [title]="key.issuedAt | date: 'medium'">{{
              key.issuedAt | date: 'd MMM y, HH:mm'
            }}</span>
          </ng-template>
          <ng-template dsoCell="lastUsedAt" let-key>
            @if (key.lastUsedAt) {
              {{ key.lastUsedAt | relative }}
            } @else {
              <span class="muted">Never</span>
            }
          </ng-template>
          <ng-template dsoCell="revoked" let-key>
            @if (key.revokedAt) {
              <div>
                <div>{{ key.revokedAt | date: 'd MMM y, HH:mm' }}</div>
                <div class="muted reason">{{ key.revokeReason }}</div>
              </div>
            } @else {
              <span class="muted">–</span>
            }
          </ng-template>
        </dso-grid>
      }
    </div>
    <div class="modal-footer">
      <button type="button" class="btn btn-primary" dsoDialogClose>Close</button>
    </div>
  `,
  styles: `
    .modal-body {
      width: min(860px, 86vw);
    }
    .intro {
      margin: 0 0 12px;
      color: var(--dso-muted);
    }
    .key-status {
      margin-bottom: 10px;
      font-size: 12.5px;
    }
    .reason {
      font-size: 12.5px;
    }
  `,
})
export class KeyHistoryDialog {
  readonly keyIssued = output<Pipeline>();

  protected readonly data = inject<Pipeline>(DIALOG_DATA);
  protected readonly name = pipelineName(this.data);
  private readonly api = inject(PipelinesApi);
  private readonly notifier = inject(Notifier);

  protected readonly status = status;
  protected readonly keyId = (key: PipelineKey) => key.id;
  protected readonly columns: GridColumn<PipelineKey>[] = [
    { key: 'key', header: 'Key', value: (key) => key.hint, width: 150 },
    { key: 'status', header: 'Status', value: status, width: 120 },
    { key: 'issuedAt', header: 'Issued', value: (key) => key.issuedAt, width: 150 },
    {
      key: 'lastUsedAt',
      header: 'Last used by Jenkins',
      value: (key) => key.lastUsedAt,
      width: 180,
    },
    {
      key: 'revoked',
      header: 'Invalidated',
      value: (key) => key.revokedAt,
      minWidth: 200,
      wrap: true,
    },
  ];
  protected readonly pipeline = rxResource({ stream: () => this.api.get(this.data.id) });
  protected readonly regenerating = signal(false);
  protected readonly regenerateError = signal<string | null>(null);
  protected readonly issuedKey = signal<string | null>(null);
  protected readonly errorMessage = errorMessage;

  protected regenerate(): void {
    this.regenerating.set(true);
    this.regenerateError.set(null);
    this.api
      .issueKey(this.data.id)
      .pipe(finalize(() => this.regenerating.set(false)))
      .subscribe({
        next: (updated) => {
          this.issuedKey.set(updated.activeKey?.value ?? null);
          if (updated.keys === null) {
            this.pipeline.reload();
          } else {
            this.pipeline.set(updated);
          }
          this.keyIssued.emit(updated);
        },
        error: (error) => this.regenerateError.set(errorMessage(error)),
      });
  }

  protected copied(): void {
    this.notifier.success('Key copied to the clipboard');
  }
}
