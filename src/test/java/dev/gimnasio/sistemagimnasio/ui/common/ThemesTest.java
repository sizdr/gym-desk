package dev.gimnasio.sistemagimnasio.ui.common;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThemesTest {

    private static final class MemoryStore implements Themes.Store {
        String value;
        final List<String> saved = new ArrayList<>();
        boolean failOnSave;

        MemoryStore(String value) {
            this.value = value;
        }

        @Override
        public Optional<String> load() {
            return Optional.ofNullable(value);
        }

        @Override
        public void save(String value) {
            if (failOnSave) {
                throw new IllegalStateException("sin permisos");
            }
            this.value = value;
            saved.add(value);
        }
    }

    @Test
    void withoutAStoredChoiceItIsLight() {
        assertEquals(Themes.Theme.CLARO, new Themes(new MemoryStore(null)).current());
    }

    @Test
    void remembersTheLastChoice() {
        assertEquals(Themes.Theme.OSCURO, new Themes(new MemoryStore("OSCURO")).current());
    }

    @Test
    void anUnknownStoredValueFallsBackToLight() {
        assertEquals(Themes.Theme.CLARO, new Themes(new MemoryStore("SISTEMA")).current());
    }

    @Test
    void choosingSavesIt() {
        MemoryStore store = new MemoryStore(null);
        Themes themes = new Themes(store);

        themes.set(Themes.Theme.OSCURO);

        assertEquals(Themes.Theme.OSCURO, themes.current());
        assertEquals(List.of("OSCURO"), store.saved);
    }

    @Test
    void notBeingAbleToSaveStillChangesTheTheme() {
        MemoryStore store = new MemoryStore(null);
        store.failOnSave = true;
        Themes themes = new Themes(store);

        themes.set(Themes.Theme.OSCURO);

        assertEquals(Themes.Theme.OSCURO, themes.current());
    }

    @Test
    void eachThemeHasItsOwnStyleClass() {
        assertEquals("tema-claro", Themes.Theme.CLARO.styleClass());
        assertEquals("tema-oscuro", Themes.Theme.OSCURO.styleClass());
    }
}
