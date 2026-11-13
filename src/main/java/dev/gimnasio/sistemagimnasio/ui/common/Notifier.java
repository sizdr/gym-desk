package dev.gimnasio.sistemagimnasio.ui.common;

/** Avisos flotantes de resultado (abajo a la derecha, como en el prototipo). Se llaman en el hilo de JavaFX. */
public interface Notifier {
    /** Algo salió bien, por ejemplo «Se agregó «Vales».». */
    void ok(String message);

    /** No se pudo hacer, con el mensaje del caso de uso. */
    void error(String message);

    /** No muestra nada (pruebas). */
    Notifier NONE = new Notifier() {
        @Override
        public void ok(String message) {
        }

        @Override
        public void error(String message) {
        }
    };
}
