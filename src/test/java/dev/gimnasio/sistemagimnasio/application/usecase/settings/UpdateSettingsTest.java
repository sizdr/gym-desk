package dev.gimnasio.sistemagimnasio.application.usecase.settings;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryFolioSeries;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembershipPayments;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySales;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySettings;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UpdateSettingsTest {
    private static final Currency MXN = InMemorySettings.MXN;
    private static final Currency USD = Currency.getInstance("USD");
    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};

    private final InMemorySettings settings = new InMemorySettings();
    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryFolioSeries folioSeries = new InMemoryFolioSeries();
    private final InMemoryMembershipPayments payments = new InMemoryMembershipPayments();
    private final InMemorySales sales = new InMemorySales();
    private final ImmediateTransactions transactions = new ImmediateTransactions();
    private final UpdateSettings update = new UpdateSettings(settings, folioSeries, payments, sales, transactions,
            new Authorizer(users));
    private final User admin = users.admin();

    private static UpdateSettingsCommand valid() {
        return new UpdateSettingsCommand("Gym Fuerza", "Av. Juárez 10", "55 1234 5678", "¡Gracias!", PNG, MXN, 10, 45, "GF-");
    }

    private static UpdateSettingsCommand with(String gymName, String address, String phone, String receiptFooter,
                                              byte[] logo, Currency currency, Integer warningDays,
                                              Integer autoDeactivationDays, String folioPrefix) {
        return new UpdateSettingsCommand(gymName, address, phone, receiptFooter, logo, currency, warningDays,
                autoDeactivationDays, folioPrefix);
    }

    private String prefix() {
        return folioSeries.get(FolioSeries.MEMBERSHIP_PAYMENTS).prefix();
    }

    @Test
    void todoOcurreEnUnaSolaTransaccion() {
        for (var guard : List.of(settings.guard, folioSeries.guard, payments.guard, sales.guard, users.guard)) {
            guard.watch(transactions);
        }
        update.execute(admin, valid());
        assertEquals(1, transactions.started);
    }

    @Test
    void conErrores_noSeGuardaNiLaConfiguracionNiElPrefijo() {
        errors(with(" ", null, null, null, null, MXN, 7, 30, "GF-"));
        assertEquals(0, settings.saves);
        assertEquals("GYM-", prefix());
    }

    @Test
    void conservaElAnchoDeLaSerie_alCambiarElPrefijo() {
        folioSeries.put(new FolioSeries(FolioSeries.MEMBERSHIP_PAYMENTS, "GYM-", 6));
        assertEquals("GF-000001", update.execute(admin, valid()).folioExample());
        assertEquals(6, folioSeries.get(FolioSeries.MEMBERSHIP_PAYMENTS).width());
    }

    private Map<String, String> errors(UpdateSettingsCommand command) {
        return assertThrows(ValidationException.class, () -> update.execute(admin, command)).errors();
    }

    private static UpdateSettingsCommand logo(byte[] logo) {
        UpdateSettingsCommand v = valid();
        return with(v.gymName(), v.address(), v.phone(), v.receiptFooter(), logo, v.currency(), v.warningDays(),
                v.autoDeactivationDays(), v.folioPrefix());
    }

    @Test
    void guardaYDevuelveLaVistaActualizada() {
        SettingsView view = update.execute(admin, valid());

        GymSettings saved = settings.settings;
        assertEquals("Gym Fuerza", saved.gymName());
        assertEquals("Av. Juárez 10", saved.address());
        assertEquals("55 1234 5678", saved.phone());
        assertEquals("¡Gracias!", saved.receiptFooter());
        assertArrayEquals(PNG, saved.logo());
        assertEquals(10, saved.warningDays());
        assertEquals(45, saved.autoDeactivationDays());
        assertEquals("GF-", prefix());
        assertEquals("Gym Fuerza", view.gymName());
        assertEquals("GF-0001", view.folioExample());
    }

    @Test
    void recortaEspacios_yLosOpcionalesEnBlancoQuedanVacios() {
        update.execute(admin, with("  Gym Fuerza ", "   ", "\t", " ", null, MXN, 7, 30, " GYM- "));

        GymSettings saved = settings.settings;
        assertEquals("Gym Fuerza", saved.gymName());
        assertNull(saved.address());
        assertNull(saved.phone());
        assertNull(saved.receiptFooter());
        assertEquals("GYM-", prefix());
    }

    @Test
    void logoNulo_quitaElLogotipo() {
        settings.settings = new GymSettings("Gym", null, null, PNG, null, MXN, 7, 30);
        update.execute(admin, logo(null));
        assertNull(settings.settings.logo());
    }

    @Test
    void todosLosErroresSeReportanJuntos_enOrdenDelFormulario_ySinGuardar() {
        Map<String, String> errors = errors(with(" ", "x".repeat(201), "x".repeat(31), "x".repeat(161),
                new byte[]{1, 2, 3}, null, 0, 366, "gym"));

        assertEquals(List.of("gymName", "address", "phone", "receiptFooter", "logo", "currency", "warningDays",
                "autoDeactivationDays", "folioPrefix"), List.copyOf(errors.keySet()));
        assertEquals(0, settings.saves);
    }

    @Test
    void mensajesDeCadaCampo() {
        Map<String, String> errors = errors(with(null, "x".repeat(201), "x".repeat(31), "x".repeat(161),
                new byte[]{1}, null, null, null, null));

        assertEquals("Escribe el nombre del gimnasio.", errors.get("gymName"));
        assertEquals("Usa 200 caracteres o menos.", errors.get("address"));
        assertEquals("Usa 30 caracteres o menos.", errors.get("phone"));
        assertEquals("Usa 160 caracteres o menos.", errors.get("receiptFooter"));
        assertEquals("Usa una imagen PNG, JPG, WEBP o SVG de hasta 300 KB.", errors.get("logo"));
        assertEquals("Elige una moneda.", errors.get("currency"));
        assertEquals("Escribe un número de días entre 1 y 60.", errors.get("warningDays"));
        assertEquals("Escribe un número de días entre 1 y 365.", errors.get("autoDeactivationDays"));
        assertEquals("Usa de 1 a 8 letras mayúsculas, números o guiones, por ejemplo GYM-.", errors.get("folioPrefix"));
    }

    @Test
    void nombreDeMasDe100Caracteres_seRechaza() {
        assertEquals(Map.of("gymName", "Usa 100 caracteres o menos."),
                errors(with("x".repeat(101), null, null, null, null, MXN, 7, 30, "GYM-")));
    }

    @Test
    void enLosLimites_seAcepta() {
        update.execute(admin, with("x".repeat(100), "x".repeat(200), "x".repeat(30), "x".repeat(160),
                null, MXN, 60, 365, "ABCD-123"));
        update.execute(admin, with("G", null, null, null, null, MXN, 1, 1, "G"));
        assertEquals(2, settings.saves);
    }

    @ParameterizedTest
    @ValueSource(strings = {"gym-", "GYM_", "GYM 1", "ABCDE1234"})
    void prefijoInvalido_seRechaza(String prefix) {
        assertEquals(List.of("folioPrefix"), List.copyOf(errors(with("Gym", null, null, null, null, MXN, 7, 30, prefix)).keySet()));
    }

    @Test
    void conCobrosOVentas_noSePuedeCambiarLaMoneda_peroSiConservarla() {
        payments.count = 1;

        assertEquals(Map.of("currency", "No se puede cambiar: ya hay cobros o ventas registrados en MXN."),
                errors(with("Gym", null, null, null, null, USD, 7, 30, "GYM-")));
        update.execute(admin, with("Gym", null, null, null, null, MXN, 7, 30, "GYM-"));
        assertEquals(MXN, settings.settings.currency());
    }

    @Test
    void conSoloVentas_tambienSeBloqueaLaMoneda() {
        sales.count = 3;
        assertEquals(List.of("currency"), List.copyOf(errors(with("Gym", null, null, null, null, USD, 7, 30, "GYM-")).keySet()));
    }

    @Test
    void sinCobrosNiVentas_laMonedaSePuedeCambiar() {
        update.execute(admin, with("Gym", null, null, null, null, USD, 7, 30, "GYM-"));
        assertEquals(USD, settings.settings.currency());
    }

    @Test
    void soloElAdministrador_guardaLaConfiguracion() {
        User recepcion = users.add("Pedro", Role.RECEPCION, true);
        assertThrows(DomainException.class, () -> update.execute(recepcion, valid()));
        assertEquals(0, settings.saves);
    }

    // ---------- Logotipo ----------

    private static byte[] padded(byte[] head, int length) {
        return Arrays.copyOf(head, length);
    }

    @Test
    void aceptaPngJpgWebpYSvg() {
        byte[] jpg = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0};
        byte[] webp = "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.ISO_8859_1);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] svgWithXmlHeader = "﻿  <?xml version=\"1.0\"?>\n<svg/>".getBytes(StandardCharsets.UTF_8);

        for (byte[] image : List.of(PNG, jpg, webp, svg, svgWithXmlHeader)) {
            update.execute(admin, logo(image));
            assertArrayEquals(image, settings.settings.logo());
        }
    }

    @Test
    void otrosFormatos_seRechazan() {
        byte[] gif = "GIF89a".getBytes(StandardCharsets.US_ASCII);
        byte[] riffSinWebp = "RIFF\0\0\0\0WAVE".getBytes(StandardCharsets.ISO_8859_1);
        byte[] xmlSinSvg = "<?xml version=\"1.0\"?><html/>".getBytes(StandardCharsets.UTF_8);

        for (byte[] image : List.of(gif, riffSinWebp, xmlSinSvg, new byte[0])) {
            assertEquals(List.of("logo"), List.copyOf(errors(logo(image)).keySet()));
        }
    }

    @Test
    void hasta300KB_seAcepta_yUnByteMas_seRechaza() {
        update.execute(admin, logo(padded(PNG, 307_200)));
        assertEquals(List.of("logo"), List.copyOf(errors(logo(padded(PNG, 307_201))).keySet()));
    }
}
