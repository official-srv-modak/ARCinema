package com.souravmodak.arcinema;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.VideoView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.hardware.Camera;
import java.io.IOException;

public class CameraVideoOverlayActivity extends AppCompatActivity implements SurfaceHolder.Callback {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;
    private Camera camera;
    private Button captureButton;
    private VideoView videoView;
    private FrameLayout frameLayout;
    private Bitmap capturedBitmap;
    private CameraOverlayView overlayView;

    private FrameLayout loadingScreen;  // Loading screen frame
    private ProgressBar loadingProgressBar;  // Circular progress bar
    private TextView loadingText;  // Loading text




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
    }

    private void initializeCamera() {
        try {
            camera = Camera.open();

            camera.setDisplayOrientation(90);

            camera.setPreviewDisplay(surfaceHolder);
            camera.startPreview();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private void capturePhoto() {
        loadingScreen.setVisibility(View.VISIBLE);
        loadingText.setVisibility(View.VISIBLE);
        if (camera != null) {
            camera.takePicture(null, null, (data, camera) -> {
                capturedBitmap = android.graphics.BitmapFactory.decodeByteArray(data, 0, data.length);
                new Handler().postDelayed(this::displayVideoOverlay, 2000);
            });
        }
    }

    private void displayVideoOverlay() {
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
            Uri videoUri = Uri.parse("https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4");
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
        }
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
