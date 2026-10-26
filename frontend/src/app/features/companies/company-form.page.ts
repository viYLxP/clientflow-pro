import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CompanyDetail, Company, UserRef } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { PHONE_PATTERN, WEBSITE_PATTERN, canChange } from '../../core/crm';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Create / edit a company. The validation rules mirror CompanyDtos on the server.
 * إنشاء وتعديل شركة. قواعد التحقق مطابقة لـ CompanyDtos في الخادم.
 */
@Component({
  selector: 'app-company-form',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-form.page.html',
})
export class CompanyFormPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  readonly id = input<string>();
  protected readonly isEdit = computed(() => !!this.id());

  protected readonly form = inject(NonNullableFormBuilder).group({
    name: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    industry: ['', Validators.maxLength(80)],
    city: ['', Validators.maxLength(80)],
    phone: ['', [Validators.maxLength(30), Validators.pattern(PHONE_PATTERN)]],
    email: ['', [Validators.maxLength(150), Validators.email]],
    website: ['', [Validators.maxLength(200), Validators.pattern(WEBSITE_PATTERN)]],
    notes: ['', Validators.maxLength(2000)],
    ownerId: [''],
  });

  protected readonly team = rxResource({ stream: () => this.api.get<UserRef[]>('/team') });
  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => (id ? this.api.get<CompanyDetail>(`/companies/${id}`) : of(null)),
  });
  protected readonly readOnly = computed(() => {
    const c = this.detail.value()?.company;
    return !!c && !canChange(this.auth.user(), [c.owner]);
  });

  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});
  protected readonly errorText = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });

  constructor() {
    effect(() => {
      const c = this.detail.value()?.company;
      if (c) {
        this.form.reset({
          name: c.name,
          industry: c.industry ?? '',
          city: c.city ?? '',
          phone: c.phone ?? '',
          email: c.email ?? '',
          website: c.website ?? '',
          notes: c.notes ?? '',
          ownerId: String(c.owner.id),
        });
      }
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.apiError()) this.apiError.set(null);
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const existing = this.detail.value()?.company;
    const body = {
      name: v.name.trim(),
      industry: v.industry,
      city: v.city,
      phone: v.phone,
      email: v.email,
      website: v.website,
      notes: v.notes,
      ownerId: v.ownerId ? Number(v.ownerId) : null,
      version: existing?.version ?? null,
    };
    this.saving.set(true);
    this.apiError.set(null);
    const request = existing
      ? this.api.put<Company>(`/companies/${existing.id}`, body)
      : this.api.post<Company>('/companies', body);
    request.subscribe({
      next: (saved) => {
        this.toast.success(this.i18n.t(existing ? 'common.saved' : 'common.created'));
        this.form.markAsPristine();
        void this.router.navigate(['/companies', saved.id]);
      },
      error: (err) => {
        this.saving.set(false);
        const apiError = toApiError(err);
        this.apiError.set(apiError);
        this.form.markAllAsTouched();
        if (apiError.code === 'VALIDATION_FAILED')
          this.toast.error(this.i18n.t('validation.fixForm'));
      },
    });
  }
}
