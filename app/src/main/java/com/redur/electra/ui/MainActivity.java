package com.redur.electra.ui;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import com.redur.electra.databinding.ActivityMainBinding;
import com.redur.electra.ui.logout.LogoutBottomSheet;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ActivityMainBinding binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        setupBackNavigation();
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
