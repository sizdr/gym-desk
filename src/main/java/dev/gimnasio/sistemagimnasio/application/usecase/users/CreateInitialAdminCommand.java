package dev.gimnasio.sistemagimnasio.application.usecase.users;

public record CreateInitialAdminCommand(String name, String username, String password, String passwordConfirmation) {
    @Override
    public String toString() {
        return "CreateInitialAdminCommand[name=" + name + ", username=" + username
                + ", password=***, passwordConfirmation=***]";
    }
}
