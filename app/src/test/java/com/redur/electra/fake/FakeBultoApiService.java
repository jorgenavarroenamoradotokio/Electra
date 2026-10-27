package com.redur.electra.fake;

import androidx.annotation.Nullable;

import com.redur.electra.data.remote.api.BultoApiService;
import com.redur.electra.data.remote.dto.request.bulto.BultoDockRequestDT0;
import com.redur.electra.data.remote.dto.request.bulto.BultoTypeRequestDTO;
import com.redur.electra.data.remote.dto.request.incidencia.IncidenciaRequestDTO;
import com.redur.electra.data.remote.dto.response.ApiResponseDTO;
import com.redur.electra.data.remote.dto.response.bulto.BultoDockDT0;
import com.redur.electra.data.remote.dto.response.bulto.BultoTypeDTO;
import com.redur.electra.data.remote.dto.response.incidencia.IncidenciaDTO;

import java.util.List;

import retrofit2.Call;

/**
 * Devuelve la llamada programada para cada endpoint y recuerda la última petición y cuántas veces
 * se ha llamado.
 */
public final class FakeBultoApiService implements BultoApiService {

    private FakeCall<ApiResponseDTO<List<BultoTypeDTO>>> nextTypesCall;
    @Nullable
    private BultoTypeRequestDTO lastTypesRequest;
    private int typesCalls;

    private FakeCall<ApiResponseDTO<BultoDockDT0>> nextDockCall;
    @Nullable
    private BultoDockRequestDT0 lastDockRequest;

    private FakeCall<ApiResponseDTO<List<IncidenciaDTO>>> nextIncidenciasCall;
    @Nullable
    private IncidenciaRequestDTO lastIncidenciasRequest;
    private int incidenciasCalls;

    // --- Tipos de bulto ---

    public void willReturnTypes(FakeCall<ApiResponseDTO<List<BultoTypeDTO>>> call) {
        this.nextTypesCall = call;
    }

    @Override
    public Call<ApiResponseDTO<List<BultoTypeDTO>>> getBultoTypes(BultoTypeRequestDTO request) {
        typesCalls++;
        lastTypesRequest = request;
        return nextTypesCall;
    }

    @Nullable
    public BultoTypeRequestDTO lastTypesRequest() {
        return lastTypesRequest;
    }

    public int typesCalls() {
        return typesCalls;
    }

    // --- Muelle ---

    public void willReturnDock(FakeCall<ApiResponseDTO<BultoDockDT0>> call) {
        this.nextDockCall = call;
    }

    @Override
    public Call<ApiResponseDTO<BultoDockDT0>> getMuelleBulto(BultoDockRequestDT0 request) {
        lastDockRequest = request;
        return nextDockCall;
    }

    @Nullable
    public BultoDockRequestDT0 lastDockRequest() {
        return lastDockRequest;
    }

    // --- Incidencias ---

    public void willReturnIncidencias(FakeCall<ApiResponseDTO<List<IncidenciaDTO>>> call) {
        this.nextIncidenciasCall = call;
    }

    @Override
    public Call<ApiResponseDTO<List<IncidenciaDTO>>> getIncidenciaPorAplicacion(IncidenciaRequestDTO request) {
        incidenciasCalls++;
        lastIncidenciasRequest = request;
        return nextIncidenciasCall;
    }

    @Nullable
    public IncidenciaRequestDTO lastIncidenciasRequest() {
        return lastIncidenciasRequest;
    }

    public int incidenciasCalls() {
        return incidenciasCalls;
    }
}
