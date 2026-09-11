package com.video.rec;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaActionSound;
import android.media.MediaRecorder;
import android.media.MediaScannerConnection;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Range;
import android.util.Size;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private TextureView viewFinder;
    private TextView tvTimer;
    private View redBlinkingDot;
    private ImageView imgPauseIndicator;
    private ImageButton btnRecord, btnStop, btnSwitchCam, btnFlash, btnSettings;
    private Button btnZoomIn, btnZoomOut;

    // Camera2 variables
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder captureRequestBuilder;
    private String cameraId;
    private CameraCharacteristics cameraCharacteristics;
    private Size videoSize;
    private MediaRecorder mediaRecorder;
    private MediaActionSound mediaActionSound;

    private HandlerThread backgroundThread;
    private Handler backgroundHandler;
    private SettingsManager settingsManager;
    private OrientationEventListener orientationEventListener;

    private boolean isRecording = false;
    private boolean isPaused = false;
    private boolean isFlashOn = false;
    private int deviceOrientationDegrees = 0;
    private int sensorOrientation = 0;
    private String currentVideoPath;

    private int secondsElapsed = 0;
    private Handler timerHandler = new Handler(Looper.getMainLooper());
    private Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            secondsElapsed++;
            int hrs = secondsElapsed / 3600;
            int mins = (secondsElapsed % 3600) / 60;
            int secs = secondsElapsed % 60;
            tvTimer.setText(String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs));
            timerHandler.postDelayed(this, 1000);
        }
    };

    private Handler zoomHandler = new Handler(Looper.getMainLooper());
    private float zoomRatio = 1.0f;

    private final TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surface, int width, int height) {
            openCameraSafely();
        }

        @Override public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surface, int width, int height) {}
        @Override public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) { return true; }
        @Override public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {}
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.activity_main);

        settingsManager = new SettingsManager(this);
        mediaActionSound = new MediaActionSound();
        mediaActionSound.load(MediaActionSound.START_VIDEO_RECORDING);
        mediaActionSound.load(MediaActionSound.STOP_VIDEO_RECORDING);

        initViews();
        setupOrientationListener();

        if (!allPermissionsGranted()) {
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            }, 101);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (allPermissionsGranted()) {
            startBackgroundThread();
            if (orientationEventListener != null && orientationEventListener.canDetectOrientation()) {
                orientationEventListener.enable();
            }
            if (viewFinder.isAvailable()) {
                openCameraSafely();
            } else {
                viewFinder.setSurfaceTextureListener(surfaceTextureListener);
            }
        }
    }

    @Override
    protected void onPause() {
        closeCamera();
        stopBackgroundThread();
        if (orientationEventListener != null) {
            orientationEventListener.disable();
        }
        super.onPause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 101 && allPermissionsGranted()) {
            startBackgroundThread();
            if (orientationEventListener != null && orientationEventListener.canDetectOrientation()) {
                orientationEventListener.enable();
            }
            if (viewFinder.isAvailable()) {
                openCameraSafely();
            } else {
                viewFinder.setSurfaceTextureListener(surfaceTextureListener);
            }
        }
    }

    private void initViews() {
        viewFinder = findViewById(R.id.viewFinder);
        tvTimer = findViewById(R.id.tvTimer);
        redBlinkingDot = findViewById(R.id.redBlinkingDot);
        imgPauseIndicator = findViewById(R.id.imgPauseIndicator);
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

    private void openCameraSafely() {
        if (!allPermissionsGranted()) return;

        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (cameraId == null) {
                cameraId = getBackCameraId(manager);
            }
            if (cameraId == null) return;

            cameraCharacteristics = manager.getCameraCharacteristics(cameraId);
            Integer orientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = orientation != null ? orientation : 90;

            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            if (map != null) {
                Size[] choices = map.getOutputSizes(MediaRecorder.class);
                if (choices != null && choices.length > 0) {
                    int reqWidth = settingsManager.getResolutionWidth();
                    videoSize = choose4by3Size(choices, reqWidth);
                } else {
                    videoSize = new Size(640, 480);
                }
            } else {
                videoSize = new Size(640, 480);
            }

            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                manager.openCamera(cameraId, stateCallback, backgroundHandler);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private final CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            cameraDevice.close();
            cameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
        }
    };

    private void startPreview() {
        if (cameraDevice == null || !viewFinder.isAvailable()) return;
        try {
            SurfaceTexture texture = viewFinder.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
            Surface previewSurface = new Surface(texture);

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);

            int fps = settingsManager.getFps();
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(fps, fps));
            
            applyAntiFlicker();

            cameraDevice.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, backgroundHandler);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void updatePreview() {
        if (cameraDevice == null || captureSession == null) return;
        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void toggleRecording() {
        if (!isRecording) {
            startRecording();
        } else if (!isPaused) {
            pauseRecording();
        } else {
            resumeRecording();
        }
    }

    private void startRecording() {
        if (cameraDevice == null || !viewFinder.isAvailable()) return;

        try {
            closeCaptureSession();
            setUpMediaRecorder();

            SurfaceTexture texture = viewFinder.getSurfaceTexture();
            if (texture == null) return;
            texture.setDefaultBufferSize(videoSize.getWidth(), videoSize.getHeight());
            Surface previewSurface = new Surface(texture);
            Surface recorderSurface = mediaRecorder.getSurface();

            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(recorderSurface);

            int fps = settingsManager.getFps();
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(fps, fps));
            
            applyAntiFlicker();

            List<Surface> surfaces = new ArrayList<>();
            surfaces.add(previewSurface);
            surfaces.add(recorderSurface);

            cameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    captureSession = session;
                    updatePreview();
                    runOnUiThread(() -> {
                        try {
                            mediaRecorder.start();
                            mediaActionSound.play(MediaActionSound.START_VIDEO_RECORDING);
                            isRecording = true;
                            isPaused = false;
                            btnStop.setVisibility(View.VISIBLE);
                            imgPauseIndicator.setVisibility(View.GONE);
                            startBlinkingDot();

                            secondsElapsed = 0;
                            tvTimer.setText("00:00:00");
                            timerHandler.post(timerRunnable);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {}
            }, backgroundHandler);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void pauseRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaRecorder != null) {
            mediaRecorder.pause();
            isPaused = true;

            stopBlinkingDot();
            imgPauseIndicator.setVisibility(View.VISIBLE);
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void resumeRecording() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && mediaRecorder != null) {
            mediaRecorder.resume();
            isPaused = false;

            imgPauseIndicator.setVisibility(View.GONE);
            startBlinkingDot();
            timerHandler.post(timerRunnable);
        }
    }

    private void stopRecording() {
        if (isRecording && mediaRecorder != null) {
            try {
                mediaRecorder.stop();
                mediaRecorder.reset();
                mediaActionSound.play(MediaActionSound.STOP_VIDEO_RECORDING);
            } catch (Exception ignored) {}

            MediaScannerConnection.scanFile(this, new String[]{currentVideoPath}, null, null);
            Toast.makeText(this, "Video saved", Toast.LENGTH_SHORT).show();
        }

        isRecording = false;
        isPaused = false;
        btnStop.setVisibility(View.GONE);

        stopBlinkingDot();
        imgPauseIndicator.setVisibility(View.GONE);
        timerHandler.removeCallbacks(timerRunnable);
        tvTimer.setText("00:00:00");

        startPreview();
    }

    private void setUpMediaRecorder() throws IOException {
        mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);

        File file = getOutputFile();
        currentVideoPath = file.getAbsolutePath();
        mediaRecorder.setOutputFile(currentVideoPath);

        mediaRecorder.setVideoEncodingBitRate(settingsManager.getCalculatedBitrate());
        mediaRecorder.setVideoFrameRate(settingsManager.getFps());
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());

        if ("H.265 (HEVC)".equals(settingsManager.getCodec())) {
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC);
        } else {
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        }
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int rotationHint = getOrientationHint();
        mediaRecorder.setOrientationHint(rotationHint);

        mediaRecorder.prepare();
    }

    private int getOrientationHint() {
    if (cameraCharacteristics == null) return 0;

    Integer facing = cameraCharacteristics.get(CameraCharacteristics.LENS_FACING);
    boolean isFrontFacing = (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT);

    if (isFrontFacing) {
        return (sensorOrientation - deviceOrientationDegrees + 360) % 360;
    } else {

        return (sensorOrientation + deviceOrientationDegrees) % 360;
    }
    }

    private Size choose4by3Size(Size[] choices, int targetWidth) {
        Size bestSize = null;
        for (Size size : choices) {
            if (size.getWidth() * 3 == size.getHeight() * 4 || size.getWidth() * 4 == size.getHeight() * 3) {
                if (size.getWidth() == targetWidth) return size;
                if (bestSize == null || Math.abs(size.getWidth() - targetWidth) < Math.abs(bestSize.getWidth() - targetWidth)) {
                    bestSize = size;
                }
            }
        }
        return bestSize != null ? bestSize : new Size(640, 480);
    }

    private File getOutputFile() {
        File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);
        File videoDir = new File(dcimDir, "Video");
        if (!videoDir.exists()) videoDir.mkdirs();

        int count = 0;
        File file;
        do {
            file = new File(videoDir, String.format(Locale.US, "Video_%04d.mp4", count));
            count++;
        } while (file.exists());
        return file;
    }

    private void setupOrientationListener() {
        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (orientation == ORIENTATION_UNKNOWN) return;
                if (orientation >= 315 || orientation < 45) {
                    deviceOrientationDegrees = 0;
                } else if (orientation >= 45 && orientation < 135) {
                    deviceOrientationDegrees = 90;
                } else if (orientation >= 135 && orientation < 225) {
                    deviceOrientationDegrees = 180;
                } else if (orientation >= 225 && orientation < 315) {
                    deviceOrientationDegrees = 270;
                }
            }
        };
    }

    private void applyAntiFlicker() {
        if (captureRequestBuilder == null) return;
        int flickerMode = settingsManager.getAntiFlicker();
        if (flickerMode == 1) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ);
        } else if (flickerMode == 2) {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_60HZ);
        } else {
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_ANTIBANDING_MODE, CaptureRequest.CONTROL_AE_ANTIBANDING_MODE_50HZ);
        }
    }

    private String getBackCameraId(CameraManager manager) throws CameraAccessException {
        for (String id : manager.getCameraIdList()) {
            CameraCharacteristics c = manager.getCameraCharacteristics(id);
            Integer facing = c.get(CameraCharacteristics.LENS_FACING);
            if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) return id;
        }
        String[] list = manager.getCameraIdList();
        return list.length > 0 ? list[0] : null;
    }

    private void switchCamera() {
        if (isRecording) return;
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : manager.getCameraIdList()) {
                CameraCharacteristics c = manager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (cameraId.equals(id)) continue;
                if (facing != null) {
                    closeCamera();
                    cameraId = id;
                    openCameraSafely();
                    break;
                }
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void toggleFlash() {
        if (cameraDevice == null || isRecording) return;
        try {
            isFlashOn = !isFlashOn;
            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, isFlashOn ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_OFF);
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        closeCaptureSession();
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private void closeCaptureSession() {
        if (captureSession != null) {
            captureSession.close();
            captureSession = null;
        }
    }

    private void startBackgroundThread() {
        backgroundThread = new HandlerThread("CameraBackground");
        backgroundThread.start();
        backgroundHandler = new Handler(backgroundThread.getLooper());
    }

    private void stopBackgroundThread() {
        if (backgroundThread != null) {
            backgroundThread.quitSafely();
            try {
                backgroundThread.join();
                backgroundThread = null;
                backgroundHandler = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void startBlinkingDot() {
        redBlinkingDot.setVisibility(View.VISIBLE);
        AlphaAnimation blink = new AlphaAnimation(1.0f, 0.0f);
        blink.setDuration(250);
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
                if (cameraDevice != null && zoomRatio < 4.0f) {
                    zoomRatio += 0.05f;
                    applyZoom();
                    zoomHandler.postDelayed(this, 50);
                }
            }
        };

        Runnable zoomOut = new Runnable() {
            @Override
            public void run() {
                if (cameraDevice != null && zoomRatio > 1.0f) {
                    zoomRatio -= 0.05f;
                    applyZoom();
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

    private void applyZoom() {
        if (captureRequestBuilder == null || cameraCharacteristics == null) return;
        android.graphics.Rect rect = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
        if (rect == null) return;

        int cropW = (int) (rect.width() / zoomRatio);
        int cropH = (int) (rect.height() / zoomRatio);
        int cropX = (rect.width() - cropW) / 2;
        int cropY = (rect.height() - cropH) / 2;

        android.graphics.Rect zoomRect = new android.graphics.Rect(cropX, cropY, cropX + cropW, cropY + cropH);
        captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, zoomRect);
        try {
            if (captureSession != null) {
                captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }
                            }
