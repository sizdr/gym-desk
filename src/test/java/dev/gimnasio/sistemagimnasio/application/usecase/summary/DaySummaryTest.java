package dev.gimnasio.sistemagimnasio.application.usecase.summary;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryFolioSeries;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembers;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembershipPayments;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMemberships;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPaymentMethods;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPlans;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySettings;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembership;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaySummaryTest {
    static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    /** Jueves: la semana termina el domingo 15. */
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 9, 30);
    static final LocalDate TODAY = NOW.toLocalDate();

    /** Reloj que se puede mover, para registrar cobros en distintos momentos. */
    static final class MovableClock extends Clock {
        Instant instant;

        MovableClock(LocalDateTime at) {
            set(at);
        }

        void set(LocalDateTime at) {
            instant = at.atZone(ZONE).toInstant();
        }

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    final MovableClock clock = new MovableClock(NOW);
    final InMemoryUsers users = new InMemoryUsers();
    final User admin = users.admin();
    final User recepcion = users.add("rocio", Role.RECEPCION, true);
    final User caja = users.add("carlos", Role.CAJA, true);
    final InMemoryMembers members = new InMemoryMembers();
    final InMemoryMemberships memberships = new InMemoryMemberships();
    final InMemoryPlans plans = new InMemoryPlans();
    final InMemoryPaymentMethods methods = new InMemoryPaymentMethods();
    final InMemorySettings settings = new InMemorySettings();
    final InMemoryMembershipPayments payments = new InMemoryMembershipPayments();
    final Authorizer authorizer = new Authorizer(users);
    final Plan mensual = plans.add("Mensual", 30, "450", null, true);
    final Plan anual = plans.add("Anual", 365, "4200", null, true);
    final PaymentMethod tarjeta = methods.add("Tarjeta", false, true);

    final GetDaySummary summary = new GetDaySummary(members, memberships, plans, settings, payments, methods, users,
            authorizer, clock);
    final ChargeMembership charge = new ChargeMembership(members, memberships, plans, methods, settings, payments,
            new InMemoryFolioSeries(), users, new ImmediateTransactions(), authorizer, clock);

    /** Un socio con una membresía que vence en {@code endFromToday} días (negativo: ya venció). */
    Member withMembership(String name, Integer endFromToday) {
        Member member = members.add(name, TODAY.minusDays(200));
        if (endFromToday != null) {
            LocalDate end = TODAY.plusDays(endFromToday);
            memberships.add(member.id(), mensual.id(), end.minusDays(30), end);
        }
        return member;
    }

    /** Cobra con tarjeta a {@code member} en el momento {@code at}. */
    void chargeAt(LocalDateTime at, Member member, Plan plan) {
        clock.set(at);
        charge.execute(caja, new ChargeMembershipCommand(UUID.randomUUID().toString(), member.id(), plan.id(),
                tarjeta.id(), null, null));
        clock.set(NOW);
    }

    static List<String> names(List<MemberRow> rows) {
        return rows.stream().map(MemberRow::fullName).toList();
    }

    @Test
    void cuentaYOrdenaLasVigencias() {
        withMembership("Ana Vigente", 20);
        withMembership("Bruno Tres", 3);
        withMembership("Carla Uno", 1);
        withMembership("Diego Cinco", 5);
        withMembership("Elena Vencida", -10);
        withMembership("Fabián Vencido", -2);
        withMembership("Gina Baja", -40);
        withMembership("Hugo Sin", null);

        DaySummary result = summary.execute(admin);

        assertEquals(TODAY, result.today());
        assertEquals(LocalDate.of(2026, 11, 15), result.weekEnd(), "domingo de esta semana");
        assertEquals(4, result.activeMembers(), "vigentes y por vencer");
        assertEquals(3, result.expiringCount());
        assertEquals(2, result.expiringThisWeekCount(), "vencen el viernes 13 y el domingo 15; el martes 17 ya no");
        assertEquals(2, result.expiredCount(), "la baja no cuenta como vencida");
        assertEquals(List.of("Carla Uno", "Bruno Tres", "Diego Cinco"), names(result.expiring()),
                "del vencimiento más próximo al más lejano");
        assertEquals(List.of("Fabián Vencido", "Elena Vencida"), names(result.expired()),
                "del vencimiento más reciente al más antiguo");
        MemberRow fabian = result.expired().getFirst();
        assertEquals(TODAY.minusDays(2).plusDays(30), fabian.deactivatesOn(), "pasa a baja a los 30 días");
        assertEquals(2, fabian.daysOverdue());
    }

    @Test
    void elPlazoDeAvisoSaleDeLaConfiguracion() {
        withMembership("Diego Cinco", 5);
        settings.settings = new dev.gimnasio.sistemagimnasio.domain.settings.GymSettings("Mi gimnasio", null, null,
                null, null, settings.settings.currency(), 3, 30);

        DaySummary result = summary.execute(admin);

        assertEquals(0, result.expiringCount(), "con aviso de 3 días, quien vence en 5 sigue vigente");
        assertEquals(1, result.activeMembers());
    }

    @Test
    void ingresosDeHoy_ySoloDeHoy() {
        Member ana = withMembership("Ana", null);
        Member bruno = withMembership("Bruno", null);
        Member carla = withMembership("Carla", null);
        chargeAt(NOW.minusDays(1).withHour(21), ana, mensual);
        chargeAt(TODAY.atStartOfDay(), bruno, mensual);
        chargeAt(NOW, carla, anual);

        DaySummary result = summary.execute(recepcion);

        assertEquals(2, result.paymentsToday(), "el de anoche no cuenta; el de las 00:00 sí");
        assertEquals(new BigDecimal("4650.00"), result.membershipIncomeToday().amount());
    }

    @Test
    void ultimosOchoCobros_elMasRecientePrimero_conSusDatos() {
        for (int i = 0; i < 10; i++) {
            Member member = withMembership("Socio " + i, null);
            chargeAt(NOW.minusHours(10 - i), member, mensual);
        }

        List<PaymentRow> recent = summary.execute(admin).recentPayments();

        assertEquals(8, recent.size());
        assertEquals("Socio 9", recent.getFirst().memberName());
        assertEquals("Socio 2", recent.getLast().memberName());
        PaymentRow last = recent.getFirst();
        assertEquals("Mensual", last.planName());
        assertEquals("Tarjeta", last.paymentMethodName());
        assertEquals("carlos", last.cashierName());
        assertEquals(new BigDecimal("450.00"), last.amount().amount());
    }

    @Test
    void sinSociosNiCobros_todoEnCero() {
        DaySummary result = summary.execute(admin);
        assertEquals(0, result.activeMembers());
        assertEquals(Money.ZERO.amount().signum(), result.membershipIncomeToday().amount().signum());
        assertTrue(result.expiring().isEmpty() && result.expired().isEmpty() && result.recentPayments().isEmpty());
    }

    @Test
    void cajaNoVeElTablero() {
        assertThrows(DomainException.class, () -> summary.execute(caja));
    }
}
