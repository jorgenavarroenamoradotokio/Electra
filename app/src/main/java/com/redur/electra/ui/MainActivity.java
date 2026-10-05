package com.redur.electra.ui;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.FragmentManager;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.NavController;
import androidx.navigation.NavDestination;
import androidx.navigation.fragment.NavHostFragment;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.redur.electra.R;
import com.redur.electra.databinding.ActivityMainBinding;
import com.redur.electra.ui.label.PrintLabelBottomSheet;
import com.redur.electra.ui.logout.LogoutBottomSheet;
import com.redur.electra.ui.menu.DrawerMenuAdapter;
import com.redur.electra.ui.menu.DrawerMenuRow;
import com.redur.electra.ui.menu.MenuAction;
import com.redur.electra.ui.menu.MenuActionRegistry;
import com.redur.electra.ui.menu.MenuArgs;
import com.redur.electra.ui.photo.PhotoSourceBottomSheet;
import com.redur.electra.ui.place.PlaceBottomSheet;
import com.redur.electra.ui.profile.ProfileActivity;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    private MainViewModel viewModel;
    private NavController navController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // La toolbar es oscura en ambos temas: iconos de la status bar siempre claros
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        viewModel = new ViewModelProvider(this).get(MainViewModel.class);
        NavHostFragment navHost = (NavHostFragment) getSupportFragmentManager().findFragmentById(R.id.nav_host);
        navController = navHost.getNavController();

        applyWindowInsets();
        setupToolbar();
        setupDrawer();
        setupBackNavigation();
        listenPlazaChanges(navHost.getChildFragmentManager());
        listenPhotoResults(navHost.getChildFragmentManager());
        listenLabelPrinted(navHost.getChildFragmentManager());
    }

    /**
     * La toolbar y la cabecera del menú se extienden bajo la status bar; sus contenidos y el
     * cierre de sesión del pie quedan fuera de los recortes y de la barra de navegación.
     */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.appBar.getRoot().setPadding(bars.left, bars.top, bars.right, 0);
            binding.content.setPadding(0, 0, 0, bars.bottom);
            binding.drawerHeader.getRoot().setPadding(bars.left, bars.top, 0, 0);
            binding.drawerPanel.setPadding(0, 0, 0, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /** Título según el destino y botón de menú en las pantallas del menú ("atrás" en el resto). */
    private void setupToolbar() {
        MaterialToolbar toolbar = binding.appBar.toolbar;
        toolbar.inflateMenu(R.menu.menu_main);
        toolbar.setOnMenuItemClickListener(this::onToolbarItemClicked);
        AppBarConfiguration appBarConfiguration =
                new AppBarConfiguration.Builder(MenuActionRegistry.getTopLevelDestinations())
                        .setOpenableLayout(binding.drawerLayout)
                        .build();
        NavigationUI.setupWithNavController(toolbar, navController, appBarConfiguration);
    }

    private boolean onToolbarItemClicked(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_profile) {
            startActivity(new Intent(this, ProfileActivity.class));
            return true;
        }
        if (id == R.id.action_settings) {
            // Pantalla aún no implementada: se avisa en lugar de ignorar el toque
            showUnavailable();
            return true;
        }
        return false;
    }

    private void setupDrawer() {
        binding.drawerHeader.textDrawerAvatar.setText(viewModel.getUserInitial());
        binding.drawerHeader.textDrawerDisplayName.setText(viewModel.getUserDisplayName());
        String username = viewModel.getUsername();
        // Si no hay nombre completo, el usuario ya se muestra como nombre: no se repite
        boolean showUsername = username != null && !username.equals(viewModel.getUserDisplayName());
        binding.drawerHeader.textDrawerUsername.setText(username);
        binding.drawerHeader.textDrawerUsername.setVisibility(showUsername ? View.VISIBLE : View.GONE);

        DrawerMenuAdapter adapter = new DrawerMenuAdapter(this::onMenuRowClicked);
        binding.recyclerDrawerMenu.setAdapter(adapter);
        viewModel.getMenuRows().observe(this, adapter::submitList);
        navController.addOnDestinationChangedListener((controller, destination, args) -> markActiveMenu(destination));

        binding.buttonDrawerLogout.setOnClickListener(v -> {
            binding.drawerLayout.closeDrawer(binding.drawerPanel);
            LogoutBottomSheet.showIfNotShown(getSupportFragmentManager());
        });
    }

    /** Un menú con submenús se despliega en el sitio; el resto navega y cierra el menú. */
    private void onMenuRowClicked(@NonNull DrawerMenuRow row) {
        if (row.group()) {
            viewModel.onGroupToggled(row.menuId());
        } else {
            onMenuSelected(row.menuId());
        }
    }

    /** Abre el destino del menú pasándole sus permisos, leídos de la sesión en este momento. */
    private void onMenuSelected(int menuId) {
        binding.drawerLayout.closeDrawer(binding.drawerPanel);
        MenuAction action = MenuActionRegistry.getAction(menuId);
        MenuArgs args = viewModel.getMenuArgs(menuId);
        if (action == null || args == null) {
            showUnavailable();
            return;
        }
        action.navigate(navController, args.toBundle());
    }

    /** Las hojas y diálogos no cambian la opción activa: la pantalla de debajo sigue visible. */
    private void markActiveMenu(@NonNull NavDestination destination) {
        Integer menuId = MenuActionRegistry.findMenuId(destination.getId());
        if (menuId != null) {
            viewModel.onMenuShown(menuId);
        }
    }

    private void showUnavailable() {
        Snackbar.make(binding.getRoot(), R.string.feature_unavailable, Snackbar.LENGTH_SHORT).show();
    }

    /**
     * "Atrás" cierra primero el menú y después vuelve a inicio; en inicio no cierra la app, pide
     * confirmar el cierre de sesión.
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(binding.drawerPanel)) {
                    binding.drawerLayout.closeDrawer(binding.drawerPanel);
                } else if (navController.getPreviousBackStackEntry() != null) {
                    navController.popBackStack();
                } else {
                    LogoutBottomSheet.showIfNotShown(getSupportFragmentManager());
                }
            }
        });
    }

    /**
     * Los destinos del menú (p. ej. el cambio de plaza) viven en el NavHostFragment: sus resultados
     * llegan a su FragmentManager hijo, no al de la Activity.
     */
    private void listenPlazaChanges(@NonNull FragmentManager navHostFragmentManager) {
        navHostFragmentManager.setFragmentResultListener(PlaceBottomSheet.RESULT_KEY, this, (key, result) -> {
            String plazaId = result.getString(PlaceBottomSheet.RESULT_PLAZA_ID);
            if (plazaId != null) {
                Snackbar.make(binding.getRoot(), getString(R.string.change_plaza_success, plazaId),
                        Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    /** Imagen hecha o elegida y enviada desde "Toma de foto" (destino del menú, como el cambio de plaza). */
    private void listenPhotoResults(@NonNull FragmentManager navHostFragmentManager) {
        navHostFragmentManager.setFragmentResultListener(PhotoSourceBottomSheet.RESULT_KEY, this, (key, result) -> {
            if (result.getString(PhotoSourceBottomSheet.RESULT_IMAGE_URI) != null) {
                Snackbar.make(binding.getRoot(), R.string.photo_uploaded, Snackbar.LENGTH_SHORT).show();
            }
        });
    }

    /** Etiqueta enviada a una impresora Bluetooth desde la hoja de impresión. */
    private void listenLabelPrinted(@NonNull FragmentManager navHostFragmentManager) {
        navHostFragmentManager.setFragmentResultListener(PrintLabelBottomSheet.RESULT_KEY, this, (key, result) -> {
            String printerName = result.getString(PrintLabelBottomSheet.RESULT_PRINTER_NAME);
            if (printerName != null) {
                Snackbar.make(binding.getRoot(), getString(R.string.print_done, printerName),
                        Snackbar.LENGTH_SHORT).show();
            }
        });
    }
}
