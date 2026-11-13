package dev.gimnasio.sistemagimnasio.application.usecase.plans;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPlans;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.member.SavingsStatus;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlansTest {
    private final InMemoryPlans plans = new InMemoryPlans();
    private final InMemoryUsers users = new InMemoryUsers();
    private final ImmediateTransactions transactions = new ImmediateTransactions();
    private final Authorizer authorizer = new Authorizer(users);
    private final User admin = users.admin();

    private final ListPlans list = new ListPlans(plans, authorizer);
    private final PreviewPlanSavings preview = new PreviewPlanSavings(plans, authorizer);
    private final CreatePlan create = new CreatePlan(plans, transactions, authorizer);
    private final UpdatePlan update = new UpdatePlan(plans, transactions, authorizer);
    private final SetPlanActive setActive = new SetPlanActive(plans, transactions, authorizer);
    private final DeletePlan delete = new DeletePlan(plans, transactions, authorizer);

    private final Plan mensual = plans.add("Mensual", 30, "450", null, true);

    private static Map<String, String> errors(Executable operation) {
        return assertThrows(ValidationException.class, operation).errors();
    }

    private static PlanCommand command(String name, Integer days, String price, Long reference) {
        return new PlanCommand(name, days, price == null ? null : Money.of(price), reference);
    }

    @Nested
    class Listar {
        @Test
        void primeroLosActivos_luegoPorDuracionYNombre() {
            plans.add("Anual", 365, "3800", mensual.id(), true);
            plans.add("Día de prueba", 1, "60", null, false);
            plans.add("Estudiante", 30, "350", null, true);
            plans.add("Bianual", 730, "7000", null, false);

            assertEquals(List.of("Estudiante", "Mensual", "Anual", "Día de prueba", "Bianual"),
                    list.execute(admin).stream().map(PlanRow::name).toList());
        }

        @Test
        void cadaFila_traeSuReferenciaYSuAhorro() {
            Plan anual = plans.add("Anual", 365, "3800", mensual.id(), true);

            PlanRow row = list.execute(admin).stream().filter(r -> r.id() == anual.id()).findFirst().orElseThrow();

            assertEquals(mensual.id(), row.referenceMonthlyPlanId());
            assertEquals("Mensual", row.referenceMonthlyPlanName());
            assertEquals(new SavingsView(SavingsStatus.AHORRO, Money.of("1600"), 12, "Mensual", Money.of("5400"), 30, null),
                    row.savings());
        }
    }

    @Nested
    class VistaPrevia {
        @Test
        void calculaElAhorroConLosDatosDelFormulario() {
            assertEquals(SavingsStatus.AHORRO, preview.execute(admin, command("Trimestral", 90, "1200", mensual.id())).status());
        }

        @Test
        void sinDuracion_oDeUnMesOMenos_noAplica() {
            assertEquals(SavingsStatus.NO_APLICA, preview.execute(admin, command(null, null, null, null)).status());
            assertEquals(SavingsStatus.NO_APLICA, preview.execute(admin, command("x", 30, "450", mensual.id())).status());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"0"})
        void sinPrecio_noEstaDisponible_yNoLanza(String price) {
            PlanCommand command = command("Trimestral", 90, price == null || price.isEmpty() ? null : price, mensual.id());
            SavingsView view = preview.execute(admin, command);
            assertEquals(SavingsStatus.NO_DISPONIBLE, view.status());
            assertEquals("escribe un precio mayor que cero para calcular el ahorro", view.reason());
        }

        @Test
        void unaReferenciaInexistente_cuentaComoSinReferencia() {
            assertEquals("el plan no tiene un plan mensual de referencia",
                    preview.execute(admin, command("Trimestral", 90, "1200", 99L)).reason());
        }
    }

    @Nested
    class Crear {
        @Test
        void quedaActivo_conElNombreRecortado_yLaFilaCompleta() {
            PlanRow row = create.execute(admin, command("  Trimestral ", 90, "1200", mensual.id()));

            assertEquals("Trimestral", row.name());
            assertTrue(row.active());
            assertEquals("Mensual", row.referenceMonthlyPlanName());
            assertEquals(Money.of("150"), row.savings().amount());
            assertEquals(new Plan(row.id(), "Trimestral", 90, Money.of("1200"), mensual.id(), true), plans.get(row.id()));
        }

        @Test
        void sinReferencia_seCrea() {
            PlanRow row = create.execute(admin, command("Estudiante", 30, "350", null));
            assertNull(row.referenceMonthlyPlanId());
            assertEquals(SavingsStatus.NO_APLICA, row.savings().status());
        }

        @Test
        void todosLosErrores_juntos_yEnOrden_sinGuardar() {
            Map<String, String> errors = errors(() -> create.execute(admin, command(" ", 0, "0", 99L)));

            assertEquals(List.of("name", "durationDays", "price", "referenceMonthlyPlanId"), List.copyOf(errors.keySet()));
            assertEquals("Escribe el nombre del plan.", errors.get("name"));
            assertEquals("Escribe la duración en días, de 1 a 730.", errors.get("durationDays"));
            assertEquals("Escribe un precio mayor que cero, por ejemplo 450.00.", errors.get("price"));
            assertEquals("Elige un plan de 30 días distinto de este.", errors.get("referenceMonthlyPlanId"));
            assertEquals(0, plans.writes);
        }

        @Test
        void camposAusentes_seReportan() {
            assertEquals(List.of("name", "durationDays", "price"),
                    List.copyOf(errors(() -> create.execute(admin, command(null, null, null, null))).keySet()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"Mensual", "MENSUAL", " mensual "})
        void nombreRepetido_sinDistinguirMayusculas_seRechaza(String name) {
            assertEquals(Map.of("name", "Ya existe un plan con ese nombre."),
                    errors(() -> create.execute(admin, command(name, 30, "500", null))));
        }

        @Test
        void nombreDeMasDe60Caracteres_seRechaza_y60SeAcepta() {
            assertEquals(Map.of("name", "Usa 60 caracteres o menos."),
                    errors(() -> create.execute(admin, command("x".repeat(61), 30, "500", null))));
            assertEquals(60, create.execute(admin, command("x".repeat(60), 30, "500", null)).name().length());
        }

        @ParameterizedTest
        @ValueSource(ints = {-1, 0, 731})
        void duracionFueraDe1a730_seRechaza(int days) {
            assertEquals(List.of("durationDays"),
                    List.copyOf(errors(() -> create.execute(admin, command("Raro", days, "100", null))).keySet()));
        }

        @Test
        void unaReferenciaQueNoDura30Dias_seRechaza() {
            Plan anual = plans.add("Anual", 365, "3800", null, true);
            assertEquals(List.of("referenceMonthlyPlanId"),
                    List.copyOf(errors(() -> create.execute(admin, command("Trimestral", 90, "1200", anual.id()))).keySet()));
        }

        @Test
        void unaReferenciaInactiva_seAcepta_peroElAhorroNoEstaDisponible() {
            Plan viejo = plans.add("Mensual viejo", 30, "400", null, false);
            PlanRow row = create.execute(admin, command("Trimestral", 90, "1200", viejo.id()));
            assertEquals("el plan de referencia (Mensual viejo) está inactivo", row.savings().reason());
        }
    }

    @Nested
    class Editar {
        @Test
        void cambiaLosDatos_yConservaSiEstaActivo() {
            Plan anual = plans.add("Anual", 365, "3800", null, false);

            PlanRow row = update.execute(admin, anual.id(), command("Anual 2027", 365, "4000", mensual.id()));

            assertEquals(new Plan(anual.id(), "Anual 2027", 365, Money.of("4000"), mensual.id(), false), plans.get(anual.id()));
            assertFalse(row.active());
            assertEquals("Mensual", row.referenceMonthlyPlanName());
        }

        @Test
        void conservarSuNombre_oCambiarleMayusculas_sePermite() {
            assertEquals("MENSUAL", update.execute(admin, mensual.id(), command("MENSUAL", 30, "500", null)).name());
        }

        @Test
        void unPlanNoPuedeSerSuPropiaReferencia() {
            assertEquals(Map.of("referenceMonthlyPlanId", "Elige un plan de 30 días distinto de este."),
                    errors(() -> update.execute(admin, mensual.id(), command("Mensual", 30, "450", mensual.id()))));
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            NotFoundException e = assertThrows(NotFoundException.class,
                    () -> update.execute(admin, 99, command("Otro", 30, "100", null)));
            assertEquals("El plan ya no existe. Recarga la pantalla.", e.getMessage());
        }
    }

    @Nested
    class ActivarYDesactivar {
        @Test
        void desactivaYReactiva() {
            assertFalse(setActive.execute(admin, mensual.id(), false).active());
            assertFalse(plans.get(mensual.id()).active());
            assertTrue(setActive.execute(admin, mensual.id(), true).active());
        }

        @Test
        void siYaEstaEnEseEstado_noEscribe() {
            assertTrue(setActive.execute(admin, mensual.id(), true).active());
            assertEquals(0, plans.writes);
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            assertThrows(NotFoundException.class, () -> setActive.execute(admin, 99, false));
        }
    }

    @Nested
    class Eliminar {
        @Test
        void unPlanSinUso_seBorra() {
            Plan prueba = plans.add("Día de prueba", 1, "60", null, false);
            delete.execute(admin, prueba.id());
            assertNull(plans.get(prueba.id()));
        }

        @Test
        void conMembresias_noSeBorra_ySePideDesactivarlo() {
            plans.giveMembership(mensual.id());

            DomainException e = assertThrows(DomainException.class, () -> delete.execute(admin, mensual.id()));

            assertEquals("No puedes eliminar «Mensual»: ya tiene membresías. Desactívalo para dejar de ofrecerlo.",
                    e.getMessage());
            assertEquals(mensual, plans.get(mensual.id()));
        }

        @Test
        void siEsReferenciaDeOtros_noSeBorra_yLosNombra() {
            plans.add("Trimestral", 90, "1200", mensual.id(), true);
            plans.add("Anual", 365, "3800", mensual.id(), true);

            DomainException e = assertThrows(DomainException.class, () -> delete.execute(admin, mensual.id()));

            assertEquals("No puedes eliminar «Mensual»: es la referencia mensual de Anual, Trimestral. "
                    + "Cambia esa referencia antes.", e.getMessage());
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            assertThrows(NotFoundException.class, () -> delete.execute(admin, 99));
        }
    }

    @Test
    void losCasosQueEscriben_leenYEscribenSoloDentroDeSuTransaccion() {
        plans.guard.watch(transactions);
        users.guard.watch(transactions);
        PlanRow row = create.execute(admin, command("Trimestral", 90, "1200", mensual.id()));
        update.execute(admin, row.id(), command("Trimestral", 90, "1250", mensual.id()));
        setActive.execute(admin, row.id(), false);
        delete.execute(admin, row.id());
        assertEquals(4, transactions.started);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RECEPCION", "CAJA"})
    void soloElAdministrador_administraLosPlanes(Role role) {
        User user = users.add("Persona", role, true);
        PlanCommand command = command("Otro", 30, "100", null);

        assertThrows(DomainException.class, () -> list.execute(user));
        assertThrows(DomainException.class, () -> preview.execute(user, command));
        assertThrows(DomainException.class, () -> create.execute(user, command));
        assertThrows(DomainException.class, () -> update.execute(user, mensual.id(), command));
        assertThrows(DomainException.class, () -> setActive.execute(user, mensual.id(), false));
        assertThrows(DomainException.class, () -> delete.execute(user, mensual.id()));
        assertEquals(0, plans.writes);
    }
}
