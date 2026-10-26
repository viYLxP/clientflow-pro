import { ActivityType, DealStage, OPEN_STAGES, Role, UserRef } from './api/models';

/**
 * Small pure helpers shared by several pages (unit-tested in crm.spec.ts).
 * دوال صغيرة صافية تستخدمها عدة صفحات (مختبرة في crm.spec.ts).
 */

/**
 * The next open stage, or null for the last open stage and for closed deals (closing is an explicit choice).
 * المرحلة المفتوحة التالية، أو null لآخر مرحلة مفتوحة وللصفقات المغلقة (الإغلاق قرار صريح).
 */
export function nextStage(stage: DealStage): DealStage | null {
  const index = OPEN_STAGES.indexOf(stage);
  return index >= 0 && index < OPEN_STAGES.length - 1 ? OPEN_STAGES[index + 1] : null;
}

export function isClosed(stage: DealStage): boolean {
  return stage === 'WON' || stage === 'LOST';
}

/**
 * Mirrors the server's ownership rule, only to hide buttons that would fail; the server still decides.
 * نسخة من قاعدة الملكية في الخادم، فقط لإخفاء الأزرار اللي بتفشل؛ والقرار النهائي للخادم.
 */
export function canChange(
  user: { id: number; role: Role } | null,
  owners: (UserRef | null | undefined)[],
): boolean {
  if (!user) return false;
  return user.role === 'ADMIN' || owners.some((owner) => owner?.id === user.id);
}

// Amount as typed in a form: up to 12 whole digits and 2 decimals (NUMERIC(14,2) on the server).
// المبلغ كما يُكتب في النموذج: حتى 12 خانة صحيحة ومنزلتين (NUMERIC(14,2) في الخادم).
export const AMOUNT_PATTERN = /^\d{1,12}(\.\d{1,2})?$/;
// Same phone rule as the server (CompanyDtos.PHONE_REGEX), empty allowed.
// نفس قاعدة الجوال في الخادم، والفاضي مسموح.
export const PHONE_PATTERN = /^$|^\+?[0-9 ()-]{7,20}$/;
export const WEBSITE_PATTERN = /^$|^(https?:\/\/)?[A-Za-z0-9.-]+\.[A-Za-z]{2,}(\/\S*)?$/;

/**
 * A safe link for a stored website: adds https:// when missing, and refuses anything that is not http(s),
 * so a stored "javascript:" value can never become a clickable link.
 * رابط آمن للموقع المحفوظ: يضيف https:// لو ناقص، ويرفض أي شيء غير http(s)،
 * حتى قيمة "javascript:" المحفوظة ما تصير رابط قابل للضغط.
 */
export function websiteHref(website: string | null): string | null {
  if (!website) return null;
  const url = /^https?:\/\//i.test(website) ? website : `https://${website}`;
  try {
    const parsed = new URL(url);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? parsed.href : null;
  } catch {
    return null;
  }
}

/**
 * Today's date in Riyadh as YYYY-MM-DD, matching how the server decides "today" and "overdue".
 * تاريخ اليوم بتوقيت الرياض بصيغة YYYY-MM-DD، بنفس طريقة الخادم في تحديد "اليوم" و"المتأخر".
 */
export function riyadhToday(now: Date = new Date()): string {
  // en-CA formats dates as YYYY-MM-DD. / en-CA ينسّق التاريخ بصيغة YYYY-MM-DD.
  return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Riyadh' }).format(now);
}

// Icon per activity type, shared by the timeline and the dashboard. / أيقونة لكل نوع نشاط، مشتركة بين السجل واللوحة.
export const ACTIVITY_ICONS: Record<ActivityType, string> = {
  NOTE: 'note',
  CALL: 'phone',
  MEETING: 'calendar',
  EMAIL: 'mail',
};
