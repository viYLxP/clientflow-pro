import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  output,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Activity, ActivityType, Page } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { ACTIVITY_ICONS, canChange } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

const PAGE_SIZE = 10;

/**
 * Activity log of one deal or one company: a form to log a call/meeting/email/note, then newest first.
 * سجل التواصل لصفقة أو شركة: نموذج لتسجيل مكالمة أو اجتماع أو بريد أو ملاحظة، ثم الأحدث أولًا.
 */
@Component({
  selector: 'app-activity-timeline',
  imports: [ReactiveFormsModule, TranslatePipe, IconComponent, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="card" aria-labelledby="timeline-title">
      <div class="card__header">
        <h2 id="timeline-title">{{ 'activities.title' | t }}</h2>
      </div>
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate data-testid="activity-form">
        @if (errorText()) {
          <div class="alert alert--danger" role="alert">{{ errorText() }}</div>
        }
        <div class="form-grid">
          <div class="field">
            <label for="act-type">{{ 'activities.type' | t }}</label>
            <select id="act-type" formControlName="type">
              @for (type of types; track type) {
                <option [value]="type">{{ 'activityType.' + type | t }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label for="act-when"
              >{{ 'activities.occurredAt' | t }} <small>({{ 'common.optional' | t }})</small></label
            >
            <input
              id="act-when"
              type="datetime-local"
              formControlName="occurredAt"
              [max]="maxWhen()"
              aria-describedby="act-when-hint act-when-err"
            />
            <p class="hint" id="act-when-hint">{{ 'activities.occurredHint' | t }}</p>
            <app-field-error
              id="act-when-err"
              [control]="form.controls.occurredAt"
              [server]="serverFields()['occurredAt']"
            />
          </div>
          <div class="field form-grid--full">
            <label for="act-body">{{ 'activities.body' | t }}</label>
            <textarea
              id="act-body"
              rows="3"
              formControlName="body"
              [placeholder]="'activities.bodyPlaceholder' | t"
              aria-describedby="act-body-err"
            ></textarea>
            <app-field-error
              id="act-body-err"
              [control]="form.controls.body"
              [server]="serverFields()['body']"
            />
          </div>
        </div>
        <div class="form-actions">
          <button
            type="submit"
            class="btn btn--primary"
            [disabled]="saving()"
            data-testid="activity-save"
          >
            {{ (saving() ? 'common.saving' : 'activities.add') | t }}
          </button>
        </div>
      </form>

      @if (activities.error()) {
        <p class="alert alert--danger" role="alert">{{ 'app.loadFailed' | t }}</p>
      } @else if (activities.value(); as page) {
        <ul class="timeline" data-testid="timeline">
          @for (a of page.content; track a.id) {
            <li>
              <span class="timeline__icon"><app-icon [name]="icon(a.type)" /></span>
              <div>
                <div class="timeline__meta">
                  <strong>{{ 'activityType.' + a.type | t }}</strong> ·
                  {{ fmt.dateTime(a.occurredAt) }} ·
                  <bdi>{{ 'activities.by' | t: { name: a.author.fullName } }}</bdi>
                  @if (showDeal() && a.deal) {
                    · <bdi>{{ 'activities.on' | t: { deal: a.deal.title } }}</bdi>
                  }
                  @if (canDelete(a)) {
                    <button
                      type="button"
                      class="icon-btn icon-btn--danger"
                      (click)="remove(a)"
                      [attr.aria-label]="'common.delete' | t"
                    >
                      <app-icon name="trash" />
                    </button>
                  }
                </div>
                <p class="timeline__body">
                  <bdi>{{ a.body }}</bdi>
                </p>
              </div>
            </li>
          } @empty {
            <li class="muted">{{ 'activities.empty' | t }}</li>
          }
        </ul>
        @if (page.totalElements > page.content.length) {
          <button type="button" class="btn btn--small" (click)="size.set(size() + PAGE_SIZE)">
            {{ 'common.viewAll' | t }} ({{ page.totalElements }})
          </button>
        }
      }
    </section>
  `,
})
export class ActivityTimelineComponent {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly fmt = inject(FormatService);

  readonly companyId = input<number | null>(null);
  readonly dealId = input<number | null>(null);
  // On a company page, show which deal each note belongs to. / في صفحة الشركة نوضح كل ملاحظة لأي صفقة.
  readonly showDeal = input(false);
  readonly changed = output<void>();

  protected readonly PAGE_SIZE = PAGE_SIZE;
  protected readonly types: ActivityType[] = ['NOTE', 'CALL', 'MEETING', 'EMAIL'];
  protected readonly size = signal(PAGE_SIZE);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });

  protected readonly activities = rxResource({
    params: () => ({ companyId: this.companyId(), dealId: this.dealId(), size: this.size() }),
    stream: ({ params }) => this.api.get<Page<Activity>>('/activities', params),
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    type: ['NOTE' as ActivityType],
    body: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(2000)]],
    occurredAt: [''],
  });

  constructor() {
    // Editing the form clears the server's field errors from the last attempt.
    // تعديل النموذج يمسح أخطاء الحقول اللي رجّعها الخادم في المحاولة السابقة.
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.apiError()) this.apiError.set(null);
    });
  }

  // datetime-local has no time zone; "max" is the browser's local now, which is what the user types in.
  // datetime-local بدون منطقة زمنية؛ و"max" هو الوقت المحلي للمتصفح، وهو نفس اللي يكتبه المستخدم.
  protected maxWhen(): string {
    const now = new Date();
    return new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }

  protected icon(type: ActivityType): string {
    return ACTIVITY_ICONS[type];
  }

  protected canDelete(a: Activity): boolean {
    return canChange(this.auth.user(), [a.author]);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.apiError.set(null);
    this.api
      .post<Activity>('/activities', {
        type: v.type,
        body: v.body.trim(),
        // The browser turns the local time into a UTC instant. / المتصفح يحوّل الوقت المحلي لوقت UTC.
        occurredAt: v.occurredAt ? new Date(v.occurredAt).toISOString() : null,
        companyId: this.dealId() ? null : this.companyId(),
        dealId: this.dealId(),
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.toast.success(this.i18n.t('activities.logged'));
          this.form.reset({ type: v.type, body: '', occurredAt: '' });
          this.activities.reload();
          this.changed.emit();
        },
        error: (err) => {
          this.saving.set(false);
          this.apiError.set(toApiError(err));
          this.form.markAllAsTouched();
        },
      });
  }

  protected async remove(a: Activity): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: this.i18n.t(`activityType.${a.type}`) }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/activities/${a.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        this.activities.reload();
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
