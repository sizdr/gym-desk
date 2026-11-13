package dev.gimnasio.sistemagimnasio.ui.shell;

import javafx.scene.Node;
import javafx.scene.Parent;

import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Una pantalla que funciona, lista para ponerse en el área de contenido del menú.
 *
 * @param view              el contenido, que se desplaza
 * @param footer            algo que queda fijo abajo mientras el contenido se desplaza (por ejemplo la barra
 *                          «Tienes cambios sin guardar»), o {@code null}
 * @param hasUnsavedChanges {@code true} si salir de la pantalla perdería algo escrito; el menú pide confirmación
 */
public record Screen(Parent view, Node footer, BooleanSupplier hasUnsavedChanges) {
    public Screen {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(hasUnsavedChanges, "hasUnsavedChanges");
    }

    /** Una pantalla sin barra fija ni cambios que perder. */
    public static Screen of(Parent view) {
        return new Screen(view, null, () -> false);
    }
}
