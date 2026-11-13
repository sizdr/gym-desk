package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryFolioSeries;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembers;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembershipPayments;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMemberships;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPaymentMethods;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPlans;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySettings;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.fakes.RecordingReceiptPrinter;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.member.SavingsStatus;
import dev.gimnasio.sistemagimnasio.domain.payment.FolioSeries;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BillingTest {
    static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 12, 9, 30);
    static final LocalDate TODAY = NOW.toLocalDate();

    final InMemoryUsers users = new InMemoryUsers();
    final InMemoryMembers members = new InMemoryMembers();
    final InMemoryMemberships memberships = new InMemoryMemberships();
    final InMemoryPlans plans = new InMemoryPlans();
    final InMemoryPaymentMethods methods = new InMemoryPaymentMethods();
    final InMemorySettings settings = new InMemorySettings();
    final InMemoryMembershipPayments payments = new InMemoryMembershipPayments();
    final InMemoryFolioSeries folioSeries = new InMemoryFolioSeries();
    final ImmediateTransactions transactions = new ImmediateTransactions();
    final Authorizer authorizer = new Authorizer(users);
    final Clock clock = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    final User caja = users.add(new User(null, "Lucía Pérez", "lucia", Role.CAJA, true), "h");
    final Plan mensual = plans.add("Mensual", 30, "450", null, true);
    final Plan trimestral = plans.add("Trimestral", 90, "1200", mensual.id(), true);
    final Plan viejo = plans.add("Plan viejo", 30, "300", null, false);
    final PaymentMethod efectivo = methods.add("Efectivo", true, true);
    final PaymentMethod tarjeta = methods.add("Tarjeta", false, true);
    final PaymentMethod vales = methods.add("Vales", false, false);
    final Member ana = members.add("Ana López", LocalDate.of(2026, 11, 3));

    final ListChargeOptions options = new ListChargeOptions(plans, methods, authorizer);
    final PreviewMembershipCharge preview = new PreviewMembershipCharge(members, memberships, plans, methods, settings,
            authorizer, clock);
    final ChargeMembership charge = new ChargeMembership(members, memberships, plans, methods, settings, payments,
            folioSeries, users, transactions, authorizer, clock);
    final GetMembershipReceipt receipt = new GetMembershipReceipt(payments, memberships, members, plans, methods, users,
            settings, authorizer);

    static ChargeMembershipCommand command(String op, Long member, Long plan, Long method, String cash, LocalDate start) {
        return new ChargeMembershipCommand(op, member, plan, method, cash == null ? null : Money.of(cash), start);
    }

    ChargeMembershipCommand byCard(String op, Plan plan) {
        return command(op, ana.id(), plan.id(), tarjeta.id(), null, null);
    }

    /** Ana con una membresía que vence en {@code end}. */
    void anaPaidUntil(LocalDate end) {
        memberships.add(ana.id(), mensual.id(), end.minusDays(30), end);
    }

    @Nested
    class Opciones {
        @Test
        void soloPlanesYMetodosActivos_conElAhorroDeCadaPlan() {
            ChargeOptions result = options.execute(caja);

            assertEquals(List.of("Mensual", "Trimestral"), result.plans().stream().map(PlanOption::name).toList());
            assertEquals(SavingsStatus.AHORRO, result.plans().get(1).savings().status());
            assertEquals(Money.of("150"), result.plans().get(1).savings().amount());
            assertEquals(List.of(PaymentMethodItem.of(efectivo), PaymentMethodItem.of(tarjeta)), result.paymentMethods());
        }
    }

    @Nested
    class VistaPrevia {
        @Test
        void sinDatos_noLanza_yPideSocioPlanYMetodo() {
            ChargePreview result = preview.execute(caja, command(null, null, null, null, null, null));

            assertEquals(List.of("memberId", "planId", "paymentMethodId"), List.copyOf(result.errors().keySet()));
            assertEquals(Money.ZERO, result.total());
            assertFalse(result.ready());
            assertNull(result.member());
        }

        @Test
        void completa_calculaFechasTotalYCambio() {
            ChargePreview result = preview.execute(caja,
                    command(null, ana.id(), trimestral.id(), efectivo.id(), "1500", null));

            assertTrue(result.ready());
            assertEquals(MemberStatus.SIN_MEMBRESIA, result.member().status());
            assertEquals(ChargeKind.PRIMERA, result.kind());
            assertEquals(TODAY, result.newStart());
            assertEquals(TODAY.plusDays(90), result.newEnd());
            assertEquals(Money.of("1200"), result.total());
            assertEquals(Money.of("300"), result.change());
            assertNull(result.missing());
            assertEquals("Trimestral", result.plan().name());
        }

        @Test
        void efectivoInsuficiente_diceCuantoFalta() {
            ChargePreview result = preview.execute(caja, command(null, ana.id(), mensual.id(), efectivo.id(), "400", null));

            assertEquals(Map.of("cashReceived", "El efectivo recibido no cubre el total."), result.errors());
            assertEquals(Money.of("50"), result.missing());
            assertNull(result.change());
        }

        @Test
        void conOtroMetodo_loRecibidoSeIgnora() {
            ChargePreview result = preview.execute(caja, command(null, ana.id(), mensual.id(), tarjeta.id(), "10", null));
            assertTrue(result.ready());
            assertNull(result.cashReceived());
        }

        @Test
        void planOMetodoInactivos_yEfectivoSinCapturar() {
            ChargePreview result = preview.execute(caja, command(null, 99L, viejo.id(), vales.id(), null, null));

            assertEquals("El socio no existe.", result.errors().get("memberId"));
            assertEquals("Ese plan está inactivo; elige otro.", result.errors().get("planId"));
            assertEquals("Ese método de pago ya no está disponible.", result.errors().get("paymentMethodId"));
            assertEquals("Escribe cuánto efectivo recibiste.", preview.execute(caja,
                    command(null, ana.id(), mensual.id(), efectivo.id(), null, null)).errors().get("cashReceived"));
        }
    }

    @Nested
    class InicioSegunElTipoDeCobro {
        @Test
        void primera_empiezaElDiaElegido_peroNoAntesDeLaFechaDeAlta() {
            LocalDate chosen = LocalDate.of(2026, 11, 5);
            assertEquals(chosen, preview.execute(caja, command(null, ana.id(), mensual.id(), tarjeta.id(), null, chosen)).newStart());

            ChargePreview early = preview.execute(caja,
                    command(null, ana.id(), mensual.id(), tarjeta.id(), null, LocalDate.of(2026, 11, 2)));
            assertEquals(Map.of("start", "El inicio no puede ser anterior a la fecha de alta (03/11/2026)."), early.errors());
        }

        @Test
        void renovacionAnticipada_empiezaCuandoTerminaLaActual_eIgnoraElInicioElegido() {
            anaPaidUntil(TODAY.plusDays(10));

            ChargePreview result = preview.execute(caja,
                    command(null, ana.id(), mensual.id(), tarjeta.id(), null, LocalDate.of(2026, 11, 5)));

            assertEquals(ChargeKind.RENOVACION_ANTICIPADA, result.kind());
            assertEquals(TODAY.plusDays(10), result.newStart());
            assertEquals(TODAY.plusDays(40), result.newEnd());
        }

        @Test
        void renovacionVencida_empiezaHoy() {
            anaPaidUntil(TODAY.minusDays(5));
            ChargePreview result = preview.execute(caja, byCard(null, mensual));
            assertEquals(ChargeKind.RENOVACION_VENCIDA, result.kind());
            assertEquals(TODAY, result.newStart());
            assertEquals(MemberStatus.VENCIDA, result.member().status());
        }

        @Test
        void reactivacion_cuandoEstabaEnBaja_empiezaHoy() {
            anaPaidUntil(TODAY.minusDays(30));
            ChargePreview result = preview.execute(caja, byCard(null, mensual));
            assertEquals(ChargeKind.REACTIVACION, result.kind());
            assertEquals(TODAY, result.newStart());
        }
    }

    @Nested
    class Cobrar {
        @Test
        void guardaPagoYMembresia_conElFolioSiguiente_yDevuelveElCobroCompleto() {
            ChargeMembershipResult result = charge.execute(caja,
                    command("op-1", ana.id(), mensual.id(), efectivo.id(), "500", null));

            PaymentRow row = result.payment();
            assertEquals("GYM-0001", row.folio());
            assertEquals(NOW, row.dateTime());
            assertEquals("Ana López", row.memberName());
            assertEquals("Mensual", row.planName());
            assertEquals(TODAY, row.start());
            assertEquals(TODAY.plusDays(30), row.end());
            assertEquals(ChargeKind.PRIMERA, row.kind());
            assertEquals("Efectivo", row.paymentMethodName());
            assertEquals(Money.of("450"), row.amount());
            assertEquals("Lucía Pérez", row.cashierName());
            assertEquals(30, result.durationDays());
            assertEquals(Money.of("500"), result.cashReceived());
            assertEquals(Money.of("50"), result.change());
            assertFalse(result.repeated());

            Membership saved = memberships.all().getFirst();
            assertEquals(new Membership(saved.id(), ana.id(), mensual.id(), row.id(), TODAY, TODAY.plusDays(30)), saved);
            assertEquals(ChargeKind.PRIMERA, payments.get(row.id()).kind());
        }

        @Test
        void laMismaOperacionDosVeces_devuelveElCobroOriginal_sinCobrarOtraVez() {
            ChargeMembershipResult first = charge.execute(caja, byCard("op-1", mensual));
            ChargeMembershipResult second = charge.execute(caja, byCard("op-1", trimestral));

            assertTrue(second.repeated());
            assertEquals(first.payment(), second.payment());
            assertEquals(1, payments.stored());
            assertEquals(1, memberships.all().size());
            assertEquals(1, folioSeries.last(FolioSeries.MEMBERSHIP_PAYMENTS));
        }

        @Test
        void operacionesDistintas_tomanFoliosSeguidos_yLaSegundaRenuevaDesdeElFinDeLaPrimera() {
            charge.execute(caja, byCard("op-1", mensual));
            PaymentRow second = charge.execute(caja, byCard("op-2", mensual)).payment();

            assertEquals("GYM-0002", second.folio());
            assertEquals(ChargeKind.RENOVACION_ANTICIPADA, second.kind());
            assertEquals(TODAY.plusDays(30), second.start());
        }

        @Test
        void conErrores_noGuarda_niGastaFolio() {
            Map<String, String> errors = assertThrows(ValidationException.class,
                    () -> charge.execute(caja, command("op-1", ana.id(), null, efectivo.id(), null, null))).errors();

            assertEquals(List.of("planId", "cashReceived"), List.copyOf(errors.keySet()));
            assertEquals(0, payments.stored());
            assertEquals(0, folioSeries.last(FolioSeries.MEMBERSHIP_PAYMENTS));
        }

        @Test
        void unSocioQueYaNoExiste_seRechazaComoNoEncontrado() {
            NotFoundException e = assertThrows(NotFoundException.class,
                    () -> charge.execute(caja, command("op-1", 99L, mensual.id(), tarjeta.id(), null, null)));
            assertEquals("El socio no existe.", e.getMessage());
        }

        @Test
        void sinOperationId_seRechaza() {
            assertEquals(List.of("operationId"), List.copyOf(assertThrows(ValidationException.class,
                    () -> charge.execute(caja, byCard("  ", mensual))).errors().keySet()));
        }

        @Test
        void todoOcurreDentroDeUnaTransaccion() {
            users.guard.watch(transactions);
            members.guard.watch(transactions);
            memberships.guard.watch(transactions);
            plans.guard.watch(transactions);
            settings.guard.watch(transactions);
            payments.guard.watch(transactions);
            folioSeries.guard.watch(transactions);

            charge.execute(caja, byCard("op-1", mensual));

            assertEquals(1, transactions.started);
        }

        @Test
        void unUsuarioDesactivado_noCobra() {
            User inactive = users.add(new User(null, "Pedro", "pedro", Role.RECEPCION, false), "h");
            assertThrows(DomainException.class, () -> charge.execute(inactive, byCard("op-1", mensual)));
            assertThrows(DomainException.class, () -> preview.execute(inactive, byCard(null, mensual)));
            assertThrows(DomainException.class, () -> options.execute(inactive));
            assertEquals(0, payments.stored());
        }
    }

    @Nested
    class Recibo {
        @Test
        void traeElEncabezadoDelGimnasioYElCobro() {
            ChargeMembershipResult paid = charge.execute(caja,
                    command("op-1", ana.id(), mensual.id(), efectivo.id(), "500", null));

            MembershipReceipt result = receipt.execute(caja, paid.payment().id());

            assertEquals("Mi gimnasio", result.header().gymName());
            assertEquals(InMemorySettings.MXN, result.header().currency());
            assertEquals(paid.payment(), result.payment());
            assertEquals(30, result.durationDays());
            assertEquals(Money.of("50"), result.change());
        }

        @Test
        void unCobroQueNoExiste_seRechaza() {
            assertEquals("El cobro no existe.",
                    assertThrows(NotFoundException.class, () -> receipt.execute(caja, 99)).getMessage());
        }

        @Test
        void imprimir_mandaElMismoReciboALaImpresora() {
            RecordingReceiptPrinter printer = new RecordingReceiptPrinter();
            long id = charge.execute(caja, byCard("op-1", mensual)).payment().id();

            new PrintMembershipReceipt(receipt, printer).execute(caja, id);

            assertEquals(List.of(receipt.execute(caja, id)).getFirst().payment(), printer.printed.getFirst().payment());
        }
    }
}
