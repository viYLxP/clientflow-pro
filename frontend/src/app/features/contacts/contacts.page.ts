import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  input,
  linkedSignal,
  signal,
  viewChild,
} from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiError, errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Company, Contact, Page } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { PHONE_PATTERN, canChange } from '../../core/crm';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { FieldErrorComponent } from '../../core/ui/field-error.component';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Contacts list with search and a company filter; create and edit happen in a dialog on the same page.
 * قائمة جهات الاتصال مع البحث وفلتر الشركة؛ والإضافة والتعديل في نافذة بنفس الصفحة.
 */
@Component({
  selector: 'app-contacts',
  imports: [
    ReactiveFormsModule,
    TranslatePipe,
    IconComponent,
    PaginationComponent,
    FieldErrorComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './contacts.page.html',
})
export class ContactsPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  readonly q = input<string | undefined>('');
  readonly companyId = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  protected readonly searchText = linkedSignal(() => this.q() ?? '');
  protected readonly companies = rxResource({
    stream: () => this.api.get<Page<Company>>('/companies', { size: 100 }),
  });
  protected readonly contacts = rxResource({
    params: () => ({
      q: this.q(),
      companyId: this.companyId(),
      page: Number(this.page() ?? 0) || 0,
      size: 20,
    }),
    stream: ({ params }) => this.api.get<Page<Contact>>('/contacts', params),
  });
  protected readonly errorText = computed(() =>
    this.contacts.error() ? errorMessage(this.i18n, toApiError(this.contacts.error())) : null,
  );
  protected readonly hasFilters = computed(() => !!(this.q() || this.companyId()));

  protected readonly editing = signal<Contact | null>(null);
  protected readonly saving = signal(false);
  private readonly apiError = signal<ApiError | null>(null);
  protected readonly serverFields = computed(() => this.apiError()?.fields ?? {});
  protected readonly formError = computed(() => {
    const err = this.apiError();
    return err && err.code !== 'VALIDATION_FAILED' ? errorMessage(this.i18n, err) : null;
  });

  protected readonly form = inject(NonNullableFormBuilder).group({
    firstName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(80)]],
    lastName: ['', [Validators.required, Validators.pattern(/\S/), Validators.maxLength(80)]],
    email: ['', [Validators.maxLength(150), Validators.email]],
    phone: ['', [Validators.maxLength(30), Validators.pattern(PHONE_PATTERN)]],
    jobTitle: ['', Validators.maxLength(100)],
    companyId: [''],
    notes: ['', Validators.maxLength(2000)],
  });

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => {
      if (this.apiError()) this.apiError.set(null);
    });
  }

  protected canEdit(contact: Contact): boolean {
    return canChange(this.auth.user(), [contact.owner]);
  }

  protected onSearch(value: string): void {
    this.searchText.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.setFilter({ q: value.trim() || null }), 300);
  }

  protected setFilter(change: Record<string, string | number | null>): void {
    const pageChange = 'page' in change;
    void this.router.navigate([], {
      queryParams: { ...change, ...(pageChange ? {} : { page: null }) },
      queryParamsHandling: 'merge',
      replaceUrl: !pageChange,
    });
  }

  protected clearFilters(): void {
    this.searchText.set('');
    void this.router.navigate([], { queryParams: {} });
  }

  protected open(contact: Contact | null): void {
    this.editing.set(contact);
    this.apiError.set(null);
    this.form.reset({
      firstName: contact?.firstName ?? '',
      lastName: contact?.lastName ?? '',
      email: contact?.email ?? '',
      phone: contact?.phone ?? '',
      jobTitle: contact?.jobTitle ?? '',
      // A new contact starts at the company the list is filtered by. / جهة الاتصال الجديدة تبدأ بالشركة المفلترة.
      companyId: contact ? String(contact.company?.id ?? '') : (this.companyId() ?? ''),
      notes: contact?.notes ?? '',
    });
    this.dialog().nativeElement.showModal();
  }

  protected close(): void {
    this.dialog().nativeElement.close();
    this.saving.set(false);
  }

  protected submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const contact = this.editing();
    const body = {
      ...v,
      firstName: v.firstName.trim(),
      lastName: v.lastName.trim(),
      companyId: v.companyId ? Number(v.companyId) : null,
      version: contact?.version ?? null,
    };
    this.saving.set(true);
    const request = contact
      ? this.api.put<Contact>(`/contacts/${contact.id}`, body)
      : this.api.post<Contact>('/contacts', body);
    request.subscribe({
      next: () => {
        this.toast.success(this.i18n.t(contact ? 'common.saved' : 'common.created'));
        this.close();
        this.contacts.reload();
      },
      error: (err) => {
        this.saving.set(false);
        this.apiError.set(toApiError(err));
        this.form.markAllAsTouched();
      },
    });
  }

  protected async remove(contact: Contact): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: contact.fullName }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/contacts/${contact.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        this.contacts.reload();
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
