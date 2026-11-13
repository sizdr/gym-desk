package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Crea el primer administrador. Sin actor ni permiso: solo funciona mientras no exista ningún usuario, y
 * revisarlo y crearlo van en la misma transacción para que dos estaciones no creen dos.
 */
public final class CreateInitialAdmin {
    static final String ALREADY_HAS_USERS = "El sistema ya tiene usuarios; un administrador debe crear las cuentas nuevas.";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final TransactionRunner transactions;

    public CreateInitialAdmin(UserRepository users, PasswordHasher hasher, TransactionRunner transactions) {
        this.users = Objects.requireNonNull(users, "users");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
    }

    /** {@code true} si todavía no hay usuarios y hay que mostrar el primer arranque. */
    public boolean needsInitialAdmin() {
        return users.count() == 0;
    }

    public UserRow execute(CreateInitialAdminCommand command) {
        return transactions.inTransaction(() -> {
            if (users.count() > 0) {
                throw new DomainException(ALREADY_HAS_USERS);
            }
            Map<String, String> errors = new LinkedHashMap<>();
            String name = UserRules.name(command.name(), errors);
            String username = UserRules.username(command.username(), null, users, errors);
            UserRules.password(command.password(), command.passwordConfirmation(), "password",
                    "passwordConfirmation", errors);
            UserRules.throwIfAny(errors);

            User admin = users.insert(new User(null, name, username, Role.ADMINISTRADOR, true),
                    hasher.hash(command.password()));
            return UserRules.toRow(admin);
        });
    }
}
