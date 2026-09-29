package com.redur.electra.ui.place;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.redur.electra.R;
import com.redur.electra.data.model.place.Place;
import com.redur.electra.databinding.ItemPlazaDropdownBinding;

import java.util.List;

/**
 * Opciones del desplegable de plazas (item_plaza_dropdown). El desplegable es de solo selección:
 * el filtro no descarta opciones, así tras elegir una plaza se siguen ofreciendo todas.
 */
final class PlaceAdapter extends ArrayAdapter<Place> {

    private final LayoutInflater inflater;

    private final Filter showAllFilter = new Filter() {
        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            return new FilterResults();
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            notifyDataSetChanged();
        }

        @Override
        public CharSequence convertResultToString(Object resultValue) {
            return format((Place) resultValue);
        }
    };

    PlaceAdapter(@NonNull Context context) {
        super(context, R.layout.item_plaza_dropdown);
        inflater = LayoutInflater.from(context);
    }

    void setPlaces(@NonNull List<Place> places) {
        setNotifyOnChange(false);
        clear();
        addAll(places);
        notifyDataSetChanged();
    }

    /** Texto que muestra el campo para la plaza elegida: "P02 · Valencia Norte". */
    @NonNull
    String format(@NonNull Place place) {
        if (place.description().isBlank()) {
            return place.id();
        }
        return getContext().getString(R.string.change_plaza_option, place.id(), place.description());
    }

    @NonNull
    @Override
    public View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        ItemPlazaDropdownBinding binding = convertView != null
                ? ItemPlazaDropdownBinding.bind(convertView)
                : ItemPlazaDropdownBinding.inflate(inflater, parent, false);
        Place place = getItem(position);
        if (place != null) {
            binding.textPlazaCode.setText(place.id());
            binding.textPlazaDescription.setText(place.description());
            binding.textPlazaDescription.setVisibility(
                    place.description().isBlank() ? View.GONE : View.VISIBLE);
        }
        return binding.getRoot();
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return showAllFilter;
    }
}
