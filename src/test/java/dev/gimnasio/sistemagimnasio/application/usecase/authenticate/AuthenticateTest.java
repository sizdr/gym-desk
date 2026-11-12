package dev.gimnasio.sistemagimnasio.application.usecase.authenticate;

import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.UserCredentials;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticateTest {

    /** Hash falso pero distinto de la contraseña, para detectar comparaciones en claro. */
    private static final class FakeHasher implements PasswordHasher {
        int hashes;
        final List<String> verifiedAgainst = new ArrayList<>();

        @Override
        public String hash(String rawPassword) {
            hashes++;
            return encode(rawPassword);
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            verifiedAgainst.add(passwordHash);
            return encode(rawPassword).equals(passwordHash);
        }

        private static String encode(String rawPassword) {
            return "hash(" + new StringBuilder(rawPassword).reverse() + ")";
        }
    }

    private static final class InMemoryUsers implements UserRepository {
        private final Map<String, UserCredentials> byUsername = new HashMap<>();
        int lookups;

        void add(User user, String passwordHash) {
            byUsername.put(user.username(), new UserCredentials(user, passwordHash));
        }

        @Override
        public Optional<UserCredentials> findCredentialsByUsername(String username) {
            lookups++;
            return Optional.ofNullable(byUsername.get(username));
        }

        @Override
        public Optional<User> findById(long id) {
            return byUsername.values().stream().map(UserCredentials::user)
                    .filter(user -> user.id() == id).findFirst();
        }
    }

    private final FakeHasher hasher = new FakeHasher();
    private final InMemoryUsers users = new InMemoryUsers();
    private final Authenticate authenticate = new Authenticate(users, hasher);

    private User register(String username, String password, Role role, boolean active) {
        User user = new User((long) (users.byUsername.size() + 1), "Persona " + username, username, role, active);
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

        assertSame(caja, result);
        assertEquals(Role.CAJA, result.role());
    }

    @Test
    void espaciosAlrededorDelUsuario_seIgnoran() {
        User admin = register("admin", "Gym-Admin-1", Role.ADMINISTRADOR, true);
        assertSame(admin, authenticate.execute(new AuthenticateCommand("  admin \t", "Gym-Admin-1")));
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

    /** Lo lento es verificar el hash: si solo se hiciera con usuarios existentes, el tiempo los delataría. */
    @Test
    void usuarioInexistente_tambienVerificaUnHash_igualQueUnoExistente() {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        hasher.verifiedAgainst.clear();

        rejection("lucia", "otra");
        rejection("nadie", "otra");

        assertEquals(2, hasher.verifiedAgainst.size(), "una verificación por intento, exista o no el usuario");
        assertEquals(hasher.hash(Authenticate.DUMMY_PASSWORD), hasher.verifiedAgainst.get(1));
    }

    @Test
    void elHashDeRelleno_seCalculaUnaVez_enElPrimerIntentoAunqueElUsuarioExista() {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        int before = hasher.hashes;

        authenticate.execute(new AuthenticateCommand("lucia", "Mostrador#2026"));
        rejection("nadie", "otra");
        rejection("nadie", "otra");

        assertEquals(before + 1, hasher.hashes);
    }

    @Test
    void laContrasenaDeRelleno_noDaAccesoConUnUsuarioInexistente() {
        assertEquals(Authenticate.INVALID_CREDENTIALS, rejection("nadie", Authenticate.DUMMY_PASSWORD));
    }

    @Test
    void camposVacios_noCalculanNiVerificanHashes() {
        missing("", "");
        assertEquals(0, hasher.hashes);
        assertTrue(hasher.verifiedAgainst.isEmpty());
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
        assertSame(user, authenticate.execute(new AuthenticateCommand("pedro", "mi clave segura")));
    }

    @Test
    void escribirElHashGuardado_noDaAcceso() {
        register("lucia", "Mostrador#2026", Role.CAJA, true);
        String storedHash = users.byUsername.get("lucia").passwordHash();
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
