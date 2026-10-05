package com.redur.electra.fake;

import androidx.annotation.Nullable;

import com.redur.electra.data.remote.api.FileApiService;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;

import java.util.List;

import okhttp3.MultipartBody;
import retrofit2.Call;

public final class FakeFileApiService implements FileApiService {

    private FakeCall<ApiResponseDTO<Boolean>> nextUploadCall;
    @Nullable
    private List<MultipartBody.Part> lastUploadParts;
    private int uploadCalls;
    private int imgUploadCalls;

    public void willReturnUpload(FakeCall<ApiResponseDTO<Boolean>> call) {
        this.nextUploadCall = call;
    }

    @Override
    public Call<ApiResponseDTO<Boolean>> uploadLog(List<MultipartBody.Part> parts) {
        uploadCalls++;
        lastUploadParts = parts;
        return nextUploadCall;
    }

    @Override
    public Call<ApiResponseDTO<Boolean>> uploadImg(List<MultipartBody.Part> parts) {
        uploadCalls++;
        imgUploadCalls++;
        lastUploadParts = parts;
        return nextUploadCall;
    }

    @Nullable
    public List<MultipartBody.Part> lastUploadParts() {
        return lastUploadParts;
    }

    /** Subidas por cualquiera de los endpoints. */
    public int uploadCalls() {
        return uploadCalls;
    }

    public int imgUploadCalls() {
        return imgUploadCalls;
    }
}
