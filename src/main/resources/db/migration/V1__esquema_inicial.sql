-- Biblioteca Digital 360 — esquema inicial.
-- Dinheiro sempre em centavos (integer/bigint). Datas com fuso (timestamptz).

-- ===== Segurança e usuários =====
CREATE TABLE roles (
    id          bigserial PRIMARY KEY,
    code        varchar(40)  NOT NULL UNIQUE,
    description varchar(200) NOT NULL
);

CREATE TABLE permissions (
    id          bigserial PRIMARY KEY,
    code        varchar(60)  NOT NULL UNIQUE,
    description varchar(200) NOT NULL
);

CREATE TABLE role_permissions (
    role_id       bigint NOT NULL REFERENCES roles(id),
    permission_id bigint NOT NULL REFERENCES permissions(id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE users (
    id            bigserial PRIMARY KEY,
    full_name     varchar(150) NOT NULL,
    email         varchar(150) NOT NULL UNIQUE,
    password_hash varchar(100),                      -- preenchido na Fase 3 (autenticação)
    cpf           varchar(11)  UNIQUE,               -- LGPD: só quando necessário
    registration  varchar(30)  UNIQUE,               -- matrícula institucional
    phone         varchar(20),
    address       varchar(250),
    active        boolean      NOT NULL DEFAULT true,
    deleted_at    timestamptz,                       -- exclusão lógica
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE user_roles (
    user_id bigint NOT NULL REFERENCES users(id),
    role_id bigint NOT NULL REFERENCES roles(id),
    PRIMARY KEY (user_id, role_id)
);

-- ===== Acervo =====
CREATE TABLE authors (
    id   bigserial PRIMARY KEY,
    name varchar(150) NOT NULL
);

CREATE TABLE publishers (
    id   bigserial PRIMARY KEY,
    name varchar(150) NOT NULL UNIQUE
);

CREATE TABLE categories (
    id   bigserial PRIMARY KEY,
    name varchar(100) NOT NULL UNIQUE
);

CREATE TABLE books (                                  -- a obra (não o exemplar físico)
    id             bigserial PRIMARY KEY,
    title          varchar(250) NOT NULL,
    isbn           varchar(20) UNIQUE,
    edition        varchar(40),
    publication_year smallint CHECK (publication_year BETWEEN 1000 AND 2200),
    language       varchar(30),
    description    text,
    cover_url      varchar(500),
    publisher_id   bigint REFERENCES publishers(id),
    deleted_at     timestamptz,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE book_authors (
    book_id   bigint NOT NULL REFERENCES books(id),
    author_id bigint NOT NULL REFERENCES authors(id),
    PRIMARY KEY (book_id, author_id)
);

CREATE TABLE book_categories (
    book_id     bigint NOT NULL REFERENCES books(id),
    category_id bigint NOT NULL REFERENCES categories(id),
    PRIMARY KEY (book_id, category_id)
);

CREATE TABLE book_copies (                            -- o exemplar físico
    id            bigserial PRIMARY KEY,
    book_id       bigint      NOT NULL REFERENCES books(id),
    barcode       varchar(40) NOT NULL UNIQUE,
    kind          varchar(12) NOT NULL DEFAULT 'CIRCULANTE'
                  CHECK (kind IN ('CONSULTA', 'CIRCULANTE')),   -- CONSULTA nunca sai da biblioteca
    status        varchar(12) NOT NULL DEFAULT 'DISPONIVEL'
                  CHECK (status IN ('DISPONIVEL', 'EMPRESTADO', 'RESERVADO', 'PERDIDO', 'BAIXADO')),
    condition     varchar(12) NOT NULL DEFAULT 'BOM'
                  CHECK (condition IN ('NOVO', 'BOM', 'REGULAR', 'RUIM')),
    location      varchar(100),
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, kind),                                -- alvo da FK composta em loans
    CHECK (kind <> 'CONSULTA' OR status <> 'EMPRESTADO')
);
CREATE INDEX idx_book_copies_book ON book_copies(book_id);

-- Obrigatoriedade "toda obra tem >= 1 exemplar CONSULTA e >= 2 exemplares" é validada
-- na camada de aplicação (cadastro da obra, Fase 4): o banco não consegue expressar "pelo menos um".

-- ===== Empréstimos =====
CREATE TABLE loans (
    id           bigserial PRIMARY KEY,
    user_id      bigint      NOT NULL REFERENCES users(id),
    copy_id      bigint      NOT NULL,
    copy_kind    varchar(12) NOT NULL DEFAULT 'CIRCULANTE' CHECK (copy_kind = 'CIRCULANTE'),
    performed_by bigint      NOT NULL REFERENCES users(id),     -- responsável pela operação
    loaned_at    timestamptz NOT NULL DEFAULT now(),
    due_at       timestamptz NOT NULL,
    returned_at  timestamptz,
    created_at   timestamptz NOT NULL DEFAULT now(),
    -- FK composta: só exemplar CIRCULANTE pode ser emprestado (regra garantida pelo banco)
    FOREIGN KEY (copy_id, copy_kind) REFERENCES book_copies(id, kind),
    CHECK (due_at > loaned_at)
);
-- Um exemplar não pode ter dois empréstimos ativos ao mesmo tempo.
CREATE UNIQUE INDEX uq_loans_one_active_per_copy ON loans(copy_id) WHERE returned_at IS NULL;
CREATE INDEX idx_loans_user ON loans(user_id);

CREATE TABLE loan_renewals (
    id         bigserial PRIMARY KEY,
    loan_id    bigint      NOT NULL REFERENCES loans(id),
    old_due_at timestamptz NOT NULL,
    new_due_at timestamptz NOT NULL,
    renewed_by bigint      NOT NULL REFERENCES users(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    CHECK (new_due_at > old_due_at)
);

-- ===== Reservas =====
CREATE TABLE reservations (
    id         bigserial PRIMARY KEY,
    book_id    bigint      NOT NULL REFERENCES books(id),
    user_id    bigint      NOT NULL REFERENCES users(id),
    status     varchar(12) NOT NULL DEFAULT 'NA_FILA'
               CHECK (status IN ('NA_FILA', 'DISPONIVEL', 'RETIRADA', 'CANCELADA', 'EXPIRADA')),
    queued_at  timestamptz NOT NULL DEFAULT now(),       -- ordem da fila
    expires_at timestamptz,                              -- prazo de retirada
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_reservations_queue ON reservations(book_id, queued_at) WHERE status = 'NA_FILA';
CREATE UNIQUE INDEX uq_reservation_one_open_per_user_book
    ON reservations(book_id, user_id) WHERE status IN ('NA_FILA', 'DISPONIVEL');

-- ===== Multas e pagamentos =====
CREATE TABLE fine_rates (                                -- configurável pela administração
    role_id        bigint PRIMARY KEY REFERENCES roles(id),
    cents_per_day  integer NOT NULL CHECK (cents_per_day >= 0)
);

CREATE TABLE fines (
    id           bigserial PRIMARY KEY,
    loan_id      bigint      NOT NULL UNIQUE REFERENCES loans(id),   -- 1 multa por empréstimo: sem cobrança duplicada
    amount_cents integer     NOT NULL CHECK (amount_cents > 0),
    days_late    integer     NOT NULL CHECK (days_late > 0),
    status       varchar(10) NOT NULL DEFAULT 'ABERTA'
                 CHECK (status IN ('ABERTA', 'PAGA', 'ISENTA')),
    waiver_reason varchar(300),
    waived_by    bigint REFERENCES users(id),
    created_at   timestamptz NOT NULL DEFAULT now(),
    CHECK (status <> 'ISENTA' OR (waiver_reason IS NOT NULL AND waived_by IS NOT NULL))
);

CREATE TABLE payments (
    id           bigserial PRIMARY KEY,
    fine_id      bigint      NOT NULL UNIQUE REFERENCES fines(id),   -- um pagamento por multa
    amount_cents integer     NOT NULL CHECK (amount_cents > 0),
    method       varchar(20) NOT NULL,
    received_by  bigint      NOT NULL REFERENCES users(id),
    paid_at      timestamptz NOT NULL DEFAULT now()
);

-- ===== Notificações e auditoria =====
CREATE TABLE notifications (
    id         bigserial PRIMARY KEY,
    user_id    bigint       NOT NULL REFERENCES users(id),
    type       varchar(30)  NOT NULL,
    message    varchar(500) NOT NULL,
    channel    varchar(20)  NOT NULL DEFAULT 'EMAIL',
    sent_at    timestamptz,
    created_at timestamptz  NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
    id          bigserial PRIMARY KEY,
    user_id     bigint REFERENCES users(id),
    action      varchar(60)  NOT NULL,
    entity      varchar(60)  NOT NULL,
    entity_id   bigint,
    details     text,
    ip_address  varchar(45),
    created_at  timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_entity ON audit_logs(entity, entity_id);

-- ===== Dados iniciais (valores padrão, editáveis pela administração) =====
INSERT INTO roles (code, description) VALUES
    ('SUPER_ADMIN',       'Administração da plataforma'),
    ('ADMIN_INSTITUTION', 'Administração da instituição'),
    ('LIBRARIAN',         'Bibliotecário'),
    ('ASSISTANT',         'Auxiliar de biblioteca'),
    ('TEACHER',           'Professor'),
    ('STUDENT',           'Aluno'),
    ('EMPLOYEE',          'Funcionário'),
    ('VISITOR',           'Visitante');

-- Multa por dia (centavos), valores do domínio original; ajustáveis.
INSERT INTO fine_rates (role_id, cents_per_day)
SELECT id, CASE code WHEN 'STUDENT' THEN 100 WHEN 'TEACHER' THEN 50 WHEN 'VISITOR' THEN 200 END
FROM roles WHERE code IN ('STUDENT', 'TEACHER', 'VISITOR');
