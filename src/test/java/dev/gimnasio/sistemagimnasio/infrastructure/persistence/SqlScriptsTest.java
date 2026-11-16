package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import com.zaxxer.hikari.HikariDataSource;
import org.jdbi.v3.core.Handle;
import org.jdbi.v3.core.Jdbi;
import org.jdbi.v3.core.statement.StatementException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Los scripts del proyecto final (carpeta sql/): reconstruyen la base, sus consultas devuelven datos, y el
 * esquema que dejan es el mismo que dejan las migraciones de la aplicación. Si alguien cambia una migración
 * sin cambiar los scripts (o al revés), esta prueba falla.
 */
@Testcontainers(disabledWithoutDocker = true)
class SqlScriptsTest {
    private static final Path SQL = Path.of("sql");
    private static final Path MIGRATIONS = Path.of("src/main/resources/db/migration");

    /** Tablas, columnas, restricciones, índices, enumerados, funciones y triggers, uno por renglón. */
    private static final String DESCRIBE_SCHEMA = """
            SELECT 'columna ' || c.relname || '.' || a.attname || ' ' || format_type(a.atttypid, a.atttypmod)
                   || CASE WHEN a.attnotnull THEN ' not null' ELSE '' END
                   || COALESCE(' default ' || pg_get_expr(d.adbin, d.adrelid), '')
                   || CASE WHEN a.attidentity <> '' THEN ' identity ' || a.attidentity::text ELSE '' END
            FROM pg_class c
            JOIN pg_namespace n ON n.oid = c.relnamespace AND n.nspname = 'public'
            JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum > 0 AND NOT a.attisdropped
            LEFT JOIN pg_attrdef d ON d.adrelid = c.oid AND d.adnum = a.attnum
            WHERE c.relkind = 'r' AND c.relname <> 'flyway_schema_history'
            UNION ALL
            SELECT 'restriccion ' || conrelid::regclass || ' ' || conname || ' ' || pg_get_constraintdef(oid)
            FROM pg_constraint
            WHERE connamespace = 'public'::regnamespace AND conrelid::regclass::text <> 'flyway_schema_history'
            UNION ALL
            SELECT 'indice ' || indexname || ' ' || indexdef
            FROM pg_indexes WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
            UNION ALL
            SELECT 'enum ' || t.typname || ' ' || string_agg(e.enumlabel, ',' ORDER BY e.enumsortorder)
            FROM pg_type t JOIN pg_enum e ON e.enumtypid = t.oid
            WHERE t.typnamespace = 'public'::regnamespace GROUP BY t.typname
            UNION ALL
            SELECT 'rutina ' || p.proname || ' ' || pg_get_functiondef(p.oid)
            FROM pg_proc p
            WHERE p.pronamespace = 'public'::regnamespace AND p.prokind IN ('f', 'p')
              AND NOT EXISTS (SELECT 1 FROM pg_depend dp WHERE dp.objid = p.oid AND dp.deptype = 'e')
            UNION ALL
            SELECT 'trigger ' || tgname || ' ' || pg_get_triggerdef(oid) FROM pg_trigger WHERE NOT tgisinternal
            ORDER BY 1
            """;

    private static HikariDataSource scripts;
    private static HikariDataSource migrations;
    private static Jdbi jdbi;

    @BeforeAll
    static void buildBothDatabases() throws IOException {
        scripts = Database.connect(TestDatabase.newEmptyDatabase("proyecto_scripts"));
        jdbi = Jdbi.create(scripts);
        for (String file : List.of("01_esquema", "02_datos", "03_consultas", "04_funciones", "05_procedimientos")) {
            String sql = read(SQL.resolve(file + ".sql"));
            runScript(sql);
        }
        migrations = Database.connect(TestDatabase.newEmptyDatabase("proyecto_migraciones"));
        Database.migrate(migrations);
    }

    @AfterAll
    static void close() {
        scripts.close();
        migrations.close();
    }

    /**
     * El archivo completo como lo mandaría psql: el controlador de PostgreSQL separa las sentencias respetando
     * los cuerpos entre $$ (el separador de scripts de JDBI no los respeta).
     */
    private static void runScript(String sql) {
        jdbi.useHandle(h -> {
            try (var statement = h.getConnection().createStatement()) {
                statement.execute(sql);
            } catch (java.sql.SQLException e) {
                throw new IllegalStateException(e.getMessage(), e);
            }
        });
    }

