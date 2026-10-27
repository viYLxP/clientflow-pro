import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, firstValueFrom, tap } from 'rxjs';

import { ApiService } from '../api/api.service';
import { LoginResponse, User } from '../api/models';

const TOKEN_KEY = 'clientflow.token';

/**
 * Session state: the JWT and the logged-in user.
 * حالة الجلسة: رمز JWT والمستخدم المسجّل.
 *
 * Security note: the token is kept in sessionStorage (cleared when the tab closes) rather than localStorage,
 * which narrows how long a stolen token could be reused. The server also expires tokens (8h by default).
 * ملاحظة أمنية: الرمز محفوظ في sessionStorage (ينمسح لما تقفل التبويب) بدل localStorage،
 * وهذا يقلل مدة إمكانية استخدام رمز مسروق. والخادم ينهي صلاحية الرمز أيضًا (8 ساعات افتراضيًا).
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(ApiService);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(readToken());
  private readonly userSignal = signal<User | null>(null);

  readonly token = this.tokenSignal.asReadonly();
  readonly user = this.userSignal.asReadonly();
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');
  readonly isClient = computed(() => this.userSignal()?.role === 'CLIENT');
  readonly isStaff = computed(() => !!this.userSignal() && !this.isClient());
  // Where each role lands after signing in: clients in their portal, staff on the dashboard.
  // الصفحة اللي يوصل لها كل دور بعد الدخول: العميل لبوابته، والموظف للوحة.
  readonly home = computed(() => (this.isClient() ? '/portal' : '/dashboard'));

  login(username: string, password: string): Observable<LoginResponse> {
    return this.api
      .post<LoginResponse>('/auth/login', { username, password })
      .pipe(tap((response) => this.adopt(response)));
  }

  /**
   * Starts a session from a login response, e.g. right after a client accepts their invite.
   * يبدأ جلسة من رد تسجيل الدخول، مثلًا بعد ما يقبل العميل دعوته مباشرة.
   */
  adopt(response: LoginResponse): void {
    this.storeToken(response.token);
    this.userSignal.set(response.user);
  }

  /**
   * Restores the session after a page reload: if a token is saved, ask the server who it belongs to.
   * An expired or invalid token fails here and the user is simply treated as logged out.
   * يسترجع الجلسة بعد إعادة تحميل الصفحة: لو فيه رمز محفوظ، نسأل الخادم لمن هو.
   * الرمز المنتهي أو غير الصالح يفشل هنا، ونعامل المستخدم كأنه غير مسجّل.
   */
  async restore(): Promise<boolean> {
    if (this.userSignal()) {
      return true;
    }
    if (!this.tokenSignal()) {
      return false;
    }
    try {
      this.userSignal.set(await firstValueFrom(this.api.get<User>('/auth/me')));
      return true;
    } catch {
      this.clear();
      return false;
    }
  }

  logout(): void {
    // Clients go back to the public site, staff to the sign-in page.
    // العميل يرجع للموقع العام، والموظف لصفحة الدخول.
    const target = this.isClient() ? '/' : '/login';
    this.clear();
    void this.router.navigateByUrl(target);
  }

  /** Called by the interceptor on any 401 from the API. / يستدعيه الـ interceptor عند أي 401 من الـ API. */
  sessionExpired(): void {
    this.clear();
    void this.router.navigate(['/login'], { queryParams: { expired: 1 } });
  }

  private storeToken(token: string): void {
    this.tokenSignal.set(token);
    try {
      sessionStorage.setItem(TOKEN_KEY, token);
    } catch {
      // Storage blocked: the session still works until the page is reloaded.
      // التخزين محجوب: الجلسة تشتغل لين تتحدث الصفحة.
    }
  }

  private clear(): void {
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    try {
      sessionStorage.removeItem(TOKEN_KEY);
    } catch {
      // Nothing to clean up if storage is unavailable.
      // ما فيه شي ننظفه لو التخزين غير متاح.
    }
  }
}

function readToken(): string | null {
  try {
    return sessionStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}
