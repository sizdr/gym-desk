package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository.RecordedPayment;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.billing.MembershipPayment;
import dev.gimnasio.sistemagimnasio.domain.member.Email;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.Phone;
import dev.gimnasio.sistemagimnasio.domain.payment.Folio;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.UNIQUE_VIOLATION;
import static dev.gimnasio.sistemagimnasio.infrastructure.persistence.TestDatabase.assertRejected;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Socios, membresías, cobros y folios: lo que guarda y lee el cobro de membresías. */
@Testcontainers(disabledWithoutDocker = true)
class PostgresBillingRepositoriesTest {
    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");

    private Jdbi jdbi;
    private PostgresMemberRepository members;
    private PostgresMembershipRepository memberships;
    private PostgresMembershipPaymentRepository payments;
    private PostgresFolioSeriesRepository folios;
    private long usuario;
    private long socio;
    private long plan;
    private long efectivo;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        members = new PostgresMemberRepository(jdbi);
        memberships = new PostgresMembershipRepository(jdbi);
        payments = new PostgresMembershipPaymentRepository(jdbi, ZONE);
        folios = new PostgresFolioSeriesRepository(jdbi);
        usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Ana', 'ana', 'h', 'caja')");
        socio = id("INSERT INTO socio (nombre, telefono, correo, fecha_alta) VALUES ('Luis Mora', '5512345678', 'luis@gym.mx', '2026-11-01')");
        plan = id("INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('Mensual', 30, 450)");
        efectivo = id("INSERT INTO metodo_pago (nombre, es_efectivo) VALUES ('Efectivo', true)");
    }

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    private MembershipPayment payment(String folio, LocalDateTime when, String received) {
        return new MembershipPayment(null, new Folio(folio), when, socio, plan, efectivo, Money.of("450"),
                received == null ? null : Money.of(received), usuario);
    }

    @Test
    void leeAlSocio_conTelefonoYCorreo() {
        assertEquals(Optional.of(new Member(socio, "Luis Mora", new Phone("5512345678"), new Email("luis@gym.mx"),
                LocalDate.of(2026, 11, 1))), members.findById(socio));
        assertEquals(Optional.empty(), members.findById(socio + 1));
    }

    @Test
    void guardaElCobroConSuTipo_yLoLeeConLaMismaHoraLocal() {
        LocalDateTime when = LocalDateTime.of(2026, 11, 12, 21, 45, 10);
        MembershipPayment saved = payments.insert(payment("GYM-0001", when, "500"), ChargeKind.REACTIVACION, "op-1");
        memberships.insert(new Membership(null, socio, plan, saved.id(), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 12, 12)));

        RecordedPayment read = payments.findById(saved.id()).orElseThrow();

        assertEquals(saved, read.payment());
        assertEquals(when, read.payment().dateTime());
        assertEquals(ChargeKind.REACTIVACION, read.kind());
        assertEquals(Optional.of(saved.id()), payments.findIdByOperationId("op-1"));
        assertEquals(Optional.empty(), payments.findIdByOperationId("op-2"));
        assertEquals(1, payments.count());
    }

    @Test
    void sinEfectivoRecibido_quedaVacio() {
        MembershipPayment saved = payments.insert(payment("GYM-0001", LocalDateTime.of(2026, 11, 12, 9, 0), null),
                ChargeKind.PRIMERA, "op-1");
        memberships.insert(new Membership(null, socio, plan, saved.id(), LocalDate.of(2026, 11, 12), LocalDate.of(2026, 12, 12)));

        assertEquals(null, payments.findById(saved.id()).orElseThrow().payment().cashReceived());
    }

    @Test
    void laMismaOperacion_noSeGuardaDosVeces() {
        payments.insert(payment("GYM-0001", LocalDateTime.of(2026, 11, 12, 9, 0), null), ChargeKind.PRIMERA, "op-1");
        assertRejected(UNIQUE_VIOLATION, () -> payments.insert(
                payment("GYM-0002", LocalDateTime.of(2026, 11, 12, 9, 1), null), ChargeKind.PRIMERA, "op-1"));
    }

    @Test
    void membresias_porSocioEnOrden_yPorCobro() {
        MembershipPayment p1 = payments.insert(payment("GYM-0001", LocalDateTime.of(2026, 11, 1, 9, 0), null), ChargeKind.PRIMERA, "a");
        MembershipPayment p2 = payments.insert(payment("GYM-0002", LocalDateTime.of(2026, 11, 20, 9, 0), null),
                ChargeKind.RENOVACION_ANTICIPADA, "b");
        Membership second = memberships.insert(new Membership(null, socio, plan, p2.id(), LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 31)));
        Membership first = memberships.insert(new Membership(null, socio, plan, p1.id(), LocalDate.of(2026, 11, 1), LocalDate.of(2026, 12, 1)));

        assertEquals(List.of(first, second), memberships.findByMember(socio));
        assertEquals(Optional.of(second), memberships.findByPayment(p2.id()));
        assertEquals(List.of(), memberships.findByMember(socio + 1));
    }

    @Test
    void elSiguienteFolio_avanzaElContador_conElPrefijoYAnchoDeLaSerie() {
        jdbi.useHandle(handle -> handle.execute("INSERT INTO folio_secuencia (serie, prefijo, ultimo) VALUES ('COBRO', 'GYM-', 41)"));

        assertEquals(new Folio("GYM-0042"), folios.next("COBRO"));
        assertEquals(new Folio("GYM-0043"), folios.next("COBRO"));
        assertThrows(IllegalStateException.class, () -> folios.next("VENTA"));
    }

    @Test
    void siLaTransaccionSeDeshace_elFolioNoSeGasta() {
        jdbi.useHandle(handle -> handle.execute("INSERT INTO folio_secuencia (serie, prefijo) VALUES ('COBRO', 'GYM-')"));
        JdbiTransactionRunner transactions = new JdbiTransactionRunner(jdbi);

        assertThrows(IllegalStateException.class, () -> transactions.run(() -> {
            folios.next("COBRO");
            throw new IllegalStateException("falla el cobro");
        }));

        assertEquals(new Folio("GYM-0001"), folios.next("COBRO"));
    }
}
