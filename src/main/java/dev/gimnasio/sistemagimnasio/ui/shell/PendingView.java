package dev.gimnasio.sistemagimnasio.ui.shell;

import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;
import javafx.scene.text.TextAlignment;

/**
 * Contenido de una sección que todavía no funciona. Dice qué le falta, sin datos de ejemplo que aparenten
 * un funcionamiento que no existe.
 */
final class PendingView {
    static final String NO_USE_CASES = "Esta sección todavía no funciona";
    static final String SCREEN_PENDING = "Pantalla en preparación";

    private PendingView() {
    }

    static Parent create(Route route) {
        Label title = new Label(route.title());
        title.getStyleClass().add("titulo-30");
        title.setId("titulo-pagina");
        Label sub = new Label(route.description());
        sub.getStyleClass().addAll("sub", "texto-2");
        sub.setWrapText(true);
        VBox heading = new VBox(6, title, sub);
        heading.getStyleClass().add("encabezado");

        boolean missing = route.status() == Route.Status.MISSING_USE_CASES;
        Label emptyTitle = new Label(missing ? NO_USE_CASES : SCREEN_PENDING);
        emptyTitle.getStyleClass().add("vacio-titulo");
        emptyTitle.setId("estado-pendiente");
        Label reason = new Label(missing
                ? "Faltan sus casos de uso. Cuando existan, esta pantalla mostrará los datos reales del gimnasio."
                : "Sus casos de uso ya existen; la pantalla se construye en la siguiente fase.");
        reason.setWrapText(true);
        reason.setTextAlignment(TextAlignment.CENTER);
        Label detail = new Label((missing ? "Pendiente: " : "Disponibles: ") + String.join(", ", route.useCases()) + ".");
        detail.getStyleClass().addAll("chico", "texto-3");
        detail.setWrapText(true);
        detail.setTextAlignment(TextAlignment.CENTER);
        detail.setId("casos-de-uso");

        VBox empty = new VBox(Icons.icon(route.icon(), 28), emptyTitle, reason, detail);
        empty.getStyleClass().add("vacio");
        empty.setAlignment(Pos.CENTER);
        VBox panel = new VBox(empty);
        panel.getStyleClass().add("panel");

        VBox content = new VBox(heading, panel);
        content.getStyleClass().add("contenido");
        content.setAccessibleText(route.title() + ": " + emptyTitle.getText());
        return content;
    }
}
