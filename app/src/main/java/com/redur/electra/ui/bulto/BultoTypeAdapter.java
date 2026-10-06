package com.redur.electra.ui.bulto;

import android.content.Context;
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
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.databinding.ItemBultoTypeBinding;

import java.util.Objects;

/**
 * Tipos de bulto con selección única. Solo pinta: qué hacer al pulsar lo decide quien la crea y el
 * tipo elegido se le indica con {@link #setSelectedCode(String)}.
 */
public class BultoTypeAdapter extends ListAdapter<BultoType, BultoTypeAdapter.BultoTypeViewHolder> {

    public interface OnBultoTypeClickListener {
        void onBultoTypeClicked(@NonNull BultoType type);
    }

    private static final DiffUtil.ItemCallback<BultoType> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull BultoType oldType, @NonNull BultoType newType) {
            return oldType.code().equals(newType.code());
        }

        @Override
        public boolean areContentsTheSame(@NonNull BultoType oldType, @NonNull BultoType newType) {
            return oldType.equals(newType);
        }
    };

    @NonNull
    private final OnBultoTypeClickListener listener;
    @Nullable
    private String selectedCode;

    public BultoTypeAdapter(@NonNull OnBultoTypeClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Marca el tipo elegido; solo se repintan la fila que lo pierde y la que lo gana. */
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
    public BultoTypeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemBultoTypeBinding binding = ItemBultoTypeBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new BultoTypeViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull BultoTypeViewHolder holder, int position) {
        BultoType type = getItem(position);
        holder.bind(type, type.code().equals(selectedCode));
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

    static final class BultoTypeViewHolder extends RecyclerView.ViewHolder {

        private final ItemBultoTypeBinding binding;
        private final OnBultoTypeClickListener listener;
        private boolean selected;

        BultoTypeViewHolder(@NonNull ItemBultoTypeBinding binding, @NonNull OnBultoTypeClickListener listener) {
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

        void bind(@NonNull BultoType type, boolean selected) {
            this.selected = selected;
            Context context = binding.getRoot().getContext();
            binding.textBultoType.setText(type.type());
            binding.textBultoTypeDescription.setText(type.description());
            binding.textBultoTypeDescription.setVisibility(
                    type.description().isBlank() ? View.GONE : View.VISIBLE);
            binding.layoutBultoTypePhoto.setVisibility(type.photoRequired() ? View.VISIBLE : View.GONE);
            binding.radioBultoType.setChecked(selected);
            binding.getRoot().setActivated(selected);
            binding.getRoot().setContentDescription(context.getString(type.photoRequired()
                            ? R.string.bulto_type_item_description_photo
                            : R.string.bulto_type_item_description,
                    type.type(), type.description()));
            binding.getRoot().setOnClickListener(v -> listener.onBultoTypeClicked(type));
        }
    }
}
