package dev.gimnasio.sistemagimnasio.ui.users;

import dev.gimnasio.sistemagimnasio.application.usecase.users.UserRow;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.ui.common.DataTable;
import dev.gimnasio.sistemagimnasio.ui.common.Dialogs;
import dev.gimnasio.sistemagimnasio.ui.common.FieldErrors;
import dev.gimnasio.sistemagimnasio.ui.common.FormDialog;
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
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiPredicate;

/** Pantalla Usuarios: las cuentas, qué puede hacer cada rol y el diálogo de alta y edición. */
public final class UsersController {
    private static final double FORM_WIDTH = 440;

    private final UsersViewModel model;
    private final VBox root = new VBox();
    private final DataTable<UserRow> table;
    private final Label summary = new Label();
    private final Button add = new Button("Agregar usuario", Icons.icon("mas", 16));
    private BiPredicate<Window, UserRow> confirmDelete = UsersController::askToDelete;
    private FormDialog dialog;

    public UsersController(UsersViewModel model) {
        this.model = Objects.requireNonNull(model, "model");
        Label empty = new Label("No hay cuentas.");
        empty.getStyleClass().addAll("texto-2", "tabla-vacia");
        table = new DataTable<>(List.of(
                DataTable.Column.grow("Persona", this::personCell),
                DataTable.Column.fit("Rol", row -> new Label(row.role().displayName())),
                DataTable.Column.fit("Estado", row -> row.active()
                        ? StatusTag.create("Activa", "activo") : StatusTag.create("Inactiva", "inactivo")),
                DataTable.Column.actions(this::actionsCell)), empty);
        table.setDimmed(row -> !row.active());
        table.setId("us-tabla");
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
    public void confirmDeleteWith(BiPredicate<Window, UserRow> confirmation) {
        this.confirmDelete = Objects.requireNonNull(confirmation, "confirmation");
    }

    private void build() {
        Label title = new Label("Usuarios");
        title.getStyleClass().add("titulo-30");
        title.setId("titulo-pagina");
        Label sub = new Label("Las personas que usan el sistema y lo que puede hacer cada una según su rol. "
                + "Desactivar una cuenta impide entrar y conserva sus cobros.");
        sub.getStyleClass().addAll("sub", "texto-2");
        sub.setWrapText(true);
        VBox headingText = new VBox(6, title, sub);
        HBox.setHgrow(headingText, Priority.ALWAYS);
        add.getStyleClass().add("primario");
        add.setId("us-agregar");
        add.setMinWidth(Region.USE_PREF_SIZE);
        add.setOnAction(e -> model.startCreate());
        add.disableProperty().bind(model.stateProperty().isNotEqualTo(UsersViewModel.State.READY));
        HBox heading = new HBox(16, headingText, add);
        heading.getStyleClass().add("encabezado");
        heading.setAlignment(Pos.BOTTOM_LEFT);

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("progreso-chico");
        HBox loading = new HBox(12, spinner, new Label("Cargando las cuentas…"));
        loading.getStyleClass().add("cargando");
        loading.visibleProperty().bind(model.stateProperty().isEqualTo(UsersViewModel.State.LOADING));
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
        loadError.visibleProperty().bind(model.stateProperty().isEqualTo(UsersViewModel.State.LOAD_FAILED));
        loadError.managedProperty().bind(loadError.visibleProperty());

        table.visibleProperty().bind(model.stateProperty().isEqualTo(UsersViewModel.State.READY));
        table.managedProperty().bind(table.visibleProperty());
        summary.getStyleClass().addAll("chico", "texto-3");
        summary.setId("us-resumen");
        summary.visibleProperty().bind(table.visibleProperty());
        model.items().addListener((ListChangeListener<UserRow>) change -> refreshTable());
        refreshTable();

        Label accountsTitle = new Label("Cuentas");
        accountsTitle.getStyleClass().add("titulo-16");
        HBox accountsHead = new HBox(12, accountsTitle, summary);
        accountsHead.setAlignment(Pos.BASELINE_LEFT);
        accountsHead.getStyleClass().add("panel-cab");
        VBox accounts = new VBox(accountsHead, loading, loadError, table);
        accounts.getStyleClass().addAll("panel", "panel-catalogo");

        root.getChildren().addAll(heading, accounts, permissionsPanel());
        root.setSpacing(20);
        root.getStyleClass().add("contenido");
    }

    /** Qué puede hacer cada rol, sacado de {@code Role.permissions()}: el mismo dato que decide el menú. */
    private Node permissionsPanel() {
        Label title = new Label("Qué puede hacer cada rol");
        title.getStyleClass().add("titulo-16");
        Label note = new Label("Un cambio de rol se aplica en la siguiente operación de esa persona.");
        note.getStyleClass().addAll("chico", "texto-3");
        note.setWrapText(true);
        VBox head = new VBox(4, title, note);
        head.getStyleClass().add("panel-cab");

        GridPane grid = new GridPane();
        grid.getStyleClass().add("matriz-permisos");
        grid.setId("us-permisos");
        ColumnConstraints first = new ColumnConstraints();
        first.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().add(first);
        Label permissionHeader = new Label("Permiso");
        permissionHeader.getStyleClass().add("matriz-cab");
        grid.add(permissionHeader, 0, 0);
        Role[] roles = Role.values();
        for (int c = 0; c < roles.length; c++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setHalignment(HPos.CENTER);
            column.setMinWidth(110);
            grid.getColumnConstraints().add(column);
            Label header = new Label(roles[c].displayName());
            header.getStyleClass().add("matriz-cab");
            grid.add(header, c + 1, 0);
        }
        int r = 1;
        for (Map.Entry<Permission, List<Role>> entry : UsersViewModel.permissionMatrix().entrySet()) {
            Label name = new Label(UsersViewModel.permissionLabel(entry.getKey()));
            name.setWrapText(true);
            grid.add(name, 0, r);
            for (int c = 0; c < roles.length; c++) {
                boolean has = entry.getValue().contains(roles[c]);
                Node mark = has ? Icons.icon("check", 16) : new Label("—");
                mark.getStyleClass().add(has ? "permiso-si" : "permiso-no");
                if (has) {
                    ((Node) mark).setAccessibleText(roles[c].displayName() + " puede");
                }
                grid.add(mark, c + 1, r);
            }
            r++;
        }
        VBox panel = new VBox(head, grid);
        panel.getStyleClass().addAll("panel", "panel-catalogo");
        return panel;
    }

    /** Reconstruye las filas conservando el foco en la acción equivalente de la misma fila. */
    private void refreshTable() {
        Node focused = root.getScene() == null ? null : root.getScene().getFocusOwner();
        String focusedId = focused != null && focused.getId() != null && focused.getId().startsWith("us-")
                ? focused.getId() : null;
        table.setItems(List.copyOf(model.items()));
        summary.setText(UsersViewModel.summary(model.items()));
        if (focusedId != null && root.getScene() != null) {
            Node again = root.getScene().lookup("#" + focusedId);
            (again != null ? again : add).requestFocus();
        }
    }

    private Node personCell(UserRow row) {
        Label name = new Label(row.name());
        name.getStyleClass().add("celda-principal");
        name.setWrapText(true);
        Label detail = new Label(row.username() + (model.isSelf(row) ? " · tu cuenta" : ""));
        detail.getStyleClass().add("celda-detalle");
        return new VBox(2, name, detail);
    }

    private Node actionsCell(UserRow row) {
        Button edit = rowButton("Editar", "us-editar-" + row.id(), "Editar la cuenta de " + row.name(), "texto");
        edit.setOnAction(e -> model.startEdit(row));
        HBox actions = new HBox(4, edit);
        // La propia cuenta no se desactiva ni se elimina mientras se usa: ni se ofrece.
        if (!model.isSelf(row)) {
            Button toggle = rowButton(row.active() ? "Desactivar" : "Activar", "us-estado-" + row.id(),
                    (row.active() ? "Desactivar la cuenta de " : "Activar la cuenta de ") + row.name(), "texto");
            toggle.setOnAction(e -> model.setActive(row, !row.active()));
            Button delete = rowButton("Eliminar", "us-eliminar-" + row.id(), "Eliminar la cuenta de " + row.name(),
                    "texto-peligro");
            delete.setOnAction(e -> {
                if (confirmDelete.test(root.getScene().getWindow(), row)) {
                    model.delete(row);
                }
            });
            actions.getChildren().addAll(toggle, delete);
        }
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

    private static boolean askToDelete(Window owner, UserRow row) {
        return Dialogs.confirmDanger(owner, "¿Eliminar la cuenta de «" + row.name() + "»?",
                "Se quita y no se puede deshacer. Solo se puede eliminar una cuenta que nunca registró cobros; "
                        + "si ya cobró, desactívala para que no pueda entrar.", "Eliminar");
    }

    // ---------- Diálogo ----------

    private static Label error(double width) {
        Label label = new Label();
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setPrefWidth(width);
        label.setWrapText(true);
        return label;
    }

    private static Label help(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("ayuda");
        label.setWrapText(true);
        // En un diálogo el alto se calcula antes que el ancho: sin esto, el texto se corta en lugar de bajar de línea.
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setPrefWidth(FORM_WIDTH);
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
        UserRow editing = model.editing();

        TextField name = new TextField();
        name.setId("us-nombre");
        name.setAccessibleText("Nombre de la persona");
        name.textProperty().bindBidirectional(model.editorNameProperty());
        Label nameError = error(FORM_WIDTH);

        TextField username = new TextField();
        username.setId("us-usuario");
        username.setAccessibleText("Nombre de usuario");
        username.textProperty().bindBidirectional(model.editorUsernameProperty());
        Label usernameError = error(FORM_WIDTH);

        ToggleGroup roles = new ToggleGroup();
        FlowPane roleBar = new FlowPane(4, 4);
        roleBar.getStyleClass().add("segmentado");
        roleBar.setAccessibleText("Rol");
        for (Role role : Role.values()) {
            ToggleButton option = new ToggleButton(role.displayName());
            option.setId("us-rol-" + role.name().toLowerCase(Locale.ROOT));
            option.setUserData(role);
            option.setToggleGroup(roles);
            option.setSelected(role == model.editorRoleProperty().get());
            roleBar.getChildren().add(option);
        }
        ChangeListener<javafx.scene.control.Toggle> pickRole = (obs, old, selected) -> {
            if (selected == null && old != null) {
                old.setSelected(true);
                return;
            }
            model.editorRoleProperty().set(selected == null ? null : (Role) selected.getUserData());
        };
        roles.selectedToggleProperty().addListener(pickRole);
        Label roleHelp = help("");
        roleHelp.setId("us-rol-ayuda");
        Runnable showRole = () -> {
            Role role = model.editorRoleProperty().get();
            roleHelp.setText(role == null ? "Elige qué puede hacer esta persona." : UsersViewModel.roleSummary(role));
        };
        ChangeListener<Role> roleChanged = (obs, old, role) -> {
            showRole.run();
            if (dialog != null) {
                dialog.refit();
            }
        };
        model.editorRoleProperty().addListener(roleChanged);
        showRole.run();
        Label roleError = error(FORM_WIDTH);

        PasswordField password = new PasswordField();
        password.setId("us-clave");
        password.setAccessibleText(editing == null ? "Contraseña" : "Contraseña nueva");
        password.textProperty().bindBidirectional(model.editorPasswordProperty());
        Label passwordError = error(FORM_WIDTH);
        PasswordField confirmation = new PasswordField();
        confirmation.setId("us-clave2");
        confirmation.setAccessibleText("Repite la contraseña");
        confirmation.textProperty().bindBidirectional(model.editorConfirmationProperty());
        Label confirmationError = error(FORM_WIDTH);

        Label generalText = new Label();
        generalText.setWrapText(true);
        generalText.textProperty().bind(model.editorGeneralErrorProperty());
        HBox general = new HBox(10, Icons.icon("alerta"), generalText);
        general.getStyleClass().addAll("nota", "nota-error");
        general.visibleProperty().bind(model.editorGeneralErrorProperty().isNotNull());
        general.managedProperty().bind(general.visibleProperty());

        ToggleButton firstRole = (ToggleButton) roleBar.getChildren().getFirst();
        FieldErrors errors = new FieldErrors()
                .bind("name", name, nameError)
                .bind("username", username, usernameError)
                .bind("role", firstRole, roleError)
                .bind("password", password, passwordError)
                .bind("newPassword", password, passwordError)
                .bind("passwordConfirmation", confirmation, confirmationError)
                .bind("newPasswordConfirmation", confirmation, confirmationError);
        ChangeListener<Map<String, String>> showErrors = (obs, old, map) -> {
            // El foco va al primer error solo cuando llegan errores nuevos (al enviar), no al corregir uno.
            errors.show(map, old.isEmpty() || !old.keySet().containsAll(map.keySet()));
            if (dialog != null) {
                dialog.refit();
            }
        };
        model.editorFieldErrorsProperty().addListener(showErrors);

        VBox passwords = new VBox(16,
                field(editing == null ? "Contraseña" : "Contraseña nueva", password,
                        help(editing == null ? "Al menos 8 caracteres. Se guarda cifrada; nadie puede leerla."
                                : "Déjala vacía para conservar la actual."), passwordError),
                field("Repite la contraseña", confirmation, confirmationError));

        VBox form = new VBox(16, general,
                field("Nombre", name, nameError),
                field("Usuario", username, help("De 3 a 20 minúsculas, números, punto o guion. Con él se entra."),
                        usernameError),
                field("Rol", roleBar, roleHelp, roleError),
                passwords);
        form.getStyleClass().add("formulario");
        form.setPrefWidth(FORM_WIDTH);

        String title = editing == null ? "Agregar usuario" : "Editar la cuenta de «" + editing.name() + "»";
        dialog = new FormDialog(root.getScene() == null ? null : root.getScene().getWindow(), title, form,
                editing == null ? "Crear cuenta" : "Guardar cambios", model::submitEditor);
        dialog.bindBusy(model.editorSavingProperty(), "Guardando…");
        FormDialog current = dialog;
        // Cerrar con Cancelar, Esc o la «X» descarta lo escrito, contraseñas incluidas.
        current.dialog().setOnHidden(e -> {
            model.editorFieldErrorsProperty().removeListener(showErrors);
            model.editorRoleProperty().removeListener(roleChanged);
            roles.selectedToggleProperty().removeListener(pickRole);
            name.textProperty().unbindBidirectional(model.editorNameProperty());
            username.textProperty().unbindBidirectional(model.editorUsernameProperty());
            password.textProperty().unbindBidirectional(model.editorPasswordProperty());
            confirmation.textProperty().unbindBidirectional(model.editorConfirmationProperty());
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
}
