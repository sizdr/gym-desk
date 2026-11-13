package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.billing.MembershipPayment;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class InMemoryMembershipPayments implements MembershipPaymentRepository {
    /** Cobros que «ya existían» sin guardarse aquí, para probar el bloqueo de moneda. */
    public int count;
    public int inserts;
    private final Map<Long, RecordedPayment> byId = new LinkedHashMap<>();
    private final Map<String, Long> byOperation = new HashMap<>();
    private long nextId = 1;
    public final TransactionGuard guard = new TransactionGuard();

    @Override
    public int count() {
        guard.check("count");
        return count + byId.size();
    }

    @Override
    public MembershipPayment insert(MembershipPayment payment, ChargeKind kind, String operationId) {
        guard.check("insert");
        if (byOperation.containsKey(operationId)) {
            throw new IllegalStateException("operacion_id repetido: " + operationId);
        }
        inserts++;
        MembershipPayment saved = new MembershipPayment(nextId++, payment.folio(), payment.dateTime(),
                payment.memberId(), payment.planId(), payment.paymentMethodId(), payment.amount(),
                payment.cashReceived(), payment.userId());
        byId.put(saved.id(), new RecordedPayment(saved, kind));
        byOperation.put(operationId, saved.id());
        return saved;
    }

    @Override
    public Optional<RecordedPayment> findById(long id) {
        guard.check("findById");
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public Optional<Long> findIdByOperationId(String operationId) {
        guard.check("findIdByOperationId");
        return Optional.ofNullable(byOperation.get(operationId));
    }

    public RecordedPayment get(long id) {
        return byId.get(id);
    }

    public int stored() {
        return byId.size();
    }
}
