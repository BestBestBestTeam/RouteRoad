package com.routeroad.shared.infrastructure;

import com.routeroad.shared.domain.AppUserRole;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "app_user")
@Access(AccessType.FIELD)
public class AppUserEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "login", nullable = false, unique = true, columnDefinition = "text")
    private String login;

    @Column(name = "password_hash", nullable = false, columnDefinition = "text")
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Convert(converter = AppUserRoleConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "role", nullable = false, length = 16)
    private AppUserRole role;

    @Column(name = "courier_id", nullable = true, unique = true)
    private UUID courierId;

    protected AppUserEntity() {
    }

    public AppUserEntity(
            UUID id,
            String login,
            String passwordHash,
            String name,
            AppUserRole role,
            UUID courierId) {
        this.id = id;
        this.login = login;
        this.passwordHash = passwordHash;
        this.name = name;
        this.role = role;
        this.courierId = courierId;
    }

    public UUID getId() {
        return id;
    }

    public String getLogin() {
        return login;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public AppUserRole getRole() {
        return role;
    }

    public UUID getCourierId() {
        return courierId;
    }
}
