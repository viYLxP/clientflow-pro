import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
  linkedSignal,
} from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { InvoiceStatus, InvoiceSummary, Page } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';
import { InvoiceStatusComponent } from '../../shared/invoice-status.component';

const STATUSES: InvoiceStatus[] = ['DRAFT', 'ISSUED', 'PAID', 'VOID'];

/**
 * All invoices with search and filters in the URL (?q=&status=&overdue=1&page=).
 * كل الفواتير مع البحث والفلاتر في الرابط (‎?q=&status=&overdue=1&page=).
 */
@Component({
  selector: 'app-invoice-list',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent, InvoiceStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invoice-list.page.html',
})
export class InvoiceListPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  readonly q = input<string | undefined>('');
  readonly status = input<string | undefined>('');
  readonly overdue = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  protected readonly searchText = linkedSignal(() => this.q() ?? '');
  protected readonly statuses = STATUSES;

  protected readonly invoices = rxResource({
    params: () => ({
      q: this.q(),
      status: this.status(),
      overdue: this.overdue() ? true : undefined,
      page: Number(this.page() ?? 0) || 0,
      size: 20,
    }),
    stream: ({ params }) => this.api.get<Page<InvoiceSummary>>('/invoices', params),
  });
  protected readonly errorText = computed(() =>
    this.invoices.error() ? errorMessage(this.i18n, toApiError(this.invoices.error())) : null,
  );
  protected readonly hasFilters = computed(() => !!(this.q() || this.status() || this.overdue()));

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

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
}
