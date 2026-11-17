package dev.gimnasio.sistemagimnasio.ui.plans;

import dev.gimnasio.sistemagimnasio.application.usecase.plans.PlanRow;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.SavingsView;
import dev.gimnasio.sistemagimnasio.domain.member.SavingsStatus;
import dev.gimnasio.sistemagimnasio.ui.common.DataTable;
import dev.gimnasio.sistemagimnasio.ui.common.Dialogs;
import dev.gimnasio.sistemagimnasio.ui.common.FieldErrors;
import dev.gimnasio.sistemagimnasio.ui.common.FormDialog;
import dev.gimnasio.sistemagimnasio.ui.common.Formats;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import dev.gimnasio.sistemagimnasio.ui.common.StatusTag;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Pantalla Planes: el catálogo con sus acciones y el diálogo de alta y edición con la vista previa del ahorro. */
public final class PlansController {
    private static final double FORM_WIDTH = 440;
    private static final double HALF = FORM_WIDTH / 2 - 8;

    private final PlansViewModel model;
    private final Currency currency;
    private final VBox root = new VBox();
    private final DataTable<PlanRow> table;
    private final Label summary = new Label();
    private final Button add = new Button("Agregar plan", Icons.icon("mas", 16));
    private BiPredicate<Window, PlanRow> confirmDelete = PlansController::askToDelete;
    private FormDialog dialog;

