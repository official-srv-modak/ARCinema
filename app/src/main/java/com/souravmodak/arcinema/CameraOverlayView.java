package com.souravmodak.arcinema;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

public class CameraOverlayView extends View {

    private final Paint paint;
    private final Rect boxRect;

    public CameraOverlayView(Context context) {
        super(context);
        paint = new Paint();
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(5);
        boxRect = new Rect();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int canvasWidth = canvas.getWidth();
        int canvasHeight = canvas.getHeight();

        // Desired box width and height with 16:9 aspect ratio
        int boxWidth = (int) (canvasWidth * 0.8); // 80% of width
        int boxHeight = (int) (boxWidth * 9f / 16f); // Maintain 16:9 ratio

        // Center the box
        int centerX = canvasWidth / 2;
        int centerY = canvasHeight / 2;

        int left = centerX - boxWidth / 2;
        int top = centerY - boxHeight / 2;
        int right = centerX + boxWidth / 2;
        int bottom = centerY + boxHeight / 2;

        boxRect.set(left, top, right, bottom);
        canvas.drawRect(boxRect, paint);
    }
}
