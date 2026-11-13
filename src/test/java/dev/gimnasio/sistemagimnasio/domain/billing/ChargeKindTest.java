package dev.gimnasio.sistemagimnasio.domain.billing;

import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChargeKindTest {

    @Test
    void cadaEstado_tieneSuTipoDeCobro() {
        assertEquals(ChargeKind.PRIMERA, ChargeKind.forStatus(MemberStatus.SIN_MEMBRESIA));
        assertEquals(ChargeKind.RENOVACION_ANTICIPADA, ChargeKind.forStatus(MemberStatus.VIGENTE));
        assertEquals(ChargeKind.RENOVACION_ANTICIPADA, ChargeKind.forStatus(MemberStatus.POR_VENCER));
        assertEquals(ChargeKind.RENOVACION_VENCIDA, ChargeKind.forStatus(MemberStatus.VENCIDA));
        assertEquals(ChargeKind.REACTIVACION, ChargeKind.forStatus(MemberStatus.BAJA));
    }
}
