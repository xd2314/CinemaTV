package com.gathin.tvhome;

import android.system.Os;
import java.io.File;

/** Exact firmware contract: bootanim's readable-path branch falls back to stock.
 * Its MediaPlayer URL is opened by mediaserver UID1013, rather than graphics UID1003.
 * Only this pinned library/service configuration may use the media-owner read mode.
 */
final class BootVideoReaderProfile {
    static final String NAME="MEDIA_UID_0400_V1",METADATA="1013:0:400";
    static final String LIBRARY="/system/lib/libbootanimation.so",BOOT_RC="/system/etc/init/bootanim.rc",MEDIA_RC="/system/etc/init/mediaserver.rc";
    static final String LIB_SHA="4a4b7f2844c196126fc1091eb64b8591bdc5ef6745855b51a107bf05cdc83155";
    static final String MEDIA_LIBRARY="/system/lib/libmedia.so",MEDIA_LIB_SHA="85045e06b88508d21f0f4b6bb8a10fedb29cff2f510d34b3367d45e7329315ac";
    static final String BOOT_SHA="15c1c6e04c30c1e7e84e2748b933b6eb435740b2f89b5ae399b1b5cbdd85d931";
    static final String MEDIA_SHA="c4845c6207dddc77c8f2680d0eb17f56bf69e4bb865bd134c1ea38f059de74c1";
    private BootVideoReaderProfile(){}
    static boolean matches(String lib,String player,String boot,String media){return LIB_SHA.equals(lib)&&MEDIA_LIB_SHA.equals(player)&&BOOT_SHA.equals(boot)&&MEDIA_SHA.equals(media);}
    static boolean supported(){try{return matches(BootAssetStore.hash(new File(LIBRARY)),BootAssetStore.hash(new File(MEDIA_LIBRARY)),BootAssetStore.hash(new File(BOOT_RC)),BootAssetStore.hash(new File(MEDIA_RC)));}catch(Exception unavailable){return false;}}
    static boolean protectedTarget(File file){try{android.system.StructStat s=Os.lstat(file.getPath());return android.system.OsConstants.S_ISREG(s.st_mode)&&s.st_uid==1013&&s.st_gid==0&&(s.st_mode&07777)==0400;}catch(Exception invalid){return false;}}
    static boolean proof(BootVideoTransactions.Result result){return NAME.equals(result.value("CLBV_READER_PROFILE"))&&METADATA.equals(result.value("CLBV_TARGET_METADATA_AFTER"));}
}
