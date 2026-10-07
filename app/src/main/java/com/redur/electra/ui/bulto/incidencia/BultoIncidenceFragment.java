package com.redur.electra.ui.bulto.incidencia;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.widget.TextViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.color.MaterialColors;
import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoIncidence;
import com.redur.electra.data.model.bulto.OperationType;
import com.redur.electra.databinding.FragmentBultoIncidenceBinding;
import com.redur.electra.ui.photo.PhotoSourceBottomSheet;

import dagger.hilt.android.AndroidEntryPoint;

/**
 * Incidencia del último bulto leído: se elige una de las del tipo de operación y, si la incidencia
 * lo exige, se escriben observaciones y se hace una foto con la hoja de foto. De momento, como en
 * el tipo de bulto, aceptar y cancelar solo lo confirman con un aviso y dejan la pantalla lista
 * para otra incidencia.
 */
@AndroidEntryPoint
public class BultoIncidenceFragment extends Fragment {

    private static final String PHOTO_SHEET_TAG = "BultoIncidenceFragment.photo";

    @Nullable
    private FragmentBultoIncidenceBinding binding;
    private BultoIncidenceViewModel viewModel;
    @Nullable
    private BultoIncidenceAdapter adapter;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(BultoIncidenceViewModel.class);
        // La hoja de foto publica su resultado en este FragmentManager; se escucha desde onCreate
        // para recibirlo aunque llegue mientras se recrea la vista (p. ej. al volver de la cámara)
        getChildFragmentManager().setFragmentResultListener(PhotoSourceBottomSheet.RESULT_KEY, this,
                (key, result) -> {
                    String photoUri = result.getString(PhotoSourceBottomSheet.RESULT_IMAGE_URI);
                    if (photoUri != null) {
                        viewModel.onPhotoAttached(photoUri);
                    }
                });
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentBultoIncidenceBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentBultoIncidenceBinding views = requireBinding();
        BultoIncidenceArgs args = viewModel.getArgs();
        views.textBultoIncidenceOperation.setText(titleOf(args.operationType()));
        views.textBultoIncidenceBarcode.setText(args.barcode() != null
                ? args.barcode()
                : getString(R.string.bulto_incidence_no_barcode));

