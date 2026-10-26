import { APIRequestContext, Page, expect } from '@playwright/test';

// Demo accounts created by the seeder (passwords come from .env; these are the documented defaults).
// الحسابات التجريبية اللي ينشئها الـ seeder (كلمات المرور من .env، وهذي القيم الافتراضية الموثقة).
export const ADMIN = { username: 'admin', password: process.env['ADMIN_PASSWORD'] ?? 'Admin@12345' };
const SALES_PASSWORD = process.env['SALES_PASSWORD'] ?? 'Sales@12345';
export const SARA = { username: 'sara', password: SALES_PASSWORD };
export const KHALID = { username: 'khalid', password: SALES_PASSWORD };
type Account = typeof ADMIN;

export async function login(page: Page, user: Account = ADMIN): Promise<void> {
  await page.goto('/login');
  await page.locator('#username').fill(user.username);
  await page.locator('#password').fill(user.password);
  await page.locator('form button[type=submit]').click();
  await expect(page).toHaveURL(/\/dashboard$/);
}

/**
 * Creates a deal straight through the API, for tests that only need one to exist (e.g. ownership checks).
 * ينشئ صفقة مباشرة عن طريق الـ API، للاختبارات اللي تحتاج صفقة موجودة فقط (مثل فحص الملكية).
 */
export async function createDealViaApi(request: APIRequestContext, user: Account, title: string): Promise<number> {
  const auth = await request.post('/api/auth/login', { data: user });
  expect(auth.ok()).toBeTruthy();
  const headers = { Authorization: `Bearer ${(await auth.json()).token}` };
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
