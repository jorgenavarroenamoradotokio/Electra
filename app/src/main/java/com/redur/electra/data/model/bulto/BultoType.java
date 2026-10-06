package com.redur.electra.data.model.bulto;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * Tipo de bulto asignable en una lectura.
 *
 * @param code          identificador del tipo en el backend (p. ej. "020").
 * @param type          abreviatura que reconoce el operario (p. ej. "PL").
 * @param description   texto traducido al idioma del terminal.
 * @param photoRequired si al asignarlo hay que tomar una foto del bulto.
 */
public record BultoType(@NonNull String code, @NonNull String type, @NonNull String description,
                        boolean photoRequired) {

    public BultoType {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(description, "description");
    }
}
