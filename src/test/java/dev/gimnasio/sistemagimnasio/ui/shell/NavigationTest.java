package dev.gimnasio.sistemagimnasio.ui.shell;

import dev.gimnasio.sistemagimnasio.domain.user.Role;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NavigationTest {

    private static List<String> titles(Role role) {
        return Navigation.groups(role).stream().map(g -> String.valueOf(g.title())).toList();
    }

    private static List<Route> routes(Role role) {
        return Navigation.groups(role).stream().flatMap(g -> g.routes().stream()).toList();
    }

    @Test
    void administratorSeesEverySectionInThePrototypeGroups() {
        assertEquals(List.of("null", "Mostrador", "Socios", "Catálogo", "Administración"), titles(Role.ADMINISTRADOR));
        assertEquals(Arrays.asList(Route.values()), routes(Role.ADMINISTRADOR));
    }

    @Test
    void receptionSeesInventoryButNotTheRestOfTheCatalogOrAdministration() {
        assertEquals(List.of(Route.TABLERO, Route.COBRO, Route.VENTA, Route.SOCIOS, Route.INVENTARIO), routes(Role.RECEPCION));
        assertEquals(List.of("null", "Mostrador", "Socios", "Catálogo"), titles(Role.RECEPCION));
    }

    @Test
    void cashierOnlySeesTheCounterAndMembers() {
        assertEquals(List.of(Route.COBRO, Route.VENTA, Route.SOCIOS), routes(Role.CAJA));
        assertEquals(List.of("Mostrador", "Socios"), titles(Role.CAJA), "un grupo sin secciones visibles no aparece");
    }

    @Test
    void startsOnTheDashboardOnlyIfTheRoleCanSeeIt() {
        assertEquals(Route.TABLERO, Navigation.start(Role.ADMINISTRADOR));
        assertEquals(Route.TABLERO, Navigation.start(Role.RECEPCION));
        assertEquals(Route.COBRO, Navigation.start(Role.CAJA));
    }

    @Test
    void everyRoleSeesWhereItStarts() {
        for (Role role : Role.values()) {
            assertTrue(routes(role).contains(Navigation.start(role)), role.name());
        }
    }

    @Test
    void onlyPlansAndSettingsHaveTheirUseCasesAlready() {
        List<Route> ready = Arrays.stream(Route.values()).filter(r -> r.status() == Route.Status.SCREEN_PENDING).toList();
        assertEquals(List.of(Route.PLANES, Route.CONFIGURACION), ready);
        for (Route route : Route.values()) {
            assertFalse(route.useCases().isEmpty(), route.name());
        }
    }
}
