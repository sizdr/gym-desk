package dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods;

import dev.gimnasio.sistemagimnasio.application.port.out.PaymentMethodRepository;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;

/** Lo que comparten los casos de uso del catálogo de métodos de pago. */
final class PaymentMethodRules {
    static final String NAME_REQUIRED = "Escribe el nombre.";
    static final String NAME_TOO_LONG = "Usa " + PaymentMethod.MAX_NAME_LENGTH + " caracteres o menos.";
    static final String NAME_TAKEN = "Ya existe uno con ese nombre.";
    static final String NOT_FOUND = "El método de pago ya no existe. Recarga la pantalla.";
    static final String LAST_ACTIVE = "Deja al menos un método de pago.";

    private PaymentMethodRules() {
    }

    /** @param excludeId el propio método al editarlo; null al crear */
    static String validName(PaymentMethodRepository methods, String rawName, Long excludeId) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) {
            throw ValidationException.of("name", NAME_REQUIRED);
        }
        if (!PaymentMethod.isValidName(name)) {
            throw ValidationException.of("name", NAME_TOO_LONG);
        }
        if (methods.existsByName(name, excludeId)) {
            throw ValidationException.of("name", NAME_TAKEN);
        }
        return name;
    }

    static PaymentMethod find(PaymentMethodRepository methods, long id) {
        return methods.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    static PaymentMethodItem toItem(PaymentMethod method) {
        return PaymentMethodItem.of(method);
    }
}
