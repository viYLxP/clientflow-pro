import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { PHONE_PATTERN } from '../../core/crm';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';

const FEATURES = [
  { key: 'pipeline', icon: 'kanban' },
  { key: 'invoices', icon: 'invoice' },
  { key: 'portal', icon: 'user' },
  { key: 'payments', icon: 'card' },
] as const;

/**
 * The public website: what ClientFlow does, and a "Talk to sales" form. Each message lands in the team's
 * inbox (/inquiries), where a rep turns it into a lead.
 * الموقع العام: وش يسوي ClientFlow، ونموذج "تواصل مع المبيعات". كل رسالة توصل لصندوق الفريق (‎/inquiries)،
 * وهناك المندوب يحولها لصفقة.
 */
@Component({
  selector: 'app-home',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './home.page.html',
})
export class HomePage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);

  protected readonly features = FEATURES;
  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(120)]],
    companyName: ['', Validators.maxLength(150)],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    phone: ['', [Validators.pattern(PHONE_PATTERN), Validators.maxLength(30)]],
    message: ['', [Validators.required, Validators.minLength(10), Validators.maxLength(2000)]],
    // Hidden from people (CSS and aria-hidden); a bot that fills it is silently ignored by the server.
    // مخفي عن الناس (CSS وaria-hidden)؛ والروبوت اللي يعبّيه يتجاهله الخادم بصمت.
    website: [''],
  });
  protected readonly sending = signal(false);
  protected readonly sent = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  protected send(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.sending.set(true);
    this.apiError.set(null);
    this.api
      .post('/public/inquiries', {
        name: v.name.trim(),
        companyName: v.companyName.trim() || null,
        email: v.email.trim(),
        phone: v.phone.trim() || null,
        message: v.message.trim(),
        website: v.website || null,
      })
      .subscribe({
        next: () => {
          this.sending.set(false);
          this.sent.set(true);
          this.form.reset();
        },
        error: (err) => {
          this.sending.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }

  protected another(): void {
    this.sent.set(false);
  }
}
