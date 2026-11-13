package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.UserRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** El recibo de un cobro ya registrado, con el encabezado actual del gimnasio. */
public final class GetMembershipReceipt {
    static final String NOT_FOUND = "El cobro no existe.";

    private final PaymentReader reader;
    private final SettingsRepository settings;
    private final Authorizer authorizer;

    public GetMembershipReceipt(MembershipPaymentRepository payments, MembershipRepository memberships,
                                MemberRepository members, PlanRepository plans, PaymentMethodRepository methods,
                                UserRepository users, SettingsRepository settings, Authorizer authorizer) {
        this.reader = new PaymentReader(payments, memberships, members, plans, methods, users);
        this.settings = Objects.requireNonNull(settings, "settings");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public MembershipReceipt execute(User actor, long paymentId) {
        authorizer.require(actor, Permission.CHARGE_MEMBERSHIP);
        PaymentReader.View view = reader.read(paymentId).orElseThrow(() -> new NotFoundException(NOT_FOUND));
        return new MembershipReceipt(GymHeader.of(settings.get()), view.row(), view.durationDays(),
                view.cashReceived(), view.change());
    }
}
