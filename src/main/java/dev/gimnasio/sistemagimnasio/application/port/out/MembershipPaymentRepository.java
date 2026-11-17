package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.billing.MembershipPayment;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MembershipPaymentRepository {
    /** Cobros registrados. */
    int count();

    /**
     * @param operationId el que generó la UI al abrir el formulario; no se repite entre cobros
     * @return el mismo cobro con el id asignado
     */
    MembershipPayment insert(MembershipPayment payment, ChargeKind kind, String operationId);

    Optional<RecordedPayment> findById(long id);

    /** El id del cobro que ya se registró con esa operación, si existe. */
    Optional<Long> findIdByOperationId(String operationId);

    /** Los ids de los últimos {@code limit} cobros, el más reciente primero. */
    List<Long> findRecentIds(int limit);

    /** Cuántos cobros hubo de {@code from} (incluido) a {@code to} (excluido) y cuánto sumaron. */
    Totals totalsBetween(LocalDateTime from, LocalDateTime to);

    record Totals(int count, Money amount) {
    }

    /** Un cobro guardado con el tipo que tuvo al registrarse. */
    record RecordedPayment(MembershipPayment payment, ChargeKind kind) {
    }
}
