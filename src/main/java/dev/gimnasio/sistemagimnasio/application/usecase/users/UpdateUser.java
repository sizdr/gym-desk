package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** No cambia si el usuario está activo. Un cambio de rol se aplica en su siguiente operación. */
public final class UpdateUser {
    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public UpdateUser(UserRepository users, PasswordHasher hasher, TransactionRunner transactions,
                      Authorizer authorizer) {
        this.users = Objects.requireNonNull(users, "users");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public UserRow execute(User actor, long id, UpdateUserCommand command) {
        return transactions.inTransaction(() -> {
            authorizer.require(actor, Permission.MANAGE_USERS);
            User current = UserRules.find(users, id);
            Map<String, String> errors = new LinkedHashMap<>();
            String name = UserRules.name(command.name(), errors);
            String username = UserRules.username(command.username(), id, users, errors);
            UserRules.role(command.role(), errors);
            if (command.role() != null && command.role() != Role.ADMINISTRADOR
                    && UserRules.isLastActiveAdmin(current, users)) {
                errors.put("role", UserRules.LAST_ADMIN);
            }
            boolean changesPassword = command.newPassword() != null && !command.newPassword().isEmpty();
            if (changesPassword) {
                UserRules.newPassword(command.newPassword(), command.newPasswordConfirmation(), "newPassword",
                        "newPasswordConfirmation", errors);
            }
            UserRules.throwIfAny(errors);

            User updated = new User(id, name, username, command.role(), current.active());
            users.update(updated);
            if (changesPassword) {
                users.updatePasswordHash(id, hasher.hash(command.newPassword()));
            }
            return UserRules.toRow(updated);
        });
    }
}
