package dev.gimnasio.sistemagimnasio.application.usecase.authenticate;

import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.UserCredentials;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Siempre se verifica un hash, exista o no el usuario: con uno inexistente se compara contra un hash de relleno.
 * Así ambos casos tardan lo mismo (la verificación Argon2 es lo lento) y no se puede averiguar qué nombres de
 * usuario existen midiendo cuánto tarda el rechazo.
 */
public final class Authenticate {
    static final String MISSING_USERNAME = "Escribe tu nombre de usuario.";
    static final String MISSING_PASSWORD = "Escribe tu contraseña.";
    static final String INVALID_CREDENTIALS = "Usuario o contraseña incorrectos. Revísalos e inténtalo de nuevo.";
    static final String USER_DISABLED = "Tu usuario está desactivado. Pide a un administrador que lo active.";
    /** Solo sirve para calcular el hash de relleno; nunca da acceso porque no corresponde a ningún usuario. */
    static final String DUMMY_PASSWORD = "sin-usuario";

    private final UserRepository users;
    private final PasswordHasher hasher;
    /** Se calcula en el primer intento, exista o no el usuario, para que ese intento tampoco delate nada. */
    private volatile String dummyHash;

    public Authenticate(UserRepository users, PasswordHasher hasher) {
        this.users = Objects.requireNonNull(users, "users");
        this.hasher = Objects.requireNonNull(hasher, "hasher");
    }

    public User execute(AuthenticateCommand command) {
        String username = command.username() == null ? "" : command.username().strip();
        String password = command.password() == null ? "" : command.password();
        Map<String, String> errors = new LinkedHashMap<>();
        if (username.isEmpty()) {
            errors.put("username", MISSING_USERNAME);
        }
        if (password.isEmpty()) {
            errors.put("password", MISSING_PASSWORD);
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }

        String fallbackHash = dummyHash();
        Optional<UserCredentials> found = users.findCredentialsByUsername(username);
        boolean matches = hasher.matches(password, found.map(UserCredentials::passwordHash).orElse(fallbackHash));
        if (found.isEmpty() || !matches) {
            throw new AuthenticationException(INVALID_CREDENTIALS);
        }

        User user = found.get().user();
        if (!user.active()) {
            throw new AuthenticationException(USER_DISABLED);
        }
        return user;
    }

    /** Si dos intentos llegan a la vez la primera vez, se calcula dos veces; da igual cuál se quede. */
    private String dummyHash() {
        String hash = dummyHash;
        if (hash == null) {
            hash = hasher.hash(DUMMY_PASSWORD);
            dummyHash = hash;
        }
        return hash;
    }
}
