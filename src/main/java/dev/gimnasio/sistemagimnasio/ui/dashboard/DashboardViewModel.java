package dev.gimnasio.sistemagimnasio.ui.dashboard;

import dev.gimnasio.sistemagimnasio.application.usecase.summary.DaySummary;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import dev.gimnasio.sistemagimnasio.ui.common.Formats;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Tablero: control de vigencias del día. Todo lo que muestra viene de {@code GetDaySummary}; aquí solo se
 * redactan los textos.
 */
public final class DashboardViewModel {
    public enum State { LOADING, READY, LOAD_FAILED }

    private final UseCases useCases;
    private final Background background;
    private final Supplier<User> actor;
    private final ReadOnlyObjectWrapper<State> state = new ReadOnlyObjectWrapper<>(this, "state", State.LOADING);
    private final ReadOnlyObjectWrapper<DaySummary> summary = new ReadOnlyObjectWrapper<>(this, "summary");
    private final ReadOnlyStringWrapper loadError = new ReadOnlyStringWrapper(this, "loadError");
    private final ReadOnlyBooleanWrapper refreshing = new ReadOnlyBooleanWrapper(this, "refreshing");
    private long request;

    public DashboardViewModel(UseCases useCases, Background background, Supplier<User> actor) {
        this.useCases = Objects.requireNonNull(useCases, "useCases");
        this.background = Objects.requireNonNull(background, "background");
        this.actor = Objects.requireNonNull(actor, "actor");
    }

    /** Carga o actualiza. Mientras actualiza se sigue viendo lo anterior; una respuesta vieja se descarta. */
    public void load() {
        long current = ++request;
        if (summary.get() == null) {
            state.set(State.LOADING);
        }
        refreshing.set(true);
        loadError.set(null);
        User user = actor.get();
        background.run(() -> useCases.getDaySummary().execute(user), result -> {
            if (current != request) {
                return;
            }
            summary.set(result);
            state.set(State.READY);
            refreshing.set(false);
        }, error -> {
            if (current != request) {
                return;
            }
            refreshing.set(false);
            loadError.set(ErrorReport.of(error).message());
            if (summary.get() == null) {
                state.set(State.LOAD_FAILED);
            }
        });
    }

    public ReadOnlyObjectProperty<State> stateProperty() {
        return state.getReadOnlyProperty();
    }

    public ReadOnlyObjectProperty<DaySummary> summaryProperty() {
        return summary.getReadOnlyProperty();
    }

    public ReadOnlyStringProperty loadErrorProperty() {
        return loadError.getReadOnlyProperty();
    }

    public ReadOnlyBooleanProperty refreshingProperty() {
        return refreshing.getReadOnlyProperty();
    }

    // ---------- Textos ----------

    /** «Jueves 12 de noviembre de 2026 · la semana termina el domingo 15». */
    public static String dateLine(DaySummary summary) {
        String today = Formats.longDate(summary.today());
        return summary.weekEnd().equals(summary.today()) ? today + " · hoy termina la semana"
                : today + " · la semana termina el domingo " + summary.weekEnd().getDayOfMonth();
    }

    /** Debajo de «Por vencer». */
    public static String expiringDetail(DaySummary summary) {
        int week = summary.expiringThisWeekCount();
        return switch (week) {
            case 0 -> "Ninguno vence esta semana";
            case 1 -> "1 vence esta semana";
            default -> week + " vencen esta semana";
        };
    }

    /** Debajo de «Cobrado hoy». */
    public static String paymentsDetail(DaySummary summary) {
        return switch (summary.paymentsToday()) {
            case 0 -> "Sin cobros hoy";
            case 1 -> "1 cobro";
            default -> summary.paymentsToday() + " cobros";
        };
    }

    /** Debajo de «Vencidas». */
    public static String expiredDetail(DaySummary summary) {
        return summary.expiredCount() == 0 ? "Nadie pendiente de renovar" : "Pasan a baja si no renuevan";
    }
}
