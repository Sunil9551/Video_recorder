package com.video.rec;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaRecorder;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class CameraCapabilities {

    private static final int TARGET_FPS = 30;

    // Maximum video width we allow.
    // For 4:3 cameras this can allow up to 2880x2160 if supported.
    private static final int MAX_WIDTH = 2880;

    // Maximum preview width.
    private static final int MAX_PREVIEW_WIDTH = 1440;

    // Maximum zoom.
    private static final float MAX_ZOOM = 4.0f;

    // Bitrate formula:
    // width × height × fps × bits-per-pixel
    private static final float BITS_PER_PIXEL = 0.15f;

    // Maximum bitrate = 24 Mbps.
    private static final int MAX_BITRATE = 24_000_000;

    private static final int MIN_BITRATE = 500_000;

    private static final Size FALLBACK_SIZE = new Size(640, 480);


    // ---------------------------------------------------------
    // Recording configuration
    // ---------------------------------------------------------

    public static class RecordingConfig {

        public final Size videoSize;
        public final Size previewSize;

        @Nullable
        public final Range<Integer> fpsRange;

        public final int frameRate;
        public final int bitrate;

        public RecordingConfig(
                Size videoSize,
                Size previewSize,
                @Nullable Range<Integer> fpsRange,
                int frameRate,
                int bitrate
        ) {
            this.videoSize = videoSize;
            this.previewSize = previewSize;
            this.fpsRange = fpsRange;
            this.frameRate = frameRate;
            this.bitrate = bitrate;
        }
    }


    // ---------------------------------------------------------
    // Camera selection
    // ---------------------------------------------------------

    @Nullable
    public static String chooseCameraId(
            @NonNull CameraManager manager
    ) {

        try {
            String firstUsable = null;

            // Prefer rear camera.
            for (String id : manager.getCameraIdList()) {

                CameraCharacteristics c =
                        manager.getCameraCharacteristics(id);

                Integer facing =
                        c.get(CameraCharacteristics.LENS_FACING);

                if (facing != null
                        && facing == CameraCharacteristics.LENS_FACING_BACK
                        && isUsable(c)) {

                    return id;
                }

                if (firstUsable == null && isUsable(c)) {
                    firstUsable = id;
                }
            }

            // If no rear camera, return any usable camera.
            return firstUsable;

        } catch (Exception ignored) {
            return null;
        }
    }


    // ---------------------------------------------------------
    // Camera usability
    // ---------------------------------------------------------

    public static boolean isUsable(
            @NonNull CameraCharacteristics c
    ) {

        try {

            StreamConfigurationMap map =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                return false;
            }

            int[] capabilities =
                    c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

            if (capabilities == null) {
                return true;
            }

            for (int capability : capabilities) {

                if (capability ==
                        CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_BACKWARD_COMPATIBLE) {

                    return true;
                }
            }

            return false;

        } catch (RuntimeException e) {
            return false;
        }
    }


    // ---------------------------------------------------------
    // FPS
    // ---------------------------------------------------------

    @Nullable
    public static Range<Integer> pickFpsRange(
            @NonNull CameraCharacteristics c
    ) {

        try {

            Range<Integer>[] ranges =
                    c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

            if (ranges == null || ranges.length == 0) {
                return null;
            }

            Range<Integer> best = null;

            // Prefer the highest range whose upper FPS is
            // not greater than our target of 30 FPS.
            for (Range<Integer> r : ranges) {

                if (r == null) {
                    continue;
                }

                if (r.getUpper() > TARGET_FPS) {
                    continue;
                }

                if (best == null
                        || r.getUpper() > best.getUpper()
                        || (
                        r.getUpper().intValue() == best.getUpper().intValue()
                                && r.getLower() > best.getLower()
                )) {

                    best = r;
                }
            }

            // Some phones may only report ranges above 30 FPS.
            // In that case choose the lowest available upper FPS
            // instead of failing completely.
            if (best == null) {

                for (Range<Integer> r : ranges) {

                    if (r == null) {
                        continue;
                    }

                    if (best == null
                            || r.getUpper() < best.getUpper()) {

                        best = r;
                    }
                }
            }

            return best;

        } catch (RuntimeException e) {
            return null;
        }
    }


    private static int frameRateFor(
            @Nullable Range<Integer> range
    ) {

        if (range == null) {
            return TARGET_FPS;
        }

        int fps = range.getUpper();

        if (fps <= 0) {
            return TARGET_FPS;
        }

        return fps;
    }


    // ---------------------------------------------------------
    // Supported video sizes
    // ---------------------------------------------------------

    @NonNull
    public static List<Size> getSupportedVideoSizes(
            @NonNull CameraCharacteristics c
    ) {

        List<Size> result = new ArrayList<>();
        List<Size> all = new ArrayList<>();
        List<Size> fourByThree = new ArrayList<>();
        List<Size> other = new ArrayList<>();

        try {

            StreamConfigurationMap map =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                result.add(FALLBACK_SIZE);
                return result;
            }

            Size[] sizes =
                    map.getOutputSizes(MediaRecorder.class);

            if (sizes == null || sizes.length == 0) {
                result.add(FALLBACK_SIZE);
                return result;
            }

            Range<Integer> fpsRange = pickFpsRange(c);
            int fps = frameRateFor(fpsRange);

            long frameDurationNs =
                    1_000_000_000L / Math.max(1, fps);

            for (Size s : sizes) {

                if (s == null) {
                    continue;
                }

                int width = s.getWidth();
                int height = s.getHeight();

                if (width <= 0 || height <= 0) {
                    continue;
                }

                all.add(s);

                // Ignore portrait-oriented encoder sizes.
                if (width < height) {
                    continue;
                }

                // Never offer a video width above our maximum.
                if (width > MAX_WIDTH) {
                    continue;
                }

                // Make sure the camera can actually deliver
                // this size at the selected frame rate.
                if (!canCameraDeliver(
                        map,
                        s,
                        frameDurationNs
                )) {
                    continue;
                }

                // Make sure an AVC/H.264 encoder can handle
                // this resolution and FPS.
                if (!isEncodable(s, fps)) {
                    continue;
                }

                if (isFourByThree(s)) {
                    fourByThree.add(s);
                } else {
                    other.add(s);
                }
            }

            // Prefer 4:3 sizes when available.
            if (!fourByThree.isEmpty()) {
                result.addAll(fourByThree);
            } else {
                result.addAll(other);
            }

            sortByArea(result);

            // If the strict filtered list is empty,
            // try the smallest encoder-supported size.
            if (result.isEmpty()) {

                Size smallest = null;

                for (Size s : all) {

                    if (s.getWidth() < s.getHeight()) {
                        continue;
                    }

                    if (smallest == null
                            || area(s) < area(smallest)) {

                        smallest = s;
                    }
                }

                if (smallest != null) {
                    result.add(smallest);
                }
            }

            // Final safety fallback.
            if (result.isEmpty()) {
                result.add(FALLBACK_SIZE);
            }

        } catch (RuntimeException ignored) {

            result.clear();
            result.add(FALLBACK_SIZE);
        }

        return result;
    }


    private static void sortByArea(
            @NonNull List<Size> sizes
    ) {

        Collections.sort(
                sizes,
                new Comparator<Size>() {
                    @Override
                    public int compare(Size a, Size b) {
                        return Long.compare(
                                area(a),
                                area(b)
                        );
                    }
                }
        );
    }


    private static long area(
            @NonNull Size s
    ) {

        return (long) s.getWidth() * s.getHeight();
    }


    // ---------------------------------------------------------
    // Choose video size
    // ---------------------------------------------------------

    @Nullable
    public static Size chooseVideoSize(
            @NonNull List<Size> supported,
            int preferredWidth,
            int maxWidth
    ) {

        if (supported.isEmpty()) {
            return null;
        }

        Size best = null;
        int bestDistance = Integer.MAX_VALUE;

        int effectiveMaxWidth =
                Math.min(maxWidth, MAX_WIDTH);

        for (Size s : supported) {

            if (s == null) {
                continue;
            }

            int width = s.getWidth();

            if (width > effectiveMaxWidth) {
                continue;
            }

            int distance =
                    Math.abs(width - preferredWidth);

            if (best == null
                    || distance < bestDistance
                    || (
                    distance == bestDistance
                            && area(s) > area(best)
            )) {

                best = s;
                bestDistance = distance;
            }
        }

        // If no size fits the requested maximum,
        // use the smallest supported size.
        if (best == null) {

            for (Size s : supported) {

                if (s == null) {
                    continue;
                }

                if (best == null
                        || area(s) < area(best)) {

                    best = s;
                }
            }
        }

        return best;
    }


    // ---------------------------------------------------------
    // Preview size
    // ---------------------------------------------------------

    @NonNull
    public static Size choosePreviewSize(
            @NonNull CameraCharacteristics c,
            @NonNull Size videoSize
    ) {

        try {

            StreamConfigurationMap map =
                    c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map == null) {
                return FALLBACK_SIZE;
            }

            Size[] sizes =
                    map.getOutputSizes(android.graphics.SurfaceTexture.class);

            if (sizes == null || sizes.length == 0) {
                return FALLBACK_SIZE;
            }

            float targetRatio =
                    (float) videoSize.getWidth()
                            / (float) videoSize.getHeight();

            Size best = null;
            long bestArea = -1;

            for (Size s : sizes) {

                if (s == null) {
                    continue;
                }

                int width = s.getWidth();
                int height = s.getHeight();

                if (width <= 0 || height <= 0) {
                    continue;
                }

                if (width > MAX_PREVIEW_WIDTH) {
                    continue;
                }

                float ratio =
                        (float) width / (float) height;

                if (Math.abs(ratio - targetRatio) > 0.03f) {
                    continue;
                }

                long currentArea =
                        (long) width * height;

                if (currentArea > bestArea) {
                    best = s;
                    bestArea = currentArea;
                }
            }

            if (best != null) {
                return best;
            }

            // Fallback: use the largest preview that fits the limit.
            Size fallback = null;

            for (Size s : sizes) {

                if (s == null) {
                    continue;
                }

                if (s.getWidth() > MAX_PREVIEW_WIDTH) {
                    continue;
                }

                if (fallback == null
                        || area(s) > area(fallback)) {

                    fallback = s;
                }
            }

            return fallback != null
                    ? fallback
                    : FALLBACK_SIZE;

        } catch (RuntimeException e) {
            return FALLBACK_SIZE;
        }
    }


    // ---------------------------------------------------------
    // Create recording configuration
    // ---------------------------------------------------------

    @Nullable
    public static RecordingConfig createConfig(
            @NonNull CameraCharacteristics c,
            @NonNull List<Size> supportedSizes,
            int preferredWidth,
            int maxWidth
    ) {

        Size video =
                chooseVideoSize(
                        supportedSizes,
                        preferredWidth,
                        maxWidth
                );

        if (video == null) {
            return null;
        }

        Range<Integer> fpsRange =
                pickFpsRange(c);

        int fps =
                frameRateFor(fpsRange);

        int bitrate =
                calculateBitrate(
                        video,
                        fps
                );

        Size preview =
                choosePreviewSize(
                        c,
                        video
                );

        return new RecordingConfig(
                video,
                preview,
                fpsRange,
                fps,
                bitrate
        );
    }


    // ---------------------------------------------------------
    // Camera frame-duration check
    // ---------------------------------------------------------

    private static boolean canCameraDeliver(
            @NonNull StreamConfigurationMap map,
            @NonNull Size size,
            long desiredFrameDurationNs
    ) {

        try {

            long actualFrameDuration =
                    map.getOutputMinFrameDuration(
                            MediaRecorder.class,
                            size
                    );

            if (actualFrameDuration <= 0) {
                return true;
            }

            // Allow a small tolerance for camera implementations.
            return actualFrameDuration
                    <= (long) (desiredFrameDurationNs * 1.05f);

        } catch (RuntimeException e) {
            return true;
        }
    }


    // ---------------------------------------------------------
    // H.264 encoder check
    // ---------------------------------------------------------

    private static boolean isEncodable(
            @NonNull Size size,
            int fps
    ) {

        for (MediaCodecInfo.VideoCapabilities vc :
                getAvcEncoders()) {

            try {

                if (vc.areSizeAndRateSupported(
                        size.getWidth(),
                        size.getHeight(),
                        fps
                )) {
                    return true;
                }

            } catch (RuntimeException ignored) {
            }
        }

        return false;
    }


    @NonNull
    private static List<MediaCodecInfo.VideoCapabilities>
    getAvcEncoders() {

        List<MediaCodecInfo.VideoCapabilities> result =
                new ArrayList<>();

        try {

            MediaCodecList codecList =
                    new MediaCodecList(
                            MediaCodecList.REGULAR_CODECS
                    );

            MediaCodecInfo[] infos =
                    codecList.getCodecInfos();

            for (MediaCodecInfo info : infos) {

                if (info == null || info.isEncoder() == false) {
                    continue;
                }

                String[] types =
                        info.getSupportedTypes();

                if (types == null) {
                    continue;
                }

                for (String type : types) {

                    if (!"video/avc".equalsIgnoreCase(type)) {
                        continue;
                    }

                    try {

                        MediaCodecInfo.CodecCapabilities caps =
                                info.getCapabilitiesForType(type);

                        if (caps != null
                                && caps.getVideoCapabilities() != null) {

                            result.add(
                                    caps.getVideoCapabilities()
                            );
                        }

                    } catch (RuntimeException ignored) {
                    }

                    break;
                }
            }

        } catch (RuntimeException ignored) {
        }

        return result;
    }


    // ---------------------------------------------------------
    // Bitrate
    // ---------------------------------------------------------

    public static int calculateBitrate(
            @NonNull Size size,
            int fps
    ) {

        int safeFps =
                Math.max(1, fps);

        long calculated =
                (long) size.getWidth()
                        * size.getHeight()
                        * safeFps;

        calculated =
                (long) (calculated * BITS_PER_PIXEL);

        // Minimum bitrate.
        calculated =
                Math.max(
                        MIN_BITRATE,
                        calculated
                );

        // IMPORTANT:
        // Maximum bitrate is capped at 24 Mbps.
        //
        // Example:
        // 2880x2160 @ 30 FPS
        // formula ≈ 28 Mbps
        // final bitrate = 24 Mbps
        calculated =
                Math.min(
                        calculated,
                        MAX_BITRATE
                );

        int bitrate;

        if (calculated > Integer.MAX_VALUE) {
            bitrate = MAX_BITRATE;
        } else {
            bitrate = (int) calculated;
        }

        // Finally respect the actual H.264 encoder's
        // supported bitrate range when available.
        for (MediaCodecInfo.VideoCapabilities vc :
                getAvcEncoders()) {

            try {

                if (vc.isSizeSupported(
                        size.getWidth(),
                        size.getHeight()
                )) {

                    Range<Integer> range =
                            vc.getBitrateRange();

                    if (range != null) {

                        bitrate =
                                range.clamp(bitrate);

                        // Never exceed our app-level 24 Mbps limit,
                        // even after encoder clamping.
                        bitrate =
                                Math.min(
                                        bitrate,
                                        MAX_BITRATE
                                );
                    }

                    break;
                }

            } catch (RuntimeException ignored) {
            }
        }

        return Math.max(
                MIN_BITRATE,
                Math.min(
                        bitrate,
                        MAX_BITRATE
                )
        );
    }


    // ---------------------------------------------------------
    // 4:3 check
    // ---------------------------------------------------------

    private static boolean isFourByThree(
            @NonNull Size size
    ) {

        return (long) size.getWidth() * 3L
                == (long) size.getHeight() * 4L;
    }


    // ---------------------------------------------------------
    // Zoom
    // ---------------------------------------------------------

    public static float getMaxZoom(
            @NonNull CameraCharacteristics c
    ) {

        try {

            Float maxZoom =
                    c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);

            if (maxZoom == null
                    || maxZoom <= 1.0f) {

                return 1.0f;
            }

            return Math.min(
                    MAX_ZOOM,
                    maxZoom
            );

        } catch (RuntimeException e) {
            return 1.0f;
        }
    }
    }
