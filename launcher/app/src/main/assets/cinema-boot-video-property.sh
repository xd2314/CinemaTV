#!/system/bin/sh
# Firmware video_file route. One existing su, no remount/service control/reboot/retry.
TXID='@TX@'
OP='@OP@'
SOURCE='@SOURCE@'
CANDIDATE='@SHA@'
CLIENT_UID='@UID@'
EXPECTED_PROPERTY='@EXPECTED_PROPERTY@'
RESTORE_BEFORE='@RESTORE_BEFORE@'
RESTORE_PROPERTY='@RESTORE_PROPERTY@'
RESTORE_STOCK='@RESTORE_STOCK@'
KEY=persist.sys.bootanim.video_file
STOCK=/system/media/bootanimation.mp4
ACTIVE=/data/local/bootanim/bootanimation.mp4
PARENT=/data/local/bootanim
BASE=/data/local/cinema-lite-boot-video-property-v1
CONTEXT=u:object_r:system_data_file:s0
MEDIA_META=1013:0:400
write_started=0
locked=0
finish() {
  rc=$?
  if [ "$rc" -ne 0 ] && [ "$write_started" = 0 ]; then printf 'CLBV_NO_TARGET_WRITE\n'; fi
  if [ "$locked" = 1 ]; then /system/bin/rmdir "$BASE/.lock" >/dev/null 2>&1; fi
}
trap finish EXIT
regular() { [ -f "$1" ] && [ ! -L "$1" ]; }
meta() { /system/bin/stat -c '%u:%g:%a' "$1"; }
clbv_sha256() {
  value=$(/system/bin/sha256sum "$1") || return 1
  value=${value%% *}
  [ "${#value}" = 64 ] || return 1
  case "$value" in *[!a-f0-9]*) return 1;; esac
  printf '%s' "$value"
}
private_file() { regular "$1" && [ "$(meta "$1")" = 0:0:600 ]; }
read_private() { private_file "$1" && /system/bin/cat "$1"; }
atomic_text() {
  dest=$1
  if [ -e "$dest" ] || [ -L "$dest" ]; then private_file "$dest" || return 1; fi
  [ ! -e "$dest.next" ] && [ ! -L "$dest.next" ] || return 1
  (set -C; printf '%s\n' "$2" > "$dest.next") || return 1
  /system/bin/chmod 600 "$dest.next" && /system/bin/mv "$dest.next" "$dest"
}
size_ok() {
  length=$(/system/bin/stat -c %s "$1") || return 1
  [ "$length" -gt 0 ] && [ "$length" -le 20971520 ]
}
movie_ok() {
  regular "$1" && [ "$(meta "$1")" = "${3:-0:0:644}" ] && size_ok "$1" || return 1
  [ "$(/system/bin/readlink -f "$1")" = "$1" ] || return 1
  label=$(/system/bin/ls -Zd "$1") || return 1
  [ "${label%% *}" = "$2" ]
}
active_hash() {
  if [ -e "$PARENT" ] || [ -L "$PARENT" ]; then
    [ -d "$PARENT" ] && [ ! -L "$PARENT" ] || return 1
    case "$(meta "$PARENT")" in 0:0:751|0:0:755) ;; *) return 1;; esac
    [ "$(/system/bin/readlink -f "$PARENT")" = "$PARENT" ] || return 1
    label=$(/system/bin/ls -Zd "$PARENT") || return 1
    [ "${label%% *}" = "$CONTEXT" ] || return 1
  fi
  if [ -e "$ACTIVE" ] || [ -L "$ACTIVE" ]; then
    found_meta=$(meta "$ACTIVE") || return 1
    case "$found_meta" in 0:0:644|"$MEDIA_META") ;; *) return 1;; esac
    movie_ok "$ACTIVE" "$CONTEXT" "$found_meta" && clbv_sha256 "$ACTIVE"
  else printf ABSENT; fi
}
active_metadata() { if [ -e "$ACTIVE" ] || [ -L "$ACTIVE" ]; then meta "$ACTIVE"; else printf ABSENT; fi; }
stock_ok() { movie_ok "$STOCK" u:object_r:system_file:s0 && [ "$(clbv_sha256 "$STOCK")" = "$stock" ]; }
unchanged() {
  stock_ok && [ "$(/system/bin/getprop "$KEY")" = "$property" ] && [ "$(active_hash)" = "$active" ] && [ "$(active_metadata)" = "$active_meta" ]
}
umask 077
[ "$(/system/bin/id -u)" = 0 ] || exit 11
printf 'CLBV_UID=0\nCLBV_CLIENT_UID=%s\nCLBV_TX=%s\nCLBV_ROUTE=PROPERTY_MP4_V1\n' "$CLIENT_UID" "$TXID"
case "$CLIENT_UID" in *[!0-9]*|'') exit 12;; esac
[ "$CLIENT_UID" -ge 10000 ] && [ "$CLIENT_UID" -le 19999 ] || exit 12
case "$OP" in APPLY|RESTORE) ;; *) exit 13;; esac
case "$EXPECTED_PROPERTY" in "$STOCK"|"$ACTIVE") ;; *) exit 14;; esac
[ -d /data/local ] && [ ! -L /data/local ] || exit 15
[ "$(/system/bin/readlink -f /data/local)" = /data/local ] || exit 15
case "$(meta /data/local)" in 0:0:751|0:0:755) ;; *) exit 15;; esac
label=$(/system/bin/ls -Zd /data/local) || exit 15
[ "${label%% *}" = "$CONTEXT" ] || exit 15
movie_ok "$STOCK" u:object_r:system_file:s0 || exit 16
stock=$(clbv_sha256 "$STOCK") || exit 16
property=$(/system/bin/getprop "$KEY") || exit 17
[ "$property" = "$EXPECTED_PROPERTY" ] || exit 17
active=$(active_hash) || exit 18
active_meta=$(active_metadata) || exit 18
before=$stock
if [ "$property" = "$ACTIVE" ]; then [ "$active" != ABSENT ] || exit 18; before=$active; fi
space=$(/system/bin/stat -f -c '%a:%S' /data/local) || exit 19
available=${space%%:*}; block_size=${space#*:}
case "$available:$block_size" in *[!0-9:]*|:*) exit 19;; esac
[ "$block_size" -gt 0 ] && [ "$block_size" -le 1048576 ] || exit 19
[ "$available" -ge "$(((67108864 + block_size - 1) / block_size))" ] || exit 19
if [ "$OP" = APPLY ]; then
  # This reader split is a workaround for one verified inverted access(R_OK) branch.
  # Pin the code and both service identities; never apply it to an unknown firmware.
  [ "$(clbv_sha256 /system/lib/libbootanimation.so)" = 4a4b7f2844c196126fc1091eb64b8591bdc5ef6745855b51a107bf05cdc83155 ] &&
    [ "$(clbv_sha256 /system/lib/libmedia.so)" = 85045e06b88508d21f0f4b6bb8a10fedb29cff2f510d34b3367d45e7329315ac ] &&
    [ "$(clbv_sha256 /system/etc/init/bootanim.rc)" = 15c1c6e04c30c1e7e84e2748b933b6eb435740b2f89b5ae399b1b5cbdd85d931 ] &&
    [ "$(clbv_sha256 /system/etc/init/mediaserver.rc)" = c4845c6207dddc77c8f2680d0eb17f56bf69e4bb865bd134c1ea38f059de74c1 ] || exit 80
  [ "$(/system/bin/id -u graphics)" = 1003 ] && [ "$(/system/bin/id -u media)" = 1013 ] && [ "$(/system/bin/getenforce)" = Permissive ] || exit 81
  printf 'CLBV_READER_PROFILE=MEDIA_UID_0400_V1\n'
  printf 'CLBV_SOURCE_PATH=%s\n' "$SOURCE"
  regular "$SOURCE" || { printf 'CLBV_SOURCE_CHECK=NOT_REGULAR\n'; exit 20; }
  source_canonical=$(/system/bin/readlink -f "$SOURCE") || { printf 'CLBV_SOURCE_CHECK=CANONICAL_READ_FAILED\n'; exit 20; }
  printf 'CLBV_SOURCE_CANONICAL=%s\n' "$source_canonical"
  [ "$source_canonical" = "$SOURCE" ] || { printf 'CLBV_SOURCE_CHECK=CANONICAL_MISMATCH\n'; exit 20; }
  size_ok "$SOURCE" || { printf 'CLBV_SOURCE_CHECK=INVALID_SIZE\n'; exit 20; }
  printf 'CLBV_SOURCE_BYTES=%s\n' "$length"
  source_uid=$(/system/bin/stat -c %u "$SOURCE") || { printf 'CLBV_SOURCE_CHECK=UID_READ_FAILED\n'; exit 20; }
  printf 'CLBV_SOURCE_UID=%s\n' "$source_uid"
  [ "$source_uid" = "$CLIENT_UID" ] || { printf 'CLBV_SOURCE_CHECK=UID_MISMATCH\n'; exit 20; }
  source_sha=$(clbv_sha256 "$SOURCE") || { printf 'CLBV_SOURCE_CHECK=SHA_READ_FAILED\n'; exit 20; }
  printf 'CLBV_SOURCE_SHA=%s\n' "$source_sha"
  [ "$source_sha" = "$CANDIDATE" ] || { printf 'CLBV_SOURCE_CHECK=SHA_MISMATCH\n'; exit 20; }
  printf 'CLBV_SOURCE_CHECK=VERIFIED\n'
