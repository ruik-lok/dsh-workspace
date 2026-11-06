// 服务状态 + QQ 机器人管理 · 数据与接口后端
import http from 'node:http';
import https from 'node:https';
import os from 'node:os';
import crypto from 'node:crypto';
import { exec } from 'node:child_process';
import { promisify } from 'node:util';
import { readFileSync, writeFileSync, readdirSync, existsSync, statfsSync, statSync, unlinkSync } from 'node:fs';

const execP = promisify(exec);
const PORT = 8097;
const DOMAINS = ['saisqi.xyz', 'www.saisqi.xyz', 'bt.saisqi.xyz', 'ai.saisqi.xyz', 'dsh.saisqi.xyz'];
const BOT_CFG = '/root/qq-bot/config.json';
const BOT_LOG = '/var/log/qq-bot.log';
const MEM_DIR = '/root/qq-bot/memory';
const USAGE_PATH = '/root/qq-bot/usage.json';
const MODELS = ['deepseek-flash', 'deepseek-v4-pro'];
const PASS_FILE = '/root/qq-bot/admin-password.txt';
const QQ_QR = '/root/Napcat/opt/QQ/resources/app/app_launcher/napcat/cache/qrcode.png';
const COOKIE_NAME = 'panel_sid';
function panelPass() { try { return readFileSync(PASS_FILE, 'utf8').trim(); } catch { return 'admin'; } }
function sessionToken() { return crypto.createHash('sha256').update('panel-v1|' + panelPass()).digest('hex'); }
function parseCookies(req) {
  const h = req.headers.cookie || '';
  const out = {};
  for (const part of h.split(';')) {
    const i = part.indexOf('=');
    if (i > 0) out[part.slice(0, i).trim()] = decodeURIComponent(part.slice(i + 1).trim());
  }
  return out;
}
function authed(req) { return parseCookies(req)[COOKIE_NAME] === sessionToken(); }

let cpuPrev = snapCpu();
const hist = { cpu: [], mem: [] };
let domainCache = { at: 0, data: [] };
const domainCacheTtl = 120000;

function snapCpu() {
  const c = os.cpus();
  let idle = 0, total = 0;
  for (const x of c) {
    for (const k in x.times) total += x.times[k];
    idle += x.times.idle;
  }
  return { idle, total };
}
function cpuPercent() {
  const cur = snapCpu();
  const dIdle = cur.idle - cpuPrev.idle;
  const dTotal = cur.total - cpuPrev.total;
  cpuPrev = cur;
  return dTotal > 0 ? Math.max(0, Math.min(100, 100 * (1 - dIdle / dTotal))) : 0;
}
function memInfo() {
  const m = readFileSync('/proc/meminfo', 'utf8');
  const get = (k) => Number((m.match(new RegExp(k + ':\\s+(\\d+)')) || [])[1] || 0) / 1024 / 1024;
  const total = get('MemTotal'), avail = get('MemAvailable');
  return { total, used: total - avail, pct: total ? ((total - avail) / total) * 100 : 0 };
}
function netStats() {
  try {
    const l = readFileSync('/proc/net/dev', 'utf8').split('\n').find((x) => x.trim().startsWith('eth0'));
    if (!l) return null;
    const p = l.split(':')[1].trim().split(/\s+/).map(Number);
    return { rx: p[0], tx: p[8] };
  } catch { return null; }
}
async function run(cmd) {
  try { return (await execP(cmd, { timeout: 5000 })).stdout.trim(); } catch (e) { return (e.stdout || '').trim(); }
}

async function services() {
  const names = ['dsh-web', 'qq-bot', 'nginx', 'firewalld', 'status-api'];
  const out = [];
  for (const n of names) {
    const st = await run(`systemctl is-active ${n} 2>/dev/null`);
    let ageSec = null;
    if (st === 'active') {
      const ts = await run(`systemctl show -p ActiveEnterTimestamp --value ${n} 2>/dev/null`);
      if (ts) {
        const ep = await run(`date -d '${ts}' +%s 2>/dev/null`);
        const n2 = Number(ep);
        if (n2 > 0) ageSec = Math.max(0, Math.round(Date.now() / 1000 - n2));
      }
    }
    out.push({ name: n, active: st === 'active', state: st || 'unknown', ageSec });
  }
  const listen = await run('ss -tln 2>/dev/null');
  const has = (p) => new RegExp(':' + p + '\\s').test(listen);
  out.push({ name: 'napcat', active: has(3001), state: has(3001) ? 'listening' : 'down', port: 3001 });
  out.push({ name: 'dsh', active: has(8788), state: has(8788) ? 'listening' : 'down', port: 8788 });
  return out;
}

