package com.redur.electra.ui.menu;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat.AccessibilityActionCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.redur.electra.R;
import com.redur.electra.databinding.ItemDrawerMenuBinding;

import java.util.List;

/** Filas del menú lateral. Solo pinta: qué hacer al pulsar lo decide quien la crea. */
public class DrawerMenuAdapter extends ListAdapter<DrawerMenuRow, DrawerMenuAdapter.RowViewHolder> {

    public interface OnRowClickListener {
        void onRowClicked(@NonNull DrawerMenuRow row);
    }

    /** Solo ha cambiado el despliegue: se anima el chevron en lugar de repintar la fila. */
    private static final Object PAYLOAD_EXPANSION = new Object();
    private static final float CHEVRON_EXPANDED_ROTATION = 180f;

    private static final DiffUtil.ItemCallback<DrawerMenuRow> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull DrawerMenuRow oldRow, @NonNull DrawerMenuRow newRow) {
            return oldRow.menuId() == newRow.menuId();
        }

        @Override
        public boolean areContentsTheSame(@NonNull DrawerMenuRow oldRow, @NonNull DrawerMenuRow newRow) {
            return oldRow.equals(newRow);
        }

        @Override
        public Object getChangePayload(@NonNull DrawerMenuRow oldRow, @NonNull DrawerMenuRow newRow) {
            boolean onlyExpansion = oldRow.expanded() != newRow.expanded()
                    && oldRow.withExpanded(newRow.expanded()).equals(newRow);
            return onlyExpansion ? PAYLOAD_EXPANSION : null;
        }
    };

    @NonNull
    private final OnRowClickListener listener;

    public DrawerMenuAdapter(@NonNull OnRowClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public RowViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemDrawerMenuBinding binding = ItemDrawerMenuBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new RowViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    @Override
    public void onBindViewHolder(@NonNull RowViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_EXPANSION)) {
            holder.bindExpansion(getItem(position), true);
        } else {
            super.onBindViewHolder(holder, position, payloads);
        }
    }

    class RowViewHolder extends RecyclerView.ViewHolder {

        private final ItemDrawerMenuBinding binding;
        /** Padding inicial del layout: la sangría de cada nivel se suma sobre él. */
        private final int basePaddingStart;
        private final int levelIndent;

        RowViewHolder(@NonNull ItemDrawerMenuBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
            this.basePaddingStart = binding.getRoot().getPaddingStart();
            this.levelIndent = binding.getRoot().getResources().getDimensionPixelSize(R.dimen.drawer_level_indent);
        }

        void bind(@NonNull DrawerMenuRow row) {
            Context context = binding.getRoot().getContext();
            View root = binding.getRoot();
            root.setPaddingRelative(basePaddingStart + row.depth() * levelIndent,
                    root.getPaddingTop(), root.getPaddingEnd(), root.getPaddingBottom());
            // setSelected se propaga a icono y texto: sus colores cambian sobre la píldora activa
            root.setSelected(row.selected());
            binding.imageDrawerItemIcon.setImageResource(row.icon());
            binding.textDrawerItemTitle.setText(row.title().resolve(context));
            binding.imageDrawerItemChevron.setVisibility(row.group() ? View.VISIBLE : View.GONE);
            root.setOnClickListener(v -> {
                int position = getBindingAdapterPosition();
                if (position != RecyclerView.NO_POSITION) {
                    listener.onRowClicked(getItem(position));
                }
            });
            bindExpansion(row, false);
        }

        /** Chevron y lectura de TalkBack ("Administración, plegado; doble toque para desplegar"). */
        void bindExpansion(@NonNull DrawerMenuRow row, boolean animate) {
            View root = binding.getRoot();
            Context context = root.getContext();
            float rotation = row.expanded() ? CHEVRON_EXPANDED_ROTATION : 0f;
            binding.imageDrawerItemChevron.animate().cancel();
            if (animate) {
                binding.imageDrawerItemChevron.animate()
                        .rotation(rotation)
                        .setDuration(context.getResources().getInteger(R.integer.motion_duration_layout))
                        .setInterpolator(new DecelerateInterpolator())
                        .start();
            } else {
                binding.imageDrawerItemChevron.setRotation(rotation);
            }
            if (row.group()) {
                ViewCompat.setStateDescription(root, context.getString(
                        row.expanded() ? R.string.drawer_group_expanded : R.string.drawer_group_collapsed));
                ViewCompat.replaceAccessibilityAction(root, AccessibilityActionCompat.ACTION_CLICK,
                        context.getString(row.expanded() ? R.string.drawer_group_collapse : R.string.drawer_group_expand),
                        null);
            } else {
                ViewCompat.setStateDescription(root, null);
                ViewCompat.replaceAccessibilityAction(root, AccessibilityActionCompat.ACTION_CLICK, null, null);
            }
        }
    }
}
