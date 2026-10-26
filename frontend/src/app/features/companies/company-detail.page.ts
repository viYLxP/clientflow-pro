import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CompanyDetail, Contact, Deal, Page } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { canChange, websiteHref } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { ActivityTimelineComponent } from '../shared/activity-timeline.component';
import { TaskPanelComponent } from '../shared/task-panel.component';

/**
 * One company (account): totals, people, deals, activity log and tasks.
 * صفحة شركة وحدة (حساب): الإجماليات، الأشخاص، الصفقات، سجل التواصل، والمهام.
 */
@Component({
  selector: 'app-company-detail',
  imports: [
    RouterLink,
    TranslatePipe,
    IconComponent,
    ActivityTimelineComponent,
    TaskPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './company-detail.page.html',
})
export class CompanyDetailPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);

  readonly id = input.required<string>();
  protected readonly companyId = computed(() => Number(this.id()));

  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.get<CompanyDetail>(`/companies/${id}`),
  });
  protected readonly contacts = rxResource({
    params: () => this.id(),
    stream: ({ params: companyId }) =>
      this.api.get<Page<Contact>>('/contacts', { companyId, size: 50 }),
  });
  protected readonly deals = rxResource({
    params: () => this.id(),
    stream: ({ params: companyId }) => this.api.get<Page<Deal>>('/deals', { companyId, size: 50 }),
  });

  protected readonly errorText = computed(() =>
    this.detail.error() ? errorMessage(this.i18n, toApiError(this.detail.error())) : null,
  );
  protected readonly canEdit = computed(() => {
    const c = this.detail.value()?.company;
    return !!c && canChange(this.auth.user(), [c.owner]);
  });
  protected readonly websiteLink = computed(() =>
    websiteHref(this.detail.value()?.company.website ?? null),
  );

  protected async remove(): Promise<void> {
    const c = this.detail.value()?.company;
    if (!c) return;
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: c.name }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/companies/${c.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        void this.router.navigate(['/companies']);
      },
      // IN_USE carries the counts, so the message says exactly what blocks the delete.
      // IN_USE يحمل الأعداد، فالرسالة توضح بالضبط وش يمنع الحذف.
      error: (err) => {
        const apiError = toApiError(err);
        this.toast.error(
          apiError.code === 'IN_USE'
            ? this.i18n.t('companies.inUse', {
                deals: Number(apiError.details['deals'] ?? 0),
                contacts: Number(apiError.details['contacts'] ?? 0),
              })
            : errorMessage(this.i18n, apiError),
        );
      },
    });
  }
}
