import http from "node:http";
import net from "node:net";
import fs from "node:fs";
import { execFile } from "node:child_process";
import os from "node:os";

const LOG = "/data/user/0/com.deepseek.harness/files/dsh-web.log";
const TARGET = { host: "127.0.0.1", port: 3080 };
const PREFIX = "/dsh";
const ADMIN_KEY = "***已打码***";
let cpuPrev = null;
let netPrev = null;

function readText(p) {
  try { return fs.readFileSync(p, "utf8"); } catch { return ""; }
}

function cpuPercent() {
  const line = readText("/proc/stat").split("\n")[0];
  if (!line) return -1;
  const p = line.trim().split(/\s+/);
  const idle = Number(p[4] || 0) + Number(p[5] || 0);
  let total = 0;
  for (let i = 1; i <= 8; i++) total += Number(p[i] || 0);
  let pct = -1;
  if (cpuPrev) {
    const dt = total - cpuPrev.total, di = idle - cpuPrev.idle;
    if (dt > 0) pct = Math.round((dt - di) * 100 / dt);
  }
  cpuPrev = { idle, total: total };
  return pct;
}

function memInfo() {
  const t = readText("/proc/meminfo");
  const get = (k) => { const m = t.match(new RegExp(k + ":\\s+(\\d+)")); return m ? Number(m[1]) * 1024 : -1; };
  return { total: get("MemTotal"), avail: get("MemAvailable") };
}
const PKG = "com.deepseek.harness";

function restartDsh(reason) {
  log("restart requested:", reason);
  execFile("am", ["force-stop", PKG], () => {
    setTimeout(() => {
      execFile("am", ["start", "-n", PKG + "/.MainActivity"], (e) => log("restart done", e ? e.message : "ok"));
    }, 1500);
  });
}
const log = (...a) => console.log(new Date().toISOString(), ...a);

let cached = { token: "", at: 0 };

function candidates() {
  try {
    const lines = fs.readFileSync(LOG, "utf8").split("\n").slice(-400);
    const out = [];
    for (let i = lines.length - 1; i >= 0; i--) {
      const m = lines[i].match(/[?&]token=([A-Za-z0-9_-]{20,})/);
      if (m && !out.includes(m[1])) out.push(m[1]);
      if (out.length >= 5) break;
    }
    return out;
  } catch { return []; }
}

function verify(token) {
  return new Promise((resolve) => {
    const req = http.request({ host: TARGET.host, port: TARGET.port, path: "/?token=" + token, method: "GET", timeout: 3000 }, (res) => {
      res.resume();
      resolve(res.statusCode === 303 || res.statusCode === 302 || res.statusCode === 200);
    });
    req.on("error", () => resolve(false));
    req.on("timeout", () => { req.destroy(); resolve(false); });
    req.end();
  });
}

async function currentToken() {
  if (cached.token && Date.now() - cached.at < 60000) return cached.token;
  for (const t of candidates()) {
    if (await verify(t)) { cached = { token: t, at: Date.now() }; return t; }
  }
  return "";
}

// 老 Chromium（ColorOS/HeyTap 40、Android System WebView 旧版）缺这两个 API，
// 缺了客户端会在建连阶段抛错，表现就是一直卡在「重新连接」。
const POLYFILL = '<script>(function(){' +
  'if(!Promise.withResolvers){Promise.withResolvers=function(){var r,j;var p=new Promise(function(a,b){r=a;j=b});return{promise:p,resolve:r,reject:j};};}' +
  'if(!AbortSignal.any){AbortSignal.any=function(s){var c=new AbortController();function f(){try{c.abort();}catch(e){}}for(var i=0;i<s.length;i++){if(s[i].aborted){f();break;}s[i].addEventListener("abort",f,{once:true});}return c.signal;};}' +
  '})();</script>';

function injectHtml(html) {
  const i = html.indexOf("</head>");
  if (i < 0) return POLYFILL + html;
  return html.slice(0, i) + POLYFILL + html.slice(i);
}

function proxy(req, res) {
  const headers = { ...req.headers, host: "127.0.0.1:3080", origin: "http://127.0.0.1:3080" };
  headers["accept-encoding"] = "identity";
  delete headers["x-forwarded-host"];
  const up = http.request({ host: TARGET.host, port: TARGET.port, path: req.url, method: req.method, headers }, (ur) => {
    const ct = String(ur.headers["content-type"] || "");
    if (ct.includes("text/html") && ur.statusCode === 200) {
      const chunks = [];
      ur.on("data", (c) => chunks.push(c));
      ur.on("end", () => {
        const body = Buffer.from(injectHtml(Buffer.concat(chunks).toString("utf8")), "utf8");
        const h = { ...ur.headers };
        delete h["content-length"];
        delete h["transfer-encoding"];
        h["content-length"] = String(body.length);
        res.writeHead(200, h);
        res.end(body);
      });
      return;
    }
    res.writeHead(ur.statusCode || 502, ur.headers);
    ur.pipe(res);
  });
  up.on("error", () => { try { res.writeHead(502); res.end("upstream error"); } catch {} });
  req.pipe(up);
}

// 本机直连（127.0.0.1:3099/dsh/...）时把 /dsh 前缀剥掉，外部走 nginx 时 nginx 已经剥过，这里相当于兼容两种入口。
function localPath(url) {
  if (url === "/dsh") return "/";
  if (url.startsWith("/dsh/") || url.startsWith("/dsh?")) return url.slice(4);
  return url;
}

