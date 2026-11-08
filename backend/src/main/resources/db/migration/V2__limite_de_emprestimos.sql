-- Limite de empréstimos simultâneos por perfil (configurável pela administração).
-- Perfis sem linha aqui (ex.: VISITOR) não podem emprestar.
CREATE TABLE loan_limits (
    role_id   bigint  PRIMARY KEY REFERENCES roles(id),
    max_loans integer NOT NULL CHECK (max_loans >= 1)
);

INSERT INTO loan_limits (role_id, max_loans)
SELECT id, 3 FROM roles WHERE code IN ('STUDENT', 'TEACHER', 'EMPLOYEE');
