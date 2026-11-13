package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.fakes.ReversingHasher;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.Authenticate;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.AuthenticateCommand;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CreateInitialAdminTest {
    private final InMemoryUsers users = new InMemoryUsers();
    private final ReversingHasher hasher = new ReversingHasher();
    private final ImmediateTransactions transactions = new ImmediateTransactions();
    private final CreateInitialAdmin createInitialAdmin = new CreateInitialAdmin(users, hasher, transactions);

    private static CreateInitialAdminCommand command(String name, String username, String password, String confirmation) {
        return new CreateInitialAdminCommand(name, username, password, confirmation);
    }

    @Test
    void sinUsuarios_creaUnAdministradorActivo_quePuedeIniciarSesion() {
        assertTrue(createInitialAdmin.needsInitialAdmin());

        UserRow row = createInitialAdmin.execute(command(" Dueño ", "Admin", "Gym-Admin-1", "Gym-Admin-1"));

        assertEquals(new UserRow(row.id(), "Dueño", "admin", Role.ADMINISTRADOR, true), row);
        assertEquals(hasher.hash("Gym-Admin-1"), users.hashOf(row.id()));
        assertFalse(createInitialAdmin.needsInitialAdmin());
        User logged = new Authenticate(users, hasher).execute(new AuthenticateCommand("admin", "Gym-Admin-1"));
        assertEquals(Role.ADMINISTRADOR, logged.role());
    }

    @Test
    void siYaHayUsuarios_seRechaza_aunqueNingunoSeaAdministrador() {
        users.add(new User(null, "Lucía", "lucia", Role.CAJA, false), "h");

        DomainException e = assertThrows(DomainException.class,
                () -> createInitialAdmin.execute(command("Dueño", "admin", "Gym-Admin-1", "Gym-Admin-1")));

        assertEquals("El sistema ya tiene usuarios; un administrador debe crear las cuentas nuevas.", e.getMessage());
        assertEquals(1, users.size());
    }

    @Test
    void losErroresDelFormulario_seReportanJuntos_sinCrearNada() {
        Map<String, String> errors = assertThrows(ValidationException.class,
                () -> createInitialAdmin.execute(command("", "a b", "corta", "corta"))).errors();

        assertEquals(List.of("name", "username", "password"), List.copyOf(errors.keySet()));
        assertEquals(0, users.size());
    }

    @Test
    void laConfirmacionDistinta_seRechaza() {
        assertEquals(Map.of("passwordConfirmation", "Las contraseñas no coinciden."), assertThrows(ValidationException.class,
                () -> createInitialAdmin.execute(command("Dueño", "admin", "Gym-Admin-1", "Gym-Admin-2"))).errors());
    }

    @Test
    void revisarQueNoHayUsuarios_yCrearlo_ocurrenEnLaMismaTransaccion() {
        users.guard.watch(transactions);
        createInitialAdmin.execute(command("Dueño", "admin", "Gym-Admin-1", "Gym-Admin-1"));
        assertEquals(1, transactions.started);
    }

    @Test
    void laContrasena_noApareceAlImprimirElCommand() {
        assertFalse(command("Dueño", "admin", "Gym-Admin-1", "Gym-Admin-1").toString().contains("Gym-Admin-1"));
    }
}
