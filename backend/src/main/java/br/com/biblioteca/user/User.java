package br.com.biblioteca.user;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import org.hibernate.annotations.BatchSize;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String fullName;
    private String email;
    private String passwordHash;
    private String cpf;
    private String registration;
    private String phone;
    private String address;
    private boolean active = true;
    private boolean mustChangePassword;
    private int tokenVersion;
    private Instant deletedAt;
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @ManyToMany(fetch = FetchType.EAGER)
    @BatchSize(size = 100)   // lista de 200 usuários: 2 consultas de perfis em vez de 200
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public User(String fullName, String email, String passwordHash, Set<Role> roles) {
        this.fullName = fullName;
        this.email = email;
        this.passwordHash = passwordHash;
        this.roles = roles;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public boolean canLogin() {
        return active && deletedAt == null && passwordHash != null;
    }

    public void deactivate() {
        active = false;
        deletedAt = Instant.now();
    }

    public void reactivate() {
        active = true;
        deletedAt = null;
    }

    /** Troca a senha e derruba as sessões abertas com a senha anterior. */
    public void changePassword(String newHash, boolean mustChangeNext) {
        passwordHash = newHash;
        mustChangePassword = mustChangeNext;
        tokenVersion++;
    }

    public void requirePasswordChange() {
        mustChangePassword = true;
    }

    public void rename(String fullName, String email) {
        this.fullName = fullName;
        this.email = email;
    }

    /** Os perfis vão no token: mudá-los derruba as sessões abertas, que carregariam os antigos. */
    public void replaceRoles(Set<Role> roles) {
        this.roles = roles;
        tokenVersion++;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
    }

    public void setRegistration(String registration) {
        this.registration = registration;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getRegistration() {
        return registration;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public int getTokenVersion() {
        return tokenVersion;
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return roles;
    }
}
