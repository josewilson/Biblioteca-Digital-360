-- Capa enviada pelo operador. Os bytes (já reduzidos pelo servidor) ficam em tabela própria para a listagem de obras não os carregar.
-- books.cover_version muda a cada envio e entra no endereço da imagem (cache longo sem servir capa velha); nulo = sem capa enviada.
ALTER TABLE books ADD COLUMN cover_version bigint;

CREATE TABLE book_covers (
    book_id     bigint PRIMARY KEY REFERENCES books(id) ON DELETE CASCADE,
    full_image  bytea NOT NULL,
    thumb_image bytea NOT NULL
);