const server = http.createServer(async (req, res) => {
  req.url = localPath(req.url || "/");
  const u = new URL(req.url, "http://x");
  if (u.pathname === "/admin/health") {
    res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
    return res.end(JSON.stringify({ ok: true, at: Date.now() }));
  }
  if (u.pathname === "/admin/device") {
    const load = readText("/proc/loadavg").trim().split(/\s+/)[0] || "";
    const mem = memInfo();
    const bat = readText("/sys/class/power_supply/battery/capacity").trim();
    const bt = readText("/sys/class/power_supply/battery/temp").trim();
    const status = readText("/sys/class/power_supply/battery/status").trim();
    const up = Number((readText("/proc/uptime").trim().split(/\s+/)[0] || "0"));
    let disk = { total: -1, free: -1 };
    try {
      const s = fs.statfsSync("/data");
      disk = { total: s.blocks * s.bsize, free: s.bfree * s.bsize };
    } catch {}
    const netDev = readText("/proc/net/dev").split("\n").slice(2);
    let rx = 0, tx = 0;
    for (const line of netDev) {
      const p = line.trim().split(/\s+/);
      if (p.length < 10 || p[0].startsWith("lo")) continue;
      rx += Number(p[1] || 0);
      tx += Number(p[9] || 0);
    }
    const now = Date.now();
    let rxRate = 0, txRate = 0;
    if (netPrev) {
      const dt = (now - netPrev.at) / 1000;
      if (dt > 0) { rxRate = Math.max(0, (rx - netPrev.rx) / dt); txRate = Math.max(0, (tx - netPrev.tx) / dt); }
    }
    netPrev = { rx, tx, at: now };
    const dshUp = await new Promise((resolve) => {
      const s = net.connect(3080, "127.0.0.1");
      s.setTimeout(300);
      s.on("connect", () => { s.destroy(); resolve(true); });
      s.on("timeout", () => { s.destroy(); resolve(false); });
      s.on("error", () => resolve(false));
    });
    res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
    return res.end(JSON.stringify({
      cpu: cpuPercent(),
      load,
      cores: os.cpus().length,
      memTotal: mem.total,
      memAvail: mem.avail,
      diskTotal: disk.total,
      diskFree: disk.free,
      battery: bat ? Number(bat) : -1,
      temp: bt ? Number(bt) / 10 : -1,
      charging: status,
      uptime: up,
      netRx: rxRate,
      netTx: txRate,
      dsh: dshUp,
      at: now
    }));
  }
  if (u.pathname === "/admin/restart") {
    if (u.searchParams.get("key") !== ADMIN_KEY) {
      res.writeHead(403, { "content-type": "text/plain" });
      return res.end("forbidden");
    }
    res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
    res.end(JSON.stringify({ ok: true, restarting: true }));
    restartDsh(req.headers["user-agent"] || "unknown");
    return;
  }
  const path = (req.url || "/").split("?")[0];
  if (path === "/go" || path === "/go/") {
    const t = await currentToken();
    if (!t) { res.writeHead(503, { "content-type": "text/plain; charset=utf-8", "cache-control": "no-store" }); res.end("token 未就绪（harness 可能正在启动）"); return; }
    res.writeHead(302, { location: PREFIX + "/?token=" + t, "cache-control": "no-store" });
    res.end();
    return;
  }
  proxy(req, res);
});

server.on("upgrade", (req, socket, head) => {
  req.url = localPath(req.url || "/");
  const isMux = String(req.url || "").includes("remote.mux");
  const t0 = Date.now();
  let inBytes = 0, outBytes = 0, firstUp = null;
  if (isMux) log("mux OPEN", req.url, "proto=" + (req.headers["sec-websocket-protocol"] || "-"), "ua=" + String(req.headers["user-agent"] || "").slice(0, 40));

  const up = net.connect(TARGET.port, TARGET.host, () => {
    const h = { ...req.headers, host: "127.0.0.1:3080", origin: "http://127.0.0.1:3080" };
    delete h["x-forwarded-host"];
    let raw = req.method + " " + req.url + " HTTP/1.1\r\n";
    for (const [k, v] of Object.entries(h)) raw += k + ": " + v + "\r\n";
    raw += "\r\n";
    up.write(raw);
    if (head && head.length) up.write(head);
    up.pipe(socket);
    socket.pipe(up);
  });
  up.on("data", (d) => {
    outBytes += d.length;
    if (isMux && outBytes <= 1400 && d.length) {
      log("mux S", d.length, d.toString("hex").slice(0, 160), JSON.stringify(d.toString("latin1")).slice(0, 120));
    }
  });
  up.on("close", () => { if (isMux) log("mux UPSTREAM CLOSED", "after " + (Date.now() - t0) + "ms", "in=" + inBytes, "out=" + outBytes); });
  up.on("error", (e) => { if (isMux) log("mux upstream error", e.message); socket.destroy(); });
  socket.on("data", (d) => { inBytes += d.length; if (isMux && inBytes <= 600) log("mux C", d.length, d.toString("hex").slice(0, 160)); });
  socket.on("close", () => { if (isMux) log("mux CLIENT CLOSED", "after " + (Date.now() - t0) + "ms", "in=" + inBytes, "out=" + outBytes); });
  socket.on("error", () => up.destroy());
});

server.listen(3099, "127.0.0.1", () => log("gw on 3099 -> 3080"));