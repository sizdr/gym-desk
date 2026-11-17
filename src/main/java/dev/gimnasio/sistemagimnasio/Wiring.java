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
import dev.gimnasio.sistemagimnasio.application.usecase.users.CreateInitialAdmin;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.JdbiTransactionRunner;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresFolioSeriesRepository;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembership;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.GetMembershipReceipt;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ListChargeOptions;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PreviewMembershipCharge;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PrintMembershipReceipt;
import dev.gimnasio.sistemagimnasio.application.usecase.members.DeleteMember;
import dev.gimnasio.sistemagimnasio.infrastructure.printing.TextFileReceiptPrinter;
import dev.gimnasio.sistemagimnasio.application.usecase.members.RegisterMember;
import dev.gimnasio.sistemagimnasio.application.usecase.members.SearchMembers;
import dev.gimnasio.sistemagimnasio.application.usecase.summary.GetDaySummary;
import dev.gimnasio.sistemagimnasio.application.usecase.members.UpdateMember;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresMemberRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresMembershipRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresMembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresPaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.infrastructure.persistence.PostgresPlanRepository;
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

    /** Crea el primer administrador si la base no tiene usuarios; lo usa el arranque antes de mostrar el acceso. */
    static CreateInitialAdmin createInitialAdmin(Jdbi jdbi) {
        return new CreateInitialAdmin(new PostgresUserRepository(jdbi), new Argon2PasswordHasher(),
                new JdbiTransactionRunner(jdbi));
    }

    /**
     * Carpeta donde se escriben los recibos (uno por folio, en texto). Se cambia con GYM_DESK_RECEIPTS_DIR; por
     * defecto, ~/.gym-desk/recibos.
     */
    static java.nio.file.Path receiptsDirectory() {
        String configured = System.getenv("GYM_DESK_RECEIPTS_DIR");
        return configured != null && !configured.isBlank() ? java.nio.file.Path.of(configured)
                : java.nio.file.Path.of(System.getProperty("user.home"), ".gym-desk", "recibos");
    }

    static UseCases useCases(Jdbi jdbi, Clock clock) {
        var users = new PostgresUserRepository(jdbi);
        var settings = new PostgresSettingsRepository(jdbi);
        var folioSeries = new PostgresFolioSeriesRepository(jdbi);
        var payments = new PostgresMembershipPaymentRepository(jdbi);
        var paymentMethods = new PostgresPaymentMethodRepository(jdbi);
        var plans = new PostgresPlanRepository(jdbi);
        var members = new PostgresMemberRepository(jdbi);
        var memberships = new PostgresMembershipRepository(jdbi);
        var transactions = new JdbiTransactionRunner(jdbi);
        var authorizer = new Authorizer(users);
        var receipt = new GetMembershipReceipt(payments, memberships, members, plans, paymentMethods, users, settings,
                authorizer);

        return new UseCases(
                new Authenticate(users, new Argon2PasswordHasher()),
                new GetGymInfo(settings, clock),
                new GetSettings(settings, folioSeries, payments, authorizer),
                new UpdateSettings(settings, folioSeries, payments, transactions, authorizer),
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
                new DeletePlan(plans, transactions, authorizer),
                new SearchMembers(members, memberships, plans, settings, authorizer, clock),
                new RegisterMember(members, memberships, plans, settings, transactions, authorizer, clock),
                new UpdateMember(members, memberships, plans, settings, transactions, authorizer, clock),
                new DeleteMember(members, transactions, authorizer),
                new ListChargeOptions(plans, paymentMethods, authorizer),
                new PreviewMembershipCharge(members, memberships, plans, paymentMethods, settings, authorizer, clock),
                new ChargeMembership(members, memberships, plans, paymentMethods, settings, payments, folioSeries, users,
                        transactions, authorizer, clock),
                receipt,
                new PrintMembershipReceipt(receipt, new TextFileReceiptPrinter(receiptsDirectory())),
                new GetDaySummary(members, memberships, plans, settings, payments, paymentMethods, users, authorizer,
                        clock));
    }
}
