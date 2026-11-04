package com.gathin.tvhome;

import java.io.File;
import java.io.IOException;
import org.json.JSONObject;

/** Separate, explicitly confirmed, one-shot read-only probe. Never unlocks a transaction. */
final class BootVideoSourceProbe {
    private BootVideoSourceProbe(){}
    static boolean eligible(String json,BootAssetStore.Asset asset,int uid){
        try{
            JSONObject receipt=new JSONObject(json);String id=receipt.getString("transaction");
            return BootDeviceSupport.appUid(uid)&&asset!=null&&asset.target==BootAssetStore.Target.VIDEO
                &&receipt.getInt("schema")==1&&receipt.getInt("clientUid")==uid&&receipt.getString("state").equals("FAILED_NO_TARGET_WRITE")
                &&receipt.getString("action").equals("APPLY")&&receipt.getInt("exit")==20&&!receipt.getBoolean("timeout")&&!receipt.has("lastId")
                &&BootVideoRoot.uuid(id)&&BootVideoRoot.sha(asset.sha)&&asset.sha.equals(receipt.getString("candidateSha"))
                &&(receipt.getString("route").equals(BootVideoRoute.AUTO)||receipt.getString("route").equals(BootVideoRoute.PROPERTY))
                &&receipt.getString("diagnostic").equals("CLBV_UID=0\nCLBV_CLIENT_UID="+uid+"\nCLBV_TX="+id+"\nCLBV_ROUTE=PROPERTY_MP4_V1\nCLBV_NO_TARGET_WRITE\n");
        }catch(Exception invalid){return false;}
    }
    static String payload(BootVideoTransactions.Request request)throws IOException{
        String template="SOURCE='@SOURCE@'\n"+
            "printf 'CLVS_CLIENT_UID=@UID@\\nCLVS_TX=@TX@\\nCLVS_CANDIDATE_SHA=@SHA@\\n'\n"+
            "printf 'CLVS_UID='; /system/bin/id -u\n"+
            "printf 'CLVS_ID='; /system/bin/id\n"+
            "printf 'CLVS_PROCESS_STATUS\\n'; /system/bin/cat /proc/self/status\n"+
            "printf 'CLVS_SOURCE=%s\\n' \"$SOURCE\"\n"+
            "if [ -f \"$SOURCE\" ]; then printf 'CLVS_REGULAR=1\\n'; else printf 'CLVS_REGULAR=0\\n'; fi\n"+
            "if [ -L \"$SOURCE\" ]; then printf 'CLVS_SYMLINK=1\\n'; else printf 'CLVS_SYMLINK=0\\n'; fi\n"+
            "printf 'CLVS_CANONICAL\\n'; /system/bin/readlink -f \"$SOURCE\"\n"+
            "printf 'CLVS_METADATA\\n'; /system/bin/stat -c '%u:%g:%a:%s' \"$SOURCE\"\n"+
            "printf 'CLVS_SHA256\\n'; /system/bin/sha256sum \"$SOURCE\"\n"+
            "printf 'CLVS_ANCESTORS\\n'; /system/bin/ls -ldZ /data /data/data /data/user /data/user/0\n"+
            "/system/bin/ls -ldZ \"${SOURCE%/files/*}\" \"${SOURCE%/boot_asset_preparation/*}\" \"${SOURCE%/*/*}\" \"${SOURCE%/*}\" \"$SOURCE\"\n"+
            "printf 'CLVS_COMPLETE=1\\n'\nexit 0\n";
        if(!request.apply)throw new IOException("Source probe requires an existing prepared source");
        return BootVideoRoot.script(template,request);
    }
    static BootVideoTransactions.Result run(String receipt,BootAssetStore.Asset asset,int uid)throws Exception{
        if(!eligible(receipt,asset,uid)||uid!=android.os.Process.myUid())throw new IOException("Source probe binding changed");
        File file=asset.output;String source=file.getCanonicalPath();
        if(file.length()<=0||file.length()>BootAssetStore.MAX_BYTES||!BootAssetStore.hash(file).equals(asset.sha))throw new IOException("Source changed; no Root sent");
        String script=payload(new BootVideoTransactions.Request(true,new JSONObject(receipt).getString("transaction"),asset.sha,source,uid));
        String su=CinemaAdbProcess.findSu();if(su==null)throw new IOException("No existing su; no Root sent");
        return BootVideoRoot.execute(su,script);
    }
}
