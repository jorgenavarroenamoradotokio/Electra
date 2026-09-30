package com.redur.electra.ui.home;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.redur.electra.R;
import com.redur.electra.databinding.FragmentHomeBinding;
import com.redur.electra.ui.MainViewModel;

import dagger.hilt.android.AndroidEntryPoint;

/** Destino inicial del menú lateral. */
@AndroidEntryPoint
public class HomeFragment extends Fragment {

    @Nullable
    private FragmentHomeBinding binding;

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        binding = FragmentHomeBinding.inflate(inflater, container, false);
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        // Datos de la sesión compartidos con la Activity: se usa su ViewModel
        MainViewModel viewModel = new ViewModelProvider(requireActivity()).get(MainViewModel.class);
        String name = viewModel.getUserDisplayName();
        if (binding != null && name != null) {
            binding.textHomeGreeting.setText(getString(R.string.home_greeting, name));
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }
}
