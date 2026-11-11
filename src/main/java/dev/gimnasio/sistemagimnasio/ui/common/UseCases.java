package dev.gimnasio.sistemagimnasio.ui.common;

import dev.gimnasio.sistemagimnasio.application.usecase.authenticate.Authenticate;
import dev.gimnasio.sistemagimnasio.application.usecase.gyminfo.GetGymInfo;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.CreatePaymentMethod;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.DeletePaymentMethod;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.ListPaymentMethods;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.SetPaymentMethodActive;
import dev.gimnasio.sistemagimnasio.application.usecase.paymentmethods.UpdatePaymentMethod;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.CreatePlan;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.DeletePlan;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.ListPlans;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.PreviewPlanSavings;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.SetPlanActive;
import dev.gimnasio.sistemagimnasio.application.usecase.plans.UpdatePlan;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.GetSettings;
import dev.gimnasio.sistemagimnasio.application.usecase.settings.UpdateSettings;

import java.util.Objects;

/**
 * Los casos de uso que la UI puede llamar, ya conectados a su infraestructura.
 * Los arma el punto de entrada; la UI no sabe de dónde salen sus repositorios.
 */
public record UseCases(
        Authenticate authenticate,
        GetGymInfo getGymInfo,
        GetSettings getSettings,
        UpdateSettings updateSettings,
        ListPaymentMethods listPaymentMethods,
        CreatePaymentMethod createPaymentMethod,
        UpdatePaymentMethod updatePaymentMethod,
        SetPaymentMethodActive setPaymentMethodActive,
        DeletePaymentMethod deletePaymentMethod,
        ListPlans listPlans,
        PreviewPlanSavings previewPlanSavings,
        CreatePlan createPlan,
        UpdatePlan updatePlan,
        SetPlanActive setPlanActive,
        DeletePlan deletePlan) {

    public UseCases {
        Objects.requireNonNull(authenticate, "authenticate");
        Objects.requireNonNull(getGymInfo, "getGymInfo");
        Objects.requireNonNull(getSettings, "getSettings");
        Objects.requireNonNull(updateSettings, "updateSettings");
        Objects.requireNonNull(listPaymentMethods, "listPaymentMethods");
        Objects.requireNonNull(createPaymentMethod, "createPaymentMethod");
        Objects.requireNonNull(updatePaymentMethod, "updatePaymentMethod");
        Objects.requireNonNull(setPaymentMethodActive, "setPaymentMethodActive");
        Objects.requireNonNull(deletePaymentMethod, "deletePaymentMethod");
        Objects.requireNonNull(listPlans, "listPlans");
        Objects.requireNonNull(previewPlanSavings, "previewPlanSavings");
        Objects.requireNonNull(createPlan, "createPlan");
        Objects.requireNonNull(updatePlan, "updatePlan");
        Objects.requireNonNull(setPlanActive, "setPlanActive");
        Objects.requireNonNull(deletePlan, "deletePlan");
    }
}
