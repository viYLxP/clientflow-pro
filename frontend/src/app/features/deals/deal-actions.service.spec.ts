import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { Deal } from '../../core/api/models';
import { I18nService } from '../../core/i18n/i18n.service';
import { ConfirmService } from '../../core/ui/confirm.service';
import { PromptService } from '../../core/ui/prompt.service';
import { ToastService } from '../../core/ui/toast.service';
import { DealActionsService } from './deal-actions.service';

const deal = {
  id: 7,
  title: 'POS rollout',
  stage: 'PROPOSAL',
  amount: 1000,
  version: 3,
} as Deal;

describe('DealActionsService', () => {
  let actions: DealActionsService;
  let http: HttpTestingController;
  let prompt: PromptService;
  let confirm: ConfirmService;
  let toast: ToastService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    TestBed.inject(I18nService).setLang('en');
    actions = TestBed.inject(DealActionsService);
    http = TestBed.inject(HttpTestingController);
    prompt = TestBed.inject(PromptService);
    confirm = TestBed.inject(ConfirmService);
    toast = TestBed.inject(ToastService);
  });

  afterEach(() => http.verify());

  it('cancelling the lost-reason prompt sends nothing', async () => {
    const result = actions.move(deal, 'LOST');
    expect(prompt.current()?.title).toBe('Why was this deal lost?');
    prompt.answer(null);
    expect(await result).toBeNull();
    http.expectNone('/api/deals/7/stage');
  });

  it('losing sends the reason and the version the user saw', async () => {
    const result = actions.move(deal, 'LOST');
    prompt.answer('Price');
    // Let the awaited prompt resolve before the request is made. / ننتظر انتهاء النافذة قبل الطلب.
    await Promise.resolve();
    const req = http.expectOne('/api/deals/7/stage');
    expect(req.request.body).toEqual({ stage: 'LOST', lostReason: 'Price', version: 3 });
    req.flush({ ...deal, stage: 'LOST', version: 4 });
    expect((await result)?.stage).toBe('LOST');
  });

  it('a normal move needs no confirmation; a stale version shows the server error', async () => {
    const result = actions.move(deal, 'NEGOTIATION');
    expect(confirm.current()).toBeNull();
    http
      .expectOne('/api/deals/7/stage')
      .flush({ code: 'CONCURRENT_UPDATE' }, { status: 409, statusText: 'Conflict' });
    expect(await result).toBeNull();
    expect(toast.toasts().at(-1)?.text).toBe(
      'Someone else changed this record. Reload and try again.',
    );
  });

  it('winning asks for confirmation first', async () => {
    const result = actions.move(deal, 'WON');
    expect(confirm.current()?.message).toContain('POS rollout');
    confirm.answer(false);
    expect(await result).toBeNull();
  });
});
