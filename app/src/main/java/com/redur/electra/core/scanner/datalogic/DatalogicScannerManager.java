package com.redur.electra.core.scanner.datalogic;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.core.scanner.BroadcastScannerManager;

import java.nio.charset.StandardCharsets;

/** Lecturas del Intent Wedge de Datalogic con la acción y categoría por defecto. */
public class DatalogicScannerManager extends BroadcastScannerManager {

    static final String ACTION = "com.datalogic.decodewedge.decode_action";
    static final String CATEGORY = "com.datalogic.decodewedge.decode_category";
    static final String EXTRA_BARCODE_STRING = "com.datalogic.decode.intentwedge.barcode_string";
    static final String EXTRA_BARCODE_DATA = "com.datalogic.decode.intentwedge.barcode_data";

    public DatalogicScannerManager(@NonNull Context context, @NonNull ScanListener listener) {
        super(context, listener);
    }

    @NonNull
    @Override
    protected IntentFilter createIntentFilter() {
        IntentFilter filter = new IntentFilter(ACTION);
        // Datalogic envía el intent con esta categoría: si el filtro no la declara, no se recibe
        filter.addCategory(CATEGORY);
        return filter;
    }

    @Nullable
    @Override
    protected String extractBarcode(@NonNull Intent intent) {
        String value = intent.getStringExtra(EXTRA_BARCODE_STRING);
        if (value != null && !value.isEmpty()) {
            return value;
        }

        byte[] bytes = intent.getByteArrayExtra(EXTRA_BARCODE_DATA);
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
