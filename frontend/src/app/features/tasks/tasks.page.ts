import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Page, Task, UserRef } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { canChange } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';
import { TaskDialogComponent } from '../shared/task-dialog.component';

/**
 * Task list. Defaults to "my open tasks"; ?assignee=all shows the team, ?due=OVERDUE|TODAY|WEEK narrows by date.
 * قائمة المهام. الافتراضي "مهامي المفتوحة"؛ ?assignee=all يعرض الفريق، و?due يحدد حسب التاريخ.
 */
@Component({
  selector: 'app-tasks',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent, TaskDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './tasks.page.html',
})
export class TasksPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);
  protected readonly dialog = viewChild.required(TaskDialogComponent);

  readonly assignee = input<string | undefined>('');
  readonly due = input<string | undefined>('');
  readonly status = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  protected readonly team = rxResource({ stream: () => this.api.get<UserRef[]>('/team') });
  // "" = me (default), "all" = everyone, otherwise a user id. / "" = أنا، "all" = الكل، وإلا رقم مستخدم.
  protected readonly assigneeId = computed(() => {
    const value = this.assignee() ?? '';
    if (value === 'all') return null;
    return value ? Number(value) : (this.auth.user()?.id ?? null);
  });
  // Open tasks by default; ?status=all shows done ones too. / المفتوحة افتراضيًا؛ و?status=all يعرض المنجزة بعد.
  protected readonly statusFilter = computed(() => {
    const value = this.status() ?? '';
    return value === 'all' ? null : value || 'OPEN';
  });

  protected readonly tasks = rxResource({
    params: () => ({
      assigneeId: this.assigneeId(),
      due: this.due(),
      status: this.statusFilter(),
      page: Number(this.page() ?? 0) || 0,
      size: 20,
    }),
    stream: ({ params }) => this.api.get<Page<Task>>('/tasks', params),
  });
  protected readonly errorText = computed(() =>
    this.tasks.error() ? errorMessage(this.i18n, toApiError(this.tasks.error())) : null,
  );

  protected setFilter(change: Record<string, string | number | null>): void {
    const pageChange = 'page' in change;
    void this.router.navigate([], {
      queryParams: { ...change, ...(pageChange ? {} : { page: null }) },
      queryParamsHandling: 'merge',
      replaceUrl: !pageChange,
    });
  }

  protected canEdit(task: Task): boolean {
    return canChange(this.auth.user(), [task.assignee, task.createdBy]);
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

  protected async remove(task: Task): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: task.title }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/tasks/${task.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        this.tasks.reload();
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
