package dev.gimnasio.sistemagimnasio.ui.plans;

import dev.gimnasio.sistemagimnasio.application.usecase.plans.PlanCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.PlanRow;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.SavingsView;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.Formats;
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

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Catálogo de planes: consultar, agregar, editar, activar o desactivar y eliminar. Las reglas (nombre repetido,
 * referencia mensual, plan con membresías) son de los casos de uso; aquí se reparten sus mensajes y se redacta
 * el ahorro que calculan.
 */
public final class PlansViewModel {
    public enum State { LOADING, LOAD_FAILED, READY }

    /** Una opción de «Referencia mensual»; {@link #NONE} es «Sin referencia». */
    public record ReferenceOption(Long id, String label) {
        public static final ReferenceOption NONE = new ReferenceOption(null, "Sin referencia");
    }

    private final UseCases useCases;
    private final Background background;
    private final Supplier<User> actor;
    private final Notifier notifier;
    private final Currency currency;

    private final ObservableList<PlanRow> items = FXCollections.observableArrayList();
    /** Una sola vista de solo lectura: con una nueva en cada llamada se perderían los avisos de cambio. */
    private final ObservableList<PlanRow> readOnlyItems = FXCollections.unmodifiableObservableList(items);
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.LOADING);
    private final ReadOnlyStringWrapper loadError = new ReadOnlyStringWrapper(this, "loadError");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);

    // El formulario de alta o edición (uno a la vez).
    private final ReadOnlyBooleanWrapper editorOpen = new ReadOnlyBooleanWrapper(this, "editorOpen", false);
    private final StringProperty editorName = new SimpleStringProperty(this, "editorName", "");
    private final StringProperty editorDuration = new SimpleStringProperty(this, "editorDuration", "");
    private final StringProperty editorPrice = new SimpleStringProperty(this, "editorPrice", "");
    private final ObjectProperty<ReferenceOption> editorReference =
            new SimpleObjectProperty<>(this, "editorReference", ReferenceOption.NONE);
    private final ObservableList<ReferenceOption> referenceOptions = FXCollections.observableArrayList();
    private final ReadOnlyObjectWrapper<SavingsView> editorSavings = new ReadOnlyObjectWrapper<>(this, "editorSavings");
    private final ReadOnlyBooleanWrapper editorSaving = new ReadOnlyBooleanWrapper(this, "editorSaving", false);
    private final ReadOnlyObjectWrapper<Map<String, String>> editorFieldErrors =
            new ReadOnlyObjectWrapper<>(this, "editorFieldErrors", Map.of());
    private final ReadOnlyStringWrapper editorGeneralError = new ReadOnlyStringWrapper(this, "editorGeneralError");
    private PlanRow editing;
    private long previewRequest;

    public PlansViewModel(UseCases useCases, Background background, Supplier<User> actor, Notifier notifier,
                          Currency currency) {
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
        this.currency = Objects.requireNonNull(currency, "currency");
        editorDuration.addListener((obs, old, value) -> fieldEdited("durationDays"));
        editorPrice.addListener((obs, old, value) -> fieldEdited("price"));
        editorReference.addListener((obs, old, value) -> fieldEdited("referenceMonthlyPlanId"));
        editorName.addListener((obs, old, value) -> clearFieldError("name"));
    }

    /** Activos primero, luego por duración y por nombre, como los ordena {@code ListPlans}. */
    public void load() {
        state.set(State.LOADING);
        loadError.set(null);
        User user = actor.get();
        background.run(() -> useCases.listPlans().execute(user), list -> {
            items.setAll(list);
            state.set(State.READY);
        }, error -> {
            loadError.set(ErrorReport.of(error).message());
            state.set(State.LOAD_FAILED);
        });
    }

    /**
     * Vuelve a leer la lista sin pasar por «Cargando…». Se lee completa después de cada cambio porque el ahorro de
     * un plan depende de su referencia: desactivar «Mensual» cambia el de los planes que se comparan con él.
     */
    private void refresh() {
        User user = actor.get();
        background.run(() -> useCases.listPlans().execute(user), items::setAll,
                error -> notifier.error(ErrorReport.of(error).message()));
    }

    // ---------- Formulario ----------

    public void startCreate() {
        openEditor(null);
    }

    public void startEdit(PlanRow plan) {
        openEditor(Objects.requireNonNull(plan, "plan"));
    }

    private void openEditor(PlanRow plan) {
        editing = plan;
        List<ReferenceOption> options = new ArrayList<>();
        options.add(ReferenceOption.NONE);
        ReferenceOption chosen = ReferenceOption.NONE;
        for (PlanRow candidate : items) {
            if (candidate.durationDays() != Plan.MONTHLY_DAYS || (plan != null && candidate.id() == plan.id())) {
                continue;
            }
            ReferenceOption option = new ReferenceOption(candidate.id(), candidate.name() + " · "
                    + Formats.money(candidate.price(), currency) + (candidate.active() ? "" : " (inactivo)"));
            options.add(option);
            if (plan != null && Objects.equals(plan.referenceMonthlyPlanId(), candidate.id())) {
                chosen = option;
            }
        }
        referenceOptions.setAll(options);
        editorName.set(plan == null ? "" : plan.name());
        editorDuration.set(plan == null ? "" : String.valueOf(plan.durationDays()));
        editorPrice.set(plan == null ? "" : plan.price().amount().toPlainString());
        editorReference.set(chosen);
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        editorSavings.set(plan == null ? null : plan.savings());
        editorOpen.set(true);
        refreshSavings();
    }

    public void cancelEditor() {
        if (!editorSaving.get()) {
            editorOpen.set(false);
        }
    }

    /** {@code null} al agregar. */
    public PlanRow editing() {
        return editing;
    }

    private PlanCommand command() {
        ReferenceOption reference = editorReference.get();
        return new PlanCommand(editorName.get(), Inputs.integer(editorDuration.get()), Inputs.money(editorPrice.get()),
                reference == null ? null : reference.id());
    }

    private void fieldEdited(String field) {
        clearFieldError(field);
        refreshSavings();
    }

    private void clearFieldError(String field) {
        if (editorFieldErrors.get().containsKey(field)) {
            Map<String, String> rest = new java.util.LinkedHashMap<>(editorFieldErrors.get());
            rest.remove(field);
            editorFieldErrors.set(rest);
        }
    }

    /** Vista previa del ahorro con lo escrito; una respuesta vieja se descarta. */
    private void refreshSavings() {
        if (!editorOpen.get()) {
            return;
        }
        long request = ++previewRequest;
        PlanCommand command = command();
        if (command.durationDays() == null) {
            // Sin duración todavía no hay nada que comparar: se pide escribirla en lugar de decir «no aplica».
            editorSavings.set(null);
            return;
        }
        User user = actor.get();
        background.run(() -> useCases.previewPlanSavings().execute(user, command), savings -> {
            if (request == previewRequest) {
                editorSavings.set(savings);
            }
        }, error -> {
            if (request == previewRequest) {
                editorSavings.set(null);
            }
        });
    }

    /** Ignora el envío si el anterior no ha terminado. */
    public void submitEditor() {
        if (!editorOpen.get() || editorSaving.get()) {
            return;
        }
        editorSaving.set(true);
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        PlanCommand command = command();
        PlanRow target = editing;
        User user = actor.get();
        background.run(() -> target == null
                ? useCases.createPlan().execute(user, command)
                : useCases.updatePlan().execute(user, target.id(), command), saved -> {
            editorSaving.set(false);
            editorOpen.set(false);
            notifier.ok(savedMessage(target, saved));
            refresh();
        }, error -> {
            editorSaving.set(false);
            ErrorReport report = ErrorReport.of(error);
            switch (report.kind()) {
                case VALIDATION -> editorFieldErrors.set(report.fieldErrors());
                case NOT_FOUND -> {
                    editorOpen.set(false);
                    notifier.error(report.message());
                    refresh();
                }
                default -> editorGeneralError.set(report.message());
            }
        });
    }

    static String savedMessage(PlanRow before, PlanRow saved) {
        if (before == null) {
            return "Se agregó el plan «" + saved.name() + "». Ya se ofrece al cobrar.";
        }
        boolean newPrice = before.price().amount().compareTo(saved.price().amount()) != 0;
        return "Se guardaron los cambios de «" + saved.name() + "»."
                + (newPrice ? " El precio nuevo aplica a los cobros nuevos; los anteriores conservan lo pagado." : "");
    }

    // ---------- Acciones de fila ----------

    /** Desactivar deja de ofrecerlo al cobrar; su historial se conserva. */
    public void setActive(PlanRow plan, boolean active) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        User user = actor.get();
        background.run(() -> useCases.setPlanActive().execute(user, plan.id(), active), updated -> {
            busy.set(false);
            notifier.ok(active
                    ? "«" + updated.name() + "» se ofrece de nuevo al cobrar."
                    : "«" + updated.name() + "» ya no se ofrece al cobrar. Sus membresías y cobros se conservan.");
            refresh();
        }, this::actionFailed);
    }

    /** Solo se borra un plan que nadie usa; si no, el caso de uso dice por qué y qué hacer. */
    public void delete(PlanRow plan) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        User user = actor.get();
        background.run(() -> {
            useCases.deletePlan().execute(user, plan.id());
            return plan;
        }, deleted -> {
            busy.set(false);
            notifier.ok("Se eliminó el plan «" + deleted.name() + "».");
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

    /** «6 planes · 5 activos». */
    public static String summary(List<PlanRow> plans) {
        long active = plans.stream().filter(PlanRow::active).count();
        String total = plans.size() == 1 ? "1 plan" : plans.size() + " planes";
        return total + " · " + (active == 1 ? "1 activo" : active + " activos");
    }

    /** Duración y referencia, debajo del nombre: «365 días · se compara con Mensual». */
    public static String detail(PlanRow plan) {
        String days = plan.durationDays() == 1 ? "1 día" : plan.durationDays() + " días";
        return plan.referenceMonthlyPlanName() == null ? days
                : days + " · se compara con " + plan.referenceMonthlyPlanName();
    }

    /** Para la columna Ahorro: corto, porque el encabezado ya dice qué es («$1,200.00 (22%)»). */
    public String savingsShort(SavingsView savings) {
        if (savings == null) {
            return "—";
        }
        return switch (savings.status()) {
            case AHORRO -> Formats.money(savings.amount(), currency) + " (" + savings.percent() + "%)";
            case SIN_AHORRO -> "Sin ahorro";
            case NO_APLICA -> "—";
            case NO_DISPONIBLE -> "No disponible";
        };
    }

    /** Para la vista previa del formulario: con la cuenta completa o el motivo. */
    public String savingsLong(SavingsView savings) {
        if (savings == null) {
            return "Escribe la duración y el precio para ver el ahorro.";
        }
        return switch (savings.status()) {
            case AHORRO -> "Ahorra " + Formats.money(savings.amount(), currency) + " frente a " + months(savings)
                    + " de «" + savings.referencePlanName() + "» (" + Formats.money(savings.monthlyTotal(), currency)
                    + "): " + savings.percent() + "% menos.";
            case SIN_AHORRO -> "Sin ahorro: " + months(savings) + " de «" + savings.referencePlanName()
                    + "» cuestan " + Formats.money(savings.monthlyTotal(), currency) + ", lo mismo o menos que este plan.";
            case NO_APLICA -> "Un plan de 30 días o menos no muestra ahorro.";
            case NO_DISPONIBLE -> "No se puede calcular el ahorro: " + savings.reason() + ".";
        };
    }

    private static String months(SavingsView savings) {
        return savings.months() == 1 ? "1 mes" : savings.months() + " meses";
    }

    // ---------- Propiedades ----------

    /** Solo lectura: cambia con las operaciones de este ViewModel. */
    public ObservableList<PlanRow> items() {
        return readOnlyItems;
    }

    /** Para pruebas: la lista como está ahora. */
    List<PlanRow> snapshot() {
        return List.copyOf(items);
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty loadErrorProperty() {
        return loadError.getReadOnlyProperty();
    }

    /** Una operación de fila (activar, desactivar, borrar) en curso: las demás esperan. */
    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty editorOpenProperty() {
        return editorOpen.getReadOnlyProperty();
    }

    public StringProperty editorNameProperty() {
        return editorName;
    }

    public StringProperty editorDurationProperty() {
        return editorDuration;
    }

    public StringProperty editorPriceProperty() {
        return editorPrice;
    }

    public ObjectProperty<ReferenceOption> editorReferenceProperty() {
        return editorReference;
    }

    /** «Sin referencia» y los planes de 30 días, salvo el que se edita. */
    public ObservableList<ReferenceOption> referenceOptions() {
        return referenceOptions;
    }

    public ReadOnlyObjectProperty<SavingsView> editorSavingsProperty() {
        return editorSavings.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty editorSavingProperty() {
        return editorSaving.getReadOnlyProperty();
    }

    /** {@code name}, {@code durationDays}, {@code price} y {@code referenceMonthlyPlanId}, como los manda el caso de uso. */
    public ReadOnlyObjectProperty<Map<String, String>> editorFieldErrorsProperty() {
        return editorFieldErrors.getReadOnlyProperty();
    }

    /** Un error del formulario que no es de un campo (permiso, sesión, fallo técnico). */
    public ReadOnlyStringProperty editorGeneralErrorProperty() {
        return editorGeneralError.getReadOnlyProperty();
    }
}
