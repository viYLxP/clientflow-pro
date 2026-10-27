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
import { ConvertResponse, Inquiry, UserRef } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AMOUNT_PATTERN } from '../../core/crm';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';

/**
 * Turns a website message into a lead. Everything is optional: the server falls back to the company name,
 * 0 SAR and the current user as owner. Only an admin may hand the lead to someone else.
 * يحوّل رسالة الموقع لصفقة. كل شي اختياري: الخادم يستخدم اسم الشركة و0 ريال والمستخدم الحالي كمالك.
 * والمدير بس يقدر يعطي الصفقة لشخص ثاني.
 */
@Component({
  selector: 'app-convert-dialog',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog" aria-labelledby="convert-title" (close)="onClosed()">
      <form [formGroup]="form" (ngSubmit)="submit()" novalidate>
        <h2 id="convert-title">{{ 'inquiries.convertTitle' | t }}</h2>
        <p class="muted">{{ 'inquiries.convertText' | t }}</p>
        @if (errorText()) {
          <div class="alert alert--danger" role="alert">{{ errorText() }}</div>
        }
        <div class="form-grid">
          <div class="field form-grid--full">
            <label for="cv-title">{{ 'inquiries.dealTitle' | t }}</label>
            <input
              id="cv-title"
              formControlName="dealTitle"
              dir="auto"
              maxlength="150"
              [placeholder]="inquiry()?.companyName ?? inquiry()?.name ?? ''"
              aria-describedby="cv-title-hint"
              data-testid="convert-title"
            />
            <p class="hint" id="cv-title-hint">{{ 'inquiries.dealTitleHint' | t }}</p>
          </div>
          <div class="field">
            <label for="cv-amount">{{ 'inquiries.amount' | t }}</label>
            <input
              id="cv-amount"
              formControlName="amount"
              inputmode="decimal"
              dir="ltr"
              autocomplete="off"
              aria-describedby="cv-amount-err"
              data-testid="convert-amount"
            />
            <app-field-error
              id="cv-amount-err"
              [control]="form.controls.amount"
              [server]="serverFields()['amount']"
              patternKey="validation.Digits"
            />
          </div>
          @if (auth.isAdmin()) {
            <div class="field">
              <label for="cv-owner">{{ 'inquiries.owner' | t }}</label>
              <select id="cv-owner" formControlName="ownerId">
                <option value="">{{ 'common.me' | t }}</option>
                @for (m of team.value() ?? []; track m.id) {
                  <option [value]="'' + m.id">{{ m.fullName }}</option>
                }
              </select>
            </div>
          }
        </div>
        <div class="dialog__actions">
          <button type="button" class="btn" (click)="close()">{{ 'common.cancel' | t }}</button>
          <button
            type="submit"
            class="btn btn--primary"
            [disabled]="saving()"
            data-testid="convert-save"
          >
            {{ (saving() ? 'common.saving' : 'inquiries.convert') | t }}
          </button>
        </div>
      </form>
    </dialog>
  `,
})
export class ConvertDialogComponent {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly converted = output<ConvertResponse>();

  protected readonly team = rxResource({ stream: () => this.api.get<UserRef[]>('/team') });
  protected readonly inquiry = signal<Inquiry | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    dealTitle: ['', Validators.maxLength(150)],
    amount: ['', Validators.pattern(AMOUNT_PATTERN)],
    ownerId: [''],
  });

  open(inquiry: Inquiry): void {
    this.inquiry.set(inquiry);
    this.apiError.set(null);
    this.saving.set(false);
    this.form.reset({ dealTitle: '', amount: '', ownerId: '' });
    this.dialog().nativeElement.showModal();
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected onClosed(): void {
    this.inquiry.set(null);
  }

  protected submit(): void {
    const inquiry = this.inquiry();
    if (!inquiry) return;
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.apiError.set(null);
    this.api
      .post<ConvertResponse>(`/inquiries/${inquiry.id}/convert`, {
        dealTitle: v.dealTitle.trim() || null,
        amount: v.amount ? Number(v.amount) : null,
        ownerId: v.ownerId ? Number(v.ownerId) : null,
      })
      .subscribe({
        next: (result) => {
          this.saving.set(false);
          this.converted.emit(result);
          this.close();
        },
        error: (err) => {
          this.saving.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }
}
