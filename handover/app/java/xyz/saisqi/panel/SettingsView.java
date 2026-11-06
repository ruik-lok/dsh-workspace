package xyz.saisqi.panel;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class SettingsView extends ScrollView implements TabView {

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final TextView baseTv;
    private LinearLayout linksBox;
    private boolean loading = false;

    public SettingsView(Context c) {
        super(c);
        ctx = c;
        setFillViewport(true);
        setBackgroundColor(Ui.col(c, R.color.bg));
        LinearLayout col = Ui.vbox(c);
        int p = Ui.dp(c, 16);
        col.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 28));

        col.addView(Ui.text(c, "设置", 20, Ui.col(c, R.color.ink), true));

        col.addView(Ui.sectionTitle(c, "连接"));
        LinearLayout card = Ui.card(c);
        LinearLayout r = Ui.hbox(c);
        r.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        r.addView(Ui.text(c, "服务器地址", 14, Ui.col(c, R.color.ink), false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        baseTv = Ui.text(c, Api.get(c).base(), 12, Ui.col(c, R.color.muted), false);
        r.addView(baseTv);
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { editBase(); }
        });
        card.addView(r);
        card.addView(Ui.divider(c));
        TextView web = Ui.text(c, "在浏览器打开网页版", 14, Ui.col(c, R.color.accent), true);
        web.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 6));
        web.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { open(Api.get(ctx).base() + "/"); }
        });
        card.addView(web);
        card.addView(Ui.divider(c));
        TextView local = Ui.text(c, "本机 DSH（内嵌打开）", 14, Ui.col(c, R.color.accent), true);
        local.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 6));
        local.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { ctx.startActivity(new Intent(ctx, DshActivity.class)); }
                catch (Exception e) { toast("打不开本机 DSH：" + e.getMessage()); }
            }
        });
        card.addView(local);
        card.addView(Ui.divider(c));
        TextView restart = Ui.text(c, "重启 DSH（卡死时用）", 14, Ui.col(c, R.color.bad), true);
        restart.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 6));
        restart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(ctx)
                        .setTitle("重启 DSH")
                        .setMessage("会短暂中断当前页面，约 10 秒后恢复。确定？")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("重启", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) { doRestart(); }
                        }).show();
            }
        });
        card.addView(restart);
        card.addView(Ui.divider(c));
        TextView console = Ui.text(c, "设备控制台（宝塔风格）", 14, Ui.col(c, R.color.accent), true);
        console.setPadding(0, Ui.dp(c, 12), 0, Ui.dp(c, 6));
        console.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try { ctx.startActivity(new Intent(ctx, ConsoleActivity.class)); }
                catch (Exception e) { toast("打不开控制台：" + e.getMessage()); }
            }
        });
        card.addView(console);
        col.addView(card);

        col.addView(Ui.sectionTitle(c, "快捷入口"));
        LinearLayout lc = Ui.card(c);
        linksBox = Ui.vbox(c);
        linksBox.addView(Ui.text(c, "加载中…", 12, Ui.col(c, R.color.muted), false));
        lc.addView(linksBox);
        col.addView(lc);
        TextView tip = Ui.text(c, "点一下用浏览器打开 · 长按复制链接", 11, Ui.col(c, R.color.muted), false);
        Ui.gap(tip, Ui.dp(c, 6), 0);
        tip.setPadding(Ui.dp(c, 4), 0, 0, 0);
        col.addView(tip);

        col.addView(Ui.sectionTitle(c, "账户"));
        LinearLayout ac = Ui.card(c);
        TextView logout = Ui.text(c, "退出登录", 14, Ui.col(c, R.color.bad), true);
        logout.setPadding(0, Ui.dp(c, 6), 0, Ui.dp(c, 6));
        logout.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                new AlertDialog.Builder(ctx)
                        .setTitle("退出登录")
                        .setMessage("退出后需要重新输入密码")
                        .setNegativeButton("取消", null)
                        .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                            @Override public void onClick(DialogInterface d, int w) {
                                Api.get(ctx).logout();
                                if (ctx instanceof MainActivity) ((MainActivity) ctx).backToLogin();
                            }
                        }).show();
            }
        });
        ac.addView(logout);
        col.addView(ac);

        col.addView(Ui.sectionTitle(c, "关于"));
        LinearLayout about = Ui.card(c);
        about.addView(line("应用", "控制面板"));
        about.addView(Ui.divider(c));
        about.addView(line("版本", "3.5 (15)"));
        about.addView(Ui.divider(c));
        TextView upd = Ui.text(c, "检查更新", 13.5f, Ui.col(c, R.color.accent), true);
        upd.setPadding(0, Ui.dp(c, 10), 0, Ui.dp(c, 10));
        upd.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { AppUpdate.check(ctx, true); }
        });
        about.addView(upd);
        about.addView(Ui.divider(c));
        about.addView(line("包名", "xyz.saisqi.panel"));
        about.addView(Ui.divider(c));
        about.addView(line("接口", "status-api · 8097"));
        col.addView(about);

        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout line(String k, String v) {
        LinearLayout r = Ui.hbox(ctx);
        r.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));
        r.addView(Ui.text(ctx, k, 13.5f, Ui.col(ctx, R.color.ink), false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(Ui.text(ctx, v, 12.5f, Ui.col(ctx, R.color.muted), false));
        return r;
    }

    @Override public void refresh() {
        baseTv.setText(Api.get(ctx).base());
        if (loading) return;
        loading = true;
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    final JSONObject o = Api.get(ctx).get("/api/links");
                    h.post(new Runnable() { @Override public void run() { render(o); loading = false; } });
                } catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override public void run() {
                            loading = false;
                            linksBox.removeAllViews();
                            linksBox.addView(Ui.text(ctx, "读取入口失败：" + e.getMessage(), 12, Ui.col(ctx, R.color.bad), false));
                        }
                    });
                }
            }
        });
    }

    private void render(JSONObject o) {
        linksBox.removeAllViews();
        String dsh = o.optString("dsh", "");
        if (!dsh.isEmpty()) {
            addLink("DSH Harness（当前 token）", dsh, true);
            linksBox.addView(Ui.divider(ctx));
        }
        String phone = o.optString("phoneDsh", "");
        boolean phoneUp = o.optBoolean("phoneUp", false);
        addLink(phoneUp ? "手机 DSH · 公网（token 自动跟随）" : "手机 DSH · 公网（隧道离线）",
                phone.isEmpty() ? "https://dsh.saisqi.xyz/dsh/go" : phone, false);
        linksBox.addView(Ui.divider(ctx));
        addLink("控制台 /status/", o.optString("status"), false);
        linksBox.addView(Ui.divider(ctx));
        addLink("宝塔面板 8888", o.optString("bt"), false);
        linksBox.addView(Ui.divider(ctx));

        String nap = o.optString("napcat", "");
        if (!nap.isEmpty()) {
            addLink("NapCat WebUI（需 SSH 隧道）", nap, false);
            linksBox.addView(Ui.divider(ctx));
        }
        JSONArray sites = o.optJSONArray("sites");
        if (sites != null) {
            for (int i = 0; i < sites.length(); i++) {
                String u = sites.optString(i);
                addLink(u.replaceFirst("^https?://", "").replaceAll("/$", ""), u, false);
                linksBox.addView(Ui.divider(ctx));
            }
        }
        addLink("控制面板 APK 下载", o.optString("apk"), false);
        linksBox.addView(Ui.divider(ctx));

        JSONArray acc = o.optJSONArray("accounts");
        if (acc != null) {
            for (int i = 0; i < acc.length(); i++) {
                JSONObject a = acc.optJSONObject(i);
                if (a == null) continue;
                addAccount(a.optString("name"), a.optString("user"), a.optString("pass"));
                if (i < acc.length() - 1) linksBox.addView(Ui.divider(ctx));
            }
        }
    }

    private void addLink(String title, final String url, boolean highlight) {
        LinearLayout r = Ui.hbox(ctx);
        r.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10));
        LinearLayout l = Ui.vbox(ctx);
        l.addView(Ui.text(ctx, title, 13.5f, highlight ? Ui.col(ctx, R.color.accent) : Ui.col(ctx, R.color.ink), highlight));
        TextView sub = Ui.text(ctx, url, 10.5f, Ui.col(ctx, R.color.muted), false);
        sub.setSingleLine(true);
        l.addView(sub);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView cp = Ui.text(ctx, "复制", 12, Ui.col(ctx, R.color.accent), false);
        cp.setPadding(Ui.dp(ctx, 10), Ui.dp(ctx, 2), Ui.dp(ctx, 10), Ui.dp(ctx, 2));
        cp.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(url); }
        });
        r.addView(cp);
        r.addView(Ui.text(ctx, "›", 18, Ui.col(ctx, R.color.muted), false));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (url.isEmpty()) return;
                open(url);
            }
        });
        r.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) {
                copy(url);
                return true;
            }
        });
        linksBox.addView(r);
    }

    private void addAccount(String name, String user, final String pass) {
        LinearLayout r = Ui.hbox(ctx);
        r.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10));
        LinearLayout l = Ui.vbox(ctx);
        l.addView(Ui.text(ctx, name, 13, Ui.col(ctx, R.color.ink), false));
        l.addView(Ui.text(ctx, "账号 " + user + " · 密码 " + pass, 11, Ui.col(ctx, R.color.muted), false));
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(Ui.text(ctx, "复制", 12, Ui.col(ctx, R.color.accent), false));
        r.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { copy(pass); }
        });
        linksBox.addView(r);
    }

    private void doRestart() {
        toast("已发送重启指令…");
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    java.net.HttpURLConnection c = (java.net.HttpURLConnection)
                            new java.net.URL("http://127.0.0.1:3099/admin/restart?key=***已打码***").openConnection();
                    c.setConnectTimeout(3000);
                    c.setReadTimeout(5000);
                    final int code = c.getResponseCode();
                    c.disconnect();
                    toast(code == 200 ? "重启中，约 10 秒恢复" : ("失败 HTTP " + code));
                } catch (final Exception e) {
                    toast("失败：" + e.getMessage());
                }
            }
        });
    }

    private void open(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) {
            toast("打不开：" + url);
        }
    }

    private void copy(String s) {
        try {
            ClipboardManager cm = (ClipboardManager) ctx.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("panel", s));
            toast("已复制");
        } catch (Exception ignored) { }
    }

    private void toast(String s) { android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show(); }

    private void editBase() {
        final EditText e = new EditText(ctx);
        e.setText(Api.get(ctx).base());
        e.setSelection(e.getText().length());
        new AlertDialog.Builder(ctx)
                .setTitle("服务器地址")
                .setView(e)
                .setNegativeButton("取消", null)
                .setPositiveButton("保存", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        Api.get(ctx).setBase(e.getText().toString());
                        baseTv.setText(Api.get(ctx).base());
                    }
                }).show();
    }
}