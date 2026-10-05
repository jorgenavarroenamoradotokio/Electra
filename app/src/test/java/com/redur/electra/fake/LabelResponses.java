package com.redur.electra.fake;

import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.label.ZplLabelDTO;

import java.util.Base64;
import java.util.List;

import retrofit2.Response;

/** Respuestas de generación de etiquetas de ejemplo para los tests. */
public final class LabelResponses {

    public static final String ZPL = "^XA^FO50,50^FDElectra^FS^XZ";
    /** Contenido de la página: no hace falta que sea un PNG real para el repositorio. */
    public static final byte[] PAGE = {(byte) 0x89, 'P', 'N', 'G'};

    private LabelResponses() {
    }

    public static Response<ApiResponseDTO<ZplLabelDTO>> label(List<String> content) {
        return Response.success(new ApiResponseDTO<>(1, null, List.of(), new ZplLabelDTO(ZPL, content)));
    }

    public static Response<ApiResponseDTO<ZplLabelDTO>> onePage() {
        return label(List.of(Base64.getEncoder().encodeToString(PAGE)));
    }
}
