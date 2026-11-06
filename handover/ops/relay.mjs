import { Client } from "ssh2";
import net from "node:net";

const HOST = "103.68.192.206";
const PORT = 13945;
const USER = "root";
const PASS = process.env.VPASS;
const REMOTE_PORT = 18090;
const LOCAL_PORT = 3099;

const log = (...a) => console.log(new Date().toISOString(), ...a);
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function open() {
  const c = new Client();
  await new Promise((res, rej) => {
    c.on("ready", res).on("error", rej).connect({
      host: HOST, port: PORT, username: USER, password: PASS,
      keepaliveInterval: 10000, keepaliveCountMax: 3, readyTimeout: 20000,
    });
  });
  for (let i = 0; ; i++) {
    const err = await new Promise((res) => c.forwardIn("127.0.0.1", REMOTE_PORT, (e) => res(e)));
    if (!err) break;
    log("forwardIn failed:", err.message, "retry#" + (i + 1));
    if (i >= 5) { c.end(); throw new Error("forwardIn unreachable"); }
    await sleep(10000);
  }
  log("tunnel up", REMOTE_PORT, "->", LOCAL_PORT);
  c.on("tcp connection", (info, accept) => {
    const ch = accept();
    const s = net.connect(LOCAL_PORT, "127.0.0.1");
    s.on("connect", () => { ch.pipe(s); s.pipe(ch); });
    s.on("error", () => ch.destroy());
    ch.on("error", () => s.destroy());
  });
  c.on("error", (e) => log("ssh err", e.message));
  return c;
}

let delay = 5000;
for (;;) {
  try {
    const c = await open();
    delay = 5000;
    await new Promise((res) => c.on("close", res));
    log("ssh closed");
  } catch (e) {
    log("session failed:", e.message);
  }
  log("reconnect in", delay + "ms");
  await sleep(delay);
  delay = Math.min(delay * 2, 60000);
}
