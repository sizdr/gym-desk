package dev.gimnasio.sistemagimnasio.application.usecase.authenticate;

import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.fakes.ReversingHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.UserCredentials;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthenticateTest {

    private final ReversingHasher hasher = new ReversingHasher();
    private final InMemoryUsers users = new InMemoryUsers();
    private final Authenticate authenticate = new Authenticate(users, hasher);

    private User register(String username, String password, Role role, boolean active) {
        User user = new User((long) (users.size() + 1), "Persona " + username, username, role, active);
        users.add(user, hasher.hash(password));
        return user;
    }

    private String rejection(String username, String password) {
        return assertThrows(AuthenticationException.class,
                () -> authenticate.execute(new AuthenticateCommand(username, password))).getMessage();
    }


    @Test
    void usuarioYContrasenaCorrectos_devuelveAlUsuarioConSuRol() {
        User caja = register("lucia", "Mostrador#2026", Role.CAJA, true);
        register("pedro", "Recepcion#2026", Role.RECEPCION, true);

        User result = authenticate.execute(new AuthenticateCommand("lucia", "Mostrador#2026"));

        assertEquals(caja, result);
        assertEquals(Role.CAJA, result.role());
    }

    @Test
    void espaciosAlrededorDelUsuario_seIgnoran() {
        User admin = register("admin", "Gym-Admin-1", Role.ADMINISTRADOR, true);
        assertEquals(admin, authenticate.execute(new AuthenticateCommand("  admin \t", "Gym-Admin-1")));
    }


    @Test
    void contrasenaIncorrecta_seRechazaConMensajeClaro() {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        assertEquals("Usuario o contraseña incorrectos. Revísalos e inténtalo de nuevo.",
                rejection("lucia", "mostrador#2026"));
    }

    /** Así no se puede averiguar qué usuarios existen probando nombres. */
    @Test
    void usuarioInexistente_yContrasenaIncorrecta_danExactamenteElMismoMensaje() {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        assertEquals(rejection("lucia", "otra"), rejection("nadie", "otra"));
    }

    @ParameterizedTest
    @CsvSource({"' Mostrador#2026'", "'Mostrador#2026 '"})
    void contrasenaConEspaciosDeMas_noEsLaMisma(String password) {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        assertEquals(Authenticate.INVALID_CREDENTIALS, rejection("lucia", password));
    }

    @Test
    void contrasenaQueContieneEspacios_funcionaSiSeEscribeIgual() {
        User user = register("pedro", "mi clave segura", Role.RECEPCION, true);
        assertEquals(user, authenticate.execute(new AuthenticateCommand("pedro", "mi clave segura")));
    }

    @Test
    void escribirElHashGuardado_noDaAcceso() {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        String storedHash = users.findCredentialsByUsername("lucia").orElseThrow().passwordHash();
        assertEquals(Authenticate.INVALID_CREDENTIALS, rejection("lucia", storedHash));
    }

    private Map<String, String> missing(String username, String password) {
        return assertThrows(ValidationException.class,
                () -> authenticate.execute(new AuthenticateCommand(username, password))).errors();
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''", "'   '"}, nullValues = "NULL")
    void usuarioVacio_sePideEnSuCampoSinConsultarAlRepositorio(String username) {
        assertEquals(Map.of("username", Authenticate.MISSING_USERNAME), missing(username, "clave"));
        assertEquals(0, users.lookups);
    }

    @ParameterizedTest
    @CsvSource(value = {"NULL", "''"}, nullValues = "NULL")
    void contrasenaVacia_sePideEnSuCampoSinConsultarAlRepositorio(String password) {
        assertEquals(Map.of("password", Authenticate.MISSING_PASSWORD), missing("lucia", password));
        assertEquals(0, users.lookups);
    }

    @Test
    void ambosVacios_sePidenJuntos() {
        assertEquals(List.of("username", "password"), List.copyOf(missing(" ", "").keySet()));
    }


    @Test
    void usuarioDesactivadoConContrasenaCorrecta_noEntra_ySeLeDiceElMotivo() {
        register("pedro", "Recepcion#2026", Role.ADMINISTRADOR, false);
        assertEquals("Tu usuario está desactivado. Pide a un administrador que lo active.",
                rejection("pedro", "Recepcion#2026"));
    }

    /** Sin la contraseña correcta no se revela que la cuenta existe. */
    @Test
    void usuarioDesactivadoConContrasenaIncorrecta_recibeElMensajeGenerico() {
        register("pedro", "Recepcion#2026", Role.RECEPCION, false);
        assertEquals(Authenticate.INVALID_CREDENTIALS, rejection("pedro", "otra"));
    }


    @Test
    void laContrasenaNoApareceAlImprimirElComandoNiLasCredenciales() {
        AuthenticateCommand command = new AuthenticateCommand("lucia", "Mostrador#2026");
        UserCredentials credentials = new UserCredentials(
                new User(1L, "Lucía", "lucia", Role.CAJA, true), hasher.hash("Mostrador#2026"));

        assertFalse(command.toString().contains("Mostrador#2026"), command.toString());
        assertFalse(credentials.toString().contains(credentials.passwordHash()), credentials.toString());
    }

    @Test
    void elMensajeDeRechazo_noIncluyeLaContrasenaEscrita() {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        assertFalse(rejection("lucia", "MiClaveFiltrada").contains("MiClaveFiltrada"));
    }


    /** Un fallo técnico no debe disfrazarse de contraseña incorrecta. */
    @Test
    void siFallaLaVerificacionDelHash_elErrorSePropagaSinConvertirseEnRechazo() {
        IllegalStateException failure = new IllegalStateException("hash ilegible");
        PasswordHasher broken = new PasswordHasher() {
            @Override
            public String hash(String rawPassword) {
                throw failure;
            }

            @Override
            public boolean matches(String rawPassword, String passwordHash) {
                throw failure;
            }
        };
        users.add(new User(1L, "Lucía", "lucia", Role.CAJA, true), "dañado");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
                () -> new Authenticate(users, broken).execute(new AuthenticateCommand("lucia", "Mostrador#2026")));
        assertSame(failure, thrown);
    }
}
