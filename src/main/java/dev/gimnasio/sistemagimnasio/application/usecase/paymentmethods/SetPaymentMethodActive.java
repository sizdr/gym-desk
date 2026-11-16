package dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods;

import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/**
 * Desactivar no cambia los cobros anteriores. Contar los activos y desactivar van en la misma
 * transacción, para que dos estaciones no dejen el catálogo sin ninguno.
 */
public final class SetPaymentMethodActive {
    private final PaymentMethodRepository methods;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public SetPaymentMethodActive(PaymentMethodRepository methods, TransactionRunner transactions,
                                  Authorizer authorizer) {
        this.methods = Objects.requireNonNull(methods, "methods");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public PaymentMethodItem execute(User actor, long id, boolean active) {
        return transactions.inTransaction(() -> {
            authorizer.require(actor, Permission.MANAGE_SETTINGS);
            PaymentMethod current = PaymentMethodRules.find(methods, id);
            if (current.active() == active) {
                return PaymentMethodRules.toItem(current);
            }
            if (!active && methods.countActive() <= 1) {
                throw new DomainException(PaymentMethodRules.LAST_ACTIVE);
            }
            PaymentMethod updated = new PaymentMethod(id, current.name(), current.cash(), active);
            methods.update(updated);
            return PaymentMethodRules.toItem(updated);
        });
    }
}
