package com.redur.electra.data.model.place;

import androidx.annotation.NonNull;

import java.util.Objects;

/** Plaza (centro de trabajo) a la que puede asignarse el usuario. */
public record Place(@NonNull String id, @NonNull String description) {

    public Place {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(description, "description");
    }
}
