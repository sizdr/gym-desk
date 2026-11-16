package dev.gimnasio.sistemagimnasio.domain.user;

import java.util.EnumSet;
import java.util.Set;

import static dev.gimnasio.sistemagimnasio.domain.user.Permission.*;

public enum Role {
    ADMINISTRADOR("Administrador", EnumSet.allOf(Permission.class)),
    RECEPCION("Recepción", EnumSet.of(VIEW_DAILY_SUMMARY, CHARGE_MEMBERSHIP, VIEW_MEMBERS, EDIT_MEMBERS)),
    CAJA("Caja", EnumSet.of(CHARGE_MEMBERSHIP, VIEW_MEMBERS));

    private final String displayName;
    private final Set<Permission> permissions;

    Role(String displayName, Set<Permission> permissions) {
        this.displayName = displayName;
        this.permissions = permissions;
    }

    public String displayName() {
        return displayName;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }

    public boolean can(Permission permission) {
        return permissions.contains(permission);
    }
}
