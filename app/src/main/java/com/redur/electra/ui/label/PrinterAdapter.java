package com.redur.electra.ui.label;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.redur.electra.R;
import com.redur.electra.data.model.printer.Printer;
import com.redur.electra.databinding.ItemPrinterBinding;

/** Impresoras Bluetooth disponibles. Solo pinta: qué hacer al pulsar lo decide quien la crea. */
public class PrinterAdapter extends ListAdapter<Printer, PrinterAdapter.PrinterViewHolder> {

    public interface OnPrinterClickListener {
        void onPrinterClicked(@NonNull Printer printer);
    }

    private static final DiffUtil.ItemCallback<Printer> DIFF = new DiffUtil.ItemCallback<>() {
        @Override
        public boolean areItemsTheSame(@NonNull Printer oldPrinter, @NonNull Printer newPrinter) {
            return oldPrinter.address().equals(newPrinter.address());
        }

        @Override
        public boolean areContentsTheSame(@NonNull Printer oldPrinter, @NonNull Printer newPrinter) {
            return oldPrinter.equals(newPrinter);
        }
    };

    @NonNull
    private final OnPrinterClickListener listener;

    public PrinterAdapter(@NonNull OnPrinterClickListener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public PrinterViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemPrinterBinding binding = ItemPrinterBinding.inflate(
                LayoutInflater.from(parent.getContext()), parent, false);
        return new PrinterViewHolder(binding, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull PrinterViewHolder holder, int position) {
        holder.bind(getItem(position));
    }

    static final class PrinterViewHolder extends RecyclerView.ViewHolder {

        private final ItemPrinterBinding binding;
        private final OnPrinterClickListener listener;

        PrinterViewHolder(@NonNull ItemPrinterBinding binding, @NonNull OnPrinterClickListener listener) {
            super(binding.getRoot());
            this.binding = binding;
            this.listener = listener;
        }

        void bind(@NonNull Printer printer) {
            Context context = binding.getRoot().getContext();
            binding.imagePrinterIcon.setImageResource(
                    printer.likelyPrinter() ? R.drawable.ic_print_24 : R.drawable.ic_bluetooth_24);
            binding.textPrinterName.setText(printer.name());
            // El estado se dice con texto, no solo con el icono
            binding.textPrinterDetail.setText(printer.paired()
                    ? context.getString(R.string.print_printer_paired, printer.address())
                    : printer.address());
            binding.getRoot().setOnClickListener(v -> listener.onPrinterClicked(printer));
        }
    }
}
