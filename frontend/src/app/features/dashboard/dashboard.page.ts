import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Dashboard, Task } from '../../core/api/models';
import { AuthService } from '../../core/auth/auth.service';
import { ACTIVITY_ICONS } from '../../core/crm';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Dashboard built from /api/dashboard. Every figure comes from the live database.
 * لوحة التحكم مبنية من /api/dashboard. كل رقم جاي من قاعدة البيانات الحية.
 *
 * ?scope=mine|team chooses whose pipeline is shown. Reps start on their own numbers and admins on the team's;
 * "My tasks" is always personal.
 * ?scope=mine|team يحدد خط مبيعات مين يظهر. المندوب يبدأ بأرقامه والمدير بأرقام الفريق؛ و"مهامي" دائمًا شخصية.
 */
@Component({
  selector: 'app-dashboard',
  imports: [RouterLink, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './dashboard.page.html',
})
export class DashboardPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  protected readonly auth = inject(AuthService);
  protected readonly fmt = inject(FormatService);

  protected readonly icons = ACTIVITY_ICONS;

  readonly scope = input<string | undefined>();
  protected readonly mine = computed(() =>
    this.scope() ? this.scope() === 'mine' : !this.auth.isAdmin(),
  );

  protected readonly data = rxResource({
    params: () => ({ ownerId: this.mine() ? this.auth.user()?.id : null }),
    stream: ({ params }) => this.api.get<Dashboard>('/dashboard', params),
  });

  protected readonly errorText = computed(() =>
    this.data.error() ? errorMessage(this.i18n, toApiError(this.data.error())) : null,
  );

  // Bar widths relative to the largest stage, so the biggest stage fills the track.
  // عرض الأعمدة نسبةً لأكبر مرحلة، فأكبر مرحلة تعبّي المسار كامل.
  protected readonly maxStageAmount = computed(() =>
    Math.max(1, ...(this.data.value()?.pipeline.map((s) => s.amount) ?? [0])),
  );

  protected setScope(mine: boolean): void {
    void this.router.navigate([], {
      queryParams: { scope: mine ? 'mine' : 'team' },
      replaceUrl: true,
    });
  }

  protected complete(task: Task): void {
    this.api.post<Task>(`/tasks/${task.id}/complete`, { version: task.version }).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('tasks.completed'));
        this.data.reload();
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }
}
