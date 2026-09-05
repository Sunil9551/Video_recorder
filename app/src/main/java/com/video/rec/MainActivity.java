package com.video.rec;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CaptureRequest;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.camera2.interop.Camera2Interop;
import androidx.camera.core.AspectRatio;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity {

    private PreviewView viewFinder;
    private TextView tvTimer;
    private View redBlinkingDot;
    private ImageButton btnRecord, btnStop, btnSwitchCam, btnFlash, btnSettings;
    private Button btnZoomIn, btnZoomOut;

    private Camera camera;
    private ProcessCameraProvider cameraProvider;
    private CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
    private SettingsManager settingsManager;

    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean isFlashOn = false;

    // Timer logic
    private int secondsElapsed = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            secondsElapsed++;
            int mins = secondsElapsed / 60;
            int secs = secondsElapsed % 60;
            tvTimer.setText(String.format(Locale.US, "%02d:%02d", mins, secs));
            timerHandler.postDelayed(this, 1000);
        }
    };

    // Smooth Zoom Handler
    private Handler zoomHandler = new Handler(Looper.getMainLooper());
    private float zoomRatio = 1.0f;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Keep Screen Awake during camera usage
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        settingsManager = new SettingsManager(this);
        initViews();

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 101);
        }
    }

    private void initViews() {
        viewFinder = findViewById(R.id.viewFinder);
        tvTimer = findViewById(R.id.tvTimer);
        redBlinkingDot = findViewById(R.id.redBlinkingDot);
        btnRecord = findViewById(R.id.btnRecord);
        btnStop = findViewById(R.id.btnStop);
        btnSwitchCam = findViewById(R.id.btnSwitchCam);
        btnFlash = findViewById(R.id.btnFlash);
        btnSettings = findViewById(R.id.btnSettings);
        btnZoomIn = findViewById(R.id.btnZoomIn);
        btnZoomOut = findViewById(R.id.btnZoomOut);

        btnRecord.setOnClickListener(v -> toggleRecording());
        btnStop.setOnClickListener(v -> stopRecording());
        btnSwitchCam.setOnClickListener(v -> switchCamera());
        btnFlash.setOnClickListener(v -> toggleFlash());
        btnSettings.setOnClickListener(v -> startActivity(new Intent(MainActivity.this, SettingsActivity.class)));

        setupZoomControls();
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();

                Preview.Builder previewBuilder = new Preview.Builder()
                        .setTargetAspectRatio(AspectRatio.RATIO_4_3);

                // Anti-flickering setup via Camera2Interop
                Camera2Interop.Extender<Preview> extender = new Camera2Interop.Extender<>(previewBuilder);
                int flickerMode = settingsManager.getAntiFlicker();
                if (flickerMode == 1) {
                    extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ);
                } else if (flickerMode == 2) {
                    extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ);
                } else {
                    extender.setCaptureRequestOption(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_AUTO);
                }

                Preview preview = previewBuilder.build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview);

            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void toggleRecording() {
        if (!isRecording) {
            startRecordingState();
        } else if (!isPaused) {
            pauseRecordingState();
        } else {
            resumeRecordingState();
        }
    }

    private void startRecordingState() {
        isRecording = true;
        isPaused = false;
        btnStop.setVisibility(View.VISIBLE);
        btnRecord.setBackgroundResource(R.drawable.circle_red_paused);

        startBlinkingDot();
        secondsElapsed = 0;
        timerHandler.post(timerRunnable);

        File outputFile = getNextSequentialFile();
        Toast.makeText(this, "Recording to: " + outputFile.getName(), Toast.LENGTH_SHORT).show();
    }

    private void pauseRecordingState() {
        isPaused = true;
        stopBlinkingDot();
        timerHandler.removeCallbacks(timerRunnable);
        Toast.makeText(this, "Recording Paused", Toast.LENGTH_SHORT).show();
    }

    private void resumeRecordingState() {
        isPaused = false;
        startBlinkingDot();
        timerHandler.post(timerRunnable);
        Toast.makeText(this, "Recording Resumed", Toast.LENGTH_SHORT).show();
    }

    private void stopRecording() {
        isRecording = false;
        isPaused = false;
        btnStop.setVisibility(View.GONE);
        btnRecord.setBackgroundResource(R.drawable.circle_red);

        stopBlinkingDot();
        timerHandler.removeCallbacks(timerRunnable);
        tvTimer.setText("00:00");

        Toast.makeText(this, "Video Saved in DCIM/Video", Toast.LENGTH_LONG).show();
    }

    private File getNextSequentialFile() {
        File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File videoDir = new File(dcimDir, "Video");
        if (!videoDir.exists()) {
            videoDir.mkdirs();
        }

        int count = 0;
        File file;
        do {
            String fileName = String.format(Locale.US, "Video_%04d.mp4", count);
            file = new File(videoDir, fileName);
            count++;
        } while (file.exists());

        return file;
    }

    private void startBlinkingDot() {
        redBlinkingDot.setVisibility(View.VISIBLE);
        AlphaAnimation blink = new AlphaAnimation(1.0f, 0.0f);
        blink.setDuration(250); // 2 blinks per second
        blink.setRepeatCount(Animation.INFINITE);
        blink.setRepeatMode(Animation.REVERSE);
        redBlinkingDot.startAnimation(blink);
    }

    private void stopBlinkingDot() {
        redBlinkingDot.clearAnimation();
        redBlinkingDot.setVisibility(View.INVISIBLE);
    }

    private void setupZoomControls() {
        Runnable zoomIn = new Runnable() {
            @Override
            public void run() {
                if (camera != null && zoomRatio < 4.0f) {
                    zoomRatio += 0.05f;
                    camera.getCameraControl().setZoomRatio(zoomRatio);
                    zoomHandler.postDelayed(this, 50);
                }
            }
        };

        Runnable zoomOut = new Runnable() {
            @Override
            public void run() {
                if (camera != null && zoomRatio > 1.0f) {
                    zoomRatio -= 0.05f;
                    camera.getCameraControl().setZoomRatio(zoomRatio);
                    zoomHandler.postDelayed(this, 50);
                }
            }
        };

        btnZoomIn.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) zoomHandler.post(zoomIn);
            else if (event.getAction() == MotionEvent.ACTION_UP) zoomHandler.removeCallbacks(zoomIn);
            return true;
        });

        btnZoomOut.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) zoomHandler.post(zoomOut);
            else if (event.getAction() == MotionEvent.ACTION_UP) zoomHandler.removeCallbacks(zoomOut);
            return true;
        });
    }

    private void switchCamera() {
        if (cameraSelector == CameraSelector.DEFAULT_BACK_CAMERA) {
            cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
        } else {
            cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
        }
        startCamera();
    }

    private void toggleFlash() {
        if (camera != null && camera.getCameraInfo().hasFlashUnit()) {
            isFlashOn = !isFlashOn;
            camera.getCameraControl().enableTorch(isFlashOn);
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
              }
