package dev.gimnasio.sistemagimnasio.ui.billing;

import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargeMembershipResult;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.ChargePreview;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PaymentRow;
import dev.gimnasio.sistemagimnasio.application.usecase.billing.PlanOption;
import dev.gimnasio.sistemagimnasio.application.usecase.members.MemberRow;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.PaymentMethodItem;
import dev.gimnasio.sistemagimnasio.domain.member.MemberStatus;
import dev.gimnasio.sistemagimnasio.domain.member.SavingsStatus;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;
import dev.gimnasio.sistemagimnasio.ui.common.FieldErrors;
import dev.gimnasio.sistemagimnasio.ui.common.Formats;
import dev.gimnasio.sistemagimnasio.ui.common.Icons;
import dev.gimnasio.sistemagimnasio.ui.common.StatusTag;
import dev.gimnasio.sistemagimnasio.ui.members.MembersViewModel;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Currency;
import java.util.Objects;

/** Pantalla Cobrar membresía: socio, plan y pago a la izquierda; resumen en vivo y recibo a la derecha. */
public final class ChargeController {
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ChargeViewModel model;
    private final Currency currency;
    private final VBox root = new VBox();
    private final FieldErrors fieldErrors = new FieldErrors();
    private final VBox memberArea = new VBox(8);
    private final FlowPane planOptions = new FlowPane(8, 8);
    private final ToggleGroup planGroup = new ToggleGroup();
    private final FlowPane methodOptions = new FlowPane(4, 4);
    private final ToggleGroup methodGroup = new ToggleGroup();
    private final VBox summary = new VBox(12);
    private final VBox receipt = new VBox(12);
    private final TextField memberSearch = new TextField();

    public ChargeController(ChargeViewModel model, Currency currency) {
        this.model = Objects.requireNonNull(model, "model");
        this.currency = Objects.requireNonNull(currency, "currency");
        build();
    }

    public Parent view() {
        return root;
    }

    /** «1 día», «30 días». */
    static String days(int days) {
        return days == 1 ? "1 día" : days + " días";
    }

    private String money(Money amount) {
        return amount == null ? "—" : Formats.money(amount, currency);
    }

    private void build() {
        Label title = new Label("Cobrar membresía");
        title.getStyleClass().add("titulo-30");
        title.setId("titulo-pagina");
        Label sub = new Label("Elige al socio, el plan y la forma de pago. El periodo y el tipo de cobro los calcula "
                + "el sistema; el monto es siempre el precio del plan.");
        sub.getStyleClass().addAll("sub", "texto-2");
        sub.setWrapText(true);
        VBox heading = new VBox(6, title, sub);
        heading.getStyleClass().add("encabezado");

        HBox loading = new HBox(12, progress(), new Label("Cargando planes y métodos de pago…"));
        loading.getStyleClass().addAll("panel", "cargando");
        loading.visibleProperty().bind(model.stateProperty().isEqualTo(ChargeViewModel.State.LOADING));
        loading.managedProperty().bind(loading.visibleProperty());
        Label loadErrorText = new Label();
        loadErrorText.textProperty().bind(model.loadErrorProperty());
        loadErrorText.setWrapText(true);
        Button retry = new Button("Reintentar");
        retry.getStyleClass().add("chico");
        retry.setOnAction(e -> model.load());
        HBox loadError = new HBox(10, Icons.icon("alerta"), loadErrorText, retry);
        loadError.getStyleClass().addAll("nota", "nota-error");
        loadError.visibleProperty().bind(model.stateProperty().isEqualTo(ChargeViewModel.State.LOAD_FAILED));
        loadError.managedProperty().bind(loadError.visibleProperty());

        VBox form = buildForm();
        HBox.setHgrow(form, Priority.ALWAYS);
        VBox side = buildSide();
        HBox work = new HBox(20, form, side);
        work.setAlignment(Pos.TOP_LEFT);
        work.visibleProperty().bind(model.stateProperty().isEqualTo(ChargeViewModel.State.READY));
        work.managedProperty().bind(work.visibleProperty());

        root.getChildren().addAll(heading, loading, loadError, work);
        root.getStyleClass().add("contenido");

        model.fieldErrorsProperty().addListener((obs, old, errors) -> fieldErrors.show(errors));
    }

