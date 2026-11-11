package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.Objects;

/**
 * La ventana principal: una sola escena con la hoja de estilos y el tema de la app, cuyo contenido se reemplaza.
 * 1200 × 760 al abrir; no se puede achicar por debajo de 1024 × 680, el mínimo para el que se diseña cada pantalla.
 */
public final class AppWindow {
    static final String APP_NAME = "Sistema Gimnasio";
    public static final double WIDTH = 1200;
    public static final double HEIGHT = 760;
    public static final double MIN_WIDTH = 1024;
    public static final double MIN_HEIGHT = 680;
    private static final String STYLESHEET = "/css/app.css";

    private final Stage stage;
    private final Scene scene;
    private final Themes themes;

    public AppWindow(Stage stage, Themes themes) {
        this.stage = Objects.requireNonNull(stage, "stage");
        this.themes = Objects.requireNonNull(themes, "themes");
        Fonts.load();
        this.scene = new Scene(new StackPane(), WIDTH, HEIGHT);
        scene.getStylesheets().add(stylesheet());
        themes.apply(scene.getRoot());
        stage.setScene(scene);
        stage.setMinWidth(MIN_WIDTH);
        stage.setMinHeight(MIN_HEIGHT);
        stage.setTitle(APP_NAME);
    }

    /** URL de la hoja de estilos, para las escenas de los diálogos. */
    public static String stylesheet() {
        return Objects.requireNonNull(AppWindow.class.getResource(STYLESHEET), "Falta " + STYLESHEET).toExternalForm();
    }

    public void show() {
        stage.show();
    }

    public void setContent(Parent content) {
        themes.apply(content);
        scene.setRoot(content);
    }

    /** «Mi gimnasio · Sistema Gimnasio». */
    public void setGymName(String gymName) {
        stage.setTitle(gymName == null || gymName.isBlank() ? APP_NAME : gymName + " · " + APP_NAME);
    }

    public Window window() {
        return stage;
    }

    public Scene scene() {
        return scene;
    }
}