fi
[ ! -L "$BASE" ] || exit 21
if [ -e "$BASE" ]; then
  [ -d "$BASE" ] && [ "$(meta "$BASE")" = 0:0:700 ] || exit 21
else
  [ "$OP" = APPLY ] || exit 21
  /system/bin/mkdir "$BASE" && /system/bin/chown 0:0 "$BASE" && /system/bin/chmod 700 "$BASE" || exit 21
  atomic_text "$BASE/current.prop" "$property" && atomic_text "$BASE/current.sha" "$before" && atomic_text "$BASE/active.sha" "$active" && atomic_text "$BASE/current.meta" "$active_meta" && atomic_text "$BASE/stock.sha" "$stock" && atomic_text "$BASE/latest.id" - && atomic_text "$BASE/state" READY || exit 22
fi
[ "$(/system/bin/readlink -f "$BASE")" = "$BASE" ] || exit 23
/system/bin/mkdir "$BASE/.lock" || exit 24
locked=1
state=$(read_private "$BASE/state") || exit 25
case "$state" in READY|APPLIED|RESTORED) ;; *) exit 25;; esac
[ "$(read_private "$BASE/current.prop")" = "$property" ] && [ "$(read_private "$BASE/current.sha")" = "$before" ] && [ "$(read_private "$BASE/active.sha")" = "$active" ] && [ "$(read_private "$BASE/stock.sha")" = "$stock" ] || exit 26
if [ -e "$BASE/current.meta" ] || [ -L "$BASE/current.meta" ]; then
  [ "$(read_private "$BASE/current.meta")" = "$active_meta" ] || exit 26
