package dev.gimnasio.sistemagimnasio.ui.capturas;

import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.ui.GymDeskUi;
import dev.gimnasio.sistemagimnasio.ui.common.AppWindow;
import dev.gimnasio.sistemagimnasio.ui.common.Themes;
import dev.gimnasio.sistemagimnasio.ui.support.FxHarness;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.stage.Stage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Abre la UI real (con casos de uso sobre repositorios en memoria), guarda capturas en ambos temas a
 * 1200×760 y 1024×680 en {@code target/capturas/} y revisa el orden del foco con el teclado.
 *
 * <p>Necesita pantalla, así que no corre con {@code ./mvnw test}:
 * {@code ./mvnw test -Dtest=CapturasUiTest -Dui.capturas=true}
 */
@EnabledIfSystemProperty(named = "ui.capturas", matches = "true")
class CapturasUiTest {
    private static final Path OUT = Path.of("target", "capturas");
    private static final double[][] SIZES = {{AppWindow.WIDTH, AppWindow.HEIGHT}, {AppWindow.MIN_WIDTH, AppWindow.MIN_HEIGHT}};

    private Stage stage;
    private GymDeskUi ui;
    private Themes themes;

    @BeforeAll
    static void startFx() throws Exception {
        FxHarness.start();
    }

    @AfterEach
    void closeWindow() throws Exception {
        if (stage != null) {
            FxHarness.runFx(() -> {
                ui.close();
                stage.close();
            });
        }
    }

    private Scene open(UiFakes.Users users) throws Exception {
        return open(new UiFakes.Backend(users));
    }

    private Scene open(UiFakes.Backend backend) throws Exception {
        themes = UiFakes.themes(Themes.Theme.CLARO);
        stage = FxHarness.onFx(Stage::new);
        ui = FxHarness.onFx(() -> {
            GymDeskUi created = new GymDeskUi(stage, backend::useCases, themes);
            created.start();
            return created;
        });
        return FxHarness.onFx(stage::getScene);
    }

    /** Captura la pantalla actual en ambos temas y ambos tamaños: {@code <nombre>-<tema>-<ancho>.png}. */
    private List<Path> captureAll(String folder, String name) throws Exception {
        List<Path> files = new ArrayList<>();
        for (Themes.Theme theme : Themes.Theme.values()) {
            FxHarness.runFx(() -> themes.set(theme));
            for (double[] size : SIZES) {
                FxHarness.resizeScene(stage, size[0], size[1]);
                Scene scene = FxHarness.onFx(stage::getScene);
                String file = name + "-" + theme.name().toLowerCase() + "-" + (int) size[0] + ".png";
                files.add(FxHarness.capture(scene, OUT.resolve(folder).resolve(file)));
            }
        }
        FxHarness.runFx(() -> themes.set(Themes.Theme.CLARO));
        FxHarness.resizeScene(stage, AppWindow.WIDTH, AppWindow.HEIGHT);
        return files;
    }

    private List<String> tabOrder(Scene scene, int presses) throws Exception {
        List<String> order = new ArrayList<>();
        order.add(FxHarness.focusOwner(scene));
        for (int i = 0; i < presses; i++) {
            FxHarness.press(scene, KeyCode.TAB, false);
            order.add(FxHarness.focusOwner(scene));
        }
        return order;
    }

    private void typeInto(Scene scene, String id, String text) throws Exception {
        FxHarness.runFx(() -> ((javafx.scene.control.TextInputControl) scene.lookup("#" + id)).setText(text));
    }

    private String text(Scene scene, String id) throws Exception {
        return FxHarness.onFx(() -> {
            var node = scene.lookup("#" + id);
            return node instanceof javafx.scene.control.Labeled l ? l.getText()
                    : ((javafx.scene.control.TextInputControl) node).getText();
        });
    }