    private static String read(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8);
    }

    private static int paymentCount() {
        return jdbi.withHandle(h -> h.createQuery("SELECT COUNT(*) FROM pago").mapTo(Integer.class).one());
    }

    private static List<String> describe(HikariDataSource dataSource) {
        return Jdbi.create(dataSource).withHandle(h -> h.createQuery(DESCRIBE_SCHEMA).mapTo(String.class).list());
    }

    @Test
    void losScripts_dejanElMismoEsquemaQueLasMigraciones() {
        List<String> fromScripts = describe(scripts);
        List<String> fromMigrations = describe(migrations);
        List<String> onlyScripts = new ArrayList<>(fromScripts);
        onlyScripts.removeAll(fromMigrations);
        List<String> onlyMigrations = new ArrayList<>(fromMigrations);
        onlyMigrations.removeAll(fromScripts);
        assertTrue(onlyScripts.isEmpty() && onlyMigrations.isEmpty(),
                "Solo en los scripts: " + onlyScripts + "\nSolo en las migraciones: " + onlyMigrations);
        assertTrue(fromScripts.stream().anyMatch(line -> line.startsWith("rutina sp_registrar_cobro")));
    }

    @Test
    void laMigracionV6_esCopiaDeLasFuncionesYElProcedimiento() throws IOException {
        String v6 = read(MIGRATIONS.resolve("V6__funciones_y_procedimientos.sql"));
        assertTrue(v6.contains(read(SQL.resolve("04_funciones.sql"))), "V6 debe contener 04_funciones.sql tal cual");
        assertTrue(v6.contains(read(SQL.resolve("05_procedimientos.sql"))), "V6 debe contener 05_procedimientos.sql tal cual");
    }

    /** Las consultas de 03_consultas.sql, por su identificador (Q01, Q02…). */
    private static Map<String, String> queries() throws IOException {
        Map<String, String> byId = new LinkedHashMap<>();
        Matcher m = Pattern.compile("(?m)^-- (Q\\d\\d) · ([^·]+)·").matcher(read(SQL.resolve("03_consultas.sql")));
        List<int[]> starts = new ArrayList<>();
        List<String> ids = new ArrayList<>();
        while (m.find()) {
            starts.add(new int[]{m.start()});
            ids.add(m.group(1) + " " + m.group(2).strip());
        }
        String text = read(SQL.resolve("03_consultas.sql"));
        for (int i = 0; i < ids.size(); i++) {
            int end = i + 1 < starts.size() ? starts.get(i + 1)[0] : text.length();
            byId.put(ids.get(i), text.substring(starts.get(i)[0], end).strip());
        }
        return byId;
    }

    @Test
    void cadaConsulta_devuelveFilas_yHayLasQuePideLaGuia() throws IOException {
        Map<String, String> queries = queries();
        Map<String, Integer> byResource = new LinkedHashMap<>();
        for (var query : queries.entrySet()) {
            String sql = query.getValue().replaceAll("(?m)^--.*$", "").strip().replaceAll(";$", "");
            int rows = jdbi.withHandle(h -> h.createQuery(sql).mapToMap().list().size());
            assertTrue(rows > 0, query.getKey() + " no devolvió filas");
            byResource.merge(query.getKey().substring(4), 1, Integer::sum);
        }
        assertTrue(byResource.getOrDefault("WHERE", 0) >= 5, byResource.toString());
        assertTrue(byResource.getOrDefault("Funciones", 0) >= 2, byResource.toString());
        assertTrue(byResource.getOrDefault("JOIN", 0) >= 3, byResource.toString());
        assertTrue(byResource.getOrDefault("Subconsulta", 0) >= 2, byResource.toString());
    }

    @Test
    void losDatosDePrueba_cubrenTodosLosEstados() {
        Map<String, String> states = new LinkedHashMap<>();
        jdbi.useHandle(h -> h.createQuery("SELECT nombre, fn_estado_socio(id) FROM socio ORDER BY nombre")
                .map((rs, ctx) -> states.put(rs.getString(1), rs.getString(2))).list());
        assertEquals("VIGENTE", states.get("Ana López"));
        assertEquals("POR_VENCER", states.get("Bruno Díaz"));
        assertEquals("VENCIDA", states.get("Daniel Soto"));
        assertEquals("BAJA", states.get("Elena Mora"));
        assertEquals("SIN_MEMBRESIA", states.get("Fernando Gil"));
        assertEquals("VIGENTE", states.get("Héctor Ríos"), "reactivado");
        assertEquals("VENCIDA", states.get("Jorge Luna"));
    }

    @Test
    void elVencimiento_esElInicioMasLaDuracion() {
        assertEquals("2026-12-01", jdbi.withHandle(h -> h.createQuery(
                "SELECT fn_fecha_vencimiento(DATE '2026-11-01', (SELECT id FROM plan_membresia WHERE nombre = 'Mensual'))::text")
                .mapTo(String.class).one()));
        assertEquals("GYM-0007 GYM-123456", jdbi.withHandle(h -> h.createQuery(
                "SELECT fn_formatear_folio('GYM-', 7, 4) || ' ' || fn_formatear_folio('GYM-', 123456, 4)")
                .mapTo(String.class).one()));
    }

    private record Charge(String folio, String kind, String start, String end) {
    }

    private static long id(Handle h, String table, String column, String value) {
        return h.createQuery("SELECT id FROM " + table + " WHERE " + column + " = :v").bind("v", value)
                .mapTo(Long.class).one();
    }

    /** Llama al procedimiento con nombres en lugar de ids y devuelve el cobro y su membresía. */
    private static Charge charge(Handle h, String member, String plan, String method, String cash, String operation) {
        // CALL no acepta subconsultas como argumentos: los ids se buscan antes.
        h.execute("CALL sp_registrar_cobro(%d, %d, %d, %d, %s, '%s', NULL, NULL, NULL)".formatted(
                id(h, "socio", "nombre", member), id(h, "plan_membresia", "nombre", plan),
                id(h, "metodo_pago", "nombre", method), id(h, "usuario", "nombre_usuario", "carlos"), cash, operation));
        return h.createQuery("""
                SELECT p.folio, p.tipo_cobro::text, m.fecha_inicio::text, m.fecha_vencimiento::text
                FROM pago p JOIN membresia m ON m.pago_id = p.id WHERE p.operacion_id = :op""")
                .bind("op", operation)
                .map((rs, ctx) -> new Charge(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4)))
                .one();
    }

    @Test
    void elProcedimiento_aplicaLasReglasDelCobro() {
        jdbi.useTransaction(h -> {
            String today = h.createQuery("SELECT CURRENT_DATE::text").mapTo(String.class).one();
            String brunoEnd = h.createQuery("""
                    SELECT MAX(fecha_vencimiento)::text FROM membresia
                    WHERE socio_id = (SELECT id FROM socio WHERE nombre = 'Bruno Díaz')""").mapTo(String.class).one();

            Charge first = charge(h, "Fernando Gil", "Mensual", "Efectivo", "500", "t-primera");
            assertEquals("primera", first.kind());
            assertEquals(today, first.start());
            assertEquals("GYM-0015", first.folio(), "el siguiente de la serie, sin huecos");

            Charge early = charge(h, "Bruno Díaz", "Mensual", "Tarjeta", "NULL", "t-anticipada");
            assertEquals("renovacion_anticipada", early.kind());
            assertEquals(brunoEnd, early.start(), "empieza el día en que termina la vigente");

            assertEquals("renovacion_vencida", charge(h, "Daniel Soto", "Mensual", "Tarjeta", "NULL", "t-vencida").kind());
            Charge back = charge(h, "Elena Mora", "Mensual", "Tarjeta", "NULL", "t-reactivacion");
            assertEquals("reactivacion", back.kind());
            assertEquals(today, back.start());

            Charge repeated = charge(h, "Fernando Gil", "Mensual", "Efectivo", "500", "t-primera");
            assertEquals(first, repeated, "la misma operación no cobra dos veces");
            assertEquals(18, h.createQuery("SELECT COUNT(*) FROM pago").mapTo(Integer.class).one());
            h.rollback();
        });
    }

    @Test
    void elProcedimiento_rechazaLoQueNoCumpleLasReglas() {
        assertRejected("Ese plan no existe o está inactivo; elige otro.",
                "Fernando Gil", "Promoción verano", "Tarjeta", "NULL");
        assertRejected("Ese método de pago no existe o ya no está disponible.",
                "Fernando Gil", "Mensual", "Vales", "NULL");
        assertRejected("Escribe cuánto efectivo recibiste.", "Fernando Gil", "Mensual", "Efectivo", "NULL");
        assertRejected("El efectivo recibido no cubre el total.", "Fernando Gil", "Mensual", "Efectivo", "100");
        assertEquals(14, paymentCount());
    }

    private static void assertRejected(String message, String member, String plan, String method, String cash) {
        StatementException e = assertThrows(StatementException.class,
                () -> jdbi.useTransaction(h -> charge(h, member, plan, method, cash, "t-rechazo")));
        assertTrue(e.getMessage().contains(message), e.getMessage());
    }

    @Test
    void losCobrosYLasMembresias_noSeBorran() {
        for (String table : List.of("pago", "membresia")) {
            StatementException e = assertThrows(StatementException.class,
                    () -> jdbi.useHandle(h -> h.execute("DELETE FROM " + table + " WHERE id = 1")));
            assertTrue(e.getMessage().contains("Los registros de " + table + " no se borran"), e.getMessage());
        }
    }

    @Test
    void elArchivoDePruebas_correCompleto_ySinDejarCambios() throws IOException {
        String sql = read(SQL.resolve("06_pruebas.sql"));
        runScript(sql);
        assertEquals(14, paymentCount());
        assertNull(jdbi.withHandle(h -> h.createQuery("SELECT folio FROM pago WHERE operacion_id = 'prueba-p01'")
                .mapTo(String.class).findOne().orElse(null)));
        assertFalse(sql.contains("\\set"), "sin comandos de psql: la prueba lo corre por JDBC");
    }
}
