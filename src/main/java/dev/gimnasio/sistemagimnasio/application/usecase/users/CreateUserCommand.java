package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.domain.user.Role;

public record CreateUserCommand(String name, String username, Role role, String password,
                                String passwordConfirmation) {
    @Override
    public String toString() {
        return "CreateUserCommand[name=" + name + ", username=" + username + ", role=" + role
                + ", password=***, passwordConfirmation=***]";
    }
}
