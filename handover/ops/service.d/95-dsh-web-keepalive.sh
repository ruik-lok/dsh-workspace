#!/system/bin/sh
# DSH 常驻：cgroup 迁移 + oom + wakelock + 息屏空闲自动停止 + 崩溃拉起
BASE=/data/user/0/com.deepseek.harness/files
LOG=/data/local/tmp/dsh-web-keepalive.log
PKG=com.deepseek.harness
NOWAKE=/data/local/tmp/dsh-web-nowake
STOP_FLAG=/data/local/tmp/dsh-stopped
IDLE_LIMIT_FILE=/data/local/tmp/dsh-autostop-minutes
export LD_LIBRARY_PATH=$BASE/payload/runtime/lib
CURL=$BASE/payload/runtime/bin/curl
log() { echo "$(date '+%m-%d %H:%M:%S') $*" >> "$LOG"; }

move_out() {
  for p in "$@"; do
    [ -d /proc/$p ] || continue
    cg=$(grep '^0::' /proc/$p/cgroup 2>/dev/null | cut -d: -f3)
    case "$cg" in /system|"") ;; *) echo $p > /sys/fs/cgroup/system/cgroup.procs 2>/dev/null ;; esac
  done
}

log "keepalive start pid=$$"
sleep 15
dumpsys deviceidle disable >/dev/null 2>&1 && log "doze disabled"
down=0
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

  if [ ! -f "$NOWAKE" ]; then
    case "$(cat /sys/power/wake_lock 2>/dev/null)" in
      *dshweb*) ;;
      *) echo dshweb > /sys/power/wake_lock 2>/dev/null && log "wakelock on" ;;
    esac
  fi

  tick=$((tick+1))
  # dsh 已主动停止时，不再做链路保活，进一步省电
  if [ ! -f "$STOP_FLAG" ]; then
    if [ $((tick % 10)) -eq 0 ]; then
      ping -c 1 -W 1 223.5.5.5 >/dev/null 2>&1
    fi
    if [ $((tick % 30)) -eq 0 ]; then
      case "$(iw dev wlan0 get power_save 2>/dev/null)" in
        *"on"*) iw dev wlan0 set power_save off 2>/dev/null && log "wifi power_save off" ;;
      esac
    fi
  fi
  if [ $((tick % 15)) -eq 0 ]; then
    asleep=0
    dumpsys power 2>/dev/null | grep -qE "mWakefulness=(Asleep|Dozing)" && asleep=1
    if [ "$asleep" = "1" ] && [ ! -f "$STOP_FLAG" ]; then
      idle=$($CURL -s -m 3 http://127.0.0.1:3099/admin/device 2>/dev/null | sed -n "s/.*\"idleSec\":\([0-9]*\).*/\1/p")
      [ -z "$idle" ] && idle=0
      limit=$(cat "$IDLE_LIMIT_FILE" 2>/dev/null || echo 30)
      if [ "$idle" -ge $((limit * 60)) ]; then
        log "息屏且 $idle 秒无外部访问，自动停止 dsh"
        touch "$STOP_FLAG"
        am force-stop $PKG
      fi
    fi
  fi

  if [ $((tick % 5)) -eq 0 ] && [ ! -f "$STOP_FLAG" ]; then
    if dumpsys activity services $PKG 2>/dev/null | grep -q EngineService; then
      down=0
    else
      down=$((down+1))
      log "engine missing ($down)"
      if [ "$down" -ge 2 ]; then
        log "relaunch app"
        am start -n $PKG/.MainActivity >/dev/null 2>&1
        down=0
      fi
    fi
  fi
  sleep 2
done