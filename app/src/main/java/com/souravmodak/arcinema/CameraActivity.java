package com.souravmodak.arcinema;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Size;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;

import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private PreviewView previewView;
    private View rectangleOverlay;
    private ImageView snapButton;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ImageCapture imageCapture;

    private final ActivityResultLauncher<String> requestCameraPermissionLauncher =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), isGranted -> {
                if (!isGranted) {
                    Toast.makeText(this, "Camera permission is required to use the camera.", Toast.LENGTH_SHORT).show();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera);

        previewView = findViewById(R.id.previewView);
        rectangleOverlay = findViewById(R.id.rectangleOverlay);
        snapButton = findViewById(R.id.snapButton);

        // Initialize CameraX
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                startCamera(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));

        // Set click listener on snap button
        snapButton.setOnClickListener(v -> takeSnapshot());
    }

    private void startCamera(@NonNull ProcessCameraProvider cameraProvider) {
        // Set up the preview use case
        Preview preview = new Preview.Builder()
                .setTargetResolution(new Size(1280, 720)) // Set the resolution for the preview
                .build();

        // Initialize ImageCapture
        imageCapture = new ImageCapture.Builder()
                .setTargetResolution(new Size(1280, 720))
                .build();

        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Select back camera as a default
        CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

        // Bind the lifecycle of the camera to the lifecycle of the activity
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }

    private void takeSnapshot() {
        if (imageCapture == null) return;

        // Capture image and process it to only keep the rectangle overlay area
        imageCapture.takePicture(ContextCompat.getMainExecutor(this), new ImageCapture.OnImageCapturedCallback() {
            @Override
            public void onCaptureSuccess(@NonNull ImageProxy imageProxy) {
                Bitmap bitmap = imageProxyToBitmap(imageProxy);
                cropToRectangle(bitmap);
                imageProxy.close();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                exception.printStackTrace();
                Toast.makeText(CameraActivity.this, "Failed to capture image.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private Bitmap imageProxyToBitmap(ImageProxy imageProxy) {
        ByteBuffer buffer = imageProxy.getPlanes()[0].getBuffer();
        byte[] bytes = new byte[buffer.capacity()];
        buffer.get(bytes);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }

    private void cropToRectangle(Bitmap bitmap) {
        // Get the actual size of the previewView and the rectangleOverlay
        int viewWidth = previewView.getWidth();
        int viewHeight = previewView.getHeight();

        int rectLeft = rectangleOverlay.getLeft();
        int rectTop = rectangleOverlay.getTop();
        int rectWidth = rectangleOverlay.getWidth();
        int rectHeight = rectangleOverlay.getHeight();

        // Calculate scaling factors to match the bitmap's resolution
        float widthScale = (float) bitmap.getWidth() / viewWidth;
        float heightScale = (float) bitmap.getHeight() / viewHeight;

        // Apply scaling to the rectangle's coordinates
        int cropLeft = Math.round(rectLeft * widthScale);
        int cropTop = Math.round(rectTop * heightScale);
        int cropWidth = Math.round(rectWidth * widthScale);
        int cropHeight = Math.round(rectHeight * heightScale);

        // Ensure the crop rectangle does not exceed the bitmap dimensions
        cropWidth = Math.min(cropWidth, bitmap.getWidth() - cropLeft);
        cropHeight = Math.min(cropHeight, bitmap.getHeight() - cropTop);

        try {
            // Crop the bitmap to the calculated rectangle
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight);

            // Use the croppedBitmap as needed, e.g., save or display it
            Toast.makeText(getApplicationContext(), "Image captured, cropped and will be sent for processing.", Toast.LENGTH_LONG).show();
            finish();
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Error cropping the image.", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }


    @Override
    protected void onStart() {
        super.onStart();
        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA);
        }
    }
}
