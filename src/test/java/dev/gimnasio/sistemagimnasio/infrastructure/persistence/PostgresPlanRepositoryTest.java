package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPlanRepositoryTest {
    private Jdbi jdbi;
    private PostgresPlanRepository repository;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        repository = new PostgresPlanRepository(jdbi);
    }

    private Plan insert(String name, int days, String price, Long reference) {
        return repository.insert(new Plan(null, name, days, Money.of(price), reference, true));
    }

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    @Test
    void insertar_asignaElId_yGuardaTodosLosCampos() {
        Plan mensual = insert("Mensual", 30, "450", null);
        Plan anual = insert("Anual", 365, "3800.50", mensual.id());

        assertEquals(Optional.of(mensual), repository.findById(mensual.id()));
        assertEquals(Optional.of(new Plan(anual.id(), "Anual", 365, Money.of("3800.50"), mensual.id(), true)),
                repository.findById(anual.id()));
        assertEquals(Optional.empty(), repository.findById(anual.id() + 1));
        assertEquals(Set.of(mensual, anual), Set.copyOf(repository.findAll()));
    }

    @Test
    void actualizar_cambiaTodosLosCampos_incluidaLaReferencia() {
        Plan mensual = insert("Mensual", 30, "450", null);
        Plan anual = insert("Anual", 365, "3800", mensual.id());

        repository.update(new Plan(anual.id(), "Anual plus", 360, Money.of("4000"), null, false));

        assertEquals(Optional.of(new Plan(anual.id(), "Anual plus", 360, Money.of("4000"), null, false)),
                repository.findById(anual.id()));
    }

    @Test
    void actualizarUnoQueNoExiste_falla() {
        assertThrows(IllegalStateException.class,
                () -> repository.update(new Plan(99L, "X", 30, Money.of("1"), null, true)));
    }

    @Test
    void existePorNombre_sinDistinguirMayusculas_yExcluyendoAlPropio() {
        Plan mensual = insert("Mensual", 30, "450", null);

        assertTrue(repository.existsByName("MENSUAL", null));
        assertFalse(repository.existsByName("mensual", mensual.id()));
        assertTrue(repository.existsByName("mensual", mensual.id() + 1));
        assertFalse(repository.existsByName("Anual", null));
    }

    @Test
    void sabeSiUnPlanYaTieneMembresias() {
        Plan mensual = insert("Mensual", 30, "450", null);
        Plan anual = insert("Anual", 365, "3800", null);
        long usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Ana', 'ana', 'h', 'caja')");
        long socio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Luis', '5512345678', '2026-11-01')");
        long metodo = id("INSERT INTO metodo_pago (nombre) VALUES ('Tarjeta')");
        long pago = id("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) VALUES ('GYM-0001', "
                + socio + ", " + usuario + ", " + metodo + ", 450, 'primera')");
        jdbi.useHandle(handle -> handle.execute("INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, fecha_vencimiento) "
                + "VALUES (" + socio + ", " + mensual.id() + ", " + pago + ", '2026-11-01', '2026-12-01')"));

        assertTrue(repository.hasMemberships(mensual.id()));
        assertFalse(repository.hasMemberships(anual.id()));
    }

    @Test
    void nombresDeLosPlanesQueLoUsanComoReferencia_enOrden() {
        Plan mensual = insert("Mensual", 30, "450", null);
        insert("Trimestral", 90, "1200", mensual.id());
        insert("anual", 365, "3800", mensual.id());
        insert("Semestral", 180, "2200", null);

        assertEquals(List.of("anual", "Trimestral"), repository.namesReferencing(mensual.id()));
    }

    @Test
    void borrar_quitaSoloEsePlan() {
        Plan mensual = insert("Mensual", 30, "450", null);
        Plan anual = insert("Anual", 365, "3800", null);

        repository.delete(mensual.id());

        assertEquals(Optional.empty(), repository.findById(mensual.id()));
        assertEquals(Set.of(anual.id()), repository.findAll().stream().map(Plan::id).collect(Collectors.toSet()));
    }
}
