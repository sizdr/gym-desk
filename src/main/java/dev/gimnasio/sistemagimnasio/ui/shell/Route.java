package dev.gimnasio.sistemagimnasio.ui.shell;

import dev.gimnasio.sistemagimnasio.domain.user.Permission;

import java.util.List;

/**
 * Secciones del menú, con el permiso que las muestra, en el orden y los grupos del prototipo.
 * Las que todavía no funcionan dicen qué les falta.
 */
public enum Route {
    TABLERO(null, "Tablero", "Tablero", "tablero", Permission.VIEW_DAILY_SUMMARY,
            "Resumen del día: socios activos, vencimientos y cobros.",
            Status.READY, List.of("GetDaySummary")),
    COBRO("Mostrador", "Cobrar membresía", "Cobrar membresía", "tarjeta", Permission.CHARGE_MEMBERSHIP,
            "Cobra un plan a un socio y entrega su recibo.",
            Status.READY, List.of("ListChargeOptions", "PreviewMembershipCharge", "ChargeMembership",
            "GetMembershipReceipt", "PrintMembershipReceipt")),
    SOCIOS("Socios", "Socios", "Socios", "usuarios", Permission.VIEW_MEMBERS,
            "Busca socios, revisa su vigencia y registra altas.",
            Status.READY, List.of("SearchMembers", "RegisterMember", "UpdateMember", "DeleteMember")),
    PLANES("Catálogo", "Planes", "Planes de membresía", "calendario", Permission.MANAGE_PLANS,
            "Duración, precio y ahorro de cada plan.",
            Status.SCREEN_PENDING, List.of("ListPlans", "CreatePlan", "UpdatePlan", "SetPlanActive", "DeletePlan",
            "PreviewPlanSavings")),
    USUARIOS("Administración", "Usuarios", "Usuarios", "escudo", Permission.MANAGE_USERS,
            "Personas que usan el sistema, su rol y si están activas.",
            Status.SCREEN_PENDING, List.of("ListUsers", "CreateUser", "UpdateUser", "SetUserActive", "DeleteUser")),
    CONFIGURACION("Administración", "Configuración", "Configuración", "ajustes", Permission.MANAGE_SETTINGS,
            "Datos del gimnasio, plazos de membresía, moneda, folios y métodos de pago.",
            Status.READY, List.of("GetSettings", "UpdateSettings", "métodos de pago"));

    /** Qué le falta a la sección para funcionar. */
    public enum Status {
        /** Faltan los casos de uso: la UI no puede mostrar nada real todavía. */
        MISSING_USE_CASES,
        /** Los casos de uso existen; falta construir la pantalla. */
        SCREEN_PENDING,
        /** La pantalla funciona con sus casos de uso. */
        READY
    }

    private final String group;
    private final String menuLabel;
    private final String title;
    private final String icon;
    private final Permission permission;
    private final String description;
    private final Status status;
    private final List<String> useCases;

    Route(String group, String menuLabel, String title, String icon, Permission permission, String description,
          Status status, List<String> useCases) {
        this.group = group;
        this.menuLabel = menuLabel;
        this.title = title;
        this.icon = icon;
        this.permission = permission;
        this.description = description;
        this.status = status;
        this.useCases = useCases;
    }

    /** {@code null} para el tablero, que va solo arriba del menú. */
    public String group() {
        return group;
    }

    public String menuLabel() {
        return menuLabel;
    }

    public String title() {
        return title;
    }

    public String icon() {
        return icon;
    }

    public Permission permission() {
        return permission;
    }

    public String description() {
        return description;
    }

    public Status status() {
        return status;
    }

    public List<String> useCases() {
        return useCases;
    }
}
