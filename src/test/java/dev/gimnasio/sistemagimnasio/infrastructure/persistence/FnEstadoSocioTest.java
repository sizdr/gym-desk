package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.MembershipRules;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Control de vigencias (M02): la función {@code fn_estado_socio} de la base y las reglas de la app
 * ({@link MembershipRules}) dan el mismo estado en cada límite, con los plazos de la configuración.
 */
@Testcontainers(disabledWithoutDocker = true)
class FnEstadoSocioTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    /** Días del vencimiento respecto a hoy; null = nunca ha pagado. */
    private static final Integer[] OFFSETS = {null, -45, -31, -30, -29, -11, -10, -9, -1, 0, 1, 2, 3, 4, 6, 7, 8, 30};

    private Jdbi jdbi;
    private long usuario;
    private long plan;
    private long metodo;
    private int folio;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        jdbi.useHandle(handle -> handle.execute("INSERT INTO configuracion (nombre_gimnasio) VALUES ('Gym')"));
        usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Ana', 'ana', 'h', 'caja')");
        plan = id("INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('Mensual', 30, 450)");
        metodo = id("INSERT INTO metodo_pago (nombre, es_efectivo) VALUES ('Tarjeta', false)");
    }

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    /** Un socio cuya última membresía vence en {@code offset} días (con una anterior que no debe contar). */
    private long member(Integer offset) {
        long socio = id("INSERT INTO socio (nombre, telefono, fecha_alta) VALUES ('Socio " + offset + "', '55"
                + String.format("%08d", ++folio) + "', '2025-01-01')");
        if (offset != null) {
            LocalDate end = TODAY.plusDays(offset);
            membership(socio, end.minusDays(60), end.minusDays(30));
            membership(socio, end.minusDays(30), end);
        }
        return socio;
    }

    private void membership(long socio, LocalDate start, LocalDate end) {
        long pago = id("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, fecha_hora, tipo_cobro, "
                + "operacion_id) VALUES ('GYM-" + String.format("%04d", ++folio) + "', " + socio + ", " + usuario + ", "
                + metodo + ", 450, '" + start + " 10:00', 'primera', 'op-" + folio + "')");
        jdbi.useHandle(handle -> handle.execute("INSERT INTO membresia (socio_id, plan_id, pago_id, fecha_inicio, "
                + "fecha_vencimiento) VALUES (?, ?, ?, ?, ?)", socio, plan, pago, start, end));
    }

    private String inDatabase(long socio) {
        return jdbi.withHandle(handle -> handle.createQuery("SELECT fn_estado_socio(:socio, :fecha)")
                .bind("socio", socio).bind("fecha", TODAY).mapTo(String.class).one());
    }

    private void compare(int warningDays, int autoDeactivationDays) {
        jdbi.useHandle(handle -> handle.execute("UPDATE configuracion SET dias_aviso = ?, dias_baja_automatica = ?",
                warningDays, autoDeactivationDays));
        Map<Integer, String> app = new LinkedHashMap<>();
        Map<Integer, String> database = new LinkedHashMap<>();
        for (Integer offset : OFFSETS) {
            long socio = member(offset);
            Membership latest = offset == null ? null
                    : new Membership(1L, socio, plan, 1, TODAY.plusDays(offset - 30), TODAY.plusDays(offset));
            MemberStatus status = MembershipRules.statusOf(latest, TODAY, warningDays, autoDeactivationDays);
            app.put(offset, status.name());
            database.put(offset, inDatabase(socio));
        }
        assertEquals(app, database, "aviso " + warningDays + ", baja " + autoDeactivationDays);
    }

    @Test
    void conLosPlazosIniciales() {
        compare(7, 30);
    }

    @Test
    void conOtrosPlazos() {
        compare(3, 10);
    }
}
