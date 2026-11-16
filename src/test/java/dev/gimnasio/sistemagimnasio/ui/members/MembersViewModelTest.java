package dev.gimnasio.sistemagimnasio.ui.members;

import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MembersViewModelTest {
    private static final LocalDate TODAY = UiFakes.TODAY;
    private final UiFakes.Users users = new UiFakes.Users();
    private final User admin = users.add("Ana Admin", "ana", Role.ADMINISTRADOR, true);
    private final UiFakes.Backend backend = new UiFakes.Backend(users);
    private final List<String> ok = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);
    private final Notifier notifier = new Notifier() {
        @Override
        public void ok(String message) {
            ok.add(message);
        }

        @Override
        public void error(String message) {
            errors.add(message);
        }
    };
    private final MembersViewModel model = new MembersViewModel(backend.useCases(), background, () -> admin, notifier,
            true, () -> TODAY);
    private final long mensual = backend.plans.add("Mensual", 30, "450", null, true).id();

    @AfterEach
    void stop() {
        background.close();
        uiThread.shutdownNow();
    }

    private <T> T onUi(Callable<T> work) throws Exception {
        return uiThread.submit(work).get(2, TimeUnit.SECONDS);
    }

    private void onUi(Runnable work) throws Exception {
        uiThread.submit(work).get(2, TimeUnit.SECONDS);
    }

    private void waitUntil(BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            if (onUi(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("no terminó a tiempo");
    }

    private void settle() throws Exception {
        Thread.sleep(60);
        waitUntil(() -> !model.busyProperty().get() && !model.editorSavingProperty().get());
        Thread.sleep(60);
        onUi(() -> { });
    }

    private Member member(String name, Integer endFromToday) {
        Member member = backend.members.add(name, TODAY.minusDays(100));
        if (endFromToday != null) {
            LocalDate end = TODAY.plusDays(endFromToday);
            backend.memberships.add(member.id(), mensual, end.minusDays(30), end);
        }
        return member;
    }

    private void loadAndWait() throws Exception {
        onUi(model::load);
        waitUntil(() -> model.stateProperty().get() != MembersViewModel.State.LOADING);
    }

    private List<String> names() throws Exception {
        return onUi(() -> model.snapshot().stream().map(MemberRow::fullName).toList());
    }

    @Test
    void cargaLaLista_conLosConteosPorEstado() throws Exception {
        member("Ana Vigente", 20);
        member("Bruno PorVencer", 3);
        member("Carla Sin", null);
        loadAndWait();

        assertEquals(MembersViewModel.State.READY, model.stateProperty().get());
        assertEquals(List.of("Bruno PorVencer", "Ana Vigente", "Carla Sin"), names());
        assertEquals(3, model.totalMembersProperty().get());
        assertEquals(1, model.countsProperty().get().get(MemberStatus.POR_VENCER));
    }

    @Test
    void buscarYFiltrar_actualizanLaLista() throws Exception {
        member("Ana López", 20);
        member("Bruno Díaz", -10);
        loadAndWait();

        onUi(() -> model.searchTextProperty().set("ana"));
        waitUntil(() -> model.snapshot().size() == 1);
        assertEquals(List.of("Ana López"), names());
        assertEquals(1, model.matchCountProperty().get());

        onUi(() -> {
            model.searchTextProperty().set("");
            model.statusFilterProperty().set(MemberStatus.VENCIDA);
        });
        waitUntil(() -> model.snapshot().size() == 1 && model.snapshot().getFirst().fullName().equals("Bruno Díaz"));
        assertEquals(2, model.totalMembersProperty().get(), "el total no depende del filtro");
    }

    @Test
    void registrar_cierraElFormulario_avisa_yRecarga() throws Exception {
        loadAndWait();
        onUi(model::startCreate);
        assertEquals(TODAY, model.editorJoinDateProperty().get(), "propone la fecha de hoy");
        onUi(() -> {
            model.editorNameProperty().set("Ana López");
            model.editorPhoneProperty().set("555 010 2233");
            model.editorEmailProperty().set("ana@gym.mx");
            model.submitEditor();
        });
        settle();
        waitUntil(() -> model.snapshot().size() == 1);

        assertFalse(model.editorOpenProperty().get());
        assertEquals(List.of("Se registró a «Ana López». Queda sin membresía hasta su primer cobro."), ok);
        assertEquals("5550102233", model.snapshot().getFirst().phone());
    }

    @Test
    void erroresDelFormulario_enSusCampos_yElFormularioSigueAbierto() throws Exception {
        loadAndWait();
        onUi(model::startCreate);
        onUi(() -> {
            model.editorPhoneProperty().set("123");
            model.submitEditor();
        });
        settle();

        Map<String, String> fieldErrors = model.editorFieldErrorsProperty().get();
        assertEquals(List.of("fullName", "phone"), List.copyOf(fieldErrors.keySet()));
        assertTrue(model.editorOpenProperty().get());
        assertTrue(ok.isEmpty());
    }

    @Test
    void editar_cargaSusDatos_yGuarda() throws Exception {
        member("Ana López", 20);
        loadAndWait();
        MemberRow ana = onUi(() -> model.snapshot().getFirst());
        onUi(() -> model.startEdit(ana));
        assertEquals("Ana López", model.editorNameProperty().get());
        assertEquals(ana.joinDate(), model.editorJoinDateProperty().get());

        onUi(() -> {
            model.editorNameProperty().set("Ana María López");
            model.submitEditor();
        });
        settle();
        waitUntil(() -> model.snapshot().getFirst().fullName().equals("Ana María López"));
        assertEquals(List.of("Se guardaron los datos de «Ana María López»."), ok);
    }

    @Test
    void eliminar_sinCobros_loQuita_yConCobros_avisaElMotivo() throws Exception {
        Member ana = member("Ana López", null);
        Member bruno = member("Bruno Díaz", 20);
        backend.members.markPaid(bruno.id());
        loadAndWait();

        MemberRow anaRow = onUi(() -> model.snapshot().stream().filter(r -> r.id() == ana.id()).findFirst().orElseThrow());
        onUi(() -> model.delete(anaRow));
        settle();
        waitUntil(() -> model.snapshot().size() == 1);
        assertEquals(List.of("Se eliminó a «Ana López»."), ok);

        MemberRow brunoRow = onUi(() -> model.snapshot().getFirst());
        onUi(() -> model.delete(brunoRow));
        settle();
        assertEquals(List.of("No puedes eliminar a «Bruno Díaz»: ya tiene cobros registrados y su historial se conserva."),
                errors);
        assertEquals(1, onUi(() -> model.snapshot().size()));
    }

    @Test
    void sinPermisoDeEditar_noAbreElFormularioNiBorra() throws Exception {
        User caja = users.add("Carlos Caja", "carlos", Role.CAJA, true);
        MembersViewModel readOnly = new MembersViewModel(backend.useCases(), background, () -> caja, notifier, false,
                () -> TODAY);
        member("Ana López", 20);
        onUi(readOnly::load);
        waitUntil(() -> readOnly.stateProperty().get() == MembersViewModel.State.READY);
        onUi(readOnly::startCreate);
        assertFalse(readOnly.editorOpenProperty().get());
        assertEquals(1, readOnly.rows().size(), "caja sí consulta");
    }

    @Test
    void textosDeVigencia_conLosDatosDelCasoDeUso() {
        assertEquals("Quedan 12 días", MembersViewModel.validity(row(MemberStatus.VIGENTE, 12, null, null, null)));
        assertEquals("Vence hoy", MembersViewModel.validity(row(MemberStatus.POR_VENCER, 0, null, null, null)));
        assertEquals("Vence mañana", MembersViewModel.validity(row(MemberStatus.POR_VENCER, 1, null, null, null)));
        assertEquals("Venció hace 5 días; pasa a baja el 05/12/2026",
                MembersViewModel.validity(row(MemberStatus.VENCIDA, null, 5, LocalDate.of(2026, 12, 5), null)));
        assertEquals("Baja desde el 30/09/2026",
                MembersViewModel.validity(row(MemberStatus.BAJA, null, 40, null, LocalDate.of(2026, 9, 30))));
        assertEquals("Sin membresía", MembersViewModel.validity(row(MemberStatus.SIN_MEMBRESIA, null, null, null, null)));
        assertEquals("551 234 5678", MembersController.formatPhone("5512345678"));
    }

    private static MemberRow row(MemberStatus status, Integer left, Integer overdue, LocalDate deactivatesOn,
                                 LocalDate deactivation) {
        return new MemberRow(1, "Ana", "5512345678", null, status, null, null, left, overdue, deactivatesOn,
                deactivation, TODAY);
    }

    @Test
    void siFallaLaCarga_quedaElMotivo() throws Exception {
        User caja = users.add("Pedro Inactivo", "pedro", Role.CAJA, false);
        MembersViewModel failing = new MembersViewModel(backend.useCases(), background, () -> caja, notifier, false,
                () -> TODAY);
        onUi(failing::load);
        waitUntil(() -> failing.stateProperty().get() != MembersViewModel.State.LOADING);
        assertEquals(MembersViewModel.State.LOAD_FAILED, failing.stateProperty().get());
        assertEquals("Tu sesión no es válida. Vuelve a iniciar sesión.", failing.loadErrorProperty().get());
        assertNull(failing.editing());
    }
}
