package dev.gimnasio.sistemagimnasio.ui.members;

import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.ui.common.StatusTag;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * El campo de búsqueda del encabezado y su panel de resultados. Elegir un socio abre su cobro (si quien busca
 * puede cobrar) o lo muestra en Socios; Enter o «Ver todos en Socios» abre Socios con lo escrito.
 *
 * Teclado: ↓ pasa del campo a los resultados, ↑ y ↓ se mueven entre ellos, Esc cierra y vuelve al campo.
 */
public final class QuickSearchController {
    private final QuickSearchViewModel model;
    private final Consumer<MemberRow> onChoose;
    private final Consumer<String> onShowAll;
    private final String chooseVerb;
    private final TextField field = new TextField();
    private final VBox panel = new VBox();
    private final VBox list = new VBox(2);
    private final Label count = new Label();
    private final Label message = new Label();
    private final Button showAll = new Button("Ver todos en Socios");

    /**
     * @param onChoose  qué hacer con el socio elegido
     * @param chooseVerb cómo se anuncia esa acción («Cobrar a», «Ver a»)
     * @param onShowAll abrir Socios con el texto buscado
     */
    public QuickSearchController(QuickSearchViewModel model, Consumer<MemberRow> onChoose, String chooseVerb,
                                 Consumer<String> onShowAll) {
        this.model = Objects.requireNonNull(model, "model");
        this.onChoose = Objects.requireNonNull(onChoose, "onChoose");
        this.chooseVerb = Objects.requireNonNull(chooseVerb, "chooseVerb");
        this.onShowAll = Objects.requireNonNull(onShowAll, "onShowAll");
        build();
    }

    public TextField field() {
        return field;
    }

    public Region panel() {
        return panel;
    }

