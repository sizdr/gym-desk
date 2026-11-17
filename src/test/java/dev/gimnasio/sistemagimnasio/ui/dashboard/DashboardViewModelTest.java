package dev.gimnasio.sistemagimnasio.ui.dashboard;

import dev.gimnasio.sistemagimnasio.application.usecase.summary.DaySummary;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class DashboardViewModelTest {
    private static final LocalDate TODAY = UiFakes.TODAY;
    private final UiFakes.Users users = new UiFakes.Users();
    private final User recepcion = users.add("Rocío Méndez", "rocio", Role.RECEPCION, true);
    private final User caja = users.add("Carlos Ruiz", "carlos", Role.CAJA, true);
    private final UiFakes.Backend backend = new UiFakes.Backend(users);
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);

    @AfterEach
    void stop() {
        background.close();
        uiThread.shutdownNow();
    }

    private <T> T onUi(Callable<T> work) throws Exception {
        return uiThread.submit(work).get(2, TimeUnit.SECONDS);
    }

    private void waitUntil(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (onUi(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("no terminó a tiempo");
    }

    private DashboardViewModel model(User actor) {
        return new DashboardViewModel(backend.useCases(), background, () -> actor);
    }

    @Test
    void cargaElResumen() throws Exception {
        long mensual = backend.plans.add("Mensual", 30, "450", null, true).id();
        var ana = backend.members.add("Ana López", TODAY.minusDays(100));
        backend.memberships.add(ana.id(), mensual, TODAY.minusDays(27), TODAY.plusDays(3));
        DashboardViewModel model = model(recepcion);

        onUi(() -> {
            model.load();
            return null;
        });
        waitUntil(() -> model.stateProperty().get() == DashboardViewModel.State.READY);

        DaySummary summary = onUi(() -> model.summaryProperty().get());
        assertEquals(1, summary.expiringCount());
        assertEquals("Ana López", summary.expiring().getFirst().fullName());
        assertEquals(false, onUi(() -> model.refreshingProperty().get()));
    }

    @Test
    void sinPermiso_quedaElErrorConReintento() throws Exception {
        DashboardViewModel model = model(caja);
        onUi(() -> {
            model.load();
            return null;
        });
        waitUntil(() -> model.stateProperty().get() == DashboardViewModel.State.LOAD_FAILED);
        assertNotNull(onUi(() -> model.loadErrorProperty().get()));
        assertNull(onUi(() -> model.summaryProperty().get()));
    }

    private static DaySummary summary(LocalDate today, LocalDate weekEnd, int thisWeek, int expired, int payments) {
        return new DaySummary(today, weekEnd, 0, thisWeek, thisWeek, expired, Money.ZERO, payments, List.of(),
                List.of(), List.of());
    }

    @Test
    void textos() {
        LocalDate thursday = LocalDate.of(2026, 11, 12);
        LocalDate sunday = LocalDate.of(2026, 11, 15);
        assertEquals("Jueves 12 de noviembre de 2026 · la semana termina el domingo 15",
                DashboardViewModel.dateLine(summary(thursday, sunday, 0, 0, 0)));
        assertEquals("Domingo 15 de noviembre de 2026 · hoy termina la semana",
                DashboardViewModel.dateLine(summary(sunday, sunday, 0, 0, 0)));
        assertEquals("Ninguno vence esta semana", DashboardViewModel.expiringDetail(summary(thursday, sunday, 0, 0, 0)));
        assertEquals("1 vence esta semana", DashboardViewModel.expiringDetail(summary(thursday, sunday, 1, 0, 0)));
        assertEquals("4 vencen esta semana", DashboardViewModel.expiringDetail(summary(thursday, sunday, 4, 0, 0)));
        assertEquals("Sin cobros hoy", DashboardViewModel.paymentsDetail(summary(thursday, sunday, 0, 0, 0)));
        assertEquals("1 cobro", DashboardViewModel.paymentsDetail(summary(thursday, sunday, 0, 0, 1)));
        assertEquals("3 cobros", DashboardViewModel.paymentsDetail(summary(thursday, sunday, 0, 0, 3)));
        assertEquals("Nadie pendiente de renovar", DashboardViewModel.expiredDetail(summary(thursday, sunday, 0, 0, 0)));
        assertEquals("Pasan a baja si no renuevan", DashboardViewModel.expiredDetail(summary(thursday, sunday, 0, 2, 0)));
    }
}
