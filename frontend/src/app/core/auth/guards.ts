import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthService } from './auth.service';

// Guards only hide pages in the UI; the real protection is the server's role check on every endpoint.
// الحراس يخفون الصفحات في الواجهة فقط؛ والحماية الحقيقية هي فحص الدور في الخادم على كل رابط.
// inject() only works before the first await, so every guard injects first.
// inject() يشتغل بس قبل أول await، فكل حارس يسوي inject أول.

/**
 * The CRM itself: staff only. A visitor goes to the sign-in page, a signed-in client to their portal.
 * نظام إدارة العملاء نفسه: للموظفين بس. الزائر يروح لصفحة الدخول، والعميل المسجّل لبوابته.
 */
export const staffGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!(await auth.restore())) return router.createUrlTree(['/login']);
  return auth.isStaff() ? true : router.parseUrl(auth.home());
};

/**
 * Admin-only pages.
 * صفحات المدير فقط.
 */
export const adminGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.restore();
  return auth.isAdmin() ? true : router.createUrlTree(['/dashboard']);
};

/**
 * The client portal (/portal): clients only; staff go to their dashboard.
 * بوابة العملاء (‎/portal): للعملاء بس؛ والموظف يروح للوحته.
 */
export const clientGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (!(await auth.restore())) return router.createUrlTree(['/login']);
  return auth.isClient() ? true : router.parseUrl(auth.home());
};

/**
 * Keeps signed-in users away from the sign-in and invite pages, and off the public home page.
 * يمنع المستخدم المسجّل من صفحة الدخول وصفحة الدعوة، ومن الصفحة الرئيسية العامة.
 */
export const guestGuard: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return (await auth.restore()) ? router.parseUrl(auth.home()) : true;
};

/**
 * Public pages that still show who is signed in (the payment page): restore a saved session first.
 * الصفحات العامة اللي تعرض مين داخل (صفحة الدفع): تسترجع الجلسة المحفوظة أول.
 */
export const sessionRestore: CanActivateFn = async () => {
  await inject(AuthService).restore();
  return true;
};
