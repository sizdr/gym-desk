package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** Solo se borra un usuario que nunca registró nada; uno con historial se desactiva. */
public final class DeleteUser {
    static final String SELF = "No puedes eliminar tu propio usuario mientras lo usas.";

    private final UserRepository users;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public DeleteUser(UserRepository users, TransactionRunner transactions, Authorizer authorizer) {
        this.users = Objects.requireNonNull(users, "users");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public void execute(User actor, long id) {
        transactions.run(() -> {
            User actingUser = authorizer.require(actor, Permission.MANAGE_USERS);
            User target = UserRules.find(users, id);
            if (target.id().equals(actingUser.id())) {
                throw new DomainException(SELF);
            }
            if (users.hasOperations(id)) {
                throw new DomainException("No puedes eliminar a «" + target.name()
                        + "»: ya tiene cobros, ventas o movimientos registrados. Desactívalo para que no pueda entrar.");
            }
            if (UserRules.isLastActiveAdmin(target, users)) {
                throw new DomainException(UserRules.LAST_ADMIN);
            }
            users.delete(id);
        });
    }
}
