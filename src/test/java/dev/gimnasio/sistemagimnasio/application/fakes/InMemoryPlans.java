package dev.gimnasio.sistemagimnasio.application.fakes;

import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.shared.Money;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InMemoryPlans implements PlanRepository {
    private final Map<Long, Plan> byId = new LinkedHashMap<>();
    private final Set<Long> withMemberships = new HashSet<>();
    private long nextId = 1;
    public int writes;
    public final TransactionGuard guard = new TransactionGuard();

    public Plan add(String name, int days, String price, Long reference, boolean active) {
        Plan plan = new Plan(nextId++, name, days, Money.of(price), reference, active);
        byId.put(plan.id(), plan);
        return plan;
    }

    public void giveMembership(long planId) {
        withMemberships.add(planId);
    }

    public Plan get(long id) {
        return byId.get(id);
    }

    @Override
    public List<Plan> findAll() {
        guard.check("findAll");
        return new ArrayList<>(byId.values());
    }

    @Override
    public Optional<Plan> findById(long id) {
        guard.check("findById");
        return Optional.ofNullable(byId.get(id));
    }

    @Override
    public boolean existsByName(String name, Long excludeId) {
        guard.check("existsByName");
        return byId.values().stream()
                .anyMatch(plan -> plan.name().equalsIgnoreCase(name) && !Objects.equals(plan.id(), excludeId));
    }

    @Override
    public Plan insert(Plan plan) {
        guard.check("insert");
        writes++;
        Plan saved = new Plan(nextId++, plan.name(), plan.durationDays(), plan.price(), plan.referenceMonthlyPlanId(),
                plan.active());
        byId.put(saved.id(), saved);
        return saved;
    }

    @Override
    public void update(Plan plan) {
        guard.check("update");
        writes++;
        byId.put(plan.id(), plan);
    }

    @Override
    public boolean hasMemberships(long id) {
        guard.check("hasMemberships");
        return withMemberships.contains(id);
    }

    @Override
    public List<String> namesReferencing(long id) {
        guard.check("namesReferencing");
        return byId.values().stream()
                .filter(plan -> Objects.equals(plan.referenceMonthlyPlanId(), id))
                .map(Plan::name)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    @Override
    public void delete(long id) {
        guard.check("delete");
        writes++;
        byId.remove(id);
    }
}
