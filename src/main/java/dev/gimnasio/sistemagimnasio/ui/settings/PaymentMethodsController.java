package dev.gimnasio.sistemagimnasio.ui.settings;

import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.ui.common.DataTable;
import dev.gimnasio.sistemagimnasio.ui.common.Dialogs;
import dev.gimnasio.sistemagimnasio.ui.common.FieldErrors;
import dev.gimnasio.sistemagimnasio.ui.common.FormDialog;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import dev.gimnasio.sistemagimnasio.ui.common.StatusTag;
import javafx.application.Platform;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Objects;
import java.util.function.BiPredicate;

/**
 * Panel «Métodos de pago» de Configuración: la lista con sus acciones y el diálogo de alta y edición.
 * Va en su propio panel, aparte de «Guardar cambios», porque cada cambio de la lista se guarda al momento.
 */
public final class PaymentMethodsController {
    static final String CASH_DETAIL = "Pide el efectivo recibido y calcula el cambio";
    private static final double FORM_WIDTH = 420;

    private final PaymentMethodsViewModel model;
    private final VBox panel = new VBox();
    private final DataTable<PaymentMethodItem> table;
    private final Button add = new Button("Agregar método", Icons.icon("mas", 16));
    private BiPredicate<Window, PaymentMethodItem> confirmDelete = PaymentMethodsController::askToDelete;
    private FormDialog dialog;

    public PaymentMethodsController(PaymentMethodsViewModel model) {
        this.model = Objects.requireNonNull(model, "model");
        Label empty = new Label("No hay métodos de pago. Agrega al menos uno para poder cobrar.");
        empty.getStyleClass().addAll("texto-2", "tabla-vacia");
        table = new DataTable<>(List.of(
                DataTable.Column.grow("Método", this::nameCell),
                DataTable.Column.fit("Estado", item -> item.active()
                        ? StatusTag.create("Activo", "activo") : StatusTag.create("Inactivo", "inactivo")),
                DataTable.Column.actions(this::actionsCell)), empty);
        table.setDimmed(item -> !item.active());
        build();
        model.editorOpenProperty().addListener((obs, was, open) -> {
            if (open) {
                openDialog();
            } else if (dialog != null) {
                FormDialog closing = dialog;
                dialog = null;
                closing.close();
            }
        });
    }

    public VBox view() {
        return panel;
    }

    /** Cambia cómo se confirma un borrado (en pruebas, sin abrir el diálogo). */
    public void confirmDeleteWith(BiPredicate<Window, PaymentMethodItem> confirmation) {
        this.confirmDelete = Objects.requireNonNull(confirmation, "confirmation");
    }

    private void build() {
        Label title = new Label("Métodos de pago");
        title.getStyleClass().add("titulo-16");
        Label description = new Label("Aparecen al cobrar membresías y al vender. Los cambios de esta lista se "
                + "aplican al momento; desactivar uno no cambia los cobros ni las ventas ya registrados.");
        description.getStyleClass().addAll("t-13", "texto-3");
        description.setWrapText(true);
        VBox heading = new VBox(4, title, description);
        HBox.setHgrow(heading, Priority.ALWAYS);
        add.setId("mp-agregar");
        add.setMinWidth(Region.USE_PREF_SIZE);
        add.setOnAction(e -> model.startCreate());
        add.disableProperty().bind(model.stateProperty().isNotEqualTo(PaymentMethodsViewModel.State.READY));
        HBox header = new HBox(16, heading, add);
        header.getStyleClass().add("panel-cab");
        header.setAlignment(Pos.TOP_LEFT);

        HBox loading = new HBox(12, progress(), new Label("Cargando los métodos de pago…"));
        loading.getStyleClass().add("cargando");
        loading.visibleProperty().bind(model.stateProperty().isEqualTo(PaymentMethodsViewModel.State.LOADING));
        loading.managedProperty().bind(loading.visibleProperty());

        Label loadErrorText = new Label();
        loadErrorText.textProperty().bind(model.loadErrorProperty());
        loadErrorText.setWrapText(true);
        loadErrorText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(loadErrorText, Priority.ALWAYS);
        Button retry = new Button("Reintentar");
        retry.getStyleClass().add("chico");
        retry.setOnAction(e -> model.load());
        HBox loadError = new HBox(10, Icons.icon("alerta"), loadErrorText, retry);
        loadError.getStyleClass().addAll("nota", "nota-error");
        VBox.setMargin(loadError, new javafx.geometry.Insets(0, 20, 20, 20));
        loadError.visibleProperty().bind(model.stateProperty().isEqualTo(PaymentMethodsViewModel.State.LOAD_FAILED));
        loadError.managedProperty().bind(loadError.visibleProperty());

        table.visibleProperty().bind(model.stateProperty().isEqualTo(PaymentMethodsViewModel.State.READY));
        table.managedProperty().bind(table.visibleProperty());
        table.setId("mp-tabla");
        model.items().addListener((ListChangeListener<PaymentMethodItem>) change -> refreshTable());
        refreshTable();

        panel.getChildren().addAll(header, loading, loadError, table);
        panel.getStyleClass().addAll("panel", "panel-catalogo");
        panel.setId("mp-panel");
        panel.setAccessibleText("Métodos de pago");
    }

