#!/system/bin/sh
# bg.sh <name> <cmd...> : start detached, return immediately
name=$1; shift
if [ -z "$name" ]; then echo "usage: bg.sh <name> <cmd...>"; exit 2; fi
LOG=/data/local/tmp/$name.log
nohup setsid "$@" >> "$LOG" 2>&1 < /dev/null &
echo "name=$name pid=$! log=$LOG"