    @Test
    void entrega2AccesoFuncional() throws Exception {
        UiFakes.Users users = new UiFakes.Users();
        users.add("Administración", "admin", Role.ADMINISTRADOR, true);
        users.add("Luis Pérez", "luis", Role.RECEPCION, false);
        Scene scene = open(users);
        FxHarness.waitUntil("foco en Usuario", () -> scene.getFocusOwner() != null
                && "username".equals(scene.getFocusOwner().getId()));

        // 1. Enter con los campos vacíos: los dos errores juntos y el foco vuelve a Usuario.
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("errores por campo", () -> scene.lookup("#usernameError").isVisible());
        assertEquals("Escribe tu nombre de usuario.", text(scene, "usernameError"));
        assertEquals("Escribe tu contraseña.", text(scene, "passwordError"));
        assertEquals("username", FxHarness.focusOwner(scene));
        captureAll("entrega-2", "campos-vacios");

        // 2. Contraseña incorrecta, enviada con Enter desde Contraseña.
        typeInto(scene, "username", "admin");
        typeInto(scene, "password", "no-es-esta");
        FxHarness.runFx(() -> scene.lookup("#password").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("aviso de credenciales", () -> scene.lookup("#errorNote").isVisible());
        assertEquals("Usuario o contraseña incorrectos. Revísalos e inténtalo de nuevo.", text(scene, "errorText"));
        assertEquals(false, FxHarness.onFx(() -> scene.lookup("#usernameError").isVisible()), "se quitan los errores por campo");
        assertEquals("admin", text(scene, "username"));
        assertEquals("", text(scene, "password"));
        assertEquals("password", FxHarness.focusOwner(scene));
        captureAll("entrega-2", "credenciales-incorrectas");

        // 3. Usuario desactivado con la contraseña correcta.
        typeInto(scene, "username", "luis");
        typeInto(scene, "password", UiFakes.PASSWORD);
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("aviso de desactivado", () -> ((javafx.scene.control.Label) scene.lookup("#errorText"))
                .getText().startsWith("Tu usuario está desactivado"));
        captureAll("entrega-2", "usuario-desactivado");

        // 4. Mientras se revisa: botón «Entrando…» desactivado y sin segundos envíos.
        users.gate = new java.util.concurrent.CountDownLatch(1);
        int before = users.lookups;
        typeInto(scene, "username", "admin");
        typeInto(scene, "password", UiFakes.PASSWORD);
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("botón ocupado", () -> scene.lookup("#submit").isDisabled());
        assertEquals("Entrando…", text(scene, "submit"));
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.runFx(() -> ((javafx.scene.control.Button) scene.lookup("#submit")).fire());
        captureAll("entrega-2", "entrando");
        users.gate.countDown();
        FxHarness.waitUntil("sesión iniciada", () -> FxHarness.onFxUnchecked(stage::getScene).lookup("#userName") != null);
        assertEquals(before + 1, users.lookups, "Enter repetido y clic durante la revisión no envían otra vez");
        Scene current = FxHarness.onFx(stage::getScene);
        assertEquals("Administración", text(current, "userName"));
        assertEquals("Administrador · admin", text(current, "userRole"));
        assertEquals(true, FxHarness.onFx(() -> ui.session().isOpen()));

        // 5. Cerrar sesión vuelve al acceso con los campos vacíos.
        FxHarness.runFx(() -> ((javafx.scene.control.Button) current.lookup("#signOut")).fire());
        FxHarness.waitUntil("vuelta al acceso", () -> FxHarness.onFxUnchecked(stage::getScene).lookup("#username") != null);
        Scene back = FxHarness.onFx(stage::getScene);
        assertEquals("", text(back, "password"));
        assertEquals(false, FxHarness.onFx(() -> ui.session().isOpen()));
        System.out.println("[teclado] acceso funcional: Enter envía desde Usuario y Contraseña; foco al campo con error; "
                + "Enter y clic repetidos ignorados mientras revisa");
    }

    private Scene signIn(UiFakes.Users users, String username) throws Exception {
        return signIn(new UiFakes.Backend(users), username);
    }

    private Scene signIn(UiFakes.Backend backend, String username) throws Exception {
        Scene scene = open(backend);
        FxHarness.waitUntil("pantalla de acceso", () -> scene.lookup("#username") != null);
        typeInto(scene, "username", username);
        typeInto(scene, "password", UiFakes.PASSWORD);
        FxHarness.runFx(() -> scene.lookup("#password").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("menú", () -> FxHarness.onFxUnchecked(stage::getScene).lookup("#nav-cobro") != null);
        Scene shell = FxHarness.onFx(stage::getScene);
        FxHarness.waitUntil("foco en el menú", () -> shell.getFocusOwner() != null
                && shell.getFocusOwner().getId() != null && shell.getFocusOwner().getId().startsWith("nav-"));
        return shell;
    }

    private List<String> navIds(Scene scene) throws Exception {
        return FxHarness.onFx(() -> scene.getRoot().lookupAll(".nav-enlace").stream().map(javafx.scene.Node::getId).toList());
    }

    private static UiFakes.Users staff() {
        UiFakes.Users users = new UiFakes.Users();
        users.add("Administración General", "admin", Role.ADMINISTRADOR, true);
        users.add("Rocío Méndez", "rocio", Role.RECEPCION, true);
        users.add("Carlos Ruiz", "carlos", Role.CAJA, true);
        return users;
    }

    @Test
    void entrega3MenuAdministrador() throws Exception {
        Scene scene = signIn(staff(), "admin");
        assertEquals(List.of("nav-tablero", "nav-cobro", "nav-socios", "nav-planes", "nav-usuarios",
                "nav-configuracion"), navIds(scene));
        assertEquals("Tablero", text(scene, "titulo-pagina"));
        assertEquals("nav-tablero", FxHarness.focusOwner(scene));
        assertEquals("mar 10 nov 2026", FxHarness.onFx(() -> ((javafx.scene.control.Label) scene.lookup(".fecha")).getText()));
        captureAll("entrega-3", "tablero-administrador");
        FxHarness.resizeScene(stage, AppWindow.MIN_WIDTH, AppWindow.MIN_HEIGHT);
        // A 1024×680, si las secciones del administrador no caben, la lista se desplaza con barra visible.
        assertEquals(true, FxHarness.onFx(() -> {
            var sp = (javafx.scene.control.ScrollPane) scene.lookup(".nav-desplazable");
            boolean overflows = sp.getContent().getLayoutBounds().getHeight() > sp.getViewportBounds().getHeight();
            var bar = sp.lookupAll(".scroll-bar").stream()
                    .map(n -> (javafx.scene.control.ScrollBar) n)
                    .filter(b -> b.getOrientation() == javafx.geometry.Orientation.VERTICAL).findFirst().orElseThrow();
            var thumb = bar.lookup(".thumb");
            return !overflows || (bar.isVisible() && thumb.getLayoutBounds().getWidth() > 0);
        }), "barra del menú visible al mínimo si hace falta");
        FxHarness.runFx(() -> scene.lookup("#nav-configuracion").requestFocus());
        FxHarness.settle();
        assertEquals(true, FxHarness.onFx(() -> {
            var sp = (javafx.scene.control.ScrollPane) scene.lookup(".nav-desplazable");
            var button = scene.lookup("#nav-configuracion");
            var inViewport = sp.lookup(".viewport").localToScene(sp.lookup(".viewport").getLayoutBounds());
            return inViewport.contains(button.localToScene(button.getLayoutBounds()));
        }), "con el foco, Configuración queda a la vista");
        FxHarness.capture(scene, OUT.resolve("entrega-3").resolve("menu-desplazado-claro-1024.png"));
        FxHarness.runFx(() -> scene.lookup("#nav-tablero").requestFocus());
        FxHarness.resizeScene(stage, AppWindow.WIDTH, AppWindow.HEIGHT);

        // Tab recorre el menú, Cerrar sesión, el tema y los atajos, y vuelve al menú.
        List<String> order = tabOrder(scene, 9);
        System.out.println("[teclado] menú administrador, Tab: " + order);
        // Después de los atajos, Tab entra al contenido: el tablero ya no es un aviso de sección pendiente.
        assertEquals(List.of("nav-tablero", "nav-cobro", "nav-socios", "nav-planes", "nav-usuarios",
                "nav-configuracion", "signOut", "tema-claro", "atajos", "tb-actualizar"), order);

        // Espacio sobre una sección la abre.
        FxHarness.runFx(() -> scene.lookup("#nav-planes").requestFocus());
        FxHarness.press(scene, KeyCode.SPACE, false);
        assertEquals("Planes de membresía", text(scene, "titulo-pagina"));
        assertEquals("Pantalla en preparación", text(scene, "estado-pendiente"));
        captureAll("entrega-3", "planes-administrador");
        FxHarness.runFx(() -> scene.lookup("#nav-configuracion").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        assertEquals("Configuración", text(scene, "titulo-pagina"), "Enter también abre la sección, como un enlace");
        assertEquals("nav-configuracion", FxHarness.focusOwner(scene), "el foco se queda en el menú");

        // «?» abre los atajos; el diálogo toma el tema y Esc lo cierra.
        FxHarness.typeLater(scene, "?");
        FxHarness.waitUntil("diálogo de atajos", () -> dialogScene() != null);
        for (Themes.Theme theme : Themes.Theme.values()) {
            FxHarness.runFx(() -> themes.set(theme));
            FxHarness.settle();
            Scene dialog = FxHarness.onFx(this::dialogScene);
            assertEquals(true, FxHarness.onFx(() -> dialog.getRoot().getStyleClass().contains(theme.styleClass())));
            FxHarness.capture(dialog, OUT.resolve("entrega-3").resolve("dialogo-atajos-" + theme.name().toLowerCase() + ".png"));
        }
        Scene dialog = FxHarness.onFx(this::dialogScene);
        FxHarness.pressLater(dialog, KeyCode.ESCAPE);
        FxHarness.waitUntil("diálogo cerrado", () -> dialogScene() == null);
        FxHarness.runFx(() -> themes.set(Themes.Theme.CLARO));
        System.out.println("[teclado] Espacio y Enter abren la sección con el foco; «?» abre los atajos; Esc los cierra");

        // Cerrar sesión con el teclado vuelve al acceso.
        FxHarness.runFx(() -> scene.lookup("#signOut").requestFocus());
        FxHarness.press(scene, KeyCode.SPACE, false);
        FxHarness.waitUntil("vuelta al acceso", () -> FxHarness.onFxUnchecked(stage::getScene).lookup("#username") != null);
        assertEquals(false, FxHarness.onFx(() -> ui.session().isOpen()));
    }

    private Scene dialogScene() {
        return javafx.stage.Window.getWindows().stream()
                .filter(w -> w != stage && w.getScene() != null && w.getScene().getRoot() instanceof javafx.scene.control.DialogPane)
                .map(javafx.stage.Window::getScene).findFirst().orElse(null);
    }

    @Test
    void entrega3MenuRecepcionYCaja() throws Exception {
        Scene recepcion = signIn(staff(), "rocio");
        assertEquals(List.of("nav-tablero", "nav-cobro", "nav-socios"), navIds(recepcion));
        assertEquals("Tablero", text(recepcion, "titulo-pagina"));
        captureAll("entrega-3", "tablero-recepcion");
        closeWindow();

        Scene caja = signIn(staff(), "carlos");
        assertEquals(List.of("nav-cobro", "nav-socios"), navIds(caja));
        assertEquals("Cobrar membresía", text(caja, "titulo-pagina"), "Caja no ve el tablero: empieza en Cobrar");
        assertEquals("nav-cobro", FxHarness.focusOwner(caja));
        assertEquals("Carlos Ruiz", text(caja, "userName"));
        assertEquals("Caja · carlos", text(caja, "userRole"));
        captureAll("entrega-3", "cobro-caja");
        stage = null;
        closeWindowIfOpen();
    }

    private void closeWindowIfOpen() throws Exception {
        FxHarness.runFx(() -> javafx.stage.Window.getWindows().stream()
                .filter(w -> w instanceof Stage).map(w -> (Stage) w).toList().forEach(Stage::close));
    }

    /** Una lista desplegable con el CSS de la app: la lista abierta toma el tema del control que la abre. */
    @Test
    void entrega3ListaDesplegable() throws Exception {
        themes = UiFakes.themes(Themes.Theme.CLARO);
        FxHarness.runFx(dev.gimnasio.sistemagimnasio.ui.common.Fonts::load);
        FxHarness.runFx(themes::installOnNewWindows);
        for (Themes.Theme theme : Themes.Theme.values()) {
            FxHarness.runFx(() -> themes.set(theme));
            Stage owner = FxHarness.onFx(() -> {
                var combo = new javafx.scene.control.ComboBox<String>(javafx.collections.FXCollections.observableArrayList(
                        "Efectivo", "Tarjeta de débito", "Tarjeta de crédito", "Transferencia"));
                combo.setId("combo");
                combo.getSelectionModel().select(1);
                combo.setPrefWidth(260);
                var label = new javafx.scene.control.Label("Método de pago");
                label.getStyleClass().add("etiqueta");
                var box = new javafx.scene.layout.VBox(6, label, combo);
                box.setPadding(new javafx.geometry.Insets(24));
                box.setStyle("-fx-background-color: -gd-superficie;");
                Stage created = new Stage();
                Scene scene = new Scene(box, 320, 260);
                scene.getStylesheets().add(AppWindow.stylesheet());
                created.setScene(scene);
                created.show();
                combo.show();
                return created;
            });
            FxHarness.settle();
            Scene popup = FxHarness.onFx(() -> javafx.stage.Window.getWindows().stream()
                    .filter(w -> w instanceof javafx.stage.PopupWindow).map(javafx.stage.Window::getScene)
                    .findFirst().orElseThrow());
            assertEquals(true, FxHarness.onFx(() -> owner.getScene().getRoot().getStyleClass().contains(theme.styleClass())));
            FxHarness.capture(owner.getScene(), OUT.resolve("entrega-3").resolve("lista-control-" + theme.name().toLowerCase() + ".png"));
            FxHarness.capture(popup, OUT.resolve("entrega-3").resolve("lista-abierta-" + theme.name().toLowerCase() + ".png"));
            FxHarness.runFx(owner::close);
        }
    }

    /** Todos los iconos en ambos temas, para revisar que la conversión de Lucide a SVGPath dibuja bien. */
    @Test
    void entrega1Iconos() throws Exception {
        themes = UiFakes.themes(Themes.Theme.CLARO);
        FxHarness.runFx(dev.gimnasio.sistemagimnasio.ui.common.Fonts::load);
        for (Themes.Theme theme : Themes.Theme.values()) {
            Scene scene = FxHarness.onFx(() -> {
                javafx.scene.layout.FlowPane grid = new javafx.scene.layout.FlowPane(18, 18);
                grid.setPadding(new javafx.geometry.Insets(24));
                for (String name : dev.gimnasio.sistemagimnasio.ui.common.Icons.names()) {
                    var cell = new javafx.scene.layout.VBox(6, dev.gimnasio.sistemagimnasio.ui.common.Icons.icon(name, 24),
                            new javafx.scene.control.Label(name));
                    cell.setAlignment(javafx.geometry.Pos.CENTER);
                    cell.setPrefWidth(110);
                    grid.getChildren().add(cell);
                }
                grid.getStyleClass().add(theme.styleClass());
                grid.setStyle("-fx-background-color: -gd-superficie;");
                Scene created = new Scene(grid, 720, 420);
                created.getStylesheets().add(AppWindow.stylesheet());
                return created;
            });
            FxHarness.settle();
            FxHarness.capture(scene, OUT.resolve("entrega-1").resolve("iconos-" + theme.name().toLowerCase() + ".png"));
        }
    }

    @Test
    void entrega1Acceso() throws Exception {
        UiFakes.Users users = new UiFakes.Users();
        users.add("Administración", "admin", Role.ADMINISTRADOR, true);
        Scene scene = open(users);
        FxHarness.waitUntil("pantalla de acceso", () -> scene.lookup("#username") != null);
        FxHarness.waitUntil("foco en Usuario", () -> scene.getFocusOwner() != null
                && "username".equals(scene.getFocusOwner().getId()));

        List<Path> files = captureAll("entrega-1", "acceso");

        List<String> order = tabOrder(scene, 4);
        System.out.println("[capturas] " + files);
        System.out.println("[teclado] acceso, Tab: " + order);
        // Los dos botones de tema son un grupo: Tab entra al elegido y las flechas cambian entre ellos.
        assertEquals(List.of("username", "password", "submit", "tema-claro", "username"), order);

        FxHarness.press(scene, KeyCode.TAB, true);
        assertEquals("tema-claro", FxHarness.focusOwner(scene), "Mayús+Tab vuelve al selector de tema");
        FxHarness.press(scene, KeyCode.RIGHT, false);
        assertEquals("tema-oscuro", FxHarness.focusOwner(scene), "flecha derecha pasa al tema nocturno");
        assertEquals(Themes.Theme.OSCURO, FxHarness.onFx(themes::current), "la flecha elige el tema nocturno");
        assertEquals(true, FxHarness.onFx(() -> scene.getRoot().getStyleClass().contains("tema-oscuro")));
        FxHarness.press(scene, KeyCode.SPACE, false);
        assertEquals(Themes.Theme.OSCURO, FxHarness.onFx(themes::current), "Espacio sobre el elegido no lo quita");
        FxHarness.press(scene, KeyCode.LEFT, false);
        assertEquals(Themes.Theme.CLARO, FxHarness.onFx(themes::current), "flecha izquierda vuelve al claro");
        System.out.println("[teclado] acceso: Mayús+Tab → tema-claro; → elige nocturno; Espacio lo mantiene; ← vuelve a claro");
    }

    // ---------- Fase 2 · Entrega 1: Configuración, datos del gimnasio ----------

    /** Un PNG real de 96×96 (un cuadrado con un círculo), para probar el logotipo sin archivos del repositorio. */
    private static java.io.File samplePng() throws Exception {
        var image = new java.awt.image.BufferedImage(96, 96, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(0x0A, 0x65, 0x32));
        g.fillRect(0, 0, 96, 96);
        g.setColor(java.awt.Color.WHITE);
        g.fillOval(22, 22, 52, 52);
        g.dispose();
        java.nio.file.Path file = java.nio.file.Files.createTempFile("logo-prueba", ".png");
        file.toFile().deleteOnExit();
        javax.imageio.ImageIO.write(image, "png", file.toFile());
        return file.toFile();
    }

    private Scene openSettings(UiFakes.Backend backend) throws Exception {
        Scene scene = signIn(backend, "admin");
        FxHarness.runFx(() -> scene.lookup("#nav-configuracion").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("formulario cargado", () -> scene.lookup("#cf-nombre") != null && scene.lookup("#cf-nombre").isVisible());
        return scene;
    }

    private boolean visible(Scene scene, String id) throws Exception {
        return FxHarness.onFx(() -> shown(scene, id));
    }

    /** Para condiciones de {@code waitUntil}, que ya corren en el hilo de JavaFX. */
    private static boolean shown(Scene scene, String id) {
        var node = scene.lookup("#" + id);
        return node != null && node.isVisible();
    }

    private static String title(Scene scene) {
        return ((javafx.scene.control.Labeled) scene.lookup("#titulo-pagina")).getText();
    }

    private Scene dialogWithButton(String text) {
        Scene dialog = dialogScene();
        if (dialog == null) {
            return null;
        }
        boolean has = dialog.getRoot().lookupAll(".button").stream()
                .anyMatch(n -> n instanceof javafx.scene.control.Button b && text.equals(b.getText()));
        return has ? dialog : null;
    }

    private void clickInDialog(Scene dialog, String text) {
        javafx.application.Platform.runLater(() -> dialog.getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof javafx.scene.control.Button b && text.equals(b.getText()))
                .map(n -> (javafx.scene.control.Button) n).findFirst().orElseThrow().fire());
    }

    @Test
    void fase2Entrega1Configuracion() throws Exception {
        UiFakes.Backend backend = new UiFakes.Backend(staff());
        backend.settings.settings = new dev.gimnasio.sistemagimnasio.domain.settings.GymSettings("Gimnasio Fuerza",
                "Av. Juárez 120, Centro", "555 010 2233", null, "Gracias por entrenar con nosotros.",
                java.util.Currency.getInstance("MXN"), 7, 30);

        // 1. Mientras carga.
        Scene scene = signIn(backend, "admin");
        // El aviso de «guardado» dura lo suficiente para capturarlo en los cuatro tamaños y temas.
        FxHarness.runFx(() -> ui.shell().setToastTime(javafx.util.Duration.minutes(1)));
        java.util.concurrent.CountDownLatch slow = new java.util.concurrent.CountDownLatch(1);
        backend.users.idGate = slow;
        FxHarness.runFx(() -> scene.lookup("#nav-configuracion").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("cargando", () -> shown(scene, "loading"));
        captureAll("fase-2-entrega-1", "cargando");
        backend.users.idGate = null;
        slow.countDown();
        FxHarness.waitUntil("formulario cargado", () -> shown(scene, "cf-nombre"));

        // 2. Lo guardado, sin barra de cambios.
        assertEquals("Gimnasio Fuerza", text(scene, "cf-nombre"));
        assertEquals("Ejemplo de folio: GYM-0001. Los folios ya emitidos no cambian.", text(scene, "folioHelp"));
        assertEquals("Solo se puede elegir mientras no haya cobros.", text(scene, "currencyHelp"));
        assertEquals(false, visible(scene, "barra-guardar"));
        assertEquals("nav-configuracion", FxHarness.focusOwner(scene), "abrir la sección deja el foco en el menú");
        captureAll("fase-2-entrega-1", "configuracion");

        // 3. Teclado: Tab recorre el formulario en orden de lectura.
        FxHarness.runFx(() -> scene.lookup("#cf-nombre").requestFocus());
        List<String> order = tabOrder(scene, 8);
        System.out.println("[teclado] configuración, Tab: " + order);
        assertEquals(List.of("cf-nombre", "cf-direccion", "cf-telefono", "cf-logo", "cf-leyenda", "cf-aviso",
                "cf-plazo", "cf-moneda", "cf-prefijo"), order);

        // 4. Cambios: nombre, logotipo (con el selector de archivos de prueba) y prefijo en minúsculas.
        java.io.File png = samplePng();
        FxHarness.runFx(() -> ui.useLogoChooser(owner -> png));
        typeInto(scene, "cf-nombre", "Gimnasio Fuerza Centro");
        FxHarness.runFx(() -> ((javafx.scene.control.Button) scene.lookup("#cf-logo")).fire());
        FxHarness.runFx(() -> ((javafx.scene.control.TextInputControl) scene.lookup("#cf-prefijo")).setText(""));
        FxHarness.runFx(() -> ((javafx.scene.control.TextInputControl) scene.lookup("#cf-prefijo")).insertText(0, "fza-"));
        FxHarness.settle();
        assertEquals("FZA-", text(scene, "cf-prefijo"), "el prefijo se escribe en mayúsculas");
        assertEquals("Ejemplo de folio: FZA-0001. Los folios ya emitidos no cambian.", text(scene, "folioHelp"));
        assertEquals("Logotipo nuevo, sin guardar", text(scene, "logoStatus"));
        assertEquals(true, visible(scene, "barra-guardar"));
        assertEquals(true, visible(scene, "removeLogo"));
        captureAll("fase-2-entrega-1", "cambios-sin-guardar");

        // 5. Errores del caso de uso, enviados con Enter desde un campo: cada uno en su campo y el foco al primero.
        typeInto(scene, "cf-nombre", "");
        typeInto(scene, "cf-aviso", "0");
        typeInto(scene, "cf-leyenda", "x".repeat(170));
        FxHarness.runFx(() -> scene.lookup("#cf-aviso").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("errores por campo", () -> shown(scene, "gymNameError"));
        assertEquals("Escribe el nombre del gimnasio.", text(scene, "gymNameError"));
        assertEquals("Usa 160 caracteres o menos.", text(scene, "receiptFooterError"));
        assertEquals("Escribe un número de días entre 1 y 60.", text(scene, "warningDaysError"));
        assertEquals("170 de 160 caracteres.", text(scene, "receiptFooterCount"));
        assertEquals("cf-nombre", FxHarness.focusOwner(scene));
        assertEquals("Gimnasio Fuerza", backend.settings.settings.gymName(), "no se guardó nada");
        FxHarness.runFx(() -> ((javafx.scene.control.ScrollPane) scene.lookup(".lienzo .scroll-pane")).setVvalue(0));
        captureAll("fase-2-entrega-1", "errores");

        // 6. Corregido y guardado: aviso flotante, sin barra, y el menú ya muestra el nombre y el logotipo nuevos.
        typeInto(scene, "cf-nombre", "Gimnasio Fuerza Centro");
        typeInto(scene, "cf-aviso", "10");
        typeInto(scene, "cf-leyenda", "Gracias por entrenar con nosotros.");
        FxHarness.runFx(() -> scene.lookup("#cf-nombre").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("guardado", () -> !shown(scene, "barra-guardar") && shown(scene, "aviso"));
        FxHarness.waitUntil("menú actualizado", () -> "Gimnasio Fuerza Centro".equals(
                ((javafx.scene.control.Label) scene.lookup(".marca-nombre")).getText()));
        assertEquals("Gimnasio Fuerza Centro", backend.settings.settings.gymName());
        assertEquals(10, backend.settings.settings.warningDays());
        assertEquals("FZA-", backend.folios.get("COBRO").prefix());
        assertEquals(true, backend.settings.settings.logo().length > 0);
        assertEquals("Configuración guardada. Ya se ve en el menú y en el acceso.",
                FxHarness.onFx(() -> ((javafx.scene.control.Label) scene.lookup("#aviso .label")).getText()));
        assertEquals("Logotipo actual", text(scene, "logoStatus"));
        assertEquals(true, FxHarness.onFx(() -> scene.lookup(".marca .logo").getStyleClass().contains("con-imagen")),
                "el logotipo del menú es la imagen nueva");
        assertEquals("Gimnasio Fuerza Centro · Sistema Gimnasio", FxHarness.onFx(stage::getTitle));
        captureAll("fase-2-entrega-1", "guardado");

        // 7. Salir con cambios sin guardar pide confirmación; «Seguir editando» (o Esc) conserva lo escrito.
        typeInto(scene, "cf-telefono", "555 999 0000");
        FxHarness.runFx(() -> scene.lookup("#nav-planes").requestFocus());
        FxHarness.pressLater(scene, KeyCode.SPACE);
        FxHarness.waitUntil("confirmación al salir", () -> dialogWithButton("Salir sin guardar") != null);
        for (Themes.Theme theme : Themes.Theme.values()) {
            FxHarness.runFx(() -> themes.set(theme));
            FxHarness.settle();
            Scene dialog = FxHarness.onFx(() -> dialogWithButton("Salir sin guardar"));
            FxHarness.capture(dialog, OUT.resolve("fase-2-entrega-1").resolve("salir-sin-guardar-" + theme.name().toLowerCase() + ".png"));
        }
        FxHarness.runFx(() -> themes.set(Themes.Theme.CLARO));
        Scene dialog = FxHarness.onFx(() -> dialogWithButton("Salir sin guardar"));
        assertEquals("Seguir editando", FxHarness.onFx(() -> {
            var owner = dialog.getFocusOwner();
            return owner instanceof javafx.scene.control.Button b ? b.getText() : String.valueOf(owner);
        }), "el foco empieza en la opción que no pierde nada");
        FxHarness.pressLater(dialog, KeyCode.ESCAPE);
        FxHarness.waitUntil("diálogo cerrado", () -> dialogScene() == null);
        assertEquals("Configuración", text(scene, "titulo-pagina"));
        assertEquals("555 999 0000", text(scene, "cf-telefono"), "lo escrito sigue ahí");

        // «Salir sin guardar» cambia de sección y descarta.
        FxHarness.runFx(() -> scene.lookup("#nav-planes").requestFocus());
        FxHarness.pressLater(scene, KeyCode.SPACE);
        FxHarness.waitUntil("confirmación al salir", () -> dialogWithButton("Salir sin guardar") != null);
        clickInDialog(FxHarness.onFx(() -> dialogWithButton("Salir sin guardar")), "Salir sin guardar");
        FxHarness.waitUntil("en Planes", () -> "Planes de membresía".equals(title(scene)));
        assertEquals("555 010 2233", backend.settings.settings.phone(), "el cambio descartado no se guardó");
        System.out.println("[teclado] configuración: Enter desde un campo guarda; foco al primer error; al salir con "
                + "cambios, el diálogo abre en «Seguir editando» y Esc lo cierra sin perder nada");
    }

    @Test
    void fase2Entrega1MonedaBloqueada() throws Exception {
        UiFakes.Backend backend = new UiFakes.Backend(staff());
        backend.payments.count = 41;
        Scene scene = openSettings(backend);
        assertEquals(true, FxHarness.onFx(() -> scene.lookup("#cf-moneda").isDisabled()));
        assertEquals("Bloqueada: ya hay 41 cobros registrados en MXN. Cambiarla alteraría el "
                + "significado de esos importes.", text(scene, "currencyHelp"));
        FxHarness.runFx(() -> {
            var sp = (javafx.scene.control.ScrollPane) scene.lookup(".lienzo .scroll-pane");
            sp.setVvalue(1);
        });
        captureAll("fase-2-entrega-1", "moneda-bloqueada");
    }

    // ---------- Fase 2 · Entrega 2: Configuración, métodos de pago ----------

    private void fire(Scene scene, String id) throws Exception {
        FxHarness.runFx(() -> ((javafx.scene.control.ButtonBase) scene.lookup("#" + id)).fire());
        FxHarness.settle();
    }

    private void scrollToBottom(Scene scene) throws Exception {
        FxHarness.runFx(() -> ((javafx.scene.control.ScrollPane) scene.lookup(".lienzo .scroll-pane")).setVvalue(1));
        FxHarness.settle();
    }

    private static String toastText(Scene scene, String id) {
        var toast = scene.lookup("#" + id);
        return toast == null ? null : ((javafx.scene.control.Label) toast.lookup(".label")).getText();
    }

    /** Quita los avisos flotantes que siguen en pantalla, para que la siguiente captura muestre solo el nuevo. */
    private void clearToasts(Scene scene) throws Exception {
        FxHarness.runFx(() -> scene.getRoot().lookupAll(".aviso-flotante").stream().toList()
                .forEach(n -> ((javafx.scene.layout.Pane) n.getParent()).getChildren().remove(n)));
    }

    private void captureDialog(String name) throws Exception {
        for (Themes.Theme theme : Themes.Theme.values()) {
            FxHarness.runFx(() -> themes.set(theme));
            FxHarness.settle();
            Scene dialog = FxHarness.onFx(this::dialogScene);
            FxHarness.capture(dialog, OUT.resolve("fase-2-entrega-2").resolve(name + "-" + theme.name().toLowerCase() + ".png"));
        }
        FxHarness.runFx(() -> themes.set(Themes.Theme.CLARO));
        FxHarness.settle();
    }

    @Test
    void fase2Entrega2MetodosDePago() throws Exception {
        UiFakes.Backend backend = new UiFakes.Backend(staff());
        long efectivo = backend.methods.add("Efectivo", true, true).id();
        long tarjeta = backend.methods.add("Tarjeta", false, true).id();
        long transferencia = backend.methods.add("Transferencia", false, false).id();
        backend.methods.markUsed(efectivo);
        Scene scene = openSettings(backend);
        FxHarness.runFx(() -> ui.shell().setToastTime(javafx.util.Duration.minutes(1)));
        FxHarness.waitUntil("lista de métodos", () -> shown(scene, "mp-tabla"));
        scrollToBottom(scene);
        assertEquals("Pide el efectivo recibido y calcula el cambio",
                FxHarness.onFx(() -> ((javafx.scene.control.Label) scene.lookup("#mp-tabla .celda-detalle")).getText()));
        assertEquals("Activar", text(scene, "mp-estado-" + transferencia), "un inactivo se puede activar");
        captureAll("fase-2-entrega-2", "metodos-de-pago");

        // 1. Teclado: Tab recorre «Agregar método» y las acciones de cada fila, en orden.
        FxHarness.runFx(() -> scene.lookup("#mp-agregar").requestFocus());
        List<String> order = tabOrder(scene, 9);
        System.out.println("[teclado] métodos de pago, Tab: " + order);
        assertEquals(List.of("mp-agregar",
                "mp-editar-" + efectivo, "mp-estado-" + efectivo, "mp-eliminar-" + efectivo,
                "mp-editar-" + tarjeta, "mp-estado-" + tarjeta, "mp-eliminar-" + tarjeta,
                "mp-editar-" + transferencia, "mp-estado-" + transferencia, "mp-eliminar-" + transferencia), order);

        // 2. Agregar: el diálogo abre con el foco en Nombre; un nombre repetido se marca en su campo.
        fire(scene, "mp-agregar");
        FxHarness.waitUntil("diálogo de alta", () -> dialogScene() != null && dialogScene().getFocusOwner() != null
                && "mp-nombre".equals(dialogScene().getFocusOwner().getId()));
        Scene dialog = FxHarness.onFx(this::dialogScene);
        captureDialog("dialogo-agregar");
        typeInto(dialog, "mp-nombre", "tarjeta");
        FxHarness.press(dialog, KeyCode.ENTER, false);
        FxHarness.waitUntil("error de nombre", () -> dialog.lookup(".field-error") != null && dialog.lookup(".field-error").isVisible()
                && !((javafx.scene.control.Label) dialog.lookup(".field-error")).getText().isEmpty());
        assertEquals("Ya existe uno con ese nombre.", FxHarness.onFx(() -> ((javafx.scene.control.Label) dialog.lookup(".field-error")).getText()));
        assertEquals("mp-nombre", FxHarness.focusOwner(dialog));
        captureDialog("dialogo-error");

        // Corregido y enviado con Enter: se cierra, avisa y aparece en su lugar (por nombre).
        typeInto(dialog, "mp-nombre", "Vales de despensa");
        FxHarness.press(dialog, KeyCode.ENTER, false);
        FxHarness.waitUntil("diálogo cerrado", () -> dialogScene() == null);
        FxHarness.waitUntil("fila nueva", () -> scene.lookup("#mp-editar-4") != null);
        assertEquals("Se agregó «Vales de despensa». Ya se ofrece al cobrar.", FxHarness.onFx(() -> toastText(scene, "aviso")));
        scrollToBottom(scene);
        captureAll("fase-2-entrega-2", "agregado");
        clearToasts(scene);

        // 3. Editar: cambiar «es efectivo» en uno ya usado se rechaza en su campo; Esc cancela sin cambios.
        fire(scene, "mp-editar-" + efectivo);
        FxHarness.waitUntil("diálogo de edición", () -> dialogScene() != null);
        Scene edit = FxHarness.onFx(this::dialogScene);
        FxHarness.runFx(() -> ((javafx.scene.control.CheckBox) edit.lookup("#mp-efectivo")).setSelected(false));
        FxHarness.runFx(() -> edit.lookup("#mp-nombre").requestFocus());
        FxHarness.press(edit, KeyCode.ENTER, false);
        FxHarness.waitUntil("error de efectivo", () -> edit.lookup("#mp-efectivo").getStyleClass().contains("invalid"));
        assertEquals("mp-efectivo", FxHarness.focusOwner(edit));
        FxHarness.pressLater(edit, KeyCode.ESCAPE);
        FxHarness.waitUntil("edición cancelada", () -> dialogScene() == null);
        assertEquals(true, backend.methods.get(efectivo).cash());

        // 4. Desactivar al momento, con aviso; el foco se queda en la misma fila («Activar»).
        FxHarness.runFx(() -> scene.lookup("#mp-estado-" + tarjeta).requestFocus());
        FxHarness.press(scene, KeyCode.SPACE, false);
        FxHarness.waitUntil("desactivado", () -> "Activar".equals(((javafx.scene.control.Button) scene.lookup("#mp-estado-" + tarjeta)).getText()));
        assertEquals(false, backend.methods.get(tarjeta).active());
        assertEquals("mp-estado-" + tarjeta, FxHarness.focusOwner(scene));

        clearToasts(scene);

        // 5. Eliminar pide confirmación; uno ya usado se rechaza con el mensaje del caso de uso.
        FxHarness.runFx(() -> scene.lookup("#mp-eliminar-" + efectivo).requestFocus());
        FxHarness.pressLater(scene, KeyCode.SPACE);
        FxHarness.waitUntil("confirmación de borrado", () -> dialogWithButton("Eliminar") != null);
        assertEquals("Cancelar", FxHarness.onFx(() -> {
            var owner = dialogScene().getFocusOwner();
            return owner instanceof javafx.scene.control.Button b ? b.getText() : String.valueOf(owner);
        }), "la confirmación abre en Cancelar");
        captureDialog("confirmar-eliminar");
        clickInDialog(FxHarness.onFx(() -> dialogWithButton("Eliminar")), "Eliminar");
        FxHarness.waitUntil("aviso de error", () -> toastText(scene, "aviso-error") != null);
        assertEquals("No puedes eliminar «Efectivo»: ya se usó en cobros. Desactívalo para dejar de ofrecerlo.",
                FxHarness.onFx(() -> toastText(scene, "aviso-error")));
        scrollToBottom(scene);
        captureAll("fase-2-entrega-2", "eliminar-rechazado");

        // Uno sin uso sí se elimina.
        FxHarness.runFx(() -> scene.lookup("#mp-eliminar-" + transferencia).requestFocus());
        FxHarness.pressLater(scene, KeyCode.SPACE);
        FxHarness.waitUntil("confirmación de borrado", () -> dialogWithButton("Eliminar") != null);
        clickInDialog(FxHarness.onFx(() -> dialogWithButton("Eliminar")), "Eliminar");
        FxHarness.waitUntil("fila quitada", () -> scene.lookup("#mp-editar-" + transferencia) == null);
        assertEquals(null, backend.methods.get(transferencia));
        System.out.println("[teclado] métodos de pago: Enter envía el diálogo; foco al campo con error; Esc cancela; "
                + "Espacio desactiva y el foco sigue en la fila; la confirmación de borrado abre en Cancelar");
    }

    // ---------- Proyecto final: catálogo de Socios ----------

    private UiFakes.Backend gymWithMembers() {
        UiFakes.Backend backend = new UiFakes.Backend(staff());
        long mensual = backend.plans.add("Mensual", 30, "450", null, true).id();
        long anual = backend.plans.add("Anual", 365, "4200", null, true).id();
        Object[][] socios = {
                {"Ana López", 20, mensual}, {"Bruno Díaz", 3, mensual}, {"Carla Ruiz", 200, anual},
                {"Daniel Soto", -10, mensual}, {"Elena Mora", -50, mensual}, {"Fernando Gil", null, null},
                {"Gabriela Paz", 12, mensual}, {"Héctor Ríos", 25, mensual}};
        for (Object[] socio : socios) {
            var member = backend.members.add((String) socio[0], UiFakes.TODAY.minusDays(300));
            if (socio[1] != null) {
                java.time.LocalDate end = UiFakes.TODAY.plusDays((Integer) socio[1]);
                long plan = (Long) socio[2];
                int days = plan == anual ? 365 : 30;
                backend.memberships.add(member.id(), plan, end.minusDays(days), end);
                backend.members.markPaid(member.id());
            }
        }
        return backend;
    }

    @Test
    void proyectoSocios() throws Exception {
        UiFakes.Backend backend = gymWithMembers();
        Scene scene = signIn(backend, "admin");
        FxHarness.runFx(() -> ui.shell().setToastTime(javafx.util.Duration.minutes(1)));
        FxHarness.runFx(() -> scene.lookup("#nav-socios").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("lista de socios", () -> shown(scene, "so-tabla"));
        assertEquals("Socios", title(scene));
        assertEquals("Todos 8", text(scene, "so-filtro-todos"));
        assertEquals("Vencida 1", text(scene, "so-filtro-vencida"));
        captureAll("proyecto-socios", "socios");

        // Teclado: Registrar socio → buscador → filtro (un grupo de opciones: Tab entra una vez) → acciones por fila.
        FxHarness.runFx(() -> scene.lookup("#so-agregar").requestFocus());
        List<String> order = tabOrder(scene, 4);
        System.out.println("[teclado] socios, Tab: " + order);
        assertEquals(List.of("so-agregar", "so-buscar", "so-filtro-todos"), order.subList(0, 3));
        assertTrue(order.get(3).startsWith("so-editar-"), order.get(3));
        // En el filtro, la flecha derecha pasa a «Vigente» y filtra la lista.
        FxHarness.runFx(() -> scene.lookup("#so-filtro-todos").requestFocus());
        FxHarness.press(scene, KeyCode.RIGHT, false);
        FxHarness.waitUntil("filtro con flecha", () -> "Mostrando 4 de 8 socios.".equals(
                ((javafx.scene.control.Label) scene.lookup("#so-resumen")).getText()));
        assertEquals("so-filtro-vigente", FxHarness.focusOwner(scene));
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) scene.lookup("#so-filtro-todos")).fire());

        // Buscar y filtrar.
        typeInto(scene, "so-buscar", "LOPEZ");
        FxHarness.waitUntil("búsqueda", () -> "Mostrando 1 de 8 socios.".equals(
                ((javafx.scene.control.Label) scene.lookup("#so-resumen")).getText()));
        captureAll("proyecto-socios", "busqueda");
        typeInto(scene, "so-buscar", "");
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) scene.lookup("#so-filtro-por-vencer")).fire());
        FxHarness.waitUntil("filtro", () -> "Mostrando 1 de 8 socios.".equals(
                ((javafx.scene.control.Label) scene.lookup("#so-resumen")).getText()));
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) scene.lookup("#so-filtro-todos")).fire());

        // Registrar: errores en sus campos, luego el alta.
        fire(scene, "so-agregar");
        FxHarness.waitUntil("diálogo de alta", () -> dialogScene() != null && dialogScene().getFocusOwner() != null
                && "so-nombre".equals(dialogScene().getFocusOwner().getId()));
        Scene dialog = FxHarness.onFx(this::dialogScene);
        typeInto(dialog, "so-telefono", "555 010");
        typeInto(dialog, "so-correo", "no-es-correo");
        FxHarness.press(dialog, KeyCode.ENTER, false);
        FxHarness.waitUntil("errores", () -> dialog.getRoot().lookupAll(".field-error").stream()
                .filter(javafx.scene.Node::isVisible).count() == 3);
        assertEquals("so-nombre", FxHarness.focusOwner(dialog));
        captureDialogIn("proyecto-socios", "registrar-errores");
        typeInto(dialog, "so-nombre", "Irene Vega");
        typeInto(dialog, "so-telefono", "555 010 2233");
        typeInto(dialog, "so-correo", "irene@correo.mx");
        FxHarness.runFx(() -> dialog.lookup("#so-nombre").requestFocus());
        FxHarness.press(dialog, KeyCode.ENTER, false);
        FxHarness.waitUntil("diálogo cerrado", () -> dialogScene() == null);
        FxHarness.waitUntil("alta en la lista", () -> "Todos 9".equals(
                ((javafx.scene.control.ToggleButton) scene.lookup("#so-filtro-todos")).getText()));
        assertEquals("Se registró a «Irene Vega». Queda sin membresía hasta su primer cobro.",
                FxHarness.onFx(() -> toastText(scene, "aviso")));
        captureAll("proyecto-socios", "registrado");
        clearToasts(scene);

        // Eliminar a un socio con cobros se rechaza con el mensaje del caso de uso.
        long ana = backend.members.findAll().stream().filter(m -> m.fullName().equals("Ana López")).findFirst().orElseThrow().id();
        FxHarness.runFx(() -> scene.lookup("#so-eliminar-" + ana).requestFocus());
        FxHarness.pressLater(scene, KeyCode.SPACE);
        FxHarness.waitUntil("confirmación", () -> dialogWithButton("Eliminar") != null);
        captureDialogIn("proyecto-socios", "confirmar-eliminar");
        clickInDialog(FxHarness.onFx(() -> dialogWithButton("Eliminar")), "Eliminar");
        FxHarness.waitUntil("aviso de error", () -> toastText(scene, "aviso-error") != null);
        assertEquals("No puedes eliminar a «Ana López»: ya tiene cobros registrados y su historial se conserva.",
                FxHarness.onFx(() -> toastText(scene, "aviso-error")));
        captureAll("proyecto-socios", "eliminar-rechazado");
        closeWindow();

        // Caja consulta, pero no registra, edita ni elimina.
        Scene caja = signIn(gymWithMembers(), "carlos");
        FxHarness.runFx(() -> caja.lookup("#nav-socios").requestFocus());
        FxHarness.press(caja, KeyCode.ENTER, false);
        FxHarness.waitUntil("lista de socios", () -> shown(caja, "so-tabla"));
        assertEquals(false, FxHarness.onFx(() -> caja.lookup("#so-agregar") != null));
        assertEquals(false, FxHarness.onFx(() -> caja.getRoot().lookupAll(".button").stream()
                .anyMatch(n -> n.getId() != null && n.getId().startsWith("so-eliminar-"))));
        captureAll("proyecto-socios", "socios-caja");
    }

    private long memberId(UiFakes.Backend backend, String name) {
        return backend.members.findAll().stream().filter(m -> m.fullName().equals(name)).findFirst().orElseThrow().id();
    }

    /** Visible de verdad: el nodo y todos sus contenedores. */
    private static boolean onScreen(Scene scene, String id) {
        for (var node = scene.lookup("#" + id); ; node = node.getParent()) {
            if (node == null) {
                return scene.lookup("#" + id) != null;
            }
            if (!node.isVisible()) {
                return false;
            }
        }
    }

    private static boolean enabled(Scene scene, String id) {
        return onScreen(scene, id) && !scene.lookup("#" + id).isDisabled();
    }

    @Test
    void proyectoCobro() throws Exception {
        UiFakes.Backend backend = gymWithMembers();
        backend.methods.add("Efectivo", true, true);
        backend.methods.add("Tarjeta", false, true);
        backend.methods.add("Transferencia", false, true);
        long mensual = backend.plans.findAll().stream().filter(p -> p.name().equals("Mensual")).findFirst()
                .orElseThrow().id();
        backend.plans.add("Trimestral", 90, "1200", mensual, true);
        long daniel = memberId(backend, "Daniel Soto");
        long fernando = memberId(backend, "Fernando Gil");

        // Caja entra directo a Cobrar membresía.
        Scene scene = signIn(backend, "carlos");
        FxHarness.runFx(() -> ui.shell().setToastTime(javafx.util.Duration.minutes(1)));
        FxHarness.waitUntil("pantalla de cobro", () -> onScreen(scene, "co-buscar") && onScreen(scene, "co-planes"));
        assertEquals("Cobrar membresía", title(scene));
        assertFalse(FxHarness.onFx(() -> enabled(scene, "co-cobrar")), "sin socio no se cobra");
        assertEquals("Elige al socio que va a pagar.", text(scene, "co-falta-dato"));
        captureAll("proyecto-cobro", "inicio");

        // Teclado: buscador → cada plan (aún no hay uno elegido) → método (grupo con Efectivo elegido) → efectivo.
        FxHarness.runFx(() -> scene.lookup("#co-buscar").requestFocus());
        List<String> order = tabOrder(scene, 5);
        System.out.println("[teclado] cobro, Tab: " + order);
        assertEquals("co-buscar", order.get(0));
        assertTrue(order.subList(1, 4).stream().allMatch(id -> id.startsWith("co-plan-")), order.toString());
        assertTrue(order.get(4).startsWith("co-metodo-"), order.toString());
        assertEquals("co-efectivo", order.get(5));

        // Buscar y elegir a un socio con la membresía vencida.
        typeInto(scene, "co-buscar", "soto");
        FxHarness.waitUntil("resultados", () -> onScreen(scene, "co-socio-" + daniel));
        captureAll("proyecto-cobro", "busqueda");
        FxHarness.runFx(() -> scene.lookup("#co-socio-" + daniel).requestFocus());
        FxHarness.press(scene, KeyCode.SPACE, false);
        FxHarness.waitUntil("socio elegido", () -> onScreen(scene, "co-socio") && onScreen(scene, "co-cambiar"));
        // Sin plan aún: el resumen ya sabe desde cuándo, pero no hasta cuándo.
        FxHarness.waitUntil("periodo sin plan", () -> scene.lookup("#co-resumen").lookupAll(".label").stream()
                .anyMatch(n -> ("Desde el " + dev.gimnasio.sistemagimnasio.ui.common.Formats.date(UiFakes.TODAY))
                        .equals(((javafx.scene.control.Label) n).getText())));

        // Plan con el teclado, efectivo insuficiente: dice cuánto falta y no deja cobrar.
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) scene.lookup("#co-plan-" + mensual)).fire());
        typeInto(scene, "co-efectivo", "300");
        FxHarness.waitUntil("faltante", () -> onScreen(scene, "co-falta"));
        assertFalse(FxHarness.onFx(() -> enabled(scene, "co-cobrar")));
        assertEquals("El efectivo recibido no cubre el total.", text(scene, "co-falta-dato"));
        captureAll("proyecto-cobro", "falta-efectivo");

        typeInto(scene, "co-efectivo", "500");
        FxHarness.waitUntil("listo para cobrar", () -> enabled(scene, "co-cobrar") && onScreen(scene, "co-cambio"));
        assertTrue(text(scene, "co-regla").equals("Renovación de una membresía vencida: empieza hoy."), text(scene, "co-regla"));
        assertTrue(text(scene, "co-cobrar").startsWith("Cobrar "), text(scene, "co-cobrar"));
        captureAll("proyecto-cobro", "listo");

        // Enter cobra (botón predeterminado); el recibo reemplaza al resumen y el foco va a «Nuevo cobro».
        FxHarness.runFx(() -> scene.lookup("#co-efectivo").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("recibo", () -> onScreen(scene, "co-folio"));
        assertEquals("GYM-0001", text(scene, "co-folio"));
        assertEquals(1, backend.payments.stored());
        FxHarness.waitUntil("foco en nuevo cobro", () -> scene.getFocusOwner() != null && "co-nuevo".equals(scene.getFocusOwner().getId()));
        assertEquals("Cobro registrado con el folio GYM-0001.", FxHarness.onFx(() -> toastText(scene, "aviso")));
        captureAll("proyecto-cobro", "recibo");
        clearToasts(scene);

        fire(scene, "co-imprimir");
        FxHarness.waitUntil("impreso", () -> !backend.printer.printed.isEmpty() && toastText(scene, "aviso") != null);
        assertEquals("Recibo GYM-0001 enviado a imprimir.", FxHarness.onFx(() -> toastText(scene, "aviso")));
        clearToasts(scene);

        // Nuevo cobro: formulario limpio. Primera membresía: aparece la fecha de inicio.
        FxHarness.press(scene, KeyCode.SPACE, false);
        FxHarness.waitUntil("formulario limpio", () -> onScreen(scene, "co-buscar") && !onScreen(scene, "co-folio"));
        assertEquals("", text(scene, "co-buscar"));
        typeInto(scene, "co-buscar", "fernando");
        FxHarness.waitUntil("resultados", () -> onScreen(scene, "co-socio-" + fernando));
        fire(scene, "co-socio-" + fernando);
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) scene.getRoot().lookupAll(".plan-opcion").stream()
                .filter(n -> n.getId().startsWith("co-plan-") && !n.getId().equals("co-plan-" + mensual))
                .reduce((a, b) -> b).orElseThrow()).fire());
        FxHarness.runFx(() -> scene.getRoot().lookupAll(".toggle-button").stream()
                .filter(n -> n.getId() != null && n.getId().startsWith("co-metodo-"))
                .map(javafx.scene.control.ToggleButton.class::cast)
                .filter(b -> b.getText().equals("Tarjeta")).findFirst().orElseThrow().fire());
        FxHarness.waitUntil("primera membresía", () -> onScreen(scene, "co-inicio") && enabled(scene, "co-cobrar")
                && !onScreen(scene, "co-efectivo"));
        assertTrue(text(scene, "co-regla").startsWith("Primera membresía"), text(scene, "co-regla"));
        captureAll("proyecto-cobro", "primera-membresia");
    }

    /** El gimnasio de Socios con más vencimientos, métodos de pago y dos cobros de hoy. */
    private UiFakes.Backend gymForDashboard() throws Exception {
        UiFakes.Backend backend = gymWithMembers();
        long efectivo = backend.methods.add("Efectivo", true, true).id();
        long tarjeta = backend.methods.add("Tarjeta", false, true).id();
        long mensual = backend.plans.findAll().stream().filter(p -> p.name().equals("Mensual")).findFirst()
                .orElseThrow().id();
        Object[][] more = {{"Iván Cruz", 1}, {"Julia Vela", 6}, {"Karen Ortiz", -3}, {"Luis Mora", -24}};
        for (Object[] socio : more) {
            var member = backend.members.add((String) socio[0], UiFakes.TODAY.minusDays(200));
            java.time.LocalDate end = UiFakes.TODAY.plusDays((Integer) socio[1]);
            backend.memberships.add(member.id(), mensual, end.minusDays(30), end);
            backend.members.markPaid(member.id());
        }
        var caja = backend.users.findAll().stream().filter(u -> u.username().equals("carlos")).findFirst()
                .orElseThrow();
        var cobro = backend.useCases().chargeMembership();
        cobro.execute(caja, new dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipCommand(
                "op-a", memberId(backend, "Fernando Gil"), mensual, tarjeta, null, null));
        cobro.execute(caja, new dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipCommand(
                "op-b", memberId(backend, "Gabriela Paz"), mensual, efectivo,
                dev.gimnasio.sistemagimnasio.domain.shared.Money.of("500"), null));
        return backend;
    }

    @Test
    void proyectoTablero() throws Exception {
        UiFakes.Backend backend = gymForDashboard();
        long karen = memberId(backend, "Karen Ortiz");
        long ivan = memberId(backend, "Iván Cruz");

        // El administrador entra al tablero.
        Scene scene = signIn(backend, "admin");
        FxHarness.runFx(() -> ui.shell().setToastTime(javafx.util.Duration.minutes(1)));
        FxHarness.waitUntil("tablero", () -> onScreen(scene, "tb-tabla-cobros") && onScreen(scene, "tb-cobrar-" + ivan));
        assertEquals("Tablero", title(scene));
        assertEquals("8", text(scene, "tb-activos"), "vigentes y por vencer");
        assertEquals("3", text(scene, "tb-por-vencer"), "Iván (1), Bruno (3) y Julia (6)");
        assertEquals("3", text(scene, "tb-vencidas"), "Karen (-3), Daniel (-10) y Luis (-24); Elena ya es baja");
        assertEquals("$900.00", text(scene, "tb-cobrado"));
        assertEquals("Martes 10 de noviembre de 2026 · la semana termina el domingo 15", text(scene, "tb-fecha"));
        captureAll("proyecto-tablero", "tablero");

        // Teclado: Actualizar → acciones de por vencer (de la más próxima) → acciones de vencidas.
        FxHarness.runFx(() -> scene.lookup("#tb-actualizar").requestFocus());
        List<String> order = tabOrder(scene, 4);
        System.out.println("[teclado] tablero, Tab: " + order);
        assertEquals("tb-actualizar", order.get(0));
        assertEquals("tb-cobrar-" + ivan, order.get(1));
        assertTrue(order.get(4).startsWith("tb-renovar-"), order.toString());
        assertEquals("tb-renovar-" + karen, order.get(4), "la vencida más reciente primero");

        // Renovar a Karen abre el cobro con ella elegida; Enter cobra.
        FxHarness.pressLater(scene, KeyCode.SPACE);
        FxHarness.waitUntil("cobro con el socio", () -> onScreen(scene, "co-socio") && onScreen(scene, "co-planes"));
        assertEquals("Cobrar membresía", title(scene));
        assertEquals(1, FxHarness.onFx(() -> scene.getRoot().lookupAll(".nav-enlace").stream()
                .filter(n -> n.getPseudoClassStates().stream().anyMatch(p -> p.getPseudoClassName().equals("actual")))
                .count()).intValue());
        long mensual = backend.plans.findAll().stream().filter(p -> p.name().equals("Mensual")).findFirst()
                .orElseThrow().id();
        FxHarness.runFx(() -> ((javafx.scene.control.ToggleButton) scene.lookup("#co-plan-" + mensual)).fire());
        typeInto(scene, "co-efectivo", "450");
        FxHarness.waitUntil("listo", () -> enabled(scene, "co-cobrar"));
        assertEquals("Renovación de una membresía vencida: empieza hoy.", text(scene, "co-regla"));
        captureAll("proyecto-tablero", "renovar-desde-tablero");
        FxHarness.runFx(() -> scene.lookup("#co-efectivo").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("recibo", () -> onScreen(scene, "co-folio"));
        assertEquals("GYM-0003", text(scene, "co-folio"));
        clearToasts(scene);

        // De vuelta en el tablero, Karen ya no está vencida y el cobro aparece primero.
        FxHarness.runFx(() -> scene.lookup("#nav-tablero").requestFocus());
        FxHarness.press(scene, KeyCode.ENTER, false);
        FxHarness.waitUntil("tablero actualizado", () -> onScreen(scene, "tb-tabla-cobros")
                && "2".equals(((javafx.scene.control.Label) scene.lookup("#tb-vencidas")).getText()));
        assertEquals("$1,350.00", text(scene, "tb-cobrado"));
        assertEquals(false, FxHarness.onFx(() -> scene.lookup("#tb-renovar-" + karen) != null));
        captureAll("proyecto-tablero", "despues-del-cobro");
        closeWindow();

        // Sin cobros ni socios: los vacíos dicen qué pasa.
        Scene empty = signIn(new UiFakes.Backend(staff()), "rocio");
        FxHarness.waitUntil("tablero vacío", () -> onScreen(empty, "tb-tabla-cobros")
                && "0".equals(((javafx.scene.control.Label) empty.lookup("#tb-activos")).getText()));
        assertEquals("Sin cobros hoy", FxHarness.onFx(() -> ((javafx.scene.control.Label) empty.lookup("#tb-cobrado")
                .getParent().getChildrenUnmodifiable().getLast()).getText()));
        captureAll("proyecto-tablero", "vacio");
    }

    private void captureDialogIn(String folder, String name) throws Exception {
        for (Themes.Theme theme : Themes.Theme.values()) {
            FxHarness.runFx(() -> themes.set(theme));
            FxHarness.settle();
            Scene dialog = FxHarness.onFx(this::dialogScene);
            FxHarness.capture(dialog, OUT.resolve(folder).resolve(name + "-" + theme.name().toLowerCase() + ".png"));
        }
        FxHarness.runFx(() -> themes.set(Themes.Theme.CLARO));
        FxHarness.settle();
    }
}
