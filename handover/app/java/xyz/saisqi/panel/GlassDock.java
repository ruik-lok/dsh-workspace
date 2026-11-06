package xyz.saisqi.panel;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.widget.LinearLayout;

/**
 * 液态玻璃 dock：抓取身后内容 → 高斯模糊 → 半透明白玻璃 → 高光描边。
 * Android 12+ 走 GPU RenderEffect 真模糊，低版本退化成半透明玻璃。
 */
public class GlassDock extends LinearLayout {

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hi = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path clipPath = new Path();
    private final RectF rect = new RectF();
    private final float density;
    private final float radius;

    private View behind;
    private Bitmap frame;
    private long lastCapture;
    private final Object blurNode = null;

    public GlassDock(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        radius = 33 * density;
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        setClipChildren(false);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(Math.max(1f, 1.1f * density));
        hi.setStyle(Paint.Style.STROKE);
        hi.setStrokeWidth(Math.max(1f, 1f * density));
    }

    public void setBehind(View v) { behind = v; }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (night) {
            tint.setShader(new LinearGradient(0, 0, w, h, 0x4A9DB4D8, 0x2A6E86AD, Shader.TileMode.CLAMP));
        } else {
            tint.setShader(new LinearGradient(0, 0, w, h, 0x5AFFFFFF, 0x2EFFFFFF, Shader.TileMode.CLAMP));
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        rect.set(0, 0, w, h);
        clipPath.reset();
        clipPath.addRoundRect(rect, radius, radius, Path.Direction.CW);

        canvas.save();
        canvas.clipPath(clipPath);
        boolean drew = drawBlurred(canvas, w, h);
        if (!drew) {
            boolean dn = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            fill.setColor(dn ? 0x660B1220 : 0x66FFFFFF);
            canvas.drawRect(rect, fill);
        }
        canvas.drawRect(rect, tint);
        canvas.restore();

        boolean dark = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        stroke.setColor(dark ? 0x40FFFFFF : 0x66FFFFFF);
        canvas.drawRoundRect(rect, radius, radius, stroke);
        hi.setColor(dark ? 0x40FFFFFF : 0x70FFFFFF);
        canvas.save();
        canvas.clipPath(clipPath);
        canvas.drawLine(radius * 0.5f, 1.1f * density, w - radius * 0.5f, 1.1f * density, hi);
        canvas.restore();
    }

    private Bitmap capture(int w, int h) {
        if (behind == null) return null;
        long now = SystemClock.uptimeMillis();
        if (frame == null || frame.getWidth() != w || frame.getHeight() != h) {
            frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            lastCapture = 0;
        }
        if (now - lastCapture < 110) return frame;
        try {
            frame.eraseColor(Color.TRANSPARENT);
            Canvas bc = new Canvas(frame);
            int[] loc = new int[2];
            getLocationInWindow(loc);
            int[] bl = new int[2];
            behind.getLocationInWindow(bl);
            bc.translate(bl[0] - loc[0], bl[1] - loc[1]);
            behind.draw(bc);
            lastCapture = now;
            return frame;
        } catch (Throwable t) {
            return frame;
        }
    }

    private boolean drawBlurred(Canvas canvas, int w, int h) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return false;
        Bitmap bmp = capture(w, h);
        if (bmp == null) return false;
        try {
            return Blur.draw(canvas, bmp, w, h, 26f * density);
        } catch (Throwable t) {
            return false;
        }
    }

    /** 单独成类，避免低版本加载 RenderNode/RenderEffect。 */
    private static final class Blur {
        private static android.graphics.RenderNode node;

        static boolean draw(Canvas canvas, Bitmap bmp, int w, int h, float radius) {
            if (node == null) node = new android.graphics.RenderNode("dock-blur");
            node.setPosition(0, 0, w, h);
            android.graphics.RecordingCanvas rc = node.beginRecording();
            rc.drawBitmap(bmp, 0, 0, null);
            node.endRecording();
            node.setRenderEffect(android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            canvas.drawRenderNode(node);
            return true;
        }
    }
}