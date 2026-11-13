package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.FolioSeriesRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.billing.MembershipPayment;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Optional;

/**
 * Cobra una membresía. Tomar el folio, guardar el pago y guardar la membresía van en una sola transacción: si algo
 * falla no queda nada a medias ni se pierde el número de folio. Con un {@code operationId} ya registrado devuelve el
 * cobro original con {@code repeated = true} en lugar de cobrar otra vez.
 */
public final class ChargeMembership {
    static final String OPERATION_REQUIRED = "Falta el identificador de la operación; vuelve a abrir el formulario.";

    private final ChargeDraft.Sources sources;
    private final MembershipPaymentRepository payments;
    private final MembershipRepository memberships;
    private final FolioSeriesRepository folioSeries;
    private final PaymentReader reader;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;
    private final Clock clock;

    public ChargeMembership(MemberRepository members, MembershipRepository memberships, PlanRepository plans,
                            PaymentMethodRepository methods, SettingsRepository settings,
                            MembershipPaymentRepository payments, FolioSeriesRepository folioSeries,
                            UserRepository users, TransactionRunner transactions, Authorizer authorizer,
                            Clock clock) {
        this.sources = new ChargeDraft.Sources(members, memberships, plans, methods, settings);
        this.payments = Objects.requireNonNull(payments, "payments");
        this.memberships = memberships;
        this.folioSeries = Objects.requireNonNull(folioSeries, "folioSeries");
        this.reader = new PaymentReader(payments, memberships, members, plans, methods, users);
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public ChargeMembershipResult execute(User actor, ChargeMembershipCommand command) {
        return transactions.inTransaction(() -> {
            User actingUser = authorizer.require(actor, Permission.CHARGE_MEMBERSHIP);
            String operationId = command.operationId() == null ? "" : command.operationId().strip();
            if (operationId.isEmpty()) {
                throw ValidationException.of("operationId", OPERATION_REQUIRED);
            }
            Optional<Long> previous = payments.findIdByOperationId(operationId);
            if (previous.isPresent()) {
                return result(previous.get(), true);
            }

            LocalDateTime now = LocalDateTime.now(clock);
            ChargeDraft draft = ChargeDraft.evaluate(command, now.toLocalDate(), sources);
            if (draft.memberMissing()) {
                throw new NotFoundException(ChargeDraft.MEMBER_NOT_FOUND);
            }
            if (!draft.errors().isEmpty()) {
                throw new ValidationException(draft.errors());
            }

            MembershipPayment payment = MembershipPayment.charge(folioSeries.next(FolioSeries.MEMBERSHIP_PAYMENTS),
                    now, draft.member().id(), draft.plan(), draft.method(), draft.cashReceived(), actingUser.id());
            MembershipPayment saved = payments.insert(payment, draft.kind(), operationId);
            memberships.insert(Membership.begin(draft.member().id(), draft.plan(), saved.id(), draft.start()));
            return result(saved.id(), false);
        });
    }

    private ChargeMembershipResult result(long paymentId, boolean repeated) {
        PaymentReader.View view = reader.read(paymentId)
                .orElseThrow(() -> new IllegalStateException("No se encontró el cobro " + paymentId + "."));
        return new ChargeMembershipResult(view.row(), view.durationDays(), view.cashReceived(), view.change(),
                repeated);
    }
}
