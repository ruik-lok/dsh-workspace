package xyz.saisqi.panel;

import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class DashView extends ScrollView implements TabView {

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final LinearLayout col;

    private TextView hostTv, subTv, dshLocal, dshPub, dInfoTv;
    private TextView dCpuV, dCpuSub, dMemV, dMemSub, dDiskV, dDiskSub, dBatV, dBatSub;
    private ProgressBar dCpuBar, dMemBar, dDiskBar, dBatBar;
    private long lastDshCheck = 0;
    private TextView cpuV, memV, diskV, loadV, netV;
    private TextView cpuSub, memSub, diskSub, loadSub;
    private ProgressBar cpuBar, memBar, diskBar;
    private SparkView cpuSpark, memSpark;
    private LinearLayout svcBox, domBox;
    private boolean loading = false;

    public DashView(Context c) {
        super(c);
        ctx = c;
        setFillViewport(true);
        setBackgroundColor(Ui.col(c, R.color.bg));
        col = Ui.vbox(c);
        int p = Ui.dp(c, 16);
        col.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 28));
        addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        build();
    }

    private TextView big(String s) { return Ui.text(ctx, s, 26, Ui.col(ctx, R.color.ink), true); }
    private TextView small(String s) { return Ui.text(ctx, s, 11.5f, Ui.col(ctx, R.color.muted), false); }

    private void build() {
        // 头部
        LinearLayout head = Ui.hbox(ctx);
        LinearLayout hl = Ui.vbox(ctx);
        hostTv = Ui.text(ctx, "连接中…", 20, Ui.col(ctx, R.color.ink), true);
        subTv = Ui.text(ctx, "", 12, Ui.col(ctx, R.color.muted), false);
        Ui.gap(subTv, Ui.dp(ctx, 4), 0);
        hl.addView(hostTv);
        hl.addView(subTv);
        head.addView(hl, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        col.addView(head, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // DSH 在线状态
        col.addView(Ui.sectionTitle(ctx, "DSH"));
        LinearLayout dshCard = Ui.card(ctx);
        dshLocal = Ui.text(ctx, "本机 DSH：检测中…", 13.5f, Ui.col(ctx, R.color.muted), false);
        dshPub = Ui.text(ctx, "公网入口：检测中…", 13.5f, Ui.col(ctx, R.color.muted), false);
        Ui.gap(dshPub, Ui.dp(ctx, 6), 0);
        dshCard.addView(dshLocal);
        dshCard.addView(dshPub);
        col.addView(dshCard);

        // 本机设备（与服务器同款卡片）
        col.addView(Ui.sectionTitle(ctx, "本机设备"));
        LinearLayout dr1 = Ui.hbox(ctx);
        LinearLayout dCpu = tile("CPU", true, true);
        LinearLayout dMem = tile("内存", true, true);
        dr1.addView(dCpu, weight());
        dr1.addView(dMem, weight());
        col.addView(dr1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout dr2 = Ui.hbox(ctx);
        LinearLayout dDisk = tile("存储", true, false);
        LinearLayout dBat = tile("电量", true, false);
        dr2.addView(dDisk, weight());
        dr2.addView(dBat, weight());
        Ui.gap(dr2, Ui.dp(ctx, 10), 0);
        col.addView(dr2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dCpuV = (TextView) dCpu.getTag(R.id.tag_value);
        dCpuSub = (TextView) dCpu.getTag(R.id.tag_sub);
        dCpuBar = (ProgressBar) dCpu.getTag(R.id.tag_bar);
        dMemV = (TextView) dMem.getTag(R.id.tag_value);
        dMemSub = (TextView) dMem.getTag(R.id.tag_sub);
        dMemBar = (ProgressBar) dMem.getTag(R.id.tag_bar);
        dDiskV = (TextView) dDisk.getTag(R.id.tag_value);
        dDiskSub = (TextView) dDisk.getTag(R.id.tag_sub);
        dDiskBar = (ProgressBar) dDisk.getTag(R.id.tag_bar);
        dBatV = (TextView) dBat.getTag(R.id.tag_value);
        dBatSub = (TextView) dBat.getTag(R.id.tag_sub);
        dBatBar = (ProgressBar) dBat.getTag(R.id.tag_bar);

        LinearLayout devInfo = Ui.card(ctx);
        dInfoTv = Ui.text(ctx, "读取中…", 12f, Ui.col(ctx, R.color.muted), false);
        devInfo.addView(dInfoTv);
        col.addView(devInfo);

        // 第一行指标
        col.addView(Ui.sectionTitle(ctx, "服务器 · 运行指标"));
        LinearLayout r1 = Ui.hbox(ctx);
        LinearLayout t1 = tile("CPU", true);
        LinearLayout t2 = tile("内存", true);
        r1.addView(t1, weight());
        r1.addView(t2, weight());
        col.addView(r1, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout r2 = Ui.hbox(ctx);
        LinearLayout t3 = tile("磁盘", true, false);
        LinearLayout t4 = tile("负载", false);
        r2.addView(t3, weight());
        r2.addView(t4, weight());
        Ui.gap(r2, Ui.dp(ctx, 10), 0);
        col.addView(r2, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        cpuV = (TextView) t1.getTag(R.id.tag_value);
        cpuSub = (TextView) t1.getTag(R.id.tag_sub);
        cpuBar = (ProgressBar) t1.getTag(R.id.tag_bar);
        cpuSpark = (SparkView) t1.getTag(R.id.tag_spark);
        memV = (TextView) t2.getTag(R.id.tag_value);
        memSub = (TextView) t2.getTag(R.id.tag_sub);
        memBar = (ProgressBar) t2.getTag(R.id.tag_bar);
        memSpark = (SparkView) t2.getTag(R.id.tag_spark);
        diskV = (TextView) t3.getTag(R.id.tag_value);
        diskSub = (TextView) t3.getTag(R.id.tag_sub);
        diskBar = (ProgressBar) t3.getTag(R.id.tag_bar);
        loadV = (TextView) t4.getTag(R.id.tag_value);
        loadSub = (TextView) t4.getTag(R.id.tag_sub);

        // 网络
        col.addView(Ui.sectionTitle(ctx, "网络"));
        LinearLayout netCard = Ui.card(ctx);
        netV = Ui.text(ctx, "—", 15, Ui.col(ctx, R.color.ink), true);
        netCard.addView(netV);
        col.addView(netCard);

        // 服务
        col.addView(Ui.sectionTitle(ctx, "服务"));
        LinearLayout svcCard = Ui.card(ctx);
        svcBox = Ui.vbox(ctx);
        svcCard.addView(svcBox);
        col.addView(svcCard);

        // 域名
        col.addView(Ui.sectionTitle(ctx, "站点"));
        LinearLayout domCard = Ui.card(ctx);
        domBox = Ui.vbox(ctx);
        domCard.addView(domBox);
        col.addView(domCard);
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1);
        lp.rightMargin = Ui.dp(ctx, 5);
        lp.leftMargin = Ui.dp(ctx, 5);
        return lp;
    }

    private LinearLayout tile(String label, boolean withBar) {
        return tile(label, withBar, withBar);
    }

    private LinearLayout tile(String label, boolean withBar, boolean withSpark) {
        LinearLayout card = Ui.card(ctx);
        card.setPadding(Ui.dp(ctx, 14), Ui.dp(ctx, 12), Ui.dp(ctx, 14), Ui.dp(ctx, 12));

        TextView lb = Ui.text(ctx, label, 12, Ui.col(ctx, R.color.muted), true);
        card.addView(lb);

        TextView val = Ui.text(ctx, "—", 24, Ui.col(ctx, R.color.ink), true);
        Ui.gap(val, Ui.dp(ctx, 4), 0);
        card.addView(val);

        TextView sub = Ui.text(ctx, "", 11, Ui.col(ctx, R.color.muted), false);
        card.addView(sub);

        if (withBar) {
            ProgressBar bar = Ui.bar(ctx, Ui.col(ctx, R.color.accent));
            Ui.gap(bar, Ui.dp(ctx, 8), 0);
            card.addView(bar);
            if (!withSpark) {
                card.setTag(R.id.tag_bar, bar);
                card.setTag(R.id.tag_value, val);
                card.setTag(R.id.tag_sub, sub);
                return card;
            }
            SparkView sp = new SparkView(ctx);
            sp.setTint(Ui.col(ctx, R.color.accent));
            sp.setStroke(Ui.col(ctx, R.color.line));
            LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(ctx, 26));
            slp.topMargin = Ui.dp(ctx, 6);
            card.addView(sp, slp);
            card.setTag(R.id.tag_bar, bar);
            card.setTag(R.id.tag_spark, sp);
        }
        card.setTag(R.id.tag_value, val);
        card.setTag(R.id.tag_sub, sub);
        return card;
    }

    /** 本机 3099 网关 / 公网入口 探活，任何 HTTP 响应都算在线。 */
    private void checkDsh() {
        if (System.currentTimeMillis() - lastDshCheck < 5000) return;
        lastDshCheck = System.currentTimeMillis();
        Net.run(new Runnable() {
            @Override public void run() {
                final int local = probe("http://127.0.0.1:3099/go");
                final int pub = probe("https://dsh.saisqi.xyz/dsh/");
                h.post(new Runnable() {
                    @Override public void run() {
                        boolean lo = local > 0;
                        boolean po = pub > 0;
                        dshLocal.setText("本机 DSH：" + (lo ? "● 在线（127.0.0.1:3099）" : "○ 离线"));
                        dshLocal.setTextColor(Ui.col(ctx, lo ? R.color.accent : R.color.bad));
                        dshPub.setText("公网入口：" + (po ? "● 在线（dsh.saisqi.xyz/dsh/）" : "○ 离线"));
                        dshPub.setTextColor(Ui.col(ctx, po ? R.color.accent : R.color.bad));
                    }
                });
            }
        });
    }

    private int probe(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(3000);
            c.setReadTimeout(3000);
            c.setInstanceFollowRedirects(false);
            c.setRequestMethod("GET");
            int code = c.getResponseCode();
            c.disconnect();
            return code;
        } catch (Exception e) {
            return -1;
        }
    }

    private void updateDevice() {
        Net.run(new Runnable() {
            @Override public void run() {
                final DeviceStat.Info i = DeviceStat.info((Context) ctx);
                h.post(new Runnable() { @Override public void run() { renderDevice(i); } });
            }
        });
    }

    private String gb(long b) {
        return String.format(java.util.Locale.US, "%.1f", b / 1073741824.0);
    }

    private void renderDevice(DeviceStat.Info i) {
        int cpu = Math.max(0, i.cpu);
        dCpuV.setText(cpu + "%");
        dCpuSub.setText("负载 " + i.load);
        dCpuBar.setProgress(cpu);

        int memPct = i.memTotal > 0 ? (int) Math.round((i.memTotal - i.memAvail) * 100.0 / i.memTotal) : 0;
        dMemV.setText(memPct + "%");
        dMemSub.setText(gb(i.memTotal - i.memAvail) + " / " + gb(i.memTotal) + " GB");
        dMemBar.setProgress(memPct);

        int diskPct = i.diskTotal > 0 ? (int) Math.round((i.diskTotal - i.diskFree) * 100.0 / i.diskTotal) : 0;
        dDiskV.setText(diskPct + "%");
        dDiskSub.setText(gb(i.diskTotal - i.diskFree) + " / " + gb(i.diskTotal) + " GB");
        dDiskBar.setProgress(diskPct);

        dBatV.setText(i.battery >= 0 ? i.battery + "%" : "—");
        String sub = "";
        if (i.temp > 0) sub = String.format(java.util.Locale.US, "%.1f°C", i.temp);
        if (i.charging) sub = sub.isEmpty() ? "充电中" : sub + " · 充电中";
        dBatSub.setText(sub);
        dBatBar.setProgress(Math.max(0, i.battery));

        dInfoTv.setText(i.net + " · " + (i.screenOn ? "亮屏" : "息屏") + (i.locked ? "（已锁）" : "")
                + " · 已运行 " + dur(i.uptime) + " · DSH " + (i.dsh ? "运行中" : "已停止"));
    }

    @Override public void refresh() {
        checkDsh();
        updateDevice();
        if (loading) return;
        loading = true;
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    final JSONObject o = Api.get(ctx).get("/api/status");
                    h.post(new Runnable() { @Override public void run() { render(o); loading = false; } });
                } catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override public void run() {
                            loading = false;
                            if (e instanceof Api.AuthException) {
                                subTv.setText("登录已失效，请重新登录");
                            } else {
                                subTv.setText("连接失败：" + e.getMessage());
                            }
                        }
                    });
                }
            }
        });
    }

    private void render(JSONObject o) {
        try {
            hostTv.setText(o.optString("host", "服务器"));
            long up = o.optLong("uptime", 0);
            subTv.setText("已运行 " + dur(up) + " · " + o.optInt("cores", 0) + " 核 · " + time(o.optLong("at", 0)));

            double cpu = o.optDouble("cpu", 0);
            cpuV.setText(Math.round(cpu) + "%");
            cpuSub.setText("实时占用");
            cpuBar.setProgress((int) Math.round(cpu * 10));
            JSONArray hc = o.optJSONObject("history") == null ? null : o.optJSONObject("history").optJSONArray("cpu");
            cpuSpark.setData(arr(hc));

            JSONObject mem = o.optJSONObject("mem");
            if (mem != null) {
                double mt = mem.optDouble("total", 0), mu = mem.optDouble("used", 0), mp = mem.optDouble("pct", 0);
                memV.setText(Math.round(mp) + "%");
                memSub.setText(fmtGB(mu) + " / " + fmtGB(mt) + " GB");
                memBar.setProgress((int) Math.round(mp * 10));
            }
            JSONArray hm = o.optJSONObject("history") == null ? null : o.optJSONObject("history").optJSONArray("mem");
            memSpark.setData(arr(hm));

            JSONObject disk = o.optJSONObject("disk");
            if (disk != null) {
                double dp = disk.optDouble("pct", 0);
                diskV.setText(Math.round(dp) + "%");
                diskSub.setText(String.format(Locale.US, "%.0f / %.0f GB", disk.optDouble("usedGB", 0), disk.optDouble("totalGB", 0)));
                diskBar.setProgress((int) Math.round(dp * 10));
            }

            JSONArray load = o.optJSONArray("load");
            if (load != null && load.length() >= 3) {
                loadV.setText(String.format(Locale.US, "%.2f", load.optDouble(0, 0)));
                loadSub.setText(String.format(Locale.US, "5m %.2f · 15m %.2f", load.optDouble(1, 0), load.optDouble(2, 0)));
            }

            JSONObject net = o.optJSONObject("net");
            if (net != null) {
                netV.setText("↓ " + fmtBytes(net.optDouble("rx", 0)) + "   ↑ " + fmtBytes(net.optDouble("tx", 0)));
            } else {
                netV.setText("无数据");
            }

            svcBox.removeAllViews();
            JSONArray svcs = o.optJSONArray("services");
            if (svcs != null) {
                for (int i = 0; i < svcs.length(); i++) {
                    JSONObject s = svcs.optJSONObject(i);
                    if (s == null) continue;
                    if (i > 0) svcBox.addView(Ui.divider(ctx));
                    svcBox.addView(svcRow(s.optString("name"), s.optBoolean("active"), s.optString("state"), s.optLong("ageSec", Math.round(s.optDouble("ageSec", 0)))));
                }
            }

            domBox.removeAllViews();
            JSONArray doms = o.optJSONArray("domains");
            if (doms != null) {
                for (int i = 0; i < doms.length(); i++) {
                    JSONObject d = doms.optJSONObject(i);
                    if (d == null) continue;
                    if (i > 0) domBox.addView(Ui.divider(ctx));
                    domBox.addView(domRow(d));
                }
            }
        } catch (Exception e) {
            subTv.setText("解析失败：" + e.getMessage());
        }
    }

    private View svcRow(String name, boolean active, String state, long ageSec) {
        LinearLayout r = Ui.hbox(ctx);
        r.setPadding(0, Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9));
        View dot = new View(ctx);
        dot.setBackground(Ui.round(ctx, active ? Ui.col(ctx, R.color.ok) : Ui.col(ctx, R.color.bad), Color.TRANSPARENT, 99));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        dlp.rightMargin = Ui.dp(ctx, 10);
        r.addView(dot, dlp);
        r.addView(Ui.text(ctx, name, 14, Ui.col(ctx, R.color.ink), false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String right = active ? (ageSec > 0 ? "运行 " + dur(ageSec) : "正常") : state;
        TextView rv = Ui.text(ctx, right, 12, active ? Ui.col(ctx, R.color.muted) : Ui.col(ctx, R.color.bad), false);
        r.addView(rv);
        return r;
    }

    private View domRow(JSONObject d) {
        LinearLayout r = Ui.hbox(ctx);
        r.setPadding(0, Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9));
        boolean ok = d.optBoolean("ok", false);
        View dot = new View(ctx);
        dot.setBackground(Ui.round(ctx, ok ? Ui.col(ctx, R.color.ok) : Ui.col(ctx, R.color.bad), Color.TRANSPARENT, 99));
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(Ui.dp(ctx, 8), Ui.dp(ctx, 8));
        dlp.rightMargin = Ui.dp(ctx, 10);
        r.addView(dot, dlp);
        r.addView(Ui.text(ctx, d.optString("domain"), 13.5f, Ui.col(ctx, R.color.ink), false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        String right = d.optInt("status", 0) + "";
        if (d.has("certDays") && !d.isNull("certDays")) right += " · 证书 " + d.optInt("certDays") + " 天";
        r.addView(Ui.text(ctx, right, 11.5f, Ui.col(ctx, R.color.muted), false));
        return r;
    }

    private float[] arr(JSONArray a) {
        if (a == null) return new float[0];
        float[] out = new float[a.length()];
        for (int i = 0; i < a.length(); i++) out[i] = (float) a.optDouble(i, 0);
        return out;
    }

    private String fmtGB(double gb) { return String.format(Locale.US, "%.1f", gb); }

    private String fmtBytes(double bytes) {
        if (bytes < 1024 * 1024) return String.format(Locale.US, "%.0f KB", bytes / 1024);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.US, "%.1f MB", bytes / 1024 / 1024);
        return String.format(Locale.US, "%.2f GB", bytes / 1024 / 1024 / 1024);
    }

    private String dur(long s) {
        long d = s / 86400, hh = (s % 86400) / 3600, mm = (s % 3600) / 60;
        if (d > 0) return d + " 天 " + hh + " 小时";
        if (hh > 0) return hh + " 小时 " + mm + " 分";
        return mm + " 分钟";
    }

    private String time(long at) {
        if (at <= 0) return "--:--";
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm:ss", Locale.CHINA);
        return f.format(new java.util.Date(at));
    }
}