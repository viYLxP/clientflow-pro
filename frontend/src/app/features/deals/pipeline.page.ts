import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { BoardColumn, Deal, DealStage, OPEN_STAGES } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { canChange, riyadhToday } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { DealActionsService } from './deal-actions.service';

/**
 * Kanban-style pipeline. Deals move with a stage menu and Won / Lost buttons rather than drag and drop,
 * so the board works the same with a keyboard, a screen reader and a phone.
 * لوحة خط المبيعات. الصفقات تنتقل بقائمة المرحلة وأزرار الفوز والخسارة بدل السحب والإفلات،
 * فاللوحة تشتغل بنفس الطريقة مع لوحة المفاتيح وقارئ الشاشة والجوال.
 */
@Component({
  selector: 'app-pipeline',
  imports: [RouterLink, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './pipeline.page.html',
})
export class PipelinePage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly actions = inject(DealActionsService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);

  readonly scope = input<string | undefined>();
  protected readonly mine = computed(() =>
    this.scope() ? this.scope() === 'mine' : !this.auth.isAdmin(),
  );
  protected readonly stages = OPEN_STAGES;
  protected readonly today = riyadhToday();

  protected readonly board = rxResource({
    params: () => ({ ownerId: this.mine() ? this.auth.user()?.id : null }),
    stream: ({ params }) => this.api.get<BoardColumn[]>('/deals/board', params),
  });
  protected readonly errorText = computed(() =>
    this.board.error() ? errorMessage(this.i18n, toApiError(this.board.error())) : null,
  );

  protected setScope(mine: boolean): void {
    void this.router.navigate([], {
      queryParams: { scope: mine ? 'mine' : 'team' },
      replaceUrl: true,
    });
  }

  protected canMove(deal: Deal): boolean {
    return canChange(this.auth.user(), [deal.owner]);
  }

  protected async move(deal: Deal, stage: string, select?: HTMLSelectElement): Promise<void> {
    // Put the menu back to its placeholder at once, so it never shows a stage the deal is not in.
    // نرجّع القائمة لخيارها الأول فورًا، حتى ما تعرض مرحلة الصفقة مو فيها.
    if (select) select.value = '';
    if (!stage) return;
    const updated = await this.actions.move(deal, stage as DealStage);
    if (updated) this.board.reload();
  }
}
