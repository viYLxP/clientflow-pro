import { expect, test } from '@playwright/test';

import { FAISAL, SARA, createAlNoorContact, issueInvoiceToFaisal, login, unique } from './helpers';

// Invoices, the client portal and online payments, end to end on the real stack (test gateway, no real money).
// الفواتير وبوابة العميل والدفع الإلكتروني، من البداية للنهاية على النظام الحقيقي (بوابة تجريبية، بدون فلوس حقيقية).

test.describe('invoices and the client portal', () => {
  test('a rep builds and issues an invoice; the VAT preview matches the server', async ({
    page,
  }) => {
    const item = `Consulting ${unique()}`;
    await login(page, SARA);
    await page.goto('/invoices');
    await page.getByTestId('new-invoice').click();
    await page.getByTestId('inv-company').selectOption({ label: 'Al Noor Trading Co.' });
    await page.locator('#l-desc-0').fill(item);
    await page.locator('#l-qty-0').fill('3');
    await page.locator('#l-price-0').fill('0.10');
    await page.getByTestId('add-line').click();
    await page.locator('#l-desc-1').fill('Setup');
    await page.locator('#l-qty-1').fill('1');
    await page.locator('#l-price-1').fill('1000');
    // 1000.30 + 15% VAT (150.045 → 150.05) = 1150.35. / ‏1000.30 + ضريبة 15% (150.045 ← 150.05) = 1150.35.
    await expect(page.getByTestId('form-total')).toContainText('1,150.35');
    await page.getByTestId('save-invoice').click();

    await expect(page).toHaveURL(/\/invoices\/\d+$/);
    await expect(page.getByTestId('invoice-total')).toContainText('1,150.35');
    await expect(page.locator('[data-status="DRAFT"]')).toBeVisible();
    await page.getByTestId('issue-invoice').click();
    await page.getByTestId('confirm-yes').click();
    await expect(page.locator('[data-status="ISSUED"]')).toBeVisible();
    await expect(page.getByTestId('issue-invoice')).toHaveCount(0);
  });

  test('the client pays by card: a declined card, then an approved one', async ({
    page,
    request,
  }) => {
    const { id, number } = await issueInvoiceToFaisal(request, `Card test ${unique()}`);
    await login(page, FAISAL);
    await page.getByTestId(`open-${number}`).click();
    await expect(page).toHaveURL(new RegExp(`/portal/invoices/${number}$`));
    // 2 × 300 + 15% = 690 SAR. / ‏2 × 300 + 15% = 690 ريال.
    await expect(page.getByTestId('payment-due')).toContainText('690');
    await page.getByTestId('pay-now').click();

    await expect(page).toHaveURL(/\/pay\/[0-9a-f-]+$/);
    await expect(page.getByTestId('sandbox-banner')).toBeVisible();
    await page.getByTestId('use-card-0002').click();
    await page.getByTestId('card-submit').click();
    await expect(page.getByTestId('pay-failed')).toBeVisible();
    await page.getByTestId('use-card-1111').click();
    await page.getByTestId('card-submit').click();
    await expect(page.getByTestId('pay-success')).toBeVisible();

    await page.getByTestId('pay-success').getByRole('link').click();
    await expect(page.getByTestId('payment-paid')).toContainText('1111');
    await expect(page.locator('[data-status="PAID"]')).toBeVisible();

    // The team sees the payment on the invoice. A retry on the same payment page is one attempt.
    // الفريق يشوف الدفعة على الفاتورة. وإعادة المحاولة في نفس صفحة الدفع تنحسب محاولة وحدة.
    await page.getByTestId('logout').click();
    await login(page, SARA);
    await page.goto(`/invoices/${id}`);
    const history = page.getByTestId('payment-history');
    await expect(history.locator('li')).toHaveCount(1);
    await expect(history.locator('.badge--pay-PAID')).toBeVisible();
    await expect(history).toContainText('1111');
  });

  test('the client splits an invoice into 4 payments with Tabby', async ({ page, request }) => {
    const { number } = await issueInvoiceToFaisal(request, `Tabby test ${unique()}`, 400);
    await login(page, FAISAL);
    await page.goto(`/portal/invoices/${number}`);
    await page.getByTestId('method-TABBY').check({ force: true });
    await expect(page.getByTestId('payment-plan').locator('li')).toHaveCount(4);
    await page.getByTestId('pay-now').click();
    await expect(page.getByTestId('bnpl-plan').locator('li')).toHaveCount(4);
    await page.getByTestId('bnpl-confirm').click();
    await expect(page.getByTestId('pay-success')).toBeVisible();
  });

  test('a bank transfer marks the invoice paid, and voiding it records the reason', async ({
    page,
    request,
  }) => {
    const { id } = await issueInvoiceToFaisal(request, `Transfer test ${unique()}`);
    await login(page, SARA);
    await page.goto(`/invoices/${id}`);
    await page.getByTestId('record-transfer').click();
    await page.getByTestId('prompt-input').fill('RJHI-TRX-1001');
    await page.getByTestId('prompt-ok').click();
    await expect(page.locator('[data-status="PAID"]')).toBeVisible();
    await expect(page.getByTestId('payment-history')).toContainText('sara');

    await page.getByTestId('void-invoice').click();
    // The reason is required. / السبب مطلوب.
    await page.getByTestId('prompt-ok').click();
    await expect(page.locator('#prompt-error')).not.toBeEmpty();
    await page.getByTestId('prompt-input').fill('Duplicate invoice');
    await page.getByTestId('prompt-ok').click();
    await expect(page.locator('[data-status="VOID"]')).toBeVisible();
    await expect(page.getByTestId('invoice-doc')).toContainText('Duplicate invoice');
  });

  test('clients and staff each stay in their own area', async ({ page }) => {
    await login(page, FAISAL);
    await page.goto('/dashboard');
    await expect(page).toHaveURL(/\/portal\/invoices$/);
    await page.goto('/invoices');
    await expect(page).toHaveURL(/\/portal\/invoices$/);
    // Drafts never reach the portal. / المسودات ما توصل للبوابة.
    await expect(page.getByTestId('portal-invoices').locator('[data-status="DRAFT"]')).toHaveCount(
      0,
    );
    await page.getByTestId('logout').click();
    await expect(page).toHaveURL(/\/$/);

    await login(page, SARA);
    await page.goto('/portal/invoices');
    await expect(page).toHaveURL(/\/dashboard$/);
  });

  test('a rep invites a contact, who sets a password and lands in the portal', async ({
    page,
    browser,
    request,
  }) => {
    const email = `noor.${unique().toLowerCase()}@alnoor.example`;
    await createAlNoorContact(request, 'Reem', email);
    await login(page, SARA);
    await page.goto(`/contacts?q=${email}`);
    const row = page.locator('tbody tr').first();
    await row.locator('[data-testid^="portal-"]').click();
    await page.getByTestId('portal-invite').click();
    const url = await page.getByTestId('invite-url').inputValue();
    expect(url).toContain('/activate#');
    await expect(page.getByTestId('portal-status')).toBeVisible();

    // The client opens the link in their own browser. / العميل يفتح الرابط في متصفحه.
    const client = await browser.newContext({ locale: 'en-US' });
    const tab = await client.newPage();
    // Same page as the link, opened on the address under test. / نفس صفحة الرابط، على العنوان اللي نختبره.
    await tab.goto(`/activate${new URL(url).hash}`);
    await expect(tab.getByTestId('invite-welcome')).toContainText('Reem');
    // The token leaves the address bar once read. / الرمز ينشال من شريط العنوان بعد ما ينقرا.
    await expect(tab).toHaveURL(/\/activate$/);
    await tab.getByTestId('activate-password').fill('Reem@2026!');
    await tab.getByTestId('activate-confirm').fill('Reem@2026?');
    await tab.getByTestId('activate-submit').click();
    await expect(tab.locator('#a-confirm-err')).not.toBeEmpty();
    await tab.getByTestId('activate-confirm').fill('Reem@2026!');
    await tab.getByTestId('activate-submit').click();
    await expect(tab).toHaveURL(/\/portal\/invoices$/);
    await expect(tab.getByTestId('portal-hello')).toContainText('Reem');
    await client.close();

    // The same link cannot be used twice. / نفس الرابط ما ينفع مرتين.
    const again = await browser.newContext();
    const second = await again.newPage();
    await second.goto(`/activate${new URL(url).hash}`);
    await expect(second.getByTestId('invite-invalid')).toBeVisible();
    await again.close();
  });
});

