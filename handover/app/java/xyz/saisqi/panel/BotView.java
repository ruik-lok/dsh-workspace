package xyz.saisqi.panel;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;

public class BotView extends ScrollView implements TabView {

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final LinearLayout col;

    private Switch swEnabled, swGroup;
    private TextView qqState, qrAge;
    private android.widget.ImageView qrView;
    private int lastQrAge = -2;
    private LinearLayout modelRow, thinkRow, wlBox, memBox;
    private EditText personaInput;
    private TextView usageTv, memSummaryTv, titleTv;
    private boolean loading = false;
    private String model = "deepseek-flash";
    private String thinking = "default";

    public BotView(Context c) {
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

    private void build() {
        titleTv = Ui.text(ctx, "机器人", 20, Ui.col(ctx, R.color.ink), true);
        col.addView(titleTv);

        col.addView(Ui.sectionTitle(ctx, "QQ 登录"));
        LinearLayout qc = Ui.card(ctx);
        qqState = Ui.text(ctx, "检测中…", 14.5f, Ui.col(ctx, R.color.ink), true);
        qc.addView(qqState);
        qrAge = Ui.text(ctx, "", 11.5f, Ui.col(ctx, R.color.muted), false);
        qc.addView(qrAge);
        qrView = new android.widget.ImageView(ctx);
        qrView.setAdjustViewBounds(true);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(Ui.dp(ctx, 190), Ui.dp(ctx, 190));
        qlp.gravity = Gravity.CENTER_HORIZONTAL;
        qlp.topMargin = Ui.dp(ctx, 10);
        qc.addView(qrView, qlp);
        TextView refreshQr = Ui.text(ctx, "刷新二维码", 13, Ui.col(ctx, R.color.accent), true);
        refreshQr.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 2));
        refreshQr.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                qqState.setText("正在重启 QQ…");
                Net.run(new Runnable() {
                    @Override public void run() {
                        try {
                            JSONObject b = new JSONObject();
                            Api.get(ctx).post("/api/qq/refresh", b);
                            h.post(new Runnable() { @Override public void run() {
                                toast("已触发重启，约 40 秒后出新码");
                                lastQrAge = -2;
                            }});
                        } catch (final Exception e) {
                            h.post(new Runnable() { @Override public void run() {
                                toast("刷新失败：" + e.getMessage());
                            }});
                        }
                    }
                });
            }
        });
        qc.addView(refreshQr);
        col.addView(qc);

        col.addView(Ui.sectionTitle(ctx, "开关"));
        LinearLayout card = Ui.card(ctx);
        swEnabled = Ui.toggle(ctx);
        swEnabled.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act("toggle", new JSONObject()); }
        });
        card.addView(row("机器人启用", "关闭后不回复任何消息", swEnabled));
        card.addView(Ui.divider(ctx));
        swGroup = Ui.toggle(ctx);
        swGroup.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { act("group", new JSONObject()); }
        });
        card.addView(row("群聊 @ 回复", "开启后群里 @ 机器人也会回", swGroup));
        col.addView(card);

        col.addView(Ui.sectionTitle(ctx, "模型"));
        LinearLayout mc = Ui.card(ctx);
        modelRow = Ui.hbox(ctx);
        mc.addView(modelRow);
        col.addView(mc);

        col.addView(Ui.sectionTitle(ctx, "思考档"));
        LinearLayout tc = Ui.card(ctx);
        thinkRow = Ui.hbox(ctx);
        tc.addView(thinkRow);
        col.addView(tc);

        col.addView(Ui.sectionTitle(ctx, "人设"));
        LinearLayout pc = Ui.card(ctx);
        personaInput = new EditText(ctx);
        personaInput.setTextSize(14);
        personaInput.setTextColor(Ui.col(ctx, R.color.ink));
        personaInput.setHintTextColor(Ui.col(ctx, R.color.muted));
        personaInput.setHint("描述机器人的性格与说话方式");
        personaInput.setMinLines(3);
        personaInput.setMaxLines(8);
        personaInput.setGravity(Gravity.TOP | Gravity.START);
        personaInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        personaInput.setBackground(Ui.round(ctx, Ui.col(ctx, R.color.surface2), Ui.col(ctx, R.color.line), 10));
        personaInput.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10));
        pc.addView(personaInput);
        TextView save = Ui.text(ctx, "保存人设", 13, Ui.col(ctx, R.color.accent), true);
        save.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 2));
        save.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    JSONObject b = new JSONObject();
                    b.put("persona", personaInput.getText().toString());
                    act("persona", b);
                } catch (Exception ignored) { }
            }
        });
        pc.addView(save);
        col.addView(pc);

        col.addView(Ui.sectionTitle(ctx, "白名单"));
        LinearLayout wc = Ui.card(ctx);
        LinearLayout addRow = Ui.hbox(ctx);
        final EditText uidIn = new EditText(ctx);
        uidIn.setHint("QQ 号");
        uidIn.setTextSize(14);
        uidIn.setInputType(InputType.TYPE_CLASS_NUMBER);
        uidIn.setTextColor(Ui.col(ctx, R.color.ink));
        uidIn.setHintTextColor(Ui.col(ctx, R.color.muted));
        uidIn.setBackground(Ui.round(ctx, Ui.col(ctx, R.color.surface2), Ui.col(ctx, R.color.line), 10));
        uidIn.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 12), Ui.dp(ctx, 8));
        addRow.addView(uidIn, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView add = Ui.text(ctx, "添加", 13, Ui.col(ctx, R.color.accent), true);
        add.setPadding(Ui.dp(ctx, 12), 0, Ui.dp(ctx, 4), 0);
        add.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                String id = uidIn.getText().toString().trim();
                if (id.isEmpty()) return;
                try {
                    JSONObject b = new JSONObject();
                    b.put("id", id);
                    uidIn.setText("");
                    act("whitelist/add", b);
                } catch (Exception ignored) { }
            }
        });
        addRow.addView(add);
        wc.addView(addRow);
        wlBox = Ui.vbox(ctx);
        wc.addView(wlBox);
        col.addView(wc);

        col.addView(Ui.sectionTitle(ctx, "记忆"));
        LinearLayout mcard = Ui.card(ctx);
        memSummaryTv = Ui.text(ctx, "—", 12, Ui.col(ctx, R.color.muted), false);
        mcard.addView(memSummaryTv);
        mcard.addView(Ui.text(ctx, "点某个会话进入详情：逐条查看、删单条、改摘要", 11, Ui.col(ctx, R.color.muted), false));
        memBox = Ui.vbox(ctx);
        Ui.gap(memBox, Ui.dp(ctx, 6), 0);
        mcard.addView(memBox);
        TextView clearAll = Ui.text(ctx, "清空全部记忆", 13, Ui.col(ctx, R.color.bad), true);
        clearAll.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 2));
        clearAll.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm("清空全部记忆？", "该操作不可撤销", new Runnable() {
                    @Override public void run() {
                        try {
                            JSONObject b = new JSONObject();
                            b.put("uid", "all");
                            act("memory/forget", b);
                        } catch (Exception ignored) { }
                    }
                });
            }
        });
        mcard.addView(clearAll);
        col.addView(mcard);

        col.addView(Ui.sectionTitle(ctx, "用量"));
        LinearLayout uc = Ui.card(ctx);
        usageTv = Ui.text(ctx, "—", 13, Ui.col(ctx, R.color.ink), false);
        usageTv.setLineSpacing(Ui.dp(ctx, 3), 1f);
        uc.addView(usageTv);
        col.addView(uc);

        col.addView(Ui.sectionTitle(ctx, "维护"));
        LinearLayout cc = Ui.card(ctx);
        TextView restart = Ui.text(ctx, "重启桥接服务", 14, Ui.col(ctx, R.color.accent), true);
        restart.setPadding(0, Ui.dp(ctx, 4), 0, Ui.dp(ctx, 4));
        restart.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                confirm("重启 qq-bot？", "重启期间机器人会短暂下线", new Runnable() {
                    @Override public void run() { act("restart", new JSONObject()); }
                });
            }
        });
        cc.addView(restart);
        col.addView(cc);
    }

    private LinearLayout row(String title, String desc, View right) {
        LinearLayout r = Ui.hbox(ctx);
        r.setPadding(0, Ui.dp(ctx, 10), 0, Ui.dp(ctx, 10));
        LinearLayout l = Ui.vbox(ctx);
        l.addView(Ui.text(ctx, title, 14.5f, Ui.col(ctx, R.color.ink), false));
        l.addView(Ui.text(ctx, desc, 11.5f, Ui.col(ctx, R.color.muted), false));
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        r.addView(right);
        return r;
    }

    @Override public void refresh() {
        if (loading) return;
        loading = true;
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    final JSONObject o = Api.get(ctx).get("/api/bot");
                    final JSONObject qq = Api.get(ctx).get("/api/qq");
                    final boolean online = qq.optBoolean("online", false);
                    final int age = qq.optInt("age", -1);
                    android.graphics.Bitmap qr = null;
                    if (!online && age != lastQrAge) {
                        try {
                            byte[] bytes = Api.get(ctx).getBytes("/api/qq/qr.png");
                            android.graphics.Bitmap raw = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                            if (raw != null) {
                                qr = android.graphics.Bitmap.createScaledBitmap(raw, raw.getWidth() * 4, raw.getHeight() * 4, false);
                            }
                        } catch (Exception ignored) { }
                    }
                    final android.graphics.Bitmap qrBmp = qr;
                    h.post(new Runnable() { @Override public void run() {
                        renderQq(online, age, qrBmp);
                        render(o);
                        loading = false;
                    } });
                } catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override public void run() {
                            loading = false;
                            titleTv.setText("机器人 · 连接失败");
                        }
                    });
                }
            }
        });
    }

    private void render(JSONObject o) {
        try {
            JSONObject c = o.optJSONObject("config");
            if (c == null) return;
            titleTv.setText("机器人");
            swEnabled.setChecked(c.optBoolean("enabled"));
            swGroup.setChecked(c.optBoolean("replyInGroup"));
            model = c.optString("model", "deepseek-flash");
            thinking = c.optString("thinking", "default");
            if (personaInput.getTag() == null) {
                personaInput.setText(c.optString("persona", ""));
                personaInput.setTag("loaded");
            }

            // 模型
            modelRow.removeAllViews();
            String[] models = {"deepseek-flash", "deepseek-v4-pro"};
            for (final String m : models) {
                TextView chip = Ui.chip(ctx, m.equals("deepseek-flash") ? "flash" : "v4-pro", m.equals(model));
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            JSONObject b = new JSONObject();
                            b.put("model", m);
                            act("model", b);
                        } catch (Exception ignored) { }
                    }
                });
                modelRow.addView(chip);
            }

            // 思考档
            thinkRow.removeAllViews();
            String[][] th = {{"default", "默认"}, {"low", "低"}, {"off", "关闭"}};
            for (String[] t : th) {
                TextView chip = Ui.chip(ctx, t[1], t[0].equals(thinking));
                final String mode = t[0];
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override public void onClick(View v) {
                        try {
                            JSONObject b = new JSONObject();
                            b.put("mode", mode);
                            act("thinking", b);
                        } catch (Exception ignored) { }
                    }
                });
                thinkRow.addView(chip);
            }

            // 白名单
            wlBox.removeAllViews();
            JSONArray wl = c.optJSONArray("whitelist");
            if (wl == null || wl.length() == 0) {
                wlBox.addView(Ui.text(ctx, "未设置白名单（所有人可聊）", 12, Ui.col(ctx, R.color.muted), false));
            } else {
                for (int i = 0; i < wl.length(); i++) {
                    final String id = wl.optString(i);
                    if (i > 0) wlBox.addView(Ui.divider(ctx));
                    LinearLayout r = Ui.hbox(ctx);
                    r.setPadding(0, Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9));
                    r.addView(Ui.text(ctx, id, 13.5f, Ui.col(ctx, R.color.ink), false), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                    TextView del = Ui.text(ctx, "移除", 12, Ui.col(ctx, R.color.bad), false);
                    del.setOnClickListener(new View.OnClickListener() {
                        @Override public void onClick(View v) {
                            try {
                                JSONObject b = new JSONObject();
                                b.put("id", id);
                                act("whitelist/remove", b);
                            } catch (Exception ignored) { }
                        }
                    });
                    r.addView(del);
                    wlBox.addView(r);
                }
            }

            // 记忆
            JSONObject mem = o.optJSONObject("memory");
            memBox.removeAllViews();
            if (mem != null) {
                memSummaryTv.setText(mem.optInt("users") + " 个会话 · " + mem.optInt("messages") + " 条消息");
                JSONArray list = mem.optJSONArray("list");
                if (list != null) {
                    for (int i = 0; i < list.length(); i++) {
                        final JSONObject u = list.optJSONObject(i);
                        if (u == null) continue;
                        if (i > 0) memBox.addView(Ui.divider(ctx));
                        LinearLayout r = Ui.hbox(ctx);
                        r.setPadding(0, Ui.dp(ctx, 9), 0, Ui.dp(ctx, 9));
                        LinearLayout l = Ui.vbox(ctx);
                        l.addView(Ui.text(ctx, u.optString("uid"), 13.5f, Ui.col(ctx, R.color.ink), false));
                        l.addView(Ui.text(ctx, u.optInt("messages") + " 条 · " + ago(u.optLong("updatedAt")), 11, Ui.col(ctx, R.color.muted), false));
                        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
                        final String uid = u.optString("uid");
                        r.addView(Ui.text(ctx, "管理 ›", 12, Ui.col(ctx, R.color.muted), false));
                        r.setOnClickListener(new View.OnClickListener() {
                            @Override public void onClick(View v) { new MemDialog(ctx, uid).show(); }
                        });
                        memBox.addView(r);
                    }
                }
            }

            // 用量
            JSONObject usage = o.optJSONObject("usage");
            if (usage != null) {
                JSONObject t = usage.optJSONObject("today");
                JSONObject all = usage.optJSONObject("total");
                if (t != null && all != null) {
                    usageTv.setText("今日 " + t.optInt("calls") + " 次调用 · 输入 " + t.optInt("prompt") + " · 输出 " + t.optInt("completion") + " tokens\n"
                            + "累计 " + all.optInt("calls") + " 次调用 · 输入 " + all.optInt("prompt") + " · 输出 " + all.optInt("completion") + " tokens");
                }
            }
        } catch (Exception e) {
            titleTv.setText("渲染错误：" + e);
        }
    }

    private void renderQq(boolean online, int age, android.graphics.Bitmap qr) {
        if (online) {
            qqState.setText("QQ 在线 · 1619716221");
            qqState.setTextColor(Ui.col(ctx, R.color.ok));
            qrAge.setText("OneBot WS 3001 正常");
            qrView.setVisibility(View.GONE);
        } else {
            qqState.setText("QQ 未登录");
            qqState.setTextColor(Ui.col(ctx, R.color.bad));
            qrAge.setText(age >= 0 ? ("二维码生成于 " + age + " 秒前" + (age > 110 ? "（可能已过期，点下面刷新）" : "")) : "暂无二维码");
            qrView.setVisibility(View.VISIBLE);
            if (qr != null) {
                qrView.setImageBitmap(qr);
                lastQrAge = age;
            }
        }
    }

    private void toast(String s) { android.widget.Toast.makeText(ctx, s, android.widget.Toast.LENGTH_SHORT).show(); }

    private void confirm(String title, String msg, final Runnable ok) {
        new AlertDialog.Builder(ctx)
                .setTitle(title)
                .setMessage(msg)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                    @Override public void onClick(android.content.DialogInterface d, int w) { ok.run(); }
                })
                .show();
    }

    private void act(final String action, final JSONObject body) {
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    final JSONObject o = Api.get(ctx).post("/api/bot/" + action, body);
                    h.post(new Runnable() { @Override public void run() {
                        render(o);
                        toast(doneText(action));
                    } });
                } catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override public void run() {
                            toast("操作失败：" + e.getMessage());
                        }
                    });
                }
            }
        });
    }

    private String doneText(String a) {
        if (a.equals("persona")) return "人设已保存";
        if (a.equals("toggle")) return "已切换机器人开关";
        if (a.equals("group")) return "已切换群聊回复";
        if (a.equals("model")) return "模型已切换";
        if (a.equals("thinking")) return "思考档已切换";
        if (a.startsWith("whitelist/add")) return "已加入白名单";
        if (a.startsWith("whitelist/remove")) return "已移出白名单";
        if (a.startsWith("memory/")) return "操作完成";
        if (a.equals("restart")) return "已触发重启";
        return "已保存";
    }

    private String ago(long t) {
        if (t <= 0) return "从未";
        long s = (System.currentTimeMillis() - t) / 1000;
        if (s < 60) return "刚刚";
        if (s < 3600) return s / 60 + " 分钟前";
        if (s < 86400) return s / 3600 + " 小时前";
        return s / 86400 + " 天前";
    }
}