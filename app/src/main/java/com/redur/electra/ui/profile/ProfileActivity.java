package com.redur.electra.ui.profile;

import android.content.ClipData;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.SystemBarStyle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.snackbar.Snackbar;
import com.redur.electra.BuildConfig;
import com.redur.electra.R;
import com.redur.electra.core.ui.UiState;
import com.redur.electra.data.model.user.User;
import com.redur.electra.databinding.ActivityProfileBinding;
import com.redur.electra.ui.MainActivity;
import com.redur.electra.ui.login.LoginActivity;
import com.redur.electra.ui.logout.LogoutBottomSheet;
import com.redur.electra.ui.place.PlaceBottomSheet;

import java.io.File;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

@AndroidEntryPoint
public class ProfileActivity extends AppCompatActivity {

    private static final String LOG_MIME_TYPE = "text/plain";
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";

    private ActivityProfileBinding binding;
    private ProfileViewModel viewModel;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        // La toolbar es oscura en ambos temas: iconos de la status bar siempre claros
        EdgeToEdge.enable(this, SystemBarStyle.dark(Color.TRANSPARENT));
        super.onCreate(savedInstanceState);

        viewModel = new ViewModelProvider(this).get(ProfileViewModel.class);
        User user = viewModel.getUser();
        if (user == null) {
            // La sesión solo vive en memoria: si se perdió, no hay perfil que mostrar
            navigateToLogin();
            return;
        }

        binding = ActivityProfileBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        applyWindowInsets();
        setupToolbar();
        setupBackNavigation();
        renderUser(user);
        setupActions();
        viewModel.getLogShareState().observe(this, this::renderLogShare);
    }

    /** Igual que la pantalla principal: la franja de la toolbar se extiende bajo la status bar. */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.appBar.getRoot().setPadding(bars.left, bars.top, bars.right, 0);
            root.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    private void setupToolbar() {
        MaterialToolbar toolbar = binding.appBar.toolbar;
        toolbar.setTitle(R.string.profile_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationContentDescription(R.string.profile_navigate_up);
        toolbar.setNavigationOnClickListener(v -> navigateToMain());
    }

    /** "Atrás" y la flecha de la toolbar llevan siempre a la pantalla principal. */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToMain();
            }
        });
    }

    private void renderUser(@NonNull User user) {
        binding.textDisplayName.setText(user.displayName());
        binding.textUsername.setText(user.username());
        String plazaId = user.plazaId();
        binding.textPlaza.setText(plazaId != null && !plazaId.isBlank()  ? plazaId : getString(R.string.profile_plaza_empty));
        binding.textVersion.setText(getString(R.string.login_version, BuildConfig.VERSION_NAME));
        binding.buttonChangePlaza.setVisibility(viewModel.canChangePlaza() ? View.VISIBLE : View.GONE);
    }

    private void setupActions() {
        binding.buttonChangePlaza.setOnClickListener(v -> PlaceBottomSheet.showIfNotShown(getSupportFragmentManager()));
        getSupportFragmentManager().setFragmentResultListener(PlaceBottomSheet.RESULT_KEY, this,
                (key, result) -> onPlazaChanged(result.getString(PlaceBottomSheet.RESULT_PLAZA_ID)));
        binding.buttonSendLog.setOnClickListener(v -> viewModel.onSendLogClicked());
        binding.buttonLogout.setOnClickListener(v ->  LogoutBottomSheet.showIfNotShown(getSupportFragmentManager()));
    }

    /** La sesión ya refleja la nueva plaza: se repinta el perfil y se confirma el cambio. */
    private void onPlazaChanged(@Nullable String plazaId) {
        User user = viewModel.getUser();
        if (user == null) {
            navigateToLogin();
            return;
        }
        renderUser(user);
        if (plazaId != null) {
            Snackbar.make(binding.getRoot(), getString(R.string.change_plaza_success, plazaId),
                    Snackbar.LENGTH_SHORT).show();
        }
    }

    private void renderLogShare(UiState state) {
        binding.buttonSendLog.setEnabled(!(state instanceof UiState.Loading));

        if (state instanceof UiState.Success) {
            File file = viewModel.getLogFile();
            viewModel.onLogShareHandled();
            if (file != null) {
                shareLog(file);
            }
        } else if (state instanceof UiState.Error error) {
            viewModel.onLogShareHandled();
            Snackbar.make(binding.getRoot(), error.message().resolve(this), Snackbar.LENGTH_LONG).show();
        }
    }

    /** Abre el selector del sistema para que el usuario elija cómo enviar el fichero. */
    private void shareLog(@NonNull File file) {
        Uri uri;
        try {
            uri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + FILE_PROVIDER_SUFFIX, file);
        } catch (IllegalArgumentException e) {
            // La carpeta del log no está declarada en file_paths.xml
            Timber.e(e, "No se puede compartir el log %s", file.getName());
            showMessage(R.string.profile_log_share_failed);
            return;
        }

        User user = viewModel.getUser();
        String subject = getString(R.string.profile_log_subject, getString(R.string.app_name),
                user != null ? user.username() : "", BuildConfig.VERSION_NAME);
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType(LOG_MIME_TYPE)
                .putExtra(Intent.EXTRA_STREAM, uri)
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // ClipData permite que el permiso de lectura llegue también a través del selector
        send.setClipData(ClipData.newRawUri(file.getName(), uri));
        startActivity(Intent.createChooser(send, getString(R.string.profile_log_chooser)));
    }

    private void showCanEditPlaza(){

    }

    /** Reutiliza la instancia existente de la pantalla principal en lugar de apilar otra. */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** Se vacía la pila: sin sesión no puede quedar ninguna pantalla autenticada detrás. */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showMessage(@StringRes int messageRes) {
        Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_SHORT).show();
    }
}
