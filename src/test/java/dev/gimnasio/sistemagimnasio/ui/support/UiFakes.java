package dev.gimnasio.sistemagimnasio.ui.support;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryFolioSeries;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembershipPayments;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPaymentMethods;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPlans;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySales;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySettings;
import dev.gimnasio.sistemagimnasio.application.port.out.PasswordHasher;
import dev.gimnasio.sistemagimnasio.application.port.out.UserCredentials;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.Authenticate;
import dev.gimnasio.sistemagimnasio.application.usecase.gyminfo.GetGymInfo;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.CreatePaymentMethod;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.DeletePaymentMethod;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.ListPaymentMethods;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.SetPaymentMethodActive;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.UpdatePaymentMethod;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.CreatePlan;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.DeletePlan;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.ListPlans;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.PreviewPlanSavings;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.SetPlanActive;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.UpdatePlan;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.GetSettings;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.UpdateSettings;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Themes;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Casos de uso reales sobre repositorios en memoria, para probar la UI sin base de datos. */
public final class UiFakes {
    /** Contraseña de las cuentas de prueba en memoria; solo existe en estas pruebas. */
    public static final String PASSWORD = "prueba-ui-1";
    public static final LocalDate TODAY = LocalDate.of(2026, 11, 10);

    private UiFakes() {
    }

    /** Usuarios con contraseña {@link #PASSWORD}; el hash es falso. */
    public static final class Users implements UserRepository {
        private final Map<Long, User> byId = new LinkedHashMap<>();
        /** Si se pone, cada búsqueda de credenciales espera a que se libere (para ver el estado «Entrando…»). */
        public volatile CountDownLatch gate;
        public volatile int lookups;
        /** Si se pone, cada búsqueda de credenciales lanza este error (para simular la base caída). */
        public volatile RuntimeException failure;

        public User add(String name, String username, Role role, boolean active) {
            User user = new User((long) byId.size() + 1, name, username, role, active);
            byId.put(user.id(), user);
            return user;
        }

        @Override
        public Optional<UserCredentials> findCredentialsByUsername(String username) {
            lookups++;
            if (failure != null) {
                throw failure;
            }
            CountDownLatch wait = gate;
            if (wait != null) {
                try {
                    wait.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            return byId.values().stream()
                    .filter(user -> user.username().equalsIgnoreCase(username))
                    .map(user -> new UserCredentials(user, "hash-falso"))
                    .findFirst();
        }

        @Override
        public Optional<User> findById(long id) {
            return Optional.ofNullable(byId.get(id));
        }

        @Override
        public List<User> findAll() {
            return new ArrayList<>(byId.values());
        }

        @Override
        public int count() {
            return byId.size();
        }

        @Override
        public boolean existsByUsername(String username, Long excludeId) {
            return byId.values().stream()
                    .anyMatch(user -> user.username().equalsIgnoreCase(username) && !Objects.equals(user.id(), excludeId));
        }

        @Override
        public int countActiveAdmins() {
            return (int) byId.values().stream().filter(user -> user.active() && user.role() == Role.ADMINISTRADOR).count();
        }

        @Override
        public User insert(User user, String passwordHash) {
            return add(user.name(), user.username(), user.role(), user.active());
        }

        @Override
        public void update(User user) {
            byId.put(user.id(), user);
        }

        @Override
        public void updatePasswordHash(long id, String passwordHash) {
            // Todas las cuentas de prueba usan PASSWORD; el hash no se guarda.
        }

        @Override
        public boolean hasOperations(long id) {
            return false;
        }

        @Override
        public void delete(long id) {
            byId.remove(id);
        }
    }

    public static final PasswordHasher HASHER = new PasswordHasher() {
        @Override
        public String hash(String rawPassword) {
            return "hash-falso";
        }

        @Override
        public boolean matches(String rawPassword, String passwordHash) {
            return PASSWORD.equals(rawPassword);
        }
    };

    public static UseCases useCases(Users users) {
        var settings = new InMemorySettings();
        var folios = new InMemoryFolioSeries();
        var payments = new InMemoryMembershipPayments();
        var sales = new InMemorySales();
        var methods = new InMemoryPaymentMethods();
        var plans = new InMemoryPlans();
        var transactions = new ImmediateTransactions();
        var authorizer = new Authorizer(users);
        Clock clock = Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());
        return new UseCases(
                new Authenticate(users, HASHER),
                new GetGymInfo(settings, clock),
                new GetSettings(settings, folios, payments, sales, authorizer),
                new UpdateSettings(settings, folios, payments, sales, transactions, authorizer),
                new ListPaymentMethods(methods, authorizer),
                new CreatePaymentMethod(methods, transactions, authorizer),
                new UpdatePaymentMethod(methods, transactions, authorizer),
                new SetPaymentMethodActive(methods, transactions, authorizer),
                new DeletePaymentMethod(methods, transactions, authorizer),
                new ListPlans(plans, authorizer),
                new PreviewPlanSavings(plans, authorizer),
                new CreatePlan(plans, transactions, authorizer),
                new UpdatePlan(plans, transactions, authorizer),
                new SetPlanActive(plans, transactions, authorizer),
                new DeletePlan(plans, transactions, authorizer));
    }

    /** Tema que no se guarda en ningún lado. */
    public static Themes themes(Themes.Theme initial) {
        Themes themes = new Themes(new Themes.Store() {
            @Override
            public Optional<String> load() {
                return Optional.of(initial.name());
            }

            @Override
            public void save(String value) {
            }
        });
        return themes;
    }
}
