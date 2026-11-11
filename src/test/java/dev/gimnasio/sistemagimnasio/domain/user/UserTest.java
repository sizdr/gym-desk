package dev.gimnasio.sistemagimnasio.domain.user;

import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UserTest {

    @ParameterizedTest
    @EnumSource(Role.class)
    void usuarioDesactivado_noPuedeHacerNada(Role role) {
        User user = new User(1L, "Ana", "ana", role, false);
        for (Permission permission : Permission.values()) {
            assertThrows(DomainException.class, () -> user.requirePermission(permission), permission::name);
        }
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    void usuarioActivo_soloPuedeLoQueSuRolPermite(Role role) {
        User user = new User(1L, "Ana", "ana", role, true);
        for (Permission permission : Permission.values()) {
            if (role.can(permission)) {
                assertDoesNotThrow(() -> user.requirePermission(permission), permission::name);
            } else {
                assertThrows(DomainException.class, () -> user.requirePermission(permission), permission::name);
            }
        }
    }

    @Test
    void sinPermiso_elMensajeNombraElRol() {
        User caja = new User(1L, "Lucía", "lucia", Role.CAJA, true);
        DomainException e = assertThrows(DomainException.class, () -> caja.requirePermission(Permission.MANAGE_SETTINGS));
        assertEquals("Tu rol (Caja) no tiene permiso para esta acción.", e.getMessage());
    }

    @Test
    void desactivado_elMensajePideVolverAIniciarSesion() {
        User user = new User(1L, "Ana", "ana", Role.ADMINISTRADOR, false);
        DomainException e = assertThrows(DomainException.class, user::requireActive);
        assertEquals("Tu sesión no es válida. Vuelve a iniciar sesión.", e.getMessage());
    }

    @Test
    void nombreYUsuario_seGuardanSinEspaciosAlrededor() {
        User user = new User(null, " Ana López ", " ana ", Role.CAJA, true);
        assertEquals("Ana López", user.name());
        assertEquals("ana", user.username());
    }

    @Test
    void nombreUsuarioYRol_sonObligatorios() {
        assertThrows(DomainException.class, () -> new User(null, " ", "ana", Role.CAJA, true));
        assertThrows(DomainException.class, () -> new User(null, "Ana", "", Role.CAJA, true));
        assertThrows(DomainException.class, () -> new User(null, "Ana", "ana", null, true));
    }

    @Test
    void elUsuario_seGuardaEnMinusculas() {
        assertEquals("lucia.perez", new User(null, "Lucía", " Lucia.Perez ", Role.CAJA, true).username());
    }

    @ParameterizedTest
    @ValueSource(strings = {"ana", "lucia.perez", "caja-2", "a1b", "abcdefghij0123456789"})
    void usuariosValidos_de3a20Caracteres(String username) {
        assertEquals(username, new User(null, "Persona", username, Role.CAJA, true).username());
    }

    @ParameterizedTest
    @ValueSource(strings = {"an", "abcdefghij01234567890", "lucia perez", "señor", "caja_2", "ana@gym"})
    void usuariosInvalidos_seRechazan(String username) {
        assertFalse(User.isValidUsername(User.normalizeUsername(username)));
        assertThrows(DomainException.class, () -> new User(null, "Persona", username, Role.CAJA, true));
    }

    @Test
    void elNombre_tieneComoMaximo100Caracteres() {
        assertEquals(User.MAX_NAME_LENGTH,
                new User(null, "x".repeat(User.MAX_NAME_LENGTH), "ana", Role.CAJA, true).name().length());
        assertThrows(DomainException.class,
                () -> new User(null, "x".repeat(User.MAX_NAME_LENGTH + 1), "ana", Role.CAJA, true));
    }
}
