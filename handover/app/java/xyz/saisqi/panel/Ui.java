package xyz.saisqi.panel;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ClipDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;
import android.widget.TextView;

/** 纯代码 UI 工具：全部走资源色，自动适配深浅色。 */
public class Ui {

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    public static int col(Context c, int id) { return c.getColor(id); }

    public static GradientDrawable round(Context c, int fill, int stroke, float r) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(dp(c, r));
        if (stroke != Color.TRANSPARENT) d.setStroke(Math.max(1, dp(c, 1)), stroke);
        return d;
    }

    public static LinearLayout vbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    public static LinearLayout hbox(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    public static TextView text(Context c, String s, float sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        t.setIncludeFontPadding(false);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    public static TextView mono(Context c, String s, float sp, int color) {
        TextView t = text(c, s, sp, color, false);
        t.setTypeface(Typeface.MONOSPACE);
        return t;
    }

    /** 卡片容器：surface + hairline 描边 + 18dp 圆角 */
    public static LinearLayout card(Context c) {
        LinearLayout l = vbox(c);
        int p = dp(c, 16);
        l.setPadding(p, dp(c, 14), p, dp(c, 14));
        l.setBackground(round(c, col(c, R.color.surface), col(c, R.color.line), 18));
        return l;
    }

    public static View divider(Context c) {
        View v = new View(c);
        v.setBackgroundColor(col(c, R.color.line));
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, dp(c, 1))));
        return v;
    }

    public static LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }

    public static LinearLayout.LayoutParams lpw(float weight) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        return p;
    }

    public static void gap(View v, int top, int bottom) {
        ViewGroup.LayoutParams p = v.getLayoutParams();
        if (!(p instanceof LinearLayout.LayoutParams)) {
            p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            v.setLayoutParams(p);
        }
        ((LinearLayout.LayoutParams) p).topMargin = top;
        ((LinearLayout.LayoutParams) p).bottomMargin = bottom;
    }

    public static TextView sectionTitle(Context c, String s) {
        TextView t = text(c, s, 12.5f, col(c, R.color.muted), true);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, 22);
        p.bottomMargin = dp(c, 8);
        p.leftMargin = dp(c, 4);
        t.setLayoutParams(p);
        t.setLetterSpacing(0.08f);
        t.setAllCaps(false);
        return t;
    }

    public static ProgressBar bar(Context c, int color) {
        ProgressBar pb = new ProgressBar(c, null, android.R.attr.progressBarStyleHorizontal);
        Drawable bg = round(c, col(c, R.color.surface2), Color.TRANSPARENT, 3);
        Drawable fg = round(c, color, Color.TRANSPARENT, 3);
        LayerDrawable ld = new LayerDrawable(new Drawable[]{bg, new ClipDrawable(fg, Gravity.LEFT, ClipDrawable.HORIZONTAL)});
        ld.setId(0, android.R.id.background);
        ld.setId(1, android.R.id.progress);
        pb.setProgressDrawable(ld);
        pb.setMax(1000);
        pb.setMinimumHeight(dp(c, 4));
        pb.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(c, 4)));
        return pb;
    }

    public static Switch toggle(Context c) {
        Switch s = new Switch(c);
        int accent = col(c, R.color.accent);
        s.setThumbTintList(ColorStateList.valueOf(accent));
        s.setTrackTintList(ColorStateList.valueOf(0x55201A15));
        s.setScaleX(0.9f);
        s.setScaleY(0.9f);
        return s;
    }

    public static LinearLayout rowCard(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        l.setBackground(round(c, col(c, R.color.surface), col(c, R.color.line), 16));
        return l;
    }

    public static GradientDrawable chipBg(Context c, boolean active) {
        return round(c, active ? col(c, R.color.accent) : col(c, R.color.surface2),
                active ? Color.TRANSPARENT : col(c, R.color.line), 999);
    }

    public static TextView chip(Context c, String s, boolean active) {
        TextView t = text(c, s, 13, active ? 0xFFFFF7ED : col(c, R.color.ink), false);
        int px = dp(c, 14), py = dp(c, 7);
        t.setPadding(px, py, px, py);
        t.setBackground(chipBg(c, active));
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.rightMargin = dp(c, 8);
        t.setLayoutParams(p);
        return t;
    }

    public static RippleDrawable ripple(Context c, Drawable content, float radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(0x1A000000), content, null);
    }
}
