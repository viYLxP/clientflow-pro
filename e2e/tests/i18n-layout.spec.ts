import { expect, test } from '@playwright/test';

import { FAISAL, expectNoHorizontalScroll, login } from './helpers';

const PAGES = [
  '/dashboard',
  '/pipeline',
  '/deals',
  '/deals/new',
  '/companies',
  '/companies/new',
  '/contacts',
  '/tasks',
  '/invoices',
  '/invoices/new',
  '/invoices/1',
  '/inquiries',
  '/users',
];
// The public site and the client portal (INV-000001 is a seeded invoice to Faisal). / الموقع العام وبوابة العميل.
const CLIENT_PAGES = ['/portal/invoices', '/portal/invoices/INV-000001', '/portal/profile'];

test('switching language keeps typed input and flips direction', async ({ page }) => {
  await login(page);
  await page.goto('/deals/new');
  await page.locator('#title').fill('ERP rollout for 3 branches');
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');

  await page.getByTestId('lang-switch').click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'ltr');
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  await expect(page.locator('#title')).toHaveValue('ERP rollout for 3 branches');

  // The choice survives a reload.
  // الاختيار يبقى بعد إعادة التحميل.
  await page.reload();
  await expect(page.locator('html')).toHaveAttribute('lang', 'en');
});

for (const lang of ['ar', 'en'] as const) {
  test(`every page renders without horizontal scroll or console errors (${lang})`, async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    page.on('pageerror', (err) => errors.push(err.message));

    await page.addInitScript((l) => localStorage.setItem('clientflow.lang', l), lang);
    await login(page);
    for (const path of PAGES) {
      await page.goto(path);
      await expect(page.locator('h1').first()).toBeVisible();
      await expect(page.locator('.loading')).toHaveCount(0);
      await expectNoHorizontalScroll(page);
    }
    expect(errors).toEqual([]);
  });

  test(`the website and client portal render without horizontal scroll or console errors (${lang})`, async ({ page }) => {
    const errors: string[] = [];
    page.on('console', (msg) => {
      if (msg.type() === 'error') errors.push(msg.text());
    });
    page.on('pageerror', (err) => errors.push(err.message));

    await page.addInitScript((l) => localStorage.setItem('clientflow.lang', l), lang);
    for (const path of ['/', '/login', '/activate']) {
      await page.goto(path);
      await expect(page.locator('h1').first()).toBeVisible();
      await expectNoHorizontalScroll(page);
    }
    await login(page, FAISAL);
    for (const path of CLIENT_PAGES) {
      await page.goto(path);
      await expect(page.locator('h1').first()).toBeAttached();
      await expect(page.locator('.loading')).toHaveCount(0);
      await expectNoHorizontalScroll(page);
    }
    expect(errors).toEqual([]);
  });
}

test('the menu button only exists on small screens and opens the drawer', async ({ page }, info) => {
  await login(page);
  const menu = page.locator('.topbar__menu');
  if (info.project.name === 'desktop') {
    // Desktop shows the sidebar permanently, so the drawer buttons must stay hidden.
    // الكمبيوتر يعرض القائمة الجانبية دائمًا، فأزرار الدرج لازم تبقى مخفية.
    await expect(menu).toBeHidden();
    await expect(page.locator('.sidebar__close')).toBeHidden();
    await expect(page.locator('.sidebar')).toBeVisible();
  } else {
    await expect(page.locator('.sidebar')).toBeHidden();
    await menu.click();
    await expect(page.locator('.sidebar')).toBeVisible();
    // Navigating closes the drawer again.
    // التنقل يقفل الدرج مرة ثانية.
    await page.locator('.sidebar a[href="/pipeline"]').click();
    await expect(page).toHaveURL(/\/pipeline/);
    await expect(page.locator('.sidebar')).toBeHidden();
  }
});
