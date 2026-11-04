package com.gathin.tvhome;

import android.content.Context;
import java.io.File;
import java.nio.file.Files;
import org.json.JSONObject;

/** Separate read-only evidence lane. It never changes or unlocks the original transaction. */
final class BootLogoReadProbe {
    private BootLogoReadProbe(){}
    static boolean eligible(String text,int uid){
        try{JSONObject r=new JSONObject(text);
            return BootDeviceSupport.appUid(uid)&&r.getInt("schema")==1&&r.getInt("clientUid")==uid
                &&r.getString("state").equals("UNKNOWN")&&BootVideoRoot.uuid(r.getString("transaction"))
                &&BootVideoRoot.sha(r.getString("sourceSha"))&&BootVideoRoot.uuid(r.getString("sourceAssetId"))
                &&r.getString("diagnostic").equals("IOException: Root read incomplete; do not resend")
                &&!r.has("beforeSha")&&!r.has("afterSha")&&!r.has("seek")&&!r.has("count")&&!r.has("readDiagnostic")&&!r.has("nodeMeta");
        }catch(Exception invalid){return false;}
    }
    static JSONObject run(Context context,String receipt)throws Exception{
        if(!eligible(receipt,android.os.Process.myUid()))throw new java.io.IOException("Receipt binding changed; no Root sent");
        String id=new JSONObject(receipt).getString("transaction");File parent=new File(context.getFilesDir(),"boot_logo_read_probe_v1"),dir=new File(parent,id);
        if(!parent.isDirectory()&&!parent.mkdirs()||!dir.mkdir())throw new java.io.IOException("Probe directory unavailable; no Root sent");
        if(dir.getUsableSpace()<128L*1024*1024)throw new java.io.IOException("Insufficient backup space; no Root sent");
        File backup=new File(dir,"before.img");BootLogoTransactions.ReadResult read=new BootLogoRoot(context).read(backup);
        if(!read.valid()||read.clientUid!=android.os.Process.myUid()||backup.length()!=BootLogoLayout.PHYSICAL_BYTES||!BootAssetStore.hash(backup).equals(read.sha))throw new java.io.IOException("Incomplete backup proof");
        JSONObject result=new JSONObject().put("device",read.device).put("metadata",read.meta).put("sha",read.sha).put("bytes",backup.length()).put("diagnostic",read.diagnostic);
        try{BootLogoLayout l=BootLogoLayout.parse(backup);result.put("layoutSupported",true).put("windowStart",l.windowStart).put("windowEnd",l.windowEnd);}
        catch(java.io.IOException unsupported){result.put("layoutSupported",false).put("layoutError",unsupported.getMessage());}
        return result;
    }
}
