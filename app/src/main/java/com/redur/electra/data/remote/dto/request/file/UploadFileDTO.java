package com.redur.electra.data.remote.dto.request.file;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;

/**
 * Subida de un fichero al backend. Viaja como multipart/form-data, no como JSON: cada campo es una
 * parte cuyo nombre debe coincidir con el del {@code UploadFileDTO} del backend o llegará vacío.
 */
public record UploadFileDTO(
        @NonNull String fileName,
        @NonNull RequestBody file,
        @Nullable String plzsId,
        @NonNull String username,
        @NonNull String password,
        @NonNull String language) {

    static final String PART_FILE = "file";
    static final String PART_PLZS_ID = "plzs_id";
    static final String PART_USERNAME = "userName";
    static final String PART_PASSWORD = "password";
    static final String PART_LANGUAGE = "language";

    // Explícito para que los caracteres no ASCII (p. ej. en la contraseña) no dependan del servidor
    private static final MediaType TEXT_PLAIN = MediaType.get("text/plain; charset=utf-8");

    public UploadFileDTO {
        Objects.requireNonNull(fileName, "fileName");
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(password, "password");
        Objects.requireNonNull(language, "language");
    }

    /** Partes del formulario. Sin plaza asignada se omite su parte y el backend la recibe nula. */
    @NonNull
    public List<MultipartBody.Part> toParts() {
        List<MultipartBody.Part> parts = new ArrayList<>();
        parts.add(MultipartBody.Part.createFormData(PART_FILE, fileName, file));
        if (plzsId != null) {
            parts.add(textPart(PART_PLZS_ID, plzsId));
        }
        parts.add(textPart(PART_USERNAME, username));
        parts.add(textPart(PART_PASSWORD, password));
        parts.add(textPart(PART_LANGUAGE, language));
        return parts;
    }

    /** Nunca expone la contraseña: evita que acabe en logcat o en los ficheros de log. */
    @NonNull
    @Override
    public String toString() {
        return "UploadFileDTO[fileName=" + fileName + ", plzsId=" + plzsId
                + ", username=" + username + ", password=****, language=" + language + "]";
    }

    private static MultipartBody.Part textPart(@NonNull String name, @NonNull String value) {
        return MultipartBody.Part.createFormData(name, null, RequestBody.create(value, TEXT_PLAIN));
    }
}
