package dev.gimnasio.sistemagimnasio.domain.user;

import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;

import java.util.Locale;
import java.util.regex.Pattern;

/** La contraseña no forma parte del dominio. El nombre de usuario se guarda en minúsculas. */
public record User(Long id, String name, String username, Role role, boolean active) {
    /** Los mensajes se arman con estos límites; la base repite los mismos en sus varchar. */
    public static final int MAX_NAME_LENGTH = 100;
    public static final int MIN_USERNAME_LENGTH = 3;
    public static final int MAX_USERNAME_LENGTH = 20;
    private static final Pattern USERNAME =
            Pattern.compile("[a-z0-9.-]{" + MIN_USERNAME_LENGTH + "," + MAX_USERNAME_LENGTH + "}");

    public User {
        if (name == null || name.isBlank()) {
            throw new DomainException("El nombre es obligatorio.");
        }
        name = name.strip();
        if (!isValidName(name)) {
            throw new DomainException("El nombre debe tener " + MAX_NAME_LENGTH + " caracteres o menos.");
        }
        if (username == null || username.isBlank()) {
            throw new DomainException("El nombre de usuario es obligatorio.");
        }
        username = normalizeUsername(username);
        if (!isValidUsername(username)) {
            throw new DomainException("El nombre de usuario debe tener de " + MIN_USERNAME_LENGTH + " a "
                    + MAX_USERNAME_LENGTH + " letras minúsculas, números, punto o guion.");
        }
        if (role == null) {
            throw new DomainException("El rol es obligatorio.");
        }
    }

    /** Recorta los extremos y pasa a minúsculas; null queda null. */
    public static String normalizeUsername(String username) {
        return username == null ? null : username.strip().toLowerCase(Locale.ROOT);
    }

    /** El nombre ya recortado: no vacío y dentro del largo máximo. */
    public static boolean isValidName(String name) {
        return name != null && !name.isBlank() && name.length() <= MAX_NAME_LENGTH;
    }

    /** El nombre de usuario ya normalizado. */
    public static boolean isValidUsername(String username) {
        return username != null && USERNAME.matcher(username).matches();
    }

    public void requireActive() {
        if (!active) {
            throw new DomainException("Tu sesión no es válida. Vuelve a iniciar sesión.");
        }
    }

    public void requirePermission(Permission permission) {
        requireActive();
        if (!role.can(permission)) {
            throw new DomainException("Tu rol (" + role.displayName() + ") no tiene permiso para esta acción.");
        }
    }
}
