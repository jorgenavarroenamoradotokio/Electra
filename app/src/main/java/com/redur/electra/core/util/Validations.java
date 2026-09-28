package com.redur.electra.core.util;

import androidx.annotation.Nullable;

public final class Validations {

    public static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
