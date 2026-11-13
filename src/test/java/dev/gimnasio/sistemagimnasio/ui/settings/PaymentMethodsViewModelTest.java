package dev.gimnasio.sistemagimnasio.ui.settings;

import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaymentMethodsViewModelTest {
    private final UiFakes.Users users = new UiFakes.Users();
    private final User admin = users.add("Ana Admin", "ana", Role.ADMINISTRADOR, true);
    private final UiFakes.Backend backend = new UiFakes.Backend(users);
    private final List<String> ok = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);
    private User actor = admin;
    private final PaymentMethodsViewModel model = new PaymentMethodsViewModel(backend.useCases(), background,
            () -> actor, new Notifier() {
        @Override
        public void ok(String message) {
            ok.add(message);
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }
    });
    private long cash;
    private long card;
    private long transfer;

    @BeforeEach
    void seed() {
        cash = backend.methods.add("Efectivo", true, true).id();
        card = backend.methods.add("Tarjeta", false, true).id();
        transfer = backend.methods.add("Transferencia", false, false).id();
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

    /** Espera a que termine lo que está en curso y a que la lista refleje el último cambio. */
    private void settle() throws Exception {
        waitUntil(() -> !model.busyProperty().get() && !model.editorSavingProperty().get());
        Thread.sleep(30);
        onUi(() -> { });
    }

    private void loadAndWait() throws Exception {
        onUi(model::load);
        waitUntil(() -> model.stateProperty().get() != PaymentMethodsViewModel.State.LOADING);
    }

    private List<String> names() throws Exception {
        return onUi(() -> model.snapshot().stream().map(PaymentMethodItem::name).toList());
    }

    private PaymentMethodItem item(long id) throws Exception {
        return onUi(() -> model.snapshot().stream().filter(i -> i.id() == id).findFirst().orElseThrow());
    }

    private void submit(String name, boolean isCash) throws Exception {
        onUi(() -> {
            model.editorNameProperty().set(name);
            model.editorCashProperty().set(isCash);
            model.submitEditor();
        });
        settle();
    }

    @Test
    void cargaActivosEInactivos_porNombre() throws Exception {
        loadAndWait();
        assertEquals(PaymentMethodsViewModel.State.READY, model.stateProperty().get());
        assertEquals(List.of("Efectivo", "Tarjeta", "Transferencia"), names());
        assertFalse(item(transfer).active());
    }

    /** La pantalla escucha {@code items()}: tiene que seguir enterándose aunque pase el recolector de basura. */
    @Test
    void quienEscuchaLaLista_sigueRecibiendoLosCambios_despuesDeRecolectarBasura() throws Exception {
        loadAndWait();
        List<Integer> sizes = new ArrayList<>();
        onUi(() -> model.items().addListener((javafx.collections.ListChangeListener<PaymentMethodItem>)
                change -> sizes.add(change.getList().size())));
        for (int i = 0; i < 3; i++) {
            System.gc();
            Thread.sleep(20);
        }
        onUi(() -> model.delete(model.snapshot().get(2)));
        settle();
        assertEquals(List.of(2), sizes);
        assertTrue(model.items() == model.items(), "siempre la misma vista");
    }

    @Test
    void agregar_cierraElFormulario_avisa_yLoMuestraEnSuLugar() throws Exception {
        loadAndWait();
        onUi(model::startCreate);
        assertTrue(model.editorOpenProperty().get());
        assertNull(model.editing());

        submit("  Vales de despensa ", false);

        assertFalse(model.editorOpenProperty().get());
        waitUntil(() -> model.snapshot().size() == 4);
        assertEquals(List.of("Efectivo", "Tarjeta", "Transferencia", "Vales de despensa"), names());
        assertEquals(List.of("Se agregó «Vales de despensa». Ya se ofrece al cobrar y al vender."), ok);
    }

    @Test
    void nombreRepetidoOVacio_quedaEnElCampo_yElFormularioSigueAbierto() throws Exception {
        loadAndWait();
        onUi(model::startCreate);

        submit("tarjeta", false);
        assertEquals(Map.of("name", "Ya existe uno con ese nombre."), model.editorFieldErrorsProperty().get());
        assertTrue(model.editorOpenProperty().get());

        submit("  ", false);
        assertEquals(Map.of("name", "Escribe el nombre."), model.editorFieldErrorsProperty().get());
        assertEquals(3, backend.methods.findAll(true).size());
        assertTrue(ok.isEmpty());
    }

    @Test
    void editar_cargaSusDatos_yGuarda() throws Exception {
        loadAndWait();
        onUi(() -> model.startEdit(model.snapshot().get(1)));
        assertEquals("Tarjeta", model.editorNameProperty().get());
        assertFalse(model.editorCashProperty().get());

        submit("Tarjeta de débito o crédito", false);

        assertFalse(model.editorOpenProperty().get());
        waitUntil(() -> model.snapshot().stream().anyMatch(i -> i.name().startsWith("Tarjeta de")));
        assertEquals("Tarjeta de débito o crédito", backend.methods.get(card).name());
        assertEquals(List.of("Se guardaron los cambios de «Tarjeta de débito o crédito»."), ok);
    }

    @Test
    void cambiarSiEsEfectivo_unoYaUsado_seRechazaEnSuCampo() throws Exception {
        backend.methods.markUsed(cash);
        loadAndWait();
        onUi(() -> model.startEdit(model.snapshot().getFirst()));

        submit("Efectivo", false);

        assertEquals(List.of("cash"), List.copyOf(model.editorFieldErrorsProperty().get().keySet()));
        assertTrue(model.editorFieldErrorsProperty().get().get("cash").startsWith("No puedes cambiar si «Efectivo» es efectivo"));
        assertTrue(backend.methods.get(cash).cash());
    }

    @Test
    void cancelar_cierraSinGuardar() throws Exception {
        loadAndWait();
        onUi(() -> model.startEdit(model.snapshot().getFirst()));
        onUi(() -> model.editorNameProperty().set("Otro"));
        onUi(model::cancelEditor);
        assertFalse(model.editorOpenProperty().get());
        assertEquals("Efectivo", backend.methods.get(cash).name());
        assertEquals(0, backend.methods.writes);
    }

    @Test
    void desactivarYActivar_cambianLaFila_yAvisan() throws Exception {
        loadAndWait();
        onUi(() -> model.setActive(model.snapshot().get(1), false));
        settle();
        assertFalse(item(card).active());
        assertEquals("«Tarjeta» ya no se ofrece al cobrar ni al vender. Los cobros y ventas anteriores no cambian.", ok.getLast());

        onUi(() -> model.setActive(model.snapshot().get(2), true));
        settle();
        assertTrue(item(transfer).active());
        assertEquals("«Transferencia» se ofrece de nuevo al cobrar y al vender.", ok.getLast());
    }

    @Test
    void desactivarElUltimoActivo_avisaConElMensajeDelCasoDeUso() throws Exception {
        backend.methods.add("Vales", false, false);
        loadAndWait();
        onUi(() -> model.setActive(model.snapshot().get(1), false));
        settle();
        onUi(() -> model.setActive(model.snapshot().getFirst(), false));
        settle();

        assertEquals(List.of("Deja al menos un método de pago."), errors);
        assertTrue(item(cash).active());
    }

    @Test
    void eliminarUnoSinUso_loQuitaDeLaLista() throws Exception {
        loadAndWait();
        onUi(() -> model.delete(model.snapshot().get(2)));
        settle();
        assertEquals(List.of("Efectivo", "Tarjeta"), names());
        assertEquals(List.of("Se eliminó «Transferencia»."), ok);
    }

    @Test
    void eliminarUnoYaUsado_seRechaza_ySugiereDesactivarlo() throws Exception {
        backend.methods.markUsed(card);
        loadAndWait();
        onUi(() -> model.delete(model.snapshot().get(1)));
        settle();
        assertEquals(List.of("No puedes eliminar «Tarjeta»: ya se usó en cobros o ventas. Desactívalo para dejar de "
                + "ofrecerlo."), errors);
        assertEquals(List.of("Efectivo", "Tarjeta", "Transferencia"), names());
    }

    @Test
    void siOtroUsuarioLoBorro_seAvisa_ySeRecargaLaLista() throws Exception {
        loadAndWait();
        backend.methods.delete(transfer);

        onUi(() -> model.setActive(model.snapshot().get(2), true));
        settle();

        assertEquals(List.of("El método de pago ya no existe. Recarga la pantalla."), errors);
        waitUntil(() -> model.snapshot().size() == 2);
    }

    @Test
    void editarUnoQueOtroBorro_cierraElFormulario_yRecarga() throws Exception {
        loadAndWait();
        onUi(() -> model.startEdit(model.snapshot().get(2)));
        backend.methods.delete(transfer);

        submit("Transferencia SPEI", false);

        assertFalse(model.editorOpenProperty().get());
        assertEquals(List.of("El método de pago ya no existe. Recarga la pantalla."), errors);
        waitUntil(() -> model.snapshot().size() == 2);
    }

    @Test
    void sinPermiso_elFormularioMuestraElAvisoGeneral() throws Exception {
        loadAndWait();
        actor = users.add("Carlos Caja", "carlos", Role.CAJA, true);
        onUi(model::startCreate);
        submit("Vales", false);
        assertEquals("Tu rol (Caja) no tiene permiso para esta acción.", model.editorGeneralErrorProperty().get());
        assertTrue(model.editorOpenProperty().get());
    }

    @Test
    void siFallaLaCarga_quedaElMotivo() throws Exception {
        actor = users.add("Carlos Caja", "carlos", Role.CAJA, true);
        loadAndWait();
        assertEquals(PaymentMethodsViewModel.State.LOAD_FAILED, model.stateProperty().get());
        assertEquals("Tu rol (Caja) no tiene permiso para esta acción.", model.loadErrorProperty().get());
    }

    @Test
    void mientrasUnaAccionSigue_lasDemasSeIgnoran() throws Exception {
        loadAndWait();
        CountDownLatch gate = new CountDownLatch(1);
        users.idGate = gate;
        onUi(() -> {
            model.setActive(model.snapshot().get(1), false);
            model.delete(model.snapshot().get(2));
            model.setActive(model.snapshot().get(2), true);
        });
        assertTrue(model.busyProperty().get());
        gate.countDown();
        users.idGate = null;
        settle();
        assertEquals(1, backend.methods.writes);
        assertFalse(item(card).active());
        assertEquals(List.of("Efectivo", "Tarjeta", "Transferencia"), names());
    }

    @Test
    void unSegundoEnvioDelFormulario_mientrasElPrimeroSigue_seIgnora() throws Exception {
        loadAndWait();
        onUi(model::startCreate);
        CountDownLatch gate = new CountDownLatch(1);
        users.idGate = gate;
        onUi(() -> {
            model.editorNameProperty().set("Vales");
            model.submitEditor();
            model.submitEditor();
            model.cancelEditor();
        });
        assertTrue(model.editorOpenProperty().get(), "mientras guarda no se puede cancelar");
        gate.countDown();
        users.idGate = null;
        settle();
        assertEquals(1, backend.methods.writes);
    }
}
