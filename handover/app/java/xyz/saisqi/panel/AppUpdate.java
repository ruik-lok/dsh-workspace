package xyz.saisqi.panel;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import org.json.JSONObject;

/** 应用内更新：向 status-api 查最新版本，有新版就提示下载安装。 */
public final class AppUpdate {

    /** 当前版本；改版本号时和 AndroidManifest.xml / build.sh 一起改。 */
    public static final int VERSION_CODE = 15;
    public static final String VERSION_NAME = "3.5";

    private AppUpdate() {}

    public static void check(final Context ctx, final boolean manual) {
        final Handler h = new Handler(Looper.getMainLooper());
        Net.run(new Runnable() {
            @Override public void run() {
                final JSONObject o;
                try {
                    o = Api.get(ctx).get("/api/app");
                } catch (final Exception e) {
                    if (manual) {
                        h.post(new Runnable() { @Override public void run() {
                            Toast.makeText(ctx, "检查更新失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
                        }});
                    }
                    return;
                }
                h.post(new Runnable() { @Override public void run() { show(ctx, o, manual); } });
            }
        });
    }

    private static void show(final Context ctx, JSONObject o, boolean manual) {
        final int code = o.optInt("versionCode", 0);
        final String name = o.optString("versionName", "");
        final String apk = o.optString("apk", "https://saisqi.xyz/g");
        final String notes = o.optString("notes", "");
        if (code <= VERSION_CODE) {
            if (manual) Toast.makeText(ctx, "已是最新版本 v" + VERSION_NAME, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(ctx)
                .setTitle("发现新版本 v" + name)
                .setMessage(notes.isEmpty() ? "是否现在更新？" : notes)
                .setNegativeButton("以后再说", null)
                .setPositiveButton("立即更新", new DialogInterface.OnClickListener() {
                    @Override public void onClick(DialogInterface d, int w) {
                        try {
                            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(apk));
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ctx.startActivity(i);
                        } catch (Exception e) {
                            Toast.makeText(ctx, "打不开下载页：" + apk, Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .show();
    }
}
