package dev.gimnasio.sistemagimnasio.ui.shell;

import dev.gimnasio.sistemagimnasio.application.usecase.gyminfo.GymInfo;
import dev.gimnasio.sistemagimnasio.domain.user.User;
import dev.gimnasio.sistemagimnasio.ui.common.BrandLogo;
import dev.gimnasio.sistemagimnasio.ui.common.Dialogs;
import dev.gimnasio.sistemagimnasio.ui.common.Formats;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import dev.gimnasio.sistemagimnasio.ui.common.ThemeSelector;
import dev.gimnasio.sistemagimnasio.ui.common.Themes;
import javafx.application.Platform;
import javafx.css.PseudoClass;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Menú lateral, cabecera y área de contenido una vez iniciada la sesión. */
public final class ShellController {
    private static final PseudoClass CURRENT = PseudoClass.getPseudoClass("actual");

    private final GymInfo gymInfo;
    private final User user;
    private final Themes themes;
    private final Runnable onSignOut;
    private final Map<Route, Button> navButtons = new EnumMap<>(Route.class);
    private Route current;

    @FXML
    private VBox rail;
    @FXML
    private HBox brand;
    @FXML
    private Label gymName;
    @FXML
    private VBox nav;
    @FXML
    private ScrollPane navScroll;
    @FXML
    private Label avatar;
    @FXML
    private Label userName;
    @FXML
    private Label userRole;
    @FXML
    private Button signOut;
    @FXML
    private HBox search;
    @FXML
    private Label searchPending;
    @FXML
    private HBox sessionCapsule;
    @FXML
    private Label today;
    @FXML
    private ScrollPane scroll;
    @FXML
    private StackPane content;

    public ShellController(GymInfo gymInfo, User user, Themes themes, Runnable onSignOut) {
        this.gymInfo = Objects.requireNonNull(gymInfo, "gymInfo");
        this.user = Objects.requireNonNull(user, "user");
        this.themes = Objects.requireNonNull(themes, "themes");
        this.onSignOut = Objects.requireNonNull(onSignOut, "onSignOut");
    }

    @FXML
    private void initialize() {
        brand.getChildren().addFirst(BrandLogo.create(gymInfo.gymName(), gymInfo.logo(), 36));
        gymName.setText(gymInfo.gymName());
        buildNav();

        avatar.setText(BrandLogo.initials(user.name()));
        userName.setText(user.name());
        userRole.setText(user.role().displayName() + " · " + user.username());
        signOut.setGraphic(Icons.icon("salir", 16));

        search.getChildren().addFirst(Icons.icon("buscar"));
        search.setAccessibleText("Búsqueda de socios: pendiente, falta el caso de uso SearchMembers.");
        Tooltip.install(searchPending, new Tooltip("Falta el caso de uso SearchMembers."));

        today.setText(Formats.mediumDate(gymInfo.today()));
        Button shortcuts = new Button();
        shortcuts.setId("atajos");
        shortcuts.getStyleClass().add("boton-icono");
        shortcuts.setGraphic(Icons.icon("teclado"));
        shortcuts.setTooltip(new Tooltip("Atajos de teclado (?)"));
        shortcuts.setAccessibleText("Atajos de teclado");
        shortcuts.setOnAction(e -> showShortcuts());
        sessionCapsule.getChildren().addAll(ThemeSelector.create(themes), shortcuts);

        // «?» abre los atajos desde cualquier lugar, salvo mientras se escribe en un campo.
        root().addEventHandler(KeyEvent.KEY_TYPED, e -> {
            if ("?".equals(e.getCharacter()) && !(e.getTarget() instanceof TextInputControl)) {
                e.consume();
                showShortcuts();
            }
        });

        navigate(Navigation.start(user.role()));
        focusWhenShown(navButtons.get(current));
        // Si la lista se desplaza, la sección con el foco queda a la vista (Tab hacia abajo del menú).
        navButtons.values().forEach(button -> button.focusedProperty().addListener((obs, was, focused) -> {
            if (focused) {
                Platform.runLater(() -> revealInNav(button));
            }
        }));
    }

    private void buildNav() {
        for (Navigation.Group group : Navigation.groups(user.role())) {
            VBox box = new VBox();
            box.getStyleClass().add("nav-grupo");
            if (group.title() != null) {
                Label title = new Label(group.title());
                title.getStyleClass().add("nav-titulo");
                box.getChildren().add(title);
            }
            for (Route route : group.routes()) {
                Button button = new Button(route.menuLabel(), Icons.icon(route.icon()));
                button.getStyleClass().add("nav-enlace");
                button.setId("nav-" + route.name().toLowerCase());
                button.setMaxWidth(Double.MAX_VALUE);
                button.setOnAction(e -> navigate(route));
                navButtons.put(route, button);
                box.getChildren().add(button);
            }
            nav.getChildren().add(box);
        }
    }

    /** Muestra una sección del menú del rol; las demás no existen para él. */
    public void navigate(Route route) {
        if (!navButtons.containsKey(route)) {
            throw new IllegalArgumentException("El rol " + user.role() + " no ve " + route);
        }
        if (current != null) {
            navButtons.get(current).pseudoClassStateChanged(CURRENT, false);
        }
        current = route;
        Button button = navButtons.get(route);
        button.pseudoClassStateChanged(CURRENT, true);
        button.setAccessibleHelp("Sección actual");
        content.getChildren().setAll(PendingView.create(route));
        scroll.setVvalue(0);
    }

    /**
     * El {@code ScrollPane} del menú agrega su contenido a la escena cuando crea su skin, después de cargar la
     * vista: el foco se pide cuando el botón ya está en la escena.
     */
    private static void focusWhenShown(Button button) {
        if (button.getScene() != null) {
            Platform.runLater(button::requestFocus);
            return;
        }
        button.sceneProperty().addListener(new javafx.beans.value.ChangeListener<>() {
            @Override
            public void changed(javafx.beans.value.ObservableValue<? extends javafx.scene.Scene> obs,
                                javafx.scene.Scene old, javafx.scene.Scene scene) {
                if (scene != null) {
                    button.sceneProperty().removeListener(this);
                    Platform.runLater(button::requestFocus);
                }
            }
        });
    }

    private void revealInNav(Button button) {
        // Medidas de layout: los límites «en local» incluyen la sombra de la sección actual.
        double contentHeight = nav.getLayoutBounds().getHeight();
        double viewport = navScroll.getViewportBounds().getHeight();
        double range = contentHeight - viewport;
        if (range <= 0) {
            return;
        }
        javafx.geometry.Bounds bounds = nav.sceneToLocal(button.localToScene(button.getLayoutBounds()));
        double top = navScroll.getVvalue() * range;
        if (bounds.getMinY() < top) {
            navScroll.setVvalue(Math.max(0, bounds.getMinY() / range));
        } else if (bounds.getMaxY() > top + viewport) {
            navScroll.setVvalue(Math.min(1, (bounds.getMaxY() - viewport) / range));
        }
    }

    public Route current() {
        return current;
    }

    private void showShortcuts() {
        Dialogs.shortcuts(rail.getScene().getWindow());
    }

    @FXML
    private void signOut() {
        onSignOut.run();
    }

    /** Raíz de la vista, para pruebas. */
    BorderPane root() {
        return (BorderPane) rail.getParent();
    }
}
