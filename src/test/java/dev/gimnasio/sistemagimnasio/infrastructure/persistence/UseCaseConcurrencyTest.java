package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.fakes.ReversingHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembership;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipResult;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.SetPaymentMethodActive;
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateInitialAdmin;
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateInitialAdminCommand;
import dev.gimnasio.sistemagimnasio.application.usecase.users.SetUserActive;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.lang.reflect.InvocationTargetException;
import java.time.Clock;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Casos de uso reales contra PostgreSQL, con dos estaciones que hacen la misma consulta a la vez antes de que
 * alguna escriba. Si una comprobación sale de su transacción, las dos pasan y la regla se rompe; los
 * repositorios en memoria no pueden detectarlo.
 */
@Testcontainers(disabledWithoutDocker = true)
class UseCaseConcurrencyTest {
    private final ReversingHasher hasher = new ReversingHasher();
    private JdbiTransactionRunner transactions;
    private PostgresUserRepository users;
    private PostgresPaymentMethodRepository methods;

    @BeforeEach
    void setUp() {
        Jdbi jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        transactions = new JdbiTransactionRunner(jdbi);
        users = new PostgresUserRepository(jdbi);
        methods = new PostgresPaymentMethodRepository(jdbi);
    }

    @Test
    void dosEstacionesCreandoElPrimerAdministrador_soloUnaLoLogra() throws Exception {
        UserRepository meeting = new Rendezvous().after(UserRepository.class, users, "count");
        CreateInitialAdmin create = new CreateInitialAdmin(meeting, hasher, transactions);

        List<Object> outcomes = together(
                () -> create.execute(new CreateInitialAdminCommand("Dueño", "dueno", "Gym-Admin-1", "Gym-Admin-1")),
                () -> create.execute(new CreateInitialAdminCommand("Socia", "socia", "Gym-Admin-2", "Gym-Admin-2")));

        assertOneSucceededAndOneWasRejected(outcomes);
        assertEquals(1, users.count());
    }

    @Test
    void dosEstacionesDesactivandoLosDosUltimosMetodos_dejanUnoActivo() throws Exception {
        User admin = users.insert(new User(null, "Ana Admin", "ana", Role.ADMINISTRADOR, true), "hash");
        PaymentMethod efectivo = methods.insert(new PaymentMethod(null, "Efectivo", true, true));
        PaymentMethod tarjeta = methods.insert(new PaymentMethod(null, "Tarjeta", false, true));
        PaymentMethodRepository meeting = new Rendezvous().after(PaymentMethodRepository.class, methods, "countActive");
        SetPaymentMethodActive setActive = new SetPaymentMethodActive(meeting, transactions, new Authorizer(users));

        List<Object> outcomes = together(
                () -> setActive.execute(admin, efectivo.id(), false),
                () -> setActive.execute(admin, tarjeta.id(), false));

        assertOneSucceededAndOneWasRejected(outcomes);
        assertEquals(1, methods.countActive());
    }

    @Test
    void dosAdministradoresDesactivandoseEntreSi_dejanUnoActivo() throws Exception {
        User ana = users.insert(new User(null, "Ana Admin", "ana", Role.ADMINISTRADOR, true), "hash");
        User beto = users.insert(new User(null, "Beto Admin", "beto", Role.ADMINISTRADOR, true), "hash");
        UserRepository meeting = new Rendezvous().after(UserRepository.class, users, "countActiveAdmins");
        SetUserActive setActive = new SetUserActive(meeting, transactions, new Authorizer(meeting));

        List<Object> outcomes = together(
                () -> setActive.execute(ana, beto.id(), false),
                () -> setActive.execute(beto, ana.id(), false));

        assertOneSucceededAndOneWasRejected(outcomes);
        assertEquals(1, users.countActiveAdmins());
    }

    @Test
    void dosEstacionesConLaMismaOperacionDeCobro_registranUnSoloCobro() throws Exception {
        Billing billing = new Billing();
        ChargeMembership charge = billing.charge(new Rendezvous()
                .after(MembershipPaymentRepository.class, billing.payments, "findIdByOperationId"), billing.memberships);
        ChargeMembershipCommand command = billing.command("op-1");

        List<Object> outcomes = together(
                () -> charge.execute(billing.cashier, command),
                () -> charge.execute(billing.cashier, command));

        List<ChargeMembershipResult> results = outcomes.stream().map(ChargeMembershipResult.class::cast).toList();
        assertEquals(1, results.stream().filter(ChargeMembershipResult::repeated).count(), outcomes::toString);
        assertEquals(results.get(0).payment(), results.get(1).payment());
        assertEquals(1, billing.payments.count());
    }

