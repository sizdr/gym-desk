package dev.gimnasio.sistemagimnasio.infrastructure.bootstrap;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.fakes.ReversingHasher;
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateInitialAdmin;
import dev.gimnasio.sistemagimnasio.application.usecase.users.UserRow;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InitialAdminSetupTest {
    private final InMemoryUsers users = new InMemoryUsers();
    private final ReversingHasher hasher = new ReversingHasher();
    private final CreateInitialAdmin create = new CreateInitialAdmin(users, hasher, new ImmediateTransactions());

    @Test
    void sinUsuarios_creaElAdministradorConLasVariables() {
        Optional<UserRow> admin = InitialAdminSetup.ensure(
                Map.of("ADMIN_NAME", "Dueño", "ADMIN_USERNAME", "Admin", "ADMIN_PASSWORD", "Gym-Admin-1"), create);

        assertEquals(Optional.of(new UserRow(admin.orElseThrow().id(), "Dueño", "admin", Role.ADMINISTRADOR, true)), admin);
        assertEquals(hasher.hash("Gym-Admin-1"), users.hashOf(admin.get().id()));
    }

    @Test
    void sinNombre_usaAdministrador() {
        UserRow admin = InitialAdminSetup.ensure(Map.of("ADMIN_USERNAME", "admin", "ADMIN_PASSWORD", "Gym-Admin-1"), create)
                .orElseThrow();
        assertEquals("Administrador", admin.name());
    }

    @Test
    void conUsuarios_noHaceNada_aunqueFaltenLasVariables() {
        users.add(new User(null, "Ana", "ana", Role.CAJA, true), "h");

        assertEquals(Optional.empty(), InitialAdminSetup.ensure(Map.of(), create));
        assertEquals(1, users.size());
    }

    @Test
    void sinUsuariosYSinVariables_fallaExplicandoQueDefinir() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> InitialAdminSetup.ensure(Map.of("ADMIN_USERNAME", "admin"), create));

        assertEquals(InitialAdminSetup.MISSING, e.getMessage());
        assertEquals(0, users.size());
    }

    @Test
    void conVariablesInvalidas_fallaConElMotivo_sinMostrarLaContrasena() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> InitialAdminSetup.ensure(Map.of("ADMIN_USERNAME", "admin", "ADMIN_PASSWORD", "corta"), create));

        assertTrue(e.getMessage().contains("al menos 8 caracteres"), e.getMessage());
        assertFalse(e.getMessage().contains("corta"));
        assertEquals(0, users.size());
    }
}
