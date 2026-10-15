import { Dialog } from '@angular/cdk/dialog';
import { Injectable, inject } from '@angular/core';
import { outputToObservable } from '@angular/core/rxjs-interop';
import { EMPTY, Observable, catchError, filter, map, of, switchMap, tap } from 'rxjs';
import { PipelinesApi, SettingsApi } from '../core/api';
import { Pipeline, pipelineTypeLabel, pipelineTypeSlug } from '../core/models';
import { Notifier } from '@common/core/notifier';
import { jenkinsfile } from '../products/jenkinsfile';
import { KeyHistoryDialog } from '../products/key-history-dialog';
import { PipelineDialog, PipelineDialogData } from '../products/pipeline-dialog';
import { RevokeKeyDialog } from '../products/revoke-key-dialog';
import { CodeDialog, CodeDialogData } from '../shared/code-dialog';
import { ConfirmDialog, ConfirmDialogData } from '@common/shared/confirm-dialog';
import { JENKINSFILE_HELP, pipelineName, typeName } from './pipeline-texts';

export { typeName } from './pipeline-texts';

@Injectable({ providedIn: 'root' })
export class PipelineActions {
  private readonly api = inject(PipelinesApi);
  private readonly settings = inject(SettingsApi);
  private readonly dialog = inject(Dialog);
  private readonly notifier = inject(Notifier);

  copied(what = 'Key'): void {
    this.notifier.success(`${what} copied to the clipboard`);
  }

  showConfig(pipeline: Pipeline): void {
    this.api.config(pipeline.id).subscribe({
      next: (code) =>
        this.openCode({
          title: `Settings sent to Jenkins for ${pipelineName(pipeline)} (config.yaml)`,
          subtitle:
            'What the Jenkins job of this pipeline receives when it fetches its settings with its key, in the ' +
            'config.yaml format of the DevSecOps library. Opening it here does not count as a use of the key.',
          code,
          fileName: `${pipeline.productCode.toLowerCase()}-${pipeline.serviceName}-${pipelineTypeSlug(pipeline.type)}.yaml`,
        }),
      error: (error) => this.notifier.error(error),
    });
  }

  showJenkinsfile(pipelines: readonly Pipeline[]): void {
    const together = pipelines.length > 1;
    const subtitle = together
      ? `One run builds ${pipelines.map((p) => p.serviceName).join(', ')}; the first key is that of the ` +
        "primary service. Put the file in the primary service's repository: it holds only the pipeline keys, " +
        'and Jenkins fetches every other setting from this portal.'
      : JENKINSFILE_HELP;
    this.settings
      .get()
      .pipe(catchError(() => of(null)))
      .subscribe((settings) =>
        this.openCode({
          title: together
            ? 'Jenkinsfile for several services'
            : `Jenkinsfile of ${pipelineName(pipelines[0])}`,
          subtitle,
          code: jenkinsfile(pipelines, settings?.platform.jenkinsLibrary),
          fileName: 'Jenkinsfile',
        }),
      );
  }

  add(data: PipelineDialogData): Observable<Pipeline> {
    return this.openPipelineDialog(
      data,
      (pipeline) => `${pipelineTypeLabel(pipeline.type)} pipeline added to ${pipeline.serviceName}`,
    );
  }

  edit(pipeline: Pipeline): Observable<Pipeline> {
    return this.openPipelineDialog(
      {
        service: {
          serviceId: pipeline.serviceId,
          serviceName: pipeline.serviceName,
          pipelines: [pipeline],
        },
        pipeline,
      },
      () => 'Pipeline settings saved. Jenkins uses them the next time the pipeline runs.',
    );
  }

  revokeKey(pipeline: Pipeline): Observable<Pipeline> {
    return this.dialog
      .open<Pipeline, Pipeline, RevokeKeyDialog>(RevokeKeyDialog, { data: pipeline })
      .closed.pipe(
        filter((updated): updated is Pipeline => !!updated),
        tap(() =>
          this.notifier.success(
            `Key invalidated. The pipeline ${pipelineName(pipeline)} is refused its settings and stops at its next start.`,
          ),
        ),
      );
  }

  replaceKey(pipeline: Pipeline): Observable<Pipeline> {
    return this.confirm({
      title: `Replace the key of the pipeline ${pipelineName(pipeline)}?`,
      message:
        'The key the pipeline uses now stops working at once and a new key is issued. Put the new key in the ' +
        "service's Jenkinsfile, or the pipeline is refused its settings and stops at its next start. " +
        'The old key cannot be used again.',
      confirmLabel: 'Replace key',
      danger: true,
    }).pipe(
      switchMap(() => this.issued(this.api.issueKey(pipeline.id))),
      tap(() =>
        this.notifier.success(
          `New key issued for ${pipelineName(pipeline)}. Put it in the service's Jenkinsfile.`,
        ),
      ),
    );
  }

  regenerateKey(pipeline: Pipeline): Observable<Pipeline> {
    return this.issued(this.api.issueKey(pipeline.id)).pipe(
      tap((updated) =>
        this.notifier.success(
          `${pipelineTypeLabel(updated.type)} pipeline of ${updated.serviceName} has a new key: ` +
            'pass it in the Jenkinsfile',
        ),
      ),
    );
  }

  showKeyHistory(pipeline: Pipeline): Observable<Pipeline> {
    return outputToObservable(
      this.dialog.open(KeyHistoryDialog, { data: pipeline, maxWidth: '95vw' }).componentInstance!
        .keyIssued,
    );
  }

  deletePipeline(pipeline: Pipeline): Observable<Pipeline> {
    return this.confirm({
      title: `Delete the pipeline ${pipelineName(pipeline)}?`,
      message:
        `The ${typeName(pipeline.type)} pipeline of ${pipeline.serviceName} and its key history are deleted. ` +
        'Its Jenkins job is refused its settings from now on and stops at its next start. This cannot be undone.',
      confirmLabel: 'Delete pipeline',
      danger: true,
    }).pipe(
      switchMap(() =>
        this.api.delete(pipeline.id).pipe(
          map(() => pipeline),
          catchError((error) => this.failed(error)),
        ),
      ),
      tap(() => this.notifier.success(`Pipeline ${pipelineName(pipeline)} deleted.`)),
    );
  }

  confirm(data: ConfirmDialogData): Observable<boolean> {
    return this.dialog
      .open<boolean, ConfirmDialogData, ConfirmDialog>(ConfirmDialog, { data })
      .closed.pipe(filter((confirmed) => confirmed === true));
  }

  openCode(data: CodeDialogData): void {
    this.dialog.open(CodeDialog, { data, width: '760px', maxWidth: '95vw' });
  }

  private openPipelineDialog(
    data: PipelineDialogData,
    message: (pipeline: Pipeline) => string,
  ): Observable<Pipeline> {
    return this.dialog
      .open<Pipeline, PipelineDialogData, PipelineDialog>(PipelineDialog, { data })
      .closed.pipe(
        filter((pipeline): pipeline is Pipeline => !!pipeline),
        tap((pipeline) => this.notifier.success(message(pipeline))),
      );
  }

  private issued(request: Observable<Pipeline>): Observable<Pipeline> {
    return request.pipe(catchError((error) => this.failed(error)));
  }

  private failed(error: unknown): Observable<never> {
    this.notifier.error(error);
    return EMPTY;
  }
}
