package dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPaymentMethods;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;


import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.payment.PaymentMethod;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
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

class PaymentMethodsTest {
    private final InMemoryPaymentMethods methods = new InMemoryPaymentMethods();
    private final InMemoryUsers users = new InMemoryUsers();
    private final Authorizer authorizer = new Authorizer(users);
    private final User admin = users.admin();

    private final ListPaymentMethods list = new ListPaymentMethods(methods, authorizer);
    private final ImmediateTransactions transactions = new ImmediateTransactions();
    private final CreatePaymentMethod create = new CreatePaymentMethod(methods, transactions, authorizer);
    private final UpdatePaymentMethod update = new UpdatePaymentMethod(methods, transactions, authorizer);
    private final SetPaymentMethodActive setActive = new SetPaymentMethodActive(methods, transactions, authorizer);
    private final DeletePaymentMethod delete = new DeletePaymentMethod(methods, transactions, authorizer);

    private final PaymentMethod efectivo = methods.add("Efectivo", true, true);
    private final PaymentMethod tarjeta = methods.add("Tarjeta", false, true);
    private final PaymentMethod vales = methods.add("Vales", false, false);

    private static Map<String, String> errors(Executable operation) {
        return assertThrows(ValidationException.class, operation).errors();
    }

    @Nested
    class Listar {
        @Test
        void soloActivos_oTodos_ordenadosPorNombre() {
            methods.add("bitcoin", false, true);

            assertEquals(List.of("bitcoin", "Efectivo", "Tarjeta"),
                    list.execute(admin, false).stream().map(PaymentMethodItem::name).toList());
            assertEquals(List.of("bitcoin", "Efectivo", "Tarjeta", "Vales"),
                    list.execute(admin, true).stream().map(PaymentMethodItem::name).toList());
        }

        @Test
        void cadaElemento_traeSiEsEfectivoYSiEstaActivo() {
            assertEquals(new PaymentMethodItem(efectivo.id(), "Efectivo", true, true), list.execute(admin, false).getFirst());
        }
    }

    @Nested
    class Crear {
        @Test
        void quedaActivo_conElNombreRecortado() {
            PaymentMethodItem item = create.execute(admin, new PaymentMethodCommand("  Transferencia ", false));

            assertEquals("Transferencia", item.name());
            assertTrue(item.active());
            assertFalse(item.cash());
            assertEquals(item, PaymentMethodRules.toItem(methods.get(item.id())));
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = {"   "})
        void sinNombre_seRechaza(String name) {
            assertEquals(Map.of("name", "Escribe el nombre."), errors(() -> create.execute(admin, new PaymentMethodCommand(name, false))));
            assertEquals(0, methods.writes);
        }

        @Test
        void nombreDeMasDe40Caracteres_seRechaza_y40SeAcepta() {
            assertEquals(Map.of("name", "Usa 40 caracteres o menos."),
                    errors(() -> create.execute(admin, new PaymentMethodCommand("x".repeat(41), false))));
            assertEquals(40, create.execute(admin, new PaymentMethodCommand("x".repeat(40), false)).name().length());
        }

        @ParameterizedTest
        @ValueSource(strings = {"Tarjeta", "TARJETA", " tarjeta ", "vales"})
        void nombreRepetido_sinDistinguirMayusculas_niInactivos_seRechaza(String name) {
            assertEquals(Map.of("name", "Ya existe uno con ese nombre."),
                    errors(() -> create.execute(admin, new PaymentMethodCommand(name, false))));
        }
    }

    @Nested
    class Editar {
        @Test
        void cambiaNombreYEfectivo_yConservaSiEstaActivo() {
            PaymentMethodItem item = update.execute(admin, vales.id(), new PaymentMethodCommand("Vales de despensa", true));

            assertEquals(new PaymentMethodItem(vales.id(), "Vales de despensa", true, false), item);
            assertEquals(new PaymentMethod(vales.id(), "Vales de despensa", true, false), methods.get(vales.id()));
        }

        @Test
        void conservarSuPropioNombre_oCambiarleLasMayusculas_sePermite() {
            assertEquals("TARJETA", update.execute(admin, tarjeta.id(), new PaymentMethodCommand("TARJETA", false)).name());
        }

        @Test
        void tomarElNombreDeOtro_seRechaza() {
            assertEquals(Map.of("name", "Ya existe uno con ese nombre."),
                    errors(() -> update.execute(admin, tarjeta.id(), new PaymentMethodCommand("efectivo", false))));
        }

