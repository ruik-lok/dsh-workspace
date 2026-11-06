package xyz.saisqi.panel;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;

/** 单个会话的记忆明细：摘要可编辑、逐条可删、可压缩/清空。 */
public class MemDialog extends Dialog {

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final String uid;

    private final LinearLayout col;
    private final TextView metaTv;
    private final EditText summaryIn;
    private final LinearLayout listBox;
    private final LinearLayout actionBox;

    private String lastSummary = null;

    public MemDialog(Context c, String uid) {
        super(c, R.style.Theme_Panel);
        this.ctx = c;
        this.uid = uid;
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        int p = Ui.dp(ctx, 16);
        LinearLayout root = Ui.vbox(ctx);
        root.setBackgroundColor(Ui.col(ctx, R.color.bg));
        root.setPadding(p, Ui.dp(ctx, 14), p, Ui.dp(ctx, 8));

        LinearLayout head = Ui.hbox(ctx);
        head.addView(Ui.text(ctx, "记忆 · " + uid, 18, Ui.col(ctx, R.color.ink), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView close = Ui.text(ctx, "关闭", 13, Ui.col(ctx, R.color.accent), true);
        close.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 8), Ui.dp(ctx, 4), Ui.dp(ctx, 8));
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismiss(); }
        });
        head.addView(close);
        root.addView(head);

        metaTv = Ui.text(ctx, "加载中…", 11.5f, Ui.col(ctx, R.color.muted), false);
        root.addView(metaTv);

        ScrollView sv = new ScrollView(ctx);
        sv.setFillViewport(true);
        col = Ui.vbox(ctx);
        col.setPadding(0, Ui.dp(ctx, 2), 0, Ui.dp(ctx, 20));
        sv.addView(col, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        root.addView(sv, slp);

        col.addView(Ui.sectionTitle(ctx, "长期记忆摘要"));
        LinearLayout sc = Ui.card(ctx);
        summaryIn = new EditText(ctx);
        summaryIn.setTextSize(13.5f);
        summaryIn.setTextColor(Ui.col(ctx, R.color.ink));
        summaryIn.setHintTextColor(Ui.col(ctx, R.color.muted));
        summaryIn.setHint("这段会话沉淀下来的长期记忆");
        summaryIn.setMinLines(3);
        summaryIn.setMaxLines(8);
        summaryIn.setGravity(Gravity.TOP | Gravity.START);
        summaryIn.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        summaryIn.setBackground(Ui.round(ctx, Ui.col(ctx, R.color.surface2), Ui.col(ctx, R.color.line), 10));
        summaryIn.setPadding(Ui.dp(ctx, 12), Ui.dp(ctx, 10), Ui.dp(ctx, 12), Ui.dp(ctx, 10));
        sc.addView(summaryIn);
        TextView saveSum = Ui.text(ctx, "保存摘要", 13, Ui.col(ctx, R.color.accent), true);
        saveSum.setPadding(0, Ui.dp(ctx, 12), 0, Ui.dp(ctx, 2));
        saveSum.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    JSONObject b = new JSONObject();
                    b.put("uid", uid);
                    b.put("summary", summaryIn.getText().toString());
                    post("memory/summary", b, "摘要已保存");
                } catch (Exception ignored) { }
            }
        });
        sc.addView(saveSum);
        col.addView(sc);

        col.addView(Ui.sectionTitle(ctx, "对话记忆"));
        listBox = Ui.vbox(ctx);
        col.addView(listBox);
        actionBox = Ui.vbox(ctx);
        col.addView(actionBox);

        setContentView(root);
    }

    @Override protected void onStart() {
        super.onStart();
        Window w = getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT);
        }
        load();
    }

    private void load() {
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    final JSONObject d = Api.get(ctx).get("/api/bot/memory?uid=" + URLEncoder.encode(uid, "UTF-8"));
                    h.post(new Runnable() { @Override public void run() { render(d); } });
                } catch (final Exception e) {
                    h.post(new Runnable() { @Override public void run() {
                        metaTv.setText("加载失败：" + e.getMessage());
                    } });
                }
            }
        });
    }

    private void render(JSONObject d) {
        try {
            int total = d.optInt("total", 0);
            String summary = d.optString("summary", "");
            metaTv.setText(total + " 条消息 · 摘要 " + summary.length() + " 字 · 已压缩 " + d.optInt("compressions", 0) + " 次");
            if (!summaryIn.hasFocus() && (lastSummary == null || !lastSummary.equals(summary))) {
                summaryIn.setText(summary);
                lastSummary = summary;
            }

            listBox.removeAllViews();
            LinearLayout card = Ui.card(ctx);
            JSONArray arr = d.optJSONArray("messages");
            if (arr == null || arr.length() == 0) {
                card.addView(Ui.text(ctx, "还没有记忆", 12.5f, Ui.col(ctx, R.color.muted), false));
            } else {
                for (int k = 0; k < arr.length(); k++) {
                    final JSONObject m = arr.optJSONObject(k);
                    if (m == null) continue;
                    if (k > 0) card.addView(Ui.divider(ctx));
                    card.addView(msgRow(m));
                }
            }
            listBox.addView(card);

            actionBox.removeAllViews();
            actionBox.addView(Ui.sectionTitle(ctx, "操作"));
            LinearLayout ac = Ui.card(ctx);
            TextView compress = Ui.text(ctx, "压缩记忆（保留最近 12 条）", 13, Ui.col(ctx, R.color.accent), false);
            compress.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));
            compress.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    try {
                        JSONObject b = new JSONObject();
                        b.put("uid", uid);
                        post("memory/compress", b, "已压缩");
                    } catch (Exception ignored) { }
                }
            });
            ac.addView(compress);
            ac.addView(Ui.divider(ctx));
            TextView clear = Ui.text(ctx, "清空该会话记忆", 13, Ui.col(ctx, R.color.bad), false);
            clear.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));
            clear.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    new android.app.AlertDialog.Builder(ctx)
                            .setTitle("清空 " + uid + " 的记忆？")
                            .setMessage("该操作不可撤销")
                            .setNegativeButton("取消", null)
                            .setPositiveButton("确定", new android.content.DialogInterface.OnClickListener() {
                                @Override public void onClick(android.content.DialogInterface dd, int w) {
                                    try {
                                        JSONObject b = new JSONObject();
                                        b.put("uid", uid);
                                        post("memory/forget", b, "已清空");
                                    } catch (Exception ignored) { }
                                }
                            })
                            .show();
                }
            });
            ac.addView(clear);
            actionBox.addView(ac);
        } catch (Exception e) {
            metaTv.setText("渲染失败：" + e.getMessage());
        }
    }

    private View msgRow(final JSONObject m) {
        int idx = m.optInt("i", 0);
        String role = m.optString("role", "");
        boolean user = "user".equals(role);
        String content = m.optString("content", "");

        LinearLayout row = Ui.vbox(ctx);
        row.setPadding(0, Ui.dp(ctx, 8), 0, Ui.dp(ctx, 8));

        LinearLayout top = Ui.hbox(ctx);
        top.addView(Ui.text(ctx, "#" + (idx + 1) + " · " + (user ? "用户" : "助手"), 11, user ? Ui.col(ctx, R.color.accent) : Ui.col(ctx, R.color.muted), true),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView del = Ui.text(ctx, "删除", 11.5f, Ui.col(ctx, R.color.bad), false);
        del.setPadding(Ui.dp(ctx, 10), 0, 0, 0);
        del.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                try {
                    JSONObject b = new JSONObject();
                    b.put("uid", uid);
                    b.put("index", m.optInt("i", 0));
                    post("memory/delete", b, "已删除第 " + (m.optInt("i", 0) + 1) + " 条");
                } catch (Exception ignored) { }
            }
        });
        top.addView(del);
        row.addView(top);

        final TextView body = Ui.text(ctx, content, 13, Ui.col(ctx, R.color.ink), false);
        body.setMaxLines(4);
        body.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bp.topMargin = Ui.dp(ctx, 3);
        row.addView(body, bp);
        row.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                boolean expanded = body.getMaxLines() > 4;
                body.setMaxLines(expanded ? 4 : 1000);
                body.setEllipsize(expanded ? TextUtils.TruncateAt.END : null);
            }
        });
        return row;
    }

    private void post(final String path, JSONObject body, final String okMsg) {
        final JSONObject payload = body;
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    Api.get(ctx).post("/api/bot/" + path, payload);
                    h.post(new Runnable() { @Override public void run() {
                        android.widget.Toast.makeText(ctx, okMsg, android.widget.Toast.LENGTH_SHORT).show();
                        load();
                    } });
                } catch (final Exception e) {
                    h.post(new Runnable() { @Override public void run() {
                        android.widget.Toast.makeText(ctx, "失败：" + e.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                    } });
                }
            }
        });
    }
}