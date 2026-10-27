import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';

import { errorMessage, toApiError } from '../../core/api/api-error';
import { ApiService } from '../../core/api/api.service';
import { Contact, InviteResponse, PortalAccess } from '../../core/api/models';
import { FormatService } from '../../core/i18n/format.service';
import { I18nService } from '../../core/i18n/i18n.service';
import { TranslatePipe } from '../../core/i18n/translate.pipe';
import { ConfirmService } from '../../core/ui/confirm.service';
import { IconComponent } from '../../core/ui/icon.component';
import { ToastService } from '../../core/ui/toast.service';

/**
 * Client-portal access for one contact: see the status, create an invite link to send them, or turn access off.
 * The system sends no emails, so the link is shown once here and the rep sends it (email or WhatsApp).
 * دخول بوابة العملاء لجهة اتصال وحدة: شوف الحالة، أو سوّ رابط دعوة ترسله له، أو اقفل الدخول.
 * النظام ما يرسل إيميلات، فالرابط ينعرض هنا مرة وحدة والمندوب يرسله (بالإيميل أو الواتساب).
 */
@Component({
  selector: 'app-portal-access-dialog',
  imports: [TranslatePipe, IconComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <dialog #dialog class="dialog" aria-labelledby="portal-title" (close)="onClosed()">
      @if (contact(); as c) {
        <h2 id="portal-title">{{ 'portalAccess.title' | t: { name: c.fullName } }}</h2>
        @if (errorText(); as message) {
          <div class="alert alert--danger" role="alert" data-testid="portal-error">
            {{ message }}
          </div>
        }
        @if (access(); as a) {
          <p>
            <span [class]="'badge badge--portal-' + a.status" data-testid="portal-status">{{
              'portalAccess.status.' + a.status | t
            }}</span>
          </p>
          <p class="muted">
            {{
              'portalAccess.text.' + a.status
                | t: { date: fmt.dateTime(a.inviteExpiresAt), login: a.login ?? '' }
            }}
          </p>
        } @else if (loading()) {
          <div class="loading" role="status">
            <span class="spinner"></span>{{ 'app.loading' | t }}
          </div>
        }

        @if (link(); as url) {
          <div class="invite-link" data-testid="invite-link">
            <strong>{{ 'portalAccess.linkTitle' | t }}</strong>
            <p class="hint">
              {{ 'portalAccess.linkHint' | t: { date: fmt.dateTime(access()?.inviteExpiresAt) } }}
            </p>
            <div class="input-group">
              <input
                #linkInput
                [value]="url"
                readonly
                dir="ltr"
                [attr.aria-label]="'portalAccess.linkTitle' | t"
                (focus)="linkInput.select()"
                data-testid="invite-url"
              />
              <button type="button" class="btn btn--small" (click)="copy(url)">
                <app-icon name="copy" /> {{ 'portalAccess.copy' | t }}
              </button>
            </div>
          </div>
        }

        <div class="dialog__actions">
          @if (access()?.status === 'ACTIVE' || access()?.status === 'INVITED') {
            <button
              type="button"
              class="btn btn--danger"
              (click)="disable(c)"
              [disabled]="busy()"
              data-testid="portal-disable"
            >
              {{ 'portalAccess.disable' | t }}
            </button>
          }
          <button type="button" class="btn" (click)="close()">{{ 'common.close' | t }}</button>
          @if (access() && access()?.status !== 'ACTIVE') {
            <button
              type="button"
              class="btn btn--primary"
              (click)="invite(c)"
              [disabled]="busy()"
              data-testid="portal-invite"
            >
              <app-icon name="key" />
              {{
                (access()?.status === 'NONE' ? 'portalAccess.invite' : 'portalAccess.resend') | t
              }}
            </button>
          }
        </div>
      }
    </dialog>
  `,
})
export class PortalAccessDialogComponent {
  private readonly api = inject(ApiService);
  private readonly i18n = inject(I18nService);
  private readonly toast = inject(ToastService);
  private readonly confirm = inject(ConfirmService);
  protected readonly fmt = inject(FormatService);
  private readonly dialog = viewChild.required<ElementRef<HTMLDialogElement>>('dialog');

  protected readonly contact = signal<Contact | null>(null);
  protected readonly access = signal<PortalAccess | null>(null);
  protected readonly link = signal<string | null>(null);
  protected readonly loading = signal(false);
  protected readonly busy = signal(false);
  private readonly error = signal<unknown>(null);
  protected readonly errorText = computed(() =>
    this.error() ? errorMessage(this.i18n, toApiError(this.error())) : null,
  );

  open(contact: Contact): void {
    this.contact.set(contact);
    this.access.set(null);
    this.link.set(null);
    this.error.set(null);
    this.loading.set(true);
    this.dialog().nativeElement.showModal();
    this.api.get<PortalAccess>(`/contacts/${contact.id}/portal-access`).subscribe({
      next: (access) => {
        this.loading.set(false);
        this.access.set(access);
      },
      error: (err) => {
        this.loading.set(false);
        this.error.set(err);
      },
    });
  }

  close(): void {
    this.dialog().nativeElement.close();
  }

  protected onClosed(): void {
    // The link is not kept once the dialog closes. / الرابط ما ينحفظ بعد ما تنقفل النافذة.
    this.contact.set(null);
    this.link.set(null);
  }

  protected invite(contact: Contact): void {
    this.busy.set(true);
    this.error.set(null);
    this.api.post<InviteResponse>(`/contacts/${contact.id}/portal-access/invite`).subscribe({
      next: (response) => {
        this.busy.set(false);
        this.access.set(response.access);
        this.link.set(response.inviteUrl);
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err);
      },
    });
  }

  protected async disable(contact: Contact): Promise<void> {
    // The confirm dialog opens on top of this one. / نافذة التأكيد تنفتح فوق هذي.
    const ok = await this.confirm.ask({
      title: this.i18n.t('portalAccess.disable'),
      message: this.i18n.t('portalAccess.disableConfirm', { name: contact.fullName }),
      confirmLabel: this.i18n.t('portalAccess.disable'),
      danger: true,
    });
    if (!ok) return;
    this.busy.set(true);
    this.error.set(null);
    this.api.post<PortalAccess>(`/contacts/${contact.id}/portal-access/disable`).subscribe({
      next: (access) => {
        this.busy.set(false);
        this.access.set(access);
        this.link.set(null);
        this.toast.success(this.i18n.t('portalAccess.disabled'));
      },
      error: (err) => {
        this.busy.set(false);
        this.error.set(err);
      },
    });
  }

  protected async copy(url: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(url);
      this.toast.success(this.i18n.t('portalAccess.copied'));
    } catch {
      // Clipboard blocked (e.g. plain http): the field is selectable instead. / الحافظة محجوبة: الحقل قابل للتحديد بدالها.
      this.toast.error(this.i18n.t('portalAccess.copyFailed'));
    }
  }
}
