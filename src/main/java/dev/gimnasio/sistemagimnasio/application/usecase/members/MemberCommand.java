package dev.gimnasio.sistemagimnasio.application.usecase.members;

import java.time.LocalDate;

/**
 * Los datos de un socio tal como se capturaron, para darlo de alta o corregirlo.
 *
 * @param email opcional: null o vacío lo deja sin correo
 */
public record MemberCommand(String fullName, String phone, String email, LocalDate joinDate) {
}
