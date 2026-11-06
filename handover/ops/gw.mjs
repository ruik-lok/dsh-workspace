import http from "node:http";
import net from "node:net";
import fs from "node:fs";
import { execFile } from "node:child_process";
import os from "node:os";

let lastProxyAt = Date.now();

const LOG = "/data/user/0/com.deepseek.harness/files/dsh-web.log";
const TARGET = { host: "127.0.0.1", port: 3080 };
const PREFIX = "/dsh";
const ADMIN_KEY = "dsh-restart-7f3a91";
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

let restartState = { phase: "idle", startedAt: 0, attempts: 0, lastError: "", ok: false };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

function runCmd(cmd) {
  return new Promise((resolve) => {
    execFile("sh", ["-c", cmd], { timeout: 20000 }, (e, out, err) => {
      resolve({ code: e ? (e.code ?? -1) : 0, out: String(out || ""), err: String(err || "") });
    });
  });
}

function portUp(port) {
  return new Promise((resolve) => {
    const s = net.connect(port, "127.0.0.1");
    s.setTimeout(400);
    s.on("connect", () => { s.destroy(); resolve(true); });
    s.on("timeout", () => { s.destroy(); resolve(false); });
    s.on("error", () => resolve(false));
  });
}

async function startDsh(reason) {
  restartState = { phase: "starting", startedAt: Date.now(), attempts: 0, lastError: "", ok: false };
  log("start begin:", reason);
  await runCmd("rm -f /data/local/tmp/dsh-stopped");
  const launchers = [
    "am start -n " + PKG + "/.MainActivity --user 0",
    "monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1",
    "am start -n " + PKG + "/.MainActivity --user 0 --activity-clear-top",
    "monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1"
  ];
  for (let i = 1; i <= launchers.length; i++) {
    restartState.attempts = i;
    const a = await runCmd(launchers[i - 1]);
    log("start #" + i, "code=" + a.code);
    for (let t = 0; t < 10; t++) {
      await sleep(2000);
      if (await portUp(3080)) {
        restartState.phase = "running"; restartState.ok = true;
        log("start ok after", (Date.now() - restartState.startedAt) + "ms");
        return;
      }
    }
    restartState.lastError = "第 " + i + " 次启动后 3080 仍未就绪";
  }
  restartState.phase = "failed";
  log("start failed:", restartState.lastError);
}

async function stopDsh(reason) {
  restartState = { phase: "stopping", startedAt: Date.now(), attempts: 1, lastError: "", ok: false };
  log("stop begin:", reason);
  await runCmd("touch /data/local/tmp/dsh-stopped");
  await runCmd("am force-stop " + PKG);
  await sleep(1500);
  const up = await portUp(3080);
  restartState.phase = up ? "failed" : "stopped";
  restartState.ok = !up;
  if (up) restartState.lastError = "强制停止后 3080 仍在监听";
  log("stop done, up=" + up);
}

async function doRestart(reason) {
  restartState = { phase: "stopping", startedAt: Date.now(), attempts: 0, lastError: "", ok: false };
  log("restart begin:", reason);
  await runCmd("rm -f /data/local/tmp/dsh-stopped");
  await runCmd("am force-stop " + PKG);
  await sleep(3000);
  const launchers = [
    "am start -n " + PKG + "/.MainActivity --user 0",
    "monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1",
    "am start -n " + PKG + "/.MainActivity --user 0 --activity-clear-top",
    "monkey -p " + PKG + " -c android.intent.category.LAUNCHER 1"
  ];
  for (let i = 1; i <= launchers.length; i++) {
    restartState.attempts = i;
    restartState.phase = "starting";
    const a = await runCmd(launchers[i - 1]);
    log("launch #" + i, launchers[i - 1].slice(0, 40), "code=" + a.code, (a.err || "").slice(0, 100));
    for (let t = 0; t < 10; t++) {
      await sleep(2000);
      if (await portUp(3080)) {
        restartState.phase = "running";
        restartState.ok = true;
        log("restart ok after", (Date.now() - restartState.startedAt) + "ms");
        return;
      }
    }
    restartState.lastError = "第 " + i + " 次启动后 3080 仍未就绪";
  }
  restartState.phase = "failed";
  log("restart failed:", restartState.lastError);
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
  lastProxyAt = Date.now();
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
      loadPerCore: (() => { const l = Number(load); const c = os.cpus().length; return l > 0 && c > 0 ? Math.round(l / c * 10) / 10 : 0; })(),
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
      idleSec: Math.round((Date.now() - lastProxyAt) / 1000),
      at: now
    }));
  }
  if (u.pathname === "/admin/start" || u.pathname === "/admin/stop" || u.pathname === "/admin/restart") {
    if (u.searchParams.get("key") !== ADMIN_KEY) {
      res.writeHead(403, { "content-type": "text/plain" });
      return res.end("forbidden");
    }
    const action = u.pathname.split("/").pop();
    res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
    res.end(JSON.stringify({ ok: true, action }));
    if (action === "start") startDsh(req.headers["user-agent"] || "api");
    else if (action === "stop") stopDsh(req.headers["user-agent"] || "api");
    else doRestart(req.headers["user-agent"] || "api");
    return;
  }
  if (false) {
    if (u.searchParams.get("key") !== ADMIN_KEY) {
      res.writeHead(403, { "content-type": "text/plain" });
      return res.end("forbidden");
    }
    res.writeHead(200, { "content-type": "application/json", "cache-control": "no-store" });
    res.end(JSON.stringify({ ok: true, restarting: true }));
    doRestart(req.headers["user-agent"] || "unknown");
    return;
  }
  if (u.pathname === "/admin/restart/status") {
    res.writeHead(200, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
    return res.end(JSON.stringify(restartState));
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
  lastProxyAt = Date.now();
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