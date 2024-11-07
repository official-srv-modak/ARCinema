package com.souravmodak.arcinema;

public class Movie {
    private String title;
    private int imageResourceId;

    public Movie(String title, int imageResourceId) {
        this.title = title;
        this.imageResourceId = imageResourceId;
    }

    public String getTitle() {
        return title;
    }

    public int getImageResourceId() {
        return imageResourceId;
    }
}
