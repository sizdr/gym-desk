package dev.gimnasio.sistemagimnasio.ui.billing;

import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargePreview;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PlanOption;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChargeViewModelTest {
    private static final LocalDate TODAY = UiFakes.TODAY;
    private final UiFakes.Users users = new UiFakes.Users();
    private final User caja = users.add("Carlos Caja", "carlos", Role.CAJA, true);
    private final UiFakes.Backend backend = new UiFakes.Backend(users);
    private final List<String> ok = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);
    private final ChargeViewModel model = new ChargeViewModel(backend.useCases(), background, () -> caja,
            new Notifier() {
                @Override
                public void ok(String message) {
                    ok.add(message);
                }

                @Override
                public void error(String message) {
                    errors.add(message);
                }
            });
    private long mensual;
    private Member nuevo;
    private Member vigente;

    @BeforeEach
    void seed() throws Exception {
        backend.methods.add("Efectivo", true, true);
        backend.methods.add("Tarjeta", false, true);
        mensual = backend.plans.add("Mensual", 30, "450", null, true).id();
        backend.plans.add("Inactivo", 30, "300", null, false);
        nuevo = backend.members.add("Fernando Gil", TODAY.minusDays(3));
        vigente = backend.members.add("Bruno Díaz", TODAY.minusDays(100));
        backend.memberships.add(vigente.id(), mensual, TODAY.minusDays(25), TODAY.plusDays(5));
        onUi(model::load);
        waitUntil(() -> model.stateProperty().get() != ChargeViewModel.State.LOADING);
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

    private MemberRow find(String text) throws Exception {
        onUi(() -> model.memberQueryProperty().set(text));
        waitUntil(() -> !model.memberResults().isEmpty());
        return onUi(() -> model.memberResults().getFirst());
    }

    private PaymentMethodItem method(String name) throws Exception {
        return onUi(() -> model.methods().stream().filter(m -> m.name().equals(name)).findFirst().orElseThrow());
    }

    private PlanOption plan() throws Exception {
        return onUi(() -> model.plans().getFirst());
    }

    private ChargePreview previewWhen(BooleanSupplier ready) throws Exception {
        waitUntil(ready);
        return onUi(() -> model.previewProperty().get());
    }

    @Test
    void soloOfreceLoActivo() throws Exception {
        assertEquals(List.of("Mensual"), onUi(() -> model.planSnapshot().stream().map(PlanOption::name).toList()));
        assertEquals(2, onUi(() -> model.methods().size()));
    }

    @Test
    void primeraMembresia_enEfectivo_conCambio_yRecibo() throws Exception {
        MemberRow fernando = find("fernando");
        onUi(() -> {
            model.chooseMember(fernando);
            model.planProperty().set(model.plans().getFirst());
        });
        PaymentMethodItem efectivo = method("Efectivo");
        onUi(() -> {
            model.methodProperty().set(efectivo);
            model.cashTextProperty().set("500");
        });
        ChargePreview preview = previewWhen(() -> model.previewProperty().get() != null
                && model.previewProperty().get().ready());
        assertEquals(ChargeKind.PRIMERA, preview.kind());
        assertEquals(TODAY, preview.newStart());
        assertEquals(new BigDecimal("50.00"), preview.change().amount());
        assertEquals("Primera membresía: empieza el 10/11/2026.", ChargeViewModel.explain(preview.kind(), preview.newStart()));

        onUi(model::charge);
        onUi(model::charge);
        waitUntil(() -> model.resultProperty().get() != null);
        assertEquals("GYM-0001", model.resultProperty().get().payment().folio());
        assertEquals(1, backend.payments.stored(), "el segundo clic no cobra otra vez");
        assertEquals(List.of("Cobro registrado con el folio GYM-0001."), ok);

        onUi(model::printReceipt);
        waitUntil(() -> !backend.printer.printed.isEmpty() && !model.printingProperty().get());
        assertEquals("GYM-0001", backend.printer.printed.getFirst().payment().folio());
    }

    @Test
    void renovacionAnticipada_empiezaAlTerminarLaVigencia() throws Exception {
        MemberRow bruno = find("bruno");
        PaymentMethodItem tarjeta = method("Tarjeta");
        PlanOption mensualOption = plan();
        onUi(() -> {
            model.chooseMember(bruno);
            model.planProperty().set(mensualOption);
            model.methodProperty().set(tarjeta);
        });
        ChargePreview preview = previewWhen(() -> model.previewProperty().get() != null
                && model.previewProperty().get().ready());
        assertEquals(ChargeKind.RENOVACION_ANTICIPADA, preview.kind());
        assertEquals(TODAY.plusDays(5), preview.newStart());
        assertEquals(TODAY.plusDays(35), preview.newEnd());
    }

    @Test
    void mientrasFaltaAlgo_noSePuedeCobrar_yDiceQue() throws Exception {
        MemberRow fernando = find("fernando");
        PaymentMethodItem efectivo = method("Efectivo");
        PlanOption mensualOption = plan();
        onUi(() -> {
            model.chooseMember(fernando);
            model.planProperty().set(mensualOption);
            model.methodProperty().set(efectivo);
            model.cashTextProperty().set("100");
        });
        ChargePreview preview = previewWhen(() -> model.previewProperty().get() != null
                && model.previewProperty().get().missing() != null);
        assertFalse(preview.ready());
        assertEquals(new BigDecimal("350.00"), preview.missing().amount());
        assertEquals("El efectivo recibido no cubre el total.", ChargeViewModel.missing(preview));

        onUi(model::charge);
        Thread.sleep(50);
        assertEquals(0, backend.payments.stored());
    }

    @Test
    void nuevoCobro_limpiaElFormulario_conOtraOperacion() throws Exception {
        MemberRow fernando = find("fernando");
        PaymentMethodItem tarjeta = method("Tarjeta");
        PlanOption mensualOption = plan();
        onUi(() -> {
            model.chooseMember(fernando);
            model.planProperty().set(mensualOption);
            model.methodProperty().set(tarjeta);
        });
        previewWhen(() -> model.previewProperty().get() != null && model.previewProperty().get().ready());
        String first = onUi(model::operationId);
        onUi(model::charge);
        waitUntil(() -> model.resultProperty().get() != null);

        onUi(model::newCharge);

        assertNull(model.resultProperty().get());
        assertNull(model.memberProperty().get());
        assertNull(model.planProperty().get());
        assertNotEquals(first, onUi(model::operationId));
        assertTrue(model.fieldErrorsProperty().get().isEmpty());
    }

    @Test
    void laBusquedaDeSocios_seLimpiaAlElegir() throws Exception {
        MemberRow fernando = find("gil");
        onUi(() -> model.chooseMember(fernando));
        assertEquals("", model.memberQueryProperty().get());
        waitUntil(() -> model.memberResults().isEmpty());
        assertEquals(nuevo.id(), model.memberProperty().get().id());
        assertTrue(errors.isEmpty());
    }
}
