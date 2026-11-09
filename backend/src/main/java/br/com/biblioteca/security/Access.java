package br.com.biblioteca.security;

/** Expressões de autorização reutilizadas em @PreAuthorize. */
public final class Access {

    /** Equipe da biblioteca que opera cadastros. */
    public static final String STAFF = "hasAnyRole('SUPER_ADMIN','ADMIN_INSTITUTION','LIBRARIAN','ASSISTANT')";

    public static final String[] STAFF_ROLES = {"SUPER_ADMIN", "ADMIN_INSTITUTION", "LIBRARIAN", "ASSISTANT"};

    /** Isenção de multa: só quem pode autorizá-la. */
    public static final String[] WAIVER_ROLES = {"SUPER_ADMIN", "ADMIN_INSTITUTION", "LIBRARIAN"};

    public static final String WAIVER = "hasAnyRole('SUPER_ADMIN','ADMIN_INSTITUTION','LIBRARIAN')";

    public static final String[] ADMIN_ROLES = {"SUPER_ADMIN", "ADMIN_INSTITUTION"};

    public static final String ADMIN = "hasAnyRole('SUPER_ADMIN','ADMIN_INSTITUTION')";

    private Access() {
    }
}
