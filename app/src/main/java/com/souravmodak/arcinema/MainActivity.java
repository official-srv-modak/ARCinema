package com.souravmodak.arcinema;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);


        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        recyclerView.setLayoutManager(new GridLayoutManager(this, 3)); // 3 columns

        // Sample data
        List<Movie> movieList = new ArrayList<>();
        movieList.add(new Movie("Avatar", R.drawable.placeholder_foreground));
        movieList.add(new Movie("Avengers", R.drawable.placeholder_foreground));
        movieList.add(new Movie("Iron Man", R.drawable.placeholder_foreground));

        for(int i = 1; i < 10; i++)
        {
            movieList.add(new Movie("Movie "+i, R.drawable.placeholder_foreground));
        }
        // Add more movies as needed

        MovieAdapter adapter = new MovieAdapter(movieList);
        recyclerView.setAdapter(adapter);
    }

    public void fabOnClick(View view) {
        Intent intent = new Intent(this, CameraVideoOverlayActivity.class);
        startActivity(intent);
    }
}