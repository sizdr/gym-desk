package dev.gimnasio.sistemagimnasio.ui.dashboard;

import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.summary.DaySummary;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import dev.gimnasio.sistemagimnasio.ui.common.DataTable;
import dev.gimnasio.sistemagimnasio.ui.common.Formats;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import dev.gimnasio.sistemagimnasio.ui.members.MembersViewModel;
import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Currency;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Tablero: socios activos, por vencer, vencidos y lo cobrado hoy, con las listas para actuar. Desde cada socio
 * por vencer o vencido se abre el cobro con ese socio ya elegido.
 */
public final class DashboardController {
    private final DashboardViewModel model;
    private final Currency currency;
    private final Consumer<MemberRow> onCharge;
    private final VBox root = new VBox();
    private final Label dateLine = new Label();
    private final Label activeValue = value("tb-activos");
    private final Label activeDetail = detail();
    private final Label expiringValue = value("tb-por-vencer");
    private final Label expiringDetail = detail();
    private final Label expiredValue = value("tb-vencidas");
    private final Label expiredDetail = detail();
    private final Label incomeValue = value("tb-cobrado");
    private final Label incomeDetail = detail();
    private final VBox expiringList = new VBox();
    private final VBox expiredList = new VBox();
    private final Label expiringCount = new Label();
    private final Label expiredCount = new Label();
    private final DataTable<PaymentRow> payments;

    /**
     * @param onCharge abre el cobro con ese socio; null si el usuario no puede cobrar
     */
    public DashboardController(DashboardViewModel model, Currency currency, Consumer<MemberRow> onCharge) {
        this.model = Objects.requireNonNull(model, "model");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.onCharge = onCharge;
        Label empty = new Label("Aún no hay cobros registrados.");
        empty.getStyleClass().addAll("texto-2", "tabla-vacia");
        payments = new DataTable<>(List.of(
                DataTable.Column.fit("Folio", row -> strong(row.folio())),
                DataTable.Column.fit("Fecha", row -> plain(Formats.dateTime(row.dateTime()))),
                DataTable.Column.grow("Socio", this::memberCell),
                DataTable.Column.fit("Forma de pago", row -> plain(row.paymentMethodName())),
                new DataTable.Column<>("Monto", row -> strong(money(row.amount())), false, HPos.RIGHT)), empty);
        payments.setId("tb-tabla-cobros");
        build();
        model.summaryProperty().addListener((obs, old, summary) -> show(summary));
    }

    public Parent view() {
        return root;
    }

    private String money(Money amount) {
        return Formats.money(amount, currency);
    }

    private static Label value(String id) {
        Label label = new Label("—");
        label.getStyleClass().add("dato-valor");
        label.setId(id);
        return label;
    }

    private static Label detail() {
        Label label = new Label();
        label.getStyleClass().add("dato-detalle");
        label.setWrapText(true);
        return label;
    }

    private static Label plain(String text) {
        return new Label(text);
    }

    private static Label strong(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("peso-600");
        return label;
    }

    private Node memberCell(PaymentRow row) {
        Label plan = new Label(row.planName() + " · " + Formats.date(row.start()) + " al " + Formats.date(row.end()));
        plan.getStyleClass().add("celda-detalle");
        return new VBox(2, strong(row.memberName()), plan);
    }

    private void build() {
        Label title = new Label("Tablero");
        title.getStyleClass().add("titulo-30");
        title.setId("titulo-pagina");
        dateLine.getStyleClass().addAll("sub", "texto-2");
        dateLine.setId("tb-fecha");
        dateLine.setWrapText(true);
        Label sub = new Label("Control de vigencias: quién está cubierto, quién vence pronto y quién ya venció, con "
                + "los plazos de Configuración.");
        sub.getStyleClass().addAll("sub", "texto-2");
        sub.setWrapText(true);
        VBox headingText = new VBox(6, title, dateLine, sub);
        HBox.setHgrow(headingText, Priority.ALWAYS);
        Button refresh = new Button("Actualizar", Icons.icon("reintentar", 16));
        refresh.setId("tb-actualizar");
        refresh.disableProperty().bind(model.refreshingProperty());
        refresh.setOnAction(e -> model.load());
        refresh.setMinWidth(Region.USE_PREF_SIZE);
        HBox heading = new HBox(16, headingText, refresh);
        heading.setAlignment(Pos.TOP_LEFT);
        heading.getStyleClass().add("encabezado");

        ProgressIndicator spinner = new ProgressIndicator();
        spinner.getStyleClass().add("progreso-chico");
        HBox loading = new HBox(12, spinner, new Label("Cargando el tablero…"));
        loading.getStyleClass().addAll("panel", "cargando");
        loading.visibleProperty().bind(model.stateProperty().isEqualTo(DashboardViewModel.State.LOADING));
        loading.managedProperty().bind(loading.visibleProperty());

        Label errorText = new Label();
        errorText.textProperty().bind(model.loadErrorProperty());
        errorText.setWrapText(true);
        HBox.setHgrow(errorText, Priority.ALWAYS);
        Button retry = new Button("Reintentar");
        retry.getStyleClass().add("chico");
        retry.setOnAction(e -> model.load());
        HBox error = new HBox(10, Icons.icon("alerta"), errorText, retry);
        error.setAlignment(Pos.CENTER_LEFT);
        error.getStyleClass().addAll("nota", "nota-error");
        error.setId("tb-error");
        error.visibleProperty().bind(model.loadErrorProperty().isNotNull());
        error.managedProperty().bind(error.visibleProperty());

        GridPane tiles = new GridPane();
        tiles.getStyleClass().add("tablero-datos");
        for (int i = 0; i < 4; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(25);
            column.setFillWidth(true);
            tiles.getColumnConstraints().add(column);
        }
        tiles.addRow(0,
                tile("Socios activos", "usuarios", activeValue, activeDetail, null),
                tile("Por vencer", "calendario", expiringValue, expiringDetail, "por-vencer"),
                tile("Vencidas", "alerta", expiredValue, expiredDetail, "vencida"),
                tile("Cobrado hoy", "tarjeta", incomeValue, incomeDetail, null));

        GridPane lists = new GridPane();
        lists.getStyleClass().add("tablero-listas");
        for (int i = 0; i < 2; i++) {
            ColumnConstraints column = new ColumnConstraints();
            column.setPercentWidth(50);
            column.setFillWidth(true);
            lists.getColumnConstraints().add(column);
        }
        expiringList.setId("tb-lista-por-vencer");
        expiredList.setId("tb-lista-vencidas");
        lists.addRow(0,
                listPanel("Por vencer", expiringCount, expiringList),
                listPanel("Vencidas", expiredCount, expiredList));

        Label paymentsTitle = new Label("Últimos cobros");
        paymentsTitle.getStyleClass().add("titulo-16");
        HBox paymentsHead = new HBox(paymentsTitle);
        paymentsHead.getStyleClass().add("panel-cab");
        VBox paymentsPanel = new VBox(paymentsHead, payments);
        paymentsPanel.getStyleClass().addAll("panel", "panel-catalogo");

        VBox work = new VBox(20, tiles, lists, paymentsPanel);
        work.visibleProperty().bind(model.stateProperty().isEqualTo(DashboardViewModel.State.READY));
        work.managedProperty().bind(work.visibleProperty());

        root.getChildren().addAll(heading, loading, error, work);
        root.getStyleClass().add("contenido");
    }

