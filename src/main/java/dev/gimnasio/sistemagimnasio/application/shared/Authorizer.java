package dev.gimnasio.sistemagimnasio.application.shared;

import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** Revisa el permiso con el usuario como está hoy en la base, no como estaba al iniciar sesión. */
public final class Authorizer {
    static final String INVALID_SESSION = "Tu sesión no es válida. Vuelve a iniciar sesión.";

    private final UserRepository users;

    public Authorizer(UserRepository users) {
        this.users = Objects.requireNonNull(users, "users");
    }

    /** @return el usuario vigente, para registrarlo como quien hizo la operación */
    public User require(User actor, Permission permission) {
        if (actor == null || actor.id() == null) {
            throw new DomainException(INVALID_SESSION);
        }
        User refreshed = users.findById(actor.id()).orElseThrow(() -> new DomainException(INVALID_SESSION));
        refreshed.requirePermission(permission);
        return refreshed;
    }
}
