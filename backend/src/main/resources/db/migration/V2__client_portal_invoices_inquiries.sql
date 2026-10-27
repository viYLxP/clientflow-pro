-- V2: client portal accounts, invoices with online payment, and inquiries from the public contact form.
-- V2: حسابات بوابة العملاء، والفواتير مع الدفع أونلاين، والاستفسارات من نموذج التواصل العام.

-- Client users log in with their email, so the username grows to 150 like the contact email.
-- مستخدمين العملاء يدخلون بإيميلهم، فاسم المستخدم يكبر لـ 150 مثل إيميل جهة الاتصال.
ALTER TABLE app_user ALTER COLUMN username TYPE VARCHAR(150);
ALTER TABLE app_user DROP CONSTRAINT app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check CHECK (role IN ('ADMIN', 'SALES', 'CLIENT'));
-- A client account belongs to exactly one contact; deleting the contact removes the login with it.
-- حساب العميل تابع لجهة اتصال وحدة بالضبط؛ وحذف جهة الاتصال يحذف الدخول معها.
ALTER TABLE app_user ADD COLUMN contact_id BIGINT UNIQUE REFERENCES contact (id) ON DELETE CASCADE;
-- Only the SHA-256 of the one-time invite token is stored, so a database leak cannot be used to activate accounts.
-- نخزّن SHA-256 لرمز الدعوة فقط، فتسريب قاعدة البيانات ما ينفع لتفعيل الحسابات.
ALTER TABLE app_user ADD COLUMN invite_token_hash VARCHAR(64) UNIQUE;
ALTER TABLE app_user ADD COLUMN invite_expires_at TIMESTAMPTZ;
ALTER TABLE app_user ADD CONSTRAINT app_user_client_has_contact CHECK ((role = 'CLIENT') = (contact_id IS NOT NULL));

-- Invoices. Numbers come from a sequence (INV-000001) so two people issuing at once never get the same number.
-- الفواتير. الأرقام من sequence (INV-000001) حتى شخصين يصدرون بنفس اللحظة ما ياخذون نفس الرقم.
CREATE SEQUENCE invoice_number_seq;
CREATE TABLE invoice (
    id          BIGSERIAL PRIMARY KEY,
    number      VARCHAR(20)    NOT NULL UNIQUE,
    company_id  BIGINT         NOT NULL REFERENCES company (id),
    contact_id  BIGINT REFERENCES contact (id) ON DELETE SET NULL,
    deal_id     BIGINT REFERENCES deal (id) ON DELETE SET NULL,
    status      VARCHAR(10)    NOT NULL CHECK (status IN ('DRAFT', 'ISSUED', 'PAID', 'VOID')),
    issue_date  DATE,
    due_date    DATE           NOT NULL,
    subtotal    NUMERIC(14, 2) NOT NULL CHECK (subtotal >= 0),
    vat         NUMERIC(14, 2) NOT NULL CHECK (vat >= 0),
    total       NUMERIC(14, 2) NOT NULL CHECK (total >= 0),
    notes       VARCHAR(1000),
    created_by  BIGINT         NOT NULL REFERENCES app_user (id),
    issued_at   TIMESTAMPTZ,
    paid_at     TIMESTAMPTZ,
    voided_at   TIMESTAMPTZ,
    void_reason VARCHAR(255),
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version     BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT invoice_total_adds_up CHECK (total = subtotal + vat),
    CONSTRAINT invoice_issued_has_date CHECK (status = 'DRAFT' OR (issued_at IS NOT NULL AND issue_date IS NOT NULL)),
    CONSTRAINT invoice_paid_has_date CHECK (status = 'VOID' OR ((status = 'PAID') = (paid_at IS NOT NULL))),
    CONSTRAINT invoice_void_has_reason CHECK ((status = 'VOID') = (voided_at IS NOT NULL AND void_reason IS NOT NULL))
);
CREATE INDEX idx_invoice_company ON invoice (company_id, status);
CREATE INDEX idx_invoice_status_due ON invoice (status, due_date);
CREATE INDEX idx_invoice_deal ON invoice (deal_id);

