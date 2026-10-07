package com.redur.electra.core.ui;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.redur.electra.R;
import com.redur.electra.core.error.AppError;
import com.redur.electra.core.error.NetworkType;
import com.redur.electra.core.error.PrinterFailure;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import timber.log.Timber;

/**
 * Traduce un {@link AppError} al texto que ve el usuario.
 */
public final class ErrorUiMapper {

    private static final Map<String, Integer> API_CODES;

    static {
        Map<String, Integer> codes = new HashMap<>();
        codes.put("HTTP_500",R.string.error_unknown);
        codes.put("HTTP_503",R.string.error_network_503);
        codes.put("ERROR_A01", R.string.error_login_failed);
        codes.put("ERROR_A06", R.string.error_user_not_active);
        codes.put("ERROR_FILE_01", R.string.error_file_not_upload);

        codes.put("ERROR_M01", R.string.error_user_menu_not_configuration);
        codes.put("ERROR_T01", R.string.error_truck_plaza_failed);
        codes.put("ERROR_C01", R.string.error_truck_request_invalid);
        codes.put("ERROR_C06", R.string.error_truck_barcode_invalid);
        codes.put("ERROR_CCB_04_BARCODE_INVALID", R.string.error_truck_barcode_invalid);
        API_CODES = Collections.unmodifiableMap(codes);
    }

    private ErrorUiMapper() {
    }

    @NonNull
    public static UiText toUiText(@NonNull AppError error) {
        if (error instanceof AppError.Network network) {
            return new UiText.Res(mapNetwork(network.type()));
        }
        if (error instanceof AppError.Api api) {
            return mapApi(api.code(), api.serverMessage());
        }
        if (error instanceof AppError.Printer printer) {
            return new UiText.Res(mapPrinter(printer.failure()));
        }
        // Inalcanzable mientras AppError solo permita los subtipos anteriores
        return new UiText.Res(R.string.error_unknown);
    }

    @NonNull
    private static UiText mapApi(@Nullable String code, @Nullable String serverMessage) {
        Integer res = code != null ? API_CODES.get(code) : null;
        if (res != null) {
            return new UiText.Res(res);
        }

        // En caso de no encontrar el error de la api usamos el txt de error del servidor
        Timber.w("Código de error de API no mapeado: %s", code);
        if (serverMessage != null && !serverMessage.isBlank()) {
            return new UiText.Raw(serverMessage);
        }

        // Error generico error_unknown
        Timber.w("No se ha encontrado error del servidor procedemos a usar el de por defecto");
        return new UiText.Res(R.string.error_unknown);
    }

    @StringRes
    private static int mapNetwork(@NonNull NetworkType type) {
        // Sin default: el compilador obliga a cubrir cada valor nuevo del enum
        return switch (type) {
            case NO_CONNECTION -> R.string.error_network_no_connection;
            case TIMEOUT -> R.string.error_network_timeout;
        };
    }

    @StringRes
    private static int mapPrinter(@NonNull PrinterFailure failure) {
        return switch (failure) {
            case CONNECTION -> R.string.error_printer_connection;
            case SEND -> R.string.error_printer_send;
            case BLUETOOTH_UNAVAILABLE -> R.string.error_printer_bluetooth_unavailable;
        };
    }
}
