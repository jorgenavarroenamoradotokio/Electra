package com.redur.electra.ui.login;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Estado del formulario de login. Un error nulo indica que el campo es válido.
 */
public record LoginFormState(
        @Nullable @StringRes Integer usernameError,
        @Nullable @StringRes Integer passwordError
) {

    public static final LoginFormState EMPTY = new LoginFormState(null, null);

    public boolean isValid() {
        return usernameError == null && passwordError == null;
    }
}
