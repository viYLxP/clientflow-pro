import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthService } from '../core/auth/auth.service';
import { I18nService } from '../core/i18n/i18n.service';
import { TranslatePipe } from '../core/i18n/translate.pipe';
import { IconComponent } from '../core/ui/icon.component';

/**
 * The public layout: the website home page, invite activation, the payment page and the client portal.
 * The header links change with who is signed in: a visitor sees "Sign in", a client their portal, staff the CRM.
 * التخطيط العام: الصفحة الرئيسية، وتفعيل الدعوة، وصفحة الدفع، وبوابة العميل.
 * روابط الهيدر تتغير حسب مين داخل: الزائر يشوف "تسجيل الدخول"، والعميل بوابته، والموظف النظام.
 */
@Component({
  selector: 'app-site-layout',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a class="skip-link" href="#main">{{ 'app.skipToContent' | t }}</a>
    <header class="site-header">
      <a class="site-header__brand" routerLink="/">
        <span class="logo" aria-hidden="true">CF</span>
        <strong>{{ 'app.name' | t }}</strong>
      </a>
      <nav class="site-header__nav" [attr.aria-label]="'site.nav' | t">
        @if (auth.isClient()) {
          <a
            class="site-link"
            routerLink="/portal/invoices"
            routerLinkActive="site-link--active"
            ariaCurrentWhenActive="page"
            data-testid="nav-portal"
            ><app-icon name="invoice" /> <span>{{ 'site.myInvoices' | t }}</span></a
          >
          <a
            class="site-link"
            routerLink="/portal/profile"
            routerLinkActive="site-link--active"
            ariaCurrentWhenActive="page"
            ><app-icon name="user" /> <span>{{ 'site.profile' | t }}</span></a
          >
        } @else if (auth.isStaff()) {
          <a class="site-link" routerLink="/dashboard"
            ><app-icon name="dashboard" /> <span>{{ 'site.openCrm' | t }}</span></a
          >
        }
      </nav>
      <button
        type="button"
        class="btn btn--small lang-switch"
        (click)="i18n.toggle()"
        [attr.aria-label]="'app.switchToLabel' | t"
        [attr.lang]="i18n.lang() === 'ar' ? 'en' : 'ar'"
        data-testid="lang-switch"
      >
        <app-icon name="globe" /> {{ 'app.switchTo' | t }}
      </button>
      @if (auth.user()) {
        <button
          type="button"
          class="icon-btn"
          (click)="auth.logout()"
          data-testid="logout"
          [attr.aria-label]="'nav.logout' | t"
          [title]="'nav.logout' | t"
        >
          <app-icon name="logout" [flip]="true" />
        </button>
      } @else {
        <a class="btn btn--small btn--primary" routerLink="/login" data-testid="sign-in">{{
          'site.signIn' | t
        }}</a>
      }
    </header>
    <main id="main" class="site-main" tabindex="-1">
      <router-outlet />
    </main>
    <footer class="site-footer">
      <p>{{ 'site.footer' | t }}</p>
    </footer>
  `,
})
export class SiteLayoutComponent {
  protected readonly auth = inject(AuthService);
  protected readonly i18n = inject(I18nService);
}
