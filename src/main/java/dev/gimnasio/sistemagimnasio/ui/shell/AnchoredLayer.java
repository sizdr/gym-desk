package dev.gimnasio.sistemagimnasio.ui.shell;

import javafx.geometry.Bounds;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import java.util.Objects;

/**
 * Capa que cubre el contenido y coloca un panel justo debajo de otro nodo (el de los resultados de la búsqueda,
 * debajo de su cápsula). Fuera del panel no atrapa clics, así que el contenido sigue usable.
 */
final class AnchoredLayer extends Pane {
    private static final double GAP = 6;
    private static final double MIN_WIDTH = 380;
    private static final double BOTTOM_MARGIN = 16;

    private final Region anchor;
    private final Region panel;

    AnchoredLayer(Region anchor, Region panel) {
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.panel = Objects.requireNonNull(panel, "panel");
        setPickOnBounds(false);
        getChildren().add(panel);
        // Al cambiar el ancho de la ventana se mueve la cápsula: el panel la sigue.
        anchor.boundsInParentProperty().addListener((obs, old, bounds) -> requestLayout());
        anchor.layoutBoundsProperty().addListener((obs, old, bounds) -> requestLayout());
    }

    @Override
    protected void layoutChildren() {
        if (anchor.getScene() == null || getScene() == null) {
            return;
        }
        Bounds below = sceneToLocal(anchor.localToScene(anchor.getLayoutBounds()));
        double width = Math.min(Math.max(below.getWidth(), MIN_WIDTH), getWidth() - below.getMinX() - BOTTOM_MARGIN);
        double top = below.getMaxY() + GAP;
        double height = Math.min(panel.prefHeight(width), Math.max(0, getHeight() - top - BOTTOM_MARGIN));
        panel.resizeRelocate(below.getMinX(), top, width, height);
    }
}
