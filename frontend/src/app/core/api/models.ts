// TypeScript mirrors of the backend DTOs. Keeping them in one file makes API changes easy to spot.
// نسخ TypeScript من الـ DTOs في الخادم. وجودها في ملف واحد يسهّل ملاحظة أي تغيير في الـ API.
// Money values arrive as JSON numbers (BigDecimal on the server, 2 decimals, SAR).
// المبالغ تصل كأرقام JSON (BigDecimal في الخادم بمنزلتين، بالريال).

export type Role = 'ADMIN' | 'SALES';
export type DealStage = 'LEAD' | 'QUALIFIED' | 'PROPOSAL' | 'NEGOTIATION' | 'WON' | 'LOST';
export type TaskPriority = 'LOW' | 'MEDIUM' | 'HIGH';
export type TaskStatus = 'OPEN' | 'DONE';
export type ActivityType = 'NOTE' | 'CALL' | 'MEETING' | 'EMAIL';

// Pipeline order: the four open stages, then the two closing outcomes.
// ترتيب خط المبيعات: المراحل الأربع المفتوحة، ثم نتيجتا الإغلاق.
export const OPEN_STAGES: DealStage[] = ['LEAD', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION'];
export const ALL_STAGES: DealStage[] = [...OPEN_STAGES, 'WON', 'LOST'];

export interface Page<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface User {
  id: number;
  username: string;
  fullName: string;
  role: Role;
  active: boolean;
  createdAt: string;
}

export interface LoginResponse {
  token: string;
  expiresAt: string;
  user: User;
}

/** Short reference to another record. / مرجع مختصر لسجل ثاني. */
export interface UserRef {
  id: number;
  fullName: string;
}
export interface CompanyRef {
  id: number;
  name: string;
}
export interface ContactRef {
  id: number;
  fullName: string;
}
export interface DealRef {
  id: number;
  title: string;
}

export interface Company {
  id: number;
  name: string;
  industry: string | null;
  city: string | null;
  phone: string | null;
  email: string | null;
  website: string | null;
  notes: string | null;
  owner: UserRef;
  createdAt: string;
  version: number;
}

export interface CompanyDetail {
  company: Company;
  contactCount: number;
  openDealCount: number;
  openDealAmount: number;
  wonAmount: number;
}

export interface Contact {
  id: number;
  firstName: string;
  lastName: string;
  fullName: string;
  email: string | null;
  phone: string | null;
  jobTitle: string | null;
  notes: string | null;
  company: CompanyRef | null;
  owner: UserRef;
  createdAt: string;
  version: number;
}

export interface Deal {
  id: number;
  title: string;
  company: CompanyRef;
  contact: ContactRef | null;
  owner: UserRef;
  stage: DealStage;
  probability: number;
  amount: number;
  weightedAmount: number;
  expectedCloseDate: string | null;
  lostReason: string | null;
  closedAt: string | null;
  createdAt: string;
  updatedAt: string;
  version: number;
}

export interface BoardColumn {
  stage: DealStage;
  probability: number;
  count: number;
  amount: number;
  deals: Deal[];
}

export interface StageChange {
  id: number;
  fromStage: DealStage | null;
  toStage: DealStage;
  changedBy: string;
  changedAt: string;
}

export interface Task {
  id: number;
  title: string;
  description: string | null;
  dueDate: string;
  priority: TaskPriority;
  status: TaskStatus;
  overdue: boolean;
  assignee: UserRef;
  createdBy: UserRef;
  company: CompanyRef | null;
  deal: DealRef | null;
  contact: ContactRef | null;
  completedAt: string | null;
  createdAt: string;
  version: number;
}

export interface Activity {
  id: number;
  type: ActivityType;
  body: string;
  occurredAt: string;
  author: UserRef;
  company: CompanyRef;
  deal: DealRef | null;
  contact: ContactRef | null;
  createdAt: string;
}

export interface Dashboard {
  scopeOwnerId: number | null;
  pipeline: {
    stage: DealStage;
    probability: number;
    count: number;
    amount: number;
    weightedAmount: number;
  }[];
  openCount: number;
  openAmount: number;
  weightedForecast: number;
  wonThisMonthCount: number;
  wonThisMonthAmount: number;
  // null when no deal closed in the last 90 days. / null لو ما انغلقت صفقة آخر 90 يوم.
  winRate: number | null;
  myOverdueCount: number;
  myDueTodayCount: number;
  myTasks: Task[];
  topReps: { userId: number; fullName: string; wonCount: number; wonAmount: number }[];
  closingSoon: Deal[];
  recentActivities: Activity[];
}
