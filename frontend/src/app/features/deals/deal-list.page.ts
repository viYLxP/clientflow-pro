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
import { ALL_STAGES, Deal, Page, UserRef } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { PaginationComponent } from '../../core/ui/pagination.component';

/**
 * All deals with search and filters. Filters live in the URL (?q=&stage=&status=&ownerId=&page=),
 * so a filtered view can be bookmarked and the Back button restores it.
 * كل الصفقات مع البحث والفلاتر. الفلاتر في الرابط، فتقدر تحفظ العرض المفلتر وزر الرجوع يرجّعه.
 */
@Component({
  selector: 'app-deal-list',
  imports: [RouterLink, TranslatePipe, IconComponent, PaginationComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './deal-list.page.html',
})
export class DealListPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  protected readonly fmt = inject(FormatService);

  // A query param missing from the URL arrives as undefined, hence the union types.
  // الباراميتر غير الموجود في الرابط يوصل undefined، لذلك الأنواع تشمل undefined.
  readonly q = input<string | undefined>('');
  readonly stage = input<string | undefined>('');
  readonly status = input<string | undefined>('');
  readonly ownerId = input<string | undefined>('');
  readonly page = input<string | undefined>('0');

  protected readonly searchText = linkedSignal(() => this.q() ?? '');
  protected readonly stages = ALL_STAGES;
  protected readonly team = rxResource({ stream: () => this.api.get<UserRef[]>('/team') });

  protected readonly deals = rxResource({
    params: () => ({
      q: this.q(),
      stage: this.stage(),
      status: this.status(),
      ownerId: this.ownerId(),
      page: Number(this.page() ?? 0) || 0,
      size: 20,
    }),
    stream: ({ params }) => this.api.get<Page<Deal>>('/deals', params),
  });

  protected readonly errorText = computed(() =>
    this.deals.error() ? errorMessage(this.i18n, toApiError(this.deals.error())) : null,
  );
  protected readonly hasFilters = computed(
    () => !!(this.q() || this.stage() || this.status() || this.ownerId()),
  );

  private searchTimer: ReturnType<typeof setTimeout> | undefined;

  // Debounced: one request 300 ms after the last keystroke. / مؤجل: طلب واحد بعد 300 ملي ثانية من آخر حرف.
  protected onSearch(value: string): void {
    this.searchText.set(value);
    clearTimeout(this.searchTimer);
    this.searchTimer = setTimeout(() => this.setFilter({ q: value.trim() || null }), 300);
  }

  // Any filter change goes back to the first page. / أي تغيير في الفلاتر يرجّع للصفحة الأولى.
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
