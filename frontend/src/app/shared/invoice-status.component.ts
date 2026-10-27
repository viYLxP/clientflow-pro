import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

import { InvoiceStatus } from '../core/api/models';
import { TranslatePipe } from '../core/i18n/translate.pipe';

/**
 * Invoice status pill; an issued invoice past its due date shows "Overdue" instead.
 * شارة حالة الفاتورة؛ والفاتورة المصدرة اللي فات موعدها تظهر "متأخرة" بدالها.
 */
@Component({
  selector: 'app-invoice-status',
  imports: [TranslatePipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span [class]="'badge badge--' + css()" [attr.data-status]="status()">{{
    label() | t
  }}</span>`,
})
export class InvoiceStatusComponent {
  readonly status = input.required<InvoiceStatus>();
  readonly overdue = input(false);
  protected readonly css = computed(() => (this.overdue() ? 'overdue' : `inv-${this.status()}`));
  protected readonly label = computed(() =>
    this.overdue() ? 'invoices.overdue' : `invoices.status.${this.status()}`,
  );
}
