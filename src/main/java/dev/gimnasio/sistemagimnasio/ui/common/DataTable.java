package dev.gimnasio.sistemagimnasio.ui.common;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Tabla del prototipo para listas cortas de catálogo (métodos de pago, planes, usuarios): encabezado, filas
 * con líneas finas y botones de acción reales en cada fila. A diferencia de {@code TableView}, Tab recorre los
 * botones de cada fila en orden, como en el prototipo.
 */
public final class DataTable<T> extends VBox {
    /**
     * @param header texto del encabezado; vacío para la columna de acciones
     * @param cell   el contenido de la celda para un elemento
     * @param grow   la columna toma el espacio sobrante
     * @param align  alineación horizontal (las acciones y los importes van a la derecha)
     */
    public record Column<T>(String header, Function<T, Node> cell, boolean grow, HPos align) {
        public Column {
            Objects.requireNonNull(header, "header");
            Objects.requireNonNull(cell, "cell");
            Objects.requireNonNull(align, "align");
        }

        public static <T> Column<T> grow(String header, Function<T, Node> cell) {
            return new Column<>(header, cell, true, HPos.LEFT);
        }

        public static <T> Column<T> fit(String header, Function<T, Node> cell) {
            return new Column<>(header, cell, false, HPos.LEFT);
        }

        public static <T> Column<T> actions(Function<T, Node> cell) {
            return new Column<>("", cell, false, HPos.RIGHT);
        }
    }

    private final List<Column<T>> columns;
    private final GridPane grid = new GridPane();
    private final Node empty;
    private Predicate<T> dimmed = item -> false;

    /** @param empty lo que se muestra si no hay filas */
    public DataTable(List<Column<T>> columns, Node empty) {
        this.columns = List.copyOf(columns);
        this.empty = Objects.requireNonNull(empty, "empty");
        getStyleClass().add("tabla");
        grid.getStyleClass().add("tabla-rejilla");
        for (Column<T> column : this.columns) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setHgrow(column.grow() ? Priority.ALWAYS : Priority.NEVER);
            constraints.setHalignment(column.align());
            constraints.setFillWidth(true);
            grid.getColumnConstraints().add(constraints);
        }
        getChildren().addAll(grid, empty);
        empty.managedProperty().bind(empty.visibleProperty());
        setItems(List.of());
    }

    /** Filas atenuadas, por ejemplo las inactivas. */
    public void setDimmed(Predicate<T> dimmed) {
        this.dimmed = Objects.requireNonNull(dimmed, "dimmed");
    }

    public void setItems(List<T> items) {
        grid.getChildren().clear();
        for (int c = 0; c < columns.size(); c++) {
            Label header = new Label(columns.get(c).header());
            header.setMinWidth(Label.USE_PREF_SIZE);
            grid.add(cell(header, c, "tabla-cab", columns.get(c).align()), c, 0);
        }
        for (int r = 0; r < items.size(); r++) {
            T item = items.get(r);
            boolean last = r == items.size() - 1;
            for (int c = 0; c < columns.size(); c++) {
                StackPane cell = cell(columns.get(c).cell().apply(item), c, "tabla-celda", columns.get(c).align());
                if (last) {
                    cell.getStyleClass().add("ultima-fila");
                }
                if (dimmed.test(item)) {
                    cell.getStyleClass().add("fila-inactiva");
                }
                grid.add(cell, c, r + 1);
            }
        }
        empty.setVisible(items.isEmpty());
    }

    private StackPane cell(Node content, int column, String styleClass, HPos align) {
        // El contenido conserva su alto: así queda centrado en la fila aunque otra celda sea más alta.
        if (content instanceof Region region) {
            region.setMaxHeight(Region.USE_PREF_SIZE);
        }
        StackPane cell = new StackPane(content);
        cell.getStyleClass().add(styleClass);
        if (column == 0) {
            cell.getStyleClass().add("primera-columna");
        }
        if (column == columns.size() - 1) {
            cell.getStyleClass().add("ultima-columna");
        }
        cell.setAlignment(align == HPos.RIGHT ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
        cell.setMaxWidth(Double.MAX_VALUE);
        cell.setMaxHeight(Double.MAX_VALUE);
        return cell;
    }
}
