#!/system/bin/sh
# root(uid 0) 常驻：手机 dsh 公网隧道 + 网关。锁屏时 App uid 会被掐网，root 不会。
BASE=/data/user/0/com.deepseek.harness/files
export LD_LIBRARY_PATH=$BASE/payload/runtime/lib
LOG=$BASE/root-tunnel.log
cd $BASE/ops || exit 1
log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; }

log "root tunnel stack start pid=$$"
( while :; do
    if ! pgrep -f 'g[w].mjs' >/dev/null 2>&1; then
      node gw.mjs >> "$LOG" 2>&1 &
      log "gw started $!"
    fi
    sleep 5
  done ) &

while :; do
  VPASS=***已打码*** node relay.mjs >> "$LOG" 2>&1
  log "relay exited rc=$? restart in 3s"
  sleep 3
done