    /** Reconstruye las filas conservando el foco en la acción equivalente de la misma fila. */
    private void refreshTable() {
        Node focused = panel.getScene() == null ? null : panel.getScene().getFocusOwner();
        String focusedId = focused != null && focused.getId() != null && focused.getId().startsWith("mp-") ? focused.getId() : null;
        table.setItems(List.copyOf(model.items()));
        if (focusedId != null && panel.getScene() != null) {
            Node again = panel.getScene().lookup("#" + focusedId);
            (again != null ? again : add).requestFocus();
        }
    }

    private Node nameCell(PaymentMethodItem item) {
        Label name = new Label(item.name());
        name.getStyleClass().add("celda-principal");
        name.setWrapText(true);
        VBox cell = new VBox(2, name);
        if (item.cash()) {
            Label detail = new Label(CASH_DETAIL);
            detail.getStyleClass().add("celda-detalle");
            detail.setWrapText(true);
            cell.getChildren().add(detail);
        }
        return cell;
    }

    private Node actionsCell(PaymentMethodItem item) {
        Button edit = rowButton("Editar", "mp-editar-" + item.id(), "Editar " + item.name(), "texto");
        edit.setOnAction(e -> model.startEdit(item));
        Button toggle = rowButton(item.active() ? "Desactivar" : "Activar", "mp-estado-" + item.id(),
                (item.active() ? "Desactivar " : "Activar ") + item.name(), "texto");
        toggle.setOnAction(e -> model.setActive(item, !item.active()));
        Button delete = rowButton("Eliminar", "mp-eliminar-" + item.id(), "Eliminar " + item.name(), "texto-peligro");
        delete.setOnAction(e -> {
            if (confirmDelete.test(panel.getScene().getWindow(), item)) {
                model.delete(item);
            }
        });
        HBox actions = new HBox(4, edit, toggle, delete);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        return actions;
    }

    private Button rowButton(String text, String id, String accessibleText, String style) {
        Button button = new Button(text);
        button.setId(id);
        button.getStyleClass().addAll("chico", style);
        button.setAccessibleText(accessibleText);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.disableProperty().bind(model.busyProperty());
        return button;
    }

    private static boolean askToDelete(Window owner, PaymentMethodItem item) {
        return Dialogs.confirmDanger(owner, "¿Eliminar «" + item.name() + "»?",
                "Se quita del catálogo y no se puede deshacer. Solo se puede eliminar un método que nunca se usó; "
                        + "si ya se usó, desactívalo para dejar de ofrecerlo.", "Eliminar");
    }

    private void openDialog() {
        PaymentMethodItem editing = model.editing();

        TextField name = new TextField();
        name.setId("mp-nombre");
        name.setAccessibleText("Nombre del método de pago");
        name.textProperty().bindBidirectional(model.editorNameProperty());
        Label nameLabel = new Label("Nombre");
        nameLabel.setLabelFor(name);
        Label nameError = new Label();
        VBox nameField = new VBox(nameLabel, name, nameError);
        nameField.getStyleClass().add("campo");

        CheckBox cash = new CheckBox("Es efectivo");
        cash.setId("mp-efectivo");
        cash.selectedProperty().bindBidirectional(model.editorCashProperty());
        Label cashHelp = new Label("Al cobrar con este método se pide cuánto se recibió y se calcula el cambio.");
        cashHelp.getStyleClass().add("ayuda");
        cashHelp.setWrapText(true);
        // En un diálogo el alto se calcula antes que el ancho: sin esto, el texto se corta en lugar de bajar de línea.
        cashHelp.setMinHeight(Region.USE_PREF_SIZE);
        cashHelp.setPrefWidth(FORM_WIDTH);
        Label cashError = new Label();
        VBox cashField = new VBox(cash, cashHelp, cashError);
        cashField.getStyleClass().add("campo");

        Label generalText = new Label();
        generalText.setWrapText(true);
        generalText.textProperty().bind(model.editorGeneralErrorProperty());
        HBox general = new HBox(10, Icons.icon("alerta"), generalText);
        general.getStyleClass().addAll("nota", "nota-error");
        general.visibleProperty().bind(model.editorGeneralErrorProperty().isNotNull());
        general.managedProperty().bind(general.visibleProperty());

        FieldErrors errors = new FieldErrors().bind("name", name, nameError).bind("cash", cash, cashError);
        javafx.beans.value.ChangeListener<java.util.Map<String, String>> showErrors = (obs, old, map) -> errors.show(map);
        model.editorFieldErrorsProperty().addListener(showErrors);

        VBox form = new VBox(16, general, nameField, cashField);
        form.getStyleClass().add("formulario");
        form.setPrefWidth(FORM_WIDTH);

        String title = editing == null ? "Agregar método de pago" : "Editar «" + editing.name() + "»";
        dialog = new FormDialog(panel.getScene() == null ? null : panel.getScene().getWindow(), title, form,
                editing == null ? "Agregar" : "Guardar cambios", model::submitEditor);
        dialog.bindBusy(model.editorSavingProperty(), "Guardando…");
        FormDialog current = dialog;
        // Cerrar con Cancelar, Esc o la «X» descarta lo escrito.
        current.dialog().setOnHidden(e -> {
            model.editorFieldErrorsProperty().removeListener(showErrors);
            name.textProperty().unbindBidirectional(model.editorNameProperty());
            cash.selectedProperty().unbindBidirectional(model.editorCashProperty());
            if (dialog == current) {
                dialog = null;
                model.cancelEditor();
            }
        });
        current.show();
        Platform.runLater(() -> {
            name.requestFocus();
            name.end();
        });
    }

    private static ProgressIndicator progress() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.getStyleClass().add("progreso-chico");
        return progress;
    }
}
