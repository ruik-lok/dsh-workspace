package xyz.saisqi.panel;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

public class LogView extends ScrollView implements TabView {

    private final Context ctx;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final TextView body;
    private boolean loading = false;

    public LogView(Context c) {
        super(c);
        ctx = c;
        setFillViewport(true);
        setBackgroundColor(Ui.col(c, R.color.bg));
        LinearLayout col = Ui.vbox(c);
        int p = Ui.dp(c, 16);
        col.setPadding(p, Ui.dp(c, 8), p, Ui.dp(c, 28));

        TextView title = Ui.text(c, "日志", 20, Ui.col(c, R.color.ink), true);
        col.addView(title);
        TextView sub = Ui.text(c, "qq-bot 最近输出 · 每 3 秒刷新", 12, Ui.col(c, R.color.muted), false);
        Ui.gap(sub, Ui.dp(c, 4), Ui.dp(c, 12));
        col.addView(sub);

        LinearLayout card = Ui.card(c);
        body = Ui.mono(c, "加载中…", 11.5f, Ui.col(c, R.color.ink));
        body.setLineSpacing(Ui.dp(c, 3), 1f);
        body.setTextIsSelectable(true);
        card.addView(body);
        col.addView(card);

        addView(col, new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    @Override public void refresh() {
        if (loading) return;
        loading = true;
        Net.run(new Runnable() {
            @Override public void run() {
                try {
                    final JSONObject o = Api.get(ctx).get("/api/bot");
                    h.post(new Runnable() {
                        @Override public void run() {
                            loading = false;
                            JSONArray a = o.optJSONArray("log");
                            if (a == null || a.length() == 0) { body.setText("暂无日志"); return; }
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < a.length(); i++) sb.append(a.optString(i)).append('\n');
                            body.setText(sb.toString());
                        }
                    });
                } catch (final Exception e) {
                    h.post(new Runnable() {
                        @Override public void run() {
                            loading = false;
                            body.setText("读取失败：" + e.getMessage());
                        }
                    });
                }
            }
        });
    }
}