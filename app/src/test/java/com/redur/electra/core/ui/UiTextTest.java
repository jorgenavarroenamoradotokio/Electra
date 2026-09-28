package com.redur.electra.core.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import com.redur.electra.R;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class UiTextTest {

    @Test
    public void resConLosMismosArgumentos_esIgual() {
        // Imprescindible para distinctUntilChanged y para comparar estados en tests
        UiText first = new UiText.Res(R.string.login_version, List.of("1.0"));
        UiText second = new UiText.Res(R.string.login_version, List.of("1.0"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    public void resConArgumentosDistintos_noEsIgual() {
        assertNotEquals(
                new UiText.Res(R.string.login_version, List.of("1.0")),
                new UiText.Res(R.string.login_version, List.of("2.0")));
    }

    @Test
    public void resSinArgumentos_equivaleAListaVacia() {
        assertEquals(new UiText.Res(R.string.error_unknown), new UiText.Res(R.string.error_unknown, List.of()));
    }

    @Test
    public void resCopiaLosArgumentos_cambiosPosterioresNoLeAfectan() {
        List<Object> args = new ArrayList<>(List.of("1.0"));
        UiText.Res text = new UiText.Res(R.string.login_version, args);

        args.set(0, "2.0");

        assertEquals(List.of("1.0"), text.args());
        assertThrows(UnsupportedOperationException.class, () -> text.args().add("3.0"));
    }

    @Test
    public void raw_devuelveElTextoTalCual() {
        // Raw no usa el Context
        assertEquals("Mensaje del servidor", new UiText.Raw("Mensaje del servidor").resolve(null));
    }

    @Test
    public void raw_noAdmiteTextoNulo() {
        assertThrows(NullPointerException.class, () -> new UiText.Raw(null));
    }
}
