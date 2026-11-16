package dev.gimnasio.sistemagimnasio.ui.billing;

import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipResult;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeOptions;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargePreview;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PlanOption;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberSort;
import dev.gimnasio.sistemagimnasio.application.usecase.members.SearchMembersQuery;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.Inputs;
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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Módulo de cobro de membresías. Socio, plan, método y efectivo se eligen aquí; el resumen (periodo, tipo de
 * cobro, total, cambio) lo calcula {@code PreviewMembershipCharge} cada vez que algo cambia, y cobrar lo hace
 * {@code ChargeMembership} con un {@code operationId} propio del formulario: un segundo envío no cobra dos veces.
 */
public final class ChargeViewModel {
    public enum State { LOADING, LOAD_FAILED, READY }

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final UseCases useCases;
    private final Background background;
    private final Supplier<User> actor;
    private final Notifier notifier;

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.LOADING);
    private final ReadOnlyStringWrapper loadError = new ReadOnlyStringWrapper(this, "loadError");
    private final ObservableList<PlanOption> plans = FXCollections.observableArrayList();
    private final ObservableList<PaymentMethodItem> methods = FXCollections.observableArrayList();

    private final StringProperty memberQuery = new SimpleStringProperty(this, "memberQuery", "");
    private final ObservableList<MemberRow> memberResults = FXCollections.observableArrayList();
    private final ObjectProperty<MemberRow> member = new SimpleObjectProperty<>(this, "member");
    private final ObjectProperty<PlanOption> plan = new SimpleObjectProperty<>(this, "plan");
    private final ObjectProperty<PaymentMethodItem> method = new SimpleObjectProperty<>(this, "method");
    private final StringProperty cashText = new SimpleStringProperty(this, "cashText", "");
    private final ObjectProperty<LocalDate> start = new SimpleObjectProperty<>(this, "start");

    private final ReadOnlyObjectWrapper<ChargePreview> preview = new ReadOnlyObjectWrapper<>(this, "preview");
    private final ReadOnlyBooleanWrapper charging = new ReadOnlyBooleanWrapper(this, "charging", false);
    private final ReadOnlyObjectWrapper<ChargeMembershipResult> result = new ReadOnlyObjectWrapper<>(this, "result");
    private final ReadOnlyObjectWrapper<Map<String, String>> fieldErrors =
            new ReadOnlyObjectWrapper<>(this, "fieldErrors", Map.of());
    private final ReadOnlyStringWrapper generalError = new ReadOnlyStringWrapper(this, "generalError");
    private final ReadOnlyBooleanWrapper printing = new ReadOnlyBooleanWrapper(this, "printing", false);

    private String operationId = UUID.randomUUID().toString();
    private long previewRequest;
    private long searchRequest;

    public ChargeViewModel(UseCases useCases, Background background, Supplier<User> actor, Notifier notifier) {
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        memberQuery.addListener((obs, old, text) -> searchMembers());
        member.addListener((obs, old, chosen) -> refreshPreview());
        plan.addListener((obs, old, chosen) -> refreshPreview());
        method.addListener((obs, old, chosen) -> refreshPreview());
        cashText.addListener((obs, old, text) -> refreshPreview());
        start.addListener((obs, old, date) -> refreshPreview());
    }

    /** Planes y métodos activos. */
    public void load() {
        state.set(State.LOADING);
        loadError.set(null);
        User user = actor.get();
        background.run(() -> useCases.listChargeOptions().execute(user), (ChargeOptions options) -> {
            plans.setAll(options.plans());
            methods.setAll(options.paymentMethods());
            state.set(State.READY);
            refreshPreview();
        }, error -> {
            loadError.set(ErrorReport.of(error).message());
            state.set(State.LOAD_FAILED);
        });
    }

    private void searchMembers() {
        long request = ++searchRequest;
        String text = memberQuery.get() == null ? "" : memberQuery.get().strip();
        if (text.isEmpty()) {
            memberResults.clear();
            return;
        }
        SearchMembersQuery query = new SearchMembersQuery(text, null, MemberSort.NOMBRE, 7);
        User user = actor.get();
        background.run(() -> useCases.searchMembers().execute(user, query), found -> {
            if (request == searchRequest) {
                memberResults.setAll(found.members());
            }
        }, error -> {
            if (request == searchRequest) {
                notifier.error(ErrorReport.of(error).message());
            }
        });
    }

    public void chooseMember(MemberRow row) {
        member.set(row);
        memberQuery.set("");
        clearFieldError("memberId");
    }

    public void clearMember() {
        member.set(null);
    }

    private void clearFieldError(String field) {
        if (fieldErrors.get().containsKey(field)) {
            Map<String, String> rest = new java.util.LinkedHashMap<>(fieldErrors.get());
            rest.remove(field);
            fieldErrors.set(rest);
        }
    }

    private ChargeMembershipCommand command() {
        return new ChargeMembershipCommand(operationId,
                member.get() == null ? null : member.get().id(),
                plan.get() == null ? null : plan.get().id(),
                method.get() == null ? null : method.get().id(),
                method.get() != null && method.get().cash() ? Inputs.money(cashText.get()) : null,
                start.get());
    }

    /** Vista previa en vivo; si llega la respuesta de una consulta anterior, se descarta. */
    private void refreshPreview() {
        if (state.get() != State.READY || result.get() != null) {
            return;
        }
        long request = ++previewRequest;
        ChargeMembershipCommand command = command();
        User user = actor.get();
        background.run(() -> useCases.previewMembershipCharge().execute(user, command), computed -> {
            if (request == previewRequest) {
                preview.set(computed);
            }
        }, error -> {
            if (request == previewRequest) {
                generalError.set(ErrorReport.of(error).message());
            }
        });
    }

    /** Cobra si la vista previa dice que se puede; un segundo clic mientras cobra se ignora. */
    public void charge() {
        if (charging.get() || result.get() != null || preview.get() == null || !preview.get().ready()) {
            return;
        }
        charging.set(true);
        fieldErrors.set(Map.of());
        generalError.set(null);
        ChargeMembershipCommand command = command();
        User user = actor.get();
        background.run(() -> useCases.chargeMembership().execute(user, command), done -> {
            charging.set(false);
            result.set(done);
            notifier.ok("Cobro registrado con el folio " + done.payment().folio() + ".");
        }, error -> {
            charging.set(false);
            ErrorReport report = ErrorReport.of(error);
            if (report.kind() == ErrorReport.Kind.VALIDATION) {
                fieldErrors.set(report.fieldErrors());
            } else {
                generalError.set(report.message());
            }
            refreshPreview();
        });
    }

    /** Imprime el recibo del cobro que se acaba de registrar. */
    public void printReceipt() {
        ChargeMembershipResult done = result.get();
        if (done == null || printing.get()) {
            return;
        }
        printing.set(true);
        User user = actor.get();
        background.run(() -> {
            useCases.printMembershipReceipt().execute(user, done.payment().id());
            return done.payment().folio();
        }, folio -> {
            printing.set(false);
            notifier.ok("Recibo " + folio + " enviado a imprimir.");
        }, error -> {
            printing.set(false);
            notifier.error(ErrorReport.of(error).message());
        });
    }

    /** Deja el formulario listo para otro cobro, con una operación nueva. */
    public void newCharge() {
        operationId = UUID.randomUUID().toString();
        result.set(null);
        fieldErrors.set(Map.of());
        generalError.set(null);
        cashText.set("");
        start.set(null);
        plan.set(null);
        member.set(null);
        memberQuery.set("");
        refreshPreview();
    }

    /** Qué regla se aplicó, con el inicio que calculó el caso de uso. */
    public static String explain(ChargeKind kind, LocalDate start) {
        if (kind == null) {
            return "";
        }
        String day = start == null ? "" : DAY.format(start);
        return switch (kind) {
            case PRIMERA -> "Primera membresía: empieza el " + day + ".";
            case RENOVACION_ANTICIPADA -> "Renovación anticipada: empieza el " + day
                    + ", al terminar su vigencia actual; no pierde días.";
            case RENOVACION_VENCIDA -> "Renovación de una membresía vencida: empieza hoy.";
            case REACTIVACION -> "Reactivación: estaba en baja y empieza hoy.";
        };
    }

    /** El primer motivo por el que todavía no se puede cobrar, para mostrarlo junto al botón. */
    public static String missing(ChargePreview preview) {
        if (preview == null) {
            return "";
        }
        return preview.errors().values().stream().findFirst().orElse("");
    }

    List<PlanOption> planSnapshot() {
        return List.copyOf(plans);
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty loadErrorProperty() {
        return loadError.getReadOnlyProperty();
    }

    public ObservableList<PlanOption> plans() {
        return plans;
    }

    public ObservableList<PaymentMethodItem> methods() {
        return methods;
    }

    public StringProperty memberQueryProperty() {
        return memberQuery;
    }

    public ObservableList<MemberRow> memberResults() {
        return memberResults;
    }

    public ReadOnlyObjectProperty<MemberRow> memberProperty() {
        return member;
    }

    public ObjectProperty<PlanOption> planProperty() {
        return plan;
    }

    public ObjectProperty<PaymentMethodItem> methodProperty() {
        return method;
    }

    public StringProperty cashTextProperty() {
        return cashText;
    }

    public ObjectProperty<LocalDate> startProperty() {
        return start;
    }

    public ReadOnlyObjectProperty<ChargePreview> previewProperty() {
        return preview.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty chargingProperty() {
        return charging.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<ChargeMembershipResult> resultProperty() {
        return result.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<Map<String, String>> fieldErrorsProperty() {
        return fieldErrors.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty generalErrorProperty() {
        return generalError.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty printingProperty() {
        return printing.getReadOnlyProperty();
    }

    /** Para pruebas: el id de la operación en curso. */
    String operationId() {
        return operationId;
    }
}