CREATE TABLE invoice_line (
    id          BIGSERIAL PRIMARY KEY,
    invoice_id  BIGINT         NOT NULL REFERENCES invoice (id) ON DELETE CASCADE,
    position    INT            NOT NULL,
    description VARCHAR(300)   NOT NULL,
    quantity    INT            NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(14, 2) NOT NULL CHECK (unit_price >= 0),
    line_total  NUMERIC(14, 2) NOT NULL CHECK (line_total >= 0),
    CONSTRAINT invoice_line_total CHECK (line_total = quantity * unit_price)
);
CREATE INDEX idx_invoice_line_invoice ON invoice_line (invoice_id, position);

-- Payment attempts for an invoice: online through a gateway, or a bank transfer recorded by staff.
-- محاولات الدفع للفاتورة: أونلاين عن طريق بوابة، أو تحويل بنكي يسجّله الموظف.
CREATE TABLE payment (
    id             UUID PRIMARY KEY,
    invoice_id     BIGINT         NOT NULL REFERENCES invoice (id),
    method         VARCHAR(20)    NOT NULL
        CHECK (method IN ('CARD', 'APPLE_PAY', 'TABBY', 'TAMARA', 'BANK_TRANSFER')),
    provider       VARCHAR(20)    NOT NULL
        CHECK (provider IN ('SANDBOX', 'MOYASAR', 'STRIPE', 'TABBY', 'TAMARA', 'MANUAL')),
    provider_ref   VARCHAR(200),
    checkout_url   VARCHAR(1000),
    amount         NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    currency       VARCHAR(3)     NOT NULL DEFAULT 'SAR',
    status         VARCHAR(20)    NOT NULL CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'REFUNDED')),
    failure_reason VARCHAR(255),
    card_brand     VARCHAR(20),
    card_last4     VARCHAR(4),
    recorded_by    VARCHAR(150),
    created_at     TIMESTAMPTZ    NOT NULL,
    paid_at        TIMESTAMPTZ,
    refunded_at    TIMESTAMPTZ,
    CONSTRAINT ck_payment_paid CHECK (status NOT IN ('PAID', 'REFUNDED') OR paid_at IS NOT NULL)
);
CREATE INDEX idx_payment_invoice ON payment (invoice_id);
CREATE INDEX idx_payment_paid_at ON payment (paid_at) WHERE status = 'PAID';
-- At most one successful payment per invoice, even if two gateway callbacks race.
-- دفعة ناجحة وحدة بالكثير لكل فاتورة، حتى لو تسابق ردّين من البوابة.
CREATE UNIQUE INDEX ux_payment_one_paid ON payment (invoice_id) WHERE status = 'PAID';

-- Messages from the public "contact sales" form. Converting one creates a company, a contact and a LEAD deal.
-- رسائل نموذج "تواصل مع المبيعات" العام. تحويل الرسالة ينشئ شركة وجهة اتصال وصفقة LEAD.
CREATE TABLE inquiry (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(120)  NOT NULL,
    company_name VARCHAR(150),
    email        VARCHAR(150)  NOT NULL,
    phone        VARCHAR(30),
    message      VARCHAR(2000) NOT NULL,
    status       VARCHAR(10)   NOT NULL CHECK (status IN ('NEW', 'CONVERTED', 'DISMISSED')),
    deal_id      BIGINT REFERENCES deal (id) ON DELETE SET NULL,
    handled_by   BIGINT REFERENCES app_user (id),
    created_at   TIMESTAMPTZ   NOT NULL DEFAULT now(),
    handled_at   TIMESTAMPTZ,
    CONSTRAINT inquiry_handled CHECK ((status = 'NEW') = (handled_at IS NULL))
);
CREATE INDEX idx_inquiry_status ON inquiry (status, created_at DESC);
