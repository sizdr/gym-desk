package dev.gimnasio.sistemagimnasio;

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
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.JdbiTransactionRunner;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresFolioSeriesRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresMembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresPaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresPlanRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresSaleRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresSettingsRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresUserRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.security.Argon2PasswordHasher;
import dev.gimnasio.sistemagimnasio.ui.common.UseCases;
import org.jdbi.v3.core.Jdbi;

import java.time.Clock;

/**
 * Raíz de composición: el único lugar que conoce a la vez la infraestructura y la UI.
 * Vive fuera de {@code ui/} porque la UI no puede depender de {@code infrastructure}.
 */
final class Wiring {
    private Wiring() {
    }

    static UseCases useCases(Jdbi jdbi, Clock clock) {
        var users = new PostgresUserRepository(jdbi);
        var settings = new PostgresSettingsRepository(jdbi);
        var folioSeries = new PostgresFolioSeriesRepository(jdbi);
        var payments = new PostgresMembershipPaymentRepository(jdbi);
        var sales = new PostgresSaleRepository(jdbi);
        var paymentMethods = new PostgresPaymentMethodRepository(jdbi);
        var plans = new PostgresPlanRepository(jdbi);
        var transactions = new JdbiTransactionRunner(jdbi);
        var authorizer = new Authorizer(users);

        return new UseCases(
                new Authenticate(users, new Argon2PasswordHasher()),
                new GetGymInfo(settings, clock),
                new GetSettings(settings, folioSeries, payments, sales, authorizer),
                new UpdateSettings(settings, folioSeries, payments, sales, transactions, authorizer),
                new ListPaymentMethods(paymentMethods, authorizer),
                new CreatePaymentMethod(paymentMethods, transactions, authorizer),
                new UpdatePaymentMethod(paymentMethods, transactions, authorizer),
                new SetPaymentMethodActive(paymentMethods, transactions, authorizer),
                new DeletePaymentMethod(paymentMethods, transactions, authorizer),
                new ListPlans(plans, authorizer),
                new PreviewPlanSavings(plans, authorizer),
                new CreatePlan(plans, transactions, authorizer),
                new UpdatePlan(plans, transactions, authorizer),
                new SetPlanActive(plans, transactions, authorizer),
                new DeletePlan(plans, transactions, authorizer));
    }
}
