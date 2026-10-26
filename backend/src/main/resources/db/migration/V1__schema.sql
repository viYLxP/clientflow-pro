-- ClientFlow Pro schema. Flyway runs this once on an empty database; later changes go in V2__..., V3__...
-- مخطط ClientFlow Pro. Flyway يشغّله مرة وحدة على قاعدة بيانات فاضية؛ والتعديلات اللاحقة تكون في V2__ و V3__...
-- CHECK constraints repeat the most important business rules, so bad data is refused even if a bug slips past the API.
-- قيود CHECK تكرر أهم قواعد العمل، فالبيانات الخاطئة تُرفض حتى لو فلت خطأ من الـ API.

CREATE TABLE app_user (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(50)  NOT NULL UNIQUE,
    full_name     VARCHAR(120) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    role          VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'SALES')),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Companies (accounts). The name is unique ignoring case, so "Al Noor" and "al noor" cannot both exist.
-- الشركات (الحسابات). الاسم فريد بدون حساسية لحالة الأحرف، فما يصير "Al Noor" و"al noor" معًا.
CREATE TABLE company (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(150) NOT NULL,
    industry   VARCHAR(80),
    city       VARCHAR(80),
    phone      VARCHAR(30),
    email      VARCHAR(150),
    website    VARCHAR(200),
    notes      VARCHAR(2000),
    owner_id   BIGINT       NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE UNIQUE INDEX ux_company_name ON company (lower(name));
CREATE INDEX idx_company_owner ON company (owner_id);

-- People. A contact may exist without a company (e.g. a freelancer).
-- الأشخاص. جهة الاتصال ممكن تكون بدون شركة (مثل مستقل).
CREATE TABLE contact (
    id         BIGSERIAL PRIMARY KEY,
    first_name VARCHAR(80)  NOT NULL,
    last_name  VARCHAR(80)  NOT NULL,
    email      VARCHAR(150),
    phone      VARCHAR(30),
    job_title  VARCHAR(100),
    notes      VARCHAR(2000),
    company_id BIGINT REFERENCES company (id),
    owner_id   BIGINT       NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version    BIGINT       NOT NULL DEFAULT 0
);
CREATE INDEX idx_contact_company ON contact (company_id);

-- Sales opportunities. A closed deal (WON/LOST) always has closed_at; a LOST deal always has a reason.
-- فرص البيع. الصفقة المغلقة (WON/LOST) دائمًا لها closed_at، والخاسرة دائمًا لها سبب.
CREATE TABLE deal (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(150)   NOT NULL,
    company_id          BIGINT         NOT NULL REFERENCES company (id),
    contact_id          BIGINT REFERENCES contact (id) ON DELETE SET NULL,
    owner_id            BIGINT         NOT NULL REFERENCES app_user (id),
    stage               VARCHAR(20)    NOT NULL
        CHECK (stage IN ('LEAD', 'QUALIFIED', 'PROPOSAL', 'NEGOTIATION', 'WON', 'LOST')),
    amount              NUMERIC(14, 2) NOT NULL CHECK (amount >= 0),
    expected_close_date DATE,
    lost_reason         VARCHAR(255),
    closed_at           TIMESTAMPTZ,
    created_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ    NOT NULL DEFAULT now(),
    version             BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT deal_closed_at_matches_stage CHECK ((stage IN ('WON', 'LOST')) = (closed_at IS NOT NULL)),
    CONSTRAINT deal_lost_needs_reason CHECK (stage <> 'LOST' OR lost_reason IS NOT NULL)
);
CREATE INDEX idx_deal_stage ON deal (stage);
CREATE INDEX idx_deal_owner ON deal (owner_id);
CREATE INDEX idx_deal_company ON deal (company_id);
CREATE INDEX idx_deal_closed ON deal (closed_at);

-- Every stage change, for the deal timeline and for win-rate reports.
-- كل تغيير في مرحلة الصفقة، لسجل الصفقة وتقارير نسبة الفوز.
CREATE TABLE deal_stage_change (
    id         BIGSERIAL PRIMARY KEY,
    deal_id    BIGINT      NOT NULL REFERENCES deal (id) ON DELETE CASCADE,
    from_stage VARCHAR(20),
    to_stage   VARCHAR(20) NOT NULL,
    changed_by VARCHAR(50) NOT NULL,
    changed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_stage_change_deal ON deal_stage_change (deal_id, changed_at);

-- Follow-up tasks. A DONE task always has completed_at.
-- مهام المتابعة. المهمة المنجزة دائمًا لها completed_at.
CREATE TABLE task (
    id           BIGSERIAL PRIMARY KEY,
    title        VARCHAR(200) NOT NULL,
    description  VARCHAR(2000),
    due_date     DATE         NOT NULL,
    priority     VARCHAR(10)  NOT NULL CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    status       VARCHAR(10)  NOT NULL CHECK (status IN ('OPEN', 'DONE')),
    assignee_id  BIGINT       NOT NULL REFERENCES app_user (id),
    created_by   BIGINT       NOT NULL REFERENCES app_user (id),
    company_id   BIGINT REFERENCES company (id) ON DELETE CASCADE,
    deal_id      BIGINT REFERENCES deal (id) ON DELETE CASCADE,
    contact_id   BIGINT REFERENCES contact (id) ON DELETE SET NULL,
    completed_at TIMESTAMPTZ,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT now(),
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT task_done_has_date CHECK ((status = 'DONE') = (completed_at IS NOT NULL))
);
CREATE INDEX idx_task_assignee_status ON task (assignee_id, status, due_date);
CREATE INDEX idx_task_deal ON task (deal_id);
CREATE INDEX idx_task_company ON task (company_id);

-- Interaction log (notes, calls, meetings, emails). Always tied to a company; optionally to a deal and a contact.
-- سجل التواصل (ملاحظات، مكالمات، اجتماعات، إيميلات). دائمًا مرتبط بشركة، واختياريًا بصفقة وجهة اتصال.
CREATE TABLE activity (
    id          BIGSERIAL PRIMARY KEY,
    type        VARCHAR(10)   NOT NULL CHECK (type IN ('NOTE', 'CALL', 'MEETING', 'EMAIL')),
    body        VARCHAR(2000) NOT NULL,
    occurred_at TIMESTAMPTZ   NOT NULL,
    author_id   BIGINT        NOT NULL REFERENCES app_user (id),
    company_id  BIGINT        NOT NULL REFERENCES company (id) ON DELETE CASCADE,
    deal_id     BIGINT REFERENCES deal (id) ON DELETE CASCADE,
    contact_id  BIGINT REFERENCES contact (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX idx_activity_company ON activity (company_id, occurred_at DESC);
CREATE INDEX idx_activity_deal ON activity (deal_id, occurred_at DESC);
