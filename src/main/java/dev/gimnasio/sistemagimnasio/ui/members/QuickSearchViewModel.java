package dev.gimnasio.sistemagimnasio.ui.members;

import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberSort;
import dev.gimnasio.sistemagimnasio.application.usecase.members.SearchMembersQuery;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Búsqueda rápida del encabezado: mientras se escribe, los primeros socios que coinciden (por nombre, correo o
 * teléfono, como en Socios) con su estado. Las respuestas viejas se descartan.
 */
public final class QuickSearchViewModel {
    /** Cuántos socios muestra el panel; los demás se ven en Socios. */
    static final int LIMIT = 7;

    private final UseCases useCases;
    private final Background background;
    private final Supplier<User> actor;
    private final StringProperty text = new SimpleStringProperty(this, "text", "");
    private final ObservableList<MemberRow> results = FXCollections.observableArrayList();
    private final ObservableList<MemberRow> readOnlyResults = FXCollections.unmodifiableObservableList(results);
    private final ReadOnlyIntegerWrapper matchCount = new ReadOnlyIntegerWrapper(this, "matchCount");
    private final ReadOnlyBooleanWrapper searching = new ReadOnlyBooleanWrapper(this, "searching");
    private final ReadOnlyBooleanWrapper open = new ReadOnlyBooleanWrapper(this, "open");
    private final ReadOnlyStringWrapper error = new ReadOnlyStringWrapper(this, "error");
    private long request;

    public QuickSearchViewModel(UseCases useCases, Background background, Supplier<User> actor) {
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
        text.addListener((obs, old, value) -> search());
    }

    private void search() {
        long current = ++request;
        String query = text.get() == null ? "" : text.get().strip();
        if (query.isEmpty()) {
            results.clear();
            matchCount.set(0);
            searching.set(false);
            error.set(null);
            open.set(false);
            return;
        }
        open.set(true);
        searching.set(true);
        User user = actor.get();
        background.run(() -> useCases.searchMembers().execute(user,
                new SearchMembersQuery(query, null, MemberSort.NOMBRE, LIMIT)), found -> {
            if (current != request) {
                return;
            }
            error.set(null);
            results.setAll(found.members());
            matchCount.set(found.matchCount());
            searching.set(false);
        }, failure -> {
            if (current != request) {
                return;
            }
            results.clear();
            matchCount.set(0);
            error.set(ErrorReport.of(failure).message());
            searching.set(false);
        });
    }

    /** Cierra el panel y deja el campo vacío (después de elegir, con Esc o al salir de la búsqueda). */
    public void clear() {
        text.set("");
    }

    /** «7 de 12 socios» si hay más de los que caben; si no, «3 socios». */
    public static String countText(int shown, int total) {
        if (total > shown) {
            return shown + " de " + total + " socios";
        }
        return total == 1 ? "1 socio" : total + " socios";
    }

    public StringProperty textProperty() {
        return text;
    }

    /** Solo lectura: cambia con cada búsqueda. */
    public ObservableList<MemberRow> results() {
        return readOnlyResults;
    }

    List<MemberRow> snapshot() {
        return List.copyOf(results);
    }

    /** Cuántos coinciden en total, aunque solo se muestren {@link #LIMIT}. */
    public ReadOnlyIntegerProperty matchCountProperty() {
        return matchCount.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty searchingProperty() {
        return searching.getReadOnlyProperty();
    }

    /** Hay algo escrito: el panel se muestra. */
    public ReadOnlyBooleanProperty openProperty() {
        return open.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty errorProperty() {
        return error.getReadOnlyProperty();
    }
}
