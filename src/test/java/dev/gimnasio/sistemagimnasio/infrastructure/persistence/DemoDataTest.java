package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRows;
import dev.gimnasio.sistemagimnasio.application.usecase.summary.DaySummary;
import dev.gimnasio.sistemagimnasio.application.usecase.summary.GetDaySummary;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * scripts/demo/datos-demo.sql sobre una base migrada como la de la app: los estados, ingresos, folios y tipos de
 * cobro que promete su cabecera salen igual con el código de la app, y se niega a cargarse donde no debe.
 */
@Testcontainers(disabledWithoutDocker = true)
class DemoDataTest {
    private static final Path SCRIPT = Path.of("scripts/demo/datos-demo.sql");

    private HikariDataSource dataSource;
    private Jdbi jdbi;

    @AfterEach
    void close() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /** Una base nueva migrada por la app, con las cuentas dadas ({@code rol:usuario}). */
    private void demoDatabase(String name, String... accounts) {
        close();
        dataSource = Database.connect(TestDatabase.newEmptyDatabase(name));
        Database.migrate(dataSource);
        jdbi = Jdbi.create(dataSource);
        for (String account : accounts) {
            String[] parts = account.split(":");
            jdbi.useHandle(h -> h.execute("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) "
                    + "VALUES (?, ?, 'hash', CAST(? AS rol_usuario))", "Cuenta " + parts[1], parts[1], parts[0]));
        }
    }

