package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;

/**
 * Carga {@code /fxml/<nombre>.fxml} con un controlador ya construido. Los FXML no declaran
 * {@code fx:controller}: así cada controlador recibe sus dependencias por constructor.
 */
public final class FxmlView {
    private FxmlView() {
    }

    public static Parent load(String name, Object controller) {
        URL url = FxmlView.class.getResource("/fxml/" + name + ".fxml");
        if (url == null) {
            throw new IllegalStateException("No existe la vista /fxml/" + name + ".fxml");
        }
        FXMLLoader loader = new FXMLLoader(url);
        loader.setController(controller);
        try {
            return loader.load();
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo cargar la vista " + name, e);
        }
    }
}
