package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.user.PasswordPolicy;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Map;
import java.util.Objects;

/** Lo que comparten los casos de uso de usuarios. Cada método agrega su error al mapa del formulario. */
final class UserRules {
    static final String NAME_REQUIRED = "Escribe el nombre de la persona.";
    static final String NAME_TOO_LONG = "Usa " + User.MAX_NAME_LENGTH + " caracteres o menos.";
    static final String USERNAME_REQUIRED = "Escribe el nombre de usuario.";
    static final String USERNAME_FORMAT = "Usa de " + User.MIN_USERNAME_LENGTH + " a " + User.MAX_USERNAME_LENGTH
            + " letras minúsculas, números, punto o guion, sin espacios.";
    static final String USERNAME_TAKEN = "Ese nombre de usuario ya está en uso.";
    static final String ROLE_REQUIRED = "Elige un rol.";
    static final String PASSWORD_REQUIRED = "Escribe una contraseña.";
    static final String PASSWORD_TOO_SHORT = "La contraseña debe tener al menos " + PasswordPolicy.MIN_LENGTH
            + " caracteres.";
    static final String PASSWORDS_DIFFER = "Las contraseñas no coinciden.";
    static final String LAST_ADMIN = "Debe quedar al menos un administrador activo.";
    static final String NOT_FOUND = "El usuario ya no existe. Recarga la pantalla.";

    private UserRules() {
    }

    /** @return el nombre recortado */
    static String name(String raw, Map<String, String> errors) {
        String name = raw == null ? "" : raw.strip();
        if (name.isEmpty()) {
            errors.put("name", NAME_REQUIRED);
        } else if (!User.isValidName(name)) {
            errors.put("name", NAME_TOO_LONG);
        }
        return name;
    }

    /**
     * @param currentId el propio usuario al editarlo; null al crear
     * @return el usuario normalizado (recortado y en minúsculas)
     */
    static String username(String raw, Long currentId, UserRepository users, Map<String, String> errors) {
        String username = raw == null ? "" : User.normalizeUsername(raw);
        if (username.isEmpty()) {
            errors.put("username", USERNAME_REQUIRED);
        } else if (!User.isValidUsername(username)) {
            errors.put("username", USERNAME_FORMAT);
        } else if (users.existsByUsername(username, currentId)) {
            errors.put("username", USERNAME_TAKEN);
        }
        return username;
    }

    static void role(Role role, Map<String, String> errors) {
        if (role == null) {
            errors.put("role", ROLE_REQUIRED);
        }
    }

    /** Contraseña obligatoria (al crear). */
    static void password(String password, String confirmation, String field, String confirmationField,
                         Map<String, String> errors) {
        if (password == null || password.isEmpty()) {
            errors.put(field, PASSWORD_REQUIRED);
            return;
        }
        newPassword(password, confirmation, field, confirmationField, errors);
    }

    /** Contraseña ya escrita: largo mínimo y confirmación. No se recortan espacios. */
    static void newPassword(String password, String confirmation, String field, String confirmationField,
                            Map<String, String> errors) {
        if (!PasswordPolicy.isAcceptable(password)) {
            errors.put(field, PASSWORD_TOO_SHORT);
        } else if (!Objects.equals(password, confirmation)) {
            errors.put(confirmationField, PASSWORDS_DIFFER);
        }
    }

    static void throwIfAny(Map<String, String> errors) {
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    static User find(UserRepository users, long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    static boolean isLastActiveAdmin(User user, UserRepository users) {
        return user.active() && user.role() == Role.ADMINISTRADOR && users.countActiveAdmins() <= 1;
    }

    static UserRow toRow(User user) {
        return new UserRow(user.id(), user.name(), user.username(), user.role(), user.active());
    }
}
