package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.UNIQUE_VIOLATION;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.assertRejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPaymentMethodRepositoryTest {
    private PostgresPaymentMethodRepository repository;

    @BeforeEach
    void setUp() {
        TestDatabase.clean();
        repository = new PostgresPaymentMethodRepository(TestDatabase.jdbi());
    }

    private PaymentMethod insert(String name, boolean cash, boolean active) {
        return repository.insert(new PaymentMethod(null, name, cash, active));
    }

    @Test
    void insertar_asignaElId_yGuardaTodosLosCampos() {
        PaymentMethod efectivo = insert("Efectivo", true, true);

        assertEquals(Optional.of(efectivo), repository.findById(efectivo.id()));
        assertEquals(new PaymentMethod(efectivo.id(), "Efectivo", true, true), efectivo);
        assertEquals(Optional.empty(), repository.findById(efectivo.id() + 1));
    }

    @Test
    void listar_ordenaPorNombreSinDistinguirMayusculas_yFiltraInactivos() {
        insert("Tarjeta", false, true);
        insert("vales", false, false);
        insert("Efectivo", true, true);

        assertEquals(List.of("Efectivo", "Tarjeta"),
                repository.findAll(false).stream().map(PaymentMethod::name).toList());
        assertEquals(List.of("Efectivo", "Tarjeta", "vales"),
                repository.findAll(true).stream().map(PaymentMethod::name).toList());
    }

    @Test
    void existePorNombre_sinDistinguirMayusculas_yExcluyendoAlPropio() {
        PaymentMethod tarjeta = insert("Tarjeta", false, true);

        assertTrue(repository.existsByName("TARJETA", null));
        assertFalse(repository.existsByName("TARJETA", tarjeta.id()));
        assertTrue(repository.existsByName("tarjeta", tarjeta.id() + 1));
        assertFalse(repository.existsByName("Efectivo", null));
    }

    @Test
    void cuentaSoloLosActivos() {
        insert("Efectivo", true, true);
        insert("Tarjeta", false, true);
        insert("Vales", false, false);

        assertEquals(2, repository.countActive());
    }

    @Test
    void actualizar_cambiaNombreEfectivoYActivo() {
        PaymentMethod vales = insert("Vales", false, true);

        repository.update(new PaymentMethod(vales.id(), "Vales de despensa", true, false));

        assertEquals(Optional.of(new PaymentMethod(vales.id(), "Vales de despensa", true, false)),
                repository.findById(vales.id()));
    }

    @Test
    void actualizarUnoQueNoExiste_falla() {
        assertThrows(IllegalStateException.class, () -> repository.update(new PaymentMethod(99L, "X", false, true)));
    }

    private long id(String insert) {
        return TestDatabase.jdbi().withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    @Test
    void sabeSiUnMetodoSeUsoEnCobrosOVentas() {
        PaymentMethod efectivo = insert("Efectivo", true, true);
        PaymentMethod tarjeta = insert("Tarjeta", false, true);
        PaymentMethod vales = insert("Vales", false, true);
        long usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Ana', 'ana', 'h', 'caja')");
        long socio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Luis', '5512345678', '2026-11-01')");
        id("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) VALUES ('GYM-0001', "
                + socio + ", " + usuario + ", " + efectivo.id() + ", 450, 'primera')");
        id("INSERT INTO venta (folio, metodo_pago_id, total, usuario_id) VALUES ('GYM-V-0001', "
                + tarjeta.id() + ", 18.50, " + usuario + ")");

        assertTrue(repository.isUsed(efectivo.id()));
        assertTrue(repository.isUsed(tarjeta.id()));
        assertFalse(repository.isUsed(vales.id()));
    }

    @Test
    void borrar_quitaSoloEseMetodo() {
        PaymentMethod efectivo = insert("Efectivo", true, true);
        PaymentMethod vales = insert("Vales", false, true);

        repository.delete(vales.id());

        assertEquals(Optional.empty(), repository.findById(vales.id()));
        assertEquals(List.of(efectivo), repository.findAll(true));
    }

    @Test
    void nombreRepetido_conOtrasMayusculas_loRechazaLaBase() {
        insert("Efectivo", true, true);
        assertRejected(UNIQUE_VIOLATION, () -> insert("EFECTIVO", true, true));
    }
}
