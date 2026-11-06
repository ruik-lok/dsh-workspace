package xyz.saisqi.panel;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

/** 迷你折线图（无第三方依赖）。 */
public class SparkView extends View {
    private float[] data = new float[0];
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int tint = 0xFFB45309;
    private int stroke = 0x1F1C1917;

    public SparkView(Context c) { super(c); init(); }
    public SparkView(Context c, AttributeSet a) { super(c, a); init(); }

    private void init() {
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(1.6f * getResources().getDisplayMetrics().density);
        line.setStrokeCap(Paint.Cap.ROUND);
        fill.setStyle(Paint.Style.FILL);
        base.setStyle(Paint.Style.STROKE);
        base.setStrokeWidth(1f);
    }

    public void setTint(int c) { tint = c; invalidate(); }
    public void setStroke(int c) { stroke = c; invalidate(); }

    public void setData(float[] d) {
        data = d == null ? new float[0] : d;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        base.setColor(stroke);
        float y = h - 1.5f;
        canvas.drawLine(0, y, w, y, base);
        if (data.length < 2) return;

        float max = 1f;
        for (float v : data) max = Math.max(max, v);
        max = Math.max(max, 10f);

        Path p = new Path();
        for (int i = 0; i < data.length; i++) {
            float x = w * i / (float) (data.length - 1);
            float py = h - (data[i] / max) * (h - 4f) - 2f;
            if (i == 0) p.moveTo(x, py); else p.lineTo(x, py);
        }
        Path area = new Path(p);
        area.lineTo(w, h);
        area.lineTo(0, h);
        area.close();
        fill.setShader(new LinearGradient(0, 0, 0, h, (tint & 0x00FFFFFF) | 0x33000000, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawPath(area, fill);
        line.setColor(tint);
        canvas.drawPath(p, line);
    }
}
