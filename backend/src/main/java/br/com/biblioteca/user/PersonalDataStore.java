package br.com.biblioteca.user;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dados pessoais de um usuário espalhados pelas tabelas (LGPD): exportação, limpeza na anonimização e retenção.
 * SQL fixo, só com parâmetros ligados (como em report e notification).
 */
@Component
class PersonalDataStore {

    /** Tentativas de login falhas só servem ao bloqueio de 15 min; depois disso guardam e-mail alheio à toa. */
    static final int FAILED_LOGIN_RETENTION_DAYS = 90;

    private final JdbcTemplate jdbc;

    PersonalDataStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Tudo o que o sistema guarda sobre a pessoa, inclusive CPF, endereço e o registro dos próprios acessos. */
    Map<String, Object> export(Long id) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("generatedAt", Instant.now());
        data.put("profile", jdbc.queryForMap("""
                select id, full_name, email, cpf, registration, phone, address, active, created_at
                from users where id = ?""", id));
        data.put("roles", jdbc.queryForList(
                "select r.code from user_roles ur join roles r on r.id = ur.role_id where ur.user_id = ? order by r.code",
                String.class, id));
        data.put("loans", jdbc.queryForList("""
                select l.id, b.title, c.barcode as copy_code, l.loaned_at, l.due_at, l.returned_at
                from loans l join book_copies c on c.id = l.copy_id join books b on b.id = c.book_id
                where l.user_id = ? order by l.loaned_at""", id));
        data.put("reservations", jdbc.queryForList("""
                select r.id, b.title, r.status, r.queued_at, r.expires_at
                from reservations r join books b on b.id = r.book_id
                where r.user_id = ? order by r.queued_at""", id));
        data.put("fines", jdbc.queryForList("""
                select f.id, f.loan_id, f.amount, f.days_late, f.status, f.created_at
                from fines f join loans l on l.id = f.loan_id
                where l.user_id = ? order by f.created_at""", id));
        data.put("notifications", jdbc.queryForList(
                "select type, message, channel, sent_at, created_at from notifications where user_id = ? order by created_at", id));
        data.put("accessLog", jdbc.queryForList(
                "select action, ip_address, created_at from audit_logs where user_id = ? order by created_at", id));
        return data;
    }

    /** Motivo que impede a anonimização, ou null. Empréstimo, multa e reserva abertos ainda dependem da identidade. */
    String openObligation(Long id) {
        if (count("select count(*) from loans where user_id = ? and returned_at is null", id) > 0) {
            return "tem empréstimo em aberto";
        }
        if (count("select count(*) from fines f join loans l on l.id = f.loan_id where l.user_id = ? and f.status = 'ABERTA'", id) > 0) {
            return "tem multa em aberto";
        }
        if (count("select count(*) from reservations where user_id = ? and status in ('NA_FILA', 'DISPONIVEL')", id) > 0) {
            return "tem reserva em andamento";
        }
        return null;
    }

    /** Apaga o que identifica a pessoa fora de users: avisos, IPs e e-mails digitados em tentativas de login. */
    void scrub(Long id, String oldEmail) {
        jdbc.update("delete from notifications where user_id = ?", id);
        jdbc.update("update audit_logs set details = null, ip_address = null where user_id = ?", id);
        jdbc.update("""
                update audit_logs set details = null, ip_address = null
                where action in ('LOGIN_FAILED', 'LOGIN_BLOCKED') and lower(details) = lower(?)""", oldEmail);
    }

    @Scheduled(cron = "0 30 3 * * *", zone = "${app.zone:America/Sao_Paulo}")
    @Transactional
    void purgeOldFailedLogins() {
        jdbc.update("""
                delete from audit_logs
                where action in ('LOGIN_FAILED', 'LOGIN_BLOCKED') and created_at < now() - make_interval(days => ?)""",
                FAILED_LOGIN_RETENTION_DAYS);
    }

    private long count(String sql, Long id) {
        Long n = jdbc.queryForObject(sql, Long.class, id);
        return n == null ? 0 : n;
    }
}
