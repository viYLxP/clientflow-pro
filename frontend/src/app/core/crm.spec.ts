import {
  AMOUNT_PATTERN,
  PHONE_PATTERN,
  WEBSITE_PATTERN,
  canChange,
  isClosed,
  nextStage,
  riyadhToday,
  websiteHref,
  addDays,
} from './crm';

describe('crm helpers', () => {
  it('nextStage walks the open stages and stops before closing', () => {
    expect(nextStage('LEAD')).toBe('QUALIFIED');
    expect(nextStage('PROPOSAL')).toBe('NEGOTIATION');
    // Winning or losing is an explicit choice, never "next". / الفوز أو الخسارة قرار صريح، مو "التالي".
    expect(nextStage('NEGOTIATION')).toBeNull();
    expect(nextStage('WON')).toBeNull();
    expect(isClosed('LOST')).toBe(true);
    expect(isClosed('LEAD')).toBe(false);
  });

  it('canChange mirrors the server ownership rule', () => {
    const owner = { id: 2, fullName: 'Sara' };
    expect(canChange({ id: 2, role: 'SALES' }, [owner])).toBe(true);
    expect(canChange({ id: 3, role: 'SALES' }, [owner])).toBe(false);
    expect(canChange({ id: 3, role: 'ADMIN' }, [owner])).toBe(true);
    // A task can be changed by its assignee or its creator. / المهمة يعدّلها المكلّف أو المنشئ.
    expect(canChange({ id: 3, role: 'SALES' }, [owner, { id: 3, fullName: 'K' }])).toBe(true);
    expect(canChange(null, [owner])).toBe(false);
  });

  it('form patterns match the server rules', () => {
    expect(AMOUNT_PATTERN.test('185000')).toBe(true);
    expect(AMOUNT_PATTERN.test('10.5')).toBe(true);
    expect(AMOUNT_PATTERN.test('10.555')).toBe(false);
    expect(AMOUNT_PATTERN.test('-1')).toBe(false);
    expect(AMOUNT_PATTERN.test('1234567890123')).toBe(false);
    expect(PHONE_PATTERN.test('')).toBe(true);
    expect(PHONE_PATTERN.test('+966 11 400 1122')).toBe(true);
    expect(PHONE_PATTERN.test('12345')).toBe(false);
    expect(WEBSITE_PATTERN.test('alnoor.example')).toBe(true);
    expect(WEBSITE_PATTERN.test('not a site')).toBe(false);
  });

  it('websiteHref only ever produces http(s) links', () => {
    expect(websiteHref('alnoor.example')).toBe('https://alnoor.example/');
    expect(websiteHref('http://x.example/path')).toBe('http://x.example/path');
    expect(websiteHref('javascript:alert(1)')).toBeNull();
    expect(websiteHref(null)).toBeNull();
  });

  it('riyadhToday uses the Riyadh calendar day, not the browser or UTC day', () => {
    // 22:30 UTC is already the next day in Riyadh (UTC+3). / 22:30 UTC هو اليوم التالي في الرياض.
    expect(riyadhToday(new Date('2026-03-14T22:30:00Z'))).toBe('2026-03-15');
    expect(riyadhToday(new Date('2026-03-14T20:59:00Z'))).toBe('2026-03-14');
  });

  it('addDays crosses month and year ends', () => {
    expect(addDays('2026-10-27', 14)).toBe('2026-11-10');
    expect(addDays('2026-12-25', 10)).toBe('2027-01-04');
    expect(addDays('2028-02-28', 1)).toBe('2028-02-29');
  });
});
