import { ChangeDetectionStrategy, Component, inject, input, viewChild } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Page, Task } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { canChange } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { TaskDialogComponent } from './task-dialog.component';

/**
 * Tasks of one deal or one company, with "add" and "done" toggles. Open tasks are listed first.
 * مهام صفقة أو شركة وحدة، مع إضافة وتعليم كمنجز. المفتوحة تظهر أولًا.
 */
@Component({
  selector: 'app-task-panel',
  imports: [TranslatePipe, IconComponent, TaskDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card" aria-labelledby="task-panel-title">
      <div class="card__header">
        <h2 id="task-panel-title">{{ 'nav.tasks' | t }}</h2>
        <button type="button" class="btn btn--small" (click)="add()" data-testid="add-task">
          <app-icon name="plus" /> {{ 'tasks.new' | t }}
        </button>
      </div>
      @if (tasks.error()) {
        <p class="alert alert--danger" role="alert">{{ 'app.loadFailed' | t }}</p>
      } @else if (tasks.value(); as page) {
        <ul class="list">
          @for (task of page.content; track task.id) {
            <li class="task-row" [class.task-row--done]="task.status === 'DONE'">
              <button
                type="button"
                class="icon-btn"
                [disabled]="!canEdit(task)"
                (click)="toggle(task)"
                [attr.aria-pressed]="task.status === 'DONE'"
                [attr.aria-label]="
                  (task.status === 'DONE' ? 'tasks.reopen' : 'tasks.complete')
                    | t: { title: task.title }
                "
              >
                <app-icon [name]="task.status === 'DONE' ? 'checkSquare' : 'square'" />
              </button>
              <div class="task-row__main">
                <span class="task-row__title"
                  ><bdi>{{ task.title }}</bdi></span
                >
                <div class="muted">
                  <span [class.overdue]="task.overdue">{{ fmt.day(task.dueDate) }}</span>
                  · <bdi>{{ task.assignee.fullName }}</bdi>
                  @if (task.overdue) {
                    <span class="badge badge--overdue">{{ 'tasks.overdue' | t }}</span>
                  }
                </div>
              </div>
              <span [class]="'badge badge--' + task.priority">{{
                'priority.' + task.priority | t
              }}</span>
              @if (canEdit(task)) {
                <button
                  type="button"
                  class="icon-btn"
                  (click)="dialog().open(task)"
                  [attr.aria-label]="('common.edit' | t) + ' ' + task.title"
                >
                  <app-icon name="edit" />
                </button>
              }
            </li>
          } @empty {
            <li class="muted">{{ 'tasks.empty' | t }}</li>
          }
        </ul>
      }
    </section>
    <app-task-dialog (saved)="tasks.reload()" />
  `,
})
export class TaskPanelComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly fmt = inject(FormatService);
  protected readonly dialog = viewChild.required(TaskDialogComponent);

  readonly companyId = input<number | null>(null);
  readonly dealId = input<number | null>(null);

  protected readonly tasks = rxResource({
    params: () => ({ companyId: this.companyId(), dealId: this.dealId() }),
    // Open first ("OPEN" sorts after "DONE", so descending), then soonest due.
    // المفتوحة أولًا ("OPEN" بعد "DONE" أبجديًا، فالترتيب تنازلي)، ثم الأقرب استحقاقًا.
    stream: ({ params }) =>
      this.api.get<Page<Task>>('/tasks', {
        ...params,
        size: 50,
        sort: ['status,desc', 'dueDate,asc', 'id,asc'],
      }),
  });

  protected canEdit(task: Task): boolean {
    return canChange(this.auth.user(), [task.assignee, task.createdBy]);
  }

  protected add(): void {
    this.dialog().open(null, { companyId: this.companyId(), dealId: this.dealId() });
  }

  protected toggle(task: Task): void {
    const action = task.status === 'DONE' ? 'reopen' : 'complete';
    this.api.post<Task>(`/tasks/${task.id}/${action}`, { version: task.version }).subscribe({
      next: () => {
        this.toast.success(
          this.i18n.t(action === 'complete' ? 'tasks.completed' : 'tasks.reopened'),
        );
        this.tasks.reload();
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
