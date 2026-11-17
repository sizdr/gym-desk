package dev.gimnasio.sistemagimnasio.ui.users;

import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateUserCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.users.UpdateUserCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.users.UserRow;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * Usuarios: las cuentas que entran al sistema, su rol y si están activas. Las reglas (usuario repetido, último
 * administrador, no desactivarse ni eliminarse a sí mismo, cuentas con cobros) son de los casos de uso; aquí se
 * reparten sus mensajes. Las contraseñas solo pasan por aquí para enviarlas y se borran del formulario enseguida.
 */
public final class UsersViewModel {
    public enum State { LOADING, LOAD_FAILED, READY }

    private final UseCases useCases;
    private final Background background;
    private final Supplier<User> actor;
    private final Notifier notifier;

    private final ObservableList<UserRow> items = FXCollections.observableArrayList();
    /** Una sola vista de solo lectura: con una nueva en cada llamada se perderían los avisos de cambio. */
    private final ObservableList<UserRow> readOnlyItems = FXCollections.unmodifiableObservableList(items);
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.LOADING);
    private final ReadOnlyStringWrapper loadError = new ReadOnlyStringWrapper(this, "loadError");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);

    private final ReadOnlyBooleanWrapper editorOpen = new ReadOnlyBooleanWrapper(this, "editorOpen", false);
    private final StringProperty editorName = new SimpleStringProperty(this, "editorName", "");
    private final StringProperty editorUsername = new SimpleStringProperty(this, "editorUsername", "");
    private final ObjectProperty<Role> editorRole = new SimpleObjectProperty<>(this, "editorRole");
    private final StringProperty editorPassword = new SimpleStringProperty(this, "editorPassword", "");
    private final StringProperty editorConfirmation = new SimpleStringProperty(this, "editorConfirmation", "");
    private final ReadOnlyBooleanWrapper editorSaving = new ReadOnlyBooleanWrapper(this, "editorSaving", false);
    private final ReadOnlyObjectWrapper<Map<String, String>> editorFieldErrors =
            new ReadOnlyObjectWrapper<>(this, "editorFieldErrors", Map.of());
    private final ReadOnlyStringWrapper editorGeneralError = new ReadOnlyStringWrapper(this, "editorGeneralError");
    private UserRow editing;

    public UsersViewModel(UseCases useCases, Background background, Supplier<User> actor, Notifier notifier) {
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        editorName.addListener((obs, old, value) -> clearFieldError("name"));
        editorUsername.addListener((obs, old, value) -> clearFieldError("username"));
        editorRole.addListener((obs, old, value) -> clearFieldError("role"));
        editorPassword.addListener((obs, old, value) -> {
            clearFieldError("password");
            clearFieldError("newPassword");
        });
        editorConfirmation.addListener((obs, old, value) -> {
            clearFieldError("passwordConfirmation");
            clearFieldError("newPasswordConfirmation");
        });
    }

    /** Activos primero, luego por nombre, como los ordena {@code ListUsers}. */
    public void load() {
        state.set(State.LOADING);
        loadError.set(null);
        User user = actor.get();
        background.run(() -> useCases.listUsers().execute(user), list -> {
            items.setAll(list);
            state.set(State.READY);
        }, error -> {
            loadError.set(ErrorReport.of(error).message());
            state.set(State.LOAD_FAILED);
        });
    }

    private void refresh() {
        User user = actor.get();
        background.run(() -> useCases.listUsers().execute(user), items::setAll,
                error -> notifier.error(ErrorReport.of(error).message()));
    }

    /** La cuenta con la que se entró: no se puede desactivar ni eliminar desde aquí. */
    public boolean isSelf(UserRow row) {
        User me = actor.get();
        return me != null && me.id() != null && me.id() == row.id();
    }

    // ---------- Formulario ----------

    public void startCreate() {
        openEditor(null);
    }

    public void startEdit(UserRow row) {
        openEditor(Objects.requireNonNull(row, "row"));
    }

    private void openEditor(UserRow row) {
        editing = row;
        editorName.set(row == null ? "" : row.name());
        editorUsername.set(row == null ? "" : row.username());
        editorRole.set(row == null ? null : row.role());
        clearPasswords();
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        editorOpen.set(true);
    }

    private void clearPasswords() {
        editorPassword.set("");
        editorConfirmation.set("");
    }

    public void cancelEditor() {
        if (!editorSaving.get()) {
            clearPasswords();
            editorOpen.set(false);
        }
    }

    /** {@code null} al agregar. */
    public UserRow editing() {
        return editing;
    }

    private void clearFieldError(String field) {
        if (editorFieldErrors.get().containsKey(field)) {
            Map<String, String> rest = new LinkedHashMap<>(editorFieldErrors.get());
            rest.remove(field);
            editorFieldErrors.set(rest);
        }
    }

    /** Ignora el envío si el anterior no ha terminado. */
    public void submitEditor() {
        if (!editorOpen.get() || editorSaving.get()) {
            return;
        }
        editorSaving.set(true);
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        UserRow target = editing;
        String password = editorPassword.get();
        String confirmation = editorConfirmation.get();
        String name = editorName.get();
        String username = editorUsername.get();
        Role role = editorRole.get();
        User user = actor.get();
        background.run(() -> target == null
                ? useCases.createUser().execute(user, new CreateUserCommand(name, username, role, password, confirmation))
                : useCases.updateUser().execute(user, target.id(),
                        new UpdateUserCommand(name, username, role, password, confirmation)), saved -> {
            editorSaving.set(false);
            clearPasswords();
            editorOpen.set(false);
            notifier.ok(savedMessage(target, saved, password != null && !password.isEmpty()));
            refresh();
        }, error -> {
            editorSaving.set(false);
            ErrorReport report = ErrorReport.of(error);
            switch (report.kind()) {
                case VALIDATION -> editorFieldErrors.set(report.fieldErrors());
                case NOT_FOUND -> {
                    clearPasswords();
                    editorOpen.set(false);
                    notifier.error(report.message());
                    refresh();
                }
                default -> editorGeneralError.set(report.message());
            }
        });
    }

    static String savedMessage(UserRow before, UserRow saved, boolean newPassword) {
        if (before == null) {
            return "Se creó la cuenta de «" + saved.name() + "». Ya puede entrar como «" + saved.username() + "».";
        }
        StringBuilder text = new StringBuilder("Se guardaron los cambios de «" + saved.name() + "».");
        if (before.role() != saved.role()) {
            text.append(" El rol nuevo aplica desde su siguiente operación.");
        }
        if (newPassword) {
            text.append(" Su contraseña cambió.");
        }
        return text.toString();
    }

    // ---------- Acciones de fila ----------

    /** Desactivar impide entrar; sus cobros se conservan. */
    public void setActive(UserRow row, boolean active) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        User user = actor.get();
        background.run(() -> useCases.setUserActive().execute(user, row.id(), active), updated -> {
            busy.set(false);
            notifier.ok(active
                    ? "«" + updated.name() + "» puede entrar de nuevo."
                    : "«" + updated.name() + "» ya no puede entrar. Sus cobros se conservan.");
            refresh();
        }, this::actionFailed);
    }

    /** Solo se elimina una cuenta sin cobros; si no, el caso de uso dice por qué y qué hacer. */
    public void delete(UserRow row) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        User user = actor.get();
        background.run(() -> {
            useCases.deleteUser().execute(user, row.id());
            return row;
        }, deleted -> {
            busy.set(false);
            notifier.ok("Se eliminó la cuenta de «" + deleted.name() + "».");
            refresh();
        }, this::actionFailed);
    }

    private void actionFailed(Throwable error) {
        busy.set(false);
        ErrorReport report = ErrorReport.of(error);
        notifier.error(report.message());
        if (report.kind() == ErrorReport.Kind.NOT_FOUND) {
            refresh();
        }
    }

    // ---------- Textos ----------

    /** «4 cuentas · 3 activas». */
    public static String summary(List<UserRow> rows) {
        long active = rows.stream().filter(UserRow::active).count();
        String total = rows.size() == 1 ? "1 cuenta" : rows.size() + " cuentas";
        return total + " · " + (active == 1 ? "1 activa" : active + " activas");
    }

    /** Nombre de cada permiso, en el orden de {@link Permission}. */
    public static String permissionLabel(Permission permission) {
        return switch (permission) {
            case VIEW_DAILY_SUMMARY -> "Ver el tablero";
            case CHARGE_MEMBERSHIP -> "Cobrar membresías";
            case VIEW_MEMBERS -> "Consultar socios";
            case EDIT_MEMBERS -> "Registrar, editar y eliminar socios";
            case MANAGE_PLANS -> "Administrar planes";
            case MANAGE_USERS -> "Administrar usuarios";
            case MANAGE_SETTINGS -> "Configuración y métodos de pago";
        };
    }

    /** Lo que puede hacer un rol, sacado de {@code Role.permissions()}: «Puede: cobrar membresías y consultar socios.» */
    public static String roleSummary(Role role) {
        List<String> labels = Arrays.stream(Permission.values()).filter(role::can)
                .map(permission -> permissionLabel(permission).toLowerCase(java.util.Locale.ROOT)).toList();
        if (labels.size() == Permission.values().length) {
            return "Puede hacer todo: también administrar planes, usuarios y la configuración.";
        }
        String joined = labels.size() == 1 ? labels.getFirst()
                : String.join(", ", labels.subList(0, labels.size() - 1)) + " y " + labels.getLast();
        return "Puede " + joined + ".";
    }

    /** Para la matriz de permisos: cada permiso con los roles que lo tienen. */
    public static Map<Permission, List<Role>> permissionMatrix() {
        return Arrays.stream(Permission.values()).collect(Collectors.toMap(p -> p,
                p -> Arrays.stream(Role.values()).filter(r -> r.can(p)).toList(), (a, b) -> a, LinkedHashMap::new));
    }

    // ---------- Propiedades ----------

    public ObservableList<UserRow> items() {
        return readOnlyItems;
    }

    List<UserRow> snapshot() {
        return List.copyOf(items);
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty loadErrorProperty() {
        return loadError.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty editorOpenProperty() {
        return editorOpen.getReadOnlyProperty();
    }

    public StringProperty editorNameProperty() {
        return editorName;
    }

    public StringProperty editorUsernameProperty() {
        return editorUsername;
    }

    public ObjectProperty<Role> editorRoleProperty() {
        return editorRole;
    }

    public StringProperty editorPasswordProperty() {
        return editorPassword;
    }

    public StringProperty editorConfirmationProperty() {
        return editorConfirmation;
    }

    public ReadOnlyBooleanProperty editorSavingProperty() {
        return editorSaving.getReadOnlyProperty();
    }

    /** Los campos del caso de uso: {@code name}, {@code username}, {@code role} y los de contraseña. */
    public ReadOnlyObjectProperty<Map<String, String>> editorFieldErrorsProperty() {
        return editorFieldErrors.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty editorGeneralErrorProperty() {
        return editorGeneralError.getReadOnlyProperty();
    }
}
