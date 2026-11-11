package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.util.Objects;

/**
 * El usuario que inició sesión. Se envía como {@code actor} a cada caso de uso, que vuelve a revisar
 * su permiso en la base; {@link #can} solo decide qué áreas mostrar.
 */
public final class Session {
    private final ReadOnlyObjectWrapper<User> actor = new ReadOnlyObjectWrapper<>(this, "actor");

    public void open(User user) {
        actor.set(Objects.requireNonNull(user, "user"));
    }

    /** Cerrar sesión no es un caso de uso: se descarta el actor y se vuelve al acceso. */
    public void close() {
        actor.set(null);
    }

    public boolean isOpen() {
        return actor.get() != null;
    }

    public User actor() {
        User current = actor.get();
        if (current == null) {
            throw new IllegalStateException("No hay una sesión abierta.");
        }
        return current;
    }

    public boolean can(Permission permission) {
        User current = actor.get();
        return current != null && current.role().can(permission);
    }

    public ReadOnlyObjectProperty<User> actorProperty() {
        return actor.getReadOnlyProperty();
    }
}
