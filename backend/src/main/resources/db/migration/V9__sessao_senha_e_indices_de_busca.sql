-- Sessão: token_version muda ao trocar senha ou perfis, e isso derruba os tokens já emitidos.
-- must_change_password: senha definida pela equipe (cadastro ou redefinição) precisa ser trocada pelo dono.
ALTER TABLE users ADD COLUMN token_version integer NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN must_change_password boolean NOT NULL DEFAULT false;

-- Busca por trecho, sem diferenciar acento nem maiúscula ("jose" acha "José").
-- Ambas as extensões são "trusted" no PostgreSQL 13+: o dono do banco pode criá-las.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS unaccent;

-- unaccent() é STABLE e não pode ser usada em índice; esta versão fixa o dicionário e pode.
CREATE FUNCTION f_unaccent(text) RETURNS text
    LANGUAGE sql IMMUTABLE PARALLEL SAFE STRICT
    AS $$ SELECT public.unaccent('public.unaccent'::regdictionary, $1) $$;

CREATE INDEX idx_books_title_search   ON books   USING gin (f_unaccent(lower(title)) gin_trgm_ops);
CREATE INDEX idx_authors_name_search  ON authors USING gin (f_unaccent(lower(name)) gin_trgm_ops);
CREATE INDEX idx_users_name_search    ON users   USING gin (f_unaccent(lower(full_name)) gin_trgm_ops);
CREATE INDEX idx_users_email_search   ON users   USING gin (lower(email) gin_trgm_ops);
CREATE INDEX idx_users_registration_search ON users USING gin (lower(registration) gin_trgm_ops);

-- Listas ordenadas por nome/título (paginação sem ordenar a tabela inteira).
CREATE INDEX idx_books_title     ON books (title);
CREATE INDEX idx_users_full_name ON users (full_name);

-- Chaves estrangeiras consultadas no sentido inverso: o PostgreSQL não as indexa sozinho.
CREATE INDEX idx_book_authors_author      ON book_authors (author_id);
CREATE INDEX idx_book_categories_category ON book_categories (category_id);
CREATE INDEX idx_books_publisher          ON books (publisher_id);
