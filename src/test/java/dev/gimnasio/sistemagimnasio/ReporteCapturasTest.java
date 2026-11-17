package dev.gimnasio.sistemagimnasio;

import com.zaxxer.hikari.HikariDataSource;
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateInitialAdminCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateUserCommand;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.Database;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.DatabaseConfig;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresUserRepository;
import dev.gimnasio.sistemagimnasio.ui.GymDeskUi;
import dev.gimnasio.sistemagimnasio.ui.common.AppWindow;
import dev.gimnasio.sistemagimnasio.ui.common.Themes;
import dev.gimnasio.sistemagimnasio.ui.support.FxHarness;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import javafx.scene.Scene;
import javafx.scene.control.Labeled;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import javafx.stage.Window;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Capturas del reporte técnico con la aplicación real sobre PostgreSQL: una base migrada por la app, con cuentas
 * cifradas con Argon2id y los datos de scripts/demo/datos-demo.sql. Después de cada operación consulta la base y
 * guarda lo que quedó en {@code target/reporte/evidencias.md}, como prueba de que la app persiste en PostgreSQL.
 *
 * <p>Necesita Docker y pantalla: {@code ./mvnw test -Dtest=ReporteCapturasTest -Dreporte.capturas=true}.
 * Las capturas quedan en {@code target/reporte/capturas/}; scripts/docs/reporte/generar-reporte.sh las usa.
 */
@EnabledIfSystemProperty(named = "reporte.capturas", matches = "true")
class ReporteCapturasTest {
    private static final Path OUT = Path.of("target", "reporte");
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("gimnasio_demo");
    /** Contraseñas de esta prueba: aleatorias, no se imprimen ni se guardan. */
    private static final String ADMIN_PASSWORD = UUID.randomUUID().toString();
    private static final String CAJA_PASSWORD = UUID.randomUUID().toString();

    private static HikariDataSource dataSource;
    private static Jdbi jdbi;
    private static final List<String> evidence = new ArrayList<>();

    private Stage stage;
    private GymDeskUi ui;
    private Themes themes;

    @BeforeAll
    static void database() throws Exception {
        POSTGRES.start();
        dataSource = Database.connect(new DatabaseConfig(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(),
                POSTGRES.getPassword()));
        Database.migrate(dataSource);
        jdbi = Jdbi.create(dataSource);
        Wiring.createInitialAdmin(jdbi).execute(new CreateInitialAdminCommand("Administración General", "admin",
                ADMIN_PASSWORD, ADMIN_PASSWORD));
        User admin = new PostgresUserRepository(jdbi).findAll().getFirst();
        var useCases = Wiring.useCases(jdbi, Clock.systemDefaultZone());
        String recepcion = UUID.randomUUID().toString();
        useCases.createUser().execute(admin, new CreateUserCommand("Rocío Méndez", "rocio", Role.RECEPCION,
                recepcion, recepcion));
        String sql = Files.readString(Path.of("scripts/demo/datos-demo.sql"));
        jdbi.useHandle(h -> {
            try (var statement = h.getConnection().createStatement()) {
                statement.execute(sql);
            }
        });
        FxHarness.start();
    }

