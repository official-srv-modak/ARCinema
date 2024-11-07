package com.souravmodak.arcinema;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class Description extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_description);

        String movieTitle = getIntent().getStringExtra("MOVIE_TITLE");
        TextView title = findViewById(R.id.showName);
        title.setText(movieTitle);

        ImageView backButton = findViewById(R.id.backButton);
        backButton.setOnClickListener(view -> {
            onBackPressed();
        });
    }
}