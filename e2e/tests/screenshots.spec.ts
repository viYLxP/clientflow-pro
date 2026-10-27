import { test } from '@playwright/test';

import { FAISAL, SARA, login } from './helpers';

// Captures README screenshots. Run with SCREENSHOTS=1; skipped otherwise so normal runs stay fast.
// يلتقط صور README. شغّله مع SCREENSHOTS=1؛ وإلا يتم تخطيه حتى يبقى التشغيل العادي سريع.
test.skip(!process.env['SCREENSHOTS'], 'set SCREENSHOTS=1 to capture README images');

for (const lang of ['ar', 'en'] as const) {
  test(`screenshots (${lang})`, async ({ page }, info) => {
    const dir = `../docs/screenshots/${info.project.name}`;
    await page.addInitScript((l) => localStorage.setItem('clientflow.lang', l), lang);
    await login(page, SARA);
    for (const [name, path] of [
      ['dashboard', '/dashboard'],
      ['pipeline', '/pipeline'],
      ['deals', '/deals'],
      ['companies', '/companies'],
      ['invoices', '/invoices'],
      ['invoice', '/invoices/1'],
      ['inquiries', '/inquiries'],
    ] as const) {
      await page.goto(path);
      await page.locator('.loading').waitFor({ state: 'detached' }).catch(() => undefined);
      await page.waitForLoadState('networkidle');
      await page.screenshot({ path: `${dir}/${name}-${lang}.png`, fullPage: info.project.name === 'desktop' });
    }
  });
}

for (const lang of ['ar', 'en'] as const) {
  test(`website and portal screenshots (${lang})`, async ({ page }, info) => {
    const dir = `../docs/screenshots/${info.project.name}`;
    const full = info.project.name === 'desktop';
    await page.addInitScript((l) => localStorage.setItem('clientflow.lang', l), lang);
    await page.goto('/');
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: `${dir}/home-${lang}.png`, fullPage: full });
    await login(page, FAISAL);
    await page.waitForLoadState('networkidle');
    await page.screenshot({ path: `${dir}/portal-${lang}.png`, fullPage: full });
    await page.goto('/portal/invoices/INV-000002');
    await page.waitForLoadState('networkidle');
    await page.getByTestId('method-TABBY').check({ force: true });
    await page.screenshot({ path: `${dir}/portal-invoice-${lang}.png`, fullPage: full });
  });
}