function memoryStats() {
  const out = { users: 0, messages: 0, summaryChars: 0, lastAt: 0, list: [] };
  try {
    for (const f of readdirSync(MEM_DIR)) {
      if (!f.endsWith('.json')) continue;
      const m = JSON.parse(readFileSync(MEM_DIR + '/' + f, 'utf8'));
      out.users++;
      out.messages += (m.messages || []).length;
      out.summaryChars += (m.summary || '').length;
      if ((m.updatedAt || 0) > out.lastAt) out.lastAt = m.updatedAt || 0;
      out.list.push({ uid: f.replace('.json', ''), messages: (m.messages || []).length, chars: (m.summary || '').length, updatedAt: m.updatedAt || 0 });
    }
    out.list.sort((a, b) => b.updatedAt - a.updatedAt);
  } catch {}
  return out;
}

function readBot() {
  const c = JSON.parse(readFileSync(BOT_CFG, 'utf8'));
  return {
    enabled: !!c.enabled,
    persona: c.persona || '',
    whitelist: (c.whitelist || []).map(String),
    replyInGroup: !!c.replyInGroup,
    model: c.model || 'deepseek-flash',
    thinking: c.thinking || 'default',
    maxRawMessages: c.maxRawMessages ?? 200,
    keepRecentMessages: c.keepRecentMessages ?? 40,
    models: MODELS,
  };
}
function writeBot(mutate) {
  const c = JSON.parse(readFileSync(BOT_CFG, 'utf8'));
  mutate(c);
  const mode = statSync(BOT_CFG).mode & 0o777;
  writeFileSync(BOT_CFG, JSON.stringify(c, null, 2), { mode });
  try { execP('chmod ' + mode.toString(8) + ' ' + BOT_CFG); } catch {}
}
function usageStats() {
  try {
    const u = JSON.parse(readFileSync(USAGE_PATH, 'utf8'));
    const k = new Date().toISOString().slice(0, 10);
    const d = (u.days || {})[k] || { calls: 0, prompt: 0, completion: 0, reasoning: 0, ms: 0 };
    return { today: d, total: u.total || { calls: 0, prompt: 0, completion: 0, reasoning: 0, ms: 0 } };
  } catch { return { today: { calls: 0, prompt: 0, completion: 0, reasoning: 0, ms: 0 }, total: { calls: 0, prompt: 0, completion: 0, reasoning: 0, ms: 0 } }; }
}

function memFile(uid) { return MEM_DIR + '/' + uid + '.json'; }

function memDetail(uid) {
  const m = JSON.parse(readFileSync(memFile(uid), 'utf8'));
  const msgs = m.messages || [];
  return {
    uid,
    summary: m.summary || '',
    messages: msgs.map((x, i) => ({ i, role: x.role, content: x.content })),
    total: msgs.length,
    compressions: m.compressions || 0,
    updatedAt: m.updatedAt || 0,
  };
}

