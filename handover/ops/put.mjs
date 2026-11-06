import { Client } from "ssh2";

const conn = new Client();
conn.on("ready", () => {
  conn.sftp((err, sftp) => {
    if (err) { console.log("sftp error:", err.message); process.exit(1); }
    sftp.fastPut(process.env.LOCAL, process.env.REMOTE, (e) => {
      console.log(e ? "ERR " + e.message : "OK");
      conn.end();
    });
  });
}).on("error", (e) => { console.log("SSH ERROR:", e.message); process.exit(1); })
  .connect({ host: process.env.VHOST, port: parseInt(process.env.VPORT || "22", 10), username: "root", password: process.env.VPASS, readyTimeout: 12000 });
