#!/system/bin/sh
# DSH 常驻：挪出可冻结 cgroup + 降 oom + wakelock + 无响应自动重启
BASE=/data/user/0/com.deepseek.harness/files
LOG=/data/local/tmp/dsh-web-keepalive.log
PKG=com.deepseek.harness
FLAG_NOWAKE=/data/local/tmp/dsh-web-nowake
export LD_LIBRARY_PATH=$BASE/payload/runtime/lib
CURL=$BASE/payload/runtime/bin/curl
log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; }

move_out() {
  for p in "$@"; do
    [ -d /proc/$p ] || continue
    cg=$(grep '^0::' /proc/$p/cgroup 2>/dev/null | cut -d: -f3)
    case "$cg" in
      /system|"") ;;
      *) echo $p > /sys/fs/cgroup/system/cgroup.procs 2>/dev/null ;;
    esac
  done
}

log "keepalive start pid=$$"
sleep 15
fail=0
tick=0
while :; do
  move_out $(pidof $PKG 2>/dev/null)
  for d in /proc/[0-9]*; do
    c=$(tr '\0' ' ' < "$d/cmdline" 2>/dev/null)
    case "$c" in
      *relay.mjs*|*gw.mjs*|*dsh/lib/bin.js*) move_out $(echo "$d" | cut -d/ -f3) ;;
    esac
  done

  for p in $(pidof $PKG 2>/dev/null); do
    [ "$(cat /proc/$p/oom_score_adj 2>/dev/null)" != "-900" ] && echo -900 > /proc/$p/oom_score_adj 2>/dev/null
  done

  if [ ! -f "$FLAG_NOWAKE" ]; then
    case "$(cat /sys/power/wake_lock 2>/dev/null)" in
      *dshweb*) ;;
      *) echo dshweb > /sys/power/wake_lock 2>/dev/null && log "wakelock on" ;;
    esac
  fi

  tick=$((tick+1))
  if [ $((tick % 5)) -eq 0 ]; then
    code=$($CURL -s -m 3 -o /dev/null -w '%{http_code}' http://127.0.0.1:3080/ 2>/dev/null)
    case "$code" in
      000|"") fail=$((fail+1)) ;;
      *) fail=0 ;;
    esac
    if [ "$fail" -ge 3 ]; then
      log "dsh 3080 无响应 -> 重启"
      am force-stop $PKG
      sleep 2
      am start -n $PKG/.MainActivity >/dev/null 2>&1
      log "已重新拉起"
      fail=0
    fi
  fi
  sleep 2
done
