package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;

/** Diálogos modales con los textos, botones y tema de la app. */
public final class Dialogs {
    /** Atajos que ya funcionan. Los del prototipo para buscar, cobrar y vender llegan con sus pantallas. */
    static final List<List<String>> SHORTCUTS = List.of(
            List.of("Tab", "Pasar al siguiente control"),
            List.of("Mayús+Tab", "Volver al control anterior"),
            List.of("Enter", "Confirmar el formulario, por ejemplo Entrar o Guardar cambios"),
            List.of("Espacio", "Pulsar el botón que tiene el foco"),
            List.of("←", "→", "Cambiar de tema en el selector de tema"),
            List.of("Esc", "Cerrar un diálogo"),
            List.of("?", "Ver estos atajos"));

    private Dialogs() {
    }

    public static void error(Window owner, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR, message, new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        prepare(alert, owner, "No se pudo completar");
        alert.showAndWait();
    }

    /** @return {@code true} si se eligió {@code confirmText} */
    public static boolean confirm(Window owner, String title, String message, String confirmText) {
        return confirm(owner, title, message, confirmText, "Cancelar");
    }

    /** @return {@code true} si se eligió {@code confirmText}; Esc y {@code cancelText} cuentan como no */
    public static boolean confirm(Window owner, String title, String message, String confirmText, String cancelText) {
        Alert alert = confirmation(owner, title, message, confirmText, cancelText);
        return alert.showAndWait().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent();
    }

    /** Como {@link #confirm(Window, String, String, String, String)}, con el botón de confirmar en rojo. */
    public static boolean confirmDanger(Window owner, String title, String message, String confirmText) {
        Alert alert = confirmation(owner, title, message, confirmText, "Cancelar");
        alert.getDialogPane().getButtonTypes().stream()
                .filter(type -> type.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                .forEach(type -> alert.getDialogPane().lookupButton(type).getStyleClass().add("peligro"));
        return alert.showAndWait().filter(button -> button.getButtonData() == ButtonBar.ButtonData.OK_DONE).isPresent();
    }

    /**
     * La confirmación sin mostrar, para poder abrirla sin bloquear (pruebas y capturas). Cancelar es el botón
     * predeterminado: Enter no confirma por accidente algo que se pierde.
     */
    public static Alert confirmation(Window owner, String title, String message, String confirmText, String cancelText) {
        ButtonType confirm = new ButtonType(confirmText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType(cancelText, ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, cancel, confirm);
        prepare(alert, owner, title);
        alert.setHeaderText(title);
        alert.setGraphic(null);
        ((javafx.scene.control.Button) alert.getDialogPane().lookupButton(confirm)).setDefaultButton(false);
        ((javafx.scene.control.Button) alert.getDialogPane().lookupButton(cancel)).setDefaultButton(true);
        return alert;
    }

    /** Lista de atajos de teclado, como el diálogo «Atajos de teclado» del prototipo. */
    public static void shortcuts(Window owner) {
        shortcutsDialog(owner).showAndWait();
    }

    /** El diálogo sin mostrar, para poder abrirlo sin bloquear (pruebas y capturas). */
    public static Dialog<ButtonType> shortcutsDialog(Window owner) {
        GridPane grid = new GridPane();
        grid.getStyleClass().add("atajos-lista");
        int row = 0;
        for (List<String> shortcut : SHORTCUTS) {
            HBox keys = new HBox(4);
            for (String key : shortcut.subList(0, shortcut.size() - 1)) {
                Label kbd = new Label(key);
                kbd.getStyleClass().add("kbd");
                keys.getChildren().add(kbd);
            }
            grid.addRow(row++, keys, new Label(shortcut.getLast()));
        }
        Label note = new Label("Los atajos para buscar socios, cobrar y vender llegan con sus pantallas.");
        note.getStyleClass().addAll("chico", "texto-3");
        note.setWrapText(true);

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Atajos de teclado");
        dialog.setHeaderText("Atajos de teclado");
        dialog.getDialogPane().setContent(new VBox(14, grid, note));
        dialog.getDialogPane().getButtonTypes().add(new ButtonType("Entendido", ButtonBar.ButtonData.OK_DONE));
        dialog.getDialogPane().getStyleClass().add("dialogo-atajos");
        dialog.getDialogPane().getStylesheets().add(AppWindow.stylesheet());
        if (owner != null) {
            dialog.initOwner(owner);
        }
        return dialog;
    }

    private static void prepare(Alert alert, Window owner, String title) {
        if (owner != null) {
            alert.initOwner(owner);
        }
        alert.setTitle(title);
        alert.setHeaderText(null);
        // El tema lo pone Themes al abrirse la ventana del diálogo.
        alert.getDialogPane().getStylesheets().add(AppWindow.stylesheet());
    }
}
