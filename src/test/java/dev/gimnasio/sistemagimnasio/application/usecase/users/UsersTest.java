package dev.gimnasio.sistemagimnasio.application.usecase.users;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.fakes.ReversingHasher;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UsersTest {
    final InMemoryUsers users = new InMemoryUsers();
    final ReversingHasher hasher = new ReversingHasher();
    final ImmediateTransactions transactions = new ImmediateTransactions();
    final Authorizer authorizer = new Authorizer(users);
    final User admin = users.add(new User(null, "Ana Admin", "ana", Role.ADMINISTRADOR, true), "hash");

    final ListUsers list = new ListUsers(users, authorizer);

    @Nested
    class Listar {
        @Test
        void primeroLosActivos_luegoPorNombre() {
            users.add(new User(null, "lucía Pérez", "lucia", Role.CAJA, true), "h");
            users.add(new User(null, "Beto Ruiz", "beto", Role.RECEPCION, false), "h");
            users.add(new User(null, "Carla Díaz", "carla", Role.CAJA, true), "h");

            assertEquals(List.of("Ana Admin", "Carla Díaz", "lucía Pérez", "Beto Ruiz"),
                    list.execute(admin).stream().map(UserRow::name).toList());
        }

        @Test
        void cadaFila_traeSusDatos_sinElHash() {
            assertEquals(List.of(new UserRow(admin.id(), "Ana Admin", "ana", Role.ADMINISTRADOR, true)),
                    list.execute(admin));
        }
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RECEPCION", "CAJA"})
    void soloElAdministrador_administraUsuarios(Role role) {
        User user = users.add(new User(null, "Persona", "persona", role, true), "h");

        assertThrows(DomainException.class, () -> list.execute(user));
        assertEquals(0, users.writes);
    }
}