    public PlansController(PlansViewModel model, Currency currency) {
        this.model = Objects.requireNonNull(model, "model");
        this.currency = Objects.requireNonNull(currency, "currency");
        Label empty = new Label("No hay planes. Agrega al menos uno para poder cobrar.");
        empty.getStyleClass().addAll("texto-2", "tabla-vacia");
        table = new DataTable<>(List.of(
                DataTable.Column.grow("Plan", this::nameCell),
                new DataTable.Column<>("Precio", this::priceCell, false, HPos.RIGHT),
                DataTable.Column.fit("Ahorro", this::savingsCell),
                DataTable.Column.fit("Estado", plan -> plan.active()
                        ? StatusTag.create("Activo", "activo") : StatusTag.create("Inactivo", "inactivo")),
                DataTable.Column.actions(this::actionsCell)), empty);
        table.setDimmed(plan -> !plan.active());
        table.setId("pl-tabla");
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

    public Parent view() {
        return root;
    }

    /** Cambia cómo se confirma un borrado (en pruebas, sin abrir el diálogo). */
    public void confirmDeleteWith(BiPredicate<Window, PlanRow> confirmation) {
        this.confirmDelete = Objects.requireNonNull(confirmation, "confirmation");
    }

    private void build() {
        Label title = new Label("Planes de membresía");
        title.getStyleClass().add("titulo-30");
        title.setId("titulo-pagina");
        Label sub = new Label("Los planes que se cobran. Cambiar el precio aplica a los cobros nuevos; desactivar "
                + "un plan deja de ofrecerlo y conserva su historial.");
        sub.getStyleClass().addAll("sub", "texto-2");
        sub.setWrapText(true);
        VBox headingText = new VBox(6, title, sub);
        HBox.setHgrow(headingText, Priority.ALWAYS);
        add.getStyleClass().add("primario");
        add.setId("pl-agregar");
        add.setMinWidth(Region.USE_PREF_SIZE);
        add.setOnAction(e -> model.startCreate());
        add.disableProperty().bind(model.stateProperty().isNotEqualTo(PlansViewModel.State.READY));
        HBox heading = new HBox(16, headingText, add);
        heading.getStyleClass().add("encabezado");
        heading.setAlignment(Pos.BOTTOM_LEFT);

        summary.getStyleClass().addAll("chico", "texto-3");
        summary.setId("pl-resumen");

        HBox loading = new HBox(12, progress(), new Label("Cargando los planes…"));
        loading.getStyleClass().add("cargando");
        loading.visibleProperty().bind(model.stateProperty().isEqualTo(PlansViewModel.State.LOADING));
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
        loadError.visibleProperty().bind(model.stateProperty().isEqualTo(PlansViewModel.State.LOAD_FAILED));
        loadError.managedProperty().bind(loadError.visibleProperty());

        table.visibleProperty().bind(model.stateProperty().isEqualTo(PlansViewModel.State.READY));
        table.managedProperty().bind(table.visibleProperty());
        summary.visibleProperty().bind(table.visibleProperty());
        summary.managedProperty().bind(summary.visibleProperty());
        model.items().addListener((ListChangeListener<PlanRow>) change -> refreshTable());
        refreshTable();

        Label panelTitle = new Label("Catálogo");
        panelTitle.getStyleClass().add("titulo-16");
        HBox panelHead = new HBox(12, panelTitle, summary);
        panelHead.setAlignment(Pos.BASELINE_LEFT);
        panelHead.getStyleClass().add("panel-cab");
        VBox panel = new VBox(panelHead, loading, loadError, table);
        panel.getStyleClass().addAll("panel", "panel-catalogo");
        panel.setAccessibleText("Catálogo de planes");

        root.getChildren().addAll(heading, panel);
        root.getStyleClass().add("contenido");
    }

    /** Reconstruye las filas conservando el foco en la acción equivalente de la misma fila. */
    private void refreshTable() {
        Node focused = root.getScene() == null ? null : root.getScene().getFocusOwner();
        String focusedId = focused != null && focused.getId() != null && focused.getId().startsWith("pl-")
                ? focused.getId() : null;
        table.setItems(List.copyOf(model.items()));
        summary.setText(PlansViewModel.summary(model.items()));
        if (focusedId != null && root.getScene() != null) {
            Node again = root.getScene().lookup("#" + focusedId);
            (again != null ? again : add).requestFocus();
        }
    }

    private Node nameCell(PlanRow plan) {
        Label name = new Label(plan.name());
        name.getStyleClass().add("celda-principal");
        name.setWrapText(true);
        Label detail = new Label(PlansViewModel.detail(plan));
        detail.getStyleClass().add("celda-detalle");
        detail.setWrapText(true);
        return new VBox(2, name, detail);
    }

    private Node priceCell(PlanRow plan) {
        Label price = new Label(Formats.money(plan.price(), currency));
        price.getStyleClass().add("peso-600");
        price.setMinWidth(Region.USE_PREF_SIZE);
        return price;
    }

    private Node savingsCell(PlanRow plan) {
        Label savings = new Label(model.savingsShort(plan.savings()));
        savings.setMinWidth(Region.USE_PREF_SIZE);
        SavingsStatus status = plan.savings() == null ? null : plan.savings().status();
        savings.getStyleClass().add(status == SavingsStatus.AHORRO ? "plan-ahorro" : "texto-3");
        if (status == SavingsStatus.NO_DISPONIBLE) {
            savings.setTooltip(new javafx.scene.control.Tooltip(model.savingsLong(plan.savings())));
        }
        return savings;
    }

    private Node actionsCell(PlanRow plan) {
        Button edit = rowButton("Editar", "pl-editar-" + plan.id(), "Editar " + plan.name(), "texto");
        edit.setOnAction(e -> model.startEdit(plan));
        Button toggle = rowButton(plan.active() ? "Desactivar" : "Activar", "pl-estado-" + plan.id(),
                (plan.active() ? "Desactivar " : "Activar ") + plan.name(), "texto");
        toggle.setOnAction(e -> model.setActive(plan, !plan.active()));
        Button delete = rowButton("Eliminar", "pl-eliminar-" + plan.id(), "Eliminar " + plan.name(), "texto-peligro");
        delete.setOnAction(e -> {
            if (confirmDelete.test(root.getScene().getWindow(), plan)) {
                model.delete(plan);
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

    private static boolean askToDelete(Window owner, PlanRow plan) {
        return Dialogs.confirmDanger(owner, "¿Eliminar el plan «" + plan.name() + "»?",
                "Se quita del catálogo y no se puede deshacer. Solo se puede eliminar un plan que nadie ha "
                        + "pagado; si ya tiene membresías, desactívalo para dejar de ofrecerlo.", "Eliminar");
    }

    // ---------- Diálogo ----------

    private static Label error(double width) {
        Label label = new Label();
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setPrefWidth(width);
        label.setWrapText(true);
        return label;
    }

    private static Label help(String text, double width) {
        Label label = new Label(text);
        label.getStyleClass().add("ayuda");
        label.setWrapText(true);
        // En un diálogo el alto se calcula antes que el ancho: sin esto, el texto se corta en lugar de bajar de línea.
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setPrefWidth(width);
        return label;
    }

    private static VBox field(String caption, Node control, Node... below) {
        Label label = new Label(caption);
        if (control instanceof javafx.scene.control.Control c) {
            label.setLabelFor(c);
        }
        VBox box = new VBox(label, control);
        box.getChildren().addAll(below);
        box.getStyleClass().add("campo");
        return box;
    }

    private void openDialog() {
        PlanRow editing = model.editing();

        TextField name = new TextField();
        name.setId("pl-nombre");
        name.setAccessibleText("Nombre del plan");
        name.textProperty().bindBidirectional(model.editorNameProperty());
        Label nameError = error(FORM_WIDTH);

        TextField duration = new TextField();
        duration.setId("pl-duracion");
        duration.setAccessibleText("Duración en días");
        duration.setPromptText("30");
        duration.setPrefColumnCount(6);
        duration.textProperty().bindBidirectional(model.editorDurationProperty());
        Label durationError = error(HALF);
        Label days = new Label("días");
        days.getStyleClass().add("texto-2");
        HBox durationBox = new HBox(8, duration, days);
        durationBox.setAlignment(Pos.CENTER_LEFT);

        TextField price = new TextField();
        price.setId("pl-precio");
        price.setAccessibleText("Precio");
        price.setPromptText("450.00");
        price.setPrefColumnCount(8);
        price.textProperty().bindBidirectional(model.editorPriceProperty());
        Label priceError = error(HALF);

        VBox durationField = field("Duración", durationBox, help("30 es mensual; 365, anual.", HALF), durationError);
        VBox priceField = field("Precio (" + currency.getCurrencyCode() + ")", price,
                help("Con punto decimal, por ejemplo 450.00.", HALF), priceError);
        HBox.setHgrow(durationField, Priority.ALWAYS);
        HBox.setHgrow(priceField, Priority.ALWAYS);
        HBox numbers = new HBox(16, durationField, priceField);

        ComboBox<PlansViewModel.ReferenceOption> reference = new ComboBox<>(model.referenceOptions());
        reference.setId("pl-referencia");
        reference.setAccessibleText("Plan mensual de referencia");
        reference.setMaxWidth(Double.MAX_VALUE);
        StringConverter<PlansViewModel.ReferenceOption> labels = new StringConverter<>() {
            @Override
            public String toString(PlansViewModel.ReferenceOption option) {
                return option == null ? "" : option.label();
            }

            @Override
            public PlansViewModel.ReferenceOption fromString(String text) {
                return null;
            }
        };
        reference.setConverter(labels);
        reference.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(PlansViewModel.ReferenceOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? "" : item.label());
            }
        });
        reference.valueProperty().bindBidirectional(model.editorReferenceProperty());
        Label referenceError = error(FORM_WIDTH);

        Label savingsText = new Label();
        savingsText.setId("pl-ahorro");
        savingsText.setWrapText(true);
        savingsText.setMinHeight(Region.USE_PREF_SIZE);
        savingsText.setPrefWidth(FORM_WIDTH - 40);
        HBox savingsNote = new HBox(10, Icons.icon("info"), savingsText);
        savingsNote.getStyleClass().add("nota");
        ChangeListener<SavingsView> showSavings = (obs, old, savings) -> {
            savingsText.setText(model.savingsLong(savings));
            if (dialog != null) {
                dialog.refit();
            }
        };
        model.editorSavingsProperty().addListener(showSavings);
        savingsText.setText(model.savingsLong(model.editorSavingsProperty().get()));

        Label generalText = new Label();
        generalText.setWrapText(true);
        generalText.textProperty().bind(model.editorGeneralErrorProperty());
        HBox general = new HBox(10, Icons.icon("alerta"), generalText);
        general.getStyleClass().addAll("nota", "nota-error");
        general.visibleProperty().bind(model.editorGeneralErrorProperty().isNotNull());
        general.managedProperty().bind(general.visibleProperty());

        FieldErrors errors = new FieldErrors()
                .bind("name", name, nameError)
                .bind("durationDays", duration, durationError)
                .bind("price", price, priceError)
                .bind("referenceMonthlyPlanId", reference, referenceError);
        ChangeListener<Map<String, String>> showErrors = (obs, old, map) -> {
            // El foco va al primer error solo cuando llegan errores nuevos (al enviar), no al corregir uno.
            errors.show(map, old.isEmpty() || !old.keySet().containsAll(map.keySet()));
            if (dialog != null) {
                dialog.refit();
            }
        };
        model.editorFieldErrorsProperty().addListener(showErrors);

        VBox form = new VBox(16, general,
                field("Nombre", name, nameError),
                numbers,
                field("Referencia mensual", reference,
                        help("Opcional. El plan de 30 días con el que se compara para mostrar el ahorro.", FORM_WIDTH),
                        referenceError),
                savingsNote);
        form.getStyleClass().add("formulario");
        form.setPrefWidth(FORM_WIDTH);

        String title = editing == null ? "Agregar plan" : "Editar «" + editing.name() + "»";
        dialog = new FormDialog(root.getScene() == null ? null : root.getScene().getWindow(), title, form,
                editing == null ? "Agregar" : "Guardar cambios", model::submitEditor);
        dialog.bindBusy(model.editorSavingProperty(), "Guardando…");
        FormDialog current = dialog;
        // Cerrar con Cancelar, Esc o la «X» descarta lo escrito.
        current.dialog().setOnHidden(e -> {
            model.editorFieldErrorsProperty().removeListener(showErrors);
            model.editorSavingsProperty().removeListener(showSavings);
            name.textProperty().unbindBidirectional(model.editorNameProperty());
            duration.textProperty().unbindBidirectional(model.editorDurationProperty());
            price.textProperty().unbindBidirectional(model.editorPriceProperty());
            reference.valueProperty().unbindBidirectional(model.editorReferenceProperty());
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
