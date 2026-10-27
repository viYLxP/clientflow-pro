import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { InvoiceStatus, InvoiceSummary, Page, PortalMe } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { InvoiceStatusComponent } from '../../shared/invoice-status.component';

// Drafts never reach the client, so the filter offers only these. / المسودات ما توصل للعميل، فالفلتر يعرض هذي بس.
const CLIENT_STATUSES: InvoiceStatus[] = ['ISSUED', 'PAID', 'VOID'];

/**
 * The client's home: a greeting, how much is left to pay, and their company's invoices (?status=&page=).
 * صفحة العميل الرئيسية: ترحيب، وكم باقي عليه، وفواتير شركته (‎?status=&page=).
 */
@Component({
  selector: 'app-portal-invoices',
  imports: [RouterLink, TranslatePipe, PaginationComponent, InvoiceStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './portal-invoices.page.html',
})
export class PortalInvoicesPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  readonly status = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  protected readonly statuses = CLIENT_STATUSES;
  protected readonly me = rxResource({ stream: () => this.api.get<PortalMe>('/portal/me') });
  // Everything still to pay, for the summary card (a company rarely has more than a few open invoices).
  // كل اللي باقي ينْدفع، لبطاقة الملخص (الشركة نادرًا يكون عندها أكثر من كم فاتورة مفتوحة).
  protected readonly open = rxResource({
    stream: () =>
      this.api.get<Page<InvoiceSummary>>('/portal/invoices', { status: 'ISSUED', size: 100 }),
  });
  protected readonly toPay = computed(() =>
    (this.open.value()?.content ?? []).reduce((sum, i) => sum + i.total, 0),
  );

  protected readonly invoices = rxResource({
    params: () => ({ status: this.status(), page: Number(this.page() ?? 0) || 0, size: 20 }),
    stream: ({ params }) => this.api.get<Page<InvoiceSummary>>('/portal/invoices', params),
  });
  protected readonly errorText = computed(() =>
    this.invoices.error() ? errorMessage(this.i18n, toApiError(this.invoices.error())) : null,
  );

  protected setFilter(change: Record<string, string | number | null>): void {
    const pageChange = 'page' in change;
    void this.router.navigate([], {
      queryParams: { ...change, ...(pageChange ? {} : { page: null }) },
      queryParamsHandling: 'merge',
      replaceUrl: !pageChange,
    });
  }
}