else
  # Old v55-v59 heads only supported root:root 644 or absence. Preserve their data.
  case "$active_meta" in 0:0:644|ABSENT) ;; *) exit 26;; esac
  atomic_text "$BASE/current.meta" "$active_meta" || exit 26
fi
TX=$BASE/$TXID
if [ "$OP" = APPLY ]; then
  [ ! -e "$TX" ] && [ ! -L "$TX" ] || exit 27
  /system/bin/mkdir "$TX" && /system/bin/chmod 700 "$TX" || exit 28
  /system/bin/cat "$STOCK" > "$TX/stock.mp4" || exit 29
  [ "$(clbv_sha256 "$TX/stock.mp4")" = "$stock" ] || exit 29
  if [ "$active" != ABSENT ]; then
    /system/bin/cat "$ACTIVE" > "$TX/before-active.mp4" || exit 30
    [ "$(clbv_sha256 "$TX/before-active.mp4")" = "$active" ] || exit 30
  fi
  atomic_text "$TX/before.prop" "$property" && atomic_text "$TX/before.sha" "$before" && atomic_text "$TX/active.sha" "$active" && atomic_text "$TX/stock.sha" "$stock" && atomic_text "$TX/client.uid" "$CLIENT_UID" && atomic_text "$TX/candidate.sha" "$CANDIDATE" && atomic_text "$TX/active.metadata" "$active_meta:$CONTEXT" || exit 31
  /system/bin/cat "$SOURCE" > "$TX/new.mp4" || exit 32
  [ "$(clbv_sha256 "$TX/new.mp4")" = "$CANDIDATE" ] || exit 32
  wanted=$CANDIDATE; wanted_property=$ACTIVE; wanted_active=$CANDIDATE; wanted_meta=$MEDIA_META; result=APPLIED; phase=APPLYING
  atomic_text "$TX/status" PREPARED || exit 33
