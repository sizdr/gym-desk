package dev.gimnasio.sistemagimnasio.ui.settings;

import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;
import dev.gimnasio.sistemagimnasio.ui.common.BrandLogo;
import dev.gimnasio.sistemagimnasio.ui.common.FieldErrors;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.TextFormatter;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;

/** Pantalla de Configuración: une la vista con {@link SettingsViewModel}. */
public final class SettingsController {
    static final String SAVE = "Guardar cambios";
    static final String SAVING = "Guardando…";

    private final SettingsViewModel model;
    private final Function<Window, File> logoChooser;
    private final FieldErrors fieldErrors = new FieldErrors();
    private final HBox saveBar = new HBox();
    private final Button saveButton = new Button(SAVE);
    private final Button discardButton = new Button("Descartar");
    private StackPane currentLogo;

    @FXML
    private VBox root;
    @FXML
    private HBox loading;
    @FXML
    private HBox loadErrorNote;
    @FXML
    private Label loadErrorText;
    @FXML
    private HBox generalErrorNote;
    @FXML
    private Label generalErrorText;
    @FXML
    private VBox form;
    @FXML
    private TextField gymName;
    @FXML
    private Label gymNameError;
    @FXML
    private TextField address;
    @FXML
    private Label addressError;
    @FXML
    private TextField phone;
    @FXML
    private Label phoneError;
    @FXML
    private HBox logoPreview;
    @FXML
    private Label previewName;
    @FXML
    private Label logoStatus;
    @FXML
    private Button uploadLogo;
    @FXML
    private Button removeLogo;
    @FXML
    private Label logoError;
    @FXML
    private TextField receiptFooter;
    @FXML
    private Label receiptFooterCount;
    @FXML
    private Label receiptFooterError;
    @FXML
    private TextField warningDays;
    @FXML
    private Label warningDaysError;
    @FXML
    private TextField autoDeactivationDays;
    @FXML
    private Label autoDeactivationDaysError;
    @FXML
    private ComboBox<Currency> currency;
    @FXML
    private Label currencyHelp;
    @FXML
    private Label currencyError;
    @FXML
    private TextField folioPrefix;
    @FXML
    private Label folioHelp;
    @FXML
    private Label folioPrefixError;
    @FXML
    private Region barSpace;

    /** @param logoChooser abre el selector de archivos y devuelve el elegido, o {@code null} si se cancela */
    public SettingsController(SettingsViewModel model, Function<Window, File> logoChooser) {
        this.model = Objects.requireNonNull(model, "model");
        this.logoChooser = Objects.requireNonNull(logoChooser, "logoChooser");
    }

    /** El selector de archivos del sistema, con solo PNG y JPG. */
    public static File chooseImage(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Elegir logotipo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Imágenes PNG o JPG", "*.png", "*.jpg", "*.jpeg"));
        return chooser.showOpenDialog(owner);
    }

    @FXML
    private void initialize() {
        for (Node node : new Node[]{loading, loadErrorNote, generalErrorNote, form, barSpace, removeLogo}) {
            node.managedProperty().bind(node.visibleProperty());
        }
        loadErrorNote.getChildren().addFirst(Icons.icon("alerta"));
        generalErrorNote.getChildren().addFirst(Icons.icon("alerta"));
        uploadLogo.setGraphic(Icons.icon("subir", 16));

        loading.visibleProperty().bind(model.stateProperty().isEqualTo(SettingsViewModel.State.LOADING));
        loadErrorNote.visibleProperty().bind(model.stateProperty().isEqualTo(SettingsViewModel.State.LOAD_FAILED));
        loadErrorText.textProperty().bind(model.loadErrorProperty());
        form.visibleProperty().bind(model.stateProperty().isEqualTo(SettingsViewModel.State.READY));
        generalErrorNote.visibleProperty().bind(model.generalErrorProperty().isNotNull());
        generalErrorText.textProperty().bind(model.generalErrorProperty());

        fieldErrors.bind("gymName", gymName, gymNameError)
                .bind("address", address, addressError)
                .bind("phone", phone, phoneError)
                .bind("receiptFooter", receiptFooter, receiptFooterError)
                .bind("logo", uploadLogo, logoError)
                .bind("currency", currency, currencyError)
                .bind("warningDays", warningDays, warningDaysError)
                .bind("autoDeactivationDays", autoDeactivationDays, autoDeactivationDaysError)
                .bind("folioPrefix", folioPrefix, folioPrefixError);
        model.fieldErrorsProperty().addListener((obs, old, errors) -> fieldErrors.show(errors));

        gymName.textProperty().bindBidirectional(model.gymNameProperty());
        address.textProperty().bindBidirectional(model.addressProperty());
        phone.textProperty().bindBidirectional(model.phoneProperty());
        receiptFooter.textProperty().bindBidirectional(model.receiptFooterProperty());
        // Solo cifras: «siete» no se puede escribir; el rango lo revisa el caso de uso al guardar.
        warningDays.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d{0,3}") ? change : null));
        autoDeactivationDays.setTextFormatter(new TextFormatter<>(change -> change.getControlNewText().matches("\\d{0,3}") ? change : null));
        warningDays.textProperty().bindBidirectional(model.warningDaysProperty());
        autoDeactivationDays.textProperty().bindBidirectional(model.autoDeactivationDaysProperty());
        // El prefijo se guarda en mayúsculas: se escriben así desde el principio.
        folioPrefix.setTextFormatter(new TextFormatter<>(change -> {
            change.setText(change.getText().toUpperCase(Locale.ROOT));
            return change;
        }));
        folioPrefix.textProperty().bindBidirectional(model.folioPrefixProperty());

        receiptFooterCount.textProperty().bind(Bindings.createStringBinding(
                () -> model.receiptFooterProperty().get().length() + " de " + GymSettings.MAX_RECEIPT_FOOTER_LENGTH
                        + " caracteres.", model.receiptFooterProperty()));
        folioHelp.textProperty().bind(Bindings.createStringBinding(() -> {
            String example = model.folioExampleProperty().get();
            return (example.isEmpty() ? "" : "Ejemplo de folio: " + example + ". ") + "Los folios ya emitidos no cambian.";
        }, model.folioExampleProperty()));

        currency.itemsProperty().bind(Bindings.createObjectBinding(
                () -> javafx.collections.FXCollections.observableArrayList(model.currenciesProperty().get()),
                model.currenciesProperty()));
        currency.setCellFactory(list -> new CurrencyCell());
        currency.setButtonCell(new CurrencyCell());
        currency.valueProperty().bindBidirectional(model.currencyProperty());
        currency.disableProperty().bind(model.currencyLockedProperty());
        currencyHelp.textProperty().bind(model.currencyHelpProperty());

        model.logoProperty().addListener((obs, old, logo) -> showLogo());
        model.gymNameProperty().addListener((obs, old, name) -> showLogo());
        removeLogo.visibleProperty().bind(model.logoProperty().isNotNull());
        showLogo();

        buildSaveBar();
    }

