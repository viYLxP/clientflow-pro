import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  input,
  signal,
} from '@angular/core';
import { rxResource, takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import {
  Company,
  Contact,
  Deal,
  DealStage,
  OPEN_STAGES,
  Page,
  UserRef,
} from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AMOUNT_PATTERN, canChange, isClosed } from '../../core/crm';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

// Size of the company dropdown (the API caps pages at 100). More customers would need a search-as-you-type picker.
// حجم قائمة الشركات. قاعدة عملاء أكبر تحتاج منتقي بالبحث أثناء الكتابة.
const COMPANY_LIMIT = 100;

/**
 * Create / edit a deal. /deals/new?companyId=5 pre-selects the company (from the company page).
 * إنشاء وتعديل صفقة. /deals/new?companyId=5 يختار الشركة مسبقًا (من صفحة الشركة).
 */
@Component({
  selector: 'app-deal-form',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './deal-form.page.html',
})
export class DealFormPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);

  readonly id = input<string>();
  readonly companyId = input<string | undefined>();
  protected readonly isEdit = computed(() => !!this.id());
  protected readonly stages = OPEN_STAGES;

  protected readonly form = inject(NonNullableFormBuilder).group({
    title: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(150)]],
    companyId: ['', Validators.required],
    contactId: [''],
    ownerId: [''],
    amount: ['', [Validators.required, Validators.pattern(AMOUNT_PATTERN)]],
    expectedCloseDate: [''],
    stage: ['LEAD' as DealStage],
  });

  private readonly selectedCompany = toSignal(this.form.controls.companyId.valueChanges, {
    initialValue: '',
  });

  protected readonly team = rxResource({ stream: () => this.api.get<UserRef[]>('/team') });
  protected readonly companies = rxResource({
    stream: () => this.api.get<Page<Company>>('/companies', { size: COMPANY_LIMIT }),
  });
  // Only the chosen company's people, so the server's CONTACT_COMPANY_MISMATCH rule is rarely hit.
  // فقط موظفي الشركة المختارة، حتى نادرًا ما تنطبق قاعدة CONTACT_COMPANY_MISMATCH في الخادم.
  protected readonly contacts = rxResource({
    params: () => this.selectedCompany(),
    stream: ({ params: companyId }) =>
      companyId ? this.api.get<Page<Contact>>('/contacts', { companyId, size: 100 }) : of(null),
  });
  protected readonly deal = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => (id ? this.api.get<Deal>(`/deals/${id}`) : of(null)),
  });

  protected readonly closed = computed(() => {
    const d = this.deal.value();
    return !!d && isClosed(d.stage);
  });
  protected readonly readOnly = computed(() => {
    const d = this.deal.value();
    return !!d && !canChange(this.auth.user(), [d.owner]);
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
      const d = this.deal.value();
      if (d) {
        this.form.reset({
          title: d.title,
          companyId: String(d.company.id),
          contactId: d.contact ? String(d.contact.id) : '',
          ownerId: String(d.owner.id),
          amount: d.amount.toFixed(2),
          expectedCloseDate: d.expectedCloseDate ?? '',
          stage: d.stage,
        });
      }
    });
    effect(() => {
      const preset = this.companyId();
      if (preset && !this.isEdit()) this.form.controls.companyId.setValue(preset);
    });
    const destroy = takeUntilDestroyed();
    // Changing company drops a contact that belongs to the previous one.
    // تغيير الشركة يشيل جهة الاتصال التابعة للشركة السابقة.
    this.form.controls.companyId.valueChanges.pipe(destroy).subscribe(() => {
      const d = this.deal.value();
      if (!d || String(d.company.id) !== this.form.controls.companyId.value) {
        this.form.controls.contactId.setValue('');
      }
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.apiError()?.code === 'VALIDATION_FAILED') this.apiError.set(null);
    });
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const deal = this.deal.value();
    const body = {
      title: v.title.trim(),
      companyId: Number(v.companyId),
      contactId: v.contactId ? Number(v.contactId) : null,
      ownerId: v.ownerId ? Number(v.ownerId) : null,
      amount: Number(v.amount),
      expectedCloseDate: v.expectedCloseDate || null,
      // The starting stage is only read on create. / مرحلة البداية تُقرأ عند الإنشاء فقط.
      stage: deal ? null : v.stage,
      version: deal?.version ?? null,
    };
    this.saving.set(true);
    this.apiError.set(null);
    const request = deal
      ? this.api.put<Deal>(`/deals/${deal.id}`, body)
      : this.api.post<Deal>('/deals', body);
    request.subscribe({
      next: (saved) => {
        this.toast.success(this.i18n.t(deal ? 'common.saved' : 'common.created'));
        this.form.markAsPristine();
        void this.router.navigate(['/deals', saved.id]);
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
