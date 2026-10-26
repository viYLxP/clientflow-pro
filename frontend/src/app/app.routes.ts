import { Routes } from '@angular/router';

import { adminGuard, authGuard, guestGuard } from './core/auth/guards';
import { ShellComponent } from './layout/shell.component';

/**
 * Routes. Feature pages are lazy-loaded (loadComponent), so the login page downloads only what it needs.
 * المسارات. صفحات الميزات تُحمّل عند الحاجة (loadComponent)، فصفحة الدخول تحمّل فقط اللي تحتاجه.
 *
 * Forms share one component for "new" and ":id/edit"; the presence of :id tells them which mode to use.
 * النماذج تستخدم مكوّن واحد لـ "جديد" و ":id/edit"؛ ووجود :id يحدد الوضع.
 */
export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () => import('./features/login/login.page').then((m) => m.LoginPage),
  },
  {
    path: '',
    component: ShellComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
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
        path: 'users',
        canActivate: [adminGuard],
        loadComponent: () => import('./features/users/users.page').then((m) => m.UsersPage),
      },
      {
        path: '**',
        loadComponent: () => import('./features/not-found.page').then((m) => m.NotFoundPage),
      },
    ],
  },
];
