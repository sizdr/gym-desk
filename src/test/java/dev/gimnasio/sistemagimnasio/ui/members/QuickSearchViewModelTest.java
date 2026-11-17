package dev.gimnasio.sistemagimnasio.ui.members;

import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuickSearchViewModelTest {
    private final UiFakes.Users users = new UiFakes.Users();
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

    private QuickSearchViewModel model(User actor) {
        return new QuickSearchViewModel(backend.useCases(), background, () -> actor);
    }

    @Test
    void muestraHasta7_porNombre_yCuantosHay() throws Exception {
        for (String name : List.of("Ana", "Bruno", "Carla", "Daniel", "Elena", "Fabián", "Gina", "Hugo", "Iván")) {
            backend.members.add(name + " García", UiFakes.TODAY.minusDays(10));
        }
        backend.members.add("Luis Mora", UiFakes.TODAY.minusDays(10));
        QuickSearchViewModel model = model(caja);

        onUi(() -> {
            model.textProperty().set("garcia");
            return null;
        });
        waitUntil(() -> !model.searchingProperty().get() && !model.results().isEmpty());

        assertEquals(List.of("Ana García", "Bruno García", "Carla García", "Daniel García", "Elena García",
                "Fabián García", "Gina García"), onUi(() -> model.snapshot().stream().map(MemberRow::fullName).toList()));
        assertEquals(9, onUi(() -> model.matchCountProperty().get()));
        assertEquals("7 de 9 socios", QuickSearchViewModel.countText(7, 9));
        assertTrue(onUi(() -> model.openProperty().get()));

        onUi(() -> {
            model.clear();
            return null;
        });
        assertFalse(onUi(() -> model.openProperty().get()));
        assertTrue(onUi(() -> model.results().isEmpty()));
    }

    @Test
    void textos_deCuantos() {
        assertEquals("1 socio", QuickSearchViewModel.countText(1, 1));
        assertEquals("3 socios", QuickSearchViewModel.countText(3, 3));
        assertEquals("0 socios", QuickSearchViewModel.countText(0, 0));
    }

    @Test
    void sinPermiso_quedaElError() throws Exception {
        User inactivo = users.add("Pedro Salas", "pedro", Role.CAJA, false);
        QuickSearchViewModel model = model(inactivo);
        onUi(() -> {
            model.textProperty().set("ana");
            return null;
        });
        waitUntil(() -> !model.searchingProperty().get() && model.errorProperty().get() != null);
        assertNotNull(onUi(() -> model.errorProperty().get()));
        assertTrue(onUi(() -> model.results().isEmpty()));
    }
}
