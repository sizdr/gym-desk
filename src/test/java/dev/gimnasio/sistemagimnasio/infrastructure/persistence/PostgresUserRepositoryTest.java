package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.UserCredentials;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.Authenticate;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.AuthenticateCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.AuthenticationException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.infrastructure.security.Argon2PasswordHasher;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;

import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.INVALID_ENUM_VALUE;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.UNIQUE_VIOLATION;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.assertRejected;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresUserRepositoryTest {
    private Jdbi jdbi;
    private PostgresUserRepository repository;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        repository = new PostgresUserRepository(jdbi);
    }

    private long insert(String name, String username, String role, boolean active, String passwordHash) {
        return jdbi.withHandle(handle -> handle.createUpdate("""
                        INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol, activo)
                        VALUES (:nombre, :usuario, :hash, CAST(:rol AS rol_usuario), :activo)""")
                .bind("nombre", name).bind("usuario", username).bind("hash", passwordHash)
                .bind("rol", role).bind("activo", active)
                .executeAndReturnGeneratedKeys("id").mapTo(Long.class).one());
    }

    @Test
    void encuentraAlUsuario_conTodosSusDatosYSuHash() {
        long id = insert("Lucía Pérez", "lucia", "caja", true, "hash-de-lucia");
        insert("Pedro Ruiz", "pedro", "recepcion", false, "hash-de-pedro");

        assertEquals(Optional.of(new UserCredentials(new User(id, "Lucía Pérez", "lucia", Role.CAJA, true), "hash-de-lucia")),
                repository.findCredentialsByUsername("lucia"));
    }

    @Test
    void cadaRolDeLaBase_correspondeASuRolDelDominio() {
        insert("Ana", "ana", "administrador", true, "h1");
        insert("Pedro", "pedro", "recepcion", true, "h2");
        insert("Lucía", "lucia", "caja", true, "h3");

        assertEquals(Role.ADMINISTRADOR, repository.findCredentialsByUsername("ana").orElseThrow().user().role());
        assertEquals(Role.RECEPCION, repository.findCredentialsByUsername("pedro").orElseThrow().user().role());
        assertEquals(Role.CAJA, repository.findCredentialsByUsername("lucia").orElseThrow().user().role());
    }

    @Test
    void porId_devuelveAlUsuarioSinSuHash_oVacioSiNoExiste() {
        long id = insert("Lucía Pérez", "lucia", "caja", false, "hash-de-lucia");

        assertEquals(Optional.of(new User(id, "Lucía Pérez", "lucia", Role.CAJA, false)), repository.findById(id));
        assertEquals(Optional.empty(), repository.findById(id + 1));
    }

    @Test
    void usuarioDesactivado_seDevuelveIgual_paraQueElCasoDeUsoDecida() {
        insert("Pedro Ruiz", "pedro", "recepcion", false, "hash-de-pedro");
        assertFalse(repository.findCredentialsByUsername("pedro").orElseThrow().user().active());
    }

    @Test
    void usuarioInexistente_devuelveVacio() {
        insert("Lucía Pérez", "lucia", "caja", true, "hash-de-lucia");
        assertEquals(Optional.empty(), repository.findCredentialsByUsername("nadie"));
    }

    @Test
    void laBusqueda_noDistingueMayusculas() {
        insert("Lucía Pérez", "Lucia", "caja", true, "hash-de-lucia");
        assertEquals("lucia", repository.findCredentialsByUsername("LUCIA").orElseThrow().user().username());
    }

    @Test
    void nombreDeUsuarioRepetido_aunqueCambienLasMayusculas_loRechazaLaBase() {
        insert("Lucía Pérez", "lucia", "caja", true, "hash-1");
        assertRejected(UNIQUE_VIOLATION, () -> insert("Lucía Gómez", "LUCIA", "recepcion", true, "hash-2"));
    }

    @Test
    void rolDesconocido_loRechazaLaBase() {
        assertRejected(INVALID_ENUM_VALUE, () -> insert("Ana", "ana", "gerente", true, "hash"));
    }

    @Test
    void migrarOtraVez_noFallaNiDuplicaNada() {
        insert("Lucía Pérez", "lucia", "caja", true, "hash-de-lucia");
        assertDoesNotThrow(() -> Database.migrate(TestDatabase.dataSource()));
        assertTrue(repository.findCredentialsByUsername("lucia").isPresent());
    }

    @Test
    void autenticacionCompleta_conPostgresYArgon2() {
        Argon2PasswordHasher hasher = new Argon2PasswordHasher();
        long id = insert("Lucía Pérez", "lucia", "caja", true, hasher.hash("Mostrador#2026"));
        Authenticate authenticate = new Authenticate(repository, hasher);

        assertEquals(id, authenticate.execute(new AuthenticateCommand("lucia", "Mostrador#2026")).id());
        assertThrows(AuthenticationException.class,
                () -> authenticate.execute(new AuthenticateCommand("lucia", "otra")));
    }
}
