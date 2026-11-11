package dev.gimnasio.sistemagimnasio.ui.login;

import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.Authenticate;
import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.AuthenticateCommand;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.Background;
import dev.gimnasio.sistemagimnasio.ui.common.ErrorReport;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Estado del formulario de acceso. Las reglas (campos vacíos, credenciales, usuario desactivado) son de
 * {@link Authenticate}; aquí solo se envía, se evita un segundo envío mientras el primero sigue en curso y
 * se reparte la respuesta entre los campos y el aviso general.
 */
public final class LoginViewModel {
    /** Campo que debe recibir el foco después de un intento fallido. */
    public enum Focus { USERNAME, PASSWORD }

    private final Authenticate authenticate;
    private final Background background;
    private final Consumer<User> onSuccess;

    private final StringProperty username = new SimpleStringProperty(this, "username", "");
    private final StringProperty password = new SimpleStringProperty(this, "password", "");
    private final ReadOnlyBooleanWrapper busy = new ReadOnlyBooleanWrapper(this, "busy", false);
    private final ReadOnlyStringWrapper generalError = new ReadOnlyStringWrapper(this, "generalError");
    private final ReadOnlyObjectWrapper<Map<String, String>> fieldErrors =
            new ReadOnlyObjectWrapper<>(this, "fieldErrors", Map.of());
    private final ObjectProperty<Focus> focusAfterFailure = new SimpleObjectProperty<>(this, "focusAfterFailure");

    public LoginViewModel(Authenticate authenticate, Background background, Consumer<User> onSuccess) {
        this.authenticate = Objects.requireNonNull(authenticate, "authenticate");
        this.background = Objects.requireNonNull(background, "background");
        this.onSuccess = Objects.requireNonNull(onSuccess, "onSuccess");
    }

    /** Ignora el envío si el anterior no ha terminado (doble clic, Enter repetido). */
    public void submit() {
        if (busy.get()) {
            return;
        }
        busy.set(true);
        generalError.set(null);
        fieldErrors.set(Map.of());
        focusAfterFailure.set(null);
        AuthenticateCommand command = new AuthenticateCommand(username.get(), password.get());
        background.run(() -> authenticate.execute(command), this::succeeded, this::failed);
    }

    private void succeeded(User user) {
        busy.set(false);
        password.set("");
        onSuccess.accept(user);
    }

    private void failed(Throwable error) {
        busy.set(false);
        // Como en el prototipo: tras un intento fallido la contraseña se vuelve a escribir.
        password.set("");
        ErrorReport report = ErrorReport.of(error);
        if (report.kind() == ErrorReport.Kind.VALIDATION) {
            fieldErrors.set(report.fieldErrors());
            focusAfterFailure.set(report.fieldErrors().containsKey("username") ? Focus.USERNAME : Focus.PASSWORD);
        } else {
            generalError.set(report.message());
            focusAfterFailure.set(Focus.PASSWORD);
        }
    }

    public StringProperty usernameProperty() {
        return username;
    }

    public StringProperty passwordProperty() {
        return password;
    }

    public ReadOnlyBooleanProperty busyProperty() {
        return busy.getReadOnlyProperty();
    }

    /** {@code null} si no hay aviso. */
    public ReadOnlyStringProperty generalErrorProperty() {
        return generalError.getReadOnlyProperty();
    }

    /** Campo → mensaje, en el orden en que se validaron. */
    public ReadOnlyObjectProperty<Map<String, String>> fieldErrorsProperty() {
        return fieldErrors.getReadOnlyProperty();
    }

    public ObjectProperty<Focus> focusAfterFailureProperty() {
        return focusAfterFailure;
    }
}
