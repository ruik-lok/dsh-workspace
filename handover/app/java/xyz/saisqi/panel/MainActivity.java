package xyz.saisqi.panel;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private FrameLayout content;
    private LinearLayout nav;
    private final Handler h = new Handler(Looper.getMainLooper());
    private int tab = 0;
    private final DashView[] views = new DashView[1];
    private DashView dash;
    private BotView bot;
    private LogView logs;
    private SettingsView settings;
    private View topScrim;
    private TextView[] labels = new TextView[4];
    private View[] pills = new View[4];

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            TabView t = current();
            if (t != null) t.refresh();
            h.postDelayed(this, 3000);
        }
    };

    private TabView current() {
        switch (tab) {
            case 0: return dash;
            case 1: return bot;
            case 2: return logs;
            default: return settings;
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        // 全屏沉浸：内容可以滚到状态栏/手势条下面，从屏幕边缘滑出去
        getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
        getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            getWindow().setDecorFitsSystemWindows(false);
        }
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!night) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        getWindow().getDecorView().setSystemUiVisibility(flags);
        installCrashLogger();
        try {
            if (!Api.get(this).loggedIn()) showLogin();
            else showMain();
        } catch (Throwable t) {
            logCrash(t);
            showFatal(t);
        }
    }

    private void installCrashLogger() {
        final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override public void uncaughtException(Thread t, Throwable e) {
                logCrash(e);
                if (prev != null) prev.uncaughtException(t, e);
            }
        });
    }

    private void logCrash(Throwable t) {
        try {
            java.io.File dir = getExternalFilesDir(null);
            if (dir == null) dir = getFilesDir();
            java.io.File f = new java.io.File(dir, "crash.log");
            java.io.FileWriter w = new java.io.FileWriter(f, true);
            w.write("=== " + new java.util.Date() + " ===\n");
            w.write(android.util.Log.getStackTraceString(t));
            w.write("\n");
            w.close();
        } catch (Throwable ignored) { }
    }

    private void showFatal(Throwable t) {
        LinearLayout box = Ui.vbox(this);
        box.setPadding(Ui.dp(this, 24), Ui.dp(this, 120), Ui.dp(this, 24), 0);
        box.setBackgroundColor(Ui.col(this, R.color.bg));
        box.addView(Ui.text(this, "启动失败", 20, Ui.col(this, R.color.bad), true));
        box.addView(Ui.text(this, String.valueOf(t), 12, Ui.col(this, R.color.muted), false));
        setContentView(box);
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null && nav != null) {
            h.removeCallbacks(tick);
            h.post(tick);
        }
    }

    @Override protected void onPause() {
        super.onPause();
        h.removeCallbacks(tick);
    }

    // ---------------- 登录 ----------------

    private void showLogin() {
        final LinearLayout root = Ui.vbox(this);
        root.setBackgroundColor(Ui.col(this, R.color.bg));
        root.setGravity(Gravity.CENTER);
        root.setPadding(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);

        LinearLayout card = Ui.card(this);
        card.setPadding(Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22), Ui.dp(this, 22));

        TextView title = Ui.text(this, "控制面板", 22, Ui.col(this, R.color.ink), true);
        card.addView(title);
        TextView sub = Ui.text(this, "连接服务器，查看状态与管理机器人", 13, Ui.col(this, R.color.muted), false);
        Ui.gap(sub, Ui.dp(this, 6), Ui.dp(this, 18));
        card.addView(sub);

        TextView lb = Ui.text(this, "服务器地址", 12, Ui.col(this, R.color.muted), true);
        card.addView(lb);

        final EditText url = field(Api.get(this).base());
        url.setInputType(InputType.TYPE_TEXT_VARIATION_URI);
        card.addView(url, fieldParams());
        Ui.gap(url, Ui.dp(this, 6), Ui.dp(this, 14));

        TextView lb2 = Ui.text(this, "面板密码", 12, Ui.col(this, R.color.muted), true);
        card.addView(lb2);

        final EditText pwd = field("");
        pwd.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        pwd.setHint("请输入密码");
        card.addView(pwd, fieldParams());
        Ui.gap(pwd, Ui.dp(this, 6), Ui.dp(this, 6));

        final TextView err = Ui.text(this, "", 12, Ui.col(this, R.color.bad), false);
        Ui.gap(err, Ui.dp(this, 8), Ui.dp(this, 4));
        card.addView(err);

        final Button go = new Button(this);
        go.setText("进入");
        go.setAllCaps(false);
        go.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        go.setTextColor(0xFFFFF7ED);
        go.setBackground(Ui.round(this, Ui.col(this, R.color.accent), Color.TRANSPARENT, 12));
        go.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        card.addView(go, fieldParams());
        Ui.gap(go, Ui.dp(this, 10), 0);

        go.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                final String u = url.getText().toString().trim();
                final String p = pwd.getText().toString();
                if (u.isEmpty() || p.isEmpty()) { err.setText("请填写地址与密码"); return; }
                go.setEnabled(false);
                go.setText("连接中…");
                Api.get(MainActivity.this).setBase(u);
                Net.run(new Runnable() {
                    @Override public void run() {
                        try {
                            final boolean ok = Api.get(MainActivity.this).login(p);
                            h.post(new Runnable() {
                                @Override public void run() {
                                    go.setEnabled(true);
                                    go.setText("进入");
                                    if (ok) {
                                        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                                        if (imm != null) imm.hideSoftInputFromWindow(pwd.getWindowToken(), 0);
                                        showMain();
                                    } else err.setText("密码不正确");
                                }
                            });
                        } catch (final Exception e) {
                            h.post(new Runnable() {
                                @Override public void run() {
                                    go.setEnabled(true);
                                    go.setText("进入");
                                    err.setText("连接失败：" + e.getMessage());
                                }
                            });
                        }
                    }
                });
            }
        });

        FrameLayout outer = new FrameLayout(this);
        outer.setBackgroundColor(Ui.col(this, R.color.bg));
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.setMargins(Ui.dp(this, 24), 0, Ui.dp(this, 24), 0);
        clp.gravity = Gravity.CENTER;
        outer.addView(card, clp);
        setContentInsets(outer);
    }

    /** 让内容避开系统手势条/导航栏，否则底部 tab 会被压到手势区里点不到。 */
    private void setContentInsets(View v) {
        v.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                view.setPadding(0, insets.getSystemWindowInsetTop(), 0, insets.getSystemWindowInsetBottom());
                return insets;
            }
        });
        setContentView(v);
    }

    private LinearLayout.LayoutParams fieldParams() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private EditText field(String value) {
        EditText e = new EditText(this);
        e.setText(value);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        e.setTextColor(Ui.col(this, R.color.ink));
        e.setHintTextColor(Ui.col(this, R.color.muted));
        e.setBackground(Ui.round(this, Ui.col(this, R.color.surface2), Ui.col(this, R.color.line), 10));
        e.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        return e;
    }

    // ---------------- 主体 ----------------

    private void showMain() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Ui.col(this, R.color.bg));

        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        dash = new DashView(this);
        bot = new BotView(this);
        logs = new LogView(this);
        settings = new SettingsView(this);
        content.addView(dash);
        content.addView(bot);
        content.addView(logs);
        content.addView(settings);
        bot.setVisibility(View.GONE);
        logs.setVisibility(View.GONE);
        settings.setVisibility(View.GONE);

        // 液态玻璃 dock：模糊身后内容 + 半透明白 + 高光描边
        GlassDock dock = new GlassDock(this);
        nav = dock;
        nav.setPadding(Ui.dp(this, 8), Ui.dp(this, 13), Ui.dp(this, 8), Ui.dp(this, 13));
        String[] names = {"状态", "机器人", "日志", "设置"};
        for (int i = 0; i < 4; i++) {
            LinearLayout item = Ui.vbox(this);
            item.setGravity(Gravity.CENTER);
            item.setPadding(Ui.dp(this, 2), Ui.dp(this, 8), Ui.dp(this, 2), Ui.dp(this, 8));
            item.setBackground(Ui.round(this, i == 0 ? Ui.col(this, R.color.dock_on) : Color.TRANSPARENT, Color.TRANSPARENT, 20));
            TextView t = Ui.text(this, names[i], 13.5f, i == 0 ? 0xFFFFFFFF : Ui.col(this, R.color.dock_off), i == 0);
            t.setGravity(Gravity.CENTER);
            item.addView(t, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            final int idx = i;
            item.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) { select(idx); }
            });
            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            ilp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
            nav.addView(item, ilp);
            labels[i] = t;
            pills[i] = item;
        }
        // 顶部渐隐遮罩：内容从状态栏下方"化开"，不会和时钟叠在一起
        final View scrim = new View(this);
        scrim.setClickable(false);
        scrim.setFocusable(false);
        int bgCol = Ui.col(this, R.color.bg);
        scrim.setBackground(new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{bgCol, bgCol, (bgCol & 0x00FFFFFF)}));
        this.topScrim = scrim;
        FrameLayout.LayoutParams slp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 108));
        root.addView(scrim, slp);

        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.gravity = Gravity.BOTTOM;
        nlp.setMargins(Ui.dp(this, 14), 0, Ui.dp(this, 14), Ui.dp(this, 14));
        root.addView(nav, nlp);
        dock.setBehind(content);
        applyEdgeInsets(root, dock);
        dock.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override public void onLayoutChange(View v, int l, int t, int r, int b, int ol, int ot, int or, int ob) {
                int bottom = ((View) v).getRootWindowInsets() == null ? 0
                        : ((View) v).getRootWindowInsets().getSystemWindowInsetBottom();
                int pad = v.getHeight() + Ui.dp(MainActivity.this, 26) + bottom;
                applyScrollInsets(((View) v).getRootWindowInsets() == null ? 0
                        : ((View) v).getRootWindowInsets().getSystemWindowInsetTop(), pad);
            }
        });
        setContentView(root);
        select(0);
        AppUpdate.check(this, false);
        h.removeCallbacks(tick);
        h.post(tick);
    }

    /** 内容铺满全屏：顶部让出状态栏、底部让出 dock 高度，滚动时内容从屏幕边滑出去 */
    private void applyEdgeInsets(final View root, final GlassDock dock) {
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override public WindowInsets onApplyWindowInsets(View v, final WindowInsets insets) {
                final int top = insets.getSystemWindowInsetTop();
                final int bottom = insets.getSystemWindowInsetBottom();
                dock.post(new Runnable() {
                    @Override public void run() {
                        int dockH = Math.max(dock.getHeight(), Ui.dp(MainActivity.this, 58));
                        int pad = dockH + Ui.dp(MainActivity.this, 26) + bottom;
                        applyScrollInsets(top, pad);
                        if (topScrim != null) {
                            int scrimH = top + Ui.dp(MainActivity.this, 18);
                            FrameLayout.LayoutParams slp = (FrameLayout.LayoutParams) topScrim.getLayoutParams();
                            slp.height = scrimH;
                            topScrim.setLayoutParams(slp);
                        }
                        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) dock.getLayoutParams();
                        lp.bottomMargin = bottom + Ui.dp(MainActivity.this, 14);
                        dock.setLayoutParams(lp);
                    }
                });
                return insets;
            }
        });
    }

    private void applyScrollInsets(int top, int bottom) {
        View[] vs = {dash, bot, logs, settings};
        for (View v : vs) {
            if (v == null) continue;
            if (v instanceof ViewGroup) ((ViewGroup) v).setClipToPadding(false);
            v.setPadding(0, top, 0, bottom);
        }
    }

    private void select(int idx) {
        tab = idx;
        dash.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        bot.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        logs.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);
        settings.setVisibility(idx == 3 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 4; i++) {
            boolean on = i == idx;
            labels[i].setTextColor(on ? 0xFFFFFFFF : Ui.col(this, R.color.dock_off));
            labels[i].setTypeface(Typeface.create("sans-serif-medium", on ? Typeface.BOLD : Typeface.NORMAL));
            pills[i].setBackground(Ui.round(this, on ? Ui.col(this, R.color.dock_on) : Color.TRANSPARENT, Color.TRANSPARENT, 20));
        }
        TabView t = current();
        if (t != null) t.refresh();
    }

    public void toast(String s) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show(); }

    public void backToLogin() {
        Api.get(this).logout();
        h.removeCallbacks(tick);
        showLogin();
    }
}