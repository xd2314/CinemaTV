package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class BootDeviceSupportTest {
    @Test public void anotherOwnerUserAppUidIsNotAnOldDeviceAllowlist(){
        assertTrue(BootDeviceSupport.rootMaterials(29,10234,"com.gathin.tvhome.cinemalite.dev"));
        assertTrue(BootDeviceSupport.rootMaterials(30,10234,"com.gathin.tvhome.cinemalite.dev"));
        for(int uid:new int[]{0,2000,9999,20000,110234})assertFalse(BootDeviceSupport.rootMaterials(29,uid,"com.gathin.tvhome.cinemalite.dev"));
        assertFalse(BootDeviceSupport.rootMaterials(29,10234,"another.package"));
    }
    @Test public void ordinaryWallpaperBackupSupportsAndroid10Through12Only(){
        assertTrue(BootDeviceSupport.wallpaper(29,10234,"com.gathin.tvhome.cinemalite.dev"));
        for(int sdk:new int[]{29,30,31,32})assertTrue(BootDeviceSupport.wallpaper(sdk,10234,"com.gathin.tvhome.cinemalite.dev"));
        for(int sdk:new int[]{28,33,34,35})assertFalse(BootDeviceSupport.wallpaper(sdk,10234,"com.gathin.tvhome.cinemalite.dev"));
    }
    @Test public void canonicalBlockNodeCannotInjectOrTraverse(){
        assertTrue(BootDeviceSupport.blockPath("/dev/block/mmcblk1p2"));
        assertTrue(BootDeviceSupport.blockPath("/dev/block/sda1"));
        for(String path:new String[]{"/dev/block/../etc", "/dev/block/a;reboot", "/dev/block/a\n", "/dev/block/by-name/bootloader", "/data/image", "", null})assertFalse(BootDeviceSupport.blockPath(path));
    }
}
