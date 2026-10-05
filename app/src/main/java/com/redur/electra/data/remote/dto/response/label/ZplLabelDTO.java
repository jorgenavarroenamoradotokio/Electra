package com.redur.electra.data.remote.dto.response.label;

import com.google.gson.annotations.SerializedName;

import java.util.List;

/** Etiqueta generada: el código ZPL y cada página renderizada como PNG en Base64. */
public record ZplLabelDTO(
        @SerializedName("zpl") String zpl,
        @SerializedName("content") List<String> content) {
}