    // ---------- Formulario ----------

    private VBox buildForm() {
        Label memberError = new Label();
        memberSearch.setId("co-buscar");
        memberSearch.setPromptText("Nombre, correo o teléfono");
        memberSearch.setAccessibleText("Buscar al socio que va a pagar");
        memberSearch.textProperty().bindBidirectional(model.memberQueryProperty());
        fieldErrors.bind("memberId", memberSearch, memberError);
        model.memberProperty().addListener((obs, old, member) -> showMember());
        model.memberResults().addListener((ListChangeListener<MemberRow>) change -> showMember());
        showMember();
        VBox memberSection = section("1. Socio", memberArea, memberError);

        Label planError = new Label();
        planOptions.setId("co-planes");
        model.plans().addListener((ListChangeListener<PlanOption>) change -> buildPlans());
        planGroup.selectedToggleProperty().addListener((obs, old, selected) ->
                model.planProperty().set(selected == null ? null : (PlanOption) selected.getUserData()));
        model.planProperty().addListener((obs, old, plan) -> {
            if (plan == null && planGroup.getSelectedToggle() != null) {
                planGroup.selectToggle(null);
            }
        });
        buildPlans();
        loose("planId", planError);
        VBox planSection = section("2. Plan", planOptions, planError);

        Label methodError = new Label();
        methodOptions.getStyleClass().add("segmentado");
        methodOptions.setId("co-metodos");
        model.methods().addListener((ListChangeListener<PaymentMethodItem>) change -> buildMethods());
        methodGroup.selectedToggleProperty().addListener((obs, old, selected) -> {
            if (selected == null && old != null && model.resultProperty().get() == null && model.methodProperty().get() != null) {
                old.setSelected(true);
                return;
            }
            model.methodProperty().set(selected == null ? null : (PaymentMethodItem) selected.getUserData());
        });
        buildMethods();
        loose("paymentMethodId", methodError);

        TextField cash = new TextField();
        cash.setId("co-efectivo");
        cash.setPromptText("Por ejemplo 500");
        cash.setAccessibleText("Efectivo recibido");
        cash.setMaxWidth(220);
        cash.textProperty().bindBidirectional(model.cashTextProperty());
        Label cashLabel = new Label("Efectivo recibido");
        cashLabel.setLabelFor(cash);
        Label cashError = new Label();
        fieldErrors.bind("cashReceived", cash, cashError);
        VBox cashField = new VBox(6, cashLabel, cash, cashError);
        cashField.getStyleClass().add("campo");
        cashField.visibleProperty().bind(Bindings.createBooleanBinding(
                () -> model.methodProperty().get() != null && model.methodProperty().get().cash(), model.methodProperty()));
        cashField.managedProperty().bind(cashField.visibleProperty());
        VBox methodSection = section("3. Pago", new VBox(12, methodOptions, cashField), methodError);

        DatePicker start = new DatePicker();
        start.setId("co-inicio");
        start.setAccessibleText("Inicio de la primera membresía");
        start.setConverter(new DayConverter());
        start.setPromptText("Hoy");
        start.valueProperty().bindBidirectional(model.startProperty());
        Label startError = new Label();
        fieldErrors.bind("start", start, startError);
        Label startHelp = new Label("Solo en la primera membresía: si no eliges, empieza hoy. No puede ser anterior a "
                + "la fecha de alta.");
        startHelp.getStyleClass().add("ayuda");
        startHelp.setWrapText(true);
        VBox startSection = section("Inicio", new VBox(6, start, startHelp), startError);
        startSection.visibleProperty().bind(Bindings.createBooleanBinding(() -> {
            ChargePreview preview = model.previewProperty().get();
            return preview != null && preview.kind() == dev.gimnasio.sistemagimnasio.domain.billing.ChargeKind.PRIMERA;
        }, model.previewProperty()));
        startSection.managedProperty().bind(startSection.visibleProperty());

        VBox form = new VBox(6, memberSection, planSection, methodSection, startSection);
        form.getStyleClass().addAll("panel", "formulario-cobro");
        form.disableProperty().bind(model.resultProperty().isNotNull().or(model.chargingProperty()));
        form.setMinWidth(380);
        return form;
    }

