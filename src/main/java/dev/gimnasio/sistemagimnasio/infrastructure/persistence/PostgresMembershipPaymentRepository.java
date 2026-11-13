package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.application.port.out.MembershipPaymentRepository;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.billing.MembershipPayment;
import dev.gimnasio.sistemagimnasio.domain.payment.Folio;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import org.jdbi.v3.core.Jdbi;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** {@code fecha_hora} es {@code timestamptz}: se guarda y se lee en la zona del equipo. */
public final class PostgresMembershipPaymentRepository implements MembershipPaymentRepository {
    private final Jdbi jdbi;
    private final ZoneId zone;

    public PostgresMembershipPaymentRepository(Jdbi jdbi) {
        this(jdbi, ZoneId.systemDefault());
    }

    public PostgresMembershipPaymentRepository(Jdbi jdbi, ZoneId zone) {
        this.jdbi = Objects.requireNonNull(jdbi, "jdbi");
        this.zone = Objects.requireNonNull(zone, "zone");
    }

    @Override
    public int count() {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT COUNT(*)
                        FROM pago
                        """)
                .mapTo(Integer.class)
                .one());
    }

    @Override
    public MembershipPayment insert(MembershipPayment payment, ChargeKind kind, String operationId) {
        long id = jdbi.withHandle(handle -> handle.createUpdate("""
                        INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, efectivo_recibido,
                                          fecha_hora, tipo_cobro, operacion_id)
                        VALUES (:folio, :socio, :usuario, :metodo, :monto, :recibido,
                                :fechaHora, CAST(:tipo AS tipo_cobro), :operacion)
                        """)
                .bind("folio", payment.folio().value())
                .bind("socio", payment.memberId())
                .bind("usuario", payment.userId())
                .bind("metodo", payment.paymentMethodId())
                .bind("monto", payment.amount().amount())
                .bind("recibido", payment.cashReceived() == null ? null : payment.cashReceived().amount())
                .bind("fechaHora", payment.dateTime().atZone(zone).toOffsetDateTime())
                .bind("tipo", kind.name().toLowerCase(Locale.ROOT))
                .bind("operacion", operationId)
                .executeAndReturnGeneratedKeys("id")
                .mapTo(Long.class)
                .one());
        return new MembershipPayment(id, payment.folio(), payment.dateTime(), payment.memberId(), payment.planId(),
                payment.paymentMethodId(), payment.amount(), payment.cashReceived(), payment.userId());
    }

    @Override
    public Optional<RecordedPayment> findById(long id) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT p.id, p.folio, p.fecha_hora, p.socio_id, m.plan_id, p.metodo_pago_id, p.monto,
                               p.efectivo_recibido, p.usuario_id, p.tipo_cobro::TEXT AS tipo_cobro
                        FROM pago AS p
                        JOIN membresia AS m ON m.pago_id = p.id
                        WHERE p.id = :id
                        """)
                .bind("id", id)
                .map((rs, ctx) -> {
                    BigDecimal received = rs.getBigDecimal("efectivo_recibido");
                    LocalDateTime dateTime = rs.getObject("fecha_hora", OffsetDateTime.class)
                            .atZoneSameInstant(zone).toLocalDateTime();
                    MembershipPayment payment = new MembershipPayment(
                            rs.getLong("id"),
                            new Folio(rs.getString("folio")),
                            dateTime,
                            rs.getLong("socio_id"),
                            rs.getLong("plan_id"),
                            rs.getLong("metodo_pago_id"),
                            new Money(rs.getBigDecimal("monto")),
                            received == null ? null : new Money(received),
                            rs.getLong("usuario_id"));
                    return new RecordedPayment(payment, ChargeKind.valueOf(rs.getString("tipo_cobro").toUpperCase(Locale.ROOT)));
                })
                .findOne());
    }

    @Override
    public Optional<Long> findIdByOperationId(String operationId) {
        return jdbi.withHandle(handle -> handle.createQuery("""
                        SELECT id
                        FROM pago
                        WHERE operacion_id = :operacion
                        """)
                .bind("operacion", operationId)
                .mapTo(Long.class)
                .findOne());
    }
}
