package com.gathin.tvhome;

import android.content.Context;
import java.io.*;
import java.nio.file.Files;
import java.util.UUID;
import org.json.JSONObject;

/** Read-only candidate logic. Public builds use synthetic legacy bindings for tests only; no private incident records are distributed. Never dispatches Root. */
final class BootLogoCandidateReview {
    static final String OLD_ID="00000000-0000-4000-8000-000000000001",OLD_SOURCE="1111111111111111111111111111111111111111111111111111111111111111";
    static final String OLD_ASSET="00000000-0000-4000-8000-000000000002",BEFORE="2222222222222222222222222222222222222222222222222222222222222222";
    private BootLogoCandidateReview(){}
    static boolean eligible(String receipt,String probe,String exported,BootAssetStore.Asset asset,int uid){
        try{
            if(uid!=10092||asset==null||asset.target!=BootAssetStore.Target.LOGO||!asset.sha.equals(OLD_SOURCE)||!BootVideoRoot.uuid(asset.id)||!BootLogoProbeExport.eligible(receipt,probe,uid))return false;
            JSONObject r=new JSONObject(receipt),p=new JSONObject(probe).getJSONObject("result"),e=new JSONObject(exported);
            if(r.length()!=8||r.getLong("at")!=1700000000000L||!r.getString("transaction").equals(OLD_ID))return false;
            return r.getString("sourceSha").equals(OLD_SOURCE)&&r.getString("sourceAssetId").equals(OLD_ASSET)
                &&p.getString("sha").equals(BEFORE)&&p.getString("device").equals("/dev/block/mmcblk0p1")&&p.getString("metadata").equals("b3:1:0:0:600")
                &&e.getString("state").equals("FINISHED")&&e.getString("transaction").equals(OLD_ID)&&e.getJSONObject("result").getBoolean("readOnlyBackup")
                &&e.getJSONObject("result").getLong("bytes")==BootLogoLayout.PHYSICAL_BYTES&&e.getJSONObject("result").getString("sha").equals(BEFORE);
        }catch(Exception invalid){return false;}
    }
    static void copy(File from,File to)throws Exception{if(!to.createNewFile())throw new IOException("Candidate destination already exists");try(InputStream in=new FileInputStream(from);FileOutputStream out=new FileOutputStream(to)){byte[] b=new byte[65536];long size=0;int n;while((n=in.read(b))!=-1){size+=n;if(size>BootLogoLayout.PHYSICAL_BYTES)throw new IOException("Review copy is too large");out.write(b,0,n);}if(size==0)throw new IOException("Empty review copy");out.getFD().sync();}}
    static JSONObject build(File dir,File backup,BootAssetStore.Asset asset,BootLogoTransactions.ReadResult read,String original)throws Exception{
        if(!read.valid()||backup.length()!=BootLogoLayout.PHYSICAL_BYTES||!BootAssetStore.hash(backup).equals(read.sha)||!BootAssetStore.hash(asset.output).equals(asset.sha))throw new IOException("Candidate input proof changed");
        if(!dir.mkdir())throw new IOException("Candidate review directory exists");
        copy(backup,new File(dir,"before.img"));copy(asset.output,new File(dir,"source.png"));
        if(!BootAssetStore.hash(new File(dir,"source.png")).equals(asset.sha))throw new IOException("Source changed during candidate copy");
        BootLogoLayout.Candidate c=BootLogoLayout.build(new File(dir,"before.img"),BootLogoTransactions.rgb(new File(dir,"source.png")));
        if(!c.beforeSha.equals(read.sha))throw new IOException("Backup changed while building candidate");
        BootLogoTransactions.writeNew(new File(dir,"candidate-window.bin"),c.candidateWindow);BootLogoTransactions.writeNew(new File(dir,"restore-window.bin"),c.originalWindow);
        return new JSONObject().put("state","PREPARED_READ_ONLY").put("reviewId",dir.getName()).put("clientUid",read.clientUid).put("originalReceipt",new JSONObject(original))
            .put("sourceSha",asset.sha).put("sourceAssetId",asset.id).put("beforeSha",c.beforeSha).put("afterSha",c.afterSha).put("oldWindowSha",c.originalWindowSha).put("newWindowSha",c.candidateWindowSha)
            .put("seek",c.layout.windowStart/512).put("count",(c.layout.windowEnd-c.layout.windowStart)/512).put("device",read.device).put("nodeMeta",read.meta).put("readDiagnostic",read.diagnostic).put("preparedAt",System.currentTimeMillis());
    }
    static void verify(File dir,JSONObject r)throws Exception{
        if(!BootVideoRoot.uuid(r.getString("reviewId"))||!dir.getName().equals(r.getString("reviewId"))||!r.getString("state").equals("PREPARED_READ_ONLY"))throw new IOException("Invalid candidate review");
        BootLogoTransactions.ReadResult read=new BootLogoTransactions.ReadResult(r.getString("beforeSha"),r.getString("nodeMeta"),r.getString("readDiagnostic"),r.getInt("clientUid"),r.getString("device"));if(!read.valid())throw new IOException("Invalid read proof");
        File before=new File(dir,"before.img"),source=new File(dir,"source.png");if(before.length()!=BootLogoLayout.PHYSICAL_BYTES||!BootAssetStore.hash(before).equals(r.getString("beforeSha"))||!BootAssetStore.hash(source).equals(r.getString("sourceSha")))throw new IOException("Review source/backup changed");
        BootLogoLayout.Candidate c=BootLogoLayout.build(before,BootLogoTransactions.rgb(source));
        if(!c.afterSha.equals(r.getString("afterSha"))||!c.originalWindowSha.equals(r.getString("oldWindowSha"))||!c.candidateWindowSha.equals(r.getString("newWindowSha"))
            ||c.layout.windowStart/512!=r.getInt("seek")||(c.layout.windowEnd-c.layout.windowStart)/512!=r.getInt("count")
            ||!BootAssetStore.hash(new File(dir,"candidate-window.bin")).equals(c.candidateWindowSha)||!BootAssetStore.hash(new File(dir,"restore-window.bin")).equals(c.originalWindowSha))throw new IOException("Review candidate changed");
    }
    static JSONObject prepare(Context context,String original,String probe,String exported,BootAssetStore.Asset asset)throws Exception{
        if(!eligible(original,probe,exported,asset,android.os.Process.myUid()))throw new IOException("This transaction is not eligible for a read-only candidate review");
        File allowed=new File(new File(new File(context.getFilesDir(),"boot_asset_preparation"),asset.id),"prepared.png");if(!allowed.getCanonicalPath().equals(asset.output.getCanonicalPath()))throw new IOException("Prepared source path changed");
        File backup=new File(new File(new File(context.getFilesDir(),"boot_logo_read_probe_v1"),OLD_ID),"before.img");BootLogoProbeExport.validate(backup,original,probe,android.os.Process.myUid());
        File root=new File(context.getFilesDir(),"boot_logo_candidate_reviews");if(!root.isDirectory()&&!root.mkdirs())throw new IOException("No review folder");if(root.getUsableSpace()<128L*1024*1024)throw new IOException("Insufficient review space");
        JSONObject p=new JSONObject(probe).getJSONObject("result");BootLogoTransactions.ReadResult read=new BootLogoTransactions.ReadResult(p.getString("sha"),p.getString("metadata"),p.getString("diagnostic"),android.os.Process.myUid(),p.getString("device"));
        JSONObject result=build(new File(root,UUID.randomUUID().toString()),backup,asset,read,original);return result.put("readProbeEvidence",new JSONObject(probe)).put("backupExportEvidence",new JSONObject(exported));
    }
    static final class Ready {
        final File directory;final JSONObject record;final String original;
        private Ready(File directory,JSONObject record,String original){this.directory=directory;this.record=record;this.original=original;}
    }
    static Ready audit(Context context,String original,String probe,String exported,String review,BootAssetStore.Asset asset)throws Exception{
        if(!eligible(original,probe,exported,asset,android.os.Process.myUid()))throw new IOException("Manual review binding changed");
        JSONObject r=new JSONObject(review);String id=r.getString("reviewId");if(!BootVideoRoot.uuid(id)||!r.getJSONObject("originalReceipt").toString().equals(new JSONObject(original).toString())
            ||!r.getJSONObject("readProbeEvidence").toString().equals(new JSONObject(probe).toString())||!r.getJSONObject("backupExportEvidence").toString().equals(new JSONObject(exported).toString())
            ||!r.getString("sourceSha").equals(asset.sha)||!r.getString("sourceAssetId").equals(asset.id)||!BootAssetStore.hash(asset.output).equals(asset.sha))throw new IOException("Review evidence changed");
        File dir=new File(new File(context.getFilesDir(),"boot_logo_candidate_reviews"),id);if(!dir.getCanonicalFile().equals(new File(dir.getParentFile().getCanonicalFile(),id)))throw new IOException("Review path changed");verify(dir,r);return new Ready(dir,r,original);
    }
}
