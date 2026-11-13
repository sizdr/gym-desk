package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public final class ListUsers {
    private static final Comparator<User> ORDER = Comparator.comparing((User user) -> !user.active())
            .thenComparing(User::name, String.CASE_INSENSITIVE_ORDER);

    private final UserRepository users;
    private final Authorizer authorizer;

    public ListUsers(UserRepository users, Authorizer authorizer) {
        this.users = Objects.requireNonNull(users, "users");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    /** Primero los activos; en cada grupo, por nombre. */
    public List<UserRow> execute(User actor) {
        authorizer.require(actor, Permission.MANAGE_USERS);
        return users.findAll().stream().sorted(ORDER).map(UserRules::toRow).toList();
    }
}
