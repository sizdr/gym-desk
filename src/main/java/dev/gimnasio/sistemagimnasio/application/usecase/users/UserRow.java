package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.domain.user.Role;

public record UserRow(long id, String name, String username, Role role, boolean active) {
}
