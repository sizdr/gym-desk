package dev.gimnasio.sistemagimnasio.infrastructure.persistence;

import dev.gimnasio.sistemagimnasio.domain.member.Email;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.Phone;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresMemberRepositoryTest {
    private Jdbi jdbi;
    private PostgresMemberRepository members;
    private PostgresMembershipRepository memberships;

    @BeforeEach
    void setUp() {
        jdbi = TestDatabase.jdbi();
        TestDatabase.clean();
        members = new PostgresMemberRepository(jdbi);
        memberships = new PostgresMembershipRepository(jdbi);
    }

    private long id(String insert) {
        return jdbi.withHandle(handle -> handle.createQuery(insert + " RETURNING id").mapTo(Long.class).one());
    }

    private Member ana() {
        return members.insert(new Member(null, "Ana López", new Phone("5512345678"), new Email("ana@gym.mx"),
                LocalDate.of(2026, 11, 1)));
    }

    @Test
    void altaConsultaYCorreccion() {
        Member ana = ana();
        Member sinCorreo = members.insert(new Member(null, "Luis Pérez", new Phone("5587654321"), null,
                LocalDate.of(2026, 10, 1)));

        assertEquals(Optional.of(ana), members.findById(ana.id()));
        assertEquals(2, members.findAll().size());
        assertEquals(Optional.of(sinCorreo), members.findById(sinCorreo.id()));

        Member corregida = new Member(ana.id(), "Ana María López", new Phone("5511112222"), null,
                LocalDate.of(2026, 10, 15));
        members.update(corregida);
        assertEquals(Optional.of(corregida), members.findById(ana.id()));
        assertThrows(IllegalStateException.class, () -> members.update(new Member(999L, "Nadie", new Phone("5500000000"),
                null, LocalDate.of(2026, 1, 1))));
    }

    @Test
    void elCorreoRepetido_seBuscaSinMayusculas_yExcluyeAlPropio() {
        Member ana = ana();
        assertEquals(Optional.of("Ana López"), members.nameWithEmail("ANA@gym.mx", null));
        assertEquals(Optional.empty(), members.nameWithEmail("ana@gym.mx", ana.id()));
        assertEquals(Optional.empty(), members.nameWithEmail("otra@gym.mx", null));
    }

    @Test
    void sabeSiTieneCobros_yBorraSoloAlQueNoTiene() {
        Member ana = ana();
        Member luis = members.insert(new Member(null, "Luis Pérez", new Phone("5587654321"), null,
                LocalDate.of(2026, 10, 1)));
        long usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Caja', 'caja', 'h', 'caja')");
        long metodo = id("INSERT INTO metodo_pago (nombre) VALUES ('Tarjeta')");
        id("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) VALUES ('GYM-0001', "
                + ana.id() + ", " + usuario + ", " + metodo + ", 450, 'primera')");

        assertTrue(members.hasPayments(ana.id()));
        assertFalse(members.hasPayments(luis.id()));
        members.delete(luis.id());
        assertEquals(List.of(ana), members.findAll());
    }

    @Test
    void laUltimaMembresiaDeCadaSocio() {
        Member ana = ana();
        long usuario = id("INSERT INTO usuario (nombre, nombre_usuario, contrasena_hash, rol) VALUES ('Caja', 'caja', 'h', 'caja')");
        long metodo = id("INSERT INTO metodo_pago (nombre) VALUES ('Tarjeta')");
        long plan = id("INSERT INTO plan_membresia (nombre, duracion_dias, precio) VALUES ('Mensual', 30, 450)");
        for (int i = 0; i < 2; i++) {
            long pago = id("INSERT INTO pago (folio, socio_id, usuario_id, metodo_pago_id, monto, tipo_cobro) VALUES ('GYM-"
                    + i + "', " + ana.id() + ", " + usuario + ", " + metodo + ", 450, 'primera')");
            LocalDate start = LocalDate.of(2026, 9, 1).plusDays(30L * i);
            memberships.insert(new Membership(null, ana.id(), plan, pago, start, start.plusDays(30)));
        }

        List<Membership> latest = memberships.findLatestPerMember();

        assertEquals(1, latest.size());
        assertEquals(LocalDate.of(2026, 10, 31), latest.getFirst().end());
    }
}
