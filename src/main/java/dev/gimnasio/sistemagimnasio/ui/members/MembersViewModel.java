package dev.gimnasio.sistemagimnasio.ui.members;

import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberSearchResult;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberSort;
import dev.gimnasio.sistemagimnasio.application.usecase.members.SearchMembersQuery;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Catálogo de socios: buscar y filtrar (consultar), registrar, corregir y eliminar. Las reglas son de los casos
 * de uso; aquí se piden en segundo plano, se descartan las respuestas viejas de la búsqueda y se reparten los
 * mensajes: los del formulario en sus campos, los demás en un aviso.
 */
public final class MembersViewModel {
    public enum State { LOADING, LOAD_FAILED, READY }

    private final UseCases useCases;
    private final Background background;
    private final Supplier<User> actor;
    private final Notifier notifier;
    private final boolean canEdit;

    private final StringProperty searchText = new SimpleStringProperty(this, "searchText", "");
    private final ObjectProperty<MemberStatus> statusFilter = new SimpleObjectProperty<>(this, "statusFilter");
    private final ObservableList<MemberRow> rows = FXCollections.observableArrayList();
    private final ObservableList<MemberRow> readOnlyRows = FXCollections.unmodifiableObservableList(rows);
    private final ReadOnlyObjectWrapper<Map<MemberStatus, Integer>> counts =
            new ReadOnlyObjectWrapper<>(this, "counts", emptyCounts());
    private final ReadOnlyIntegerWrapper totalMembers = new ReadOnlyIntegerWrapper(this, "totalMembers");
    private final ReadOnlyIntegerWrapper matchCount = new ReadOnlyIntegerWrapper(this, "matchCount");
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.LOADING);
    private final ReadOnlyStringWrapper loadError = new ReadOnlyStringWrapper(this, "loadError");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);
    /** Cada búsqueda lleva un número: si llega la respuesta de una anterior, se descarta. */
    private long searchRequest;

    private final ReadOnlyBooleanWrapper editorOpen = new ReadOnlyBooleanWrapper(this, "editorOpen", false);
    private final StringProperty editorName = new SimpleStringProperty(this, "editorName", "");
    private final StringProperty editorPhone = new SimpleStringProperty(this, "editorPhone", "");
    private final StringProperty editorEmail = new SimpleStringProperty(this, "editorEmail", "");
    private final ObjectProperty<LocalDate> editorJoinDate = new SimpleObjectProperty<>(this, "editorJoinDate");
    private final ReadOnlyBooleanWrapper editorSaving = new ReadOnlyBooleanWrapper(this, "editorSaving", false);
    private final ReadOnlyObjectWrapper<Map<String, String>> editorFieldErrors =
            new ReadOnlyObjectWrapper<>(this, "editorFieldErrors", Map.of());
    private final ReadOnlyStringWrapper editorGeneralError = new ReadOnlyStringWrapper(this, "editorGeneralError");
    private MemberRow editing;

    /**
     * @param canEdit si el rol puede registrar, corregir y eliminar (EDIT_MEMBERS); solo decide qué se muestra:
     *                los casos de uso vuelven a revisar el permiso
     * @param today   fecha de alta que se propone al registrar
     */
    public MembersViewModel(UseCases useCases, Background background, Supplier<User> actor, Notifier notifier,
                            boolean canEdit, Supplier<LocalDate> today) {
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.canEdit = canEdit;
        this.today = Objects.requireNonNull(today, "today");
        searchText.addListener((obs, old, text) -> search());
        statusFilter.addListener((obs, old, status) -> search());
    }

    private final Supplier<LocalDate> today;

    private static Map<MemberStatus, Integer> emptyCounts() {
        Map<MemberStatus, Integer> map = new EnumMap<>(MemberStatus.class);
        for (MemberStatus status : MemberStatus.values()) {
            map.put(status, 0);
        }
        return map;
    }

    /** La primera carga muestra «Cargando…»; las siguientes búsquedas actualizan la lista sin parpadear. */
    public void load() {
        state.set(State.LOADING);
        loadError.set(null);
        search();
    }

    private void search() {
        long request = ++searchRequest;
        SearchMembersQuery query = new SearchMembersQuery(searchText.get(), statusFilter.get(), MemberSort.VENCIMIENTO, null);
        User user = actor.get();
        background.run(() -> useCases.searchMembers().execute(user, query), result -> {
            if (request == searchRequest) {
                show(result);
            }
        }, error -> {
            if (request != searchRequest) {
                return;
            }
            String message = ErrorReport.of(error).message();
            if (state.get() == State.READY) {
                notifier.error(message);
            } else {
                loadError.set(message);
                state.set(State.LOAD_FAILED);
            }
        });
    }

    private void show(MemberSearchResult result) {
        rows.setAll(result.members());
        counts.set(result.countByStatus());
        totalMembers.set(result.totalMembers());
        matchCount.set(result.matchCount());
        state.set(State.READY);
    }

    public void startCreate() {
        openEditor(null, "", "", "", today.get());
    }

    public void startEdit(MemberRow row) {
        Objects.requireNonNull(row, "row");
        openEditor(row, row.fullName(), row.phone(), row.email() == null ? "" : row.email(), row.joinDate());
    }

    private void openEditor(MemberRow row, String name, String phone, String email, LocalDate joinDate) {
        if (!canEdit) {
            return;
        }
        editing = row;
        editorName.set(name);
        editorPhone.set(phone);
        editorEmail.set(email);
        editorJoinDate.set(joinDate);
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        editorOpen.set(true);
    }

    public void cancelEditor() {
        if (!editorSaving.get()) {
            editorOpen.set(false);
        }
    }

    /** {@code null} al registrar. */
    public MemberRow editing() {
        return editing;
    }

    public void submitEditor() {
        if (!editorOpen.get() || editorSaving.get()) {
            return;
        }
        editorSaving.set(true);
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        MemberCommand command = new MemberCommand(editorName.get(), editorPhone.get(), editorEmail.get(),
                editorJoinDate.get());
        MemberRow target = editing;
        User user = actor.get();
        background.run(() -> target == null
                ? useCases.registerMember().execute(user, command)
                : useCases.updateMember().execute(user, target.id(), command), saved -> {
            editorSaving.set(false);
            editorOpen.set(false);
            notifier.ok(target == null
                    ? "Se registró a «" + saved.fullName() + "». Queda sin membresía hasta su primer cobro."
                    : "Se guardaron los datos de «" + saved.fullName() + "».");
            search();
        }, error -> {
            editorSaving.set(false);
            ErrorReport report = ErrorReport.of(error);
            switch (report.kind()) {
                case VALIDATION -> editorFieldErrors.set(report.fieldErrors());
                case NOT_FOUND -> {
                    editorOpen.set(false);
                    notifier.error(report.message());
                    search();
                }
                default -> editorGeneralError.set(report.message());
            }
        });
    }

    /** Solo se borra un socio sin cobros; si ya tiene, el caso de uso lo dice. */
    public void delete(MemberRow row) {
        if (busy.get() || !canEdit) {
            return;
        }
        busy.set(true);
        User user = actor.get();
        background.run(() -> {
            useCases.deleteMember().execute(user, row.id());
            return row;
        }, deleted -> {
            busy.set(false);
            notifier.ok("Se eliminó a «" + deleted.fullName() + "».");
            search();
        }, error -> {
            busy.set(false);
            ErrorReport report = ErrorReport.of(error);
            notifier.error(report.message());
            if (report.kind() == ErrorReport.Kind.NOT_FOUND) {
                search();
            }
        });
    }

    /** «Quedan 12 días», «Vence hoy», «Venció hace 5 días», «Baja desde el 30/09/2026»… con los datos del caso de uso. */
    public static String validity(MemberRow row) {
        return switch (row.status()) {
            case SIN_MEMBRESIA -> "Sin membresía";
            case VIGENTE, POR_VENCER -> row.daysLeft() == 0 ? "Vence hoy"
                    : row.daysLeft() == 1 ? "Vence mañana" : "Quedan " + row.daysLeft() + " días";
            case VENCIDA -> "Venció hace " + days(row.daysOverdue()) + "; pasa a baja el "
                    + dev.gimnasio.sistemagimnasio.ui.common.Formats.date(row.deactivatesOn());
            case BAJA -> "Baja desde el " + dev.gimnasio.sistemagimnasio.ui.common.Formats.date(row.deactivationDate());
        };
    }

    private static String days(int days) {
        return days == 1 ? "1 día" : days + " días";
    }

    /** Nombre para mostrar de cada estado. */
    public static String statusLabel(MemberStatus status) {
        return switch (status) {
            case SIN_MEMBRESIA -> "Sin membresía";
            case VIGENTE -> "Vigente";
            case POR_VENCER -> "Por vencer";
            case VENCIDA -> "Vencida";
            case BAJA -> "Baja";
        };
    }

    List<MemberRow> snapshot() {
        return List.copyOf(rows);
    }

    public boolean canEdit() {
        return canEdit;
    }

    public StringProperty searchTextProperty() {
        return searchText;
    }

    public ObjectProperty<MemberStatus> statusFilterProperty() {
        return statusFilter;
    }

    public ObservableList<MemberRow> rows() {
        return readOnlyRows;
    }

    public ReadOnlyObjectProperty<Map<MemberStatus, Integer>> countsProperty() {
        return counts.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty totalMembersProperty() {
        return totalMembers.getReadOnlyProperty();
    }

    public ReadOnlyIntegerProperty matchCountProperty() {
        return matchCount.getReadOnlyProperty();
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

    public StringProperty editorPhoneProperty() {
        return editorPhone;
    }

    public StringProperty editorEmailProperty() {
        return editorEmail;
    }

    public ObjectProperty<LocalDate> editorJoinDateProperty() {
        return editorJoinDate;
    }

    public ReadOnlyBooleanProperty editorSavingProperty() {
        return editorSaving.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Map<String, String>> editorFieldErrorsProperty() {
        return editorFieldErrors.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty editorGeneralErrorProperty() {
        return editorGeneralError.getReadOnlyProperty();
    }
}
