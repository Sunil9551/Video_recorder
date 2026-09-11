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

public class SettingsActivity extends AppCompatActivity {

    private Spinner spinnerResolution, spinnerFps, spinnerCodec, spinnerFlicker;
    private SettingsManager settingsManager;

    private final String[] resolutions = {
            "320×240 (QVGA)",
            "640×480 (VGA)",
            "960×720 (HD)",
            "1440×1080 (FHD)",
            "1600×1200 (UXGA)",
            "2880×2160 (UHD)"
    };
    private final int[] widths = {320, 640, 960, 1440, 1600, 2880};

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
        loadCurrentSettings();

        btnSave.setOnClickListener(v -> {
            saveSettings();
            finish();
        });
    }

    private void setupSpinners() {
        ArrayAdapter<String> resAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_spinner_item, resolutions) {
            @Override
            public View getDropDownView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
                View view = super.getDropDownView(position, convertView, parent);
                TextView tv = (TextView) view;
                tv.setTextColor(Color.WHITE);
                return view;
            }
        };
        resAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerResolution.setAdapter(resAdapter);

        setupStandardSpinner(spinnerFps, fpsList);
        setupStandardSpinner(spinnerCodec, codecList);
        setupStandardSpinner(spinnerFlicker, flickerList);
    }

    private void setupStandardSpinner(Spinner spinner, String[] data) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, data);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private void loadCurrentSettings() {
        // Load saved Resolution
        int savedWidth = settingsManager.getResolutionWidth();
        int resPos = 3; // Default index 3 (1440x1080)
        for (int i = 0; i < widths.length; i++) {
            if (widths[i] == savedWidth) {
                resPos = i;
                break;
            }
        }
        spinnerResolution.setSelection(resPos);

        // Load saved FPS
        int savedFps = settingsManager.getFps();
        int fpsPos = 5; // Default index 5 (30 FPS)
        for (int i = 0; i < fpsList.length; i++) {
            if (Integer.parseInt(fpsList[i]) == savedFps) {
                fpsPos = i;
                break;
            }
        }
        spinnerFps.setSelection(fpsPos);

        // Load saved Codec
        String savedCodec = settingsManager.getCodec();
        int codecPos = 0;
        for (int i = 0; i < codecList.length; i++) {
            if (codecList[i].equals(savedCodec)) {
                codecPos = i;
                break;
            }
        }
        spinnerCodec.setSelection(codecPos);

        // Load Anti-flicker
        spinnerFlicker.setSelection(settingsManager.getAntiFlicker());
    }

    private void saveSettings() {
        int selectedResIndex = spinnerResolution.getSelectedItemPosition();
        settingsManager.setResolutionWidth(widths[selectedResIndex]);

        int selectedFps = Integer.parseInt(fpsList[spinnerFps.getSelectedItemPosition()]);
        settingsManager.setFps(selectedFps);

        settingsManager.setCodec(codecList[spinnerCodec.getSelectedItemPosition()]);
        settingsManager.setAntiFlicker(spinnerFlicker.getSelectedItemPosition());
    }
}
