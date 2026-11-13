package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.fakes.ReversingHasher;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.Authenticate;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.AuthenticateCommand;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsersTest {
    final InMemoryUsers users = new InMemoryUsers();
    final ReversingHasher hasher = new ReversingHasher();
    final ImmediateTransactions transactions = new ImmediateTransactions();
    final Authorizer authorizer = new Authorizer(users);
    final User admin = users.add(new User(null, "Ana Admin", "ana", Role.ADMINISTRADOR, true), "hash");

    final ListUsers list = new ListUsers(users, authorizer);

    @Nested
    class Listar {
        @Test
        void primeroLosActivos_luegoPorNombre() {
            users.add(new User(null, "lucía Pérez", "lucia", Role.CAJA, true), "h");
            users.add(new User(null, "Beto Ruiz", "beto", Role.RECEPCION, false), "h");
            users.add(new User(null, "Carla Díaz", "carla", Role.CAJA, true), "h");

            assertEquals(List.of("Ana Admin", "Carla Díaz", "lucía Pérez", "Beto Ruiz"),
                    list.execute(admin).stream().map(UserRow::name).toList());
        }

        @Test
        void cadaFila_traeSusDatos_sinElHash() {
            assertEquals(List.of(new UserRow(admin.id(), "Ana Admin", "ana", Role.ADMINISTRADOR, true)),
                    list.execute(admin));
        }
    }

    final CreateUser create = new CreateUser(users, hasher, transactions, authorizer);
    final UpdateUser update = new UpdateUser(users, hasher, transactions, authorizer);
    final SetUserActive setActive = new SetUserActive(users, transactions, authorizer);
    final DeleteUser delete = new DeleteUser(users, transactions, authorizer);

    private static Map<String, String> errors(Executable operation) {
        return assertThrows(ValidationException.class, operation).errors();
    }

    private static CreateUserCommand newUser(String name, String username, Role role, String password, String confirmation) {
        return new CreateUserCommand(name, username, role, password, confirmation);
    }

    private User lucia() {
        return users.add(new User(null, "Lucía Pérez", "lucia", Role.CAJA, true), hasher.hash("Mostrador#2026"));
    }

    @Nested
    class Crear {
        @Test
        void quedaActivo_conElUsuarioEnMinusculas_yGuardaSoloElHash() {
            UserRow row = create.execute(admin, newUser(" Pedro Ruiz ", " Pedro.R ", Role.RECEPCION, "Recepcion#1", "Recepcion#1"));

            assertEquals(new UserRow(row.id(), "Pedro Ruiz", "pedro.r", Role.RECEPCION, true), row);
            assertEquals(hasher.hash("Recepcion#1"), users.hashOf(row.id()));
            assertNotEquals("Recepcion#1", users.hashOf(row.id()));
        }

        @Test
        void elUsuarioCreado_puedeIniciarSesion() {
            create.execute(admin, newUser("Pedro Ruiz", "pedro", Role.RECEPCION, "Recepcion#1", "Recepcion#1"));

            User logged = new Authenticate(users, hasher).execute(new AuthenticateCommand("PEDRO", "Recepcion#1"));
            assertEquals(Role.RECEPCION, logged.role());
        }

        @Test
        void todosLosErrores_juntos_yEnOrden_sinGuardar() {
            Map<String, String> errors = errors(() -> create.execute(admin, newUser(" ", "", null, "", "")));

            assertEquals(List.of("name", "username", "role", "password"), List.copyOf(errors.keySet()));
            assertEquals("Escribe el nombre de la persona.", errors.get("name"));
            assertEquals("Escribe el nombre de usuario.", errors.get("username"));
            assertEquals("Elige un rol.", errors.get("role"));
            assertEquals("Escribe una contraseña.", errors.get("password"));
            assertEquals(0, users.writes);
        }

        @ParameterizedTest
        @ValueSource(strings = {"lu", "lucia perez", "señora", "caja_1", "abcdefghij01234567890"})
        void usuarioConFormatoInvalido_seRechaza(String username) {
            assertEquals(Map.of("username", "Usa de 3 a 20 letras minúsculas, números, punto o guion, sin espacios."),
                    errors(() -> create.execute(admin, newUser("Persona", username, Role.CAJA, "12345678", "12345678"))));
        }

        @ParameterizedTest
        @ValueSource(strings = {"ana", "ANA", " Ana "})
        void usuarioRepetido_sinDistinguirMayusculas_seRechaza(String username) {
            assertEquals(Map.of("username", "Ese nombre de usuario ya está en uso."),
                    errors(() -> create.execute(admin, newUser("Otra Ana", username, Role.CAJA, "12345678", "12345678"))));
        }

        @Test
        void nombreDeMasDe100Caracteres_seRechaza() {
            assertEquals(Map.of("name", "Usa 100 caracteres o menos."), errors(() -> create.execute(admin,
                    newUser("x".repeat(101), "persona", Role.CAJA, "12345678", "12345678"))));
        }

        @Test
        void contrasenaCorta_oConfirmacionDistinta_seRechazan() {
            assertEquals(Map.of("password", "La contraseña debe tener al menos 8 caracteres."),
                    errors(() -> create.execute(admin, newUser("Persona", "persona", Role.CAJA, "1234567", "1234567"))));
            assertEquals(Map.of("passwordConfirmation", "Las contraseñas no coinciden."),
                    errors(() -> create.execute(admin, newUser("Persona", "persona", Role.CAJA, "12345678", "12345679"))));
        }

        @Test
        void laContrasena_noSeRecorta() {
            UserRow row = create.execute(admin, newUser("Persona", "persona", Role.CAJA, " clave12 ", " clave12 "));
            assertEquals(hasher.hash(" clave12 "), users.hashOf(row.id()));
        }

        @Test
        void laContrasena_noApareceAlImprimirLosCommands() {
            assertFalse(newUser("P", "p", Role.CAJA, "Secreta#1", "Secreta#1").toString().contains("Secreta#1"));
            assertFalse(new UpdateUserCommand("P", "p", Role.CAJA, "Secreta#1", "Secreta#1").toString().contains("Secreta#1"));
        }
    }

    @Nested
    class Editar {
        @Test
        void cambiaLosDatos_conservaSiEstaActivo_yLaContrasenaSiVieneVacia() {
            User lucia = users.add(new User(null, "Lucía Pérez", "lucia", Role.CAJA, false), hasher.hash("Mostrador#2026"));

            UserRow row = update.execute(admin, lucia.id(), new UpdateUserCommand("Lucía P.", "LPerez", Role.RECEPCION, "", null));

            assertEquals(new UserRow(lucia.id(), "Lucía P.", "lperez", Role.RECEPCION, false), row);
            assertEquals(new User(lucia.id(), "Lucía P.", "lperez", Role.RECEPCION, false), users.get(lucia.id()));
            assertEquals(hasher.hash("Mostrador#2026"), users.hashOf(lucia.id()));
        }

        @Test
        void unaContrasenaNueva_cambiaElHash() {
            User lucia = lucia();
            update.execute(admin, lucia.id(), new UpdateUserCommand("Lucía Pérez", "lucia", Role.CAJA, "Nueva#2026", "Nueva#2026"));
            assertEquals(hasher.hash("Nueva#2026"), users.hashOf(lucia.id()));
        }

        @Test
        void unaContrasenaNuevaCorta_oSinConfirmar_seRechaza() {
            User lucia = lucia();
            assertEquals(Map.of("newPassword", "La contraseña debe tener al menos 8 caracteres."), errors(() ->
                    update.execute(admin, lucia.id(), new UpdateUserCommand("Lucía", "lucia", Role.CAJA, "corta", "corta"))));
            assertEquals(Map.of("newPasswordConfirmation", "Las contraseñas no coinciden."), errors(() ->
                    update.execute(admin, lucia.id(), new UpdateUserCommand("Lucía", "lucia", Role.CAJA, "Nueva#2026", null))));
        }

        @Test
        void conservarSuUsuario_sePermite_peroNoTomarElDeOtro() {
            User lucia = lucia();
            update.execute(admin, lucia.id(), new UpdateUserCommand("Lucía", "LUCIA", Role.CAJA, null, null));
            assertEquals(Map.of("username", "Ese nombre de usuario ya está en uso."), errors(() ->
                    update.execute(admin, lucia.id(), new UpdateUserCommand("Lucía", "ana", Role.CAJA, null, null))));
        }

        @Test
        void alUltimoAdministradorActivo_noSeLeQuitaElRol_aunqueHayaOtroInactivo() {
            users.add(new User(null, "Beto Admin", "beto", Role.ADMINISTRADOR, false), "h");

            assertEquals(Map.of("role", "Debe quedar al menos un administrador activo."), errors(() ->
                    update.execute(admin, admin.id(), new UpdateUserCommand("Ana Admin", "ana", Role.CAJA, null, null))));
            assertEquals(Role.ADMINISTRADOR, users.get(admin.id()).role());
        }

        @Test
        void conOtroAdministradorActivo_siSePuedeQuitarElRol() {
            users.add(new User(null, "Beto Admin", "beto", Role.ADMINISTRADOR, true), "h");
            assertEquals(Role.CAJA, update.execute(admin, admin.id(),
                    new UpdateUserCommand("Ana Admin", "ana", Role.CAJA, null, null)).role());
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            NotFoundException e = assertThrows(NotFoundException.class,
                    () -> update.execute(admin, 99, new UpdateUserCommand("X", "xyz", Role.CAJA, null, null)));
            assertEquals("El usuario ya no existe. Recarga la pantalla.", e.getMessage());
        }
    }

    @Nested
    class ActivarYDesactivar {
        @Test
        void desactivaYReactiva() {
            User lucia = lucia();
            assertFalse(setActive.execute(admin, lucia.id(), false).active());
            assertFalse(users.get(lucia.id()).active());
            assertTrue(setActive.execute(admin, lucia.id(), true).active());
        }

        @Test
        void unoMismo_noSePuedeDesactivar() {
            users.add(new User(null, "Beto Admin", "beto", Role.ADMINISTRADOR, true), "h");
            DomainException e = assertThrows(DomainException.class, () -> setActive.execute(admin, admin.id(), false));
            assertEquals("No puedes desactivar tu propio usuario mientras lo usas.", e.getMessage());
        }

        @Test
        void siYaEstaEnEseEstado_noEscribe() {
            User lucia = lucia();
            assertTrue(setActive.execute(admin, lucia.id(), true).active());
            assertEquals(0, users.writes);
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            assertThrows(NotFoundException.class, () -> setActive.execute(admin, 99, false));
        }
    }

    @Nested
    class Eliminar {
        @Test
        void unUsuarioSinRegistros_seBorra() {
            User lucia = lucia();
            delete.execute(admin, lucia.id());
            assertNull(users.get(lucia.id()));
        }

        @Test
        void conRegistros_noSeBorra_ySePideDesactivarlo() {
            User lucia = lucia();
            users.markWithOperations(lucia.id());

            DomainException e = assertThrows(DomainException.class, () -> delete.execute(admin, lucia.id()));

            assertEquals("No puedes eliminar a «Lucía Pérez»: ya tiene cobros, ventas o movimientos registrados. "
                    + "Desactívalo para que no pueda entrar.", e.getMessage());
            assertEquals(lucia, users.get(lucia.id()));
        }

        @Test
        void unoMismo_noSeBorra() {
            DomainException e = assertThrows(DomainException.class, () -> delete.execute(admin, admin.id()));
            assertEquals("No puedes eliminar tu propio usuario mientras lo usas.", e.getMessage());
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            assertThrows(NotFoundException.class, () -> delete.execute(admin, 99));
        }
    }

    @Test
    void losCasosQueEscriben_leenYEscribenSoloDentroDeSuTransaccion() {
        users.guard.watch(transactions);
        UserRow row = create.execute(admin, newUser("Pedro", "pedro", Role.CAJA, "12345678", "12345678"));
        update.execute(admin, row.id(), new UpdateUserCommand("Pedro Ruiz", "pedro", Role.CAJA, null, null));
        setActive.execute(admin, row.id(), false);
        delete.execute(admin, row.id());
        assertEquals(4, transactions.started);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RECEPCION", "CAJA"})
    void soloElAdministrador_administraUsuarios(Role role) {
        User user = users.add(new User(null, "Persona", "persona", role, true), "h");
        User lucia = lucia();

        assertThrows(DomainException.class, () -> list.execute(user));
        assertThrows(DomainException.class,
                () -> create.execute(user, newUser("Otro", "otro", Role.CAJA, "12345678", "12345678")));
        assertThrows(DomainException.class,
                () -> update.execute(user, lucia.id(), new UpdateUserCommand("X", "xyz", Role.CAJA, null, null)));
        assertThrows(DomainException.class, () -> setActive.execute(user, lucia.id(), false));
        assertThrows(DomainException.class, () -> delete.execute(user, lucia.id()));
        assertEquals(0, users.writes);
    }
}
