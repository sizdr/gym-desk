package dev.gimnasio.sistemagimnasio.ui.users;

import dev.gimnasio.sistemagimnasio.application.usecase.users.UserRow;
import dev.gimnasio.sistemagimnasio.domain.user.Permission;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.Notifier;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class UsersViewModelTest {
    private final UiFakes.Users users = new UiFakes.Users();
    private final User admin = users.add("Ana Martínez", "ana", Role.ADMINISTRADOR, true);
    private final User rocio = users.add("Rocío Méndez", "rocio", Role.RECEPCION, true);
    private final User carlos = users.add("Carlos Ruiz", "carlos", Role.CAJA, true);
    private final UiFakes.Backend backend = new UiFakes.Backend(users);
    private final List<String> ok = new ArrayList<>();
    private final List<String> errors = new ArrayList<>();
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);
    private final UsersViewModel model = new UsersViewModel(backend.useCases(), background, () -> admin,
            new Notifier() {
                @Override
                public void ok(String message) {
                    ok.add(message);
                }

                @Override
                public void error(String message) {
                    errors.add(message);
                }
            });

    @BeforeEach
    void load() throws Exception {
        users.add("Pedro Salas", "pedro", Role.CAJA, false);
        onUi(model::load);
        waitUntil(() -> model.stateProperty().get() == UsersViewModel.State.READY);
    }

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
        for (int i = 0; i < 300; i++) {
            if (onUi(condition::getAsBoolean)) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("no terminó a tiempo");
    }

    private UserRow row(String username) throws Exception {
        return onUi(() -> model.items().stream().filter(u -> u.username().equals(username)).findFirst().orElseThrow());
    }

    @Test
    void cargaActivosPrimero_yReconoceLaPropiaCuenta() throws Exception {
        assertEquals(List.of("ana", "carlos", "rocio", "pedro"),
                onUi(() -> model.items().stream().map(UserRow::username).toList()));
        assertEquals("4 cuentas · 3 activas", UsersViewModel.summary(model.snapshot()));
        assertTrue(model.isSelf(row("ana")));
        assertFalse(model.isSelf(row("rocio")));
    }

    @Test
    void crear_conErroresEnSusCampos_yLuegoBien_sinDejarLaContrasena() throws Exception {
        onUi(model::startCreate);
        onUi(() -> {
            model.editorNameProperty().set(" ");
            model.editorUsernameProperty().set("Carlos");
            model.editorPasswordProperty().set("corta");
            model.editorConfirmationProperty().set("otra");
        });
        onUi(model::submitEditor);
        waitUntil(() -> !model.editorFieldErrorsProperty().get().isEmpty());
        Map<String, String> fieldErrors = onUi(() -> model.editorFieldErrorsProperty().get());
        assertEquals("Escribe el nombre de la persona.", fieldErrors.get("name"));
        assertEquals("Ese nombre de usuario ya está en uso.", fieldErrors.get("username"), "sin distinguir mayúsculas");
        assertEquals("Elige un rol.", fieldErrors.get("role"));
        assertTrue(fieldErrors.containsKey("password"));

        onUi(() -> {
            model.editorNameProperty().set("Lucía Pérez");
            model.editorUsernameProperty().set("lucia");
            model.editorRoleProperty().set(Role.CAJA);
            model.editorPasswordProperty().set("una-clave-larga");
            model.editorConfirmationProperty().set("una-clave-larga");
        });
        assertFalse(onUi(() -> model.editorFieldErrorsProperty().get().containsKey("name")), "corregir quita el error");
        onUi(model::submitEditor);
        onUi(model::submitEditor);
        waitUntil(() -> !model.editorOpenProperty().get() && model.items().size() == 5);
        assertEquals(List.of("Se creó la cuenta de «Lucía Pérez». Ya puede entrar como «lucia»."), ok);
        assertEquals("", onUi(() -> model.editorPasswordProperty().get()), "la contraseña no se queda en el formulario");
        assertEquals("", onUi(() -> model.editorConfirmationProperty().get()));
    }

    @Test
    void editar_sinContrasenaLaConserva_yAvisaElCambioDeRol() throws Exception {
        UserRow carlosRow = row("carlos");
        onUi(() -> model.startEdit(carlosRow));
        assertEquals(Role.CAJA, onUi(() -> model.editorRoleProperty().get()));
        onUi(() -> model.editorRoleProperty().set(Role.RECEPCION));
        onUi(model::submitEditor);
        waitUntil(() -> !model.editorOpenProperty().get() && !ok.isEmpty());
        assertEquals("Se guardaron los cambios de «Carlos Ruiz». El rol nuevo aplica desde su siguiente operación.",
                ok.getFirst());
        waitUntil(() -> model.items().stream().anyMatch(u -> u.username().equals("carlos") && u.role() == Role.RECEPCION));
    }

    @Test
    void desactivar_yLasReglasDelCasoDeUso() throws Exception {
        UserRow rocioRow = row("rocio");
        onUi(() -> model.setActive(rocioRow, false));
        waitUntil(() -> !ok.isEmpty() && !model.busyProperty().get());
        assertEquals("«Rocío Méndez» ya no puede entrar. Sus cobros se conservan.", ok.getFirst());
        waitUntil(() -> model.items().stream().anyMatch(u -> u.username().equals("rocio") && !u.active()));

        UserRow me = row("ana");
        onUi(() -> model.setActive(me, false));
        waitUntil(() -> !errors.isEmpty());
        assertEquals("No puedes desactivar tu propio usuario mientras lo usas.", errors.getFirst());
    }

    @Test
    void eliminar_conCobrosSeRechaza_ySinCobrosSeBorra() throws Exception {
        users.withOperations.add(carlos.id());
        UserRow carlosRow = row("carlos");
        onUi(() -> model.delete(carlosRow));
        waitUntil(() -> !errors.isEmpty());
        assertEquals("No puedes eliminar a «Carlos Ruiz»: ya tiene cobros registrados. Desactívalo para que no "
                + "pueda entrar.", errors.getFirst());

        UserRow pedro = row("pedro");
        onUi(() -> model.delete(pedro));
        waitUntil(() -> !ok.isEmpty() && model.items().size() == 3);
        assertEquals("Se eliminó la cuenta de «Pedro Salas».", ok.getFirst());
    }

    @Test
    void permisosDeCadaRol_salenDeRole() {
        assertEquals("Puede cobrar membresías y consultar socios.", UsersViewModel.roleSummary(Role.CAJA));
        assertEquals("Puede ver el tablero, cobrar membresías, consultar socios y registrar, editar y eliminar socios.",
                UsersViewModel.roleSummary(Role.RECEPCION));
        assertTrue(UsersViewModel.roleSummary(Role.ADMINISTRADOR).startsWith("Puede hacer todo"));
        assertEquals(List.of(Role.ADMINISTRADOR, Role.RECEPCION, Role.CAJA),
                UsersViewModel.permissionMatrix().get(Permission.CHARGE_MEMBERSHIP));
        assertEquals(List.of(Role.ADMINISTRADOR), UsersViewModel.permissionMatrix().get(Permission.MANAGE_USERS));
        assertTrue(rocio.role().can(Permission.EDIT_MEMBERS));
    }
}
