package com.redur.electra.ui.profile;

import android.content.ActivityNotFoundException;
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
import com.google.android.material.color.MaterialColors;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;
import com.redur.electra.BuildConfig;
import com.redur.electra.R;
import com.redur.electra.data.model.user.User;
import com.redur.electra.databinding.ActivityProfileBinding;
import com.redur.electra.ui.MainActivity;
import com.redur.electra.ui.login.LoginActivity;
import com.redur.electra.ui.logout.LogoutBottomSheet;
import com.redur.electra.ui.place.PlaceBottomSheet;

import java.io.File;
import java.text.NumberFormat;

import dagger.hilt.android.AndroidEntryPoint;
import timber.log.Timber;

@AndroidEntryPoint
public class ProfileActivity extends AppCompatActivity {

    private static final String LOG_MIME_TYPE = "text/plain";
    private static final String FILE_PROVIDER_SUFFIX = ".fileprovider";
    private static final String MAILTO_SCHEME = "mailto:";
    private static final int EMAIL_OFFER_DURATION_MS = 8000;

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

        // Ajustamos el toolabar
        applyWindowInsets();
        // Implementamos el toolbar
        setupToolbar();
        // Implementamos el comportamiento del back
        setupBackNavigation();
        // Mostramos la informacion del usuario,la version y plaza
        renderUser(user);
        setupActions();
        viewModel.getLogSendState().observe(this, this::renderLogSend);
    }

    /**
     * La toolbar y la cabecera del menú se extienden bajo la status bar;
     */
    private void applyWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.getRoot(), (root, windowInsets) -> {
            Insets bars = windowInsets.getInsets(
                    WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            binding.appBar.getRoot().setPadding(bars.left, bars.top, bars.right, 0);
            root.setPadding(bars.left, 0, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }

    /**
     * Título, btn navegar atras
     */
    private void setupToolbar() {
        MaterialToolbar toolbar = binding.appBar.toolbar;
        toolbar.setTitle(R.string.profile_title);
        toolbar.setNavigationIcon(R.drawable.ic_arrow_back_24);
        toolbar.setNavigationContentDescription(R.string.profile_navigate_up);
        toolbar.setNavigationOnClickListener(v -> navigateToMain());
    }

    /**
     * Indicamos el comportamiento de atras y btn hacia atras
     */
    private void setupBackNavigation() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                navigateToMain();
            }
        });
    }

    private void renderUser(@NonNull User user) {
        binding.textAvatar.setText(user.initial());
        binding.textDisplayName.setText(user.displayName());
        binding.textUsername.setText(user.username());
        // Si no hay nombre completo, el usuario ya se muestra como nombre: no se repite
        binding.textUsername.setVisibility(user.username().equals(user.displayName()) ? View.GONE : View.VISIBLE);

        String plazaId = user.plazaId();
        boolean hasPlaza = plazaId != null && !plazaId.isBlank();
        binding.textPlaza.setText(hasPlaza ? plazaId : getString(R.string.profile_plaza_empty));

        // Sin plaza el valor baja un escalón: el texto ya lo dice, el color solo lo refuerza
        binding.textPlaza.setTextColor(MaterialColors.getColor(binding.textPlaza,
                hasPlaza ? com.google.android.material.R.attr.colorOnSurface
                        : com.google.android.material.R.attr.colorOnSurfaceVariant));

        binding.textVersion.setText(getString(R.string.login_version, BuildConfig.VERSION_NAME));
        // Visualizamos o no el btn para la plaza
        binding.buttonChangePlaza.setVisibility(viewModel.canChangePlaza() ? View.VISIBLE : View.GONE);
    }

    /**
     * Creamos eventos para cerrar sesion, enviar log y cambiar plaza
     */
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

    private void renderLogSend(@NonNull LogSendState state) {
        renderLogSendRow(state);

        if (state instanceof LogSendState.Sent) {
            viewModel.onLogSendHandled();
            showMessage(R.string.profile_log_sent);
        } else if (state instanceof LogSendState.Failed failed) {
            viewModel.onLogSendHandled();
            showLogSendFailure(failed);
        } else if (state instanceof LogSendState.ReadyToEmail ready) {
            viewModel.onLogSendHandled();
            emailLog(ready.file());
        }
    }

    /**
     * Durante el envío la fila no se deshabilita (el atenuado haría ilegible el porcentaje): solo deja
     * de responder a pulsaciones. Al 100 % la barra pasa a indeterminada mientras responde el servidor.
     */
    private void renderLogSendRow(@NonNull LogSendState state) {
        LinearProgressIndicator progress = binding.progressSendLog;
        if (!(state instanceof LogSendState.Sending sending)) {
            binding.buttonSendLog.setClickable(true);
            binding.buttonSendLog.setText(R.string.profile_send_log);
            progress.hide();
            return;
        }

        binding.buttonSendLog.setClickable(false);
        boolean confirming = sending.percent() >= LogSendState.Sending.COMPLETE;
        binding.buttonSendLog.setText(confirming
                ? getString(R.string.profile_log_confirming)
                : getString(R.string.profile_log_sending,
                        NumberFormat.getPercentInstance().format(sending.percent() / 100.0)));

        if (confirming) {
            progress.setIndeterminate(true);
        } else {
            // Solo se anima el avance de una barra que ya mostraba progreso: al aparecer, al
            // recrearse la pantalla o al reintentar, salta directamente al valor
            boolean animate = progress.getVisibility() == View.VISIBLE && !progress.isIndeterminate();
            progress.setIndeterminate(false);
            progress.setProgressCompat(sending.percent(), animate);
        }
        progress.show();
    }

    /**
     * Mensaje que se muestra cuando se produce un error al enviar el fichero.
     * Dando opcion a reintentar o reenviar por correo
     */
    private void showLogSendFailure(@NonNull LogSendState.Failed failed) {
        Snackbar snackbar = Snackbar.make(binding.getRoot(), failed.message().resolve(this), Snackbar.LENGTH_LONG);
        switch (failed.recovery()) {
            case RETRY -> snackbar.setAction(R.string.profile_log_retry, v -> viewModel.onSendLogClicked());
            case SEND_BY_EMAIL -> snackbar
                    .setAction(R.string.profile_log_send_by_email, v -> viewModel.onSendByEmailClicked())
                    .setDuration(EMAIL_OFFER_DURATION_MS);
            case NONE -> { }
        }
        snackbar.show();
    }

    /**
     * Abre la app de correo con el log adjunto. Si no hay ninguna instalada, recurre al selector
     * general para que el usuario elija otra vía.
     */
    private void emailLog(@NonNull File file) {
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
                .putExtra(Intent.EXTRA_TEXT, getString(R.string.profile_log_email_body))
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        // ClipData permite que el permiso de lectura llegue también a través del selector
        send.setClipData(ClipData.newRawUri(file.getName(), uri));

        // Restringe los destinos a apps de correo sin perder el adjunto de ACTION_SEND
        Intent email = new Intent(send);
        email.setSelector(new Intent(Intent.ACTION_SENDTO, Uri.parse(MAILTO_SCHEME)));
        try {
            startActivity(email);
        } catch (ActivityNotFoundException e) {
            Timber.w("Sin app de correo: se ofrece el selector general para el log");
            startActivity(Intent.createChooser(send, getString(R.string.profile_log_chooser)));
        }
    }

    /** Reutiliza la instancia existente de la pantalla principal en lugar de apilar otra. */
    private void navigateToMain() {
        Intent intent = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }

    /** Se vacía la pila: sin sesión no puede quedar ninguna pantalla autenticada detrás. */
    private void navigateToLogin() {
        Intent intent = new Intent(this, LoginActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void showMessage(@StringRes int messageRes) {
        Snackbar.make(binding.getRoot(), messageRes, Snackbar.LENGTH_SHORT).show();
    }
}
