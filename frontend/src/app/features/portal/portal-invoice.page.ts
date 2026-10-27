import { ChangeDetectionStrategy, Component, computed, inject, input, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { Router, RouterLink } from '@angular/router';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { CheckoutResponse, PaymentMethod, PortalInvoice } from '../../core/api/models';
import { openCheckout } from '../../core/checkout';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';
import { InvoiceDocumentComponent } from '../../shared/invoice-document.component';
import { PaymentPanelComponent } from '../../shared/payment-panel.component';

/**
 * One invoice in the portal (/portal/invoices/:number): the document and the payment card next to it.
 * فاتورة وحدة في البوابة (‎/portal/invoices/:number): المستند وبطاقة الدفع جنبه.
 */
@Component({
  selector: 'app-portal-invoice',
  imports: [
    RouterLink,
    TranslatePipe,
    IconComponent,
    InvoiceDocumentComponent,
    PaymentPanelComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="breadcrumb no-print" routerLink="/portal/invoices">
      <app-icon name="chevron" class="icon--back" [flip]="true" /> {{ 'portal.back' | t }}
    </a>
    @if (errorText()) {
      <div class="alert alert--danger" role="alert">{{ errorText() }}</div>
    } @else if (detail.isLoading() && !detail.hasValue()) {
      <div class="loading" role="status"><span class="spinner"></span>{{ 'app.loading' | t }}</div>
    } @else if (detail.value(); as d) {
      <!-- The document shows the number as its own heading; the page title is for screen readers. -->
      <!-- المستند يعرض الرقم كعنوان له؛ وعنوان الصفحة لقارئ الشاشة. -->
      <h1 class="visually-hidden">{{ 'invoices.invoice' | t }} {{ d.invoice.number }}</h1>
      <div class="split">
        <div>
          <app-invoice-document [invoice]="d.invoice" />
          <p class="section-gap no-print">
            <button type="button" class="btn" (click)="print()">{{ 'portal.print' | t }}</button>
          </p>
        </div>
        <div class="no-print">
          <app-payment-panel [payment]="d.payment" [busy]="paying()" (pay)="pay(d, $event)" />
        </div>
      </div>
    }
  `,
})
export class PortalInvoicePage {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);

  readonly number = input.required<string>();

  protected readonly detail = rxResource({
    params: () => this.number(),
    stream: ({ params }) =>
      this.api.get<PortalInvoice>(`/portal/invoices/${encodeURIComponent(params)}`),
  });
  protected readonly errorText = computed(() =>
    this.detail.error() ? errorMessage(this.i18n, toApiError(this.detail.error())) : null,
  );
  protected readonly paying = signal(false);

  protected pay(d: PortalInvoice, method: PaymentMethod): void {
    this.paying.set(true);
    this.api
      .post<CheckoutResponse>(`/portal/invoices/${encodeURIComponent(d.invoice.number)}/pay`, {
        method,
      })
      .subscribe({
        next: (checkout) => openCheckout(this.router, checkout),
        error: (err) => {
          this.paying.set(false);
          this.toast.error(errorMessage(this.i18n, toApiError(err)));
          // The invoice may have changed (paid elsewhere, voided): show its current state.
          // يمكن الفاتورة تغيّرت (انْدفعت أو انلغت): نعرض حالتها الحالية.
          this.detail.reload();
        },
      });
  }

  protected print(): void {
    window.print();
  }
}
