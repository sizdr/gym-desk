package dev.gimnasio.sistemagimnasio;

import javafx.application.Application;

/** Arranca JavaFX desde el classpath, sin {@code module-info.java}. */
public class Launcher {
    public static void main(String[] args) {
        Application.launch(App.class, args);
    }
}