    @AfterAll
    static void close() throws IOException {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("evidencias.md"), String.join("\n", evidence) + "\n");
        if (dataSource != null) {
            dataSource.close();
        }
        POSTGRES.stop();
    }

    @AfterEach
    void closeWindow() throws Exception {
        if (stage != null) {
            FxHarness.runFx(() -> {
                ui.close();
                stage.close();
            });
            stage = null;
        }
    }

    // ---------- Ayudantes ----------

    private Scene open(Callable<dev.gimnasio.sistemagimnasio.ui.common.UseCases> connector) throws Exception {
        themes = UiFakes.themes(Themes.Theme.CLARO);
        stage = FxHarness.onFx(Stage::new);
        ui = FxHarness.onFx(() -> {
            GymDeskUi created = new GymDeskUi(stage, connector, themes);
            created.start();
            return created;
        });
        FxHarness.resizeScene(stage, AppWindow.WIDTH, AppWindow.HEIGHT);
        return FxHarness.onFx(stage::getScene);
    }

    private Scene signIn(String username, String password) throws Exception {
        Scene scene = open(() -> Wiring.useCases(jdbi, Clock.systemDefaultZone()));
        FxHarness.waitUntil("acceso", () -> scene.lookup("#username") != null);
        type(scene, "username", username);
        type(scene, "password", password);
        FxHarness.runFx(() -> scene.lookup("#password").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("menú", () -> FxHarness.onFxUnchecked(stage::getScene).lookup("#nav-cobro") != null);
        return FxHarness.onFx(stage::getScene);
    }

    private static void type(Scene scene, String id, String text) throws Exception {
        FxHarness.runFx(() -> ((TextInputControl) scene.lookup("#" + id)).setText(text));
    }

    private static void fire(Scene scene, String id) throws Exception {
        FxHarness.runFx(() -> ((javafx.scene.control.ButtonBase) scene.lookup("#" + id)).fire());
        FxHarness.settle();
    }

    private static boolean onScreen(Scene scene, String id) {
        var node = scene.lookup("#" + id);
        for (var n = node; n != null; n = n.getParent()) {
            if (!n.isVisible()) {
                return false;
            }
        }
        return node != null;
    }

    private static String text(Scene scene, String id) {
        return ((Labeled) scene.lookup("#" + id)).getText();
    }

    private static Scene dialog() {
        return Window.getWindows().stream()
                .filter(w -> w.getScene() != null && w.getScene().getRoot() instanceof javafx.scene.control.DialogPane)
                .map(Window::getScene).findFirst().orElse(null);
    }

    private void go(Scene scene, String nav) throws Exception {
        fire(scene, "nav-" + nav);
    }

    private static void clearToasts(Scene scene) throws Exception {
        FxHarness.runFx(() -> scene.getRoot().lookupAll(".aviso-flotante").stream().toList()
                .forEach(n -> ((javafx.scene.layout.Pane) n.getParent()).getChildren().remove(n)));
    }

    private static void capture(Scene scene, String name) throws Exception {
        FxHarness.settle();
        FxHarness.capture(scene, OUT.resolve("capturas").resolve(name + ".png"));
    }

    /** Consulta la base y la deja como tabla Markdown en las evidencias. */
    private static void query(String title, String sql) throws Exception {
        List<List<String>> rows = jdbi.withHandle(h -> h.createQuery(sql).map((rs, ctx) -> {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= rs.getMetaData().getColumnCount(); i++) {
                row.add(rs.getString(i));
            }
            return row;
        }).list());
        List<String> columns = jdbi.withHandle(h -> {
            try (var statement = h.getConnection().prepareStatement(sql)) {
                var meta = statement.getMetaData();
                List<String> names = new ArrayList<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    names.add(meta.getColumnLabel(i));
                }
                return names;
            }
        });
        evidence.add("### " + title);
        evidence.add("");
        evidence.add("```sql");
        evidence.add(sql.strip());
        evidence.add("```");
        evidence.add("");
        evidence.add("| " + String.join(" | ", columns) + " |");
        evidence.add("|" + "---|".repeat(columns.size()));
        rows.forEach(row -> evidence.add("| " + String.join(" | ", row.stream()
                .map(v -> v == null ? "NULL" : v).toList()) + " |"));
        evidence.add("");
    }

    private long id(String sql) {
        return jdbi.withHandle(h -> h.createQuery(sql).mapTo(Long.class).one());
    }

    // ---------- Recorrido ----------

    @Test
    void recorridoDelReporte() throws Exception {
        // Sin base: la pantalla de arranque dice por qué y ofrece reintentar.
        Scene noDb = open(() -> {
            Database.connect(new DatabaseConfig("jdbc:postgresql://127.0.0.1:1/gimnasio", "gimnasio", "x"));
            throw new IllegalStateException("inalcanzable");
        });
        FxHarness.waitUntil("error de conexión", () -> onScreen(noDb, "retryButton"));
        capture(noDb, "01-arranque-sin-conexion");
        closeWindow();

        Scene login = open(() -> Wiring.useCases(jdbi, Clock.systemDefaultZone()));
        FxHarness.waitUntil("acceso", () -> onScreen(login, "username"));
        type(login, "username", "admin");
        capture(login, "02-acceso");
        closeWindow();

        // Administrador: tablero.
        Scene scene = signIn("admin", ADMIN_PASSWORD);
        FxHarness.runFx(() -> ui.shell().setToastTime(javafx.util.Duration.minutes(1)));
        FxHarness.waitUntil("tablero", () -> onScreen(scene, "tb-tabla-cobros") && !"—".equals(text(scene, "tb-activos")));
        capture(scene, "03-tablero");
        String vencidasAntes = FxHarness.onFx(() -> text(scene, "tb-vencidas"));

        // Búsqueda del encabezado → cobro de una membresía vencida.
        long teresa = id("SELECT id FROM socio WHERE nombre = 'Teresa Campos'");
        FxHarness.runFx(() -> {
            scene.lookup("#buscar-global").requestFocus();
            ((TextInputControl) scene.lookup("#buscar-global")).setText("te");
        });
        FxHarness.waitUntil("resultados", () -> onScreen(scene, "bg-socio-" + teresa));
        capture(scene, "04-busqueda");
        fire(scene, "bg-socio-" + teresa);
        FxHarness.waitUntil("cobro", () -> onScreen(scene, "co-socio") && onScreen(scene, "co-planes"));
        long mensual = id("SELECT id FROM plan_membresia WHERE nombre = 'Mensual'");
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) scene.lookup("#co-plan-" + mensual)).fire());
        type(scene, "co-efectivo", "500");
        FxHarness.waitUntil("listo", () -> {
            var button = scene.lookup("#co-cobrar");
            return button != null && !button.isDisabled() && onScreen(scene, "co-cambio");
        });
        capture(scene, "05-cobro-listo");
        FxHarness.runFx(() -> scene.lookup("#co-efectivo").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("recibo", () -> onScreen(scene, "co-folio"));
        capture(scene, "06-cobro-recibo");
        clearToasts(scene);
        query("E1 · Cobro registrado desde la pantalla Cobrar membresía", """
                SELECT p.folio, s.nombre AS socio, pl.nombre AS plan, p.monto, p.efectivo_recibido,
                       p.tipo_cobro, m.fecha_inicio, m.fecha_vencimiento, u.nombre_usuario AS atendio
                FROM pago p
                JOIN socio s ON s.id = p.socio_id
                JOIN membresia m ON m.pago_id = p.id
                JOIN plan_membresia pl ON pl.id = m.plan_id
                JOIN usuario u ON u.id = p.usuario_id
                ORDER BY p.id DESC LIMIT 1""");
        query("E2 · Estado de Teresa Campos con la función de la base después del cobro",
                "SELECT nombre, fn_estado_socio(id) AS estado FROM socio WHERE nombre = 'Teresa Campos'");

        // De vuelta en el tablero: una vencida menos y el cobro en los ingresos de hoy.
        go(scene, "tablero");
        FxHarness.waitUntil("tablero actualizado", () -> onScreen(scene, "tb-tabla-cobros")
                && !vencidasAntes.equals(text(scene, "tb-vencidas")));
        capture(scene, "07-tablero-despues");

        // Socios: lista, alta y regla de borrado.
        go(scene, "socios");
        FxHarness.waitUntil("socios", () -> onScreen(scene, "so-tabla"));
        capture(scene, "08-socios");
        fire(scene, "so-agregar");
        FxHarness.waitUntil("diálogo de socio", () -> dialog() != null && dialog().lookup("#so-nombre") != null);
        Scene memberDialog = FxHarness.onFx(ReporteCapturasTest::dialog);
        type(memberDialog, "so-nombre", "Laura Medina");
        type(memberDialog, "so-telefono", "555 120 3456");
        type(memberDialog, "so-correo", "laura.medina@correo.mx");
        capture(memberDialog, "09-socio-registrar");
        FxHarness.runFx(() -> memberDialog.lookup("#so-nombre").requestFocus());
        FxHarness.press(memberDialog, KeyCode.ENTER, false);
        FxHarness.waitUntil("socio guardado", () -> dialog() == null);
        clearToasts(scene);
        query("E3 · Socio registrado desde la pantalla Socios",
                "SELECT id, nombre, telefono, correo, fecha_alta FROM socio WHERE nombre = 'Laura Medina'");
        long ana = id("SELECT id FROM socio WHERE nombre = 'Ana López'");
        type(scene, "so-buscar", "ana lopez");
        FxHarness.waitUntil("Ana", () -> onScreen(scene, "so-eliminar-" + ana));
        FxHarness.runFx(() -> scene.lookup("#so-eliminar-" + ana).requestFocus());
        FxHarness.pressLater(scene, KeyCode.SPACE);
        FxHarness.waitUntil("confirmación", () -> dialog() != null);
        FxHarness.runFx(() -> dialog().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && "Eliminar".equals(b.getText()))
                .map(javafx.scene.control.Button.class::cast).findFirst().orElseThrow().fire());
        FxHarness.waitUntil("rechazo", () -> scene.lookup("#aviso-error") != null);
        capture(scene, "10-socio-eliminar-rechazado");
        clearToasts(scene);
        type(scene, "so-buscar", "");

        // Planes: catálogo y alta con el ahorro calculado.
        go(scene, "planes");
        FxHarness.waitUntil("planes", () -> onScreen(scene, "pl-tabla"));
        capture(scene, "11-planes");
        fire(scene, "pl-agregar");
        FxHarness.waitUntil("diálogo de plan", () -> dialog() != null && dialog().lookup("#pl-nombre") != null);
        Scene planDialog = FxHarness.onFx(ReporteCapturasTest::dialog);
        type(planDialog, "pl-nombre", "Bimestral");
        type(planDialog, "pl-duracion", "60");
        type(planDialog, "pl-precio", "850");
        FxHarness.runFx(() -> {
            @SuppressWarnings("unchecked")
            var reference = (javafx.scene.control.ComboBox<Object>) planDialog.lookup("#pl-referencia");
            reference.getItems().stream().filter(o -> o.toString().contains("Mensual")).findFirst()
                    .ifPresent(o -> reference.getSelectionModel().select(o));
        });
        FxHarness.waitUntil("ahorro", () -> text(planDialog, "pl-ahorro").startsWith("Ahorra"));
        capture(planDialog, "12-plan-agregar");
        FxHarness.runFx(() -> planDialog.lookup("#pl-nombre").requestFocus());
        FxHarness.press(planDialog, KeyCode.ENTER, false);
        FxHarness.waitUntil("plan guardado", () -> dialog() == null);
        clearToasts(scene);
        query("E4 · Plan agregado desde la pantalla Planes", """
                SELECT p.nombre, p.duracion_dias, p.precio, r.nombre AS referencia_mensual, p.activo
                FROM plan_membresia p LEFT JOIN plan_membresia r ON r.id = p.referencia_mensual_id
                WHERE p.nombre = 'Bimestral'""");

        // Configuración con el catálogo de métodos de pago.
        go(scene, "configuracion");
        FxHarness.waitUntil("configuración", () -> onScreen(scene, "cf-nombre") && onScreen(scene, "mp-tabla"));
        capture(scene, "13-configuracion");
        FxHarness.runFx(() -> ((javafx.scene.control.ScrollPane) scene.lookup(".lienzo .scroll-pane")).setVvalue(1));
        capture(scene, "14-metodos-de-pago");

        // Usuarios: cuentas, matriz de permisos y alta de una cuenta de Caja.
        go(scene, "usuarios");
        FxHarness.waitUntil("usuarios", () -> onScreen(scene, "us-tabla"));
        capture(scene, "15-usuarios");
        fire(scene, "us-agregar");
        FxHarness.waitUntil("diálogo de usuario", () -> dialog() != null && dialog().lookup("#us-nombre") != null);
        Scene userDialog = FxHarness.onFx(ReporteCapturasTest::dialog);
        type(userDialog, "us-nombre", "Carlos Ruiz");
        type(userDialog, "us-usuario", "carlos");
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) userDialog.lookup("#us-rol-caja")).fire());
        type(userDialog, "us-clave", CAJA_PASSWORD);
        type(userDialog, "us-clave2", CAJA_PASSWORD);
        capture(userDialog, "16-usuario-agregar");
        FxHarness.runFx(() -> userDialog.lookup("#us-nombre").requestFocus());
        FxHarness.press(userDialog, KeyCode.ENTER, false);
        FxHarness.waitUntil("cuenta creada", () -> dialog() == null);
        query("E5 · Cuenta creada desde la pantalla Usuarios (solo se guarda el hash Argon2id)", """
                SELECT nombre, nombre_usuario, rol, activo, left(contrasena_hash, 10) || '…' AS hash
                FROM usuario WHERE nombre_usuario = 'carlos'""");

        // Tema nocturno.
        go(scene, "tablero");
        FxHarness.waitUntil("tablero", () -> onScreen(scene, "tb-tabla-cobros"));
        FxHarness.runFx(() -> themes.set(Themes.Theme.OSCURO));
        capture(scene, "17-tablero-nocturno");
        closeWindow();

        // La cuenta nueva entra con el menú de Caja.
        Scene caja = signIn("carlos", CAJA_PASSWORD);
        FxHarness.waitUntil("cobro", () -> onScreen(caja, "co-buscar"));
        assertEquals(List.of("nav-cobro", "nav-socios"), FxHarness.onFx(() -> caja.getRoot().lookupAll(".nav-enlace")
                .stream().map(javafx.scene.Node::getId).toList()));
        capture(caja, "18-menu-caja");
        closeWindow();

        query("E6 · Estados de los socios según la función de la base", """
                SELECT fn_estado_socio(id) AS estado, COUNT(*) AS socios
                FROM socio GROUP BY 1 ORDER BY 1""");
        assertTrue(Files.exists(OUT.resolve("capturas").resolve("18-menu-caja.png")));
    }
}