        @Test
        void unMetodoYaUsado_noCambiaSiEsEfectivo_niEnUnSentidoNiEnOtro() {
            methods.markUsed(efectivo.id());
            methods.markUsed(tarjeta.id());

            assertEquals(Map.of("cash", "No puedes cambiar si «Efectivo» es efectivo: ya se usó en cobros o ventas. "
                            + "Crea un método nuevo y desactiva este."),
                    errors(() -> update.execute(admin, efectivo.id(), new PaymentMethodCommand("Efectivo", false))));
            assertEquals(List.of("cash"), List.copyOf(
                    errors(() -> update.execute(admin, tarjeta.id(), new PaymentMethodCommand("Tarjeta", true))).keySet()));
            assertEquals(0, methods.writes);
        }

        @Test
        void unMetodoYaUsado_siPuedeCambiarDeNombre() {
            methods.markUsed(efectivo.id());
            assertEquals(new PaymentMethodItem(efectivo.id(), "Efectivo (caja 1)", true, true),
                    update.execute(admin, efectivo.id(), new PaymentMethodCommand("Efectivo (caja 1)", true)));
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            NotFoundException e = assertThrows(NotFoundException.class,
                    () -> update.execute(admin, 99, new PaymentMethodCommand("Otro", false)));
            assertEquals("El método de pago ya no existe. Recarga la pantalla.", e.getMessage());
        }
    }

    @Nested
    class ActivarYDesactivar {
        @Test
        void desactivaYReactiva() {
            assertFalse(setActive.execute(admin, tarjeta.id(), false).active());
            assertFalse(methods.get(tarjeta.id()).active());
            assertTrue(setActive.execute(admin, tarjeta.id(), true).active());
        }

        @Test
        void elUltimoActivo_noSePuedeDesactivar() {
            setActive.execute(admin, tarjeta.id(), false);

            DomainException e = assertThrows(DomainException.class, () -> setActive.execute(admin, efectivo.id(), false));
            assertEquals("Deja al menos un método de pago.", e.getMessage());
            assertTrue(methods.get(efectivo.id()).active());
        }

        @Test
        void siYaEstaEnEseEstado_noEscribe() {
            assertEquals(PaymentMethodRules.toItem(vales), setActive.execute(admin, vales.id(), false));
            assertEquals(0, methods.writes);
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            assertThrows(NotFoundException.class, () -> setActive.execute(admin, 99, true));
        }
    }

    @Nested
    class Eliminar {
        @Test
        void unMetodoSinUso_seBorra() {
            delete.execute(admin, vales.id());
            assertNull(methods.get(vales.id()));
        }

        @Test
        void unMetodoYaUsado_noSeBorra_ySePideDesactivarlo() {
            methods.markUsed(efectivo.id());

            DomainException e = assertThrows(DomainException.class, () -> delete.execute(admin, efectivo.id()));

            assertEquals("No puedes eliminar «Efectivo»: ya se usó en cobros o ventas. Desactívalo para dejar de ofrecerlo.",
                    e.getMessage());
            assertEquals(efectivo, methods.get(efectivo.id()));
        }

        @Test
        void elUltimoActivo_noSeBorra() {
            setActive.execute(admin, tarjeta.id(), false);

            DomainException e = assertThrows(DomainException.class, () -> delete.execute(admin, efectivo.id()));
            assertEquals("Deja al menos un método de pago.", e.getMessage());
        }

        @Test
        void unInactivoSinUso_seBorraAunqueSoloQuedeUnActivo() {
            setActive.execute(admin, tarjeta.id(), false);
            delete.execute(admin, tarjeta.id());
            assertNull(methods.get(tarjeta.id()));
        }

        @Test
        void unIdQueNoExiste_seRechaza() {
            assertThrows(NotFoundException.class, () -> delete.execute(admin, 99));
        }
    }

    @Test
    void crearEditarActivarYBorrar_leenYEscribenSoloDentroDeSuTransaccion() {
        methods.guard.watch(transactions);
        users.guard.watch(transactions);
        create.execute(admin, new PaymentMethodCommand("Otro", false));
        update.execute(admin, tarjeta.id(), new PaymentMethodCommand("Tarjeta de crédito", false));
        setActive.execute(admin, tarjeta.id(), false);
        delete.execute(admin, vales.id());
        assertEquals(4, transactions.started);
    }

    @ParameterizedTest
    @EnumSource(value = Role.class, names = {"RECEPCION", "CAJA"})
    void soloElAdministrador_administraLosMetodosDePago(Role role) {
        User user = users.add("Persona", role, true);

        assertThrows(DomainException.class, () -> list.execute(user, true));
        assertThrows(DomainException.class, () -> create.execute(user, new PaymentMethodCommand("Otro", false)));
        assertThrows(DomainException.class, () -> update.execute(user, tarjeta.id(), new PaymentMethodCommand("Otro", false)));
        assertThrows(DomainException.class, () -> setActive.execute(user, tarjeta.id(), false));
        assertThrows(DomainException.class, () -> delete.execute(user, vales.id()));
        assertEquals(0, methods.writes);
    }
}
