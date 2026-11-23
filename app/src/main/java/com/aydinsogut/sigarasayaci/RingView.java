package com.aydinsogut.sigarasayaci;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/** Basit ilerleme halkası: altta gri iz, üstte renkli yay. */
public class RingView extends View {
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private float progress; // 0..1

    public RingView(Context c, int trackColor, int arcColor) {
        super(c);
        float w = 6 * c.getResources().getDisplayMetrics().density;
        for (Paint p : new Paint[]{track, arc}) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(w);
            p.setStrokeCap(Paint.Cap.ROUND);
        }
        track.setColor(trackColor);
        arc.setColor(arcColor);
    }

    public void setProgress(float p) {
        progress = Math.max(0f, Math.min(1f, p));
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float half = track.getStrokeWidth() / 2f;
        box.set(half, half, getWidth() - half, getHeight() - half);
        canvas.drawArc(box, 135, 270, false, track);
        if (progress > 0) canvas.drawArc(box, 135, 270 * progress, false, arc);
    }
}
