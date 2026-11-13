package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Window;

import java.util.Objects;

/**
 * Diálogo con un formulario, como los del prototipo («Agregar método de pago», «Editar plan»…). El botón
 * principal no cierra el diálogo: llama a {@code onSubmit}, que guarda en segundo plano; quien lo usa cierra
 * el diálogo con {@link #close()} cuando el caso de uso responde bien. Así los errores por campo se ven sin
 * perder lo escrito. Enter envía y Esc cancela.
 */
public final class FormDialog {
    private final Dialog<ButtonType> dialog = new Dialog<>();
    private final Button primary;
    private final Button cancel;
    private ObservableBooleanValue busy;

    public FormDialog(Window owner, String title, Node content, String primaryText, Runnable onSubmit) {
        Objects.requireNonNull(onSubmit, "onSubmit");
        dialog.setTitle(title);
        dialog.setHeaderText(title);
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getStyleClass().add("dialogo-formulario");
        dialog.getDialogPane().getStylesheets().add(AppWindow.stylesheet());
        ButtonType submitType = new ButtonType(primaryText, ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelType = new ButtonType("Cancelar", ButtonBar.ButtonData.CANCEL_CLOSE);
        dialog.getDialogPane().getButtonTypes().addAll(cancelType, submitType);
        primary = (Button) dialog.getDialogPane().lookupButton(submitType);
        cancel = (Button) dialog.getDialogPane().lookupButton(cancelType);
        primary.setId("dialogo-enviar");
        cancel.setId("dialogo-cancelar");
        primary.addEventFilter(ActionEvent.ACTION, event -> {
            event.consume();
            if (busy == null || !busy.get()) {
                onSubmit.run();
            }
        });
        // Mientras guarda, Esc y la «X» no cierran: la respuesta llegaría a un diálogo que ya no existe.
        dialog.setOnCloseRequest(event -> {
            if (busy != null && busy.get()) {
                event.consume();
            }
        });
        if (owner != null) {
            dialog.initOwner(owner);
        }
    }

    /** Mientras {@code busy}, el botón principal muestra {@code busyText} con un indicador y nada se puede pulsar. */
    public void bindBusy(ObservableBooleanValue busy, String busyText) {
        this.busy = busy;
        String idle = primary.getText();
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("progreso-boton");
        primary.textProperty().bind(Bindings.when(busy).then(busyText).otherwise(idle));
        primary.graphicProperty().bind(Bindings.when(busy).<Node>then(spinner).otherwise((Node) null));
        primary.disableProperty().bind(busy);
        cancel.disableProperty().bind(busy);
    }

    /** Sin bloquear: el resultado llega por {@code onSubmit} y {@link #close()}. */
    public void show() {
        dialog.show();
    }

    public void close() {
        dialog.close();
    }

    public Dialog<ButtonType> dialog() {
        return dialog;
    }
}
