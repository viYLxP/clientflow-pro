import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { ConvertResponse, Inquiry, InquiryStatus, Page } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { ToastService } from '../../core/ui/toast.service';
import { ConvertDialogComponent } from './convert-dialog.component';

const STATUSES: InquiryStatus[] = ['NEW', 'CONVERTED', 'DISMISSED'];

/**
 * The team's inbox for the website contact form. New messages first; ?status= switches the view.
 * صندوق الفريق لنموذج التواصل في الموقع. الرسائل الجديدة أول؛ و‎?status= يبدّل العرض.
 */
@Component({
  selector: 'app-inquiries',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent, ConvertDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './inquiries.page.html',
})
export class InquiriesPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly fmt = inject(FormatService);

  // Missing = the New tab; "ALL" = every message. / بدون قيمة = تبويب الجديدة؛ و"ALL" = كل الرسائل.
  readonly status = input<string | undefined>();
  readonly page = input<string | undefined>('0');

  protected readonly statuses = STATUSES;
  protected readonly current = computed(() => this.status() ?? 'NEW');
  protected readonly inquiries = rxResource({
    params: () => ({
      status: this.current() === 'ALL' ? undefined : this.current(),
      page: Number(this.page() ?? 0) || 0,
      size: 20,
    }),
    stream: ({ params }) => this.api.get<Page<Inquiry>>('/inquiries', params),
  });
  protected readonly errorText = computed(() =>
    this.inquiries.error() ? errorMessage(this.i18n, toApiError(this.inquiries.error())) : null,
  );

  protected show(status: string): void {
    void this.router.navigate([], { queryParams: { status: status === 'NEW' ? null : status } });
  }

  protected goTo(page: number): void {
    void this.router.navigate([], { queryParams: { page }, queryParamsHandling: 'merge' });
  }

  protected onConverted(result: ConvertResponse): void {
    this.toast.success(this.i18n.t('inquiries.converted'));
    void this.router.navigate(['/deals', result.dealId]);
  }

  protected async dismiss(inquiry: Inquiry): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('inquiries.dismiss'),
      message: this.i18n.t('inquiries.dismissConfirm', { name: inquiry.name }),
      confirmLabel: this.i18n.t('inquiries.dismiss'),
      danger: true,
    });
    if (!ok) return;
    this.api.post<Inquiry>(`/inquiries/${inquiry.id}/dismiss`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('inquiries.dismissed'));
        this.inquiries.reload();
      },
      error: (err) => {
        this.toast.error(errorMessage(this.i18n, toApiError(err)));
        this.inquiries.reload();
      },
    });
  }
}
