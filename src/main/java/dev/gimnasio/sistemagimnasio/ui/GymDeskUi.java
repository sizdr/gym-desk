package dev.gimnasio.sistemagimnasio.ui;

import dev.gimnasio.sistemagimnasio.application.usecase.gyminfo.GymInfo;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.SettingsView;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.AppWindow;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.FxmlView;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.common.Session;
import dev.gimnasio.sistemagimnasio.ui.common.Themes;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import dev.gimnasio.sistemagimnasio.ui.login.LoginController;
import dev.gimnasio.sistemagimnasio.ui.login.LoginViewModel;
import dev.gimnasio.sistemagimnasio.ui.settings.PaymentMethodsController;
import dev.gimnasio.sistemagimnasio.ui.settings.PaymentMethodsViewModel;
import dev.gimnasio.sistemagimnasio.ui.settings.SettingsController;
import dev.gimnasio.sistemagimnasio.ui.settings.SettingsViewModel;
import dev.gimnasio.sistemagimnasio.ui.shell.Route;
import dev.gimnasio.sistemagimnasio.ui.shell.Screen;
import dev.gimnasio.sistemagimnasio.ui.shell.ShellController;
import dev.gimnasio.sistemagimnasio.ui.startup.StartupController;
import javafx.application.Platform;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.Function;

/** Entrada de la capa de UI: abre la ventana y decide qué pantalla se muestra. */
public final class GymDeskUi implements AutoCloseable {
    static final String SETTINGS_SAVED = "Configuración guardada. Ya se ve en el menú y en el acceso.";

    private final Stage stage;
    private final Callable<UseCases> connector;
    private final Themes themes;
    private final Session session = new Session();
    private AppWindow window;
    private UseCases useCases;
    private GymInfo gymInfo;
    private Background background;
    private ShellController shell;
    private Function<Window, File> logoChooser = SettingsController::chooseImage;
    private PaymentMethodsController paymentMethods;
    /** Los avisos van al menú de la sesión abierta. */
    private final Notifier notifier = new Notifier() {
        @Override
        public void ok(String message) {
            if (shell != null) {
                shell.ok(message);
            }
        }

        @Override
        public void error(String message) {
            if (shell != null) {
                shell.error(message);
            }
        }
    };

    /** @param connector conecta con la base y arma los casos de uso; se llama fuera del hilo de JavaFX */
    public GymDeskUi(Stage stage, Callable<UseCases> connector) {
        this(stage, connector, Themes.fromUserPreferences());
    }

    public GymDeskUi(Stage stage, Callable<UseCases> connector, Themes themes) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.themes = Objects.requireNonNull(themes, "themes");
    }

    public void start() {
        window = new AppWindow(stage, themes);
        themes.installOnNewWindows();
        background = Background.start();
        showStartup();
        // Cerrar la ventana con cambios sin guardar pide confirmación, igual que cambiar de sección.
        stage.setOnCloseRequest(event -> {
            if (shell != null && session.isOpen() && !shell.canLeave()) {
                event.consume();
            }
        });
        window.show();
    }

    /** Cambia el selector de archivos del logotipo (en pruebas, sin abrir el del sistema). */
    public void useLogoChooser(Function<Window, File> chooser) {
        this.logoChooser = Objects.requireNonNull(chooser, "chooser");
    }

    /** El panel de métodos de pago de la última Configuración abierta, o {@code null} (pruebas). */
    public PaymentMethodsController paymentMethods() {
        return paymentMethods;
    }

    /** El menú de la sesión abierta, o {@code null}. */
    public ShellController shell() {
        return shell;
    }

    public AppWindow window() {
        return window;
    }

    public Session session() {
        return session;
    }

    private void showStartup() {
        var startup = new StartupController(background, connector, this::onConnected, Platform::exit);
        window.setContent(FxmlView.load("startup", startup));
        startup.connect();
    }

    private void onConnected(UseCases connected, GymInfo info) {
        this.useCases = connected;
        this.gymInfo = info;
        window.setGymName(info.gymName());
        showLogin();
    }

    private void showLogin() {
        var model = new LoginViewModel(useCases.authenticate(), background, this::onSignedIn);
        window.setContent(FxmlView.load("login", new LoginController(gymInfo, themes, model)));
    }

    private void onSignedIn(User user) {
        session.open(user);
        // La fecha de la cabecera sale del reloj del sistema: se vuelve a leer al entrar.
        background.run(useCases.getGymInfo()::execute, this::showShell, error -> showShell(gymInfo));
    }

    private void showShell(GymInfo info) {
        if (!session.isOpen()) {
            return;
        }
        gymInfo = info;
        window.setGymName(info.gymName());
        shell = new ShellController(info, session.actor(), themes, this::signOut, this::screenFor);
        window.setContent(FxmlView.load("shell", shell));
    }

    /** Las secciones que ya funcionan; las demás muestran qué les falta. */
    private Screen screenFor(Route route) {
        return switch (route) {
            case CONFIGURACION -> settingsScreen();
            default -> null;
        };
    }

    private Screen settingsScreen() {
        var model = new SettingsViewModel(useCases.getSettings(), useCases.updateSettings(), background,
                session::actor, this::onSettingsSaved);
        // Se consulta en cada clic: así se puede cambiar el selector con la pantalla ya abierta.
        var controller = new SettingsController(model, owner -> logoChooser.apply(owner));
        FxmlView.load("settings", controller);
        var methods = new PaymentMethodsViewModel(useCases, background, session::actor, notifier);
        paymentMethods = new PaymentMethodsController(methods);
        controller.addPanel(paymentMethods.view());
        model.load();
        methods.load();
        return new Screen(controller.view(), controller.saveBar(), model::hasUnsavedChanges);
    }

    /** El nombre y el logotipo nuevos se ven enseguida en el menú, el título de la ventana y el acceso. */
    private void onSettingsSaved(SettingsView saved) {
        if (shell != null) {
            shell.showToast(SETTINGS_SAVED);
        }
        background.run(useCases.getGymInfo()::execute, info -> {
            gymInfo = info;
            window.setGymName(info.gymName());
            if (shell != null) {
                shell.updateBrand(info);
            }
        }, ErrorReport::of);
    }

    /** Cerrar sesión no es un caso de uso: se descarta el actor y se vuelve al acceso. */
    private void signOut() {
        session.close();
        shell = null;
        showLogin();
    }

    @Override
    public void close() {
        if (background != null) {
            background.close();
        }
    }
}