    /** Todo el archivo de una vez, como lo manda psql; la sesión usa la zona del equipo, como el script. */
    private static void load(Jdbi jdbi, String... before) {
        String sql;
        try {
            sql = Files.readString(SCRIPT);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        jdbi.useHandle(h -> {
            for (String statement : before) {
                h.execute(statement);
            }
            try (var statement = h.getConnection().createStatement()) {
                statement.execute(sql);
            } catch (java.sql.SQLException e) {
                // El BEGIN del script dejó la transacción abortada: se cierra antes de devolver la conexión.
                h.execute("ROLLBACK");
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
    }

    private Map<MemberStatus, List<String>> byStatus() {
        LocalDate today = LocalDate.now();
        List<MemberRow> rows = MemberRows.all(new PostgresMemberRepository(jdbi), new PostgresMembershipRepository(jdbi),
                new PostgresPlanRepository(jdbi), new PostgresSettingsRepository(jdbi).get(), today);
        Map<MemberStatus, List<String>> grouped = new EnumMap<>(MemberStatus.class);
        rows.stream().sorted(java.util.Comparator.comparing(MemberRow::fullName))
                .forEach(row -> grouped.computeIfAbsent(row.status(), s -> new java.util.ArrayList<>()).add(row.fullName()));
        return grouped;
    }

    private DaySummary summary() {
        var users = new PostgresUserRepository(jdbi);
        User admin = users.findAll().stream().filter(u -> u.role().name().equals("ADMINISTRADOR")).findFirst()
                .orElseThrow();
        return new GetDaySummary(new PostgresMemberRepository(jdbi), new PostgresMembershipRepository(jdbi),
                new PostgresPlanRepository(jdbi), new PostgresSettingsRepository(jdbi),
                new PostgresMembershipPaymentRepository(jdbi), new PostgresPaymentMethodRepository(jdbi), users,
                new Authorizer(users), Clock.systemDefaultZone()).execute(admin);
    }

    private <T> T query(String sql, Class<T> type) {
        return jdbi.withHandle(h -> h.createQuery(sql).mapTo(type).one());
    }

    @Test
    void dejaLosEstadosQuePromete_conCobrosDeHoy_yLosTiposCorrectos() {
        demoDatabase("gimnasio_demo_prueba", "administrador:ana", "recepcion:rocio", "caja:carlos");
        load(jdbi);

        Map<MemberStatus, List<String>> status = byStatus();
        assertEquals(List.of("Ana López", "Carla Ruiz", "Gabriela Paz", "Héctor Ríos", "Karla Núñez", "Lucía Pérez",
                "Mario Castro", "Nora Silva", "Óscar Ibarra"), status.get(MemberStatus.VIGENTE));
        assertEquals(List.of("Bruno Díaz", "Irene Vega", "Pablo Reyes", "Rosa Jiménez", "Sergio Vargas",
                "Yolanda Ortiz"), status.get(MemberStatus.POR_VENCER));
        assertEquals(List.of("Daniel Soto", "Jorge Luna", "Teresa Campos", "Ulises Mejía"), status.get(MemberStatus.VENCIDA));
        assertEquals(List.of("Elena Mora", "Víctor Hernández"), status.get(MemberStatus.BAJA));
        assertEquals(List.of("Fernando Gil", "Wendy Torres"), status.get(MemberStatus.SIN_MEMBRESIA));

        DaySummary day = summary();
        assertEquals(15, day.activeMembers());
        assertEquals(3, day.paymentsToday());
        assertEquals(0, new BigDecimal("1710.00").compareTo(day.membershipIncomeToday().amount()), "450 + 1200 + 60");
        assertEquals(8, day.recentPayments().size());
        PaymentRow latest = day.recentPayments().getFirst();
        assertEquals("GYM-0025", latest.folio(), "los folios siguen el orden en que se cobró");
        assertEquals(LocalDate.now(), latest.dateTime().toLocalDate());

        assertEquals(25, query("SELECT ultimo FROM folio_secuencia WHERE serie = 'COBRO'", Integer.class));
        assertEquals("GYM-0001", query("SELECT folio FROM pago ORDER BY fecha_hora LIMIT 1", String.class));
        Map<String, Integer> kinds = jdbi.withHandle(h -> h.createQuery(
                        "SELECT tipo_cobro::text AS tipo, COUNT(*) AS n FROM pago GROUP BY tipo_cobro")
                .map((rs, ctx) -> Map.entry(rs.getString("tipo"), rs.getInt("n"))).list())
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        assertEquals(Map.of("primera", 21, "renovacion_anticipada", 2, "renovacion_vencida", 1, "reactivacion", 1), kinds);
        assertEquals(0, query("SELECT COUNT(*) FROM pago p WHERE NOT EXISTS "
                + "(SELECT 1 FROM membresia m WHERE m.pago_id = p.id)", Integer.class), "cada cobro con su membresía");
        assertEquals(0, query("SELECT COUNT(*) FROM socio s WHERE fecha_alta > "
                + "(SELECT MIN(fecha_inicio) FROM membresia m WHERE m.socio_id = s.id)", Integer.class),
                "nadie empieza antes de su alta");
        Map<String, String> inDatabase = jdbi.withHandle(h -> h.createQuery(
                        "SELECT nombre, fn_estado_socio(id) AS estado FROM socio")
                .map((rs, ctx) -> Map.entry(rs.getString("nombre"), rs.getString("estado"))).list())
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        status.forEach((state, names) -> names.forEach(name ->
                assertEquals(state.name(), inDatabase.get(name), "fn_estado_socio de " + name)));
        assertEquals("Gimnasio Fuerza", query("SELECT nombre_gimnasio FROM configuracion", String.class));
        assertEquals(3L, query("SELECT COUNT(DISTINCT usuario_id) FROM pago", Long.class), "cada rol cobra lo suyo");
    }

    @Test
    void soloConElAdministrador_todosLosCobrosSonSuyos() {
        demoDatabase("gimnasio_demo_admin", "administrador:admin");
        load(jdbi);
        assertEquals(25, query("SELECT COUNT(*) FROM pago p JOIN usuario u ON u.id = p.usuario_id "
                + "WHERE u.nombre_usuario = 'admin'", Integer.class));
    }

    @Test
    void seNiega_sinAdministrador_conCobros_oEnUnaBaseQueNoEsDeDemostracion() {
        demoDatabase("gimnasio_demo_sin_admin");
        assertTrue(assertThrows(IllegalStateException.class, () -> load(jdbi)).getMessage()
                .contains("Falta un administrador activo"));
        assertEquals(0, query("SELECT COUNT(*) FROM socio", Integer.class), "no deja nada a medias");

        demoDatabase("gimnasio_demo_dos_veces", "administrador:admin");
        load(jdbi);
        assertTrue(assertThrows(IllegalStateException.class, () -> load(jdbi)).getMessage()
                .contains("La base ya tiene cobros"));
        assertEquals(23, query("SELECT COUNT(*) FROM socio", Integer.class));

        demoDatabase("gimnasio_trabajo", "administrador:admin");
        assertTrue(assertThrows(IllegalStateException.class, () -> load(jdbi)).getMessage()
                .contains("no es de demostración"));
        assertEquals(0, query("SELECT COUNT(*) FROM plan_membresia", Integer.class));
    }
}