    private static VBox tile(String caption, String icon, Label value, Label detail, String accent) {
        Label title = new Label(caption);
        title.getStyleClass().add("dato-titulo");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        Node glyph = Icons.icon(icon, 18);
        HBox top = new HBox(8, title, gap, glyph);
        top.setAlignment(Pos.CENTER_LEFT);
        VBox box = new VBox(6, top, value, detail);
        box.getStyleClass().addAll("panel", "dato");
        if (accent != null) {
            box.getStyleClass().add("dato-" + accent);
        }
        box.setMaxWidth(Double.MAX_VALUE);
        box.setMaxHeight(Double.MAX_VALUE);
        GridPane.setFillHeight(box, true);
        return box;
    }

    private static VBox listPanel(String caption, Label count, VBox list) {
        Label title = new Label(caption);
        title.getStyleClass().add("titulo-16");
        count.getStyleClass().add("conteo");
        HBox head = new HBox(8, title, count);
        head.setAlignment(Pos.CENTER_LEFT);
        head.getStyleClass().add("panel-cab");
        VBox panel = new VBox(head, list);
        panel.getStyleClass().addAll("panel", "panel-lista");
        panel.setMaxHeight(Double.MAX_VALUE);
        GridPane.setValignment(panel, javafx.geometry.VPos.TOP);
        return panel;
    }

    private void show(DaySummary summary) {
        if (summary == null) {
            return;
        }
        dateLine.setText(DashboardViewModel.dateLine(summary));
        activeValue.setText(String.valueOf(summary.activeMembers()));
        activeDetail.setText("Con membresía vigente hoy");
        expiringValue.setText(String.valueOf(summary.expiringCount()));
        expiringDetail.setText(DashboardViewModel.expiringDetail(summary));
        expiredValue.setText(String.valueOf(summary.expiredCount()));
        expiredDetail.setText(DashboardViewModel.expiredDetail(summary));
        incomeValue.setText(money(summary.membershipIncomeToday()));
        incomeDetail.setText(DashboardViewModel.paymentsDetail(summary));

        expiringCount.setText(String.valueOf(summary.expiringCount()));
        fill(expiringList, summary.expiring(), "Nadie está por vencer.", "Cobrar", "tb-cobrar-");
        expiredCount.setText(String.valueOf(summary.expiredCount()));
        fill(expiredList, summary.expired(), "No hay membresías vencidas.", "Renovar", "tb-renovar-");
        payments.setItems(summary.recentPayments());
    }

    private void fill(VBox list, List<MemberRow> rows, String emptyText, String action, String idPrefix) {
        list.getChildren().clear();
        if (rows.isEmpty()) {
            Label empty = new Label(emptyText);
            empty.getStyleClass().addAll("texto-2", "lista-vacia");
            list.getChildren().add(empty);
            return;
        }
        for (MemberRow row : rows) {
            Label name = new Label(row.fullName());
            name.getStyleClass().add("peso-600");
            name.setMinWidth(0);
            Label when = new Label(MembersViewModel.validity(row)
                    + (row.planName() == null ? "" : " · " + row.planName()));
            when.getStyleClass().addAll("chico", "texto-2");
            when.setWrapText(true);
            VBox text = new VBox(2, name, when);
            text.setMinWidth(0);
            HBox.setHgrow(text, Priority.ALWAYS);
            HBox line = new HBox(12, text);
            line.setAlignment(Pos.CENTER_LEFT);
            line.getStyleClass().add("fila-lista");
            if (onCharge != null) {
                Button go = new Button(action);
                go.getStyleClass().add("chico");
                go.setId(idPrefix + row.id());
                go.setAccessibleText(action + " la membresía de " + row.fullName());
                go.setMinWidth(Region.USE_PREF_SIZE);
                go.setOnAction(e -> onCharge.accept(row));
                line.getChildren().add(go);
            }
            list.getChildren().add(line);
        }
    }
}
