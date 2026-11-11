package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** Un usuario desactivado no entra, pero sus cobros, ventas y movimientos se conservan. */
public final class SetUserActive {
    static final String SELF = "No puedes desactivar tu propio usuario mientras lo usas.";

    private final UserRepository users;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public SetUserActive(UserRepository users, TransactionRunner transactions, Authorizer authorizer) {
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public UserRow execute(User actor, long id, boolean active) {
        return transactions.inTransaction(() -> {
            User current = authorizer.require(actor, Permission.MANAGE_USERS);
            User target = UserRules.find(users, id);
            if (target.active() == active) {
                return UserRules.toRow(target);
            }
            if (!active) {
                if (target.id().equals(current.id())) {
                    throw new DomainException(SELF);
                }
                if (UserRules.isLastActiveAdmin(target, users)) {
                    throw new DomainException(UserRules.LAST_ADMIN);
                }
            }
            User updated = new User(id, target.name(), target.username(), target.role(), active);
            users.update(updated);
            return UserRules.toRow(updated);
        });
    }
}
