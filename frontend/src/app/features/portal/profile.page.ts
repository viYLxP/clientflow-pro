import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { PortalMe } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { matches } from '../../core/passwords';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * The client's profile: their details as the team keeps them (read-only), and a password change.
 * ملف العميل: بياناته مثل ما يحفظها الفريق (للعرض بس)، وتغيير كلمة المرور.
 */
@Component({
  selector: 'app-portal-profile',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './profile.page.html',
})
export class ProfilePage {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);

  protected readonly me = rxResource({ stream: () => this.api.get<PortalMe>('/portal/me') });

  protected readonly form = inject(NonNullableFormBuilder).group({
    currentPassword: ['', [Validators.required, Validators.maxLength(72)]],
    newPassword: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirm: ['', [Validators.required, matches('newPassword')]],
  });
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  constructor() {
    // A server error on a field disappears as soon as the user edits it. / خطأ الخادم يختفي أول ما يعدّل المستخدم الحقل.
    this.form.valueChanges.subscribe(() => {
      if (this.apiError()) this.apiError.set(null);
    });
  }

  protected submit(): void {
    this.form.controls.confirm.updateValueAndValidity();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    this.saving.set(true);
    this.api
      .put<void>('/portal/password', {
        currentPassword: v.currentPassword,
        newPassword: v.newPassword,
      })
      .subscribe({
        next: () => {
          this.saving.set(false);
          this.form.reset();
          this.toast.success(this.i18n.t('portal.passwordChanged'));
        },
        error: (err) => {
          this.saving.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }
}
