package xyz.saisqi.panel;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** 与 status-api 通信：cookie 鉴权（panel_sid），REST。 */
public class Api {
    private static Api inst;
    private final SharedPreferences prefs;
    private String base;
    private String cookie;

    public static synchronized Api get(Context c) {
        if (inst == null) inst = new Api(c.getApplicationContext());
        return inst;
    }

    private Api(Context c) {
        prefs = c.getSharedPreferences("panel", Context.MODE_PRIVATE);
        base = prefs.getString("base", "https://dsh.saisqi.xyz/status");
        cookie = prefs.getString("cookie", null);
    }

    public String base() { return base; }

    public void setBase(String b) {
        base = b.trim().replaceAll("/+$", "");
        prefs.edit().putString("base", base).apply();
    }

    public boolean loggedIn() { return cookie != null && cookie.length() > 0; }

    public void logout() { cookie = null; prefs.edit().remove("cookie").apply(); }

    private HttpURLConnection open(String path, String method) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(base + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("User-Agent", "PanelApp/2.0");
        if (cookie != null) c.setRequestProperty("Cookie", cookie);
        return c;
    }

    private static String read(InputStream in) throws Exception {
        if (in == null) return "";
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = r.readLine()) != null) sb.append(line).append('\n');
        r.close();
        return sb.toString();
    }

    public boolean login(String pass) throws Exception {
        HttpURLConnection c = open("/login", "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        OutputStream os = c.getOutputStream();
        os.write(("password=" + URLEncoder.encode(pass, "UTF-8")).getBytes(StandardCharsets.UTF_8));
        os.close();
        c.getResponseCode();
        Map<String, List<String>> hs = c.getHeaderFields();
        for (Map.Entry<String, List<String>> e : hs.entrySet()) {
            if (e.getKey() != null && "Set-Cookie".equalsIgnoreCase(e.getKey())
                    && e.getValue() != null && !e.getValue().isEmpty()) {
                String kv = e.getValue().get(0).split(";")[0];
                if (kv.startsWith("panel_sid=")) {
                    cookie = kv;
                    prefs.edit().putString("cookie", cookie).apply();
                    return true;
                }
            }
        }
        return false;
    }

    public JSONObject get(String path) throws Exception {
        HttpURLConnection c = open(path, "GET");
        int code = c.getResponseCode();
        String body = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code == 401) throw new AuthException();
        if (code >= 400) throw new Exception("HTTP " + code);
        return new JSONObject(body);
    }

    public byte[] getBytes(String path) throws Exception {
        HttpURLConnection c = open(path, "GET");
        int code = c.getResponseCode();
        if (code == 401) throw new AuthException();
        if (code >= 400) throw new Exception("HTTP " + code);
        InputStream in = c.getInputStream();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) bos.write(buf, 0, n);
        in.close();
        return bos.toByteArray();
    }

    public JSONObject post(String path, JSONObject body) throws Exception {
        HttpURLConnection c = open(path, "POST");
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        byte[] data = body.toString().getBytes(StandardCharsets.UTF_8);
        c.setFixedLengthStreamingMode(data.length);
        OutputStream os = c.getOutputStream();
        os.write(data);
        os.close();
        int code = c.getResponseCode();
        String text = read(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code == 401) throw new AuthException();
        if (code >= 400) throw new Exception("HTTP " + code + " " + text);
        return text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    public static class AuthException extends Exception { }
}