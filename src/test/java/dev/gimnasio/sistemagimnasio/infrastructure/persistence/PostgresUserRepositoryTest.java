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

import java.util.List;
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

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    @Test
    void listaCuentaYCuentaAdministradoresActivos() {
        long ana = insert("Ana", "ana", "administrador", true, "h1");
        insert("Beto", "beto", "administrador", false, "h2");
        insert("Lucía", "lucia", "caja", true, "h3");

        assertEquals(3, repository.count());
        assertEquals(1, repository.countActiveAdmins());
        assertEquals(List.of(ana), repository.findAll().stream().filter(u -> u.role() == Role.ADMINISTRADOR && u.active())
                .map(User::id).toList());
    }

    @Test
    void existePorUsuario_sinDistinguirMayusculas_yExcluyendoAlPropio() {
        long id = insert("Lucía", "lucia", "caja", true, "h");

        assertTrue(repository.existsByUsername("LUCIA", null));
        assertFalse(repository.existsByUsername("lucia", id));
        assertTrue(repository.existsByUsername("lucia", id + 1));
        assertFalse(repository.existsByUsername("pedro", null));
    }

    @Test
    void insertar_guardaElUsuarioYSuHash() {
        User created = repository.insert(new User(null, "Pedro Ruiz", "pedro", Role.RECEPCION, true), "hash-de-pedro");

        assertEquals(Optional.of(new UserCredentials(new User(created.id(), "Pedro Ruiz", "pedro", Role.RECEPCION, true),
                "hash-de-pedro")), repository.findCredentialsByUsername("pedro"));
    }

    @Test
    void actualizar_cambiaDatosYRol_yAparteElHash() {
        long id = insert("Pedro", "pedro", "recepcion", true, "viejo");

        repository.update(new User(id, "Pedro Ruiz", "pruiz", Role.CAJA, false));
        repository.updatePasswordHash(id, "nuevo");

        assertEquals(Optional.of(new UserCredentials(new User(id, "Pedro Ruiz", "pruiz", Role.CAJA, false), "nuevo")),
                repository.findCredentialsByUsername("pruiz"));
    }

    @Test
    void actualizarUnoQueNoExiste_falla() {
        assertThrows(IllegalStateException.class, () -> repository.update(new User(99L, "X", "xyz", Role.CAJA, true)));
        assertThrows(IllegalStateException.class, () -> repository.updatePasswordHash(99L, "h"));
    }

    @Test
    void sabeSiUnUsuarioTieneCobrosVentasOMovimientos() {
        long cobra = insert("Ana", "ana", "caja", true, "h");
        long vende = insert("Beto", "beto", "caja", true, "h");
        long mueve = insert("Caro", "caro", "recepcion", true, "h");
        long nuevo = insert("Dani", "dani", "caja", true, "h");
        long socio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Luis', '5512345678', '2026-11-01')");
        long metodo = id("INSERT INTO metodo_pago (nombre) VALUES ('Tarjeta')");
        long categoria = id("INSERT INTO categoria_producto (nombre) VALUES ('Bebidas')");
        long producto = id("INSERT INTO producto (clave, nombre, categoria_id, precio, minimo, existencia) "
                + "VALUES ('AGUA', 'Agua', " + categoria + ", 18, 1, 10)");
        long motivo = id("INSERT INTO motivo_movimiento (nombre) VALUES ('Compra')");
        id("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto) VALUES ('GYM-0001', "
                + socio + ", " + cobra + ", " + metodo + ", 450)");
        id("INSERT INTO venta (folio, metodo_pago_id, total, usuario_id) VALUES ('GYM-V-0001', " + metodo + ", 18, " + vende + ")");
        id("INSERT INTO movimiento_inventario (producto_id, tipo, cantidad, existencia_resultante, motivo_movimiento_id, usuario_id) "
                + "VALUES (" + producto + ", 'entrada', 5, 15, " + motivo + ", " + mueve + ")");

        assertTrue(repository.hasOperations(cobra));
        assertTrue(repository.hasOperations(vende));
        assertTrue(repository.hasOperations(mueve));
        assertFalse(repository.hasOperations(nuevo));
    }

    @Test
    void borrar_quitaSoloEseUsuario() {
        long ana = insert("Ana", "ana", "caja", true, "h");
        long beto = insert("Beto", "beto", "caja", true, "h");

        repository.delete(ana);

        assertEquals(Optional.empty(), repository.findById(ana));
        assertEquals(List.of(beto), repository.findAll().stream().map(User::id).toList());
    }
}
