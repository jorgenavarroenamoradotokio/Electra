package com.redur.electra.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.redur.electra.BuildConfig;
import com.redur.electra.R;
import com.redur.electra.databinding.ActivityMainBinding;
import com.redur.electra.ui.logout.LogoutBottomSheet;
import com.redur.electra.ui.profile.ProfileActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // La toolbar es oscura en ambos temas: iconos de la status bar siempre claros
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        MainViewModel viewModel = new ViewModelProvider(this).get(MainViewModel.class);

        applyWindowInsets();
        setupToolbar(viewModel.getUserDisplayName());
        setupBackNavigation();
    }

    /** La franja de la toolbar se extiende bajo la status bar; sus acciones quedan fuera de los recortes. */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.appBar.getRoot().setPadding(bars.left, bars.top, bars.right, 0);
            root.setPadding(0, 0, 0, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupToolbar(String subtitle) {
        MaterialToolbar toolbar = binding.appBar.toolbar;
        toolbar.setTitle(R.string.app_name);
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked);
    }

    private boolean onToolbarItemClicked(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            // Pantalla aún no implementada: se avisa en lugar de ignorar el toque
            Snackbar.make(binding.getRoot(), R.string.feature_unavailable, Snackbar.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }

    /** "Atrás" en la pantalla principal no cierra la app: pide confirmar el cierre de sesión. */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                LogoutBottomSheet.showIfNotShown(getSupportFragmentManager());
            }
        });
    }
}
