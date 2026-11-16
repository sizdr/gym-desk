package dev.gimnasio.sistemagimnasio.ui.members;

import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.ui.common.DataTable;
import dev.gimnasio.sistemagimnasio.ui.common.Dialogs;
import dev.gimnasio.sistemagimnasio.ui.common.FieldErrors;
import dev.gimnasio.sistemagimnasio.ui.common.FormDialog;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import dev.gimnasio.sistemagimnasio.ui.common.StatusTag;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Pantalla Socios: buscador, filtro por estado, lista y diálogo de alta y corrección. */
public final class MembersController {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MembersViewModel model;
    private final VBox root = new VBox();
    private final DataTable<MemberRow> table;
    private final Map<MemberStatus, ToggleButton> filters = new EnumMap<>(MemberStatus.class);
    private final ToggleButton allFilter = new ToggleButton();
    private BiPredicate<Window, MemberRow> confirmDelete = MembersController::askToDelete;
    private FormDialog dialog;

    public MembersController(MembersViewModel model) {
        this.model = Objects.requireNonNull(model, "model");
        Label empty = new Label("No hay socios que coincidan.");
        empty.getStyleClass().addAll("texto-2", "tabla-vacia");
        List<DataTable.Column<MemberRow>> columns = new java.util.ArrayList<>(List.of(
                DataTable.Column.grow("Socio", this::nameCell),
                DataTable.Column.fit("Estado", row -> StatusTag.create(MembersViewModel.statusLabel(row.status()),
                        statusKind(row.status()))),
                DataTable.Column.fit("Vigencia", this::validityCell)));
        if (model.canEdit()) {
            columns.add(DataTable.Column.actions(this::actionsCell));
        }
        table = new DataTable<>(columns, empty);
        table.setId("so-tabla");
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
    public void confirmDeleteWith(BiPredicate<Window, MemberRow> confirmation) {
        this.confirmDelete = Objects.requireNonNull(confirmation, "confirmation");
    }

    private static String statusKind(MemberStatus status) {
        return switch (status) {
            case VIGENTE -> "vigente";
            case POR_VENCER -> "por-vencer";
            case VENCIDA -> "vencida";
            case BAJA -> "baja";
            case SIN_MEMBRESIA -> "sin-membresia";
        };
    }

    private void build() {
        Label title = new Label("Socios");
        title.getStyleClass().add("titulo-30");
        title.setId("titulo-pagina");
        Label sub = new Label("Busca socios, revisa su vigencia y registra altas. El estado se calcula con su última "
                + "membresía y los plazos de Configuración.");
        sub.getStyleClass().addAll("sub", "texto-2");
        sub.setWrapText(true);
        VBox headingText = new VBox(6, title, sub);
        HBox.setHgrow(headingText, Priority.ALWAYS);
        HBox heading = new HBox(16, headingText);
        heading.getStyleClass().add("encabezado");
        heading.setAlignment(Pos.BOTTOM_LEFT);
        if (model.canEdit()) {
            Button add = new Button("Registrar socio", Icons.icon("mas", 16));
            add.getStyleClass().add("primario");
            add.setId("so-agregar");
            add.setMinWidth(Region.USE_PREF_SIZE);
            add.setOnAction(e -> model.startCreate());
            heading.getChildren().add(add);
        }

        TextField search = new TextField();
        search.setId("so-buscar");
        search.setPromptText("Nombre, correo o teléfono");
        search.setAccessibleText("Buscar socios por nombre, correo o teléfono");
        search.textProperty().bindBidirectional(model.searchTextProperty());
        HBox searchBox = new HBox(8, Icons.icon("buscar"), search);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(search, Priority.ALWAYS);
        searchBox.setMaxWidth(420);
        Label searchLabel = new Label("Buscar");
        searchLabel.setLabelFor(search);
        VBox searchField = new VBox(6, searchLabel, searchBox);
        searchField.getStyleClass().add("campo");

        ToggleGroup group = new ToggleGroup();
        FlowPane filterBar = new FlowPane(4, 4);
        filterBar.getStyleClass().add("segmentado");
        filterBar.setAccessibleText("Filtrar por estado");
        allFilter.setToggleGroup(group);
        allFilter.setId("so-filtro-todos");
        allFilter.setSelected(true);
        filterBar.getChildren().add(allFilter);
        for (MemberStatus status : List.of(MemberStatus.VIGENTE, MemberStatus.POR_VENCER, MemberStatus.VENCIDA,
                MemberStatus.BAJA, MemberStatus.SIN_MEMBRESIA)) {
            ToggleButton button = new ToggleButton();
            button.setToggleGroup(group);
            button.setId("so-filtro-" + statusKind(status));
            button.setUserData(status);
            filters.put(status, button);
            filterBar.getChildren().add(button);
        }
        group.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null) {
                old.setSelected(true);
                return;
            }
            model.statusFilterProperty().set((MemberStatus) selected.getUserData());
        });
        model.countsProperty().addListener((obs, old, counts) -> updateFilterLabels());
        model.totalMembersProperty().addListener((obs, old, total) -> updateFilterLabels());
        updateFilterLabels();

        Label summary = new Label();
        summary.getStyleClass().addAll("chico", "texto-3");
        summary.setId("so-resumen");
        summary.textProperty().bind(javafx.beans.binding.Bindings.createStringBinding(() ->
                        "Mostrando " + model.matchCountProperty().get() + " de " + model.totalMembersProperty().get() + " socios.",
                model.matchCountProperty(), model.totalMembersProperty()));

        VBox tools = new VBox(14, searchField, filterBar, summary);
        tools.getStyleClass().add("herramientas");

        HBox loading = new HBox(12, progress(), new Label("Cargando los socios…"));
        loading.getStyleClass().add("cargando");
        loading.visibleProperty().bind(model.stateProperty().isEqualTo(MembersViewModel.State.LOADING));
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
        VBox.setMargin(loadError, new Insets(0, 20, 20, 20));
        loadError.visibleProperty().bind(model.stateProperty().isEqualTo(MembersViewModel.State.LOAD_FAILED));
        loadError.managedProperty().bind(loadError.visibleProperty());

        table.visibleProperty().bind(model.stateProperty().isEqualTo(MembersViewModel.State.READY));
        table.managedProperty().bind(table.visibleProperty());
        model.rows().addListener((ListChangeListener<MemberRow>) change -> refreshTable());
        refreshTable();

        VBox panel = new VBox(tools, loading, loadError, table);
        panel.getStyleClass().addAll("panel", "panel-catalogo");
        panel.setAccessibleText("Lista de socios");

        root.getChildren().addAll(heading, panel);
        root.getStyleClass().add("contenido");
    }

    private void updateFilterLabels() {
        Map<MemberStatus, Integer> counts = model.countsProperty().get();
        allFilter.setText("Todos " + model.totalMembersProperty().get());
        filters.forEach((status, button) ->
                button.setText(MembersViewModel.statusLabel(status) + " " + counts.getOrDefault(status, 0)));
    }

    /** Reconstruye las filas conservando el foco en la acción equivalente de la misma fila. */
    private void refreshTable() {
        Node focused = root.getScene() == null ? null : root.getScene().getFocusOwner();
        String focusedId = focused != null && focused.getId() != null && focused.getId().startsWith("so-")
                && focused.getId().matches("so-(editar|eliminar)-\\d+") ? focused.getId() : null;
        table.setItems(List.copyOf(model.rows()));
        if (focusedId != null && root.getScene() != null) {
            Node again = root.getScene().lookup("#" + focusedId);
            if (again != null) {
                again.requestFocus();
            } else {
                Node search = root.getScene().lookup("#so-buscar");
                if (search != null) {
                    search.requestFocus();
                }
            }
        }
    }

    private Node nameCell(MemberRow row) {
        Label name = new Label(row.fullName());
        name.getStyleClass().add("celda-principal");
        name.setWrapText(true);
        String contact = formatPhone(row.phone()) + (row.email() == null ? "" : " · " + row.email());
        Label detail = new Label(contact);
        detail.getStyleClass().add("celda-detalle");
        detail.setWrapText(true);
        return new VBox(2, name, detail);
    }

    private Node validityCell(MemberRow row) {
        Label text = new Label(MembersViewModel.validity(row));
        text.setWrapText(true);
        text.setMaxWidth(220);
        VBox cell = new VBox(2, text);
        if (row.planName() != null) {
            Label plan = new Label(row.planName() + (row.membershipEnd() == null ? ""
                    : " · vence el " + DAY.format(row.membershipEnd())));
            plan.getStyleClass().add("celda-detalle");
            cell.getChildren().add(plan);
        }
        return cell;
    }

    /** «5512345678» → «551 234 5678». */
    static String formatPhone(String phone) {
        return phone.length() == 10 ? phone.substring(0, 3) + " " + phone.substring(3, 6) + " " + phone.substring(6)
                : phone;
    }

    private Node actionsCell(MemberRow row) {
        Button edit = rowButton("Editar", "so-editar-" + row.id(), "Editar a " + row.fullName(), "texto");
        edit.setOnAction(e -> model.startEdit(row));
        Button delete = rowButton("Eliminar", "so-eliminar-" + row.id(), "Eliminar a " + row.fullName(), "texto-peligro");
        delete.setOnAction(e -> {
            if (confirmDelete.test(root.getScene().getWindow(), row)) {
                model.delete(row);
            }
        });
        HBox actions = new HBox(4, edit, delete);
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

    private static boolean askToDelete(Window owner, MemberRow row) {
        return Dialogs.confirmDanger(owner, "¿Eliminar a «" + row.fullName() + "»?",
                "Se quita del catálogo y no se puede deshacer. Solo se puede eliminar a un socio sin cobros, por "
                        + "ejemplo un alta duplicada; uno con cobros conserva su historial.", "Eliminar");
    }

    private void openDialog() {
        MemberRow editing = model.editing();
        FieldErrors errors = new FieldErrors();

        TextField name = textField("so-nombre", "Nombre completo", model.editorNameProperty());
        TextField phone = textField("so-telefono", "Teléfono", model.editorPhoneProperty());
        phone.setPromptText("555 010 2233");
        TextField email = textField("so-correo", "Correo, opcional", model.editorEmailProperty());
        email.setPromptText("nombre@ejemplo.com");
        DatePicker joinDate = new DatePicker();
        joinDate.setId("so-alta");
        joinDate.setAccessibleText("Fecha de alta");
        joinDate.setConverter(new DayConverter());
        joinDate.setPromptText("dd/mm/aaaa");
        joinDate.setMaxWidth(Double.MAX_VALUE);
        joinDate.valueProperty().bindBidirectional(model.editorJoinDateProperty());
        // Lo escrito a mano cuenta aunque no se haya salido del campo.
        joinDate.getEditor().textProperty().addListener((obs, old, text) -> {
            try {
                joinDate.setValue(text == null || text.isBlank() ? null : LocalDate.parse(text.strip(), DAY));
            } catch (DateTimeParseException ignored) {
                // Se queda con lo último válido; al guardar, el caso de uso dice si falta.
            }
        });

        VBox form = new VBox(16,
                generalNote(),
                field("Nombre completo", name, null, errors, "fullName"),
                field("Teléfono", phone, "10 dígitos; los espacios y guiones se quitan.", errors, "phone"),
                field("Correo (opcional)", email, null, errors, "email"),
                field("Fecha de alta", joinDate, "Día en que se inscribió; no puede ser posterior a hoy.", errors, "joinDate"));
        form.getStyleClass().add("formulario");
        form.setPrefWidth(440);

        ChangeListener<Map<String, String>> showErrors = (obs, old, map) -> {
            errors.show(map);
            if (dialog != null) {
                dialog.refit();
            }
        };
        model.editorFieldErrorsProperty().addListener(showErrors);

        String title = editing == null ? "Registrar socio" : "Editar a «" + editing.fullName() + "»";
        dialog = new FormDialog(root.getScene() == null ? null : root.getScene().getWindow(), title, form,
                editing == null ? "Registrar" : "Guardar cambios", model::submitEditor);
        dialog.bindBusy(model.editorSavingProperty(), "Guardando…");
        FormDialog current = dialog;
        current.dialog().setOnHidden(e -> {
            model.editorFieldErrorsProperty().removeListener(showErrors);
            name.textProperty().unbindBidirectional(model.editorNameProperty());
            phone.textProperty().unbindBidirectional(model.editorPhoneProperty());
            email.textProperty().unbindBidirectional(model.editorEmailProperty());
            joinDate.valueProperty().unbindBidirectional(model.editorJoinDateProperty());
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

    private Node generalNote() {
        Label text = new Label();
        text.setWrapText(true);
        text.textProperty().bind(model.editorGeneralErrorProperty());
        HBox note = new HBox(10, Icons.icon("alerta"), text);
        note.getStyleClass().addAll("nota", "nota-error");
        note.visibleProperty().bind(model.editorGeneralErrorProperty().isNotNull());
        note.managedProperty().bind(note.visibleProperty());
        return note;
    }

    private static TextField textField(String id, String accessible, javafx.beans.property.StringProperty property) {
        TextField field = new TextField();
        field.setId(id);
        field.setAccessibleText(accessible);
        field.textProperty().bindBidirectional(property);
        return field;
    }

    private static VBox field(String label, javafx.scene.control.Control control, String help, FieldErrors errors,
                              String fieldName) {
        Label caption = new Label(label);
        caption.setLabelFor(control);
        Label error = new Label();
        // En un diálogo el alto se calcula antes que el ancho: sin esto, un mensaje largo se corta.
        error.setMinHeight(Region.USE_PREF_SIZE);
        error.setPrefWidth(420);
        VBox box = new VBox(caption, control);
        if (help != null) {
            Label helpText = new Label(help);
            helpText.getStyleClass().add("ayuda");
            helpText.setWrapText(true);
            helpText.setMinHeight(Region.USE_PREF_SIZE);
            helpText.setPrefWidth(420);
            box.getChildren().add(helpText);
        }
        box.getChildren().add(error);
        box.getStyleClass().add("campo");
        errors.bind(fieldName, control, error);
        return box;
    }

    private static ProgressIndicator progress() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.getStyleClass().add("progreso-chico");
        return progress;
    }

    /** Día/mes/año, como el resto de la app. */
    private static final class DayConverter extends StringConverter<LocalDate> {
        @Override
        public String toString(LocalDate date) {
            return date == null ? "" : DAY.format(date);
        }

        @Override
        public LocalDate fromString(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(text.strip(), DAY);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }
}