function dsRequest(payload) {
  return new Promise((resolve, reject) => {
    const body = JSON.stringify(payload);
    const req = https.request({
      hostname: 'api.deepseek.com', path: '/chat/completions', method: 'POST', timeout: 90000,
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(body),
        Authorization: 'Bearer ' + JSON.parse(readFileSync(BOT_CFG, 'utf8')).deepseekKey,
      },
    }, (res) => {
      let buf = '';
      res.setEncoding('utf8');
      res.on('data', (d) => { buf += d; });
      res.on('end', () => resolve({ status: res.statusCode, body: buf }));
    });
    req.on('timeout', () => req.destroy(new Error('timeout')));
    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

async function compressUser(uid, keepRecent = 12) {
  const f = memFile(uid);
  const m = JSON.parse(readFileSync(f, 'utf8'));
  const msgs = m.messages || [];
  const cut = msgs.length - keepRecent;
  if (cut <= 0) return { ok: false, error: '消息太少，不需要压缩' };

  const older = msgs.slice(0, cut);
  const limit = 800;
  const lines = older.map((x) => (x.role === 'user' ? '用户' : '助手') + '：' + x.content).join('\n');
  const input = (m.summary ? '【已有摘要】\n' + m.summary + '\n\n' : '') + '【新增对话】\n' + lines;

  const c = JSON.parse(readFileSync(BOT_CFG, 'utf8'));
  const res = await dsRequest({
    model: c.model || 'deepseek-flash',
    messages: [
      { role: 'system', content: '你是一个对话记忆压缩器。把对话压缩成简洁、准确的长期记忆摘要，只输出摘要正文。' },
      { role: 'user', content: `把下面的对话合并成不超过 ${limit} 字的中文摘要。保留：称呼/身份、偏好、重要事实、约定、未完成事项。不要编造。\n\n` + input },
    ],
    temperature: 0.2,
    max_tokens: 1600,
  });
  if (res.status !== 200) return { ok: false, error: 'API ' + res.status };
  const j = JSON.parse(res.body);
  const summary = (j.choices?.[0]?.message?.content || '').trim();
  if (!summary) return { ok: false, error: '摘要为空' };

  m.summary = summary;
  m.messages = msgs.slice(cut);
  m.compressions = (m.compressions || 0) + 1;
  m.updatedAt = Date.now();
  writeFileSync(f, JSON.stringify(m), { mode: 0o600 });
  try { execP('chmod 600 ' + f); } catch {}
  return { ok: true, summary, kept: m.messages.length, removed: cut };
}

function botLog(n = 25) {
  try { return readFileSync(BOT_LOG, 'utf8').split('\n').filter(Boolean).slice(-n); } catch { return []; }
}

function checkDomain(domain) {
  return new Promise((resolve) => {
    const req = https.get({
      host: '127.0.0.1', port: 443, path: '/', servername: domain, timeout: 4000,
      headers: { 'User-Agent': 'status-probe', Host: domain },
    }, (res) => {
      const cert = res.socket.getPeerCertificate();
      res.resume();
      resolve({
        domain, ok: res.statusCode < 500, status: res.statusCode,
        certDays: cert.valid_to ? Math.round((new Date(cert.valid_to) - Date.now()) / 86400000) : null,
        error: null,
      });
    });
    req.on('timeout', () => { req.destroy(); resolve({ domain, ok: false, status: 0, certDays: null, error: 'timeout' }); });
    req.on('error', (e) => resolve({ domain, ok: false, status: 0, certDays: null, error: e.code || 'error' }));
  });
}
async function domains() {
  if (Date.now() - domainCache.at < domainCacheTtl) return domainCache.data;
  const data = await Promise.all(DOMAINS.map(checkDomain));
  domainCache = { at: Date.now(), data };
  return data;
}
function disk() {
  const s = statfsSync('/');
  const total = s.blocks * s.bsize, free = s.bavail * s.bsize;
  return { totalGB: total / 1e9, usedGB: (total - free) / 1e9, pct: ((total - free) / total) * 100 };
}

async function snapshot() {
  const cpu = cpuPercent();
  const mem = memInfo();
  hist.cpu.push(cpu); if (hist.cpu.length > 60) hist.cpu.shift();
  hist.mem.push(mem.pct); if (hist.mem.length > 60) hist.mem.shift();
  return {
    at: Date.now(), host: os.hostname(), uptime: os.uptime(), load: os.loadavg(), cores: os.cpus().length,
    cpu, mem, disk: disk(), net: netStats(),
    services: await services(),
    memory: memoryStats(),
    usage: usageStats(),
    domains: await domains(),
    history: { cpu: hist.cpu.slice(), mem: hist.mem.slice() },
  };
}

let cache = { at: 0, data: null };
async function getStatus() {
  if (cache.data && Date.now() - cache.at < 3000) return cache.data;
  cache = { at: Date.now(), data: await snapshot() };
  return cache.data;
}

const INDEX_PATH = '/root/status/index.html';
const STATIC = {
  '/manifest.webmanifest': ['application/manifest+json; charset=utf-8', '/root/status/manifest.webmanifest'],
  '/sw.js': ['text/javascript; charset=utf-8', '/root/status/sw.js'],
  '/icon-192.png': ['image/png', '/root/status/icon-192.png'],
  '/icon-512.png': ['image/png', '/root/status/icon-512.png'],
};

function json(res, code, obj) {
  res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(JSON.stringify(obj));
}
const LOGIN_PAGE = `<!doctype html><html lang="zh-CN"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>登录 · 服务状态</title>
<style>
:root{--bg:#f6f4f1;--surface:#fdfcfa;--ink:#1c1917;--muted:#6f665f;--line:rgba(28,25,23,.12);--ember:#b45309}
@media(prefers-color-scheme:dark){:root{--bg:#12100e;--surface:#1b1815;--ink:#eee9e3;--muted:#a89e94;--line:rgba(255,255,255,.14);--ember:#e0a15c}}
body{margin:0;min-height:100dvh;display:grid;place-items:center;background:var(--bg);color:var(--ink);font:14px/1.6 -apple-system,"PingFang SC","Microsoft YaHei",sans-serif}
.card{width:min(92vw,340px);background:var(--surface);border:1px solid var(--line);border-radius:16px;padding:24px 22px;box-shadow:0 12px 40px -20px rgba(0,0,0,.4)}
h1{font-size:16px;margin:0 0 4px}p{margin:0 0 18px;color:var(--muted);font-size:12.5px}
input{width:100%;box-sizing:border-box;padding:10px 12px;border-radius:10px;border:1px solid var(--line);background:var(--bg);color:var(--ink);font:inherit}
button{width:100%;margin-top:12px;padding:10px;border-radius:10px;border:1px solid var(--ember);background:var(--ember);color:#fff7ed;font:inherit;cursor:pointer}
.err{color:#b91c1c;font-size:12px;margin-top:10px;min-height:16px}
</style></head><body>
<form class="card" method="post" action="login">
  <h1>服务状态</h1><p>输入面板密码以继续</p>
  <input type="password" name="password" autofocus autocomplete="current-password" placeholder="密码">
  <button type="submit">进入</button>
  <div class="err">__ERR__</div>
</form></body></html>`;

function formBody(req) {
  return new Promise((resolve) => {
    let b = '';
    req.on('data', (d) => { b += d; });
    req.on('end', () => resolve(Object.fromEntries(new URLSearchParams(b))));
  });
}

function body(req) {
  return new Promise((resolve) => {
    let b = '';
    req.on('data', (d) => { b += d; });
    req.on('end', () => { try { resolve(b ? JSON.parse(b) : {}); } catch { resolve({}); } });
  });
}

http.createServer(async (req, res) => {
  try {
    const u = new URL(req.url, 'http://x');
    const p = u.pathname;

    if (p === '/login' && req.method === 'GET') {
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      return res.end(LOGIN_PAGE.replace('__ERR__', ''));
    }
    if (p === '/login' && req.method === 'POST') {
      const f = await formBody(req);
      if (String(f.password || '') === panelPass()) {
        res.writeHead(303, {
          'Set-Cookie': COOKIE_NAME + '=' + sessionToken() + '; Path=/; HttpOnly; Secure; SameSite=Lax; Max-Age=31536000',
          Location: './',
          'Cache-Control': 'no-store',
        });
        return res.end();
      }
      res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
      return res.end(LOGIN_PAGE.replace('__ERR__', '密码不正确'));
    }

    const isPublic = p === '/manifest.webmanifest' || p === '/sw.js' || p.startsWith('/icon-') || p === '/api/app';
    if (!isPublic && !authed(req)) {
      if (p.startsWith('/api/')) return json(res, 401, { error: 'auth required' });
      res.writeHead(302, { Location: 'login', 'Cache-Control': 'no-store' });
      return res.end();
    }

    if (req.method === 'GET' && (p === '/' || p === '/index.html')) {
      try {
        const html = readFileSync(INDEX_PATH);
        res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
        return res.end(html);
      } catch {
        res.writeHead(500, { 'Content-Type': 'text/plain; charset=utf-8' });
        return res.end('index.html missing');
      }
    }

    if (STATIC[p]) {
      const [type, file] = STATIC[p];
      if (!existsSync(file)) { res.writeHead(404); return res.end('missing'); }
      res.writeHead(200, { 'Content-Type': type, 'Cache-Control': p === '/sw.js' ? 'no-cache' : 'public, max-age=3600' });
      return res.end(readFileSync(file));
    }

    if (p === '/api/stream') {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      });
      const push = async () => {
        try {
          const status = await getStatus();
          const bot = { config: readBot(), memory: memoryStats(), usage: usageStats(), log: botLog() };
          res.write('data: ' + JSON.stringify({ status, bot }) + '\n\n');
        } catch {}
      };
      push();
      const timer = setInterval(push, 2000);
      req.on('close', () => clearInterval(timer));
      return;
    }


    if (p === '/api/links') {
      let dsh = '';
      try { dsh = (await run('/usr/local/bin/dsh-url 2>/dev/null')).trim(); } catch {}
      // 手机上的 harness：服务器经 SSH 反向隧道把 127.0.0.1:18090 转到手机 3099 的网关，
      // 网关的 /go 会用当前有效 token 302 过去，所以这里取它的 Location 就是最新 token 链接。
      let phoneDsh = '';
      let phoneUp = false;
      try {
        const loc = (await run("curl -s -m 3 -o /dev/null -w '%{redirect_url}' http://127.0.0.1:18090/go 2>/dev/null")).trim();
        if (loc) {
          phoneUp = true;
          phoneDsh = 'https://dsh.saisqi.xyz' + loc.replace(/^https?:\/\/[^/]+/, '');
        }
      } catch {}
      let napcat = '';
      try {
        const w = JSON.parse(readFileSync('/root/Napcat/opt/QQ/resources/app/app_launcher/napcat/config/webui.json', 'utf8'));
        napcat = 'http://127.0.0.1:' + w.port + '/webui?token=' + w.token;
      } catch {}
      return json(res, 200, {
        dsh,
        phoneDsh,
        phoneUp,
        status: 'https://dsh.saisqi.xyz/status/',
        dshRoot: 'https://dsh.saisqi.xyz/',
        bt: 'https://bt.saisqi.xyz/login',
        napcat,
        sites: ['https://saisqi.xyz', 'https://www.saisqi.xyz', 'https://bt.saisqi.xyz', 'https://ai.saisqi.xyz'],
        apk: 'https://saisqi.xyz/app/panel-3.0.apk?v=3.0',
        accounts: [
          { name: '控制台 /status/', user: 'admin', pass: '***已打码***' },
          { name: '宝塔面板', user: 'yunxiao', pass: '***已打码***' }
        ],
        at: Date.now()
      });
    }


    if (p === '/api/app') {
      let v = { versionCode: 0, versionName: '', apk: 'https://saisqi.xyz/g', notes: '' };
      try { v = Object.assign(v, JSON.parse(readFileSync('/root/apk/panel/version.json', 'utf8'))); } catch {}
      res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
      return res.end(JSON.stringify(v));
    }

    if (p === '/api/qq') {
      const listen = await run('ss -lnt 2>/dev/null');
      const online = /:3001\s/.test(listen);
      let age = -1;
      try { age = Math.round((Date.now() - statSync(QQ_QR).mtimeMs) / 1000); } catch {}
      return json(res, 200, { online, age, account: online ? '1619716221' : '' });
    }

    if (p === '/api/qq/qr.png') {
      try {
        const buf = readFileSync(QQ_QR);
        res.writeHead(200, { 'Content-Type': 'image/png', 'Cache-Control': 'no-store' });
        return res.end(buf);
      } catch {
        res.writeHead(404); return res.end('no qr');
      }
    }

    if (p === '/api/qq/refresh' && req.method === 'POST') {
      await run('systemctl restart napcat');
      return json(res, 200, { ok: true, startedAt: Date.now() });
    }

    if (p === '/api/status') return json(res, 200, await getStatus());

    if (p === '/api/bot/memory' && req.method === 'GET') {
      const uid = String(u.searchParams.get('uid') || '');
      if (!uid || !existsSync(memFile(uid))) return json(res, 404, { error: 'no such user' });
      try { return json(res, 200, memDetail(uid)); } catch (e) { return json(res, 500, { error: String(e.message) }); }
    }

    if (p === '/api/bot' && req.method === 'GET') {
      return json(res, 200, { config: readBot(), memory: memoryStats(), usage: usageStats(), log: botLog() });
    }

    if (p.startsWith('/api/bot/') && req.method === 'POST') {
      const action = p.slice('/api/bot/'.length);
      const b = await body(req);

      if (action === 'persona') {
        writeBot((c) => { c.persona = String(b.persona ?? ''); });
      } else if (action === 'toggle') {
        writeBot((c) => { c.enabled = !c.enabled; });
      } else if (action === 'group') {
        writeBot((c) => { c.replyInGroup = !c.replyInGroup; });
      } else if (action === 'thinking') {
        const mode = String(b.mode || 'default');
        if (!['default', 'low', 'off'].includes(mode)) return json(res, 400, { error: 'bad mode' });
        writeBot((c) => { c.thinking = mode; });
      } else if (action === 'model') {
        if (!MODELS.includes(String(b.model))) return json(res, 400, { error: 'unsupported model' });
        writeBot((c) => { c.model = String(b.model); });
      } else if (action === 'whitelist/add') {
        const id = String(b.id || '').trim();
        if (!id) return json(res, 400, { error: 'QQ 号不能为空' });
        writeBot((c) => { c.whitelist = [...new Set([...(c.whitelist || []).map(String), id])]; });
      } else if (action === 'whitelist/remove') {
        writeBot((c) => { c.whitelist = (c.whitelist || []).map(String).filter((x) => x !== String(b.id)); });
      } else if (action === 'whitelist/clear') {
        writeBot((c) => { c.whitelist = []; });
      } else if (action === 'memory/delete') {
        const uid = String(b.uid || '');
        const idx = Number(b.index);
        if (!uid || !existsSync(memFile(uid))) return json(res, 404, { error: 'no such user' });
        const m = JSON.parse(readFileSync(memFile(uid), 'utf8'));
        const msgs = m.messages || [];
        if (!Number.isInteger(idx) || idx < 0 || idx >= msgs.length) return json(res, 400, { error: 'bad index' });
        msgs.splice(idx, 1);
        m.messages = msgs;
        m.updatedAt = Date.now();
        writeFileSync(memFile(uid), JSON.stringify(m));
        return json(res, 200, { ok: true, detail: memDetail(uid), config: readBot(), memory: memoryStats(), usage: usageStats(), log: botLog() });
      } else if (action === 'memory/summary') {
        const uid = String(b.uid || '');
        if (!uid || !existsSync(memFile(uid))) return json(res, 404, { error: 'no such user' });
        const m = JSON.parse(readFileSync(memFile(uid), 'utf8'));
        m.summary = String(b.summary ?? '');
        m.updatedAt = Date.now();
        writeFileSync(memFile(uid), JSON.stringify(m));
        return json(res, 200, { ok: true, detail: memDetail(uid), config: readBot(), memory: memoryStats(), usage: usageStats(), log: botLog() });
      } else if (action === 'memory/compress') {
        const uid = String(b.uid || '');
        if (!uid || !existsSync(memFile(uid))) return json(res, 404, { error: 'no such user' });
        const r = await compressUser(uid, Number(b.keep) || 12);
        if (!r.ok) return json(res, 400, { error: r.error });
        return json(res, 200, { ok: true, result: r, config: readBot(), memory: memoryStats(), usage: usageStats(), log: botLog() });
      } else if (action === 'memory/forget') {
        const uid = String(b.uid || 'all');
        try {
          if (uid === 'all') {
            for (const f of readdirSync(MEM_DIR)) { try { writeFileSync(MEM_DIR + '/' + f, ''); } catch {} }
          } else {
            for (const ext of ['.json', '.jsonl']) {
              const f = MEM_DIR + '/' + uid + ext;
              if (existsSync(f)) unlinkSync(f);
            }
          }
        } catch {}
      } else if (action === 'restart') {
        await run('systemctl restart qq-bot');
      } else {
        return json(res, 404, { error: 'unknown action' });
      }

      return json(res, 200, { ok: true, config: readBot(), memory: memoryStats(), usage: usageStats(), log: botLog() });
    }

    res.writeHead(404);
    res.end('not found');
  } catch (e) {
    json(res, 500, { error: String(e.message || e) });
  }
}).listen(PORT, '127.0.0.1', () => console.log('panel api on 127.0.0.1:' + PORT));

