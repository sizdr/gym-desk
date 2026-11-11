package dev.gimnasio.sistemagimnasio.ui.login;

import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.Authenticate;
import dev.gimnasio.sistemagimnasio.domain.user.Role;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.support.UiFakes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoginViewModelTest {
    private static final String INVALID = "Usuario o contraseña incorrectos. Revísalos e inténtalo de nuevo.";
    private static final String DISABLED = "Tu usuario está desactivado. Pide a un administrador que lo active.";

    private final UiFakes.Users users = new UiFakes.Users();
    private final List<User> signedIn = new ArrayList<>();
    /** El «hilo de JavaFX» de la prueba: un solo hilo, para que los callbacks no se mezclen con el trabajo. */
    private final ExecutorService uiThread = Executors.newSingleThreadExecutor();
    private final Background background = Background.using(Executors.newSingleThreadExecutor(), uiThread);
    private final LoginViewModel model = new LoginViewModel(new Authenticate(users, UiFakes.HASHER), background, signedIn::add);

    @AfterEach
    void stop() {
        background.close();
        uiThread.shutdownNow();
    }

    /** Envía desde el «hilo de JavaFX» y espera a que la respuesta llegue de vuelta a él. */
    private void submitAndWait() throws Exception {
        uiThread.submit(model::submit).get(2, TimeUnit.SECONDS);
        waitUntil(() -> !model.busyProperty().get());
    }

    private void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
        for (int i = 0; i < 200; i++) {
            boolean done = uiThread.submit(condition::getAsBoolean).get(2, TimeUnit.SECONDS);
            if (done) {
                return;
            }
            Thread.sleep(10);
        }
        throw new AssertionError("no terminó a tiempo");
    }

    private void type(String username, String password) {
        model.usernameProperty().set(username);
        model.passwordProperty().set(password);
    }

    @Test
    void emptyFieldsAreReportedTogetherInOrderWithoutLookingUpTheUser() throws Exception {
        type("  ", "");

        submitAndWait();

        assertEquals(List.of("username", "password"), List.copyOf(model.fieldErrorsProperty().get().keySet()));
        assertEquals("Escribe tu nombre de usuario.", model.fieldErrorsProperty().get().get("username"));
        assertNull(model.generalErrorProperty().get());
        assertEquals(LoginViewModel.Focus.USERNAME, model.focusAfterFailureProperty().get());
        assertEquals(0, users.lookups);
    }

    @Test
    void onlyAMissingPasswordFocusesThePassword() throws Exception {
        users.add("Ana López", "ana", Role.CAJA, true);
        type("ana", "");

        submitAndWait();

        assertEquals(List.of("password"), List.copyOf(model.fieldErrorsProperty().get().keySet()));
        assertEquals(LoginViewModel.Focus.PASSWORD, model.focusAfterFailureProperty().get());
    }

    @Test
    void wrongPasswordKeepsTheUsernameAndClearsThePassword() throws Exception {
        users.add("Ana López", "ana", Role.CAJA, true);
        type("ana", "otra-cosa");

        submitAndWait();

        assertEquals(INVALID, model.generalErrorProperty().get());
        assertTrue(model.fieldErrorsProperty().get().isEmpty());
        assertEquals("ana", model.usernameProperty().get());
        assertEquals("", model.passwordProperty().get());
        assertEquals(LoginViewModel.Focus.PASSWORD, model.focusAfterFailureProperty().get());
        assertTrue(signedIn.isEmpty());
    }

    @Test
    void unknownUserGetsTheSameMessageAsAWrongPassword() throws Exception {
        type("nadie", UiFakes.PASSWORD);

        submitAndWait();

        assertEquals(INVALID, model.generalErrorProperty().get());
    }

    @Test
    void disabledUserIsToldSo() throws Exception {
        users.add("Luis Pérez", "luis", Role.RECEPCION, false);
        type("luis", UiFakes.PASSWORD);

        submitAndWait();

        assertEquals(DISABLED, model.generalErrorProperty().get());
        assertTrue(signedIn.isEmpty());
    }

    @Test
    void correctCredentialsSignInAndForgetThePassword() throws Exception {
        User ana = users.add("Ana López", "ana", Role.ADMINISTRADOR, true);
        type("  ANA ", UiFakes.PASSWORD);

        submitAndWait();

        assertEquals(List.of(ana), signedIn);
        assertEquals("", model.passwordProperty().get());
        assertNull(model.generalErrorProperty().get());
    }

    @Test
    void aNewAttemptClearsThePreviousMessages() throws Exception {
        users.add("Ana López", "ana", Role.ADMINISTRADOR, true);
        type("ana", "mala");
        submitAndWait();
        type("", "");
        submitAndWait();

        assertNull(model.generalErrorProperty().get(), "el aviso anterior se quita");
        assertFalse(model.fieldErrorsProperty().get().isEmpty());
    }

    @Test
    void submittingAgainWhileCheckingIsIgnored() throws Exception {
        users.add("Ana López", "ana", Role.ADMINISTRADOR, true);
        users.gate = new CountDownLatch(1);
        type("ana", UiFakes.PASSWORD);

        uiThread.submit(model::submit).get(2, TimeUnit.SECONDS);
        waitUntil(() -> users.lookups == 1);
        assertTrue(uiThread.submit(() -> model.busyProperty().get()).get(), "queda ocupado mientras revisa");
        uiThread.submit(model::submit).get(2, TimeUnit.SECONDS);
        uiThread.submit(model::submit).get(2, TimeUnit.SECONDS);
        users.gate.countDown();
        waitUntil(() -> !model.busyProperty().get());

        assertEquals(1, users.lookups, "solo se revisó una vez");
        assertEquals(1, signedIn.size(), "solo se inició sesión una vez");
    }

    @Test
    void aTechnicalFailureShowsTheGenericMessage() throws Exception {
        UiFakes.Users broken = new UiFakes.Users();
        broken.failure = new IllegalStateException("base caída");
        var failing = new LoginViewModel(new Authenticate(broken, UiFakes.HASHER), background, signedIn::add);
        failing.usernameProperty().set("ana");
        failing.passwordProperty().set(UiFakes.PASSWORD);

        uiThread.submit(failing::submit).get(2, TimeUnit.SECONDS);
        waitUntil(() -> failing.generalErrorProperty().get() != null);

        assertTrue(failing.generalErrorProperty().get().startsWith("Ocurrió un error inesperado"));
        assertFalse(failing.busyProperty().get());
    }
}
