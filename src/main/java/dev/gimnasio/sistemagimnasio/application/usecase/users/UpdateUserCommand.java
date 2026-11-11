package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.domain.user.Role;

/** {@code newPassword} en null o vacío conserva la contraseña actual. */
public record UpdateUserCommand(String name, String username, Role role, String newPassword,
                                String newPasswordConfirmation) {
    @Override
    public String toString() {
        return "UpdateUserCommand[name=" + name + ", username=" + username + ", role=" + role
                + ", newPassword=***, newPasswordConfirmation=***]";
    }
}
