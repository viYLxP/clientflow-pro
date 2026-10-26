import { HttpErrorResponse } from '@angular/common/http';

import { I18nService } from '../i18n/i18n.service';

/**
 * Normalised error read from the server's ProblemDetail body.
 * خطأ موحّد مقروء من جسم ProblemDetail القادم من الخادم.
 */
export interface ApiError {
  status: number;
  code: string;
  // Field name → validation rule (e.g. { sku: 'Pattern' }).
  // اسم الحقل ← قاعدة التحقق (مثل { sku: 'Pattern' }).
  fields: Record<string, string>;
  // Extra details such as { deals, contacts } for IN_USE or { stage } for INVALID_STAGE.
  // تفاصيل إضافية مثل { deals, contacts } لخطأ IN_USE أو { stage } لخطأ INVALID_STAGE.
  details: Record<string, unknown>;
}

/**
 * Converts any HttpClient error into an ApiError.
 * يحوّل أي خطأ من HttpClient إلى ApiError.
 */
export function toApiError(error: unknown): ApiError {
  if (error instanceof HttpErrorResponse) {
    // status 0 means the request never reached the server (offline, CORS, server down).
    // الحالة 0 تعني إن الطلب ما وصل للخادم أصلًا (بدون إنترنت، CORS، أو الخادم طافي).
    if (error.status === 0) {
      return { status: 0, code: 'NETWORK', fields: {}, details: {} };
    }
    const body = (error.error ?? {}) as Record<string, unknown>;
    return {
      status: error.status,
      code: typeof body['code'] === 'string' ? body['code'] : 'UNKNOWN',
      fields: (body['fields'] as Record<string, string>) ?? {},
      details: body,
    };
  }
  return { status: -1, code: 'UNKNOWN', fields: {}, details: {} };
}

/**
 * Builds the user-facing message for an error in the current language.
 * يبني رسالة الخطأ للمستخدم باللغة الحالية.
 */
export function errorMessage(i18n: I18nService, error: ApiError): string {
  const params: Record<string, string | number> = {};
  for (const [key, value] of Object.entries(error.details)) {
    if (typeof value === 'string' || typeof value === 'number') {
      params[key] = value;
    }
  }
  // Translate a deal stage inside the message too ("…while the deal is Won").
  // نترجم مرحلة الصفقة داخل الرسالة أيضًا ("…والصفقة في مرحلة رابحة").
  if (typeof params['stage'] === 'string') {
    params['stage'] = i18n.t(`stage.${params['stage']}`);
  }
  const known = i18n.t(`errors.${error.code}`, params);
  return known === `errors.${error.code}` ? i18n.t('errors.UNKNOWN') : known;
}