    /** Errores de una elección (plan, método) que no tienen un campo de texto que marcar. */
    private void loose(String field, Label message) {
        message.getStyleClass().add("field-error");
        message.setWrapText(true);
        message.managedProperty().bind(message.visibleProperty());
        message.setVisible(false);
        model.fieldErrorsProperty().addListener((obs, old, errors) -> {
            String text = errors.get(field);
            message.setText(text == null ? "" : text);
            message.setVisible(text != null);
        });
    }

    private VBox section(String title, Node content, Label error) {
        Label caption = new Label(title);
        caption.getStyleClass().add("titulo-16");
        VBox box = new VBox(10, caption, content, error);
        box.getStyleClass().add("seccion-cobro");
        return box;
    }

    private void showMember() {
        MemberRow chosen = model.memberProperty().get();
        memberArea.getChildren().clear();
        if (chosen != null) {
            Label name = new Label(chosen.fullName());
            name.getStyleClass().add("titulo-16");
            String label = MembersViewModel.statusLabel(chosen.status());
            String detail = MembersViewModel.validity(chosen);
            Label validity = new Label((detail.equals(label) ? "Será su primera membresía" : detail)
                    + (chosen.planName() == null ? "" : " · " + chosen.planName()));
            validity.getStyleClass().addAll("chico", "texto-2");
            validity.setMinWidth(0);
            HBox meta = new HBox(10, StatusTag.create(MembersViewModel.statusLabel(chosen.status()),
                    statusKind(chosen.status())), validity);
            meta.setAlignment(Pos.CENTER_LEFT);
            VBox text = new VBox(4, name, meta);
            HBox.setHgrow(text, Priority.ALWAYS);
            Button change = new Button("Cambiar");
            change.setId("co-cambiar");
            change.getStyleClass().addAll("chico", "texto");
            change.setMinWidth(Region.USE_PREF_SIZE);
            change.setOnAction(e -> {
                model.clearMember();
                memberSearch.requestFocus();
            });
            HBox card = new HBox(12, text, change);
            card.getStyleClass().add("ficha-mini");
            card.setAlignment(Pos.CENTER_LEFT);
            card.setId("co-socio");
            memberArea.getChildren().add(card);
            return;
        }
        HBox searchBox = new HBox(8, Icons.icon("buscar"), memberSearch);
        searchBox.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(memberSearch, Priority.ALWAYS);
        memberArea.getChildren().add(searchBox);
        if (!model.memberResults().isEmpty()) {
            VBox list = new VBox();
            list.getStyleClass().add("lista-elegible");
            for (MemberRow row : model.memberResults()) {
                Label name = new Label(row.fullName());
                name.getStyleClass().add("peso-600");
                Label detail = new Label(MembersViewModel.validity(row));
                detail.getStyleClass().addAll("chico", "texto-3");
                VBox text = new VBox(2, name, detail);
                HBox.setHgrow(text, Priority.ALWAYS);
                HBox content = new HBox(10, text, StatusTag.create(MembersViewModel.statusLabel(row.status()),
                        statusKind(row.status())));
                content.setAlignment(Pos.CENTER_LEFT);
                Button pick = new Button();
                pick.setGraphic(content);
                pick.setMaxWidth(Double.MAX_VALUE);
                pick.getStyleClass().add("elegible");
                pick.setId("co-socio-" + row.id());
                pick.setAccessibleText("Cobrar a " + row.fullName());
                pick.setOnAction(e -> model.chooseMember(row));
                content.prefWidthProperty().bind(pick.widthProperty().subtract(28));
                list.getChildren().add(pick);
            }
            memberArea.getChildren().add(list);
        } else if (!model.memberQueryProperty().get().isBlank()) {
            Label none = new Label("Ningún socio coincide. Regístralo primero en Socios.");
            none.getStyleClass().addAll("chico", "texto-3");
            memberArea.getChildren().add(none);
        }
    }

