import { TestBed } from '@angular/core/testing';

import { Invoice } from '../core/api/models';
import { I18nService } from '../core/i18n/i18n.service';
import { InvoiceDocumentComponent } from './invoice-document.component';

const invoice: Invoice = {
  id: 7,
  number: 'INV-000007',
  company: { id: 1, name: 'Al Noor Trading Co.' },
  contact: { id: 3, fullName: 'Faisal Al-Dosari' },
  deal: null,
  status: 'ISSUED',
  overdue: true,
  issueDate: '2026-10-01',
  dueDate: '2026-10-15',
  subtotal: 2100,
  vat: 315,
  total: 2415,
  notes: 'Thank you for your business',
  lines: [
    { position: 1, description: 'Support hours', quantity: 6, unitPrice: 350, lineTotal: 2100 },
  ],
  issuedAt: '2026-10-01T08:00:00Z',
  paidAt: null,
  voidedAt: null,
  voidReason: null,
  createdAt: '2026-09-30T08:00:00Z',
  version: 2,
};

function render(value: Invoice): HTMLElement {
  const fixture = TestBed.createComponent(InvoiceDocumentComponent);
  fixture.componentRef.setInput('invoice', value);
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('InvoiceDocumentComponent', () => {
  beforeEach(() => TestBed.inject(I18nService).setLang('en'));

  it('shows the lines, the VAT and the total the server calculated', () => {
    const el = render(invoice);
    expect(el.querySelector('[data-testid="invoice-number"]')!.textContent).toContain('INV-000007');
    expect(el.querySelectorAll('tbody tr')).toHaveLength(1);
    expect(el.textContent).toContain('Faisal Al-Dosari');
    expect(el.querySelector('[data-testid="invoice-total"]')!.textContent).toContain('2,415.00');
    expect(el.textContent).toContain('Thank you for your business');
  });

  it('marks an issued invoice past its due date as overdue', () => {
    expect(render(invoice).querySelector('[data-status]')!.textContent).toContain('Overdue');
    const onTime = render({ ...invoice, overdue: false });
    expect(onTime.querySelector('[data-status]')!.textContent).toContain('Issued');
  });

  it('shows why a voided invoice was cancelled', () => {
    const el = render({ ...invoice, status: 'VOID', overdue: false, voidReason: 'Wrong quantity' });
    expect(el.textContent).toContain('Wrong quantity');
    expect(el.querySelector('[data-status="VOID"]')).not.toBeNull();
  });
});
