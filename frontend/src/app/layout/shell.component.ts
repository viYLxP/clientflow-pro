import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';

interface NavItem {
  path: string;
  label: string;
  icon: string;
  adminOnly?: boolean;
}

/**
 * The logged-in layout: sidebar navigation, top bar (language + user) and the page outlet.
 * تخطيط الصفحات بعد الدخول: قائمة جانبية، شريط علوي (اللغة والمستخدم)، ومكان عرض الصفحة.
 *
 * The sidebar uses CSS logical properties, so it sits on the right in Arabic and on the left in English
 * without separate styles for each direction.
 * القائمة الجانبية تستخدم خصائص CSS المنطقية، فتكون يمين في العربي ويسار في الإنجليزي
 * بدون تنسيقات منفصلة لكل اتجاه.
 */
@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './shell.component.html',
})
export class ShellComponent {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
  private readonly router = inject(Router);

  // Mobile drawer state; the sidebar is always visible on wide screens.
  // حالة القائمة المنزلقة في الجوال؛ وفي الشاشات العريضة القائمة ظاهرة دائمًا.
  protected readonly menuOpen = signal(false);

  protected readonly sections: { title: string; items: NavItem[] }[] = [
    {
      title: 'nav.sectionSales',
      items: [
        { path: '/dashboard', label: 'nav.dashboard', icon: 'dashboard' },
        { path: '/pipeline', label: 'nav.pipeline', icon: 'kanban' },
        { path: '/deals', label: 'nav.deals', icon: 'briefcase' },
        { path: '/tasks', label: 'nav.tasks', icon: 'checkSquare' },
      ],
    },
    {
      title: 'nav.sectionCustomers',
      items: [
        { path: '/companies', label: 'nav.companies', icon: 'building' },
        { path: '/contacts', label: 'nav.contacts', icon: 'users' },
      ],
    },
    {
      title: 'nav.sectionAdmin',
      items: [{ path: '/users', label: 'nav.users', icon: 'shield', adminOnly: true }],
    },
  ];

  // Hide admin-only links (and empty sections) for sales reps.
  // نخفي روابط المدير (والأقسام الفاضية) عن المندوبين.
  protected readonly visibleSections = computed(() =>
    this.sections
      .map((section) => ({
        ...section,
        items: section.items.filter((i) => !i.adminOnly || this.auth.isAdmin()),
      }))
      .filter((section) => section.items.length > 0),
  );

  constructor() {
    // Close the mobile drawer after every navigation.
    // نقفل القائمة في الجوال بعد كل تنقل.
    this.router.events
      .pipe(
        filter((e) => e instanceof NavigationEnd),
        // Unsubscribe automatically when the shell is destroyed (e.g. after logout).
        // نلغي الاشتراك تلقائيًا لما ينحذف الـ shell (مثلًا بعد تسجيل الخروج).
        takeUntilDestroyed(),
      )
      .subscribe(() => this.menuOpen.set(false));
  }

  // First letter of the user's name for the avatar circle.
  // أول حرف من اسم المستخدم لدائرة الصورة الرمزية.
  protected readonly initial = computed(() =>
    (this.auth.user()?.fullName ?? '?').charAt(0).toUpperCase(),
  );
}