    /** La barra «Tienes cambios sin guardar»: el menú la pone fija abajo, encima del formulario. */
    public Node saveBar() {
        return saveBar;
    }

    public VBox view() {
        return root;
    }

    /** Un panel más debajo del formulario, por ejemplo el catálogo de métodos de pago. */
    public void addPanel(Node panel) {
        root.getChildren().add(root.getChildren().indexOf(barSpace), panel);
    }

    private void buildSaveBar() {
        Label message = new Label("Tienes cambios sin guardar");
        message.getStyleClass().add("peso-600");
        Region gap = new Region();
        HBox.setHgrow(gap, javafx.scene.layout.Priority.ALWAYS);
        discardButton.setId("descartar");
        discardButton.setOnAction(e -> model.discard());
        discardButton.disableProperty().bind(model.savingProperty());
        saveButton.setId("guardar");
        saveButton.getStyleClass().add("primario");
        // Enter en cualquier campo guarda, como el formulario del prototipo.
        saveButton.defaultButtonProperty().bind(saveBar.visibleProperty());
        saveButton.setOnAction(e -> model.save());
        saveButton.disableProperty().bind(model.savingProperty());
        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("progreso-boton");
        saveButton.graphicProperty().bind(Bindings.when(model.savingProperty())
                .<Node>then(spinner).otherwise((Node) null));
        saveButton.textProperty().bind(Bindings.when(model.savingProperty()).then(SAVING).otherwise(SAVE));

        saveBar.getChildren().addAll(message, gap, discardButton, saveButton);
        saveBar.getStyleClass().add("barra-guardar");
        saveBar.setId("barra-guardar");
        saveBar.setAlignment(Pos.CENTER_LEFT);
        saveBar.setAccessibleText("Cambios sin guardar");
        saveBar.managedProperty().bind(saveBar.visibleProperty());
        saveBar.visibleProperty().bind(model.dirtyProperty()
                .and(model.stateProperty().isEqualTo(SettingsViewModel.State.READY)));
        barSpace.visibleProperty().bind(saveBar.visibleProperty());
    }

    private void showLogo() {
        String name = model.gymNameProperty().get().isBlank() ? "Sin nombre" : model.gymNameProperty().get().strip();
        byte[] logo = model.logoProperty().get();
        StackPane preview = BrandLogo.create(name, logo, 48);
        if (currentLogo != null) {
            logoPreview.getChildren().remove(currentLogo);
        }
        currentLogo = preview;
        logoPreview.getChildren().addFirst(preview);
        previewName.setText(name);
        boolean shown = preview.getStyleClass().contains("con-imagen");
        if (logo == null) {
            logoStatus.setText("Sin logotipo: se usan las iniciales");
        } else if (!shown) {
            logoStatus.setText("No se puede mostrar esta imagen");
        } else {
            logoStatus.setText(model.logoChanged() ? "Logotipo nuevo, sin guardar" : "Logotipo actual");
        }
    }

    @FXML
    private void chooseLogo() {
        File file = logoChooser.apply(root.getScene() == null ? null : root.getScene().getWindow());
        if (file == null) {
            return;
        }
        // Se lee hasta un byte más del máximo: alcanza para que el caso de uso rechace una imagen grande
        // sin cargar en memoria un archivo de cualquier tamaño.
        try (InputStream in = Files.newInputStream(file.toPath())) {
            model.useLogo(in.readNBytes(GymSettings.MAX_LOGO_BYTES + 1));
        } catch (IOException | RuntimeException e) {
            model.logoUnreadable();
        }
    }

    @FXML
    private void removeLogo() {
        model.removeLogo();
        uploadLogo.requestFocus();
    }

    @FXML
    private void retry() {
        model.load();
    }

    /** Campos por nombre, para pruebas y capturas. */
    List<Node> fields() {
        return List.of(gymName, address, phone, uploadLogo, receiptFooter, warningDays, autoDeactivationDays,
                currency, folioPrefix);
    }

    private static final class CurrencyCell extends ListCell<Currency> {
        @Override
        protected void updateItem(Currency item, boolean empty) {
            super.updateItem(item, empty);
            setText(empty || item == null ? null : SettingsViewModel.currencyLabel(item));
        }
    }
}