else
  [ "$state" = APPLIED ] && [ "$(read_private "$BASE/latest.id")" = "$TXID" ] || exit 34
  [ "$property" = "$ACTIVE" ] && [ "$active" = "$CANDIDATE" ] || exit 35
  [ ! -L "$TX" ] && [ -d "$TX" ] && [ "$(meta "$TX")" = 0:0:700 ] || exit 36
  [ "$(read_private "$TX/status")" = APPLIED ] && [ "$(read_private "$TX/client.uid")" = "$CLIENT_UID" ] && [ "$(read_private "$TX/candidate.sha")" = "$CANDIDATE" ] || exit 37
  before=$(read_private "$TX/before.sha") && wanted_property=$(read_private "$TX/before.prop") && wanted_active=$(read_private "$TX/active.sha") || exit 38
  [ "$before" = "$RESTORE_BEFORE" ] && [ "$wanted_property" = "$RESTORE_PROPERTY" ] && [ "$stock" = "$RESTORE_STOCK" ] && [ "$(read_private "$TX/stock.sha")" = "$stock" ] || exit 39
  private_file "$TX/stock.mp4" && [ "$(clbv_sha256 "$TX/stock.mp4")" = "$stock" ] || exit 40
  case "$wanted_property" in "$STOCK") [ "$before" = "$stock" ] || exit 41;; "$ACTIVE") [ "$before" = "$wanted_active" ] && [ "$wanted_active" != ABSENT ] || exit 41;; *) exit 41;; esac
  saved_meta=$(read_private "$TX/active.metadata") || exit 42
  if [ "$wanted_active" = ABSENT ]; then
    case "$saved_meta" in "ABSENT:$CONTEXT"|"0:0:644:$CONTEXT") wanted_meta=ABSENT;; *) exit 42;; esac
  else
    case "$saved_meta" in "0:0:644:$CONTEXT") wanted_meta=0:0:644;; "$MEDIA_META:$CONTEXT") wanted_meta=$MEDIA_META;; *) exit 42;; esac
  fi
  [ ! -e "$TX/new.mp4" ] && [ ! -L "$TX/new.mp4" ] || exit 43
  if [ "$wanted_active" != ABSENT ]; then
    private_file "$TX/before-active.mp4" && [ "$(clbv_sha256 "$TX/before-active.mp4")" = "$wanted_active" ] || exit 44
    /system/bin/cat "$TX/before-active.mp4" > "$TX/new.mp4" || exit 45
    [ "$(clbv_sha256 "$TX/new.mp4")" = "$wanted_active" ] || exit 45
  fi
  wanted=$before; result=RESTORED; phase=RESTORING
fi
if [ "$wanted_active" != ABSENT ]; then
  case "$wanted_meta" in 0:0:644) /system/bin/chown 0:0 "$TX/new.mp4" && /system/bin/chmod 644 "$TX/new.mp4" || exit 46;;
    "$MEDIA_META") /system/bin/chown 1013:0 "$TX/new.mp4" && /system/bin/chmod 400 "$TX/new.mp4" || exit 46;; *) exit 46;; esac
  /system/bin/chcon "$CONTEXT" "$TX/new.mp4" || exit 46
  movie_ok "$TX/new.mp4" "$CONTEXT" "$wanted_meta" || exit 46
  [ "$(/system/bin/stat -c %d "$TX/new.mp4")" = "$(/system/bin/stat -c %d /data/local)" ] || exit 46
fi
/system/bin/sync && unchanged || exit 47
printf 'CLBV_BEFORE_SHA=%s\nCLBV_CANDIDATE_SHA=%s\nCLBV_PROPERTY_BEFORE=%s\nCLBV_STOCK_SHA=%s\n' "$before" "$CANDIDATE" "$property" "$stock"
# Both durable stores stay pending after any interruption. No automatic rollback/retry.
write_started=1
printf 'CLBV_TARGET_WRITE_ATTEMPTED\n'
atomic_text "$BASE/state" "$phase" && atomic_text "$TX/status" "$phase" && /system/bin/sync || exit 48
if [ "$OP" = RESTORE ] && [ "$wanted_property" = "$STOCK" ]; then
  /system/bin/setprop "$KEY" "$STOCK" && [ "$(/system/bin/getprop "$KEY")" = "$STOCK" ] || exit 49
fi
if [ ! -e "$PARENT" ]; then
  /system/bin/mkdir "$PARENT" && /system/bin/chown 0:0 "$PARENT" && /system/bin/chmod 751 "$PARENT" && /system/bin/chcon "$CONTEXT" "$PARENT" || exit 50
fi
if [ "$wanted_active" = ABSENT ]; then /system/bin/rm "$ACTIVE" || exit 51
else /system/bin/mv "$TX/new.mp4" "$ACTIVE" || exit 51; fi
/system/bin/sync || exit 52
/system/bin/setprop "$KEY" "$wanted_property" || exit 53
[ "$(/system/bin/getprop "$KEY")" = "$wanted_property" ] && [ "$(active_hash)" = "$wanted_active" ] && stock_ok || exit 54
[ "$(active_metadata)" = "$wanted_meta" ] || exit 54
atomic_text "$BASE/current.prop" "$wanted_property" && atomic_text "$BASE/current.sha" "$wanted" && atomic_text "$BASE/active.sha" "$wanted_active" && atomic_text "$BASE/current.meta" "$wanted_meta" && atomic_text "$BASE/latest.id" "$TXID" && atomic_text "$TX/status" "$result" && atomic_text "$BASE/state" "$result" && /system/bin/sync || exit 55
printf 'CLBV_TARGET_METADATA_AFTER=%s\n' "$wanted_meta"
printf 'CLBV_AFTER_SHA=%s\nCLBV_PROPERTY_AFTER=%s\nCLBV_RESULT=%s\n' "$wanted" "$wanted_property" "$result"
exit 0
