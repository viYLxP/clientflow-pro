import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';

import { I18nService } from '../i18n/i18n.service';
import { errorMessage, toApiError } from './api-error';

describe('api-error', () => {
  let i18n: I18nService;

  beforeEach(() => {
    i18n = TestBed.inject(I18nService);
    i18n.setLang('en');
  });

  it('reads code, fields and details from a ProblemDetail body', () => {
    const err = toApiError(
      new HttpErrorResponse({
        status: 400,
        error: { code: 'VALIDATION_FAILED', fields: { phone: 'Pattern' } },
      }),
    );
    expect(err.status).toBe(400);
    expect(err.code).toBe('VALIDATION_FAILED');
    expect(err.fields).toEqual({ phone: 'Pattern' });
  });

  it('maps status 0 to NETWORK and non-HTTP errors to UNKNOWN', () => {
    expect(toApiError(new HttpErrorResponse({ status: 0 })).code).toBe('NETWORK');
    expect(toApiError(new Error('boom')).code).toBe('UNKNOWN');
    // A proxy HTML error page has no JSON code.
    // صفحة خطأ HTML من البروكسي ما فيها code بصيغة JSON.
    expect(toApiError(new HttpErrorResponse({ status: 502, error: '<html>' })).code).toBe(
      'UNKNOWN',
    );
  });

  it('translates the deal stage inside the message', () => {
    const err = toApiError(
      new HttpErrorResponse({ status: 409, error: { code: 'INVALID_STAGE', stage: 'WON' } }),
    );
    expect(errorMessage(i18n, err)).toContain(i18n.t('stage.WON'));
    i18n.setLang('ar');
    expect(errorMessage(i18n, err)).toContain('رابحة');
  });

  it('gives a specific message for ownership errors', () => {
    const err = toApiError(new HttpErrorResponse({ status: 403, error: { code: 'NOT_OWNER' } }));
    expect(errorMessage(i18n, err)).toBe(i18n.t('errors.NOT_OWNER'));
  });

  it('falls back to a generic message for unknown codes', () => {
    const err = toApiError(
      new HttpErrorResponse({ status: 418, error: { code: 'SOMETHING_NEW' } }),
    );
    expect(errorMessage(i18n, err)).toBe(i18n.t('errors.UNKNOWN'));
  });

  it('translates an invoice or inquiry status inside the message', () => {
    const paid = toApiError(
      new HttpErrorResponse({ status: 409, error: { code: 'INVALID_STATUS', status: 'PAID' } }),
    );
    expect(errorMessage(i18n, paid)).toBe('This is not allowed while it is Paid.');
    const converted = toApiError(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'INVALID_STATUS', status: 'CONVERTED' },
      }),
    );
    expect(errorMessage(i18n, converted)).toBe('This is not allowed while it is Converted.');
  });
});
