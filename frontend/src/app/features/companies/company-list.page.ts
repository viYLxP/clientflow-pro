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
import { Company, Page, UserRef } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';

/**
 * Companies with search (name, city, industry) and an account-manager filter; filters live in the URL.
 * الشركات مع البحث (الاسم، المدينة، القطاع) وفلتر مدير الحساب؛ والفلاتر في الرابط.
 */
@Component({
  selector: 'app-company-list',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-list.page.html',
})
export class CompanyListPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);

  readonly q = input<string | undefined>('');
  readonly ownerId = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  protected readonly searchText = linkedSignal(() => this.q() ?? '');
  protected readonly team = rxResource({ stream: () => this.api.get<UserRef[]>('/team') });
  protected readonly companies = rxResource({
    params: () => ({
      q: this.q(),
      ownerId: this.ownerId(),
      page: Number(this.page() ?? 0) || 0,
      size: 20,
    }),
    stream: ({ params }) => this.api.get<Page<Company>>('/companies', params),
  });
  protected readonly errorText = computed(() =>
    this.companies.error() ? errorMessage(this.i18n, toApiError(this.companies.error())) : null,
  );
  protected readonly hasFilters = computed(() => !!(this.q() || this.ownerId()));

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
