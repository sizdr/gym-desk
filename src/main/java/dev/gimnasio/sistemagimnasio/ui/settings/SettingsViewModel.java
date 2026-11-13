package dev.gimnasio.sistemagimnasio.ui.settings;

import dev.gimnasio.sistemagimnasio.application.usecase.settings.GetSettings;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.SettingsView;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.UpdateSettings;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.UpdateSettingsCommand;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.Inputs;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.StringBinding;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Estado del formulario de Configuración. Las reglas (largos, rangos, formato del logotipo, moneda bloqueada)
 * son de {@link UpdateSettings}; aquí se carga, se lleva la cuenta de lo que cambió respecto de lo guardado y
 * se reparte la respuesta entre los campos y el aviso general.
 */
public final class SettingsViewModel {
    public enum State { LOADING, LOAD_FAILED, READY }

    /** Las monedas que se ofrecen, como en el prototipo. Si la guardada es otra, también se ofrece. */
    static final Map<String, String> CURRENCY_NAMES = Map.of(
            "MXN", "Peso mexicano",
            "USD", "Dólar estadounidense");
    private static final List<Currency> OFFERED = List.of(Currency.getInstance("MXN"), Currency.getInstance("USD"));

    /** Lo que se cargó o se guardó por última vez, para saber si hay cambios y para descartarlos. */
    private record Draft(String gymName, String address, String phone, String receiptFooter, byte[] logo,
                         Currency currency, String warningDays, String autoDeactivationDays, String folioPrefix) {
        static Draft of(SettingsView view) {
            return new Draft(text(view.gymName()), text(view.address()), text(view.phone()),
                    text(view.receiptFooter()), view.logo(), view.currency(),
                    String.valueOf(view.warningDays()), String.valueOf(view.autoDeactivationDays()),
                    text(view.folioPrefix()));
        }

        boolean sameAs(Draft other) {
            return gymName.equals(other.gymName) && address.equals(other.address) && phone.equals(other.phone)
                    && receiptFooter.equals(other.receiptFooter) && Arrays.equals(logo, other.logo)
                    && Objects.equals(currency, other.currency) && warningDays.equals(other.warningDays)
                    && autoDeactivationDays.equals(other.autoDeactivationDays) && folioPrefix.equals(other.folioPrefix);
        }
    }

    private final GetSettings getSettings;
    private final UpdateSettings updateSettings;
    private final Background background;
    private final Supplier<User> actor;
    private final Consumer<SettingsView> onSaved;

