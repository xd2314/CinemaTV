#!/system/bin/sh
# Read the whole confirmed resource block to stdout only. No device backup or write.
CLIENT_UID='@UID@'
DEVICE=
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
[ "$(/system/bin/id -u)" = 0 ] || exit 10
case "$CLIENT_UID" in *[!0-9]*|'') exit 11;; esac
[ "$CLIENT_UID" -ge 10000 ] && [ "$CLIENT_UID" -le 19999 ] || exit 11
DEVICE=$(clbl_device) || exit 15
case "$DEVICE" in /dev/block/*) ;; *) exit 15;; esac
NODE=${DEVICE#/dev/block/}
case "$NODE" in ''|*[!a-zA-Z0-9_-]*) exit 15;; esac
[ -n "$NODE" ] && [ -b "$DEVICE" ] || exit 15
[ "$(/system/bin/cat "/sys/class/block/$NODE/size")" = 65536 ] || exit 16
metadata=$(/system/bin/stat -c '%t:%T:%u:%g:%a' "$DEVICE") || exit 17
before=$(clbl_sha256 "$DEVICE") || exit 18
printf 'CLBL_UID=0\nCLBL_CLIENT_UID=%s\nCLBL_DEVICE=%s\nCLBL_META=%s\nCLBL_BYTES=33554432\nCLBL_SHA_BEFORE=%s\nCLBL_DATA_BEGIN\n' "$CLIENT_UID" "$DEVICE" "$metadata" "$before"
/system/bin/cat "$DEVICE" || exit 19
printf '\nCLBL_DATA_END\n'
[ "$(clbl_device)" = "$DEVICE" ] && [ "$(/system/bin/stat -c '%t:%T:%u:%g:%a' "$DEVICE")" = "$metadata" ] || exit 20
after=$(clbl_sha256 "$DEVICE") || exit 20
printf 'CLBL_SHA_AFTER=%s\n' "$after"
[ "$before" = "$after" ] || exit 21
printf 'CLBL_RESULT=READ_COMPLETE\n'
exit 0