        views.layoutBultoIncidenceObservations.setCounterMaxLength(BultoIncidence.OBSERVATIONS_MAX_LENGTH);
        views.inputBultoIncidenceObservations.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(BultoIncidence.OBSERVATIONS_MAX_LENGTH)});

        adapter = new BultoIncidenceAdapter(viewModel::onIncidenceSelected);
        views.recyclerBultoIncidences.setAdapter(adapter);

        views.buttonBultoIncidencesRetry.setOnClickListener(v -> viewModel.onRetryClicked());
        views.buttonBultoIncidencePhoto.setOnClickListener(v -> showPhotoSheet());
        views.buttonBultoIncidenceConfirm.setOnClickListener(v -> confirm());
        views.buttonBultoIncidenceCancel.setOnClickListener(v -> cancel());

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
        viewModel.getSelectedIncidence().observe(getViewLifecycleOwner(), this::renderSelection);
        viewModel.getPhotoUri().observe(getViewLifecycleOwner(), photoUri -> renderPhoto());
        viewModel.getFormState().observe(getViewLifecycleOwner(), this::renderForm);
    }

    /**
     * Las observaciones se escuchan una vez restaurado lo tecleado: restaurarlo no cuenta como una
     * corrección y no debe borrar el error que ya se mostraba.
     */
    @Override
    public void onViewStateRestored(@Nullable Bundle savedInstanceState) {
        super.onViewStateRestored(savedInstanceState);
        requireBinding().inputBultoIncidenceObservations.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                viewModel.onObservationsChanged();
            }
        });
    }

    private void confirm() {
        BultoIncidenceEntry entry = viewModel.onConfirmClicked(observationsText());
        if (entry == null) {
            BultoIncidenceFormState form = viewModel.getFormState().getValue();
            if (form != null && form.observationsError() != null) {
                requireBinding().inputBultoIncidenceObservations.requestFocus();
            }
            return;
        }
        requireBinding().inputBultoIncidenceObservations.setText(null);
        showToast(R.string.bulto_incidence_saved);
    }

    private void cancel() {
        viewModel.onCancelClicked();
        requireBinding().inputBultoIncidenceObservations.setText(null);
        showToast(R.string.bulto_incidence_canceled);
    }

    /** Hoja de foto ya existente: hace o elige la imagen, la envía y devuelve su content://. */
    private void showPhotoSheet() {
        FragmentManager fragmentManager = getChildFragmentManager();
        if (viewModel.canTakePhoto()
                && fragmentManager.findFragmentByTag(PHOTO_SHEET_TAG) == null
                && !fragmentManager.isStateSaved()) {
            new PhotoSourceBottomSheet().show(fragmentManager, PHOTO_SHEET_TAG);
        }
    }

    private void render(@NonNull BultoIncidenceState state) {
        FragmentBultoIncidenceBinding views = requireBinding();
        views.layoutBultoIncidencesLoading.setVisibility(
                state instanceof BultoIncidenceState.Loading ? View.VISIBLE : View.GONE);
        views.cardBultoIncidences.setVisibility(
                state instanceof BultoIncidenceState.Ready ? View.VISIBLE : View.GONE);
        views.layoutBultoIncidencesError.setVisibility(
                state instanceof BultoIncidenceState.Failed || state instanceof BultoIncidenceState.Empty
                        ? View.VISIBLE : View.GONE);

        if (state instanceof BultoIncidenceState.Ready ready && adapter != null) {
            adapter.submitList(ready.incidences());
        } else if (state instanceof BultoIncidenceState.Failed failed) {
            views.textBultoIncidencesErrorTitle.setText(R.string.bulto_incidence_error_title);
            views.textBultoIncidencesError.setText(failed.message().resolve(requireContext()));
        } else if (state instanceof BultoIncidenceState.Empty) {
            views.textBultoIncidencesErrorTitle.setText(R.string.bulto_incidence_empty_title);
            views.textBultoIncidencesError.setText(R.string.bulto_incidence_empty);
        }
        renderActions();
    }

    private void renderSelection(@Nullable BultoIncidence selected) {
        FragmentBultoIncidenceBinding views = requireBinding();
        if (adapter != null) {
            adapter.setSelectedCode(selected != null ? selected.code() : null);
        }
        boolean observationsRequired = selected != null && selected.observationsRequired();
        // Solo se graban si la incidencia las exige: no se deja texto que no se va a guardar
        if (!observationsRequired) {
            views.inputBultoIncidenceObservations.setText(null);
        }
        views.layoutBultoIncidenceObservations.setEnabled(observationsRequired);
        views.layoutBultoIncidenceObservations.setHelperText(getString(observationsRequired
                ? R.string.bulto_incidence_observations_required
                : R.string.bulto_incidence_observations_helper));
        renderActions();
        renderPhoto();
    }

    private void renderForm(@NonNull BultoIncidenceFormState form) {
        requireBinding().layoutBultoIncidenceObservations.setError(
                form.observationsError() != null ? getString(form.observationsError()) : null);
        renderPhoto();
    }

    private void renderActions() {
        FragmentBultoIncidenceBinding views = requireBinding();
        views.buttonBultoIncidenceConfirm.setEnabled(viewModel.canConfirm());
        views.buttonBultoIncidencePhoto.setEnabled(viewModel.canTakePhoto());
    }

    /** Qué pasa con la foto, en texto e icono: el color solo refuerza. */
    private void renderPhoto() {
        FragmentBultoIncidenceBinding views = requireBinding();
        BultoIncidence selected = viewModel.getSelectedIncidence().getValue();
        BultoIncidenceFormState form = viewModel.getFormState().getValue();
        boolean attached = viewModel.getPhotoUri().getValue() != null;

        if (form != null && form.photoError() != null) {
            showPhotoStatus(R.drawable.ic_error_24, form.photoError(), androidx.appcompat.R.attr.colorError);
        } else if (attached) {
            showPhotoStatus(R.drawable.ic_check_24, R.string.bulto_incidence_photo_attached,
                    androidx.appcompat.R.attr.colorPrimary);
        } else if (selected == null) {
            showPhotoStatus(R.drawable.ic_photo_camera_24, R.string.bulto_incidence_photo_choose_first,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
        } else {
            showPhotoStatus(R.drawable.ic_photo_camera_24, selected.photoRequired()
                            ? R.string.bulto_incidence_photo_required
                            : R.string.bulto_incidence_photo_optional,
                    com.google.android.material.R.attr.colorOnSurfaceVariant);
        }
        views.buttonBultoIncidencePhoto.setText(attached
                ? R.string.bulto_incidence_retake_photo
                : R.string.bulto_incidence_take_photo);
    }

    private void showPhotoStatus(@DrawableRes int icon, @StringRes int text, @AttrRes int colorAttr) {
        FragmentBultoIncidenceBinding views = requireBinding();
        TextView status = views.textBultoIncidencePhotoStatus;
        int color = MaterialColors.getColor(status, colorAttr);
        status.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(status, ColorStateList.valueOf(color));
        status.setText(text);
        status.setTextColor(color);
    }

    @StringRes
    private static int titleOf(@NonNull OperationType operationType) {
        return switch (operationType) {
            case RECOGIDAS -> R.string.bulto_incidence_operation_recogidas;
        };
    }

    @NonNull
    private String observationsText() {
        CharSequence text = requireBinding().inputBultoIncidenceObservations.getText();
        return text != null ? text.toString() : "";
    }

    private void showToast(@StringRes int message) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show();
    }

    @NonNull
    private FragmentBultoIncidenceBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de incidencias no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.recyclerBultoIncidences.setAdapter(null);
        }
        adapter = null;
        binding = null;
    }
}
