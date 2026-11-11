package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SessionTest {
    private static final User CASHIER = new User(7L, "Luis Pérez", "luis", Role.CAJA, true);

    @Test
    void withoutSessionNothingIsAllowed() {
        Session session = new Session();

        assertFalse(session.isOpen());
        assertFalse(session.can(Permission.SELL));
        assertThrows(IllegalStateException.class, session::actor);
    }

    @Test
    void permissionsFollowTheRole() {
        Session session = new Session();
        session.open(CASHIER);

        assertSame(CASHIER, session.actor());
        assertTrue(session.can(Permission.SELL));
        assertFalse(session.can(Permission.MANAGE_SETTINGS));
    }

    @Test
    void closingDiscardsTheActor() {
        Session session = new Session();
        session.open(CASHIER);
        session.close();

        assertFalse(session.isOpen());
        assertSame(null, session.actorProperty().get());
    }
}