    private final StringProperty gymName = new SimpleStringProperty(this, "gymName", "");
    private final StringProperty address = new SimpleStringProperty(this, "address", "");
    private final StringProperty phone = new SimpleStringProperty(this, "phone", "");
    private final StringProperty receiptFooter = new SimpleStringProperty(this, "receiptFooter", "");
    private final ObjectProperty<byte[]> logo = new SimpleObjectProperty<>(this, "logo");
    private final ObjectProperty<Currency> currency = new SimpleObjectProperty<>(this, "currency");
    private final StringProperty warningDays = new SimpleStringProperty(this, "warningDays", "");
    private final StringProperty autoDeactivationDays = new SimpleStringProperty(this, "autoDeactivationDays", "");
    private final StringProperty folioPrefix = new SimpleStringProperty(this, "folioPrefix", "");

    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.LOADING);
    private final ReadOnlyStringWrapper loadError = new ReadOnlyStringWrapper(this, "loadError");
    private final ReadOnlyBooleanWrapper saving = new ReadOnlyBooleanWrapper(this, "saving", false);
    private final ReadOnlyStringWrapper generalError = new ReadOnlyStringWrapper(this, "generalError");
    private final ReadOnlyObjectWrapper<Map<String, String>> fieldErrors =
            new ReadOnlyObjectWrapper<>(this, "fieldErrors", Map.of());
    private final ReadOnlyBooleanWrapper currencyLocked = new ReadOnlyBooleanWrapper(this, "currencyLocked", false);
    private final ReadOnlyStringWrapper currencyHelp = new ReadOnlyStringWrapper(this, "currencyHelp", "");
    private final ReadOnlyObjectWrapper<List<Currency>> currencies =
            new ReadOnlyObjectWrapper<>(this, "currencies", OFFERED);
    private final ObjectProperty<Draft> saved = new SimpleObjectProperty<>(this, "saved");
    /** Lo que sigue al prefijo en el folio de ejemplo («0001» en «GYM-0001»): el ancho lo decide el sistema. */
    private final StringProperty folioNumber = new SimpleStringProperty(this, "folioNumber", "");

    private final BooleanBinding dirty;
    private final StringBinding folioExample;

    public SettingsViewModel(GetSettings getSettings, UpdateSettings updateSettings, Background background,
                             Supplier<User> actor, Consumer<SettingsView> onSaved) {
        this.getSettings = Objects.requireNonNull(getSettings, "getSettings");
        this.updateSettings = Objects.requireNonNull(updateSettings, "updateSettings");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
        this.onSaved = Objects.requireNonNull(onSaved, "onSaved");
        dirty = Bindings.createBooleanBinding(() -> saved.get() != null && !current().sameAs(saved.get()),
                saved, gymName, address, phone, receiptFooter, logo, currency, warningDays, autoDeactivationDays,
                folioPrefix);
        folioExample = Bindings.createStringBinding(() -> {
            String prefix = folioPrefix.get().strip();
            return prefix.isEmpty() ? "" : prefix + folioNumber.get();
        }, folioPrefix, folioNumber);
    }

    /** Lee la configuración guardada. Mientras tanto la pantalla muestra que está cargando. */
    public void load() {
        state.set(State.LOADING);
        loadError.set(null);
        background.run(() -> getSettings.execute(actor.get()), view -> {
            apply(view);
            state.set(State.READY);
        }, error -> {
            loadError.set(ErrorReport.of(error).message());
            state.set(State.LOAD_FAILED);
        });
    }

    /** Ignora el envío si no hay cambios o si el anterior no ha terminado (doble clic, Enter repetido). */
    public void save() {
        if (saving.get() || state.get() != State.READY || !dirty.get()) {
            return;
        }
        saving.set(true);
        generalError.set(null);
        fieldErrors.set(Map.of());
        UpdateSettingsCommand command = new UpdateSettingsCommand(gymName.get(), address.get(), phone.get(),
                receiptFooter.get(), logo.get(), currency.get(), Inputs.integer(warningDays.get()),
                Inputs.integer(autoDeactivationDays.get()), folioPrefix.get());
        User user = actor.get();
        background.run(() -> updateSettings.execute(user, command), this::saved, this::saveFailed);
    }

    /** Vuelve a lo último guardado. */
    public void discard() {
        if (saving.get() || saved.get() == null) {
            return;
        }
        restore(saved.get());
        generalError.set(null);
        fieldErrors.set(Map.of());
    }

    /** La imagen elegida tal como se leyó; el caso de uso decide al guardar si el formato y el tamaño sirven. */
    public void useLogo(byte[] image) {
        logo.set(image == null ? null : image.clone());
        clearFieldError("logo");
    }

    public void removeLogo() {
        logo.set(null);
        clearFieldError("logo");
    }

    /** No se pudo leer el archivo elegido: se avisa en el campo y el logotipo no cambia. */
    public void logoUnreadable() {
        fieldErrors.set(Map.of("logo", "No se pudo leer ese archivo. Elige otra imagen PNG o JPG."));
    }

    /** {@code true} si hay algo escrito que todavía no se guarda: salir de la pantalla lo perdería. */
    public boolean hasUnsavedChanges() {
        return state.get() == State.READY && dirty.get();
    }

    /** {@code true} si el logotipo en pantalla es distinto del guardado. */
    public boolean logoChanged() {
        return saved.get() != null && !Arrays.equals(logo.get(), saved.get().logo());
    }

    private void saved(SettingsView view) {
        saving.set(false);
        apply(view);
        onSaved.accept(view);
    }

    private void saveFailed(Throwable error) {
        saving.set(false);
        ErrorReport report = ErrorReport.of(error);
        if (report.kind() == ErrorReport.Kind.VALIDATION) {
            fieldErrors.set(report.fieldErrors());
        } else {
            generalError.set(report.message());
        }
    }

    private void apply(SettingsView view) {
        Draft draft = Draft.of(view);
        String prefix = text(view.folioPrefix());
        String example = text(view.folioExample());
        folioNumber.set(example.startsWith(prefix) ? example.substring(prefix.length()) : "");
        currencyLocked.set(view.currencyLocked());
        currencyHelp.set(view.currencyLocked()
                ? "Bloqueada: ya hay " + recorded(view.paymentsCount(), view.salesCount()) + " registrados en "
                + view.currency().getCurrencyCode() + ". Cambiarla alteraría el significado de esos importes."
                : "Solo se puede elegir mientras no haya cobros ni ventas.");
        List<Currency> options = new ArrayList<>(OFFERED);
        if (view.currency() != null && !options.contains(view.currency())) {
            options.add(view.currency());
        }
        currencies.set(List.copyOf(options));
        // Primero lo guardado y después los campos: quien escuche un campo ya ve que coincide con lo guardado.
        saved.set(draft);
        restore(draft);
        generalError.set(null);
        fieldErrors.set(Map.of());
    }

    private void restore(Draft draft) {
        gymName.set(draft.gymName());
        address.set(draft.address());
        phone.set(draft.phone());
        receiptFooter.set(draft.receiptFooter());
        logo.set(draft.logo());
        currency.set(draft.currency());
        warningDays.set(draft.warningDays());
        autoDeactivationDays.set(draft.autoDeactivationDays());
        folioPrefix.set(draft.folioPrefix());
    }

    private Draft current() {
        return new Draft(text(gymName.get()), text(address.get()), text(phone.get()), text(receiptFooter.get()),
                logo.get(), currency.get(), text(warningDays.get()), text(autoDeactivationDays.get()),
                text(folioPrefix.get()));
    }

    private void clearFieldError(String field) {
        if (fieldErrors.get().containsKey(field)) {
            Map<String, String> rest = new java.util.LinkedHashMap<>(fieldErrors.get());
            rest.remove(field);
            fieldErrors.set(rest);
        }
    }

    /** «41 cobros y 18 ventas», «1 cobro», «3 ventas». */
    static String recorded(int payments, int sales) {
        List<String> parts = new ArrayList<>();
        if (payments > 0) {
            parts.add(payments + (payments == 1 ? " cobro" : " cobros"));
        }
        if (sales > 0) {
            parts.add(sales + (sales == 1 ? " venta" : " ventas"));
        }
        return String.join(" y ", parts);
    }

    /** «MXN · Peso mexicano»; una moneda sin nombre conocido, solo con su clave. */
    public static String currencyLabel(Currency currency) {
        if (currency == null) {
            return "";
        }
        String name = CURRENCY_NAMES.get(currency.getCurrencyCode());
        return name == null ? currency.getCurrencyCode() : currency.getCurrencyCode() + " · " + name;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    public StringProperty gymNameProperty() {
        return gymName;
    }

    public StringProperty addressProperty() {
        return address;
    }

    public StringProperty phoneProperty() {
        return phone;
    }

    public StringProperty receiptFooterProperty() {
        return receiptFooter;
    }

    public ReadOnlyObjectProperty<byte[]> logoProperty() {
        return logo;
    }

    public ObjectProperty<Currency> currencyProperty() {
        return currency;
    }

    public StringProperty warningDaysProperty() {
        return warningDays;
    }

    public StringProperty autoDeactivationDaysProperty() {
        return autoDeactivationDays;
    }

    public StringProperty folioPrefixProperty() {
        return folioPrefix;
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    /** {@code null} salvo en {@link State#LOAD_FAILED}. */
    public ReadOnlyStringProperty loadErrorProperty() {
        return loadError.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty savingProperty() {
        return saving.getReadOnlyProperty();
    }

    /** Un error que no es de un campo (permiso, sesión, fallo técnico); {@code null} si no hay. */
    public ReadOnlyStringProperty generalErrorProperty() {
        return generalError.getReadOnlyProperty();
    }

    /** Campo → mensaje, en el orden en que se validaron. */
    public ReadOnlyObjectProperty<Map<String, String>> fieldErrorsProperty() {
        return fieldErrors.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty currencyLockedProperty() {
        return currencyLocked.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty currencyHelpProperty() {
        return currencyHelp.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<List<Currency>> currenciesProperty() {
        return currencies.getReadOnlyProperty();
    }

    /** Hay cambios sin guardar. */
    public BooleanBinding dirtyProperty() {
        return dirty;
    }

    /** El folio de cobro con el prefijo escrito, por ejemplo «GYM-0001»; vacío si no hay prefijo. */
    public StringBinding folioExampleProperty() {
        return folioExample;
    }
}
