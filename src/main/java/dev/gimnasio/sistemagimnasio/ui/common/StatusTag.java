package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.control.Label;
import javafx.scene.shape.Circle;

/**
 * Etiqueta de estado del prototipo: texto, color y un punto, para que el estado no dependa solo del color.
 * Los colores salen de {@code .estado-<tipo>} en app.css.
 */
public final class StatusTag {
    private StatusTag() {
    }

    /** @param kind {@code activo}, {@code inactivo}, {@code ok}, {@code bajo}, {@code agotado}… */
    public static Label create(String text, String kind) {
        Circle dot = new Circle(3.5);
        dot.getStyleClass().add("estado-punto");
        Label tag = new Label(text, dot);
        tag.getStyleClass().addAll("estado", "estado-" + kind);
        tag.setMinWidth(Label.USE_PREF_SIZE);
        return tag;
    }
}
