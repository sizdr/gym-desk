package dev.gimnasio.sistemagimnasio.ui.shell;

import dev.gimnasio.sistemagimnasio.domain.user.Role;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Qué secciones ve cada rol y dónde empieza, según {@link Role#permissions()}. Ocultar una sección es solo
 * presentación: cada caso de uso vuelve a revisar el permiso del actor en la base.
 */
public final class Navigation {
    /** Un grupo del menú; {@code title} es {@code null} para el primero (Tablero). */
    public record Group(String title, List<Route> routes) {
        public Group {
            routes = List.copyOf(routes);
        }
    }

    private Navigation() {
    }

    /** Grupos con al menos una sección visible, en el orden del menú. */
    public static List<Group> groups(Role role) {
        Objects.requireNonNull(role, "role");
        List<Group> groups = new ArrayList<>();
        String current = null;
        List<Route> routes = new ArrayList<>();
        boolean first = true;
        for (Route route : Route.values()) {
            if (first || !Objects.equals(route.group(), current)) {
                if (!first && !routes.isEmpty()) {
                    groups.add(new Group(current, routes));
                }
                current = route.group();
                routes = new ArrayList<>();
                first = false;
            }
            if (role.can(route.permission())) {
                routes.add(route);
            }
        }
        if (!routes.isEmpty()) {
            groups.add(new Group(current, routes));
        }
        return List.copyOf(groups);
    }

    /** Como en el prototipo: el tablero si lo puede ver; si no, Cobrar membresía. */
    public static Route start(Role role) {
        return role.can(Route.TABLERO.permission()) ? Route.TABLERO : Route.COBRO;
    }
}
