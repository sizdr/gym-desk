package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.AccessibleRole;
import javafx.scene.control.Toggle;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;

import java.util.EnumMap;
import java.util.Map;

/**
 * Botones de sol y luna para elegir el tema; siempre hay uno elegido. Se comportan como un grupo de opciones:
 * Tab entra al elegido y las flechas cambian de tema, igual que un clic o Espacio.
 */
public final class ThemeSelector {
    private ThemeSelector() {
    }

    public static HBox create(Themes themes) {
        ToggleGroup group = new ToggleGroup();
        Map<Themes.Theme, ToggleButton> buttons = new EnumMap<>(Themes.Theme.class);
        HBox box = new HBox();
        box.getStyleClass().add("tema");
        box.setAccessibleText("Tema de la pantalla");

        for (Themes.Theme theme : Themes.Theme.values()) {
            ToggleButton button = new ToggleButton();
            button.setGraphic(Icons.icon(theme.icon(), 16));
            button.setToggleGroup(group);
            button.setUserData(theme);
            button.setTooltip(new Tooltip(theme.label()));
            button.setAccessibleText(theme.label());
            button.setAccessibleRole(AccessibleRole.RADIO_BUTTON);
            button.setId("tema-" + theme.name().toLowerCase());
            buttons.put(theme, button);
            box.getChildren().add(button);
        }

        group.selectToggle(buttons.get(themes.current()));
        // La elección sale del grupo y no del clic: las flechas también cambian la opción elegida.
        group.selectedToggleProperty().addListener((obs, previous, selected) -> {
            if (selected == null) {
                group.selectToggle(previous); // volver a pulsar el tema actual no lo quita
            } else if (selected.getUserData() != themes.current()) {
                themes.set((Themes.Theme) selected.getUserData());
            }
        });
        themes.themeProperty().addListener((obs, old, current) -> {
            Toggle wanted = buttons.get(current);
            if (group.getSelectedToggle() != wanted) {
                group.selectToggle(wanted);
            }
        });
        return box;
    }
}