    private static String statusKind(MemberStatus status) {
        return switch (status) {
            case VIGENTE -> "vigente";
            case POR_VENCER -> "por-vencer";
            case VENCIDA -> "vencida";
            case BAJA -> "baja";
            case SIN_MEMBRESIA -> "sin-membresia";
        };
    }

    private void buildPlans() {
        planOptions.getChildren().clear();
        planGroup.getToggles().clear();
        for (PlanOption plan : model.plans()) {
            Label name = new Label(plan.name());
            name.getStyleClass().add("plan-nombre");
            Label price = new Label(money(plan.price()));
            price.getStyleClass().add("plan-precio");
            Region gap = new Region();
            HBox.setHgrow(gap, Priority.ALWAYS);
            HBox top = new HBox(8, name, gap, price);
            Label meta = new Label(days(plan.durationDays()));
            meta.getStyleClass().add("plan-meta");
            Label saving = new Label(plan.savings() != null && plan.savings().status() == SavingsStatus.AHORRO
                    ? "Ahorra " + money(plan.savings().amount()) : "");
            saving.getStyleClass().add("plan-ahorro");
            Region gap2 = new Region();
            HBox.setHgrow(gap2, Priority.ALWAYS);
            HBox bottom = new HBox(8, meta, gap2, saving);
            VBox card = new VBox(2, top, bottom);
            card.setPrefWidth(200);
            ToggleButton option = new ToggleButton();
            option.setGraphic(card);
            option.setToggleGroup(planGroup);
            option.setUserData(plan);
            option.setId("co-plan-" + plan.id());
            option.getStyleClass().add("plan-opcion");
            option.setAccessibleText(plan.name() + ", " + days(plan.durationDays()) + ", " + money(plan.price()));
            if (model.planProperty().get() != null && model.planProperty().get().id() == plan.id()) {
                option.setSelected(true);
            }
            planOptions.getChildren().add(option);
        }
    }

    private void buildMethods() {
        methodOptions.getChildren().clear();
        methodGroup.getToggles().clear();
        for (PaymentMethodItem method : model.methods()) {
            ToggleButton option = new ToggleButton(method.name());
            option.setToggleGroup(methodGroup);
            option.setUserData(method);
            option.setId("co-metodo-" + method.id());
            if (model.methodProperty().get() != null && model.methodProperty().get().id() == method.id()) {
                option.setSelected(true);
            }
            methodOptions.getChildren().add(option);
        }
        // El primero queda elegido: casi siempre es Efectivo.
        if (methodGroup.getSelectedToggle() == null && !methodGroup.getToggles().isEmpty()) {
            methodGroup.selectToggle(methodGroup.getToggles().getFirst());
        }
    }

    // ---------- Resumen y recibo ----------

    private VBox buildSide() {
        summary.getStyleClass().addAll("panel", "resumen-cobro");
        summary.setId("co-resumen");
        receipt.getStyleClass().addAll("panel", "resumen-cobro");
        receipt.setId("co-recibo");
        model.previewProperty().addListener((obs, old, preview) -> showSummary());
        model.chargingProperty().addListener((obs, old, charging) -> showSummary());
        model.generalErrorProperty().addListener((obs, old, error) -> showSummary());
        model.resultProperty().addListener((obs, old, result) -> showReceipt());
        showSummary();
        showReceipt();
        VBox side = new VBox(summary, receipt);
        side.setMinHeight(Region.USE_PREF_SIZE);
        side.setMinWidth(300);
        side.setPrefWidth(340);
        side.setMaxWidth(380);
        return side;
    }

