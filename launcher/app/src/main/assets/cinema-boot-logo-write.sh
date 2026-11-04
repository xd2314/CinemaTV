#!/system/bin/sh
# Live-file Project A adapter; no hardware/fingerprint allowlist. Only a freshly derived aligned BMP window may be written.
TXID='@TX@'
OP='@OP@'
SOURCE='@SOURCE@'
CLIENT_UID='@UID@'
BEFORE='@BEFORE@'
AFTER='@AFTER@'
OLD_WINDOW='@OLD_WINDOW@'
NEW_WINDOW='@NEW_WINDOW@'
SEEK='@SEEK@'
COUNT='@COUNT@'
NODE_META='@META@'
DEVICE='@DEVICE@'
NODE=${DEVICE#/dev/block/}
BASE=/data/local/cinema-lite-boot-logo-v1
started=0
locked=0
finish() {
  rc=$?
  if [ "$rc" -ne 0 ] && [ "$started" = 0 ]; then printf 'CLBL_NO_TARGET_WRITE\n'; fi
  if [ "$locked" = 1 ]; then /system/bin/rmdir "$BASE/.lock" >/dev/null 2>&1; fi
}
trap finish EXIT
# Resolve only the currently selected slot; never guess a or write both slots.
clbl_device() {
  slot=$(/system/bin/getprop ro.boot.slot_suffix) || return 1
  short=$(/system/bin/getprop ro.boot.slot) || return 1
  case "$slot" in '') [ -z "$short" ] || return 1; alias=/dev/block/by-name/bootloader
      [ ! -e /dev/block/by-name/bootloader_a ] && [ ! -L /dev/block/by-name/bootloader_a ] && [ ! -e /dev/block/by-name/bootloader_b ] && [ ! -L /dev/block/by-name/bootloader_b ] || return 1;;
    _a|_b) [ -z "$short" ] || [ "_$short" = "$slot" ] || return 1; alias=/dev/block/by-name/bootloader$slot;;
    *) return 1;; esac
  resolved=$(/system/bin/readlink -f "$alias") || return 1
  name=${resolved#/dev/block/}
  case "$resolved:$name" in /dev/block/*:*) ;; *) return 1;; esac
  case "$name" in ''|*[!a-zA-Z0-9_-]*) return 1;; esac
  [ -L "$alias" ] && [ -b "$resolved" ] || return 1
  if [ -n "$slot" ]; then
    if [ -e /dev/block/by-name/bootloader ] || [ -L /dev/block/by-name/bootloader ]; then
      [ "$(/system/bin/readlink -f /dev/block/by-name/bootloader)" = "$resolved" ] || return 1
    fi
    if [ "$slot" = _a ]; then other=/dev/block/by-name/bootloader_b; else other=/dev/block/by-name/bootloader_a; fi
    [ "$(/system/bin/readlink -f "$other")" != "$resolved" ] || return 1
  fi
  printf '%s' "$resolved"
}
clbl_sha256() {
  digest=$(/system/bin/sha256sum "$1") || return 1
  digest=${digest%% *}
  [ "${#digest}" = 64 ] || return 1
  case "$digest" in *[!a-f0-9]*) return 1;; esac
  printf '%s' "$digest"
}
clbl_meta() { /system/bin/stat -c '%u:%g:%a' "$1"; }
clbl_regular() { [ -f "$1" ] && [ ! -L "$1" ]; }
clbl_text() {
  destination=$1
  if [ -e "$destination" ] || [ -L "$destination" ]; then
    clbl_regular "$destination" && [ "$(clbl_meta "$destination")" = 0:0:600 ] || return 1
  fi
  [ ! -e "$destination.next" ] && [ ! -L "$destination.next" ] || return 1
  (set -C; printf '%s\n' "$2" > "$destination.next") || return 1
  /system/bin/chmod 600 "$destination.next" && /system/bin/mv "$destination.next" "$destination"
}
umask 077
case "$DEVICE" in /dev/block/*) ;; *) exit 15;; esac
case "$NODE" in ''|*[!a-zA-Z0-9_-]*) exit 15;; esac
[ "$(/system/bin/id -u)" = 0 ] || exit 10
case "$CLIENT_UID" in *[!0-9]*|'') exit 11;; esac
[ "$CLIENT_UID" -ge 10000 ] && [ "$CLIENT_UID" -le 19999 ] || exit 11
printf 'CLBL_UID=0\nCLBL_CLIENT_UID=%s\nCLBL_TX=%s\nCLBL_DEVICE=%s\n' "$CLIENT_UID" "$TXID" "$DEVICE"
[ "$(clbl_device)" = "$DEVICE" ] && [ -b "$DEVICE" ] || exit 15
[ "$(/system/bin/cat "/sys/class/block/$NODE/size")" = 65536 ] || exit 16
[ "$(/system/bin/stat -c '%t:%T:%u:%g:%a' "$DEVICE")" = "$NODE_META" ] || exit 17
case "$SEEK:$COUNT" in *[!0-9:]*) exit 18;; esac
[ "$SEEK" -gt 0 ] && [ "$COUNT" -gt 0 ] && [ "$COUNT" -le 6144 ] && [ "$((SEEK+COUNT))" -le 65536 ] || exit 19
LENGTH=$((COUNT*512))
actual=$(clbl_sha256 "$DEVICE") || exit 20
if [ "$OP" = APPLY ]; then [ "$actual" = "$BEFORE" ] || exit 21
else [ "$OP" = RESTORE ] && [ "$actual" = "$AFTER" ] || exit 22; fi
[ -d /data/local ] && [ ! -L /data/local ] || exit 23
case "$(clbl_meta /data/local)" in 0:0:751|0:0:755|0:0:700) ;; *) exit 23;; esac
space=$(/system/bin/stat -f -c '%a:%S' /data/local) || exit 24
available=${space%%:*}
block_size=${space#*:}
case "$available:$block_size" in *[!0-9:]*) exit 25;; esac
[ "$block_size" -gt 0 ] && [ "$block_size" -le 1048576 ] && [ "$available" -ge "$(((100663296+block_size-1)/block_size))" ] || exit 26
[ ! -L "$BASE" ] || exit 27
if [ -e "$BASE" ]; then
  [ -d "$BASE" ] && [ "$(clbl_meta "$BASE")" = 0:0:700 ] || exit 28
  clbl_regular "$BASE/current.sha" && [ "$(clbl_meta "$BASE/current.sha")" = 0:0:600 ] || exit 29
  [ "$(/system/bin/cat "$BASE/current.sha")" = "$actual" ] || exit 30
else
  [ "$OP" = APPLY ] || exit 31
  /system/bin/mkdir "$BASE" && /system/bin/chown 0:0 "$BASE" && /system/bin/chmod 700 "$BASE" || exit 32
  clbl_text "$BASE/current.sha" "$actual" || exit 33
fi
[ "$(/system/bin/readlink -f "$BASE")" = "$BASE" ] || exit 34
/system/bin/mkdir "$BASE/.lock" || exit 35
locked=1
TX=$BASE/$TXID
if [ "$OP" = APPLY ]; then
  [ ! -e "$TX" ] && [ ! -L "$TX" ] || exit 36
  clbl_regular "$SOURCE" && [ "$(/system/bin/readlink -f "$SOURCE")" = "$SOURCE" ] && [ "$(/system/bin/stat -c %u "$SOURCE")" = "$CLIENT_UID" ] || exit 37
  [ "$(/system/bin/stat -c %s "$SOURCE")" = "$LENGTH" ] && [ "$(clbl_sha256 "$SOURCE")" = "$NEW_WINDOW" ] || exit 38
  /system/bin/mkdir "$TX" && /system/bin/chmod 700 "$TX" || exit 39
  /system/bin/cat "$DEVICE" > "$TX/before.img" || exit 40
  [ "$(/system/bin/stat -c %s "$TX/before.img")" = 33554432 ] && [ "$(clbl_sha256 "$TX/before.img")" = "$BEFORE" ] || exit 41
  /system/bin/dd if="$TX/before.img" of="$TX/restore-window.bin" bs=512 skip="$SEEK" count="$COUNT" status=none || exit 42
  [ "$(/system/bin/stat -c %s "$TX/restore-window.bin")" = "$LENGTH" ] && [ "$(clbl_sha256 "$TX/restore-window.bin")" = "$OLD_WINDOW" ] || exit 43
  /system/bin/cat "$SOURCE" > "$TX/candidate-window.bin" || exit 44
  [ "$(clbl_sha256 "$TX/candidate-window.bin")" = "$NEW_WINDOW" ] || exit 45
  clbl_text "$TX/before.sha" "$BEFORE" && clbl_text "$TX/after.sha" "$AFTER" && clbl_text "$TX/range" "$SEEK:$COUNT" && clbl_text "$TX/status" PREPARED || exit 46
  staged=$TX/candidate-window.bin
  wanted=$AFTER
  result=APPLIED
else
  [ ! -L "$TX" ] && [ -d "$TX" ] && [ "$(clbl_meta "$TX")" = 0:0:700 ] || exit 47
  for item in before.sha after.sha range status before.img restore-window.bin; do
    clbl_regular "$TX/$item" && [ "$(clbl_meta "$TX/$item")" = 0:0:600 ] || exit 48
  done
  [ "$(/system/bin/cat "$TX/status")" = APPLIED ] && [ "$(/system/bin/cat "$TX/range")" = "$SEEK:$COUNT" ] || exit 49
  [ "$(/system/bin/cat "$TX/before.sha")" = "$BEFORE" ] && [ "$(/system/bin/cat "$TX/after.sha")" = "$AFTER" ] || exit 50
  [ "$(clbl_sha256 "$TX/before.img")" = "$BEFORE" ] && [ "$(clbl_sha256 "$TX/restore-window.bin")" = "$OLD_WINDOW" ] || exit 51
  [ "$(/system/bin/stat -c %s "$TX/restore-window.bin")" = "$LENGTH" ] || exit 52
  staged=$TX/restore-window.bin
  wanted=$BEFORE
  result=RESTORED
fi
/system/bin/sync || exit 53
[ "$(clbl_device)" = "$DEVICE" ] && [ "$(clbl_sha256 "$DEVICE")" = "$actual" ] || exit 54
printf 'CLBL_BEFORE_SHA=%s\nCLBL_CANDIDATE_SHA=%s\nCLBL_SEEK=%s\nCLBL_COUNT=%s\n' "$BEFORE" "$AFTER" "$SEEK" "$COUNT"
started=1
printf 'CLBL_TARGET_WRITE_ATTEMPTED\n'
if [ "$OP" = APPLY ]; then phase=APPLYING; else phase=RESTORING; fi
clbl_text "$TX/status" "$phase" && /system/bin/sync || exit 55
/system/bin/dd if="$staged" of="$DEVICE" bs=512 seek="$SEEK" count="$COUNT" conv=notrunc status=none || exit 56
/system/bin/sync || exit 57
[ "$(clbl_sha256 "$DEVICE")" = "$wanted" ] || exit 58
clbl_text "$BASE/current.sha" "$wanted" && clbl_text "$TX/status" "$result" && /system/bin/sync || exit 59
printf 'CLBL_AFTER_SHA=%s\nCLBL_RESULT=%s\n' "$wanted" "$result"
exit 0
