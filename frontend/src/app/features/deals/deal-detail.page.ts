import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { of } from 'rxjs';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CompanyDetail, Deal, DealStage, OPEN_STAGES, StageChange } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { canChange, isClosed, nextStage } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { ActivityTimelineComponent } from '../shared/activity-timeline.component';
import { TaskPanelComponent } from '../shared/task-panel.component';
import { DealActionsService } from './deal-actions.service';

/**
 * One deal: progress stepper, stage actions, details, activity log, tasks and stage history.
 * صفحة صفقة وحدة: مؤشر التقدم، أزرار المراحل، التفاصيل، سجل التواصل، المهام، وسجل المراحل.
 */
@Component({
  selector: 'app-deal-detail',
  imports: [
    RouterLink,
    TranslatePipe,
    IconComponent,
    ActivityTimelineComponent,
    TaskPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './deal-detail.page.html',
})
export class DealDetailPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly actions = inject(DealActionsService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);

  readonly id = input.required<string>();
  protected readonly dealId = computed(() => Number(this.id()));

  protected readonly deal = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.get<Deal>(`/deals/${id}`),
  });
  protected readonly history = rxResource({
    // Reload the history whenever the deal changes (its version goes up). / نحدّث السجل كل ما تغيّرت الصفقة.
    params: () => (this.deal.value() ? `${this.id()}:${this.deal.value()?.version}` : null),
    stream: ({ params }) =>
      params ? this.api.get<StageChange[]>(`/deals/${this.id()}/history`) : of([]),
  });

  protected readonly errorText = computed(() =>
    this.deal.error() ? errorMessage(this.i18n, toApiError(this.deal.error())) : null,
  );
  protected readonly canEdit = computed(() => {
    const d = this.deal.value();
    return !!d && canChange(this.auth.user(), [d.owner]);
  });
  // Invoices are issued by the company's account manager (or an admin), not the deal owner.
  // الفواتير يصدرها مدير حساب الشركة (أو المدير)، مو مالك الصفقة.
  protected readonly canInvoice = computed(() => {
    const d = this.deal.value();
    return !!d && (this.auth.isAdmin() || this.companyOwnerId() === this.auth.user()?.id);
  });
  private readonly company = rxResource({
    params: () => this.deal.value()?.company.id,
    stream: ({ params: id }) => (id ? this.api.get<CompanyDetail>(`/companies/${id}`) : of(null)),
  });
  private readonly companyOwnerId = computed(() => this.company.value()?.company.owner.id);
  protected readonly closed = computed(() => {
    const d = this.deal.value();
    return !!d && isClosed(d.stage);
  });
  protected readonly next = computed(() => {
    const d = this.deal.value();
    return d ? nextStage(d.stage) : null;
  });

  /**
   * Stepper cells: the four open stages, then the outcome (won or lost) once there is one.
   * خانات المؤشر: المراحل الأربع المفتوحة، ثم النتيجة (رابحة أو خاسرة) لما تكون موجودة.
   */
  protected readonly steps = computed(() => {
    const d = this.deal.value();
    if (!d) return [];
    const current = OPEN_STAGES.indexOf(d.stage);
    const steps: { stage: DealStage; done: boolean; current: boolean }[] = OPEN_STAGES.map(
      (stage, i) => ({
        stage,
        // A closed deal went through every open stage before its outcome.
        // الصفقة المغلقة مرت بكل المراحل المفتوحة قبل نتيجتها.
        done: current === -1 || i < current,
        current: i === current,
      }),
    );
    steps.push({
      stage: d.stage === 'LOST' ? 'LOST' : 'WON',
      done: false,
      current: isClosed(d.stage),
    });
    return steps;
  });

  protected async move(stage: DealStage): Promise<void> {
    const d = this.deal.value();
    if (!d) return;
    const updated = await this.actions.move(d, stage);
    if (updated) this.deal.set(updated);
  }

  protected async remove(): Promise<void> {
    const d = this.deal.value();
    if (!d) return;
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: d.title }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/deals/${d.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        void this.router.navigate(['/deals']);
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