    private void showSummary() {
        boolean done = model.resultProperty().get() != null;
        summary.setVisible(!done);
        summary.setManaged(!done);
        ChargePreview preview = model.previewProperty().get();
        summary.getChildren().clear();
        Label title = new Label("Resumen");
        title.getStyleClass().add("titulo-16");
        summary.getChildren().add(title);
        GridPane rows = new GridPane();
        rows.getStyleClass().add("resumen-filas");
        int r = 0;
        r = row(rows, r, "Socio", preview == null || preview.member() == null ? "—" : preview.member().fullName());
        r = row(rows, r, "Plan", preview == null || preview.plan() == null ? "—"
                : preview.plan().name() + " · " + days(preview.plan().durationDays()));
        r = row(rows, r, "Periodo", period(preview));
        r = row(rows, r, "Forma de pago", preview == null || preview.paymentMethod() == null ? "—"
                : preview.paymentMethod().name());
        if (preview != null && preview.cashReceived() != null) {
            r = row(rows, r, "Recibido", money(preview.cashReceived()));
        }
        summary.getChildren().add(rows);
        if (preview != null && preview.kind() != null) {
            Label rule = new Label(ChargeViewModel.explain(preview.kind(), preview.newStart()));
            rule.setWrapText(true);
            rule.setMinHeight(Region.USE_PREF_SIZE);
            HBox note = new HBox(10, Icons.icon("info"), rule);
            note.getStyleClass().add("nota");
            rule.setId("co-regla");
            summary.getChildren().add(note);
        }
        Label totalCaption = new Label("Total");
        totalCaption.getStyleClass().add("peso-600");
        Label total = new Label(money(preview == null ? Money.ZERO : preview.total()));
        total.getStyleClass().add("total-cobro");
        total.setId("co-total");
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        HBox totalRow = new HBox(totalCaption, gap, total);
        totalRow.setAlignment(Pos.BASELINE_LEFT);
        summary.getChildren().add(totalRow);
        if (preview != null && preview.change() != null) {
            summary.getChildren().add(amountLine("Cambio", money(preview.change()), "cambio", "co-cambio"));
        } else if (preview != null && preview.missing() != null) {
            summary.getChildren().add(amountLine("Faltan", money(preview.missing()), "falta", "co-falta"));
        }

        Button charge = new Button();
        charge.setId("co-cobrar");
        charge.getStyleClass().addAll("primario", "grande", "ancho");
        charge.setMaxWidth(Double.MAX_VALUE);
        charge.setDefaultButton(true);
        boolean ready = preview != null && preview.ready();
        charge.setDisable(!ready || model.chargingProperty().get());
        if (model.chargingProperty().get()) {
            ProgressIndicator spinner = new ProgressIndicator();
            spinner.getStyleClass().add("progreso-boton");
            charge.setGraphic(spinner);
            charge.setText("Cobrando…");
        } else {
            charge.setText(ready ? "Cobrar " + money(preview.total()) : "Cobrar");
        }
        charge.setOnAction(e -> model.charge());
        summary.getChildren().add(charge);
        String missing = ready ? "" : ChargeViewModel.missing(preview);
        if (!missing.isEmpty()) {
            Label hint = new Label(missing);
            hint.getStyleClass().addAll("chico", "texto-3");
            hint.setWrapText(true);
            hint.setId("co-falta-dato");
            summary.getChildren().add(hint);
        }
        if (model.generalErrorProperty().get() != null) {
            Label error = new Label(model.generalErrorProperty().get());
            error.setWrapText(true);
            HBox note = new HBox(10, Icons.icon("alerta"), error);
            note.getStyleClass().addAll("nota", "nota-error");
            summary.getChildren().add(note);
        }
    }

