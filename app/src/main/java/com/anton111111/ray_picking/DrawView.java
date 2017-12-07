package com.anton111111.ray_picking;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.support.annotation.Nullable;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by Anton Potekhin (Anton.Potekhin@gmail.com) on 07.12.17.
 */
public class DrawView extends View {

    private final Paint p;
    List<float[]> points = new ArrayList<>();
    private float rectLeftX = -1.0f;
    private float rectLeftY = -1.0f;
    private float rectRightX = -1.0f;
    private float rectRightY = -1.0f;

    public DrawView(Context context) {
        super(context);
        p = new Paint();
    }

    public DrawView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        p = new Paint();
    }

    public DrawView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        p = new Paint();
    }


    public void drawPoint(float x, float y) {
        points.add(new float[]{x, y});
        invalidate();
    }

    public void drawPoints(List<float[]> points) {
        this.points.addAll(points);
        invalidate();
    }

    public void drawRect(float lx, float ly, float rx, float ry) {
        this.rectLeftX = lx;
        this.rectLeftY = ly;
        this.rectRightX = rx;
        this.rectRightY = ry;
        invalidate();
    }


    @Override
    protected void onDraw(Canvas canvas) {
        Log.e("Yo", "draw point: " + points.size());
        for (float[] point : points) {
            Log.e("Yo", "draw point: " + Arrays.toString(point));
            p.setColor(Color.RED);
            p.setStrokeWidth(10);
            canvas.drawPoint(point[0], point[1], p);
        }
        if (rectLeftX > 0 && rectLeftY > 0 &&
                rectRightX > 0 && rectRightY > 0) {

            p.setColor(Color.BLUE);
            p.setStrokeWidth(10);
            canvas.drawRect(rectLeftX, rectLeftY, rectRightX, rectRightY, p);
        }
    }
}