    private void build() {
        field.setId("buscar-global");
        field.setPromptText("Buscar socio por nombre o teléfono");
        field.setAccessibleText("Buscar socio por nombre, correo o teléfono");
        field.getStyleClass().add("campo-busqueda");
        field.textProperty().bindBidirectional(model.textProperty());
        field.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.DOWN && model.openProperty().get()) {
                e.consume();
                focusItem(0);
            } else if (e.getCode() == KeyCode.ENTER && model.openProperty().get()) {
                e.consume();
                showAll();
            } else if (e.getCode() == KeyCode.ESCAPE && model.openProperty().get()) {
                e.consume();
                model.clear();
            }
        });

        count.getStyleClass().addAll("chico", "texto-3");
        count.setId("bg-resumen");
        Label hint = new Label(chooseVerb.startsWith("Cobrar") ? "Elige un socio para cobrarle" : "Elige un socio");
        hint.getStyleClass().addAll("chico", "texto-3");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox head = new HBox(8, hint, gap, count);
        head.getStyleClass().add("busqueda-cab");

        message.getStyleClass().addAll("texto-2", "busqueda-mensaje");
        message.setWrapText(true);
        message.setId("bg-mensaje");
        message.managedProperty().bind(message.visibleProperty());

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setFocusTraversable(false);
        scroll.getStyleClass().add("busqueda-lista");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        showAll.setId("bg-ver-todos");
        showAll.getStyleClass().addAll("chico", "texto");
        showAll.setOnAction(e -> showAll());
        showAll.addEventFilter(KeyEvent.KEY_PRESSED, this::moveWithArrows);
        HBox foot = new HBox(showAll);
        foot.setAlignment(Pos.CENTER_RIGHT);
        foot.getStyleClass().add("busqueda-pie");

        panel.getChildren().addAll(head, message, scroll, foot);
        panel.getStyleClass().addAll("panel", "busqueda-resultados");
        panel.setId("bg-panel");
        panel.visibleProperty().bind(model.openProperty());
        panel.setAccessibleText("Resultados de la búsqueda de socios");

        model.results().addListener((ListChangeListener<MemberRow>) change -> refresh());
        model.searchingProperty().addListener((obs, old, searching) -> refresh());
        model.errorProperty().addListener((obs, old, error) -> refresh());
        refresh();

        // Al salir de la búsqueda (clic o Tab fuera del campo y del panel) se cierra.
        field.sceneProperty().addListener((obs, old, scene) -> {
            if (scene != null) {
                scene.focusOwnerProperty().addListener((o, was, now) -> {
                    if (model.openProperty().get() && now != null && !inside(now)) {
                        model.clear();
                    }
                });
            }
        });
    }

    private boolean inside(Node node) {
        for (Node n = node; n != null; n = n.getParent()) {
            if (n == field || n == panel) {
                return true;
            }
        }
        return false;
    }

    private void refresh() {
        List<MemberRow> rows = List.copyOf(model.results());
        list.getChildren().clear();
        for (MemberRow row : rows) {
            list.getChildren().add(item(row));
        }
        String error = model.errorProperty().get();
        String text = model.textProperty().get() == null ? "" : model.textProperty().get().strip();
        if (error != null) {
            message.setText(error);
        } else if (rows.isEmpty() && model.searchingProperty().get()) {
            message.setText("Buscando…");
        } else if (rows.isEmpty()) {
            message.setText("Ningún socio coincide con «" + text + "».");
        }
        message.setVisible(error != null || rows.isEmpty());
        count.setText(rows.isEmpty() ? "" : QuickSearchViewModel.countText(rows.size(), model.matchCountProperty().get()));
        if (panel.getParent() != null) {
            panel.getParent().requestLayout();
        }
    }

    private Button item(MemberRow row) {
        Label name = new Label(row.fullName());
        name.getStyleClass().add("peso-600");
        name.setMinWidth(0);
        Label detail = new Label(MembersViewModel.validity(row) + (row.planName() == null ? "" : " · " + row.planName()));
        detail.getStyleClass().addAll("chico", "texto-3");
        detail.setMinWidth(0);
        VBox text = new VBox(2, name, detail);
        text.setMinWidth(0);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label tag = StatusTag.create(MembersViewModel.statusLabel(row.status()), MembersViewModel.statusStyle(row.status()));
        tag.setMinWidth(Region.USE_PREF_SIZE);
        HBox content = new HBox(10, text, tag);
        content.setAlignment(Pos.CENTER_LEFT);
        Button button = new Button();
        button.setGraphic(content);
        button.setMaxWidth(Double.MAX_VALUE);
        button.getStyleClass().add("elegible");
        button.setId("bg-socio-" + row.id());
        button.setAccessibleText(chooseVerb + " " + row.fullName() + ", " + MembersViewModel.statusLabel(row.status())
                + ", " + MembersViewModel.validity(row));
        content.prefWidthProperty().bind(button.widthProperty().subtract(24));
        button.setOnAction(e -> {
            model.clear();
            onChoose.accept(row);
        });
        button.addEventFilter(KeyEvent.KEY_PRESSED, this::moveWithArrows);
        return button;
    }

    /** ↑ y ↓ entre los resultados y «Ver todos»; ↑ desde el primero vuelve al campo; Esc cierra. */
    private void moveWithArrows(KeyEvent e) {
        List<Node> stops = new java.util.ArrayList<>(list.getChildren());
        stops.add(showAll);
        int at = stops.indexOf((Node) e.getSource());
        if (e.getCode() == KeyCode.DOWN && at < stops.size() - 1) {
            e.consume();
            stops.get(at + 1).requestFocus();
        } else if (e.getCode() == KeyCode.UP) {
            e.consume();
            if (at <= 0) {
                field.requestFocus();
                field.end();
            } else {
                stops.get(at - 1).requestFocus();
            }
        } else if (e.getCode() == KeyCode.ESCAPE) {
            e.consume();
            model.clear();
            field.requestFocus();
        }
    }

    private void focusItem(int index) {
        if (index < list.getChildren().size()) {
            list.getChildren().get(index).requestFocus();
        } else {
            showAll.requestFocus();
        }
    }

    private void showAll() {
        String text = model.textProperty().get() == null ? "" : model.textProperty().get().strip();
        model.clear();
        onShowAll.accept(text);
    }
}