    @Test
    void dosEstacionesCobrandoAlMismoSocio_laSegundaRenuevaDesdeElFinDeLaPrimera() throws Exception {
        Billing billing = new Billing();
        ChargeMembership charge = billing.charge(billing.payments, new Rendezvous()
                .after(MembershipRepository.class, billing.memberships, "findByMember"));

        List<Object> outcomes = together(
                () -> charge.execute(billing.cashier, billing.command("op-1")),
                () -> charge.execute(billing.cashier, billing.command("op-2")));

        List<PaymentRow> rows = outcomes.stream().map(o -> ((ChargeMembershipResult) o).payment())
                .sorted(Comparator.comparing(PaymentRow::folio)).toList();
        assertEquals(List.of("GYM-0001", "GYM-0002"), rows.stream().map(PaymentRow::folio).toList());
        assertEquals(List.of(ChargeKind.PRIMERA, ChargeKind.RENOVACION_ANTICIPADA), rows.stream().map(PaymentRow::kind).toList());
        assertEquals(rows.get(0).end(), rows.get(1).start());
    }

    /** Un socio, un plan, un método, un cajero y la serie de cobros, listos para cobrar. */
    private final class Billing {
        final Jdbi jdbi = TestDatabase.jdbi();
        final PostgresMembershipPaymentRepository payments = new PostgresMembershipPaymentRepository(jdbi);
        final PostgresMembershipRepository memberships = new PostgresMembershipRepository(jdbi);
        final User cashier = users.insert(new User(null, "Lucía Pérez", "lucia", Role.CAJA, true), "hash");
        final PaymentMethod tarjeta = methods.insert(new PaymentMethod(null, "Tarjeta", false, true));
        final long member;
        final long plan;

        Billing() {
            jdbi.useHandle(handle -> {
                handle.execute("INSERT INTO configuracion (nombre_gimnasio) VALUES ('Gym')");
                handle.execute("INSERT INTO folio_secuencia (serie, prefijo) VALUES ('COBRO', 'GYM-')");
            });
            member = jdbi.withHandle(handle -> handle.createQuery("INSERT INTO socio (nombre, telefono, fecha_alta) "
                    + "VALUES ('Ana López', '5512345678', '2026-01-01') RETURNING id").mapTo(Long.class).one());
            plan = jdbi.withHandle(handle -> handle.createQuery("INSERT INTO plan_membresia (nombre, duracion_dias, precio) "
                    + "VALUES ('Mensual', 30, 450) RETURNING id").mapTo(Long.class).one());
        }

        ChargeMembershipCommand command(String operationId) {
            return new ChargeMembershipCommand(operationId, member, plan, tarjeta.id(), null, null);
        }

        ChargeMembership charge(MembershipPaymentRepository paymentsPort, MembershipRepository membershipsPort) {
            return new ChargeMembership(new PostgresMemberRepository(jdbi), membershipsPort, new PostgresPlanRepository(jdbi),
                    methods, new PostgresSettingsRepository(jdbi), paymentsPort, new PostgresFolioSeriesRepository(jdbi),
                    users, transactions, new Authorizer(users), Clock.systemDefaultZone());
        }
    }

    /** Uno devolvió su resultado y el otro recibió una regla de negocio, no un error técnico. */
    private static void assertOneSucceededAndOneWasRejected(List<Object> outcomes) {
        long rejected = outcomes.stream().filter(DomainException.class::isInstance).count();
        long succeeded = outcomes.stream().filter(outcome -> !(outcome instanceof Throwable)).count();
        assertEquals(1, rejected, outcomes::toString);
        assertEquals(1, succeeded, outcomes::toString);
    }

    /** Corre las dos operaciones a la vez; cada elemento es lo que devolvió o la excepción que lanzó. */
    private static List<Object> together(Callable<Object> first, Callable<Object> second) throws InterruptedException {
        ExecutorService stations = Executors.newFixedThreadPool(2);
        try {
            List<Object> outcomes = new ArrayList<>();
            for (Future<Object> future : stations.invokeAll(List.of(first, second), 30, TimeUnit.SECONDS)) {
                try {
                    outcomes.add(future.get());
                } catch (ExecutionException e) {
                    outcomes.add(e.getCause());
                }
            }
            return outcomes;
        } finally {
            stations.shutdownNow();
        }
    }

    /**
     * Detiene a cada estación justo después de una consulta hasta que la otra también la hizo, para que las dos
     * lean antes de que alguna escriba. Solo esperan las dos primeras llamadas; los reintentos pasan de largo.
     */
    private static final class Rendezvous {
        private final CountDownLatch arrived = new CountDownLatch(2);

        <T> T after(Class<T> port, T delegate, String method) {
            Object proxy = Proxy.newProxyInstance(port.getClassLoader(), new Class<?>[]{port}, (self, called, args) -> {
                Object result;
                try {
                    result = called.invoke(delegate, args);
                } catch (InvocationTargetException e) {
                    throw e.getCause();
                }
                if (called.getName().equals(method)) {
                    arrived.countDown();
                    arrived.await(5, TimeUnit.SECONDS);
                }
                return result;
            });
            return port.cast(proxy);
        }
    }
}
