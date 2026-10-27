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
import { map, of } from 'rxjs';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Company, Contact, Deal, InvoiceDetail, InvoiceRequest, Page } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { AMOUNT_PATTERN, addDays, riyadhToday } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { computeTotals, lineTotal } from '../../core/money';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

// Same limits as the server (InvoiceRequest). / نفس حدود الخادم (InvoiceRequest).
const MAX_LINES = 50;
const PAYMENT_TERMS_DAYS = 14;

/**
 * Create or edit a draft invoice. /invoices/new?companyId=5 pre-selects the company; ?dealId=9 (from a won
 * deal) also fills the contact and a first line with the deal's title and amount.
 * إنشاء أو تعديل مسودة فاتورة. ‎/invoices/new?companyId=5 يختار الشركة؛ و‎?dealId=9 (من صفقة رابحة)
 * يعبّي كمان جهة الاتصال وأول بند بعنوان الصفقة وقيمتها.
 */
@Component({
  selector: 'app-invoice-form',
  imports: [ReactiveFormsModule, RouterLink, TranslatePipe, FieldErrorComponent, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invoice-form.page.html',
})
export class InvoiceFormPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);

  readonly id = input<string>();
  readonly companyId = input<string | undefined>();
  readonly dealId = input<string | undefined>();
  protected readonly isEdit = computed(() => !!this.id());
  protected readonly maxLines = MAX_LINES;
  protected readonly today = riyadhToday();

  protected readonly form = this.fb.group({
    companyId: ['', Validators.required],
    contactId: [''],
    dealId: [''],
    dueDate: [addDays(this.today, PAYMENT_TERMS_DAYS), Validators.required],
    notes: ['', Validators.maxLength(1000)],
    lines: this.fb.array([this.line()]),
  });
  protected readonly lines = this.form.controls.lines;

  private readonly selectedCompany = toSignal(this.form.controls.companyId.valueChanges, {
    initialValue: '',
  });
  // Live preview with the server's rounding rules. / معاينة مباشرة بنفس قواعد التقريب في الخادم.
  protected readonly totals = toSignal(
    this.lines.valueChanges.pipe(map(() => computeTotals(this.lines.getRawValue()))),
    { initialValue: computeTotals([]) },
  );

  // A rep can invoice only the companies they manage; an admin any. / المندوب يفوتر بس الشركات اللي يديرها؛ والمدير أي شركة.
  protected readonly companies = rxResource({
    stream: () => this.api.get<Page<Company>>('/companies', { size: 100 }),
  });
  protected readonly myCompanies = computed(() => {
    const user = this.auth.user();
    const all = this.companies.value()?.content ?? [];
    return this.auth.isAdmin() ? all : all.filter((c) => c.owner.id === user?.id);
  });
  protected readonly contacts = rxResource({
    params: () => this.selectedCompany(),
    stream: ({ params: companyId }) =>
      companyId ? this.api.get<Page<Contact>>('/contacts', { companyId, size: 100 }) : of(null),
  });
  protected readonly deals = rxResource({
    params: () => this.selectedCompany(),
    stream: ({ params: companyId }) =>
      companyId ? this.api.get<Page<Deal>>('/deals', { companyId, size: 100 }) : of(null),
  });
  protected readonly existing = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => (id ? this.api.get<InvoiceDetail>(`/invoices/${id}`) : of(null)),
  });
  private readonly presetDeal = rxResource({
    params: () => (this.isEdit() ? undefined : this.dealId()),
    stream: ({ params: id }) => (id ? this.api.get<Deal>(`/deals/${id}`) : of(null)),
  });

  protected readonly notDraft = computed(() => {
    const d = this.existing.value();
    return !!d && d.invoice.status !== 'DRAFT';
  });
  protected readonly readOnly = computed(() => {
    const d = this.existing.value();
    return !!d && !d.canManage;
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
      const d = this.existing.value();
      if (!d) return;
      const inv = d.invoice;
      this.lines.clear();
      for (const l of inv.lines) this.lines.push(this.line(l.description, l.quantity, l.unitPrice));
      this.form.reset({
        companyId: String(inv.company.id),
        contactId: inv.contact ? String(inv.contact.id) : '',
        dealId: inv.deal ? String(inv.deal.id) : '',
        dueDate: inv.dueDate,
        notes: inv.notes ?? '',
      });
    });
    effect(() => {
      const preset = this.companyId();
      if (preset && !this.isEdit()) this.form.controls.companyId.setValue(preset);
    });
    effect(() => {
      const deal = this.presetDeal.value();
      if (!deal) return;
      this.form.controls.companyId.setValue(String(deal.company.id));
      this.form.patchValue({
        contactId: deal.contact ? String(deal.contact.id) : '',
        dealId: String(deal.id),
      });
      this.lines.at(0).setValue({
        description: deal.title,
        quantity: '1',
        unitPrice: deal.amount.toFixed(2),
      });
    });
    const destroy = takeUntilDestroyed();
    // Changing company drops the contact and deal of the previous one (unless a preset is filling them).
    // تغيير الشركة يشيل جهة الاتصال والصفقة التابعة للشركة السابقة (إلا لو فيه تعبئة مسبقة).
    this.form.controls.companyId.valueChanges.pipe(destroy).subscribe((companyId) => {
      const source =
        this.existing.value()?.invoice.company.id ?? this.presetDeal.value()?.company.id;
      if (String(source ?? '') !== companyId) {
        this.form.patchValue({ contactId: '', dealId: '' });
      }
    });
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.apiError()?.code === 'VALIDATION_FAILED') this.apiError.set(null);
    });
  }

  protected lineAmount(i: number): number {
    return lineTotal(this.lines.at(i).getRawValue());
  }

  protected addLine(): void {
    if (this.lines.length < MAX_LINES) this.lines.push(this.line());
  }

  protected removeLine(i: number): void {
    if (this.lines.length > 1) this.lines.removeAt(i);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const existing = this.existing.value()?.invoice;
    const body: InvoiceRequest = {
      companyId: Number(v.companyId),
      contactId: v.contactId ? Number(v.contactId) : null,
      dealId: v.dealId ? Number(v.dealId) : null,
      dueDate: v.dueDate,
      notes: v.notes.trim() || null,
      lines: v.lines.map((l) => ({
        description: l.description.trim(),
        quantity: Number(l.quantity),
        unitPrice: Number(l.unitPrice),
      })),
      version: existing?.version,
    };
    this.saving.set(true);
    this.apiError.set(null);
    const request = existing
      ? this.api.put<InvoiceDetail>(`/invoices/${existing.id}`, body)
      : this.api.post<InvoiceDetail>('/invoices', body);
    request.subscribe({
      next: (saved) => {
        this.toast.success(this.i18n.t(existing ? 'common.saved' : 'common.created'));
        void this.router.navigate(['/invoices', saved.invoice.id]);
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

  private line(description = '', quantity: number | string = 1, unitPrice: number | string = '') {
    return this.fb.group({
      description: [
        description,
        [Validators.required, Validators.pattern(/\S/), Validators.maxLength(300)],
      ],
      quantity: [
        String(quantity),
        [
          Validators.required,
          Validators.pattern(/^\d+$/),
          Validators.min(1),
          Validators.max(100000),
        ],
      ],
      unitPrice: [
        typeof unitPrice === 'number' ? unitPrice.toFixed(2) : unitPrice,
        [Validators.required, Validators.pattern(AMOUNT_PATTERN)],
      ],
    });
  }
}
