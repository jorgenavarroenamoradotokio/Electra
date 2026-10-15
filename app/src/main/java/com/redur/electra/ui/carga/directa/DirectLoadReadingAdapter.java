package com.redur.electra.ui.carga.directa;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.redur.electra.R;
import com.redur.electra.data.model.bulto.BultoType;
import com.redur.electra.databinding.ItemDirectLoadReadingBinding;

import java.util.Locale;

/** Bultos leídos en la carga, el último primero. Solo pinta: no hay acciones sobre las lecturas. */
public class DirectLoadReadingAdapter extends ListAdapter<DirectLoadReading, DirectLoadReadingAdapter.ReadingViewHolder> {

    private static final DiffUtil.ItemCallback<DirectLoadReading> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull DirectLoadReading oldReading, @NonNull DirectLoadReading newReading) {
            // Un bulto solo se lee una vez por carga
            return oldReading.barcode().equals(newReading.barcode());
        }

        @Override
        public boolean areContentsTheSame(@NonNull DirectLoadReading oldReading, @NonNull DirectLoadReading newReading) {
            return oldReading.equals(newReading);
        }
    };

    public DirectLoadReadingAdapter() {
        super(DIFF);
    }

    @NonNull
    @Override
    public ReadingViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ReadingViewHolder(ItemDirectLoadReadingBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull ReadingViewHolder holder, int position) {
        // El último leído va arriba: su nº de orden es el total de lecturas
        holder.bind(getItem(position), getItemCount() - position);
    }

    static final class ReadingViewHolder extends RecyclerView.ViewHolder {

        private final ItemDirectLoadReadingBinding binding;

        ReadingViewHolder(@NonNull ItemDirectLoadReadingBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }

        void bind(@NonNull DirectLoadReading reading, int order) {
            Context context = binding.getRoot().getContext();
            BultoType type = reading.type();
            binding.textDirectLoadReadingPosition.setText(String.format(Locale.getDefault(), "%d", order));
            binding.textDirectLoadReadingBarcode.setText(reading.barcode());
            if (type != null) {
                binding.textDirectLoadReadingType.setText(type.type());
                binding.textDirectLoadReadingType.setBackgroundResource(R.drawable.bg_direct_load_type_badge);
                binding.textDirectLoadReadingType.setTextColor(
                        MaterialColors.getColor(binding.getRoot(), androidx.appcompat.R.attr.colorPrimary));
                binding.getRoot().setContentDescription(context.getString(
                        R.string.direct_load_reading_description, order, reading.barcode(), type.type()));
            } else {
                binding.textDirectLoadReadingType.setText(R.string.direct_load_reading_no_type);
                binding.textDirectLoadReadingType.setBackground(null);
                binding.textDirectLoadReadingType.setTextColor(MaterialColors.getColor(binding.getRoot(),
                        com.google.android.material.R.attr.colorOnSurfaceVariant));
                binding.getRoot().setContentDescription(context.getString(
                        R.string.direct_load_reading_description_no_type, order, reading.barcode()));
            }
        }
    }
}
