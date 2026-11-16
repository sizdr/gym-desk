package dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods;

import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.TransactionRunner;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.User;

import java.util.Objects;

public final class UpdatePaymentMethod {
    private final PaymentMethodRepository methods;
    private final TransactionRunner transactions;
    private final Authorizer authorizer;

    public UpdatePaymentMethod(PaymentMethodRepository methods, TransactionRunner transactions, Authorizer authorizer) {
        this.methods = Objects.requireNonNull(methods, "methods");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.authorizer = Objects.requireNonNull(authorizer, "authorizer");
    }

    /**
     * Cambia nombre y si es efectivo; no cambia si está activo. Si ya hay cobros, «es efectivo» queda
     * fijo: los registros anteriores no copian ese dato y cambiarlo los reclasificaría.
     */
    public PaymentMethodItem execute(User actor, long id, PaymentMethodCommand command) {
        return transactions.inTransaction(() -> {
            authorizer.require(actor, Permission.MANAGE_SETTINGS);
            PaymentMethod current = PaymentMethodRules.find(methods, id);
            String name = PaymentMethodRules.validName(methods, command.name(), id);
            if (current.cash() != command.cash() && methods.isUsed(id)) {
                throw ValidationException.of("cash", "No puedes cambiar si «" + current.name()
                        + "» es efectivo: ya se usó en cobros. Crea un método nuevo y desactiva este.");
            }
            PaymentMethod updated = new PaymentMethod(id, name, command.cash(), current.active());
            methods.update(updated);
            return PaymentMethodRules.toItem(updated);
        });
    }
}
