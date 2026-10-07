package com.redur.electra.ui.bulto.incidencia;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoIncidence;
import com.redur.electra.databinding.ItemBultoIncidenceBinding;

import java.util.Objects;

/**
 * Incidencias con selección única. Solo pinta: qué hacer al pulsar lo decide quien la crea y la
 * incidencia elegida se le indica con {@link #setSelectedCode(String)}.
 */
public class BultoIncidenceAdapter
        extends ListAdapter<BultoIncidence, BultoIncidenceAdapter.BultoIncidenceViewHolder> {

    public interface OnIncidenceClickListener {
        void onIncidenceClicked(@NonNull BultoIncidence incidence);
    }

    private static final DiffUtil.ItemCallback<BultoIncidence> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull BultoIncidence oldItem, @NonNull BultoIncidence newItem) {
            return oldItem.code().equals(newItem.code());
        }

        @Override
        public boolean areContentsTheSame(@NonNull BultoIncidence oldItem, @NonNull BultoIncidence newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    private final OnIncidenceClickListener listener;
    @Nullable
    private String selectedCode;

    public BultoIncidenceAdapter(@NonNull OnIncidenceClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Marca la incidencia elegida; solo se repintan la fila que la pierde y la que la gana. */
    public void setSelectedCode(@Nullable String code) {
        if (Objects.equals(selectedCode, code)) {
            return;
        }
        int previous = positionOf(selectedCode);
        selectedCode = code;
        int current = positionOf(code);
        if (previous != RecyclerView.NO_POSITION) {
            notifyItemChanged(previous);
        }
        if (current != RecyclerView.NO_POSITION) {
            notifyItemChanged(current);
        }
    }

    @NonNull
    @Override
    public BultoIncidenceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBultoIncidenceBinding binding = ItemBultoIncidenceBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new BultoIncidenceViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull BultoIncidenceViewHolder holder, int position) {
        BultoIncidence incidence = getItem(position);
        holder.bind(incidence, incidence.code().equals(selectedCode));
    }

    private int positionOf(@Nullable String code) {
        if (code == null) {
            return RecyclerView.NO_POSITION;
        }
        for (int i = 0; i < getItemCount(); i++) {
            if (getItem(i).code().equals(code)) {
                return i;
            }
        }
        return RecyclerView.NO_POSITION;
    }

    static final class BultoIncidenceViewHolder extends RecyclerView.ViewHolder {

        private final ItemBultoIncidenceBinding binding;
        private final OnIncidenceClickListener listener;
        private boolean selected;

        BultoIncidenceViewHolder(@NonNull ItemBultoIncidenceBinding binding,
                                 @NonNull OnIncidenceClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
            // TalkBack lee la fila como un elemento marcable ("marcado" / "no marcado")
            ViewCompat.setAccessibilityDelegate(binding.getRoot(), new AccessibilityDelegateCompat() {
                @Override
                public void onInitializeAccessibilityNodeInfo(@NonNull View host,
                                                              @NonNull AccessibilityNodeInfoCompat info) {
                    super.onInitializeAccessibilityNodeInfo(host, info);
                    info.setCheckable(true);
                    info.setChecked(selected);
                }
            });
        }

        void bind(@NonNull BultoIncidence incidence, boolean selected) {
            this.selected = selected;
            binding.textBultoIncidenceCode.setText(incidence.code());
            binding.layoutBultoIncidencePhoto.setVisibility(incidence.photoRequired() ? View.VISIBLE : View.GONE);
            binding.radioBultoIncidence.setChecked(selected);
            binding.getRoot().setActivated(selected);
            binding.getRoot().setContentDescription(incidence.photoRequired()
                    ? binding.getRoot().getContext().getString(
                            R.string.bulto_incidence_item_description_photo, incidence.code())
                    : incidence.code());
            binding.getRoot().setOnClickListener(v -> listener.onIncidenceClicked(incidence));
        }
    }
}
