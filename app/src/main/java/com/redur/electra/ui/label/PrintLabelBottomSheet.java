package com.redur.electra.ui.label;

import android.bluetooth.BluetoothAdapter;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.redur.electra.R;
import com.redur.electra.core.permission.AppPermission;
import com.redur.electra.core.permission.PermissionRequester;
import com.redur.electra.core.ui.UiText;
import com.redur.electra.databinding.SheetPrintLabelBinding;
import com.redur.electra.ui.permission.PermissionSettingsBottomSheet;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Imprimir la etiqueta en una impresora Bluetooth, como hoja inferior. Pide el permiso de
 * dispositivos cercanos (si está denegado para siempre, ofrece ir a ajustes) y que se active el
 * Bluetooth si está apagado; después lista las impresoras y, al elegir una, le envía la etiqueta.
 * Una vez enviada publica {@link #RESULT_KEY} con su nombre en {@link #RESULT_PRINTER_NAME} y se cierra.
 */
@AndroidEntryPoint
public class PrintLabelBottomSheet extends BottomSheetDialogFragment {

    public static final String RESULT_KEY = "PrintLabelBottomSheet.result";
    public static final String RESULT_PRINTER_NAME = "printerName";

    // Se asigna en onCreate; los resultados de abajo siempre llegan después
    private PrintLabelViewModel viewModel;

    // Se registran al construir el Fragment: así el resultado llega aunque se recree mientras
    // está abierto el diálogo del sistema
    private final PermissionRequester permissionRequester =
            new PermissionRequester(this, status -> viewModel.onPermissionResult(status));
    private final ActivityResultLauncher<Intent> enableBluetooth = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            (ActivityResult result) -> viewModel.onEnableBluetoothResult());

    @Nullable
    private SheetPrintLabelBinding binding;
    @Nullable
    private PrinterAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(PrintLabelViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = SheetPrintLabelBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        SheetPrintLabelBinding views = requireBinding();
        PrinterAdapter printerAdapter = new PrinterAdapter(viewModel::onPrinterSelected);
        adapter = printerAdapter;
        views.recyclerPrinters.setAdapter(printerAdapter);
        views.buttonPrintSearchAgain.setOnClickListener(v -> viewModel.onSearchAgainClicked());
        views.buttonPrintCancel.setOnClickListener(v -> dismiss());

        getChildFragmentManager().setFragmentResultListener(PermissionSettingsBottomSheet.RESULT_KEY,
                getViewLifecycleOwner(), (key, result) -> {
                    if (result.getBoolean(PermissionSettingsBottomSheet.RESULT_OPENED_SETTINGS)) {
                        viewModel.onSettingsOpened();
                    } else {
                        viewModel.onSettingsDeclined();
                    }
                });
        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    /** Al volver de ajustes se comprueba si el usuario activó el permiso. */
    @Override
    public void onResume() {
        super.onResume();
        if (viewModel.getState().getValue() instanceof PrintLabelState.WaitingForSettings) {
            viewModel.onReturnedFromSettings(
                    PermissionRequester.isGranted(requireContext(), AppPermission.BLUETOOTH));
        }
    }

    private void render(@NonNull PrintLabelState state) {
        SheetPrintLabelBinding views = requireBinding();
        views.textPrintMessage.setText(messageOf(state));
        renderProgress(state);
        renderNotice(state instanceof PrintLabelState.Choosing choosing ? choosing.notice() : null);
        renderAction(state);
        renderPrinters(state);

        if (state instanceof PrintLabelState.RequestPermission) {
            viewModel.onPermissionRequested();
            permissionRequester.request(AppPermission.BLUETOOTH);
        } else if (state instanceof PrintLabelState.PermissionBlocked) {
            PermissionSettingsBottomSheet.showIfNotShown(getChildFragmentManager(), AppPermission.BLUETOOTH);
            viewModel.onSettingsPromptShown();
        } else if (state instanceof PrintLabelState.EnableBluetooth) {
            launchEnableBluetooth();
        } else if (state instanceof PrintLabelState.Printed printed) {
            Bundle result = new Bundle();
            result.putString(RESULT_PRINTER_NAME, printed.printer().name());
            getParentFragmentManager().setFragmentResult(RESULT_KEY, result);
            dismiss();
        }
    }

    @StringRes
    private static int messageOf(@NonNull PrintLabelState state) {
        if (state instanceof PrintLabelState.PermissionDenied
                || state instanceof PrintLabelState.WaitingForSettings) {
            return R.string.print_message_permission;
        }
        if (state instanceof PrintLabelState.BluetoothUnsupported) {
            return R.string.print_message_unsupported;
        }
        if (state instanceof PrintLabelState.BluetoothOff
                || state instanceof PrintLabelState.EnableBluetooth) {
            return R.string.print_message_bluetooth_off;
        }
        return R.string.print_message_choose;
    }

    private void renderProgress(@NonNull PrintLabelState state) {
        SheetPrintLabelBinding views = requireBinding();
        String progress = null;
        if (state instanceof PrintLabelState.Printing printing) {
            progress = printing.sending()
                    ? getString(R.string.print_sending, printing.printer().name())
                    : getString(R.string.print_generating);
        } else if (state instanceof PrintLabelState.Choosing choosing && choosing.searching()) {
            progress = getString(R.string.print_searching);
        }
        views.textPrintProgress.setText(progress);
        views.layoutPrintProgress.setVisibility(progress != null ? View.VISIBLE : View.GONE);
    }

    /** El aviso se mantiene hasta el siguiente intento: explica qué ha pasado y qué hacer. */
    private void renderNotice(@Nullable UiText notice) {
        SheetPrintLabelBinding views = requireBinding();
        if (notice != null) {
            views.textPrintNotice.setText(notice.resolve(requireContext()));
            views.cardPrintNotice.setVisibility(View.VISIBLE);
        } else {
            views.cardPrintNotice.setVisibility(View.GONE);
        }
    }

    private void renderAction(@NonNull PrintLabelState state) {
        SheetPrintLabelBinding views = requireBinding();
        if (state instanceof PrintLabelState.PermissionDenied) {
            views.buttonPrintAction.setText(R.string.print_allow_access);
            views.buttonPrintAction.setOnClickListener(v -> viewModel.onAllowClicked());
            views.buttonPrintAction.setVisibility(View.VISIBLE);
        } else if (state instanceof PrintLabelState.BluetoothOff) {
            views.buttonPrintAction.setText(R.string.print_enable_bluetooth);
            views.buttonPrintAction.setOnClickListener(v -> viewModel.onEnableBluetoothClicked());
            views.buttonPrintAction.setVisibility(View.VISIBLE);
        } else {
            views.buttonPrintAction.setOnClickListener(null);
            views.buttonPrintAction.setVisibility(View.GONE);
        }
    }

    /** La lista solo se ofrece al elegir: mientras se imprime no se puede lanzar otro envío. */
    private void renderPrinters(@NonNull PrintLabelState state) {
        SheetPrintLabelBinding views = requireBinding();
        PrintLabelState.Choosing choosing = state instanceof PrintLabelState.Choosing c ? c : null;
        boolean hasPrinters = choosing != null && !choosing.printers().isEmpty();
        if (adapter != null && choosing != null) {
            adapter.submitList(choosing.printers());
        }
        views.recyclerPrinters.setVisibility(hasPrinters ? View.VISIBLE : View.GONE);
        views.textPrintersEmpty.setVisibility(
                choosing != null && !hasPrinters && !choosing.searching() ? View.VISIBLE : View.GONE);
        views.buttonPrintSearchAgain.setVisibility(
                choosing != null && !choosing.searching() ? View.VISIBLE : View.GONE);
    }

    private void launchEnableBluetooth() {
        try {
            viewModel.onEnableBluetoothLaunched();
            enableBluetooth.launch(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE));
        } catch (ActivityNotFoundException | SecurityException e) {
            // Sin diálogo del sistema (o sin permiso): se comprueba el estado y sigue apagado
            Timber.w(e, "No se ha podido pedir que se active el Bluetooth");
            viewModel.onEnableBluetoothResult();
        }
    }

    @NonNull
    private SheetPrintLabelBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la hoja no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        adapter = null;
        binding = null;
    }
}
