package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.collections.ListChangeListener;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.PopupWindow;
import javafx.stage.Window;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.prefs.Preferences;

/**
 * Tema claro o nocturno, elegido a mano y recordado en este equipo.
 *
 * <p>El tema «según el sistema» del prototipo queda pendiente: JavaFX 21 no informa si el sistema operativo
 * está en modo oscuro (la API {@code Platform.Preferences} llegó en JavaFX 22) y actualizarlo es un cambio de
 * {@code pom.xml}, fuera de la rama de UI.
 *
 * <p>El tema se aplica como clase de estilo en la raíz de cada ventana: la principal y los diálogos (cada uno
 * tiene su propia escena). Los menús y listas desplegables toman los colores del control que los abre.
 */
public final class Themes {
    public enum Theme {
        CLARO("tema-claro", "Tema claro", "sol"),
        OSCURO("tema-oscuro", "Tema nocturno", "luna");

        private final String styleClass;
        private final String label;
        private final String icon;

        Theme(String styleClass, String label, String icon) {
            this.styleClass = styleClass;
            this.label = label;
            this.icon = icon;
        }

        public String styleClass() {
            return styleClass;
        }

        public String label() {
            return label;
        }

        public String icon() {
            return icon;
        }
    }

    /** Dónde se recuerda el tema elegido. */
    public interface Store {
        Optional<String> load();

        void save(String value);
    }

    static final Theme DEFAULT = Theme.CLARO;
    private static final List<String> STYLE_CLASSES = Arrays.stream(Theme.values()).map(Theme::styleClass).toList();
    private static final System.Logger LOG = System.getLogger(Themes.class.getName());

    private final Store store;
    private final ReadOnlyObjectWrapper<Theme> theme = new ReadOnlyObjectWrapper<>(this, "theme");

    public Themes(Store store) {
        this.store = Objects.requireNonNull(store, "store");
        theme.set(read(store));
        theme.addListener((obs, old, current) -> applyToOpenWindows());
    }

    /** Recuerda el tema en las preferencias del usuario del sistema operativo, no en la base. */
    public static Themes fromUserPreferences() {
        Preferences node = Preferences.userRoot().node("dev/gimnasio/sistemagimnasio/ui");
        return new Themes(new Store() {
            @Override
            public Optional<String> load() {
                return Optional.ofNullable(node.get("tema", null));
            }

            @Override
            public void save(String value) {
                node.put("tema", value);
            }
        });
    }

    public Theme current() {
        return theme.get();
    }

    public ReadOnlyObjectProperty<Theme> themeProperty() {
        return theme.getReadOnlyProperty();
    }

    public void set(Theme selected) {
        Objects.requireNonNull(selected, "selected");
        theme.set(selected);
        try {
            store.save(selected.name());
        } catch (RuntimeException e) {
            // No poder recordarlo no impide usarlo durante esta sesión.
            LOG.log(System.Logger.Level.WARNING, "No se pudo guardar el tema elegido", e);
        }
    }

    /** Aplica el tema a cada ventana que se abra a partir de ahora (diálogos incluidos). Hilo de JavaFX. */
    public void installOnNewWindows() {
        Window.getWindows().addListener((ListChangeListener<Window>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(this::apply);
            }
        });
        applyToOpenWindows();
    }

    public void apply(Parent root) {
        root.getStyleClass().removeAll(STYLE_CLASSES);
        root.getStyleClass().add(current().styleClass());
    }

    private void apply(Window window) {
        if (window instanceof PopupWindow) {
            return; // menús y listas: heredan del control que los abre
        }
        Scene scene = window.getScene();
        if (scene != null && scene.getRoot() != null) {
            String stylesheet = AppWindow.stylesheet();
            if (!scene.getStylesheets().contains(stylesheet)) {
                scene.getStylesheets().add(stylesheet); // diálogos creados fuera de Dialogs
            }
            apply(scene.getRoot());
        }
    }

    private void applyToOpenWindows() {
        if (javafx.application.Platform.isFxApplicationThread()) {
            List.copyOf(Window.getWindows()).forEach(this::apply);
        }
    }

    private static Theme read(Store store) {
        try {
            return store.load().flatMap(Themes::parse).orElse(DEFAULT);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "No se pudo leer el tema guardado", e);
            return DEFAULT;
        }
    }

    private static Optional<Theme> parse(String value) {
        return Arrays.stream(Theme.values()).filter(t -> t.name().equals(value)).findFirst();
    }
}
