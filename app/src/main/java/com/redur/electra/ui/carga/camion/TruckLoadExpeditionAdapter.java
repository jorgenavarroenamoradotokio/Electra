package com.redur.electra.ui.carga.camion;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.content.ContextCompat;
import androidx.core.widget.TextViewCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.chip.Chip;
import com.redur.electra.R;
import com.redur.electra.data.model.carga.Expedition;
import com.redur.electra.databinding.ItemTruckLoadExpeditionBinding;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Expediciones de la carga. Tocar una fila la elige; la elegida se marca con el estado activated
 * de la fila, que TalkBack anuncia como seleccionada.
 */
public class TruckLoadExpeditionAdapter
        extends ListAdapter<Expedition, TruckLoadExpeditionAdapter.ExpeditionViewHolder> {

    /** Cambio de elegida: solo se repinta la marca, no la fila entera. */
    private static final Object PAYLOAD_SELECTION = new Object();

    private static final DiffUtil.ItemCallback<Expedition> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Expedition oldItem, @NonNull Expedition newItem) {
            return oldItem.number().equals(newItem.number());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Expedition oldItem, @NonNull Expedition newItem) {
            return oldItem.equals(newItem);
        }
    };

    @NonNull
    private final Consumer<String> onExpeditionClicked;
    @Nullable
    private String selected;

    public TruckLoadExpeditionAdapter(@NonNull Consumer<String> onExpeditionClicked) {
        super(DIFF);
        this.onExpeditionClicked = onExpeditionClicked;
    }

    /** Marca la expedición elegida; null para ninguna. */
    public void setSelected(@Nullable String number) {
        if (Objects.equals(selected, number)) {
            return;
        }
        String previous = selected;
        selected = number;
        notifySelectionChanged(previous);
        notifySelectionChanged(number);
    }

    private void notifySelectionChanged(@Nullable String number) {
        if (number == null) {
            return;
        }
        List<Expedition> items = getCurrentList();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).number().equals(number)) {
                notifyItemChanged(i, PAYLOAD_SELECTION);
                return;
            }
        }
    }

    @NonNull
    @Override
    public ExpeditionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ExpeditionViewHolder holder = new ExpeditionViewHolder(ItemTruckLoadExpeditionBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
        holder.binding.getRoot().setOnClickListener(v -> {
            int position = holder.getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                onExpeditionClicked.accept(getItem(position).number());
            }
        });
        return holder;
    }

    @Override
    public void onBindViewHolder(@NonNull ExpeditionViewHolder holder, int position) {
        Expedition expedition = getItem(position);
        holder.bind(expedition);
        holder.bindSelection(expedition.number().equals(selected));
    }

    @Override
    public void onBindViewHolder(@NonNull ExpeditionViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION) && payloads.size() == 1) {
            holder.bindSelection(getItem(position).number().equals(selected));
        } else {
            onBindViewHolder(holder, position);
        }
    }

    static final class ExpeditionViewHolder extends RecyclerView.ViewHolder {

        private final ItemTruckLoadExpeditionBinding binding;

        ExpeditionViewHolder(@NonNull ItemTruckLoadExpeditionBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bindSelection(boolean isSelected) {
            binding.getRoot().setActivated(isSelected);
            binding.getRoot().setSelected(isSelected);
        }

        void bind(@NonNull Expedition expedition) {
            Context context = binding.getRoot().getContext();
            String address = context.getString(R.string.truck_load_expedition_address,
                    expedition.consignee().address(), expedition.consignee().postalCode(),
                    expedition.consignee().town());
            String status = bindStatus(context, expedition);
            List<TruckLoadTag> tags = TruckLoadTags.of(expedition);

            binding.textTruckLoadExpeditionNumber.setText(
                    context.getString(R.string.truck_load_expedition_number, expedition.number()));
            binding.textTruckLoadExpeditionSender.setText(context.getString(
                    R.string.truck_load_expedition_sender, expedition.sender(), expedition.deliveryNote()));
            binding.textTruckLoadExpeditionConsignee.setText(expedition.consignee().name());
            binding.textTruckLoadExpeditionAddress.setText(address);
            binding.textTruckLoadExpeditionDocumented.setText(number(expedition.documented()));
            binding.textTruckLoadExpeditionDestination.setText(number(expedition.atDestination()));
            binding.textTruckLoadExpeditionScanned.setText(number(expedition.scanned()));
            binding.textTruckLoadExpeditionZone.setText(expedition.zone());
            binding.textTruckLoadExpeditionOrder.setText(context.getString(
                    R.string.truck_load_order_value, expedition.loadOrder(), expedition.loadTotal()));
            bindTags(context, tags);
            binding.getRoot().setContentDescription(describe(context, expedition, status, tags));
        }

        /** @return el texto del estado, para la descripción de TalkBack. */
        @NonNull
        private String bindStatus(@NonNull Context context, @NonNull Expedition expedition) {
            int pending = expedition.pendingBultos();
            String text;
            if (pending == 0) {
                text = context.getString(R.string.truck_load_status_complete);
                styleStatus(context, text, R.drawable.ic_check_24, R.color.msg_success_bg,
                        R.color.msg_success_text, R.color.msg_success_icon);
            } else if (pending > 0) {
                text = context.getString(R.string.truck_load_status_missing, pending);
                styleStatus(context, text, R.drawable.ic_report_problem_24, R.color.msg_warning_bg,
                        R.color.msg_warning_text, R.color.msg_warning_icon);
            } else {
                text = context.getString(R.string.truck_load_status_extra, -pending);
                styleStatus(context, text, R.drawable.ic_error_24, R.color.msg_error_bg,
                        R.color.msg_error_text, R.color.msg_error_icon);
            }
            return text;
        }

        private void styleStatus(@NonNull Context context, @NonNull String text, @DrawableRes int icon,
                                 @ColorRes int background, @ColorRes int textColor, @ColorRes int iconColor) {
            binding.textTruckLoadExpeditionStatus.setText(text);
            binding.textTruckLoadExpeditionStatus.setTextColor(ContextCompat.getColor(context, textColor));
            binding.textTruckLoadExpeditionStatus.setBackgroundTintList(
                    ContextCompat.getColorStateList(context, background));
            // Los iconos son de 24dp: dentro de la etiqueta van al tamaño del texto
            Drawable drawable = AppCompatResources.getDrawable(context, icon);
            if (drawable != null) {
                int size = context.getResources().getDimensionPixelSize(R.dimen.icon_inline_size);
                drawable.setBounds(0, 0, size, size);
            }
            binding.textTruckLoadExpeditionStatus.setCompoundDrawablesRelative(drawable, null, null, null);
            TextViewCompat.setCompoundDrawableTintList(binding.textTruckLoadExpeditionStatus,
                    ContextCompat.getColorStateList(context, iconColor));
        }

        private void bindTags(@NonNull Context context, @NonNull List<TruckLoadTag> tags) {
            binding.groupTruckLoadExpeditionTags.removeAllViews();
            binding.groupTruckLoadExpeditionTags.setVisibility(tags.isEmpty() ? View.GONE
                    : View.VISIBLE);
            LayoutInflater inflater = LayoutInflater.from(context);
            for (TruckLoadTag tag : tags) {
                Chip chip = (Chip) inflater.inflate(R.layout.item_truck_load_tag,
                        binding.groupTruckLoadExpeditionTags, false);
                chip.setText(tag.label().resolve(context));
                chip.setChipIconVisible(tag.icon() != 0);
                if (tag.icon() != 0) {
                    chip.setChipIconResource(tag.icon());
                }
                if (tag.alert()) {
                    chip.setChipBackgroundColorResource(R.color.msg_warning_bg);
                    chip.setTextColor(ContextCompat.getColor(context, R.color.msg_warning_text));
                    chip.setChipIconTint(ColorStateList.valueOf(ContextCompat.getColor(context, R.color.msg_warning_icon)));
                }
                binding.groupTruckLoadExpeditionTags.addView(chip);
            }
        }

        @NonNull
        private static String describe(@NonNull Context context, @NonNull Expedition expedition,
                                       @NonNull String status, @NonNull List<TruckLoadTag> tags) {
            String description = context.getString(R.string.truck_load_expedition_description,
                    expedition.number(), expedition.deliveryNote(), expedition.sender(),
                    expedition.consignee().name(), expedition.consignee().address(),
                    expedition.consignee().postalCode(), expedition.consignee().town(), status,
                    expedition.documented(), expedition.atDestination(), expedition.scanned(),
                    expedition.zone(), expedition.loadOrder(), expedition.loadTotal());
            if (tags.isEmpty()) {
                return description;
            }
            StringBuilder labels = new StringBuilder();
            for (TruckLoadTag tag : tags) {
                if (labels.length() > 0) {
                    labels.append(", ");
                }
                labels.append(tag.label().resolve(context));
            }
            return description + " " + context.getString(R.string.truck_load_tags_description, labels);
        }

        @NonNull
        private static String number(int value) {
            return String.format(Locale.getDefault(), "%d", value);
        }
    }
}
