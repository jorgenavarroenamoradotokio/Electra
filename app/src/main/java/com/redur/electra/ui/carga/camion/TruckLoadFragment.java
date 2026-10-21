package com.redur.electra.ui.carga.camion;

import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.MenuHost;
import androidx.core.view.MenuProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.divider.MaterialDividerItemDecoration;
import com.google.android.material.snackbar.Snackbar;
import com.redur.electra.R;
import com.redur.electra.databinding.FragmentTruckLoadBinding;

import java.util.List;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

/**
 * Carga de camión: expediciones asignadas a la carga con lo escaneado de cada una. Se ordenan y
 * filtran por vuelta desde la barra de la lista, y al elegir una se habilitan sus acciones. La
 * carga se finaliza cuando ninguna expedición tiene diferencias. "Refrescar" se añade a la toolbar
 * común solo mientras la pantalla está en primer plano.
 */
@AndroidEntryPoint
public class TruckLoadFragment extends Fragment {

    private static final int PERCENT = 100;

    @Nullable
    private FragmentTruckLoadBinding binding;
    private TruckLoadViewModel viewModel;
    @Nullable
    private TruckLoadExpeditionAdapter adapter;
    /** El progreso solo se anima al cambiar, no al pintar la pantalla por primera vez. */
    private boolean progressShown;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        viewModel = new ViewModelProvider(this).get(TruckLoadViewModel.class);
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentTruckLoadBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        FragmentTruckLoadBinding views = requireBinding();

        addRefreshToToolbar();

        adapter = new TruckLoadExpeditionAdapter(viewModel::onExpeditionClicked);
        views.recyclerTruckLoadExpeditions.setAdapter(adapter);
        MaterialDividerItemDecoration divider = new MaterialDividerItemDecoration(requireContext(),
                MaterialDividerItemDecoration.VERTICAL);
        divider.setLastItemDecorated(false);
        views.recyclerTruckLoadExpeditions.addItemDecoration(divider);
        // Elegir es frecuente: la marca cambia al instante, sin fundido de la fila
        RecyclerView.ItemAnimator animator = views.recyclerTruckLoadExpeditions.getItemAnimator();
        if (animator instanceof SimpleItemAnimator simpleAnimator) {
            simpleAnimator.setSupportsChangeAnimations(false);
        }

        views.buttonTruckLoadSort.setOnClickListener(this::showSortMenu);
        views.buttonTruckLoadFilter.setOnClickListener(this::showTripMenu);
        views.buttonTruckLoadSummary.setOnClickListener(v -> showSummary());
        views.chipTruckLoadFilter.setCloseIconContentDescription(getString(R.string.truck_load_filter_clear));
        views.chipTruckLoadFilter.setOnCloseIconClickListener(v -> viewModel.onTripFilterSelected(null));
        // Acciones aún sin servicio en el backend: se avisa en lugar de ignorar el toque
        views.buttonTruckLoadIncidence.setOnClickListener(v -> showUnavailable("Incidencia"));
        views.buttonTruckLoadInfo.setOnClickListener(v -> showUnavailable("Información"));
        views.buttonTruckLoadView.setOnClickListener(v -> showUnavailable("Ver carga"));
        views.buttonTruckLoadFinish.setOnClickListener(v -> showUnavailable("Finalizar carga"));

