package dev.gimnasio.sistemagimnasio.ui.login;

import dev.gimnasio.sistemagimnasio.application.usecase.gyminfo.GymInfo;
import dev.gimnasio.sistemagimnasio.ui.common.BrandLogo;
import dev.gimnasio.sistemagimnasio.ui.common.FieldErrors;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import dev.gimnasio.sistemagimnasio.ui.common.ThemeSelector;
import dev.gimnasio.sistemagimnasio.ui.common.Themes;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;

import java.util.Objects;

/** Pantalla de acceso: une la vista con {@link LoginViewModel}. */
public final class LoginController {
    static final String SUBMIT = "Entrar";
    static final String SUBMITTING = "Entrando…";

    private final GymInfo gymInfo;
    private final Themes themes;
    private final LoginViewModel model;
    private final FieldErrors fieldErrors = new FieldErrors();

    @FXML
    private HBox brand;
    @FXML
    private Label gymName;
    @FXML
    private HBox errorNote;
    @FXML
    private Label errorText;
    @FXML
    private TextField username;
    @FXML
    private Label usernameError;
    @FXML
    private PasswordField password;
    @FXML
    private Label passwordError;
    @FXML
    private Button submit;
    @FXML
    private HBox themeRow;

    public LoginController(GymInfo gymInfo, Themes themes, LoginViewModel model) {
        this.gymInfo = Objects.requireNonNull(gymInfo, "gymInfo");
        this.themes = Objects.requireNonNull(themes, "themes");
        this.model = Objects.requireNonNull(model, "model");
    }

    @FXML
    private void initialize() {
        brand.getChildren().addFirst(BrandLogo.create(gymInfo.gymName(), gymInfo.logo(), 48));
        gymName.setText(gymInfo.gymName());
        errorNote.getChildren().addFirst(Icons.icon("alerta"));
        errorNote.managedProperty().bind(errorNote.visibleProperty());
        fieldErrors.bind("username", username, usernameError).bind("password", password, passwordError);
        themeRow.getChildren().add(ThemeSelector.create(themes));

        username.textProperty().bindBidirectional(model.usernameProperty());
        password.textProperty().bindBidirectional(model.passwordProperty());

        // Mientras se revisa: el botón no se puede volver a pulsar y los campos no cambian, sin perder el foco.
        submit.disableProperty().bind(model.busyProperty());
        username.editableProperty().bind(model.busyProperty().not());
        password.editableProperty().bind(model.busyProperty().not());
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("progreso-boton");
        submit.graphicProperty().bind(javafx.beans.binding.Bindings.when(model.busyProperty())
                .<javafx.scene.Node>then(spinner).otherwise((javafx.scene.Node) null));
        submit.textProperty().bind(javafx.beans.binding.Bindings.when(model.busyProperty())
                .then(SUBMITTING).otherwise(SUBMIT));

        errorNote.visibleProperty().bind(model.generalErrorProperty().isNotNull());
        errorText.textProperty().bind(model.generalErrorProperty());
        model.fieldErrorsProperty().addListener((obs, old, errors) -> fieldErrors.show(errors));
        model.focusAfterFailureProperty().addListener((obs, old, focus) -> {
            if (focus == LoginViewModel.Focus.USERNAME) {
                username.requestFocus();
            } else if (focus == LoginViewModel.Focus.PASSWORD && model.fieldErrorsProperty().get().isEmpty()) {
                password.requestFocus();
            }
        });

        Platform.runLater(username::requestFocus);
    }

    @FXML
    private void submit() {
        model.submit();
    }
}
