import { expect, test } from '@playwright/test';

import { KHALID, SARA, createDealViaApi, login, unique } from './helpers';

/**
 * The core sales path: a rep creates a deal, moves it forward, loses it (a reason is required),
 * reopens it and finally wins it from the pipeline board.
 * المسار الأساسي للمبيعات: المندوب ينشئ صفقة، يقدّمها، يخسرها (والسبب إلزامي)،
 * يعيد فتحها، وأخيرًا يكسبها من لوحة خط المبيعات.
 */
test('create a deal, lose it with a reason, reopen it and win it from the board', async ({ page }) => {
  const title = `E2E deal ${unique()}`;
  await login(page, SARA);

  await page.goto('/deals/new');
  await page.locator('#title').fill(title);
  await page.locator('#companyId').selectOption({ index: 1 });
  await page.locator('#amount').fill('48000');
  await page.getByTestId('save-deal').click();
  await expect(page).toHaveURL(/\/deals\/\d+$/);
  await expect(page.getByTestId('deal-stage')).toHaveClass(/badge--LEAD/);

  await page.getByTestId('next-stage').click();
  await expect(page.getByTestId('deal-stage')).toHaveClass(/badge--QUALIFIED/);

  // Losing without a reason is refused in the dialog; the deal stays open.
  // الخسارة بدون سبب ترفضها النافذة، والصفقة تبقى مفتوحة.
  await page.getByTestId('mark-lost').click();
  await page.getByTestId('prompt-ok').click();
  await expect(page.locator('#prompt-error [role=alert]')).toBeVisible();
  await page.getByTestId('prompt-input').fill('Chose a competitor');
  await page.getByTestId('prompt-ok').click();
  await expect(page.getByTestId('deal-stage')).toHaveClass(/badge--LOST/);
  await expect(page.getByTestId('lost-reason')).toContainText('Chose a competitor');
  // Created at LEAD, then QUALIFIED, then LOST: three history rows.
  // أُنشئت عند LEAD ثم QUALIFIED ثم LOST: ثلاث صفوف في السجل.
  await expect(page.getByTestId('history').locator('li')).toHaveCount(3);

  await page.getByTestId('reopen').click();
  await page.getByTestId('confirm-yes').click();
  await expect(page.getByTestId('deal-stage')).toHaveClass(/badge--NEGOTIATION/);

  // On the board the reopened deal sits in Negotiation; winning it removes it from the open columns.
  // في اللوحة الصفقة المعاد فتحها تكون في التفاوض؛ وكسبها يشيلها من الأعمدة المفتوحة.
  await page.goto('/pipeline');
  const card = page.getByTestId('deal-card').filter({ hasText: title });
  await expect(page.locator('section[data-stage=NEGOTIATION]').getByTestId('deal-card').filter({ hasText: title })).toBeVisible();
  await card.getByTestId('win').click();
  await page.getByTestId('confirm-yes').click();
  await expect(card).toHaveCount(0);
});

test('activities and tasks can be logged on a deal and the task completed', async ({ page }) => {
  const title = `E2E follow-up ${unique()}`;
  await login(page, SARA);
  const dealId = await createDealViaApi(page.request, SARA, `E2E task deal ${unique()}`);
  await page.goto(`/deals/${dealId}`);

  // An empty note is rejected next to the field.
  // الملاحظة الفاضية تنرفض بجانب الحقل.
  await page.getByTestId('activity-save').click();
  await expect(page.locator('#act-body-err')).not.toBeEmpty();
  await page.locator('#act-body').fill('Called the buyer, asked for a revised quote.');
  await page.getByTestId('activity-save').click();
  await expect(page.getByTestId('timeline')).toContainText('Called the buyer');

  await page.getByTestId('add-task').click();
  await page.locator('#task-title').fill(title);
  await page.getByTestId('task-save').click();
  await expect(page.getByText(title)).toBeVisible();

  // The tasks page (my open tasks by default) lists it; completing it removes it from that list.
  // صفحة المهام (مهامي المفتوحة افتراضيًا) تعرضها، وإنهاؤها يشيلها من القائمة.
  await page.goto('/tasks');
  const row = page.getByTestId('tasks-table').locator('tr').filter({ hasText: title });
  await expect(row).toBeVisible();
  await row.getByTestId('toggle-task').click();
  await expect(row).toHaveCount(0);
});

test("a rep sees a colleague's deal read-only", async ({ page }) => {
  const dealId = await createDealViaApi(page.request, SARA, `E2E Sara only ${unique()}`);
  await login(page, KHALID);
  await page.goto(`/deals/${dealId}`);
  await expect(page.getByTestId('deal-title')).toBeVisible();
  await expect(page.getByTestId('stage-actions')).toHaveCount(0);
  // Opening the edit page directly shows a notice instead of a form.
  // فتح صفحة التعديل مباشرة يعرض تنبيه بدل النموذج.
  await page.goto(`/deals/${dealId}/edit`);
  await expect(page.getByTestId('save-deal')).toHaveCount(0);
});
