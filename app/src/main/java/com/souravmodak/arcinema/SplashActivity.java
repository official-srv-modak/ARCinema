package com.souravmodak.arcinema;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;

public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splash);

        ImageView logo = findViewById(R.id.logoImageView);

        AlphaAnimation fadeIn = new AlphaAnimation(0.0f, 1.0f);
        fadeIn.setDuration(500);
        fadeIn.setStartOffset(200);
        AnimationSet animationSet = new AnimationSet(false);
        animationSet.addAnimation(fadeIn);

        logo.startAnimation(animationSet);

        new Handler().postDelayed(() -> {
            Intent intent = new Intent(SplashActivity.this, CameraVideoOverlayActivity.class);
            startActivity(intent);
            finish();
        }, 1200);
    }
}