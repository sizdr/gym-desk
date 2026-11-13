package dev.gimnasio.sistemagimnasio.domain.billing;

import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;

/** Qué representa un cobro según el estado del socio en ese momento; decide dónde empieza la vigencia. */
public enum ChargeKind {
    /** Nunca había pagado: empieza el día elegido (hoy por defecto). */
    PRIMERA,
    /** Sigue cubierto: el nuevo periodo empieza el día en que termina el actual. */
    RENOVACION_ANTICIPADA,
    /** Ya venció pero no ha pasado a baja: empieza hoy. */
    RENOVACION_VENCIDA,
    /** Estaba en baja: empieza hoy. */
    REACTIVACION;

    public static ChargeKind forStatus(MemberStatus status) {
        return switch (status) {
            case SIN_MEMBRESIA -> PRIMERA;
            case VIGENTE, POR_VENCER -> RENOVACION_ANTICIPADA;
            case VENCIDA -> RENOVACION_VENCIDA;
            case BAJA -> REACTIVACION;
        };
    }
}
