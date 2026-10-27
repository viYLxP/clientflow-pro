import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { InviteInfo, LoginResponse } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { matches } from '../../core/passwords';
import { FieldErrorComponent } from '../../core/ui/field-error.component';

/**
 * Invite activation (/activate#<token>). The token is in the URL fragment, which browsers never send to a
 * server, so it does not end up in access logs. The page checks it, greets the client, and lets them choose
 * a password; accepting signs them straight in.
 * تفعيل الدعوة (‎/activate#<token>). الرمز في جزء الـ # من الرابط، واللي المتصفح ما يرسله لأي خادم،
 * فما ينكتب في سجلات الوصول. الصفحة تتأكد منه، وترحّب بالعميل، وتخليه يختار كلمة مرور؛ والقبول يدخّله مباشرة.
 */
@Component({
  selector: 'app-activate',
  imports: [ReactiveFormsModule, TranslatePipe, FieldErrorComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './activate.page.html',
})
export class ActivatePage {
  private readonly api = inject(ApiService);
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  // Read once, then removed from the address bar so it is not left in history or shared by mistake.
  // نقراه مرة وحدة، ثم نشيله من شريط العنوان حتى ما يبقى في السجل أو ينشارك بالغلط.
  protected readonly token = inject(ActivatedRoute).snapshot.fragment ?? '';

  protected readonly invite = rxResource({
    params: () => this.token || null,
    stream: ({ params }) =>
      params ? this.api.post<InviteInfo>('/auth/invite/check', { token: params }) : of(null),
  });
  protected readonly inviteError = computed(() => {
    const err = this.invite.error();
    return err ? errorMessage(this.i18n, toApiError(err)) : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    password: ['', [Validators.required, Validators.minLength(8), Validators.maxLength(72)]],
    confirm: ['', [Validators.required, matches('password')]],
  });
  protected readonly submitting = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});

  constructor() {
    if (this.token) history.replaceState(history.state, '', '/activate');
  }

  protected submit(): void {
    this.form.controls.confirm.updateValueAndValidity();
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting.set(true);
    this.apiError.set(null);
    this.api
      .post<LoginResponse>('/auth/invite/accept', {
        token: this.token,
        password: this.form.getRawValue().password,
      })
      .subscribe({
        next: (response) => {
          this.auth.adopt(response);
          void this.router.navigateByUrl('/portal');
        },
        error: (err) => {
          this.submitting.set(false);
          this.apiError.set(toApiError(err));
        },
      });
  }
}
