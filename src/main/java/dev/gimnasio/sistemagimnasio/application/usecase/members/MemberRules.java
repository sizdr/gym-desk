package dev.gimnasio.sistemagimnasio.application.usecase.members;

import dev.gimnasio.sistemagimnasio.application.port.out.MemberRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.MembershipRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.PlanRepository;
import dev.gimnasio.sistemagimnasio.application.port.out.SettingsRepository;
import dev.gimnasio.sistemagimnasio.application.shared.NotFoundException;
import dev.gimnasio.sistemagimnasio.application.shared.ValidationException;
import dev.gimnasio.sistemagimnasio.domain.member.Email;
import dev.gimnasio.sistemagimnasio.domain.member.Member;
import dev.gimnasio.sistemagimnasio.domain.member.Membership;
import dev.gimnasio.sistemagimnasio.domain.member.MembershipRules;
import dev.gimnasio.sistemagimnasio.domain.member.Phone;
import dev.gimnasio.sistemagimnasio.domain.member.Plan;
import dev.gimnasio.sistemagimnasio.domain.settings.GymSettings;
import dev.gimnasio.sistemagimnasio.domain.shared.DomainException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lo que comparten los casos de uso del catálogo de socios. */
final class MemberRules {
    static final int MAX_NAME_LENGTH = 100;
    static final int MAX_EMAIL_LENGTH = 120;
    static final String NAME_REQUIRED = "Escribe el nombre completo.";
    static final String NAME_SHORT = "Escribe el nombre completo, con al menos 3 letras.";
    static final String NAME_LONG = "Usa " + MAX_NAME_LENGTH + " caracteres o menos.";
    static final String PHONE_REQUIRED = "Escribe el teléfono a 10 dígitos.";
    static final String PHONE_NOT_DIGITS = "Escribe solo números: el teléfono debe tener 10 dígitos. Ejemplo: 555 010 2233.";
    static final String EMAIL_INVALID = "Escribe un correo válido, por ejemplo nombre@ejemplo.com, o déjalo vacío.";
    static final String JOIN_DATE_REQUIRED = "Escribe la fecha de alta.";
    static final String NOT_FOUND = "El socio no existe.";
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private MemberRules() {
    }

    /**
     * Valida todos los campos y lanza un solo {@link ValidationException} con los errores, en el orden del formulario.
     *
     * @param currentId el socio al corregirlo; null al darlo de alta
     * @param firstStart inicio de su primera membresía, o null si nunca ha pagado
     * @return el socio listo para guardar, con los textos normalizados
     */
    static Member validMember(MemberCommand command, Long currentId, LocalDate today, LocalDate firstStart,
                              MemberRepository members) {
        Map<String, String> errors = new LinkedHashMap<>();

        String name = command.fullName() == null ? "" : command.fullName().strip().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            errors.put("fullName", NAME_REQUIRED);
        } else if (name.codePoints().filter(Character::isLetter).count() < 3) {
            errors.put("fullName", NAME_SHORT);
        } else if (name.length() > MAX_NAME_LENGTH) {
            errors.put("fullName", NAME_LONG);
        }

        String phone = command.phone() == null ? "" : command.phone().replaceAll("[\\s-]", "");
        if (phone.isEmpty()) {
            errors.put("phone", PHONE_REQUIRED);
        } else if (!phone.matches("\\d+")) {
            errors.put("phone", PHONE_NOT_DIGITS);
        } else if (phone.length() != 10) {
            errors.put("phone", "El teléfono debe tener 10 dígitos; tiene " + phone.length() + ". Ejemplo: 555 010 2233.");
        }

        String email = command.email() == null || command.email().isBlank() ? null : command.email().strip();
        Email parsedEmail = null;
        if (email != null) {
            if (email.length() > MAX_EMAIL_LENGTH || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
                errors.put("email", EMAIL_INVALID);
            } else {
                parsedEmail = new Email(email);
                members.nameWithEmail(parsedEmail.value(), currentId).ifPresent(owner -> errors.put("email",
                        "Ya hay un socio con este correo: " + owner + ". Usa otro o déjalo vacío."));
            }
        }

        LocalDate joinDate = command.joinDate();
        if (joinDate == null) {
            errors.put("joinDate", JOIN_DATE_REQUIRED);
        } else if (joinDate.isAfter(today)) {
            errors.put("joinDate", "La fecha de alta no puede ser posterior a hoy (" + DAY.format(today) + ").");
        } else if (firstStart != null && joinDate.isAfter(firstStart)) {
            errors.put("joinDate", "La fecha de alta no puede ser posterior a su primera membresía ("
                    + DAY.format(firstStart) + ").");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
        return new Member(currentId, name, new Phone(phone), parsedEmail, joinDate);
    }

    static Member find(MemberRepository members, long id) {
        return members.findById(id).orElseThrow(() -> new NotFoundException(NOT_FOUND));
    }

    /** La fila del socio con su estado de hoy. */
    static MemberRow row(Member member, MembershipRepository memberships, PlanRepository plans,
                         SettingsRepository settings, LocalDate today) {
        List<Membership> history = memberships.findByMember(member.id());
        Membership latest = MembershipRules.latest(history).orElse(null);
        String planName = latest == null ? null : plans.findById(latest.planId()).map(Plan::name).orElse(null);
        GymSettings current = settings.get();
        return MemberRows.of(member, latest, planName, today, current.warningDays(), current.autoDeactivationDays());
    }

    static void requireNoPayments(MemberRepository members, Member member) {
        if (members.hasPayments(member.id())) {
            throw new DomainException("No puedes eliminar a «" + member.fullName()
                    + "»: ya tiene cobros registrados y su historial se conserva.");
        }
    }
}
