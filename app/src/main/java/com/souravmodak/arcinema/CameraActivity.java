package com.souravmodak.arcinema;

import android.Manifest;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

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
                int rotationDegrees = imageProxy.getImageInfo().getRotationDegrees();
                cropToRectangle(bitmap, rotationDegrees);
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

    private void cropToRectangle(Bitmap bitmap, int rotationDegrees) {
        // Rotate the bitmap based on the rotation degrees from ImageProxy
        bitmap = rotateBitmap(bitmap, rotationDegrees);

        int viewWidth = previewView.getWidth();
        int viewHeight = previewView.getHeight();

        int rectLeft = rectangleOverlay.getLeft();
        int rectTop = rectangleOverlay.getTop();
        int rectWidth = rectangleOverlay.getWidth();
        int rectHeight = rectangleOverlay.getHeight();

        float widthScale = (float) bitmap.getWidth() / viewWidth;
        float heightScale = (float) bitmap.getHeight() / viewHeight;

        int cropLeft = Math.round(rectLeft * widthScale);
        int cropTop = Math.round(rectTop * heightScale);
        int cropWidth = Math.round(rectWidth * widthScale);
        int cropHeight = Math.round(rectHeight * heightScale);

        cropWidth = Math.min(cropWidth, bitmap.getWidth() - cropLeft);
        cropHeight = Math.min(cropHeight, bitmap.getHeight() - cropTop);

        try {
            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropLeft, cropTop, cropWidth, cropHeight);

            // Save the cropped bitmap as a JPEG file
            File outputFile = saveBitmapAsFile(croppedBitmap);

            if (outputFile != null) {
                // Call API with the saved file
                uploadImageToApi(outputFile);
            } else {
                Toast.makeText(this, "Error saving image file.", Toast.LENGTH_SHORT).show();
            }
        } catch (IllegalArgumentException e) {
            Toast.makeText(this, "Error cropping the image.", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    private Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees) {
        if (rotationDegrees == 0) {
            return bitmap;
        }
        Matrix matrix = new Matrix();
        matrix.postRotate(rotationDegrees);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    private File saveBitmapAsFile(Bitmap bitmap) {
        File file = new File(getExternalFilesDir(null), "cropped_image.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, out);
            return file;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void uploadImageToApi(File imageFile) {
        runOnUiThread(() -> showLoadingDialog()); // Show loading screen

        new Thread(() -> {
            try {
                // Configure OkHttpClient with timeouts
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS) // 60 seconds for connection
                        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS) // 60 seconds for reading
                        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS) // 60 seconds for writing
                        .build();

                RequestBody requestBody = new MultipartBody.Builder()
                        .setType(MultipartBody.FORM)
                        .addFormDataPart("imageFile", imageFile.getName(),
                                RequestBody.create(MediaType.parse("image/jpeg"), imageFile))
                        .build();

                Request request = new Request.Builder()
                        .url("http://10.0.0.47:8089/arcinema-image-search/get-url")
                        .post(requestBody)
                        .build();

                Response response = client.newCall(request).execute();

                // Handle API response
                if (response.isSuccessful() && response.body() != null) {
                    String responseBody = response.body().string();
                    String videoUrl = parseVideoUrl(responseBody);
                    runOnUiThread(() -> {
                        dismissLoadingDialog(); // Dismiss loading screen
                        startVideoPlayer(videoUrl);
                    });
                } else {
                    runOnUiThread(() -> {
                        dismissLoadingDialog(); // Dismiss loading screen
                        Toast.makeText(this, "Failed to get video URL.", Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    dismissLoadingDialog(); // Dismiss loading screen
                    Toast.makeText(this, "Error, seems the image isn't a known poster.", Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    private ProgressDialog loadingDialog;

    private void showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = new ProgressDialog(this);
            loadingDialog.setMessage("Processing...");
            loadingDialog.setCancelable(false);
        }
        loadingDialog.show();
    }

    private void dismissLoadingDialog() {
        if (loadingDialog != null && loadingDialog.isShowing()) {
            loadingDialog.dismiss();
        }
    }

    private String parseVideoUrl(String responseBody) {
        try {
            JSONObject jsonObject = new JSONObject(responseBody);
            return jsonObject.getString("url");
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void startVideoPlayer(String videoUrl) {
        if (videoUrl != null) {
            Intent intent = new Intent(this, VideoPlayerActivity.class);
            intent.putExtra("VIDEO_URL", videoUrl);
            startActivity(intent);
        } else {
            Toast.makeText(this, "Invalid video URL.", Toast.LENGTH_SHORT).show();
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