test.describe('website inquiries', () => {
  test('a visitor sends a message and a rep turns it into a lead', async ({ page }) => {
    const company = `Desert Bloom ${unique()}`;
    await page.goto('/');
    await page.getByTestId('inquiry-submit').click();
    await expect(page.locator('#q-name-err')).not.toBeEmpty();
    await page.locator('#q-name').fill('Mona Al-Harbi');
    await page.locator('#q-company').fill(company);
    await page.locator('#q-email').fill(`mona.${unique().toLowerCase()}@example.com`);
    await page.locator('#q-message').fill('We have 4 sales reps and want online invoices.');
    await page.getByTestId('inquiry-submit').click();
    await expect(page.getByTestId('inquiry-sent')).toBeVisible();

    await login(page, SARA);
    await page.goto('/inquiries');
    const card = page.locator('[data-testid^="inquiry-"]', {
      hasText: company,
    });
    await expect(card).toBeVisible();
    await card.locator('[data-testid^="convert-"]').click();
    await page.getByTestId('convert-amount').fill('36000');
    await page.getByTestId('convert-save').click();
    await expect(page).toHaveURL(/\/deals\/\d+$/);
    await expect(page.getByTestId('deal-title')).toContainText(company);
    await expect(page.getByTestId('deal-stage')).toBeVisible();
  });
});
