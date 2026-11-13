package dev.gimnasio.sistemagimnasio.application.port.out;

import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.billing.MembershipPayment;

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

    /** Un cobro guardado con el tipo que tuvo al registrarse. */
    record RecordedPayment(MembershipPayment payment, ChargeKind kind) {
    }
}
