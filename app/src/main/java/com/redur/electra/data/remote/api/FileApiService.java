package com.redur.electra.data.remote.api;

import com.redur.electra.data.remote.dto.response.ApiResponseDTO;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;
import retrofit2.http.Multipart;
import retrofit2.http.POST;
import retrofit2.http.Part;

public interface FileApiService {

    String UPLOAD_LOG_PATH = "ElectraWS/mobile/file/upload/log/";
    String UPLOAD_IMG_PATH = "ElectraWS/mobile/file/upload/img/";

    /** Las partes se construyen con {@code UploadFileDTO#toParts()}. */
    @Multipart
    @POST(UPLOAD_LOG_PATH)
    Call<ApiResponseDTO<Boolean>> uploadLog(@Part List<MultipartBody.Part> parts);

    /** Mismas partes que {@link #uploadLog}, con la imagen como fichero. */
    @Multipart
    @POST(UPLOAD_IMG_PATH)
    Call<ApiResponseDTO<Boolean>> uploadImg(@Part List<MultipartBody.Part> parts);
}
