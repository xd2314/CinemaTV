package com.gathin.tvhome;

/** UI eligibility only. It is never proof that a file or a partition can be written.
 * The explicitly confirmed su 0 operation validates live paths before any target write.
 * Owner-user app data paths are the only supported staging locations. */
final class BootDeviceSupport {
    private BootDeviceSupport() {}
    static boolean appUid(int uid) { return uid >= 10000 && uid <= 19999; }
    static boolean rootMaterials(int sdk, int uid, String pkg) {
        return sdk >= 26 && appUid(uid) && "com.gathin.tvhome.cinemalite.dev".equals(pkg);
    }
    static boolean wallpaper(int sdk, int uid, String pkg) {
        return sdk >= 29 && sdk <= 32 && rootMaterials(sdk, uid, pkg);
    }
    static boolean blockPath(String value) {
        // Only canonical block device basenames; no traversal, whitespace, or shell syntax.
        return value != null && value.matches("/dev/block/[a-zA-Z0-9_-]+")
            && !value.endsWith("/by-name");
    }
}
