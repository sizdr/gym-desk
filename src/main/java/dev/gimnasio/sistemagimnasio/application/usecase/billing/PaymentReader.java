package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository.RecordedPayment;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.domain.billing.MembershipPayment;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Optional;

/**
 * Arma un cobro guardado con los nombres de socio, plan, método y cajero. El periodo y su duración salen de la
 * membresía que generó: cambiar después la duración del plan no cambia lo que se pagó.
 */
final class PaymentReader {
    private final MembershipPaymentRepository payments;
    private final MembershipRepository memberships;
    private final MemberRepository members;
    private final PlanRepository plans;
    private final PaymentMethodRepository methods;
    private final UserRepository users;

    PaymentReader(MembershipPaymentRepository payments, MembershipRepository memberships,
                         MemberRepository members, PlanRepository plans, PaymentMethodRepository methods,
                         UserRepository users) {
        this.payments = Objects.requireNonNull(payments, "payments");
        this.memberships = Objects.requireNonNull(memberships, "memberships");
        this.members = Objects.requireNonNull(members, "members");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.methods = Objects.requireNonNull(methods, "methods");
        this.users = Objects.requireNonNull(users, "users");
    }

    /** Vacío si el cobro no existe. */
    Optional<View> read(long paymentId) {
        Optional<RecordedPayment> recorded = payments.findById(paymentId);
        if (recorded.isEmpty()) {
            return Optional.empty();
        }
        MembershipPayment payment = recorded.get().payment();
        Membership membership = memberships.findByPayment(paymentId)
                .orElseThrow(() -> new IllegalStateException("El cobro " + paymentId + " no tiene membresía."));
        PaymentRow row = new PaymentRow(
                payment.id(),
                payment.folio().value(),
                payment.dateTime(),
                payment.memberId(),
                members.findById(payment.memberId()).map(Member::fullName).orElse(""),
                plans.findById(membership.planId()).map(Plan::name).orElse(""),
                membership.start(),
                membership.end(),
                recorded.get().kind(),
                methods.findById(payment.paymentMethodId()).map(PaymentMethod::name).orElse(""),
                payment.amount(),
                users.findById(payment.userId()).map(User::name).orElse(""));
        int durationDays = (int) ChronoUnit.DAYS.between(membership.start(), membership.end());
        return Optional.of(new View(row, durationDays, payment.cashReceived(), payment.change().orElse(null)));
    }

    record View(PaymentRow row, int durationDays, Money cashReceived, Money change) {
    }
}
