import { APIRequestContext, Page, expect } from '@playwright/test';

// Demo accounts created by the seeder (passwords come from .env; these are the documented defaults).
// الحسابات التجريبية اللي ينشئها الـ seeder (كلمات المرور من .env، وهذي القيم الافتراضية الموثقة).
export const ADMIN = { username: 'admin', password: process.env['ADMIN_PASSWORD'] ?? 'Admin@12345' };
const SALES_PASSWORD = process.env['SALES_PASSWORD'] ?? 'Sales@12345';
export const SARA = { username: 'sara', password: SALES_PASSWORD };
export const KHALID = { username: 'khalid', password: SALES_PASSWORD };
// The demo client signs in to the portal with their email. / العميل التجريبي يدخل البوابة بإيميله.
export const FAISAL = { username: 'faisal@alnoor.example', password: process.env['CLIENT_PASSWORD'] ?? 'Client@12345' };
type Account = typeof ADMIN;

export async function login(page: Page, user: Account = ADMIN): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('form button[type=submit]').click();
  // Staff land on the dashboard, clients in their portal. / الموظف يوصل للوحة، والعميل لبوابته.
  await expect(page).toHaveURL(user === FAISAL ? /\/portal\/invoices$/ : /\/dashboard$/);
}

async function authHeaders(request: APIRequestContext, user: Account): Promise<Record<string, string>> {
  const auth = await request.post('/api/auth/login', { data: user });
  expect(auth.ok()).toBeTruthy();
  return { Authorization: `Bearer ${(await auth.json()).token}` };
}

/**
 * Sara (Al Noor's account manager) issues an invoice to Faisal through the API and returns its number,
 * so every payment test pays its own fresh invoice.
 * سارة (مديرة حساب النور) تصدر فاتورة لفيصل عن طريق الـ API وترجع رقمها، حتى كل اختبار دفع يدفع فاتورته الجديدة.
 */
export async function issueInvoiceToFaisal(
  request: APIRequestContext,
  description: string,
  unitPrice = 300,
): Promise<{ id: number; number: string }> {
  const headers = await authHeaders(request, SARA);
  const contacts = await request.get('/api/contacts?q=faisal@alnoor.example', { headers });
  const faisal = (await contacts.json()).content[0];
  const due = new Date(Date.now() + 14 * 86_400_000).toISOString().slice(0, 10);
  const draft = await request.post('/api/invoices', {
    headers,
    data: { companyId: faisal.company.id, contactId: faisal.id, dueDate: due, lines: [{ description, quantity: 2, unitPrice }] },
  });
  expect(draft.status()).toBe(201);
  const { invoice } = await draft.json();
  const issued = await request.post(`/api/invoices/${invoice.id}/issue`, { headers, data: { version: invoice.version } });
  expect(issued.ok()).toBeTruthy();
  return { id: invoice.id, number: invoice.number };
}

/**
 * Sara adds a new person at Al Noor, so the invite test always starts from "no access".
 * سارة تضيف شخص جديد في النور، حتى اختبار الدعوة يبدأ دايم من "بدون دخول".
 */
export async function createAlNoorContact(request: APIRequestContext, firstName: string, email: string): Promise<number> {
  const headers = await authHeaders(request, SARA);
  const companies = await request.get('/api/companies?q=Al Noor', { headers });
  const companyId = (await companies.json()).content[0].id;
  const contact = await request.post('/api/contacts', {
    headers,
    data: { firstName, lastName: 'Al-Shehri', email, companyId },
  });
  expect(contact.status()).toBe(201);
  return (await contact.json()).id;
}

/**
 * Creates a deal straight through the API, for tests that only need one to exist (e.g. ownership checks).
 * ينشئ صفقة مباشرة عن طريق الـ API، للاختبارات اللي تحتاج صفقة موجودة فقط (مثل فحص الملكية).
 */
export async function createDealViaApi(request: APIRequestContext, user: Account, title: string): Promise<number> {
  const headers = await authHeaders(request, user);
  const companies = await request.get('/api/companies?size=1', { headers });
  const companyId = (await companies.json()).content[0].id;
  const deal = await request.post('/api/deals', {
    headers,
    data: { title, companyId, amount: 25000, stage: 'LEAD' },
  });
  expect(deal.status()).toBe(201);
  return (await deal.json()).id;
}

/**
 * Asserts the page has no horizontal scroll, the most common RTL/mobile layout bug.
 * يتأكد إن الصفحة ما فيها تمرير أفقي، وهو أشهر خلل في التصميم مع RTL والجوال.
 */
export async function expectNoHorizontalScroll(page: Page): Promise<void> {
  // Compare with screen.width, not innerWidth: a mobile browser widens the layout viewport (and innerWidth)
  // to fit overflowing content, which would hide the bug.
  // نقارن مع screen.width وليس innerWidth: متصفح الجوال يوسّع منطقة العرض (و innerWidth) لتناسب المحتوى الفائض،
  // وهذا كان بيخفي الخلل.
  const overflow = await page.evaluate(
    () => document.documentElement.scrollWidth - Math.min(window.innerWidth, window.screen.width),
  );
  expect(overflow, 'page is wider than the viewport').toBeLessThanOrEqual(1);
}

// Unique suffix so repeated runs never collide on names.
// لاحقة فريدة حتى التشغيل المتكرر ما يتعارض في الأسماء.
export const unique = () => Date.now().toString(36).toUpperCase();
