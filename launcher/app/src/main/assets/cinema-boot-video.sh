#!/system/bin/sh
# Live-file Project A adapter; no hardware/fingerprint allowlist. No service control, partition access, reboot, or retry.
TXID='@TX@'
OP='@OP@'
SOURCE='@SOURCE@'
CANDIDATE='@SHA@'
CLIENT_UID='@UID@'
BASE=/data/local/cinema-lite-boot-video-v1
TARGET=/data/local/boot.mp4
STOCK=/system/media/boot.mp4
CONTEXT=
write_started=0
locked=0
finish() {
  rc=$?
  if [ "$rc" -ne 0 ] && [ "$write_started" = 0 ]; then printf 'CLBV_NO_TARGET_WRITE\n'; fi
  if [ "$locked" = 1 ]; then /system/bin/rmdir "$BASE/.lock" >/dev/null 2>&1; fi
}
trap finish EXIT
clbv_sha256() {
  value=$(/system/bin/sha256sum "$1") || return 1
  value=${value%% *}
  [ "${#value}" = 64 ] || return 1
  case "$value" in *[!a-f0-9]*) return 1;; esac
  printf '%s' "$value"
}
regular() { [ -f "$1" ] && [ ! -L "$1" ]; }
meta() { /system/bin/stat -c '%u:%g:%a' "$1"; }
atomic_text() {
  dest=$1
  if [ -e "$dest" ] || [ -L "$dest" ]; then
    regular "$dest" && [ "$(meta "$dest")" = 0:0:600 ] || return 1
  fi
  [ ! -e "$dest.next" ] && [ ! -L "$dest.next" ] || return 1
  (set -C; printf '%s\n' "$2" > "$dest.next") || return 1
  /system/bin/chmod 600 "$dest.next" || return 1
  /system/bin/mv "$dest.next" "$dest" || return 1
}
check_target() {
  regular "$TARGET" && [ "$(meta "$TARGET")" = 0:0:644 ] || return 1
  label=$(/system/bin/ls -Zd "$TARGET") || return 1
  [ "${label%% *}" = "$CONTEXT" ] || return 1
  length=$(/system/bin/stat -c %s "$TARGET") || return 1
  [ "$length" -gt 0 ] && [ "$length" -le 20971520 ] || return 1
}
umask 077
uid=$(/system/bin/id -u) || exit 10
[ "$uid" = 0 ] || exit 11
printf 'CLBV_UID=0\nCLBV_CLIENT_UID=%s\nCLBV_TX=%s\n' "$CLIENT_UID" "$TXID"
printf 'CLBV_ROUTE=LOCAL_FILE_V1\n'
case "$CLIENT_UID" in *[!0-9]*|'') exit 12;; esac
[ "$CLIENT_UID" -ge 10000 ] && [ "$CLIENT_UID" -le 19999 ] || exit 12
# The stock movie is optional and is never written. Record its actual state.
stock_before=ABSENT
if [ -e "$STOCK" ] || [ -L "$STOCK" ]; then
  regular "$STOCK" || exit 16
  stock_before=$(clbv_sha256 "$STOCK") || exit 16
fi
check_stock() {
  if [ "$stock_before" = ABSENT ]; then [ ! -e "$STOCK" ] && [ ! -L "$STOCK" ]
  else regular "$STOCK" && [ "$(clbv_sha256 "$STOCK")" = "$stock_before" ]; fi
}
[ -d /data/local ] && [ ! -L /data/local ] || exit 17
case "$(meta /data/local)" in 0:0:751|0:0:755|0:0:700) ;; *) exit 17;; esac
regular "$TARGET" || { printf 'CLBV_CHECK=TARGET_MISSING_OR_NOT_REGULAR\n'; exit 18; }
[ "$(/system/bin/readlink -f "$TARGET")" = "$TARGET" ] || exit 18
label=$(/system/bin/ls -Zd "$TARGET") || exit 18
CONTEXT=${label%% *}
# Preserve this file's existing SELinux label; never change system policy.
case "$CONTEXT" in u:object_r:*:s0) ;; *) exit 18;; esac
case "$CONTEXT" in *[!a-zA-Z0-9_:]*) exit 18;; esac
check_target || { printf 'CLBV_CHECK=UNSUPPORTED_TARGET_METADATA\n'; exit 18; }
printf 'CLBV_CHECK=LIVE_TARGET_VERIFIED\nCLBV_STOCK_SHA=%s\n' "$stock_before"
actual=$(clbv_sha256 "$TARGET") || exit 19
space=$(/system/bin/stat -f -c '%a:%S' /data/local) || exit 70
available=${space%%:*}
block_size=${space#*:}
case "$available:$block_size" in *[!0-9:]*) exit 71;; esac
[ "$block_size" -gt 0 ] && [ "$block_size" -le 1048576 ] || exit 71
required_blocks=$(((67108864 + block_size - 1) / block_size))
[ "$available" -ge "$required_blocks" ] || exit 72
[ ! -L "$BASE" ] || exit 20
if [ -e "$BASE" ]; then
  [ -d "$BASE" ] && [ "$(meta "$BASE")" = 0:0:700 ] || exit 21
  regular "$BASE/current.sha" && [ "$(meta "$BASE/current.sha")" = 0:0:600 ] || exit 22
  expected=$(/system/bin/cat "$BASE/current.sha") || exit 23
else
  [ "$OP" = APPLY ] || exit 24
  /system/bin/mkdir "$BASE" || exit 25
  /system/bin/chown 0:0 "$BASE" && /system/bin/chmod 700 "$BASE" || exit 26
  atomic_text "$BASE/current.sha" "$actual" || exit 27
  expected=$actual
