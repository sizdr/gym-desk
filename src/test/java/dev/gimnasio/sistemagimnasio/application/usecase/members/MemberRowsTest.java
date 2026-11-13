package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.domain.member.Email;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.Phone;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemberRowsTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 12);
    private static final Member ANA = new Member(7L, "Ana López", new Phone("5512345678"), new Email("Ana@Gym.mx"),
            LocalDate.of(2026, 1, 10));

    private static MemberRow row(LocalDate end) {
        Membership latest = end == null ? null : new Membership(1L, 7, 1, 1, end.minusDays(30), end);
        return MemberRows.of(ANA, latest, end == null ? null : "Mensual", TODAY, 7, 30);
    }

    @Test
    void sinMembresia_soloDatosDelSocio() {
        assertEquals(new MemberRow(7, "Ana López", "5512345678", "ana@gym.mx", MemberStatus.SIN_MEMBRESIA,
                null, null, null, null, null, null), row(null));
    }

    @Test
    void cubierto_traeLosDiasQueLeQuedan() {
        assertEquals(new MemberRow(7, "Ana López", "5512345678", "ana@gym.mx", MemberStatus.VIGENTE, "Mensual",
                TODAY.plusDays(12), 12, null, null, null), row(TODAY.plusDays(12)));
        assertEquals(0, row(TODAY).daysLeft());
        assertEquals(MemberStatus.POR_VENCER, row(TODAY).status());
    }

    @Test
    void vencido_traeLosDiasDeAtrasoYCuandoPasaraABaja() {
        MemberRow row = row(TODAY.minusDays(5));
        assertEquals(MemberStatus.VENCIDA, row.status());
        assertEquals(5, row.daysOverdue());
        assertEquals(TODAY.minusDays(5).plusDays(30), row.deactivatesOn());
    }

    @Test
    void enBaja_suFechaDeBajaEsElVencimiento() {
        MemberRow row = row(TODAY.minusDays(40));
        assertEquals(MemberStatus.BAJA, row.status());
        assertEquals(40, row.daysOverdue());
        assertEquals(TODAY.minusDays(40), row.deactivationDate());
        assertEquals(null, row.deactivatesOn());
    }
}
