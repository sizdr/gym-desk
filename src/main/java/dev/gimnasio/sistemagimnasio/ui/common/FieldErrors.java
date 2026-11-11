package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.scene.control.Control;
import javafx.scene.control.Label;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Une los campos de un {@code ValidationException} con sus controles: marca el control y escribe
 * el mensaje en la etiqueta que tiene debajo.
 */
public final class FieldErrors {
    static final String INVALID = "invalid";

    private record Field(Control control, Label message) {
    }

    private final Map<String, Field> fields = new LinkedHashMap<>();

    /** {@code field} es el nombre que usa el command, por ejemplo {@code "username"}. */
    public FieldErrors bind(String field, Control control, Label message) {
        Objects.requireNonNull(field, "field");
        Objects.requireNonNull(control, "control");
        Objects.requireNonNull(message, "message");
        message.managedProperty().bind(message.visibleProperty());
        message.getStyleClass().add("field-error");
        fields.put(field, new Field(control, message));
        hide(fields.get(field));
        return this;
    }

    /**
     * Marca cada campo y pone el foco en el primero con error.
     *
     * @return los mensajes de campos que esta pantalla no tiene, para mostrarlos aparte
     */
    public List<String> show(Map<String, String> errors) {
        clear();
        List<String> unbound = new ArrayList<>();
        Control first = null;
        for (var error : errors.entrySet()) {
            Field field = fields.get(error.getKey());
            if (field == null) {
                unbound.add(error.getValue());
                continue;
            }
            if (!field.control().getStyleClass().contains(INVALID)) {
                field.control().getStyleClass().add(INVALID);
            }
            field.message().setText(error.getValue());
            field.message().setVisible(true);
            if (first == null) {
                first = field.control();
            }
        }
        if (first != null) {
            first.requestFocus();
        }
        return unbound;
    }

    public void clear() {
        fields.values().forEach(FieldErrors::hide);
    }

    private static void hide(Field field) {
        field.control().getStyleClass().remove(INVALID);
        field.message().setText("");
        field.message().setVisible(false);
    }
}