fi
[ "$(/system/bin/readlink -f "$BASE")" = "$BASE" ] || exit 28
[ "$expected" = "$actual" ] || exit 29
/system/bin/mkdir "$BASE/.lock" || exit 30
locked=1
TX=$BASE/$TXID
if [ "$OP" = APPLY ]; then
  [ ! -e "$TX" ] && [ ! -L "$TX" ] || exit 31
  regular "$SOURCE" && [ "$(/system/bin/readlink -f "$SOURCE")" = "$SOURCE" ] || exit 32
  [ "$(/system/bin/stat -c %u "$SOURCE")" = "$CLIENT_UID" ] || exit 33
  length=$(/system/bin/stat -c %s "$SOURCE") || exit 34
  [ "$length" -gt 0 ] && [ "$length" -le 20971520 ] || exit 35
  [ "$(clbv_sha256 "$SOURCE")" = "$CANDIDATE" ] || exit 36
  /system/bin/mkdir "$TX" && /system/bin/chmod 700 "$TX" || exit 37
  /system/bin/cat "$TARGET" > "$TX/before.mp4" || exit 38
  [ "$(clbv_sha256 "$TX/before.mp4")" = "$actual" ] || exit 39
  atomic_text "$TX/before.sha" "$actual" && atomic_text "$TX/candidate.sha" "$CANDIDATE" && atomic_text "$TX/target.context" "$CONTEXT" && atomic_text "$TX/client.uid" "$CLIENT_UID" || exit 40
  /system/bin/cat "$SOURCE" > "$TX/new.mp4" || exit 41
  [ "$(clbv_sha256 "$TX/new.mp4")" = "$CANDIDATE" ] || exit 42
  before=$actual
  wanted=$CANDIDATE
  result=APPLIED
else
  [ "$OP" = RESTORE ] || exit 43
  [ ! -L "$TX" ] && [ -d "$TX" ] && [ "$(meta "$TX")" = 0:0:700 ] || exit 44
  regular "$TX/status" && [ "$(/system/bin/cat "$TX/status")" = APPLIED ] || exit 45
  if [ -e "$TX/target.context" ] || [ -L "$TX/target.context" ] || [ -e "$TX/client.uid" ] || [ -L "$TX/client.uid" ]; then
    regular "$TX/target.context" && [ "$(meta "$TX/target.context")" = 0:0:600 ] && [ "$(/system/bin/cat "$TX/target.context")" = "$CONTEXT" ] || exit 45
    regular "$TX/client.uid" && [ "$(meta "$TX/client.uid")" = 0:0:600 ] && [ "$(/system/bin/cat "$TX/client.uid")" = "$CLIENT_UID" ] || exit 45
  else
    # Legacy v49 and earlier transactions were bound to this exact UID and label.
    [ "$CLIENT_UID" = 10106 ] && [ "$CONTEXT" = u:object_r:system_data_file:s0 ] || exit 45
  fi
  regular "$TX/candidate.sha" && [ "$(/system/bin/cat "$TX/candidate.sha")" = "$CANDIDATE" ] || exit 46
  [ "$actual" = "$CANDIDATE" ] || exit 47
  regular "$TX/before.sha" && regular "$TX/before.mp4" || exit 48
  before=$(/system/bin/cat "$TX/before.sha") || exit 49
  [ "$(clbv_sha256 "$TX/before.mp4")" = "$before" ] || exit 50
  [ ! -e "$TX/new.mp4" ] && [ ! -L "$TX/new.mp4" ] || exit 51
  /system/bin/cat "$TX/before.mp4" > "$TX/new.mp4" || exit 52
  [ "$(clbv_sha256 "$TX/new.mp4")" = "$before" ] || exit 53
  wanted=$before
  result=RESTORED
fi
/system/bin/chown 0:0 "$TX/new.mp4" && /system/bin/chmod 644 "$TX/new.mp4" || exit 54
/system/bin/chcon "$CONTEXT" "$TX/new.mp4" || exit 55
label=$(/system/bin/ls -Zd "$TX/new.mp4") || exit 56
[ "${label%% *}" = "$CONTEXT" ] || exit 57
[ "$(/system/bin/stat -c %d "$TX/new.mp4")" = "$(/system/bin/stat -c %d /data/local)" ] || exit 58
if [ "$OP" = APPLY ]; then atomic_text "$TX/status" PREPARED || exit 59; fi
/system/bin/sync || exit 60
check_target && check_stock && [ "$(clbv_sha256 "$TARGET")" = "$actual" ] || exit 61
printf 'CLBV_BEFORE_SHA=%s\nCLBV_CANDIDATE_SHA=%s\n' "$before" "$CANDIDATE"
write_started=1
printf 'CLBV_TARGET_WRITE_ATTEMPTED\n'
if [ "$OP" = APPLY ]; then phase=APPLYING; else phase=RESTORING; fi
atomic_text "$TX/status" "$phase" || exit 62
/system/bin/sync || exit 63
/system/bin/mv "$TX/new.mp4" "$TARGET" || exit 64
check_target && check_stock && [ "$(clbv_sha256 "$TARGET")" = "$wanted" ] || exit 65
atomic_text "$BASE/current.sha" "$wanted" || exit 66
atomic_text "$TX/status" "$result" || exit 67
/system/bin/sync || exit 68
printf 'CLBV_AFTER_SHA=%s\nCLBV_RESULT=%s\n' "$wanted" "$result"
exit 0
