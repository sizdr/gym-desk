package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;

import java.util.List;
import java.util.Optional;

public interface PaymentMethodRepository {
    /** Ordenados por nombre. */
    List<PaymentMethod> findAll(boolean includeInactive);

    Optional<PaymentMethod> findById(long id);

    /** Sin distinguir mayúsculas; {@code excludeId} (puede ser null) no cuenta, para editar sin chocar consigo mismo. */
    boolean existsByName(String name, Long excludeId);

    int countActive();

    /** @return el mismo método con el id asignado */
    PaymentMethod insert(PaymentMethod method);

    void update(PaymentMethod method);

    /** {@code true} si ya hay cobros con este método. */
    boolean isUsed(long id);

    void delete(long id);
}
