package xyz.saisqi.panel;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.PowerManager;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;

/** 采集本机（手机）实时状态，数据主要来自本机网关（root 进程）。 */
public final class DeviceStat {

    public static final class Info {
        public int cpu = -1;
        public String load = "—";
        public long memTotal, memAvail, diskTotal, diskFree;
        public int battery = -1;
        public float temp = -1;
        public boolean charging;
        public boolean screenOn, locked;
        public String net = "无网络";
        public boolean dsh;
        public long uptime;
    }

    private DeviceStat() {}

    private static JSONObject remote() {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL("http://127.0.0.1:3099/admin/device").openConnection();
            c.setConnectTimeout(500);
            c.setReadTimeout(900);
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] b = new byte[8192];
            int n;
            try (InputStream in = c.getInputStream()) {
                while ((n = in.read(b)) > 0) bo.write(b, 0, n);
            }
            c.disconnect();
            return new JSONObject(bo.toString("UTF-8"));
        } catch (Exception e) {
            return null;
        }
    }

    public static Info info(Context c) {
        Info i = new Info();
        JSONObject o = remote();
        if (o != null) {
            i.cpu = o.optInt("cpu", -1);
            i.load = o.optString("load", "—");
            i.memTotal = o.optLong("memTotal", 0);
            i.memAvail = o.optLong("memAvail", 0);
            i.diskTotal = o.optLong("diskTotal", 0);
            i.diskFree = o.optLong("diskFree", 0);
            i.battery = o.optInt("battery", -1);
            i.temp = (float) o.optDouble("temp", -1);
            String st = o.optString("charging", "");
            i.charging = "Charging".equals(st) || "Full".equals(st);
            i.dsh = o.optBoolean("dsh", false);
            i.uptime = o.optLong("uptime", 0);
        }
        PowerManager pm = (PowerManager) c.getSystemService(Context.POWER_SERVICE);
        KeyguardManager km = (KeyguardManager) c.getSystemService(Context.KEYGUARD_SERVICE);
        i.screenOn = pm != null && pm.isInteractive();
        i.locked = km != null && km.isKeyguardLocked();
        try {
            ConnectivityManager cm = (ConnectivityManager) c.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (cm != null) {
                NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
                if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) i.net = "WiFi";
                else if (nc != null && nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) i.net = "移动数据";
                else if (nc != null) i.net = "其他网络";
            }
        } catch (Exception ignored) {}
        if (i.battery < 0 || i.temp <= 0) {
            Intent bat = c.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (bat != null) {
                int lv = bat.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int sc = bat.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
                if (i.battery < 0 && lv >= 0 && sc > 0) i.battery = Math.round(lv * 100f / sc);
                int t = bat.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (i.temp <= 0 && t > 0) i.temp = t / 10f;
            }
        }
        if (!i.dsh) {
            try (Socket s = new Socket()) {
                s.connect(new InetSocketAddress("127.0.0.1", 3080), 200);
                i.dsh = true;
            } catch (Exception ignored) {}
        }
        return i;
    }
}
