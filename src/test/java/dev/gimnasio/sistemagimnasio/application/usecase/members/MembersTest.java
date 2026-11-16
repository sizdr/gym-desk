package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.application.fakes.ImmediateTransactions;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMembers;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryMemberships;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryPlans;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemorySettings;
import dev.gimnasio.sistemagimnasio.application.fakes.InMemoryUsers;
import dev.gimnasio.sistemagimnasio.application.shared.Authorizer;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembersTest {
    static final LocalDate TODAY = LocalDate.of(2026, 11, 15);
    final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    final InMemoryUsers users = new InMemoryUsers();
    final User admin = users.admin();
    final User caja = users.add("carlos", Role.CAJA, true);
    final InMemoryMembers members = new InMemoryMembers();
    final InMemoryMemberships memberships = new InMemoryMemberships();
    final InMemoryPlans plans = new InMemoryPlans();
    final InMemorySettings settings = new InMemorySettings();
    final ImmediateTransactions transactions = new ImmediateTransactions();
    final Authorizer authorizer = new Authorizer(users);
    final long mensual = plans.add("Mensual", 30, "450", null, true).id();

    final SearchMembers search = new SearchMembers(members, memberships, plans, settings, authorizer, clock);
    final RegisterMember register = new RegisterMember(members, memberships, plans, settings, transactions, authorizer, clock);
    final UpdateMember update = new UpdateMember(members, memberships, plans, settings, transactions, authorizer, clock);
    final DeleteMember delete = new DeleteMember(members, transactions, authorizer);

    /** Un socio con una membresía que vence en {@code endFromToday} días (negativo: ya venció). */
    Member withMembership(String name, Integer endFromToday) {
        Member member = members.add(name, TODAY.minusDays(200));
        if (endFromToday != null) {
            LocalDate end = TODAY.plusDays(endFromToday);
            memberships.add(member.id(), mensual, end.minusDays(30), end);
        }
        return member;
    }

    static Map<String, String> errors(Executable operation) {
        return assertThrows(ValidationException.class, operation).errors();
    }

    static MemberCommand command(String name, String phone, String email, LocalDate joinDate) {
        return new MemberCommand(name, phone, email, joinDate);
    }

    @Nested
    class Buscar {
        @Test
        void calculaElEstadoDeCadaSocio_yCuentaTodosLosEstados() {
            withMembership("Ana Vigente", 20);
            withMembership("Bruno PorVencer", 3);
            withMembership("Carla Vencida", -10);
            withMembership("Daniel Baja", -40);
            withMembership("Elena Sin", null);

            MemberSearchResult result = search.execute(admin, SearchMembersQuery.all());

            assertEquals(5, result.totalMembers());
            assertEquals(Map.of(MemberStatus.VIGENTE, 1, MemberStatus.POR_VENCER, 1, MemberStatus.VENCIDA, 1,
                    MemberStatus.BAJA, 1, MemberStatus.SIN_MEMBRESIA, 1), result.countByStatus());
            assertEquals(List.of("Bruno PorVencer", "Ana Vigente", "Carla Vencida", "Daniel Baja", "Elena Sin"),
                    result.members().stream().map(MemberRow::fullName).toList(),
                    "cubiertos del vencimiento más próximo, luego vencidos del más reciente, al final sin membresía");
            MemberRow ana = result.members().get(1);
            assertEquals("Mensual", ana.planName());
            assertEquals(20, ana.daysLeft());
        }

        @Test
        void porTexto_sinMayusculasNiAcentos_correo_oTelefono() {
            withMembership("José Núñez", 20);
            withMembership("Lucía Pérez", 20);
            Member ana = members.insert(new Member(null, "Ana López",
                    new dev.gimnasio.sistemagimnasio.domain.member.Phone("5599887766"),
                    new dev.gimnasio.sistemagimnasio.domain.member.Email("ana@gym.mx"), TODAY));

            assertEquals(List.of("José Núñez"), names(new SearchMembersQuery("jose nun", null, null, null)));
            assertEquals(List.of("Ana López"), names(new SearchMembersQuery("ANA@GYM", null, null, null)));
            assertEquals(List.of("Ana López"), names(new SearchMembersQuery("998 877", null, null, null)));
            assertEquals(3, search.execute(admin, new SearchMembersQuery("  ", null, null, null)).matchCount());
            assertTrue(ana.id() > 0);
        }

        @Test
        void porEstado_conLimite_yElConteoEsAntesDelLimite() {
            for (int i = 0; i < 4; i++) {
                withMembership("Socio " + i, 20 + i);
            }
            withMembership("Otro", -10);

            MemberSearchResult result = search.execute(admin,
                    new SearchMembersQuery(null, MemberStatus.VIGENTE, MemberSort.NOMBRE, 2));

            assertEquals(List.of("Socio 0", "Socio 1"), result.members().stream().map(MemberRow::fullName).toList());
            assertEquals(4, result.matchCount());
            assertEquals(5, result.totalMembers());
        }

        List<String> names(SearchMembersQuery query) {
            return search.execute(admin, query).members().stream().map(MemberRow::fullName).toList();
        }

        @Test
        void cajaPuedeConsultar() {
            withMembership("Ana", 20);
            assertEquals(1, search.execute(caja, SearchMembersQuery.all()).totalMembers());
        }
    }

    @Nested
    class Registrar {
        @Test
        void normalizaLosDatos_yQuedaSinMembresia() {
            MemberRow row = register.execute(admin,
                    command("  ana   lópez ", "555-010 2233", " Ana@Gym.MX ", TODAY.minusDays(1)));

            assertEquals("ana lópez", row.fullName());
            assertEquals("5550102233", row.phone());
            assertEquals("ana@gym.mx", row.email());
            assertEquals(MemberStatus.SIN_MEMBRESIA, row.status());
            assertEquals(TODAY.minusDays(1), row.joinDate());
            assertEquals(1, members.writes);
        }

        @Test
        void correoVacio_quedaSinCorreo() {
            assertNull(register.execute(admin, command("Ana López", "5550102233", "  ", TODAY)).email());
        }

        @Test
        void todosLosErrores_juntos_yEnElOrdenDelFormulario() {
            Map<String, String> errors = errors(() -> register.execute(admin, command(" ", "", "no-es-correo", null)));
            assertEquals(List.of("fullName", "phone", "email", "joinDate"), List.copyOf(errors.keySet()));
            assertEquals("Escribe el nombre completo.", errors.get("fullName"));
            assertEquals("Escribe el teléfono a 10 dígitos.", errors.get("phone"));
            assertEquals("Escribe un correo válido, por ejemplo nombre@ejemplo.com, o déjalo vacío.", errors.get("email"));
            assertEquals("Escribe la fecha de alta.", errors.get("joinDate"));
            assertEquals(0, members.writes);
        }

        @Test
        void mensajesDeCadaRegla() {
            Map<String, String> errors = errors(() -> register.execute(admin,
                    command("A1", "555010223", null, TODAY.plusDays(1))));
            assertEquals("Escribe el nombre completo, con al menos 3 letras.", errors.get("fullName"));
            assertEquals("El teléfono debe tener 10 dígitos; tiene 9. Ejemplo: 555 010 2233.", errors.get("phone"));
            assertEquals("La fecha de alta no puede ser posterior a hoy (15/11/2026).", errors.get("joinDate"));
            assertEquals("Escribe solo números: el teléfono debe tener 10 dígitos. Ejemplo: 555 010 2233.",
                    errors(() -> register.execute(admin, command("Ana López", "55-ABC-1234", null, TODAY))).get("phone"));
            assertEquals("Usa 100 caracteres o menos.",
                    errors(() -> register.execute(admin, command("a".repeat(101), "5550102233", null, TODAY))).get("fullName"));
        }

        @Test
        void correoRepetido_sinDistinguirMayusculas_diceDeQuien() {
            register.execute(admin, command("Ana López", "5550102233", "ana@gym.mx", TODAY));
            assertEquals(Map.of("email", "Ya hay un socio con este correo: Ana López. Usa otro o déjalo vacío."),
                    errors(() -> register.execute(admin, command("Otra Ana", "5550102234", "ANA@gym.mx", TODAY))));
        }

        @Test
        void cajaNoPuedeRegistrar() {
            assertThrows(DomainException.class, () -> register.execute(caja, command("Ana López", "5550102233", null, TODAY)));
            assertEquals(0, members.writes);
        }

        @Test
        void todoOcurreEnUnaSolaTransaccion() {
            members.guard.watch(transactions);
            register.execute(admin, command("Ana López", "5550102233", "ana@gym.mx", TODAY));
            assertEquals(1, transactions.started);
        }
    }

    @Nested
    class Corregir {
        @Test
        void guardaLosDatos_yConservaSuMembresia() {
            Member ana = withMembership("Ana López", 20);
            MemberRow row = update.execute(admin, ana.id(), command("Ana María López", "5511112222", "ana@gym.mx",
                    TODAY.minusDays(300)));
            assertEquals("Ana María López", members.get(ana.id()).fullName());
            assertEquals(MemberStatus.VIGENTE, row.status());
            assertEquals("Mensual", row.planName());
        }

        @Test
        void suPropioCorreo_noCuentaComoRepetido() {
            Member ana = members.insert(new Member(null, "Ana López",
                    new dev.gimnasio.sistemagimnasio.domain.member.Phone("5550102233"),
                    new dev.gimnasio.sistemagimnasio.domain.member.Email("ana@gym.mx"), TODAY));
            update.execute(admin, ana.id(), command("Ana López", "5550102233", "ANA@gym.mx", TODAY));
        }

        @Test
        void laFechaDeAlta_noPuedeQuedarDespuesDeSuPrimeraMembresia() {
            Member ana = withMembership("Ana López", 20);
            LocalDate firstStart = TODAY.plusDays(20).minusDays(30);
            assertEquals(Map.of("joinDate", "La fecha de alta no puede ser posterior a su primera membresía (05/11/2026)."),
                    errors(() -> update.execute(admin, ana.id(), command("Ana López", "5550102233", null,
                            firstStart.plusDays(1)))));
        }

        @Test
        void unSocioQueYaNoExiste() {
            assertEquals("El socio no existe.", assertThrows(NotFoundException.class,
                    () -> update.execute(admin, 99, command("Ana López", "5550102233", null, TODAY))).getMessage());
        }
    }

    @Nested
    class Eliminar {
        @Test
        void unoSinCobros_seBorra() {
            Member ana = withMembership("Ana López", null);
            delete.execute(admin, ana.id());
            assertNull(members.get(ana.id()));
        }

        @Test
        void unoConCobros_seRechaza_yConservaSuHistorial() {
            Member ana = withMembership("Ana López", 20);
            members.markPaid(ana.id());
            assertEquals("No puedes eliminar a «Ana López»: ya tiene cobros registrados y su historial se conserva.",
                    assertThrows(DomainException.class, () -> delete.execute(admin, ana.id())).getMessage());
            assertEquals(ana, members.get(ana.id()));
        }

        @Test
        void unoQueYaNoExiste_yCajaNoPuede() {
            assertThrows(NotFoundException.class, () -> delete.execute(admin, 99));
            Member ana = withMembership("Ana López", null);
            assertThrows(DomainException.class, () -> delete.execute(caja, ana.id()));
            assertEquals(ana, members.get(ana.id()));
        }
    }
}
