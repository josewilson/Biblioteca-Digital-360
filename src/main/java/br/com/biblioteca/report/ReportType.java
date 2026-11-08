package br.com.biblioteca.report;

import br.com.biblioteca.shared.error.ResourceNotFoundException;

/**
 * Relatórios disponíveis. O SQL é fixo (nunca montado com texto do usuário); o usuário só escolhe o relatório
 * e o período. Parâmetros nomeados: :from, :to (período, [from, to)), :now, :zone, :limit.
 */
enum ReportType {

    LOANS("loans", """
            select l.id as emprestimo, u.full_name as leitor, b.title as obra, c.barcode as exemplar,
                   l.loaned_at as emprestado_em, l.due_at as vencimento, l.returned_at as devolvido_em,
                   case when l.returned_at is not null then 'DEVOLVIDO'
                        when l.due_at < :now then 'ATRASADO' else 'EM_ANDAMENTO' end as situacao
            from loans l join users u on u.id = l.user_id
                 join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
            where l.loaned_at >= :from and l.loaned_at < :to
            order by l.loaned_at desc limit :limit"""),

    RETURNS("returns", """
            select l.id as emprestimo, u.full_name as leitor, b.title as obra, c.barcode as exemplar,
                   l.due_at as vencimento, l.returned_at as devolvido_em,
                   greatest(0, (l.returned_at at time zone :zone)::date - (l.due_at at time zone :zone)::date) as dias_de_atraso
            from loans l join users u on u.id = l.user_id
                 join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
            where l.returned_at >= :from and l.returned_at < :to
            order by l.returned_at desc limit :limit"""),

    OVERDUE("overdue", """
            select l.id as emprestimo, u.full_name as leitor, u.email as email, b.title as obra,
                   c.barcode as exemplar, l.due_at as vencimento,
                   (cast(:now as timestamptz) at time zone :zone)::date - (l.due_at at time zone :zone)::date as dias_de_atraso
            from loans l join users u on u.id = l.user_id
                 join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
            where l.returned_at is null and l.due_at < :now
            order by l.due_at limit :limit"""),

    FINES("fines", """
            select f.id as multa, u.full_name as leitor, b.title as obra, f.amount as valor,
                   f.days_late as dias_de_atraso, f.status as situacao, f.waiver_reason as justificativa_isencao,
                   f.created_at as gerada_em
            from fines f join loans l on l.id = f.loan_id join users u on u.id = l.user_id
                 join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
            where f.created_at >= :from and f.created_at < :to
            order by f.created_at desc limit :limit"""),

    PAYMENTS("payments", """
            select p.id as pagamento, f.id as multa, u.full_name as leitor, p.amount as valor,
                   p.method as forma, p.paid_at as pago_em
            from payments p join fines f on f.id = p.fine_id join loans l on l.id = f.loan_id
                 join users u on u.id = l.user_id
            where p.paid_at >= :from and p.paid_at < :to
            order by p.paid_at desc limit :limit"""),

    /** Só totais por perfil: sem dados pessoais. */
    USERS("users", """
            select r.code as perfil, count(u.id) filter (where u.active and u.deleted_at is null) as ativos,
                   count(u.id) as total
            from roles r left join user_roles ur on ur.role_id = r.id left join users u on u.id = ur.user_id
            group by r.code order by r.code limit :limit"""),

    TOP_BOOKS("top-books", """
            select b.title as obra, count(*) as emprestimos
            from loans l join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
            where l.loaned_at >= :from and l.loaned_at < :to
            group by b.id, b.title order by emprestimos desc, b.title limit :limit"""),

    AVAILABILITY("availability", """
            select b.title as obra,
                   count(c.id) filter (where c.status not in ('PERDIDO', 'BAIXADO')) as exemplares,
                   count(c.id) filter (where c.kind = 'CONSULTA' and c.status not in ('PERDIDO', 'BAIXADO')) as consulta,
                   count(c.id) filter (where c.kind = 'CIRCULANTE' and c.status = 'DISPONIVEL') as disponiveis,
                   count(c.id) filter (where c.status = 'EMPRESTADO') as emprestados,
                   count(c.id) filter (where c.status = 'RESERVADO') as reservados
            from books b left join book_copies c on c.book_id = b.id
            group by b.id, b.title order by b.title limit :limit""");

    final String slug;
    final String sql;

    ReportType(String slug, String sql) {
        this.slug = slug;
        this.sql = sql;
    }

    static ReportType fromSlug(String slug) {
        for (ReportType t : values()) {
            if (t.slug.equals(slug)) {
                return t;
            }
        }
        throw new ResourceNotFoundException("Relatório inexistente: " + slug);
    }
}
