package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** Planes activos (del más corto al más largo, con su ahorro) y métodos de pago activos (por nombre). */
public final class ListChargeOptions {
    private final PlanRepository plans;
    private final PaymentMethodRepository methods;
    private final Authorizer authorizer;

    public ListChargeOptions(PlanRepository plans, PaymentMethodRepository methods, Authorizer authorizer) {
        this.plans = Objects.requireNonNull(plans, "plans");
        this.methods = Objects.requireNonNull(methods, "methods");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public ChargeOptions execute(User actor) {
        authorizer.require(actor, Permission.CHARGE_MEMBERSHIP);
        return new ChargeOptions(PlanOptions.active(plans),
                methods.findAll(false).stream().map(PaymentMethodItem::of).toList());
    }
}
