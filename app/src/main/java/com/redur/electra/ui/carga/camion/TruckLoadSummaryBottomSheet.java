package com.redur.electra.ui.carga.camion;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.redur.electra.R;
import com.redur.electra.databinding.SheetTruckLoadSummaryBinding;

import java.text.NumberFormat;

import timber.log.Timber;

/**
 * Resumen de la carga del camión: expediciones, paradas, bultos y peso tasado. Solo informa: se
 * presenta como hoja inferior sobre la lista y se cierra con su botón, "atrás", deslizando o
 * tocando fuera. Las cifras llegan en los argumentos, así sobreviven a un cambio de configuración.
 */
public class TruckLoadSummaryBottomSheet extends BottomSheetDialogFragment {

    private static final String TAG = "TruckLoadSummaryBottomSheet";
    private static final String ARG_EXPEDITIONS = "expeditions";
    private static final String ARG_STOPS = "stops";
    private static final String ARG_BULTOS = "bultos";
    private static final String ARG_WEIGHT = "weight";
    /** El peso tasado se muestra con un decimal como mucho. */
    private static final int WEIGHT_FRACTION_DIGITS = 1;

    @Nullable
    private SheetTruckLoadSummaryBinding binding;

    /** No apila una segunda hoja si ya hay una visible (p. ej. un doble toque en el icono). */
    public static void showIfNotShown(@NonNull FragmentManager fragmentManager, @NonNull TruckLoadSummary summary) {
        if (fragmentManager.findFragmentByTag(TAG) != null || fragmentManager.isStateSaved()) {
            return;
        }
        Timber.i("[ACCION] Abrir resumen de la carga");
        Bundle args = new Bundle();
        args.putInt(ARG_EXPEDITIONS, summary.expeditions());
        args.putInt(ARG_STOPS, summary.stops());
        args.putInt(ARG_BULTOS, summary.bultos());
        args.putDouble(ARG_WEIGHT, summary.chargeableWeight());
        TruckLoadSummaryBottomSheet sheet = new TruckLoadSummaryBottomSheet();
        sheet.setArguments(args);
        sheet.show(fragmentManager, TAG);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetTruckLoadSummaryBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SheetTruckLoadSummaryBinding views = requireBinding();
        Bundle args = requireArguments();
        NumberFormat count = NumberFormat.getIntegerInstance();
        NumberFormat weight = NumberFormat.getNumberInstance();
        weight.setMaximumFractionDigits(WEIGHT_FRACTION_DIGITS);

        views.textTruckLoadSummaryExpeditions.setText(count.format(args.getInt(ARG_EXPEDITIONS)));
        views.textTruckLoadSummaryStops.setText(count.format(args.getInt(ARG_STOPS)));
        views.textTruckLoadSummaryBultos.setText(count.format(args.getInt(ARG_BULTOS)));
        views.textTruckLoadSummaryWeight.setText(getString(R.string.truck_load_summary_weight_value,
                weight.format(args.getDouble(ARG_WEIGHT))));
        views.buttonTruckLoadSummaryClose.setOnClickListener(v -> dismiss());
    }

    @NonNull
    private SheetTruckLoadSummaryBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista del resumen de la carga no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
