package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class CreateUser {
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public CreateUser(UserRepository users, PasswordHasher hasher, TransactionRunner transactions,
                      Authorizer authorizer) {
        this.users = Objects.requireNonNull(users, "users");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    /** El usuario nuevo queda activo; solo se guarda el hash de su contraseña. */
    public UserRow execute(User actor, CreateUserCommand command) {
        return transactions.inTransaction(() -> {
            authorizer.require(actor, Permission.MANAGE_USERS);
            Map<String, String> errors = new LinkedHashMap<>();
            String name = UserRules.name(command.name(), errors);
            String username = UserRules.username(command.username(), null, users, errors);
            UserRules.role(command.role(), errors);
            UserRules.password(command.password(), command.passwordConfirmation(), "password",
                    "passwordConfirmation", errors);
            UserRules.throwIfAny(errors);

            User created = users.insert(new User(null, name, username, command.role(), true),
                    hasher.hash(command.password()));
            return UserRules.toRow(created);
        });
    }
}
