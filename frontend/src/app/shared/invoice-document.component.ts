import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';

import { Invoice } from '../core/api/models';
import { FormatService } from '../core/i18n/format.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { InvoiceStatusComponent } from './invoice-status.component';

/**
 * The invoice as a document: who it is for, the dates, the items and the totals. Staff and the client see the
 * same layout, so what the client is asked to pay is exactly what the team issued.
 * الفاتورة كمستند: لمين، والتواريخ، والبنود، والمجاميع. الموظف والعميل يشوفون نفس الشكل، فاللي ينطلب من
 * العميل يدفعه هو بالضبط اللي أصدره الفريق.
 */
@Component({
  selector: 'app-invoice-document',
  imports: [TranslatePipe, InvoiceStatusComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let inv = invoice();
    <article class="card invoice-doc" data-testid="invoice-doc">
      <header class="invoice-doc__head">
        <div>
          <p class="muted">{{ 'invoices.invoice' | t }}</p>
          <h2 class="mono" dir="ltr" data-testid="invoice-number">{{ inv.number }}</h2>
          <app-invoice-status [status]="inv.status" [overdue]="inv.overdue" />
        </div>
        <dl class="invoice-doc__meta">
          <div>
            <dt>{{ 'invoices.company' | t }}</dt>
            <dd>
              <strong
                ><bdi>{{ inv.company.name }}</bdi></strong
              >
              @if (inv.contact) {
                <br /><span class="muted"
                  >{{ 'invoices.contact' | t }}: <bdi>{{ inv.contact.fullName }}</bdi></span
                >
              }
            </dd>
          </div>
          <div>
            <dt>{{ 'invoices.issueDate' | t }}</dt>
            <dd>{{ fmt.plainDate(inv.issueDate) }}</dd>
          </div>
          <div>
            <dt>{{ 'invoices.dueDate' | t }}</dt>
            <dd [class.overdue]="inv.overdue">{{ fmt.plainDate(inv.dueDate) }}</dd>
          </div>
          @if (inv.paidAt) {
            <div>
              <dt>{{ 'invoices.paidAt' | t }}</dt>
              <dd>{{ fmt.dateTime(inv.paidAt) }}</dd>
            </div>
          }
        </dl>
      </header>

      @if (inv.voidReason) {
        <p class="alert alert--danger">
          {{ 'invoices.voidReason' | t }}: <bdi>{{ inv.voidReason }}</bdi>
        </p>
      }

      <div class="table-wrap">
        <table>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">{{ 'invoices.description' | t }}</th>
              <th scope="col" class="cell-end">{{ 'invoices.quantity' | t }}</th>
              <th scope="col" class="cell-end">{{ 'invoices.unitPrice' | t }}</th>
              <th scope="col" class="cell-end">{{ 'invoices.lineTotal' | t }}</th>
            </tr>
          </thead>
          <tbody>
            @for (line of inv.lines; track line.position) {
              <tr>
                <td class="num">{{ fmt.number($index + 1) }}</td>
                <td>
                  <bdi>{{ line.description }}</bdi>
                </td>
                <td class="cell-end num">{{ fmt.number(line.quantity) }}</td>
                <td class="cell-end num">{{ fmt.money(line.unitPrice) }}</td>
                <td class="cell-end num">{{ fmt.money(line.lineTotal) }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <dl class="totals">
        <div>
          <dt>{{ 'invoices.subtotal' | t }}</dt>
          <dd class="num">{{ fmt.money(inv.subtotal) }}</dd>
        </div>
        <div>
          <dt>{{ 'invoices.vat' | t }}</dt>
          <dd class="num">{{ fmt.money(inv.vat) }}</dd>
        </div>
        <div class="totals__grand">
          <dt>{{ 'invoices.total' | t }}</dt>
          <dd class="num" data-testid="invoice-total">{{ fmt.money(inv.total) }}</dd>
        </div>
      </dl>

      @if (inv.notes) {
        <p class="invoice-doc__notes">
          <strong>{{ 'invoices.notes' | t }}:</strong> <bdi>{{ inv.notes }}</bdi>
        </p>
      }
    </article>
  `,
})
export class InvoiceDocumentComponent {
  protected readonly fmt = inject(FormatService);
  readonly invoice = input.required<Invoice>();
}
