package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Scale;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Iconos de trazo de Lucide (licencia ISC, ver {@code /icons/LICENSE-lucide.txt}), los mismos del prototipo.
 * Cada icono es un {@link SVGPath} con clase {@code icono}: el CSS le da el color y el grosor del trazo.
 */
public final class Icons {
    private static final double CANVAS = 24;
    private static final Map<String, String> PATHS = load();

    private Icons() {
    }

    /** Icono de 18 px, el tamaño del prototipo junto a texto de 14.5 px. */
    public static Node icon(String name) {
        return icon(name, 18);
    }

    public static Node icon(String name, double size) {
        String path = PATHS.get(name);
        if (path == null) {
            throw new IllegalArgumentException("No existe el icono «" + name + "». Disponibles: " + PATHS.keySet());
        }
        SVGPath shape = new SVGPath();
        shape.setContent(path);
        shape.getStyleClass().add("icono");
        double factor = size / CANVAS;
        shape.getTransforms().add(new Scale(factor, factor, 0, 0));

        // El SVGPath se dibuja sobre un lienzo de 24 × 24; el contenedor fija el tamaño final y lo centra.
        StackPane box = new StackPane(new Group(shape));
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        box.getStyleClass().add("icono-caja");
        return box;
    }

    /** Nombres disponibles, en orden alfabético. */
    public static java.util.SortedSet<String> names() {
        return new java.util.TreeSet<>(PATHS.keySet());
    }

    private static Map<String, String> load() {
        Properties properties = new Properties();
        try (InputStream in = Icons.class.getResourceAsStream("/icons/lucide.properties")) {
            if (in == null) {
                throw new IllegalStateException("Falta /icons/lucide.properties");
            }
            properties.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        Map<String, String> paths = new TreeMap<>();
        properties.forEach((key, value) -> paths.put((String) key, (String) value));
        return Map.copyOf(paths);
    }
}
