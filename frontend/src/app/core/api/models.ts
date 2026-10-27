// TypeScript mirrors of the backend DTOs. Keeping them in one file makes API changes easy to spot.
// نسخ TypeScript من الـ DTOs في الخادم. وجودها في ملف واحد يسهّل ملاحظة أي تغيير في الـ API.
// Money values arrive as JSON numbers (BigDecimal on the server, 2 decimals, SAR).
// المبالغ تصل كأرقام JSON (BigDecimal في الخادم بمنزلتين، بالريال).

export type Role = 'ADMIN' | 'SALES' | 'CLIENT';
export type InvoiceStatus = 'DRAFT' | 'ISSUED' | 'PAID' | 'VOID';
export type PaymentStatus = 'UNPAID' | 'PAID' | 'REFUNDED';
export type PaymentMethod = 'CARD' | 'APPLE_PAY' | 'TABBY' | 'TAMARA' | 'BANK_TRANSFER';
export type PaymentProvider = 'SANDBOX' | 'MOYASAR' | 'STRIPE' | 'TABBY' | 'TAMARA' | 'MANUAL';
export type PaymentAttemptStatus = 'PENDING' | 'PAID' | 'FAILED' | 'REFUNDED';
export type InquiryStatus = 'NEW' | 'CONVERTED' | 'DISMISSED';
export type PortalAccessStatus = 'NONE' | 'INVITED' | 'ACTIVE' | 'DISABLED';
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
  billing: {
    outstanding: number;
    overdueCount: number;
    collectedMonth: number;
    newInquiries: number;
  };
}

// ----- Invoices / الفواتير -----

export interface InvoiceLine {
  position: number;
  description: string;
  quantity: number;
  unitPrice: number;
  lineTotal: number;
}

/** One row of an invoice list. / صف واحد في قائمة الفواتير. */
export interface InvoiceSummary {
  id: number;
  number: string;
  company: CompanyRef;
  contact: ContactRef | null;
  status: InvoiceStatus;
  overdue: boolean;
  issueDate: string | null;
  dueDate: string;
  total: number;
  paidAt: string | null;
}

export interface Invoice extends InvoiceSummary {
  deal: DealRef | null;
  subtotal: number;
  vat: number;
  notes: string | null;
  lines: InvoiceLine[];
  issuedAt: string | null;
  voidedAt: string | null;
  voidReason: string | null;
  createdAt: string;
  version: number;
}

/** The staff invoice page. / صفحة الفاتورة للموظف. */
export interface InvoiceDetail {
  invoice: Invoice;
  createdBy: UserRef;
  canManage: boolean;
  payment: PaymentSummary;
  payments: PaymentView[];
}

/** The client's view of an invoice. / عرض العميل للفاتورة. */
export interface PortalInvoice {
  invoice: Invoice;
  payment: PaymentSummary;
}

export interface InvoiceLineRequest {
  description: string;
  quantity: number;
  unitPrice: number;
}

export interface InvoiceRequest {
  companyId: number;
  contactId: number | null;
  dealId: number | null;
  dueDate: string;
  notes: string | null;
  lines: InvoiceLineRequest[];
  version?: number;
}

// ----- Payments / المدفوعات -----

/** One payment attempt as staff see it. / محاولة دفع وحدة مثل ما يشوفها الموظف. */
export interface PaymentView {
  id: string;
  method: PaymentMethod;
  provider: PaymentProvider;
  status: PaymentAttemptStatus;
  amount: number;
  currency: string;
  cardBrand: string | null;
  cardLast4: string | null;
  failureReason: string | null;
  recordedBy: string;
  createdAt: string;
  paidAt: string | null;
  refundedAt: string | null;
}

export interface Instalment {
  number: number;
  amount: number;
  dueDate: string;
}

/** The payment part of an invoice. / جزء الدفع في الفاتورة. */
export interface PaymentSummary {
  status: PaymentStatus;
  payable: boolean;
  amountDue: number | null;
  // Online methods offered right now (instalments only within their limits).
  // الطرق المتاحة الحين أونلاين (التقسيط بس داخل حدوده).
  methods: PaymentMethod[];
  instalments: Instalment[];
  method: PaymentMethod | null;
  cardBrand: string | null;
  cardLast4: string | null;
  paidAt: string | null;
}

export interface CheckoutResponse {
  paymentId: string;
  checkoutUrl: string;
}

/** What the /pay/:id page shows. / اللي تعرضه صفحة ‎/pay/:id. */
export interface CheckoutPage {
  id: string;
  method: PaymentMethod;
  status: PaymentAttemptStatus;
  provider: PaymentProvider;
  amount: number;
  currency: string;
  invoiceNumber: string;
  itemCount: number;
  instalments: Instalment[];
  failureReason: string | null;
  cardBrand: string | null;
  cardLast4: string | null;
  paidAt: string | null;
  checkoutUrl: string | null;
}

// ----- Client portal / بوابة العملاء -----

export interface PortalAccess {
  status: PortalAccessStatus;
  login: string | null;
  inviteExpiresAt: string | null;
}

export interface InviteResponse {
  access: PortalAccess;
  inviteUrl: string;
}

export interface InviteInfo {
  fullName: string;
  login: string;
  companyName: string;
}

export interface PortalMe {
  fullName: string;
  email: string;
  phone: string | null;
  jobTitle: string | null;
  company: CompanyRef;
}

// ----- Website inquiries / استفسارات الموقع -----

export interface Inquiry {
  id: number;
  name: string;
  companyName: string | null;
  email: string;
  phone: string | null;
  message: string;
  status: InquiryStatus;
  deal: DealRef | null;
  handledBy: UserRef | null;
  createdAt: string;
  handledAt: string | null;
}

export interface ConvertResponse {
  inquiry: Inquiry;
  dealId: number;
  companyId: number;
  contact: ContactRef;
}
