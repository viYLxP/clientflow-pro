import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Task, TaskPriority, UserRef } from '../../core/api/models';
import { riyadhToday } from '../../core/crm';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ToastService } from '../../core/ui/toast.service';

/** What a new task is attached to (taken from the page that opens the dialog). / الشي اللي ترتبط فيه المهمة الجديدة. */
export interface TaskLinks {
  companyId?: number | null;
  dealId?: number | null;
}

/**
 * Create / edit a task in a native <dialog>. Used on the tasks page, the deal page and the company page.
 * إنشاء وتعديل مهمة داخل <dialog> أصلي. يُستخدم في صفحة المهام وصفحة الصفقة وصفحة الشركة.
 */
@Component({
  selector: 'app-task-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog" aria-labelledby="task-dialog-title" (close)="onClosed()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h2 id="task-dialog-title">{{ (editing() ? 'tasks.edit' : 'tasks.new') | t }}</h2>
        @if (errorText()) {
          <div class="alert alert--danger" role="alert">{{ errorText() }}</div>
        }
        <div class="form-grid">
          <div class="field form-grid--full">
            <label for="task-title">{{ 'tasks.taskTitle' | t }}</label>
            <input id="task-title" formControlName="title" aria-describedby="task-title-err" />
            <app-field-error
              id="task-title-err"
              [control]="form.controls.title"
              [server]="serverFields()['title']"
            />
          </div>
          <div class="field">
            <label for="task-due">{{ 'tasks.dueDate' | t }}</label>
            <input
              id="task-due"
              type="date"
              formControlName="dueDate"
              aria-describedby="task-due-err"
            />
            <app-field-error
              id="task-due-err"
              [control]="form.controls.dueDate"
              [server]="serverFields()['dueDate']"
            />
          </div>
          <div class="field">
            <label for="task-priority">{{ 'tasks.priority' | t }}</label>
            <select id="task-priority" formControlName="priority">
              @for (p of priorities; track p) {
                <option [value]="p">{{ 'priority.' + p | t }}</option>
              }
            </select>
          </div>
          <div class="field form-grid--full">
            <label for="task-assignee">{{ 'tasks.assignee' | t }}</label>
            <select id="task-assignee" formControlName="assigneeId">
              <option value="">{{ 'common.me' | t }}</option>
              @for (m of team.value() ?? []; track m.id) {
                <option [value]="'' + m.id">{{ m.fullName }}</option>
              }
            </select>
          </div>
          <div class="field form-grid--full">
            <label for="task-desc"
              >{{ 'tasks.description' | t }} <small>({{ 'common.optional' | t }})</small></label
            >
            <textarea id="task-desc" rows="3" formControlName="description"></textarea>
          </div>
        </div>
        <div class="dialog__actions">
          <button type="button" class="btn" (click)="close()">{{ 'common.cancel' | t }}</button>
          <button
            type="submit"
            class="btn btn--primary"
            [disabled]="saving()"
            data-testid="task-save"
          >
            {{ (saving() ? 'common.saving' : 'common.save') | t }}
          </button>
        </div>
      </form>
    </dialog>
  `,
})
export class TaskDialogComponent {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly saved = output<Task>();

  protected readonly priorities: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH'];
  protected readonly team = rxResource({ stream: () => this.api.get<UserRef[]>('/team') });
  protected readonly editing = signal<Task | null>(null);
  private links: TaskLinks = {};
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(200)]],
    dueDate: ['', Validators.required],
    priority: ['MEDIUM' as TaskPriority, Validators.required],
    assigneeId: [''],
    description: ['', Validators.maxLength(2000)],
  });

  /**
   * Opens the dialog for a new task (task = null) or to edit an existing one.
   * يفتح النافذة لمهمة جديدة (task = null) أو لتعديل مهمة موجودة.
   */
  open(task: Task | null, links: TaskLinks = {}): void {
    this.editing.set(task);
    this.apiError.set(null);
    this.links = task
      ? { companyId: task.company?.id ?? null, dealId: task.deal?.id ?? null }
      : links;
    this.form.reset({
      title: task?.title ?? '',
      // New tasks default to today in Riyadh, the same "today" the server uses.
      // المهمة الجديدة افتراضيًا اليوم بتوقيت الرياض، نفس "اليوم" عند الخادم.
      dueDate: task?.dueDate ?? riyadhToday(),
      priority: task?.priority ?? 'MEDIUM',
      assigneeId: task ? String(task.assignee.id) : '',
      description: task?.description ?? '',
    });
    this.dialog().nativeElement.showModal();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
  }

  protected onClosed(): void {
    this.saving.set(false);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const task = this.editing();
    const body = {
      title: v.title.trim(),
      description: v.description.trim() || null,
      dueDate: v.dueDate,
      priority: v.priority,
      assigneeId: v.assigneeId ? Number(v.assigneeId) : null,
      companyId: this.links.companyId ?? null,
      dealId: this.links.dealId ?? null,
      // Keep the contact link of an edited task. / نحتفظ بربط جهة الاتصال للمهمة المعدّلة.
      contactId: task?.contact?.id ?? null,
      version: task?.version ?? null,
    };
    this.saving.set(true);
    this.apiError.set(null);
    const request = task
      ? this.api.put<Task>(`/tasks/${task.id}`, body)
      : this.api.post<Task>('/tasks', body);
    request.subscribe({
      next: (result) => {
        this.toast.success(this.i18n.t(task ? 'common.saved' : 'common.created'));
        this.saved.emit(result);
        this.close();
      },
      error: (err) => {
        this.saving.set(false);
        this.apiError.set(toApiError(err));
        this.form.markAllAsTouched();
      },
    });
  }
}
