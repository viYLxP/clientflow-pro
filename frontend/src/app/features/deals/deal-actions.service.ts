import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Deal, DealStage } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { ConfirmService } from '../../core/ui/confirm.service';
import { PromptService } from '../../core/ui/prompt.service';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Moving a deal between stages, shared by the pipeline board and the deal page so both behave the same:
 * winning and reopening ask for confirmation, losing asks for the reason the server requires.
 * نقل الصفقة بين المراحل، مشترك بين لوحة خط المبيعات وصفحة الصفقة حتى يتصرفون بنفس الطريقة:
 * الفوز وإعادة الفتح يطلبون تأكيد، والخسارة تطلب السبب اللي يشترطه الخادم.
 */
@Injectable({ providedIn: 'root' })
export class DealActionsService {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly fmt = inject(FormatService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  private readonly toast = inject(ToastService);

  /**
   * Resolves to the updated deal, or null if the user cancelled or the server refused (a toast explains why).
   * يرجّع الصفقة بعد التحديث، أو null لو ألغى المستخدم أو رفض الخادم (والإشعار يوضح السبب).
   */
  async move(deal: Deal, stage: DealStage): Promise<Deal | null> {
    let lostReason: string | null = null;
    if (stage === 'LOST') {
      lostReason = await this.prompt.ask({
        title: this.i18n.t('deals.lostTitle'),
        message: this.i18n.t('deals.lostPrompt'),
        label: this.i18n.t('deals.lostReason'),
        confirmLabel: this.i18n.t('deals.markLost'),
        maxLength: 255,
        danger: true,
      });
      if (lostReason === null) return null;
    } else if (stage === 'WON') {
      const ok = await this.confirm.ask({
        title: this.i18n.t('deals.markWon'),
        message: this.i18n.t('deals.wonConfirm', {
          title: deal.title,
          amount: this.fmt.money(deal.amount),
        }),
        confirmLabel: this.i18n.t('deals.markWon'),
      });
      if (!ok) return null;
    } else if (deal.stage === 'WON' || deal.stage === 'LOST') {
      const ok = await this.confirm.ask({
        title: this.i18n.t('deals.reopen'),
        message: this.i18n.t('deals.reopenConfirm', { title: deal.title }),
        confirmLabel: this.i18n.t('deals.reopen'),
      });
      if (!ok) return null;
    }
    try {
      const updated = await firstValueFrom(
        this.api.post<Deal>(`/deals/${deal.id}/stage`, {
          stage,
          lostReason,
          // The version makes a stale board fail loudly instead of overwriting a colleague's move.
          // النسخة تخلي اللوحة القديمة تفشل بوضوح بدل ما تكتب فوق نقل سواه زميل.
          version: deal.version,
        }),
      );
      this.toast.success(this.i18n.t('pipeline.moved', { stage: this.i18n.t(`stage.${stage}`) }));
      return updated;
    } catch (err) {
      this.toast.error(errorMessage(this.i18n, toApiError(err)));
      return null;
    }
  }
}
