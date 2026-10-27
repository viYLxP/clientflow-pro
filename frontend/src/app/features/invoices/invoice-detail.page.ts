import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { InvoiceDetail } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { PromptService } from '../../core/ui/prompt.service';
import { ToastService } from '../../core/ui/toast.service';
import { InvoiceDocumentComponent } from '../../shared/invoice-document.component';
import { PaymentBadgeComponent } from '../../shared/payment-badge.component';

/**
 * One invoice for the team: the document, its life-cycle actions and every payment attempt.
 * Draft → Issue (the client can now see and pay it) → Paid (online, or a bank transfer recorded here).
 * Void at any point after issue; a paid online invoice is refunded through its gateway first.
 * فاتورة وحدة للفريق: المستند، وأزرار دورة حياتها، وكل محاولات الدفع.
 * مسودة ← إصدار (العميل يقدر يشوفها ويدفعها) ← مدفوعة (أونلاين، أو تحويل بنكي ينسجل هنا).
 * الإلغاء بأي وقت بعد الإصدار؛ والفاتورة المدفوعة أونلاين يرجع مبلغها عن طريق بوابتها أول.
 */
@Component({
  selector: 'app-invoice-detail',
  imports: [
    RouterLink,
    TranslatePipe,
    IconComponent,
    InvoiceDocumentComponent,
    PaymentBadgeComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './invoice-detail.page.html',
})
export class InvoiceDetailPage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  private readonly prompt = inject(PromptService);
  protected readonly fmt = inject(FormatService);

  readonly id = input.required<string>();

  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params: id }) => this.api.get<InvoiceDetail>(`/invoices/${id}`),
  });
  protected readonly errorText = computed(() =>
    this.detail.error() ? errorMessage(this.i18n, toApiError(this.detail.error())) : null,
  );
  protected readonly busy = signal(false);

  protected async issue(d: InvoiceDetail): Promise<void> {
    const inv = d.invoice;
    const ok = await this.confirm.ask({
      title: this.i18n.t('invoices.issue'),
      message: this.i18n.t('invoices.issueConfirm', {
        number: inv.number,
        amount: this.fmt.money(inv.total),
      }),
      confirmLabel: this.i18n.t('invoices.issue'),
    });
    if (ok) {
      this.run(
        this.api.post(`/invoices/${inv.id}/issue`, { version: inv.version }),
        'invoices.issued',
      );
    }
  }

  protected async voidInvoice(d: InvoiceDetail): Promise<void> {
    const reason = await this.prompt.ask({
      title: this.i18n.t('invoices.voidTitle'),
      message: this.i18n.t('invoices.voidPrompt'),
      label: this.i18n.t('invoices.voidReason'),
      confirmLabel: this.i18n.t('invoices.void'),
      maxLength: 255,
      danger: true,
    });
    if (reason !== null) {
      this.run(
        this.api.post(`/invoices/${d.invoice.id}/void`, { reason, version: d.invoice.version }),
        'invoices.voided',
      );
    }
  }

  protected async recordTransfer(d: InvoiceDetail): Promise<void> {
    const reference = await this.prompt.ask({
      title: this.i18n.t('invoices.transferTitle'),
      message: this.i18n.t('invoices.transferPrompt'),
      label: this.i18n.t('payment.method.BANK_TRANSFER'),
      confirmLabel: this.i18n.t('invoices.transfer'),
      maxLength: 100,
      optional: true,
    });
    if (reference !== null) {
      this.run(
        this.api.post(`/invoices/${d.invoice.id}/bank-transfer`, {
          reference: reference || null,
          version: d.invoice.version,
        }),
        'invoices.transferred',
      );
    }
  }

  protected async remove(d: InvoiceDetail): Promise<void> {
    const ok = await this.confirm.ask({
      title: this.i18n.t('common.confirmTitle'),
      message: this.i18n.t('common.deleteConfirm', { name: d.invoice.number }),
      confirmLabel: this.i18n.t('common.delete'),
      danger: true,
    });
    if (!ok) return;
    this.api.delete(`/invoices/${d.invoice.id}`).subscribe({
      next: () => {
        this.toast.success(this.i18n.t('common.deleted'));
        void this.router.navigate(['/invoices']);
      },
      error: (err) => this.toast.error(errorMessage(this.i18n, toApiError(err))),
    });
  }

  // Every action answers with the updated invoice page. / كل إجراء يرد بصفحة الفاتورة بعد التحديث.
  private run(request: Observable<unknown>, successKey: string): void {
    this.busy.set(true);
    request.subscribe({
      next: (updated) => {
        this.busy.set(false);
        this.detail.set(updated as InvoiceDetail);
        this.toast.success(this.i18n.t(successKey));
      },
      error: (err) => {
        this.busy.set(false);
        this.toast.error(errorMessage(this.i18n, toApiError(err)));
        this.detail.reload();
      },
    });
  }
}