        viewModel.getState().observe(getViewLifecycleOwner(), this::render);
    }

    /**
     * La toolbar es de MainActivity y común a todas las pantallas: la acción se añade con el
     * ciclo de vida de la vista y solo en RESUMED, así no se solapa con la pantalla siguiente.
     */
    private void addRefreshToToolbar() {
        MenuHost toolbar = requireActivity().findViewById(R.id.toolbar);
        toolbar.addMenuProvider(new MenuProvider() {
            @Override
            public void onCreateMenu(@NonNull Menu menu, @NonNull MenuInflater menuInflater) {
                menuInflater.inflate(R.menu.menu_truck_load, menu);
            }

            @Override
            public boolean onMenuItemSelected(@NonNull MenuItem menuItem) {
                if (menuItem.getItemId() == R.id.action_truck_load_refresh) {
                    Timber.i("[TOOLBAR] Refrescar carga pulsado");
                    viewModel.onRefreshClicked();
                    return true;
                }
                return false;
            }
        }, getViewLifecycleOwner(), Lifecycle.State.RESUMED);
    }

    private void showSortMenu(@NonNull View anchor) {
        if (!(viewModel.getState().getValue() instanceof TruckLoadState.Ready ready)) {
            return;
        }
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();
        TruckLoadSort[] sorts = TruckLoadSort.values();
        for (int i = 0; i < sorts.length; i++) {
            menu.add(Menu.NONE, i, i, sortLabel(sorts[i])).setChecked(sorts[i] == ready.sort());
        }
        menu.setGroupCheckable(Menu.NONE, true, true);
        popup.setOnMenuItemClickListener(item -> {
            viewModel.onSortSelected(sorts[item.getItemId()]);
            return true;
        });
        popup.show();
    }

    /** "Todas" y cada vuelta presente en la carga; la activa va marcada. */
    private void showTripMenu(@NonNull View anchor) {
        if (!(viewModel.getState().getValue() instanceof TruckLoadState.Ready ready)) {
            return;
        }
        PopupMenu popup = new PopupMenu(requireContext(), anchor);
        Menu menu = popup.getMenu();
        menu.add(Menu.NONE, Menu.NONE, 0, R.string.truck_load_filter_all)
                .setChecked(ready.tripFilter() == null);
        List<Integer> trips = ready.trips();
        for (int i = 0; i < trips.size(); i++) {
            int trip = trips.get(i);
            menu.add(Menu.NONE, trip, i + 1, getString(R.string.truck_load_tag_trip, trip))
                    .setChecked(ready.tripFilter() != null && ready.tripFilter() == trip);
        }
        menu.setGroupCheckable(Menu.NONE, true, true);
        popup.setOnMenuItemClickListener(item -> {
            viewModel.onTripFilterSelected(item.getItemId() == Menu.NONE ? null : item.getItemId());
            return true;
        });
        popup.show();
    }

    /** Resumen de toda la carga, se vea filtrada o no. */
    private void showSummary() {
        if (viewModel.getState().getValue() instanceof TruckLoadState.Ready ready) {
            TruckLoadSummaryBottomSheet.showIfNotShown(getChildFragmentManager(), ready.summary());
        }
    }

    @StringRes
    private static int sortLabel(@NonNull TruckLoadSort sort) {
        return switch (sort) {
            case LOAD_ORDER -> R.string.truck_load_sort_load_order;
            case EXPEDITION_NUMBER -> R.string.truck_load_sort_number;
            case DELIVERY_NOTE -> R.string.truck_load_sort_delivery_note;
            case SENDER -> R.string.truck_load_sort_sender;
            case CONSIGNEE -> R.string.truck_load_sort_consignee;
            case POSTAL_CODE -> R.string.truck_load_sort_postal_code;
            case TOWN -> R.string.truck_load_sort_town;
            case ZONE -> R.string.truck_load_sort_zone;
        };
    }

    private void render(@NonNull TruckLoadState state) {
        FragmentTruckLoadBinding views = requireBinding();
        boolean ready = state instanceof TruckLoadState.Ready;
        views.layoutTruckLoadContent.setVisibility(ready ? View.VISIBLE : View.GONE);
        // En horizontal el progreso va en el panel lateral, fuera del contenido de la lista
        if (views.layoutTruckLoadProgress != null) {
            views.layoutTruckLoadProgress.setVisibility(ready ? View.VISIBLE : View.GONE);
        }
        views.layoutTruckLoadLoading.setVisibility(
                state instanceof TruckLoadState.Loading ? View.VISIBLE : View.GONE);
        views.layoutTruckLoadMessage.setVisibility(
                state instanceof TruckLoadState.Empty || state instanceof TruckLoadState.Failed
                        ? View.VISIBLE : View.GONE);

        if (state instanceof TruckLoadState.Ready readyState) {
            renderReady(readyState);
        } else {
            renderListActions(false, false, false);
            views.buttonTruckLoadView.setEnabled(false);
            views.buttonTruckLoadFinish.setEnabled(false);
            views.textTruckLoadFinishHint.setVisibility(View.GONE);
            progressShown = false;
        }
        if (state instanceof TruckLoadState.Empty) {
            renderMessage(R.drawable.ic_local_shipping_24, null, getString(R.string.truck_load_empty_title),
                    getString(R.string.truck_load_empty), R.string.truck_load_refresh,
                    v -> viewModel.onRefreshClicked());
        } else if (state instanceof TruckLoadState.Failed failed) {
            renderMessage(R.drawable.ic_error_24, R.color.msg_error_icon, getString(R.string.truck_load_error_title),
                    failed.message().resolve(requireContext()), R.string.truck_load_retry,
                    v -> viewModel.onRetryClicked());
        }
    }

    private void renderReady(@NonNull TruckLoadState.Ready state) {
        FragmentTruckLoadBinding views = requireBinding();
        views.textTruckLoadSummary.setText(getResources().getQuantityString(R.plurals.truck_load_summary,
                state.totalCount(), state.completeCount(), state.totalCount()));
        int percent = state.totalCount() == 0 ? 0 : state.completeCount() * PERCENT / state.totalCount();
        views.progressTruckLoadComplete.setProgressCompat(percent, progressShown);
        progressShown = true;

        Integer tripFilter = state.tripFilter();
        views.chipTruckLoadFilter.setVisibility(tripFilter != null ? View.VISIBLE : View.GONE);
        if (tripFilter != null) {
            views.chipTruckLoadFilter.setText(getString(R.string.truck_load_tag_trip, tripFilter));
        }

        if (state.refreshing()) {
            views.progressTruckLoadRefresh.show();
        } else {
            views.progressTruckLoadRefresh.hide();
        }

        // Filtrar solo tiene sentido con más de una vuelta, o para quitar un filtro activo
        renderListActions(true, state.trips().size() > 1 || tripFilter != null, state.hasSelection());
        views.buttonTruckLoadView.setEnabled(true);
        views.buttonTruckLoadFinish.setEnabled(state.canFinish());
        views.textTruckLoadFinishHint.setVisibility(state.canFinish() ? View.GONE : View.VISIBLE);

        if (adapter != null) {
            // La marca se busca en la lista ya aplicada: el diff termina después de submitList
            String selected = state.selected();
            adapter.submitList(state.expeditions(), () -> {
                if (adapter != null) {
                    adapter.setSelected(selected);
                }
            });
        }

        if (state.refreshError() != null) {
            Snackbar.make(views.getRoot(), state.refreshError().resolve(requireContext()), Snackbar.LENGTH_LONG)
                    .setAnchorView(views.layoutTruckLoadFooter)
                    .show();
            viewModel.onRefreshErrorShown();
        }
    }

    private void renderListActions(boolean listEnabled, boolean filterEnabled, boolean selectionEnabled) {
        FragmentTruckLoadBinding views = requireBinding();
        views.buttonTruckLoadSort.setEnabled(listEnabled);
        views.buttonTruckLoadFilter.setEnabled(listEnabled && filterEnabled);
        views.buttonTruckLoadSummary.setEnabled(listEnabled);
        views.buttonTruckLoadIncidence.setEnabled(selectionEnabled);
        views.buttonTruckLoadInfo.setEnabled(selectionEnabled);
    }

    private void renderMessage(@DrawableRes int icon, @Nullable @ColorRes Integer iconColor, @NonNull String title,
                               @NonNull String message, @StringRes int action,
                               @NonNull View.OnClickListener onAction) {
        FragmentTruckLoadBinding views = requireBinding();
        views.imageTruckLoadMessage.setImageResource(icon);
        views.imageTruckLoadMessage.setImageTintList(iconColor != null
                ? ContextCompat.getColorStateList(requireContext(), iconColor)
                : ColorStateList.valueOf(MaterialColors.getColor(
                views.imageTruckLoadMessage, androidx.appcompat.R.attr.colorPrimary)));
        views.textTruckLoadMessageTitle.setText(title);
        views.textTruckLoadMessage.setText(message);
        views.buttonTruckLoadMessageAction.setText(action);
        views.buttonTruckLoadMessageAction.setOnClickListener(onAction);
    }

    private void showUnavailable(@NonNull String action) {
        Timber.i("[ACCION] %s pulsado (no disponible)", action);
        FragmentTruckLoadBinding views = requireBinding();
        Snackbar.make(views.getRoot(), R.string.feature_unavailable, Snackbar.LENGTH_SHORT)
                .setAnchorView(views.layoutTruckLoadFooter)
                .show();
    }

    @NonNull
    private FragmentTruckLoadBinding requireBinding() {
        if (binding == null) {
            throw new IllegalStateException("La vista de la carga de camión no está disponible");
        }
        return binding;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (binding != null) {
            binding.recyclerTruckLoadExpeditions.setAdapter(null);
        }
        adapter = null;
        progressShown = false;
        binding = null;
    }
}
