package com.video.rec;

import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends AppCompatActivity {

    private Spinner spinnerResolution, spinnerFps, spinnerCodec, spinnerFlicker;
    private SettingsManager settingsManager;

    private final String[] resolutions = {
            "320×240 (QVGA) - 0.8Mbps",
            "640×480 (VGA) - 2Mbps",
            "960×720 (HD) - 4Mbps",
            "1440×1080 (FHD) - 6Mbps",
            "1600×1200 (UXGA) - 8Mbps",
            "2880×2160 (UHD) - 16Mbps"
    };

    private final String[] fpsList = {"5", "10", "15", "20", "24", "30"};
    private final String[] codecList = {"H.264", "H.265 (HEVC)"};
    private final String[] flickerList = {"Auto", "50 Hz", "60 Hz"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settingsManager = new SettingsManager(this);

        spinnerResolution = findViewById(R.id.spinnerResolution);
        spinnerFps = findViewById(R.id.spinnerFps);
        spinnerCodec = findViewById(R.id.spinnerCodec);
        spinnerFlicker = findViewById(R.id.spinnerFlicker);
        Button btnSave = findViewById(R.id.btnSaveSettings);

        setupSpinners();

        btnSave.setOnClickListener(v -> {
            saveSettings();
            finish();
        });
    }

    private void setupSpinners() {
        // Setup Resolution Spinner with Graying out logic
        ArrayAdapter<String> resAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, resolutions) {
            @Override
            public boolean isEnabled(int position) {
                // Here 1600x1200 / 2880x2160 can be checked against hardware capability
                // For demo UXGA/UHD disabled if device doesn't support
                if (position == 5) return false; // Example: Gray out UHD if unsupported
                return true;
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                if (!isEnabled(position)) {
                    tv.setTextColor(Color.GRAY);
                } else {
                    tv.setTextColor(Color.WHITE);
                }
                return view;
            }
        };
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerResolution.setAdapter(resAdapter);

        // Standard Adapters for FPS, Codec, Flicker
        setupStandardSpinner(spinnerFps, fpsList);
        setupStandardSpinner(spinnerCodec, codecList);
        setupStandardSpinner(spinnerFlicker, flickerList);
    }

    private void setupStandardSpinner(Spinner spinner, String[] data) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, data);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void saveSettings() {
        int selectedResIndex = spinnerResolution.getSelectedItemPosition();
        int[] widths = {320, 640, 960, 1440, 1600, 2880};
        settingsManager.setResolutionWidth(widths[selectedResIndex]);

        int selectedFps = Integer.parseInt(fpsList[spinnerFps.getSelectedItemPosition()]);
        settingsManager.setFps(selectedFps);

        settingsManager.setCodec(codecList[spinnerCodec.getSelectedItemPosition()]);
        settingsManager.setAntiFlicker(spinnerFlicker.getSelectedItemPosition());
    }
                }
