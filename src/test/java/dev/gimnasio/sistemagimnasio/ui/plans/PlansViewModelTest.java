package dev.gimnasio.sistemagimnasio.ui.plans;

import dev.gimnasio.sistemagimnasio.application.usecase.plans.PlanRow;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.SavingsView;
import dev.gimnasio.sistemagimnasio.domain.member.SavingsStatus;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlansViewModelTest {
    private final UiFakes.Users users = new UiFakes.Users();
    private final User admin = users.add("Administración", "admin", Role.ADMINISTRADOR, true);
    private final UiFakes.Backend backend = new UiFakes.Backend(users);
    private final List<String> ok = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);
    private final PlansViewModel model = new PlansViewModel(backend.useCases(), background, () -> admin,
            new Notifier() {
                @Override
                public void ok(String message) {
                    ok.add(message);
                }

                @Override
                public void error(String message) {
                    errors.add(message);
                }
            }, Currency.getInstance("MXN"));
    private long mensual;
    private long anual;

    @BeforeEach
    void seed() throws Exception {
        mensual = backend.plans.add("Mensual", 30, "450", null, true).id();
        anual = backend.plans.add("Anual", 365, "4200", mensual, true).id();
        backend.plans.add("Visita", 1, "60", null, false);
        backend.plans.add("Quincena", 15, "250", null, true);
        onUi(model::load);
        waitUntil(() -> model.stateProperty().get() == PlansViewModel.State.READY);
    }

    @AfterEach
    void stop() {
        background.close();
        uiThread.shutdownNow();
    }

    private <T> T onUi(Callable<T> work) throws Exception {
        return uiThread.submit(work).get(2, TimeUnit.SECONDS);
    }

    private void onUi(Runnable work) throws Exception {
        uiThread.submit(work).get(2, TimeUnit.SECONDS);
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

    private PlanRow row(String name) throws Exception {
        return onUi(() -> model.items().stream().filter(p -> p.name().equals(name)).findFirst().orElseThrow());
    }

    private List<String> names() throws Exception {
        return onUi(() -> model.items().stream().map(PlanRow::name).toList());
    }

    @Test
    void cargaActivosPrimero_conSuAhorro_yElResumen() throws Exception {
        assertEquals(List.of("Quincena", "Mensual", "Anual", "Visita"), names());
        PlanRow plan = row("Anual");
        assertEquals("365 días · se compara con Mensual", PlansViewModel.detail(plan));
        assertEquals("$1,200.00 (22%)", model.savingsShort(plan.savings()));
        assertEquals("—", model.savingsShort(row("Mensual").savings()));
        assertEquals("4 planes · 3 activos", PlansViewModel.summary(model.snapshot()));
    }

    @Test
    void laReferencia_ofreceSoloPlanesDe30Dias_yNoElMismo() throws Exception {
        PlanRow anualRow = row("Anual");
        onUi(() -> model.startEdit(anualRow));
        assertEquals(List.of("Sin referencia", "Mensual · $450.00"),
                onUi(() -> model.referenceOptions().stream().map(PlansViewModel.ReferenceOption::label).toList()));
        assertEquals(mensual, onUi(() -> model.editorReferenceProperty().get().id()));

        PlanRow mensualRow = row("Mensual");
        onUi(model::cancelEditor);
        onUi(() -> model.startEdit(mensualRow));
        assertEquals(List.of("Sin referencia"),
                onUi(() -> model.referenceOptions().stream().map(PlansViewModel.ReferenceOption::label).toList()),
                "un plan no puede ser su propia referencia");
    }

    @Test
    void elAhorroSeVeMientrasSeEscribe() throws Exception {
        onUi(model::startCreate);
        assertEquals("Escribe la duración y el precio para ver el ahorro.",
                model.savingsLong(onUi(() -> model.editorSavingsProperty().get())), "vacío no dice «no aplica»");
        onUi(() -> {
            model.editorNameProperty().set("Trimestral");
            model.editorDurationProperty().set("90");
            model.editorPriceProperty().set("1200");
            model.editorReferenceProperty().set(model.referenceOptions().get(1));
        });
        waitUntil(() -> model.editorSavingsProperty().get() != null
                && model.editorSavingsProperty().get().status() == SavingsStatus.AHORRO);
        SavingsView savings = onUi(() -> model.editorSavingsProperty().get());
        assertEquals("Ahorra $150.00 frente a 3 meses de «Mensual» ($1,350.00): 11% menos.",
                model.savingsLong(savings));

        onUi(() -> model.editorPriceProperty().set("1500"));
        waitUntil(() -> model.editorSavingsProperty().get().status() == SavingsStatus.SIN_AHORRO);
        assertEquals("Sin ahorro: 3 meses de «Mensual» cuestan $1,350.00, lo mismo o menos que este plan.",
                model.savingsLong(onUi(() -> model.editorSavingsProperty().get())));

        onUi(() -> model.editorReferenceProperty().set(PlansViewModel.ReferenceOption.NONE));
        waitUntil(() -> model.editorSavingsProperty().get().status() == SavingsStatus.NO_DISPONIBLE);
        assertEquals("No se puede calcular el ahorro: el plan no tiene un plan mensual de referencia.",
                model.savingsLong(onUi(() -> model.editorSavingsProperty().get())));

        onUi(() -> model.editorDurationProperty().set("30"));
        waitUntil(() -> model.editorSavingsProperty().get().status() == SavingsStatus.NO_APLICA);
    }

    @Test
    void agregar_conErroresEnSusCampos_yLuegoBien() throws Exception {
        onUi(model::startCreate);
        onUi(() -> {
            model.editorNameProperty().set("mensual");
            model.editorDurationProperty().set("0");
            model.editorPriceProperty().set("abc");
        });
        onUi(model::submitEditor);
        waitUntil(() -> !model.editorFieldErrorsProperty().get().isEmpty());
        Map<String, String> fieldErrors = onUi(() -> model.editorFieldErrorsProperty().get());
        assertEquals(List.of("name", "durationDays", "price"), List.copyOf(fieldErrors.keySet()));
        assertTrue(onUi(() -> model.editorOpenProperty().get()), "con errores el diálogo sigue abierto");

        onUi(() -> model.editorDurationProperty().set("90"));
        assertFalse(onUi(() -> model.editorFieldErrorsProperty().get().containsKey("durationDays")),
                "corregir un campo quita su error");

        onUi(() -> {
            model.editorNameProperty().set("Trimestral");
            model.editorPriceProperty().set("1,200.00");
        });
        onUi(model::submitEditor);
        onUi(model::submitEditor);
        waitUntil(() -> !model.editorOpenProperty().get() && model.items().size() == 5);
        assertEquals(List.of("Se agregó el plan «Trimestral». Ya se ofrece al cobrar."), ok);
        assertEquals(new BigDecimal("1200.00"), row("Trimestral").price().amount());
        assertEquals(5, backend.plans.findAll().size(), "el segundo envío no agrega otro");
    }

    @Test
    void cambiarElPrecio_avisaQueAplicaALosCobrosNuevos() throws Exception {
        PlanRow mensualRow = row("Mensual");
        onUi(() -> model.startEdit(mensualRow));
        onUi(() -> model.editorPriceProperty().set("480"));
        onUi(model::submitEditor);
        waitUntil(() -> !model.editorOpenProperty().get() && !ok.isEmpty());
        assertEquals("Se guardaron los cambios de «Mensual». El precio nuevo aplica a los cobros nuevos; los "
                + "anteriores conservan lo pagado.", ok.getFirst());
        waitUntil(() -> "$1,560.00 (27%)".equals(model.savingsShort(
                model.items().stream().filter(p -> p.id() == anual).findFirst().orElseThrow().savings())));
    }

    @Test
    void desactivarLaReferencia_cambiaElAhorroDeLosDemas() throws Exception {
        PlanRow mensualRow = row("Mensual");
        onUi(() -> model.setActive(mensualRow, false));
        waitUntil(() -> !model.busyProperty().get() && "No disponible".equals(model.savingsShort(
                model.items().stream().filter(p -> p.id() == anual).findFirst().orElseThrow().savings())));
        assertEquals(List.of("«Mensual» ya no se ofrece al cobrar. Sus membresías y cobros se conservan."), ok);
        assertFalse(row("Mensual").active());
    }

    @Test
    void eliminar_unoUsadoSeRechazaConElMensaje_yUnoLibreSeBorra() throws Exception {
        backend.plans.giveMembership(mensual);
        PlanRow mensualRow = row("Mensual");
        onUi(() -> model.delete(mensualRow));
        waitUntil(() -> !errors.isEmpty());
        assertEquals("No puedes eliminar «Mensual»: ya tiene membresías. Desactívalo para dejar de ofrecerlo.",
                errors.getFirst());

        PlanRow visita = row("Visita");
        onUi(() -> model.delete(visita));
        waitUntil(() -> !ok.isEmpty() && model.items().size() == 3);
        assertEquals("Se eliminó el plan «Visita».", ok.getFirst());
    }
}
