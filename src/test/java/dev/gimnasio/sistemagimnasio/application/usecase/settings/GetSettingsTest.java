package dev.gimnasio.sistemagimnasio.application.usecase.settings;

import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryFolioSeries;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembershipPayments;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySettings;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GetSettingsTest {
    private final InMemorySettings settings = new InMemorySettings();
    private final InMemoryUsers users = new InMemoryUsers();
    private final InMemoryFolioSeries folioSeries = new InMemoryFolioSeries();
    private final InMemoryMembershipPayments payments = new InMemoryMembershipPayments();
    private final GetSettings getSettings = new GetSettings(settings, folioSeries, payments,
            new Authorizer(users));

    @Test
    void devuelveTodosLosCampos_ySinCobrosNiVentasLaMonedaNoSeBloquea() {
        settings.settings = new GymSettings("Gym Fuerza", "Av. 1", "5512345678", new byte[]{7}, "Gracias",
                InMemorySettings.MXN, 10, 45);
        folioSeries.put(new FolioSeries(FolioSeries.MEMBERSHIP_PAYMENTS, "GF-", 4));

        SettingsView view = getSettings.execute(users.admin());

        assertEquals("Gym Fuerza", view.gymName());
        assertEquals("Av. 1", view.address());
        assertEquals("5512345678", view.phone());
        assertEquals("Gracias", view.receiptFooter());
        assertArrayEquals(new byte[]{7}, view.logo());
        assertEquals(InMemorySettings.MXN, view.currency());
        assertEquals(10, view.warningDays());
        assertEquals(45, view.autoDeactivationDays());
        assertEquals("GF-", view.folioPrefix());
        assertFalse(view.currencyLocked());
        assertEquals(0, view.paymentsCount());
        assertEquals("GF-0001", view.folioExample());
    }

    @Test
    void conCobros_laMonedaQuedaBloqueada_yTraeElConteo() {
        payments.count = 41;

        SettingsView view = getSettings.execute(users.admin());

        assertTrue(view.currencyLocked());
        assertEquals(41, view.paymentsCount());
    }

    @Test
    void elEjemploDeFolio_usaElAnchoDeLaSerie() {
        folioSeries.put(new FolioSeries(FolioSeries.MEMBERSHIP_PAYMENTS, "GYM-", 6));
        assertEquals("GYM-000001", getSettings.execute(users.admin()).folioExample());
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RECEPCION", "CAJA"})
    void soloElAdministrador_consultaLaConfiguracion(Role role) {
        assertThrows(DomainException.class, () -> getSettings.execute(users.add("Persona", role, true)));
    }
}
