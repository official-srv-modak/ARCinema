package com.souravmodak.arcinema;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.hardware.Camera;
import android.widget.Toast;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class CameraVideoOverlayActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private ImageButton captureButton;
    private VideoView videoView;
    private FrameLayout frameLayout;
    private Bitmap capturedBitmap;
    private CameraOverlayView overlayView;

    private FrameLayout loadingScreen;  // Loading screen frame
    private ProgressBar loadingProgressBar;  // Circular progress bar
    private TextView loadingText;  // Loading text

    String ip = "10.0.0.47:8089";


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera_video_overlay);

        surfaceView = findViewById(R.id.surfaceView);
        captureButton = findViewById(R.id.captureButton);
        videoView = new VideoView(this);
        frameLayout = findViewById(R.id.frameLayout);

        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);

        // Add the overlay box
        overlayView = new CameraOverlayView(this);
        frameLayout.addView(overlayView);
        loadingScreen = findViewById(R.id.loadingScreen);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);
        loadingText = findViewById(R.id.loadingText);

        captureButton.setOnClickListener(v -> capturePhoto());

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            initializeCamera();
        }

        // Set up tap-to-focus
        surfaceView.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                focusOnTouch(event.getX(), event.getY());
            }
            return true;
        });
    }

    private void initializeCamera() {
        try {
            camera = Camera.open();
            Camera.Parameters parameters = camera.getParameters();

            // Get the list of supported picture sizes and choose the largest one
            List<Camera.Size> supportedSizes = parameters.getSupportedPictureSizes();
            Camera.Size maxSize = supportedSizes.get(0);
            for (Camera.Size size : supportedSizes) {
                if (size.width * size.height > maxSize.width * maxSize.height) {
                    maxSize = size;
                }
            }

            // Set the picture size to the maximum supported size
            parameters.setPictureSize(maxSize.width, maxSize.height);

            // Set focus mode to auto-focus if supported
            List<String> supportedFocusModes = parameters.getSupportedFocusModes();
            if (supportedFocusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
            }

            camera.setParameters(parameters);
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void focusOnTouch(float x, float y) {
        if (camera != null) {
            Camera.Parameters parameters = camera.getParameters();
            if (parameters.getMaxNumFocusAreas() > 0) {
                List<Camera.Area> focusAreas = new ArrayList<>();
                Rect focusRect = calculateFocusArea(x, y);
                focusAreas.add(new Camera.Area(focusRect, 1000));
                parameters.setFocusAreas(focusAreas);
                parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
                camera.setParameters(parameters);
                camera.autoFocus((success, cam) -> {
                    // Optionally handle focus success/failure
                });
            }
        }
    }

    private Rect calculateFocusArea(float x, float y) {
        int left = clamp((int) (x / surfaceView.getWidth() * 2000 - 1000), -1000, 1000);
        int top = clamp((int) (y / surfaceView.getHeight() * 2000 - 1000), -1000, 1000);
        return new Rect(left, top, left + 200, top + 200);
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private void capturePhoto() {
        loadingScreen.setVisibility(View.VISIBLE);
        loadingText.setVisibility(View.VISIBLE);
        if (camera != null) {
            camera.takePicture(null, null, (data, camera) -> {
                capturedBitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);

                // Rotate the captured bitmap by 90 degrees
                Matrix matrix = new Matrix();
                matrix.postRotate(90);
                capturedBitmap = Bitmap.createBitmap(capturedBitmap, 0, 0, capturedBitmap.getWidth(), capturedBitmap.getHeight(), matrix, true);

                // Get the dimensions of the overlay box
                Rect boxRect = overlayView.getBoxRect();

                // Calculate the scaling factor between the preview size and the captured image size
                float scaleX = (float) capturedBitmap.getWidth() / surfaceView.getWidth();
                float scaleY = (float) capturedBitmap.getHeight() / surfaceView.getHeight();

                // Calculate the actual coordinates for cropping
                int left = (int) (boxRect.left * scaleX);
                int top = (int) (boxRect.top * scaleY);
                int width = (int) (boxRect.width() * scaleX);
                int height = (int) (boxRect.height() * scaleY);

                // Ensure the cropping area is within the bounds of the captured image
                left = Math.max(0, left);
                top = Math.max(0, top);
                width = Math.min(capturedBitmap.getWidth() - left, width);
                height = Math.min(capturedBitmap.getHeight() - top, height);

                // Crop the bitmap to the overlay box
                Bitmap overlayBitmap = Bitmap.createBitmap(capturedBitmap, left, top, width, height);

                sendImageToServer(overlayBitmap);
            });
        }
    }

    private void sendImageToServer(Bitmap bitmap) {
        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(120, TimeUnit.SECONDS) // Set connection timeout
                .writeTimeout(120, TimeUnit.SECONDS)   // Set write timeout
                .readTimeout(120, TimeUnit.SECONDS)    // Set read timeout
                .build();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream);
        byte[] imageBytes = byteArrayOutputStream.toByteArray();

        RequestBody requestBody = new MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("imageFile", "image.jpg", RequestBody.create(MediaType.parse("image/jpeg"), imageBytes))
                .build();

        Request request = new Request.Builder()
                .url("http://" + ip + "/arcinema-image-search/youtube-api/get-url")
                .post(requestBody)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    loadingScreen.setVisibility(View.GONE);
                    loadingText.setVisibility(View.GONE);
                    Toast.makeText(CameraVideoOverlayActivity.this, "Failed to upload image", Toast.LENGTH_SHORT).show();
                    resetUIForRetry();
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body().string();
                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);
                        String videoUrl = jsonObject.getString("url");
                        String movieName = jsonObject.getString("movie_name");
                        runOnUiThread(() -> playVideo(videoUrl, movieName));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                } else {
                    runOnUiThread(() -> {
                        loadingScreen.setVisibility(View.GONE);
                        loadingText.setVisibility(View.GONE);
                        Toast.makeText(CameraVideoOverlayActivity.this, "Couldn't recognize the poster.", Toast.LENGTH_SHORT).show();
                        resetUIForRetry();
                    });
                }
            }
        });
    }

    private void resetUIForRetry() {
        // Restart the camera preview
        if (camera != null) {
            camera.startPreview();
        }
        // Make the capture button visible again
        captureButton.setVisibility(View.VISIBLE);
        // Re-add the overlay view if it was removed
        if (overlayView.getParent() == null) {
            frameLayout.addView(overlayView);
        }
    }

    private void playVideo(String videoUrl, String movieName) {
        loadingScreen.setVisibility(View.GONE);
        loadingText.setVisibility(View.GONE);

        if (capturedBitmap != null) {
            Canvas canvas = surfaceHolder.lockCanvas();
            if (canvas != null) {
                canvas.drawBitmap(capturedBitmap, 0, 0, null);
                surfaceHolder.unlockCanvasAndPost(canvas);
            }

            if (camera != null) {
                camera.stopPreview();
                camera.release();
                camera = null;
            }

            // Remove the overlay box
            if (overlayView != null) {
                frameLayout.removeView(overlayView);
            }

            // Start video
            Uri videoUri = Uri.parse(videoUrl);
            videoView.setVideoURI(videoUri);

            int marginInPx = (int) (getResources().getDisplayMetrics().density * 24);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
            );
            params.gravity = android.view.Gravity.CENTER;
            params.setMargins(marginInPx, 0, marginInPx, 0);
            videoView.setLayoutParams(params);
            frameLayout.addView(videoView);

            // 🔥 Zoom-in animation
            videoView.setScaleX(0.2f);
            videoView.setScaleY(0.2f);
            videoView.setAlpha(0f);
            videoView.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .alpha(1f)
                    .setDuration(2000)
                    .start();

            videoView.start();
            captureButton.setVisibility(View.GONE);
        }
        Toast.makeText(CameraVideoOverlayActivity.this, "Video found: " + movieName, Toast.LENGTH_SHORT).show();
    }

    @Override
    public void surfaceCreated(@NonNull SurfaceHolder holder) {
        surfaceHolder = holder;
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            initializeCamera();
        }
    }

    @Override
    public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width, int height) {
        if (camera != null) {
            camera.stopPreview();
            try {
                camera.setPreviewDisplay(holder);
                camera.startPreview();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
        if (camera != null) {
            camera.stopPreview();
            camera.release();
            camera = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 &&
                    grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeCamera();
            }
        }
    }
}