    /** Sin plan todavía se conoce el inicio, pero no el fin. */
    static String period(ChargePreview preview) {
        if (preview == null || preview.newStart() == null) {
            return "—";
        }
        return preview.newEnd() == null ? "Desde el " + DAY.format(preview.newStart())
                : DAY.format(preview.newStart()) + " al " + DAY.format(preview.newEnd());
    }

    private HBox amountLine(String caption, String value, String style, String id) {
        Label left = new Label(caption);
        Label right = new Label(value);
        right.getStyleClass().add(style);
        right.setId(id);
        Region gap = new Region();
        HBox.setHgrow(gap, Priority.ALWAYS);
        return new HBox(left, gap, right);
    }

    private static int row(GridPane grid, int r, String caption, String value) {
        Label left = new Label(caption);
        left.getStyleClass().add("texto-2");
        left.setMinWidth(Region.USE_PREF_SIZE);
        Label right = new Label(value);
        right.getStyleClass().add("peso-500");
        right.setWrapText(true);
        GridPane.setHgrow(right, Priority.ALWAYS);
        right.setMaxWidth(Double.MAX_VALUE);
        right.setAlignment(Pos.CENTER_RIGHT);
        right.setTextAlignment(javafx.scene.text.TextAlignment.RIGHT);
        grid.addRow(r, left, right);
        return r + 1;
    }

    private void showReceipt() {
        ChargeMembershipResult result = model.resultProperty().get();
        receipt.setVisible(result != null);
        receipt.setManaged(result != null);
        receipt.getChildren().clear();
        if (result == null) {
            return;
        }
        showSummary();
        PaymentRow payment = result.payment();
        HBox done = new HBox(10, Icons.icon("check"), new Label("Cobro registrado"));
        done.getStyleClass().addAll("nota", "nota-ok");
        Label folio = new Label(payment.folio());
        folio.getStyleClass().add("titulo-24");
        folio.setId("co-folio");
        GridPane rows = new GridPane();
        rows.getStyleClass().add("resumen-filas");
        int r = 0;
        r = row(rows, r, "Fecha", DAY_TIME.format(payment.dateTime()));
        r = row(rows, r, "Socio", payment.memberName());
        r = row(rows, r, "Plan", payment.planName() + " · " + days(result.durationDays()));
        r = row(rows, r, "Periodo", DAY.format(payment.start()) + " al " + DAY.format(payment.end()));
        r = row(rows, r, "Forma de pago", payment.paymentMethodName());
        r = row(rows, r, "Monto", money(payment.amount()));
        if (result.cashReceived() != null) {
            r = row(rows, r, "Recibido", money(result.cashReceived()));
            r = row(rows, r, "Cambio", money(result.change()));
        }
        row(rows, r, "Atendió", payment.cashierName());
        Button print = new Button("Imprimir recibo", Icons.icon("impresora", 16));
        print.setId("co-imprimir");
        print.setMaxWidth(Double.MAX_VALUE);
        print.disableProperty().bind(model.printingProperty());
        print.setOnAction(e -> model.printReceipt());
        Button again = new Button("Nuevo cobro");
        again.setId("co-nuevo");
        again.getStyleClass().add("primario");
        again.setMaxWidth(Double.MAX_VALUE);
        again.setOnAction(e -> {
            model.newCharge();
            memberSearch.requestFocus();
        });
        receipt.getChildren().addAll(done, folio, rows, print, again);
        javafx.application.Platform.runLater(again::requestFocus);
    }

    private static ProgressIndicator progress() {
        ProgressIndicator progress = new ProgressIndicator();
        progress.getStyleClass().add("progreso-chico");
        return progress;
    }

    /** Día/mes/año, como el resto de la app. */
    private static final class DayConverter extends StringConverter<LocalDate> {
        @Override
        public String toString(LocalDate date) {
            return date == null ? "" : DAY.format(date);
        }

        @Override
        public LocalDate fromString(String text) {
            if (text == null || text.isBlank()) {
                return null;
            }
            try {
                return LocalDate.parse(text.strip(), DAY);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

}
