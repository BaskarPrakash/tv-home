package com.baskar.tvhome;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.Calendar;

/** Analog hands with no dial, drawn over the clock card. */
class ClockView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    ClockView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        Calendar now = Calendar.getInstance();
        float seconds = now.get(Calendar.SECOND);
        float minutes = now.get(Calendar.MINUTE) + seconds / 60;
        float hours = now.get(Calendar.HOUR) + minutes / 60;
        float radius = Math.min(getWidth(), getHeight()) / 2f;
        drawHand(canvas, hours / 12, radius * 0.55f, radius * 0.07f, 0xFF00E5FF);
        drawHand(canvas, minutes / 60, radius * 0.8f, radius * 0.06f, 0xFFFFFFFF);
        drawHand(canvas, seconds / 60, radius * 0.9f, radius * 0.03f, 0xFFFF2BD6);
    }

    private void drawHand(Canvas canvas, float turn, float length, float width, int color) {
        double angle = turn * 2 * Math.PI;
        float x = getWidth() / 2f;
        float y = getHeight() / 2f;
        paint.setColor(color);
        paint.setStrokeWidth(width);
        canvas.drawLine(x, y, x + (float) Math.sin(angle) * length, y - (float) Math.cos(angle) * length, paint);
    }
}
