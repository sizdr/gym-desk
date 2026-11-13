package dev.gimnasio.sistemagimnasio.infrastructure.bootstrap;

import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateInitialAdmin;
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateInitialAdminCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.users.UserRow;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * En una base sin usuarios, crea el primer administrador con {@code ADMIN_NAME}, {@code ADMIN_USERNAME} y
 * {@code ADMIN_PASSWORD} (del {@code .env} o del sistema). Si ya hay usuarios, no hace nada y esas variables se
 * ignoran, así que después se pueden borrar del {@code .env}.
 */
public final class InitialAdminSetup {
    static final String DEFAULT_NAME = "Administrador";
    static final String MISSING = "La base no tiene usuarios. Define ADMIN_USERNAME y ADMIN_PASSWORD en .env "
            + "para crear el primer administrador.";

    private static final System.Logger LOG = System.getLogger(InitialAdminSetup.class.getName());

    private InitialAdminSetup() {
    }

    /**
     * @return el administrador creado, o vacío si ya había usuarios
     * @throws IllegalStateException si no hay usuarios y faltan las variables o no son válidas
     */
    public static Optional<UserRow> ensure(Map<String, String> env, CreateInitialAdmin createInitialAdmin) {
        Objects.requireNonNull(env, "env");
        if (!createInitialAdmin.needsInitialAdmin()) {
            return Optional.empty();
        }
        String username = env.get("ADMIN_USERNAME");
        String password = env.get("ADMIN_PASSWORD");
        if (username == null || username.isBlank() || password == null || password.isEmpty()) {
            throw new IllegalStateException(MISSING);
        }
        String name = env.getOrDefault("ADMIN_NAME", "");
        name = name.isBlank() ? DEFAULT_NAME : name;
        try {
            UserRow admin = createInitialAdmin.execute(new CreateInitialAdminCommand(name, username, password, password));
            LOG.log(System.Logger.Level.INFO, "Se creó el administrador inicial «{0}». Cambia su contraseña "
                    + "después del primer acceso.", admin.username());
            return Optional.of(admin);
        } catch (ValidationException e) {
            throw new IllegalStateException("No se pudo crear el administrador inicial con las variables ADMIN_* del "
                    + ".env: " + e.getMessage(), e);
        }
    }
}
