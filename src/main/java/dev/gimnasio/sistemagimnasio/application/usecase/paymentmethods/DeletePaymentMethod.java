package dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods;

import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

/** Solo se borra un método que nunca se usó; uno con cobros se desactiva para conservar el historial. */
public final class DeletePaymentMethod {
    private final PaymentMethodRepository methods;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public DeletePaymentMethod(PaymentMethodRepository methods, TransactionRunner transactions, Authorizer authorizer) {
        this.methods = Objects.requireNonNull(methods, "methods");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    public void execute(User actor, long id) {
        transactions.run(() -> {
            authorizer.require(actor, Permission.MANAGE_SETTINGS);
            PaymentMethod method = PaymentMethodRules.find(methods, id);
            if (methods.isUsed(id)) {
                throw new DomainException("No puedes eliminar «" + method.name()
                        + "»: ya se usó en cobros. Desactívalo para dejar de ofrecerlo.");
            }
            if (method.active() && methods.countActive() <= 1) {
                throw new DomainException(PaymentMethodRules.LAST_ACTIVE);
            }
            methods.delete(id);
        });
    }
}
