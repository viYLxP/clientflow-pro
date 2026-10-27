import { Routes } from '@angular/router';

import {
  adminGuard,
  clientGuard,
  guestGuard,
  sessionRestore,
  staffGuard,
} from './core/auth/guards';
import { ShellComponent } from './layout/shell.component';
import { SiteLayoutComponent } from './layout/site-layout.component';

const notFound = () => import('./features/not-found.page').then((m) => m.NotFoundPage);

/**
 * Routes. Feature pages are lazy-loaded (loadComponent), so the login page downloads only what it needs.
 * المسارات. صفحات الميزات تُحمّل عند الحاجة (loadComponent)، فصفحة الدخول تحمّل فقط اللي تحتاجه.
 *
 * Forms share one component for "new" and ":id/edit"; the presence of :id tells them which mode to use.
 * النماذج تستخدم مكوّن واحد لـ "جديد" و ":id/edit"؛ ووجود :id يحدد الوضع.
 *
 * Two layouts: the public site (home page, invite, payment, client portal) and the CRM shell (staff).
 * Both sit on the empty path; the router tries the site first and falls through to the shell, and an unknown
 * address ends on the site's "not found" page.
 * تخطيطين: الموقع العام (الرئيسية، الدعوة، الدفع، بوابة العميل) ونظام الموظفين.
 * الاثنين على المسار الفاضي؛ والراوتر يجرب الموقع أول ثم ينتقل للنظام، والعنوان المجهول ينتهي بصفحة "غير موجود".
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: SiteLayoutComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/public/home.page').then((m) => m.HomePage),
      },
      {
        path: 'activate',
        canActivate: [guestGuard],
        loadComponent: () => import('./features/public/activate.page').then((m) => m.ActivatePage),
      },
      {
        path: 'pay/:id',
        canActivate: [sessionRestore],
        loadComponent: () => import('./features/pay/pay.page').then((m) => m.PayPage),
      },
      {
        path: 'portal',
        canActivate: [clientGuard],
        children: [
          { path: '', pathMatch: 'full', redirectTo: 'invoices' },
          {
            path: 'invoices',
            loadComponent: () =>
              import('./features/portal/portal-invoices.page').then((m) => m.PortalInvoicesPage),
          },
          {
            path: 'invoices/:number',
            loadComponent: () =>
              import('./features/portal/portal-invoice.page').then((m) => m.PortalInvoicePage),
          },
          {
            path: 'profile',
            loadComponent: () =>
              import('./features/portal/profile.page').then((m) => m.ProfilePage),
          },
        ],
      },
    ],
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [staffGuard],
    children: [
      {
        path: 'dashboard',
        loadComponent: () =>
          import('./features/dashboard/dashboard.page').then((m) => m.DashboardPage),
      },
      {
        path: 'pipeline',
        loadComponent: () => import('./features/deals/pipeline.page').then((m) => m.PipelinePage),
      },
      {
        path: 'deals',
        loadComponent: () => import('./features/deals/deal-list.page').then((m) => m.DealListPage),
      },
      {
        path: 'deals/new',
        loadComponent: () => import('./features/deals/deal-form.page').then((m) => m.DealFormPage),
      },
      {
        path: 'deals/:id',
        loadComponent: () =>
          import('./features/deals/deal-detail.page').then((m) => m.DealDetailPage),
      },
      {
        path: 'deals/:id/edit',
        loadComponent: () => import('./features/deals/deal-form.page').then((m) => m.DealFormPage),
      },
      {
        path: 'companies',
        loadComponent: () =>
          import('./features/companies/company-list.page').then((m) => m.CompanyListPage),
      },
      {
        path: 'companies/new',
        loadComponent: () =>
          import('./features/companies/company-form.page').then((m) => m.CompanyFormPage),
      },
      {
        path: 'companies/:id',
        loadComponent: () =>
          import('./features/companies/company-detail.page').then((m) => m.CompanyDetailPage),
      },
      {
        path: 'companies/:id/edit',
        loadComponent: () =>
          import('./features/companies/company-form.page').then((m) => m.CompanyFormPage),
      },
      {
        path: 'contacts',
        loadComponent: () =>
          import('./features/contacts/contacts.page').then((m) => m.ContactsPage),
      },
      {
        path: 'tasks',
        loadComponent: () => import('./features/tasks/tasks.page').then((m) => m.TasksPage),
      },
      {
        path: 'invoices',
        loadComponent: () =>
          import('./features/invoices/invoice-list.page').then((m) => m.InvoiceListPage),
      },
      {
        path: 'invoices/new',
        loadComponent: () =>
          import('./features/invoices/invoice-form.page').then((m) => m.InvoiceFormPage),
      },
      {
        path: 'invoices/:id',
        loadComponent: () =>
          import('./features/invoices/invoice-detail.page').then((m) => m.InvoiceDetailPage),
      },
      {
        path: 'invoices/:id/edit',
        loadComponent: () =>
          import('./features/invoices/invoice-form.page').then((m) => m.InvoiceFormPage),
      },
      {
        path: 'inquiries',
        loadComponent: () =>
          import('./features/inquiries/inquiries.page').then((m) => m.InquiriesPage),
      },
      {
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
    ],
  },
  {
    path: '',
    component: SiteLayoutComponent,
    children: [{ path: '**', loadComponent: notFound }],
  },
];
