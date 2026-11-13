package dev.gimnasio.sistemagimnasio.application.usecase.billing;

import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.SavingsView;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.member.PlanSavings;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Arma {@link PlanOption}: el ahorro se calcula contra la referencia mensual del plan, igual que en Planes. */
final class PlanOptions {
    private static final Comparator<Plan> ORDER = Comparator.comparingInt(Plan::durationDays)
            .thenComparing(Plan::name, String.CASE_INSENSITIVE_ORDER);

    private PlanOptions() {
    }

    /** Los planes activos, del más corto al más largo. */
    static List<PlanOption> active(PlanRepository plans) {
        Map<Long, Plan> byId = plans.findAll().stream().collect(Collectors.toMap(Plan::id, Function.identity()));
        return byId.values().stream().filter(Plan::active).sorted(ORDER).map(plan -> of(plan, byId)).toList();
    }

    static PlanOption of(Plan plan, PlanRepository plans) {
        Plan reference = plan.referenceMonthlyPlanId() == null
                ? null
                : plans.findById(plan.referenceMonthlyPlanId()).orElse(null);
        return of(plan, reference);
    }

    private static PlanOption of(Plan plan, Map<Long, Plan> byId) {
        return of(plan, plan.referenceMonthlyPlanId() == null ? null : byId.get(plan.referenceMonthlyPlanId()));
    }

    private static PlanOption of(Plan plan, Plan reference) {
        return new PlanOption(plan.id(), plan.name(), plan.durationDays(), plan.price(),
                SavingsView.of(PlanSavings.of(plan.durationDays(), plan.price(), reference)));
    }
}
