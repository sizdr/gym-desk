package dev.gimnasio.sistemagimnasio.ui.startup;

import dev.gimnasio.sistemagimnasio.application.usecase.gyminfo.GymInfo;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;

/**
 * Conecta con la base, aplica las migraciones y lee los datos del gimnasio. Si PostgreSQL no responde
 * o falta configuración, muestra la causa y deja reintentar sin cerrar la app.
 */
public final class StartupController {
    static final String CONNECTING = "Conectando con la base de datos…";
    static final String FAILED = "No se pudo abrir la base de datos.";
    static final String HINT = "Revisa que PostgreSQL esté encendido y que DB_URL, DB_USER y DB_PASSWORD sean correctos.";
    private static final System.Logger LOG = System.getLogger(StartupController.class.getName());

    private record Connected(UseCases useCases, GymInfo gymInfo) {
    }

    private final Background background;
    private final Callable<UseCases> connector;
    private final BiConsumer<UseCases, GymInfo> onReady;
    private final Runnable onExit;

    @FXML
    private ProgressIndicator progress;
    @FXML
    private Label status;
    @FXML
    private Label detail;
    @FXML
    private HBox actions;
    @FXML
    private Button retryButton;

    public StartupController(Background background, Callable<UseCases> connector,
                             BiConsumer<UseCases, GymInfo> onReady, Runnable onExit) {
        this.background = Objects.requireNonNull(background, "background");
        this.connector = Objects.requireNonNull(connector, "connector");
        this.onReady = Objects.requireNonNull(onReady, "onReady");
        this.onExit = Objects.requireNonNull(onExit, "onExit");
    }

    @FXML
    private void initialize() {
        for (Node node : new Node[]{progress, detail, actions}) {
            node.managedProperty().bind(node.visibleProperty());
        }
    }

    public void connect() {
        showConnecting();
        background.run(() -> {
            UseCases useCases = connector.call();
            return new Connected(useCases, useCases.getGymInfo().execute());
        }, connected -> onReady.accept(connected.useCases(), connected.gymInfo()), this::showFailure);
    }

    @FXML
    private void retry() {
        connect();
    }

    @FXML
    private void exit() {
        onExit.run();
    }

    private void showConnecting() {
        progress.setVisible(true);
        status.setText(CONNECTING);
        detail.setVisible(false);
        actions.setVisible(false);
    }

    private void showFailure(Throwable error) {
        LOG.log(System.Logger.Level.ERROR, FAILED, error);
        progress.setVisible(false);
        status.setText(FAILED);
        detail.setText(rootMessage(error) + "\n\n" + HINT);
        detail.setVisible(true);
        actions.setVisible(true);
        retryButton.requestFocus();
    }

    /** El mensaje más específico de la cadena de causas, por ejemplo «Connection to localhost:5432 refused». */
    static String rootMessage(Throwable error) {
        String message = null;
        for (Throwable cause = error; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && !cause.getMessage().isBlank()) {
                message = cause.getMessage().strip();
            }
        }
        return message == null ? error.getClass().getSimpleName() : message;
    }
}
