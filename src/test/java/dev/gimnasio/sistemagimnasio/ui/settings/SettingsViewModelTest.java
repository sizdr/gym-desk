package dev.gimnasio.sistemagimnasio.ui.settings;

import dev.gimnasio.sistemagimnasio.application.usecase.settings.SettingsView;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettingsViewModelTest {
    private static final Currency MXN = Currency.getInstance("MXN");
    private static final Currency USD = Currency.getInstance("USD");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0};

    private final UiFakes.Users users = new UiFakes.Users();
    private final User admin = users.add("Ana Admin", "ana", Role.ADMINISTRADOR, true);
    private final UiFakes.Backend backend = new UiFakes.Backend(users);
    private final List<SettingsView> savedViews = new ArrayList<>();
    /** El «hilo de JavaFX» de la prueba: un solo hilo, para que los callbacks no se mezclen con el trabajo. */
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);
    private User actor = admin;
    private final SettingsViewModel model = new SettingsViewModel(backend.useCases().getSettings(),
            backend.useCases().updateSettings(), background, () -> actor, savedViews::add);

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

    private void loadAndWait() throws Exception {
        onUi(model::load);
        waitUntil(() -> model.stateProperty().get() != SettingsViewModel.State.LOADING);
    }

    private void saveAndWait() throws Exception {
        onUi(model::save);
        waitUntil(() -> !model.savingProperty().get());
    }

    @Test
    void alCargar_muestraLoGuardado_sinCambiosPendientes() throws Exception {
        backend.settings.settings = new GymSettings("Gimnasio Fuerza", "Av. Juárez 10", "555 010 2233", null,
                "¡Gracias por entrenar!", MXN, 5, 45);

        loadAndWait();

        assertEquals(SettingsViewModel.State.READY, model.stateProperty().get());
        assertEquals("Gimnasio Fuerza", model.gymNameProperty().get());
        assertEquals("Av. Juárez 10", model.addressProperty().get());
        assertEquals("555 010 2233", model.phoneProperty().get());
        assertEquals("¡Gracias por entrenar!", model.receiptFooterProperty().get());
        assertEquals("5", model.warningDaysProperty().get());
        assertEquals("45", model.autoDeactivationDaysProperty().get());
        assertEquals(MXN, model.currencyProperty().get());
        assertEquals("GYM-", model.folioPrefixProperty().get());
        assertFalse(onUi(model::hasUnsavedChanges));
    }

    @Test
    void opcionalesVacios_seMuestranComoTextoVacio() throws Exception {
        loadAndWait();
        assertEquals("", model.addressProperty().get());
        assertEquals("", model.phoneProperty().get());
        assertEquals("", model.receiptFooterProperty().get());
        assertNull(model.logoProperty().get());
    }

    @Test
    void escribir_marcaCambios_yVolverAlValorOriginal_losQuita() throws Exception {
        loadAndWait();

        onUi(() -> model.gymNameProperty().set("Gimnasio Fuerza"));
        assertTrue(onUi(model::hasUnsavedChanges));

        onUi(() -> model.gymNameProperty().set("Mi gimnasio"));
        assertFalse(onUi(model::hasUnsavedChanges));
    }

    @Test
    void descartar_vuelveALoGuardado() throws Exception {
        loadAndWait();
        onUi(() -> {
            model.gymNameProperty().set("Otro nombre");
            model.warningDaysProperty().set("12");
            model.useLogo(PNG);
        });

        onUi(model::discard);

        assertEquals("Mi gimnasio", model.gymNameProperty().get());
        assertEquals("7", model.warningDaysProperty().get());
        assertNull(model.logoProperty().get());
        assertFalse(onUi(model::hasUnsavedChanges));
    }

    @Test
    void guardar_enviaLoEscrito_yQuedaComoLoGuardado() throws Exception {
        loadAndWait();
        onUi(() -> {
            model.gymNameProperty().set("  Gimnasio Fuerza ");
            model.addressProperty().set("Av. Juárez 10");
            model.warningDaysProperty().set("10");
            model.autoDeactivationDaysProperty().set("60");
            model.folioPrefixProperty().set("FZA-");
            model.currencyProperty().set(USD);
            model.useLogo(PNG);
        });

        saveAndWait();

        GymSettings stored = backend.settings.settings;
        assertEquals("Gimnasio Fuerza", stored.gymName(), "el caso de uso recorta");
        assertEquals("Av. Juárez 10", stored.address());
        assertEquals(10, stored.warningDays());
        assertEquals(60, stored.autoDeactivationDays());
        assertEquals(USD, stored.currency());
        assertArrayEquals(PNG, stored.logo());
        assertEquals("FZA-", backend.folios.get(FolioSeries.MEMBERSHIP_PAYMENTS).prefix());
        assertEquals("Gimnasio Fuerza", model.gymNameProperty().get(), "se muestra lo que quedó guardado");
        assertFalse(onUi(model::hasUnsavedChanges));
        assertEquals(1, savedViews.size());
        assertEquals("FZA-0001", savedViews.getFirst().folioExample());
    }

    /** La pantalla escucha el logotipo para decir si es el actual o uno nuevo sin guardar. */
    @Test
    void alGuardar_quienEscuchaElLogotipo_yaLoVeComoGuardado() throws Exception {
        loadAndWait();
        List<Boolean> changedWhenNotified = new ArrayList<>();
        onUi(() -> model.logoProperty().addListener((obs, old, logo) -> changedWhenNotified.add(model.logoChanged())));
        onUi(() -> model.useLogo(PNG));
        saveAndWait();
        assertEquals(List.of(true, false), changedWhenNotified);
    }

    @Test
    void sinCambios_guardarNoHaceNada() throws Exception {
        loadAndWait();
        saveAndWait();
        assertEquals(0, backend.settings.saves);
        assertTrue(savedViews.isEmpty());
    }

    @Test
    void erroresDeCampo_llegan_enElOrdenDelCasoDeUso_yNoSeGuarda() throws Exception {
        loadAndWait();
        onUi(() -> {
            model.gymNameProperty().set("   ");
            model.warningDaysProperty().set("");
            model.autoDeactivationDaysProperty().set("400");
            model.folioPrefixProperty().set("gym 1");
        });

        saveAndWait();

        Map<String, String> errors = model.fieldErrorsProperty().get();
        assertEquals(List.of("gymName", "warningDays", "autoDeactivationDays", "folioPrefix"),
                List.copyOf(errors.keySet()));
        assertEquals("Escribe el nombre del gimnasio.", errors.get("gymName"));
        assertEquals("Escribe un número de días entre 1 y 60.", errors.get("warningDays"));
        assertEquals(0, backend.settings.saves);
        assertTrue(onUi(model::hasUnsavedChanges), "lo escrito se conserva para corregirlo");
        assertNull(model.generalErrorProperty().get());
    }

    @Test
    void textoQueNoEsNumero_seReportaComoCampoVacio() throws Exception {
        loadAndWait();
        onUi(() -> model.warningDaysProperty().set("siete"));
        saveAndWait();
        assertEquals("Escribe un número de días entre 1 y 60.", model.fieldErrorsProperty().get().get("warningDays"));
    }

    @Test
    void conCobrosOVentas_laMonedaQuedaBloqueada_yLaAyudaDiceCuantos() throws Exception {
        backend.payments.count = 41;
        backend.sales.count = 18;

        loadAndWait();

        assertTrue(model.currencyLockedProperty().get());
        assertEquals("Bloqueada: ya hay 41 cobros y 18 ventas registrados en MXN. Cambiarla alteraría el "
                + "significado de esos importes.", model.currencyHelpProperty().get());
    }

    @Test
    void sinCobrosNiVentas_laMonedaSePuedeElegir() throws Exception {
        loadAndWait();
        assertFalse(model.currencyLockedProperty().get());
        assertEquals("Solo se puede elegir mientras no haya cobros ni ventas.", model.currencyHelpProperty().get());
        assertEquals(List.of(MXN, USD), model.currenciesProperty().get());
    }

    @Test
    void unaMonedaGuardadaQueNoSeOfrece_tambienAparece() throws Exception {
        Currency eur = Currency.getInstance("EUR");
        backend.settings.settings = new GymSettings("Mi gimnasio", null, null, null, null, eur, 7, 30);
        loadAndWait();
        assertEquals(List.of(MXN, USD, eur), model.currenciesProperty().get());
        assertEquals("EUR", SettingsViewModel.currencyLabel(eur));
        assertEquals("MXN · Peso mexicano", SettingsViewModel.currencyLabel(MXN));
    }

    @Test
    void conteosEnSingularYPlural() {
        assertEquals("1 cobro", SettingsViewModel.recorded(1, 0));
        assertEquals("3 ventas", SettingsViewModel.recorded(0, 3));
        assertEquals("2 cobros y 1 venta", SettingsViewModel.recorded(2, 1));
    }

    @Test
    void elFolioDeEjemplo_usaElPrefijoEscrito_conElAnchoDelSistema() throws Exception {
        backend.folios.put(new FolioSeries(FolioSeries.MEMBERSHIP_PAYMENTS, "GYM-", 6));
        loadAndWait();
        assertEquals("GYM-000001", model.folioExampleProperty().get());

        onUi(() -> model.folioPrefixProperty().set("FZA"));
        assertEquals("FZA000001", model.folioExampleProperty().get());

        onUi(() -> model.folioPrefixProperty().set(""));
        assertEquals("", model.folioExampleProperty().get());
    }

    @Test
    void quitarElLogotipo_seGuardaSinLogotipo() throws Exception {
        backend.settings.settings = new GymSettings("Mi gimnasio", null, null, PNG, null, MXN, 7, 30);
        loadAndWait();
        assertFalse(onUi(model::logoChanged));

        onUi(model::removeLogo);
        assertTrue(onUi(model::logoChanged));
        saveAndWait();

        assertNull(backend.settings.settings.logo());
    }

    @Test
    void unLogotipoQueNoEsPngNiJpg_loRechazaElCasoDeUsoEnSuCampo() throws Exception {
        loadAndWait();
        onUi(() -> model.useLogo("GIF89a".getBytes()));
        saveAndWait();
        assertEquals(List.of("logo"), List.copyOf(model.fieldErrorsProperty().get().keySet()));
        assertEquals(0, backend.settings.saves);
    }

    @Test
    void elegirOtraImagen_quitaElErrorDelLogotipo() throws Exception {
        loadAndWait();
        onUi(model::logoUnreadable);
        assertTrue(model.fieldErrorsProperty().get().containsKey("logo"));

        onUi(() -> model.useLogo(PNG));
        assertFalse(model.fieldErrorsProperty().get().containsKey("logo"));
    }

    @Test
    void sinPermiso_elAvisoGeneralLoDice() throws Exception {
        loadAndWait();
        actor = users.add("Carlos Caja", "carlos", Role.CAJA, true);
        onUi(() -> model.gymNameProperty().set("Otro"));

        saveAndWait();

        assertEquals("Tu rol (Caja) no tiene permiso para esta acción.", model.generalErrorProperty().get());
        assertTrue(model.fieldErrorsProperty().get().isEmpty());
        assertEquals(0, backend.settings.saves);
    }

    @Test
    void siFallaLaCarga_seMuestraElMotivoParaReintentar() throws Exception {
        actor = users.add("Carlos Caja", "carlos", Role.CAJA, true);
        loadAndWait();
        assertEquals(SettingsViewModel.State.LOAD_FAILED, model.stateProperty().get());
        assertEquals("Tu rol (Caja) no tiene permiso para esta acción.", model.loadErrorProperty().get());
        assertFalse(onUi(model::hasUnsavedChanges));

        actor = admin;
        loadAndWait();
        assertEquals(SettingsViewModel.State.READY, model.stateProperty().get());
        assertNull(model.loadErrorProperty().get());
    }

    @Test
    void unSegundoGuardar_mientrasElPrimeroSigue_seIgnora() throws Exception {
        loadAndWait();
        onUi(() -> model.gymNameProperty().set("Otro"));
        // La revisión del permiso relee al actor: se detiene ahí para simular una base lenta.
        CountDownLatch gate = new CountDownLatch(1);
        users.idGate = gate;

        onUi(model::save);
        onUi(model::save);
        onUi(model::save);
        assertTrue(onUi(() -> model.savingProperty().get()));
        gate.countDown();
        waitUntil(() -> !model.savingProperty().get());

        assertEquals(1, backend.settings.saves);
        assertEquals(1, savedViews.size());
    }
}
