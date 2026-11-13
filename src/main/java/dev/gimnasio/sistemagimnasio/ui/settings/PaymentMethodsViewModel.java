package dev.gimnasio.sistemagimnasio.ui.settings;

import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Catálogo de métodos de pago de Configuración. Cada operación se guarda al momento (el contrato las corre
 * cada una en su transacción); las reglas —nombre repetido, último activo, método ya usado— son de los casos
 * de uso y aquí solo se reparten sus mensajes: los del formulario en sus campos, los demás en un aviso.
 */
public final class PaymentMethodsViewModel {
    public enum State { LOADING, LOAD_FAILED, READY }

    private final UseCases useCases;
    private final Background background;
    private final Supplier<User> actor;
    private final Notifier notifier;

    private final ObservableList<PaymentMethodItem> items = FXCollections.observableArrayList();
    /**
     * Una sola vista de solo lectura: escucha a {@code items} con una referencia débil, así que si se creara
     * una nueva en cada llamada, la recolección de basura le quitaría los avisos de cambio a la pantalla.
     */
    private final ObservableList<PaymentMethodItem> readOnlyItems = FXCollections.unmodifiableObservableList(items);
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.LOADING);
    private final ReadOnlyStringWrapper loadError = new ReadOnlyStringWrapper(this, "loadError");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);

    // El formulario de alta o edición (uno a la vez).
    private final ReadOnlyBooleanWrapper editorOpen = new ReadOnlyBooleanWrapper(this, "editorOpen", false);
    private final StringProperty editorName = new SimpleStringProperty(this, "editorName", "");
    private final BooleanProperty editorCash = new SimpleBooleanProperty(this, "editorCash", false);
    private final ReadOnlyBooleanWrapper editorSaving = new ReadOnlyBooleanWrapper(this, "editorSaving", false);
    private final ReadOnlyObjectWrapper<Map<String, String>> editorFieldErrors =
            new ReadOnlyObjectWrapper<>(this, "editorFieldErrors", Map.of());
    private final ReadOnlyStringWrapper editorGeneralError = new ReadOnlyStringWrapper(this, "editorGeneralError");
    private PaymentMethodItem editing;

    public PaymentMethodsViewModel(UseCases useCases, Background background, Supplier<User> actor, Notifier notifier) {
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    /** Activos e inactivos, por nombre. */
    public void load() {
        state.set(State.LOADING);
        loadError.set(null);
        User user = actor.get();
        background.run(() -> useCases.listPaymentMethods().execute(user, true), list -> {
            items.setAll(list);
            state.set(State.READY);
        }, error -> {
            loadError.set(ErrorReport.of(error).message());
            state.set(State.LOAD_FAILED);
        });
    }

    /** Vuelve a leer la lista sin pasar por «Cargando…» (después de un cambio o si otro usuario la cambió). */
    private void refresh() {
        User user = actor.get();
        background.run(() -> useCases.listPaymentMethods().execute(user, true), items::setAll,
                error -> notifier.error(ErrorReport.of(error).message()));
    }

    public void startCreate() {
        openEditor(null, "", false);
    }

    public void startEdit(PaymentMethodItem item) {
        openEditor(Objects.requireNonNull(item, "item"), item.name(), item.cash());
    }

    private void openEditor(PaymentMethodItem item, String name, boolean cash) {
        editing = item;
        editorName.set(name);
        editorCash.set(cash);
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        editorOpen.set(true);
    }

    public void cancelEditor() {
        if (!editorSaving.get()) {
            editorOpen.set(false);
        }
    }

    /** {@code null} al agregar. */
    public PaymentMethodItem editing() {
        return editing;
    }

    /** Ignora el envío si el anterior no ha terminado. */
    public void submitEditor() {
        if (!editorOpen.get() || editorSaving.get()) {
            return;
        }
        editorSaving.set(true);
        editorFieldErrors.set(Map.of());
        editorGeneralError.set(null);
        PaymentMethodCommand command = new PaymentMethodCommand(editorName.get(), editorCash.get());
        PaymentMethodItem target = editing;
        User user = actor.get();
        background.run(() -> target == null
                ? useCases.createPaymentMethod().execute(user, command)
                : useCases.updatePaymentMethod().execute(user, target.id(), command), saved -> {
            editorSaving.set(false);
            editorOpen.set(false);
            notifier.ok(target == null
                    ? "Se agregó «" + saved.name() + "». Ya se ofrece al cobrar y al vender."
                    : "Se guardaron los cambios de «" + saved.name() + "».");
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

    /** Desactivar deja de ofrecerlo al cobrar y al vender; los cobros y ventas anteriores no cambian. */
    public void setActive(PaymentMethodItem item, boolean active) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        User user = actor.get();
        background.run(() -> useCases.setPaymentMethodActive().execute(user, item.id(), active), updated -> {
            busy.set(false);
            replace(updated);
            notifier.ok(active
                    ? "«" + updated.name() + "» se ofrece de nuevo al cobrar y al vender."
                    : "«" + updated.name() + "» ya no se ofrece al cobrar ni al vender. Los cobros y ventas anteriores no cambian.");
        }, this::actionFailed);
    }

    /** Solo se borra uno que nunca se usó; si ya se usó, el caso de uso lo dice y sugiere desactivarlo. */
    public void delete(PaymentMethodItem item) {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        User user = actor.get();
        background.run(() -> {
            useCases.deletePaymentMethod().execute(user, item.id());
            return item;
        }, deleted -> {
            busy.set(false);
            items.removeIf(existing -> existing.id() == deleted.id());
            notifier.ok("Se eliminó «" + deleted.name() + "».");
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

    private void replace(PaymentMethodItem updated) {
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).id() == updated.id()) {
                items.set(i, updated);
                return;
            }
        }
        items.add(updated);
    }

    /** Solo lectura: cambia con las operaciones de este ViewModel. */
    public ObservableList<PaymentMethodItem> items() {
        return readOnlyItems;
    }

    /** Para pruebas: la lista como está ahora. */
    List<PaymentMethodItem> snapshot() {
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

    public BooleanProperty editorCashProperty() {
        return editorCash;
    }

    public ReadOnlyBooleanProperty editorSavingProperty() {
        return editorSaving.getReadOnlyProperty();
    }

    /** {@code name} y {@code cash}, como los manda el caso de uso. */
    public ReadOnlyObjectProperty<Map<String, String>> editorFieldErrorsProperty() {
        return editorFieldErrors.getReadOnlyProperty();
    }

    /** Un error del formulario que no es de un campo (permiso, sesión, fallo técnico). */
    public ReadOnlyStringProperty editorGeneralErrorProperty() {
        return editorGeneralError.getReadOnlyProperty();
    }
}
