package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Separate read/prepare, apply and restore confirmations. A failed/unknown lane never retries. */
final class BootLogoTransactions {
    interface Store{String read();boolean write(String json);}
    interface Backend{ReadResult read(File destination)throws Exception;BootVideoTransactions.Result write(WriteRequest request)throws Exception;}
    static final class ReadResult{
        final String sha,meta,diagnostic,device;final int clientUid;
        ReadResult(String sha,String meta,String diagnostic){this(sha,meta,diagnostic,10106,"/dev/block/mmcblk0p1");}
        ReadResult(String sha,String meta,String diagnostic,int clientUid,String device){this.sha=sha;this.meta=meta;this.diagnostic=diagnostic;this.clientUid=clientUid;this.device=device;}
        boolean valid(){return BootDeviceSupport.appUid(clientUid)&&BootDeviceSupport.blockPath(device)&&BootVideoRoot.sha(sha)&&BootLogoRoot.nodeMeta(meta)&&diagnostic.equals("CLBL_UID=0\nCLBL_CLIENT_UID="+clientUid+"\nCLBL_BYTES=33554432\nCLBL_SHA_BEFORE="+sha+"\nCLBL_SHA_AFTER="+sha+"\nCLBL_RESULT=READ_COMPLETE\n");}
    }
    static final class WriteRequest{
        final boolean apply;final String id,before,after,oldWindow,newWindow,meta,device;final int seek,count,clientUid;final File source;
        WriteRequest(boolean apply,String id,String before,String after,String oldWindow,String newWindow,String meta,int seek,int count,int uid,File source){this(apply,id,before,after,oldWindow,newWindow,meta,seek,count,uid,source,"/dev/block/mmcblk0p1");}
        WriteRequest(boolean apply,String id,String before,String after,String oldWindow,String newWindow,String meta,int seek,int count,int uid,File source,String device){this.device=device;this.apply=apply;this.id=id;this.before=before;this.after=after;this.oldWindow=oldWindow;this.newWindow=newWindow;this.meta=meta;this.seek=seek;this.count=count;clientUid=uid;this.source=source;}
        boolean complete(BootVideoTransactions.Result r){return !r.timeout&&r.exit==0&&r.value("CLBL_UID").equals("0")&&r.value("CLBL_CLIENT_UID").equals(Integer.toString(clientUid))&&r.value("CLBL_DEVICE").equals(device)&&r.value("CLBL_TX").equals(id)
            &&r.value("CLBL_BEFORE_SHA").equals(before)&&r.value("CLBL_CANDIDATE_SHA").equals(after)&&r.value("CLBL_SEEK").equals(Integer.toString(seek))&&r.value("CLBL_COUNT").equals(Integer.toString(count))
            &&r.value("CLBL_AFTER_SHA").equals(apply?after:before)&&r.value("CLBL_RESULT").equals(apply?"APPLIED":"RESTORED");}
    }
    private static BootLogoTransactions instance;
    static synchronized BootLogoTransactions get(Context context){
        if(instance==null){Context app=context.getApplicationContext();SharedPreferences p=app.getSharedPreferences("boot_logo_transaction_v1",0);Handler h=new Handler(Looper.getMainLooper());
            instance=new BootLogoTransactions(new File(app.getFilesDir(),"boot_logo_transactions"),new BootLogoRoot(app),new Store(){public String read(){return p.getString("receipt","");}public boolean write(String s){return p.edit().putString("receipt",s).commit();}},Executors.newSingleThreadExecutor(),h::post,android.os.Process.myUid());}
        return instance;
    }
    private final File root;private final Backend backend;private final Store store;private final Executor worker,ui;private final int uid;
    private JSONObject receipt;private boolean busy,blocked;private Runnable observer;
    private String message="先明確確認，讀取當次完整資源區備份，再由 FAT／BMP 解析候選。\n匯出備份並確認有離線救援後，才可另行確認套用；不自行重啟。";
    BootLogoTransactions(File root,Backend backend,Store store,Executor worker,Executor ui,int uid){
        this.root=root;this.backend=backend;this.store=store;this.worker=worker;this.ui=ui;this.uid=uid;
        try{String saved=store.read();receipt=saved.isEmpty()?new JSONObject().put("schema",1).put("state","IDLE"):new JSONObject(saved);
            if(receipt.getInt("schema")!=1||!receipt.getString("state").matches("IDLE|READ_PENDING|READY|APPLY_PENDING|APPLIED|RESTORE_PENDING|RESTORED|UNKNOWN|FAILED_NO_TARGET_WRITE"))throw new IOException();
            String state=receipt.getString("state");blocked=state.matches(".*PENDING|UNKNOWN|FAILED_NO_TARGET_WRITE");
            if(!state.equals("IDLE")&&!BootVideoRoot.uuid(receipt.getString("transaction")))throw new IOException();
            if(state.matches("READY|APPLIED|RESTORED"))request(false);
            if(blocked)message="前次圖片交易需人工核對，已鎖定；不重送、不自動還原，請保留備份。";
            else if(state.equals("READY"))message="已從當次完整備份解析候選；尚未寫入。請先匯出完整備份，確認離線可取得，再另行確認套用。";
            else if(state.equals("APPLIED"))message="開機圖片已套用，完整資源區讀回符合候選；仍需重啟人工觀看。可還原至本次套用前。";
            else if(state.equals("RESTORED"))message="開機圖片已還原，完整資源區讀回符合本次原備份；仍需重啟人工觀看。";
        }catch(Exception error){receipt=new JSONObject();blocked=true;message="圖片交易紀錄無法驗證，維持鎖定；請保留備份。";}
    }
    synchronized boolean canRead(){return BootDeviceSupport.appUid(uid)&&!busy&&!blocked&&receipt.optString("state").matches("IDLE|RESTORED");}
    // A READY transaction has never submitted a write. A different draft may start a
    // separately confirmed fresh read, while retaining its predecessor's files/receipt.
    synchronized boolean canReplacePrepared(BootAssetStore.Asset asset){
        return BootDeviceSupport.appUid(uid)&&!busy&&!blocked&&receipt.optString("state").equals("READY")
            &&asset!=null&&asset.target==BootAssetStore.Target.LOGO&&BootVideoRoot.uuid(asset.id)
            &&BootVideoRoot.sha(asset.sha)&&!asset.sha.equals(receipt.optString("sourceSha"));
    }
    synchronized boolean canApply(BootAssetStore.Asset asset){return BootDeviceSupport.appUid(uid)&&!busy&&!blocked&&receipt.optString("state").equals("READY")&&receipt.optBoolean("backupExported")&&asset!=null&&asset.target==BootAssetStore.Target.LOGO&&asset.sha.equals(receipt.optString("sourceSha"));}
    synchronized boolean canRestore(){return BootDeviceSupport.appUid(uid)&&!busy&&!blocked&&receipt.optString("state").equals("APPLIED");}
    synchronized boolean busy(){return busy;}
    synchronized String message(){return message;}
    synchronized String preparationNote(String sha){
        if(busy||blocked)return "這項開機圖片交易進行中或需人工核對；請到圖片維護頁查看，不重送或自行重啟。";
        String state=receipt.optString("state");
        if(state.equals("APPLIED"))return receipt.optString("sourceSha").equals(sha)?"上次操作紀錄：此開機圖片已套用，完整資源區讀回符合候選；仍需人工觀看驗收。":"上次操作紀錄：另一張開機圖片已套用；請到維護頁核對目前交易。";
        if(state.equals("RESTORED"))return "上次操作紀錄：已還原本次套用前的開機圖片，完整資源區讀回通過。";
        if(state.equals("READY"))return "已有當次備份與圖片候選，尚未寫入；請到維護頁核對來源及備份匯出狀態。";
        return "開機圖片素材已保存；備份／套用／還原需另行確認。";
    }
    synchronized String exportReceipt(){return receipt.toString();}
    synchronized void observe(Runnable r){observer=r;}
    synchronized void removeObserver(Runnable r){if(observer==r)observer=null;}
    private void notifyUi(){Runnable r;synchronized(this){r=observer;}if(r!=null)r.run();}
    private File directory()throws Exception{String id=receipt.getString("transaction");if(!BootVideoRoot.uuid(id))throw new IOException();File dir=new File(root,id);if(!dir.getCanonicalFile().equals(new File(root.getCanonicalFile(),id)))throw new IOException("Unexpected transaction path");return dir;}
    synchronized File backupFile()throws Exception{File f=new File(directory(),"before.img");if(!BootVideoRoot.sha(receipt.optString("beforeSha"))||!f.isFile()||f.length()!=BootLogoLayout.PHYSICAL_BYTES)throw new IOException("No verified complete backup");return f;}
    synchronized boolean confirmBackupExport(String sha,long bytes){
        if(busy||blocked||!receipt.optString("state").matches("READY|APPLIED|RESTORED")||bytes!=BootLogoLayout.PHYSICAL_BYTES||!sha.equals(receipt.optString("beforeSha")))return false;
        try{receipt.put("backupExported",true);if(!store.write(receipt.toString()))throw new IOException();message="完整備份已匯出並讀回核對；請確保離線時也能取得。尚未因匯出而寫入分區。";return true;}
        catch(Exception error){blocked=true;message="備份匯出狀態無法保存，已停用套用；沒有寫入分區。";return false;}
    }
    private void save()throws Exception{if(!store.write(receipt.toString()))throw new IOException("Cannot persist transaction");}
    synchronized boolean prepare(BootAssetStore.Asset asset){
        if((!canRead()&&!canReplacePrepared(asset))||asset==null||asset.target!=BootAssetStore.Target.LOGO||!BootVideoRoot.uuid(asset.id)||!BootVideoRoot.sha(asset.sha))return false;
        try{
            String id=UUID.randomUUID().toString(),previousId=receipt.optString("transaction","");
            // Archive completed/prepared history before replacing the active pointer. Never
            // delete an old backup, reuse its candidate, or reset an uncertain transaction.
            if(!previousId.isEmpty()){
                File archive=new File(directory(),"receipt-before-"+id+".json");
                writeNew(archive,receipt.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            receipt=new JSONObject().put("schema",1).put("state","READ_PENDING").put("transaction",id).put("sourceSha",asset.sha).put("sourceAssetId",asset.id).put("clientUid",uid).put("at",System.currentTimeMillis());
            if(!previousId.isEmpty())receipt.put("previousTransaction",previousId);
            save();
        }
        catch(Exception error){blocked=true;message="無法保存備份請求，沒有要求 Root。";notifyUi();return false;}
        busy=true;message="正在以本 App 要求一次 Root 唯讀取回完整備份，之後在 App 內解析候選；不寫入分區。";notifyUi();
        try{worker.execute(()->prepareWorker(asset));}catch(RuntimeException error){stop("UNKNOWN",error.toString());}return true;
    }
    // A separately reviewed, proven read-only failure can become a NEW READY candidate.
    // This does not dispatch Root, and no other UNKNOWN or PENDING can enter this path.
    synchronized boolean adoptReviewedCandidate(BootLogoCandidateReview.Ready reviewed,boolean rescueConfirmed){
        if(!rescueConfirmed||reviewed==null||busy||!blocked||!BootLogoReadProbe.eligible(receipt.toString(),uid)||!receipt.toString().equals(reviewed.original))return false;
        busy=true;String original=receipt.toString();message="正在保存人工核對的候選與完整歷史；不要求Root。";ui.execute(this::notifyUi);
        try{worker.execute(()->{
            try{
                JSONObject r=reviewed.record;BootLogoCandidateReview.verify(reviewed.directory,r);
                if(r.getInt("clientUid")!=uid||!r.getJSONObject("originalReceipt").toString().equals(new JSONObject(original).toString()))throw new IOException("Reviewed receipt changed");
                if(!root.isDirectory()&&!root.mkdirs())throw new IOException("No transaction directory");
                String id=r.getString("reviewId");File dir=new File(root,id);if(!dir.mkdir())throw new IOException("Reviewed transaction already exists");
                for(String name:new String[]{"before.img","source.png","candidate-window.bin","restore-window.bin"})BootLogoCandidateReview.copy(new File(reviewed.directory,name),new File(dir,name));
                BootLogoCandidateReview.verify(dir,r);writeNew(new File(dir,"reviewed-read-failure.json"),original.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                JSONObject next=new JSONObject(r.toString());next.put("schema",1).put("state","READY").put("transaction",id).put("previousTransaction",new JSONObject(original).getString("transaction"))
                    .put("reviewedReadFailure",new JSONObject(original)).put("backupExported",true).put("rescueConfirmed",true).put("reviewConfirmedAt",System.currentTimeMillis());
                synchronized(this){if(!receipt.toString().equals(original))throw new IOException("Original lane changed");receipt=next;save();blocked=false;busy=false;message="人工核對已保存，原UNKNOWN及所有證據保留於新候選。尚未要求Root或寫入；套用仍需另外確認。";}
            }catch(Exception|OutOfMemoryError failed){synchronized(this){blocked=true;busy=false;message="候選採用未完成，維持鎖定；不重送、不要求Root。";try{receipt.put("state","UNKNOWN").put("reviewAdoptionError",failed.getClass().getSimpleName());save();}catch(Exception ignored){}}}
            ui.execute(this::notifyUi);
        });return true;}catch(RuntimeException failed){busy=false;blocked=true;message="無法開始候選採用，沒有要求Root。";return false;}
    }
    static byte[] rgb(File source)throws Exception{
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),o);
        if(o.outWidth!=1280||o.outHeight!=720||!"image/png".equals(o.outMimeType))throw new IOException("Requires normalized 1280x720 PNG");
        o.inJustDecodeBounds=false;o.inScaled=false;Bitmap image=BitmapFactory.decodeFile(source.getPath(),o);if(image==null)throw new IOException("PNG decode failed");
        try{byte[] rgb=new byte[BootLogoLayout.RGB_BYTES];int[] row=new int[1280];int at=0;for(int y=0;y<720;y++){image.getPixels(row,0,1280,0,y,1280,1);for(int color:row){if((color>>>24)!=255)throw new IOException("PNG is not opaque");rgb[at++]=(byte)(color>>>16);rgb[at++]=(byte)(color>>>8);rgb[at++]=(byte)color;}}return rgb;}finally{image.recycle();}
    }
    static void writeNew(File file,byte[] bytes)throws Exception{if(!file.createNewFile())throw new IOException("Staging already exists");try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}}
    private void prepareWorker(BootAssetStore.Asset asset){
        try{
            if(!root.isDirectory()&&!root.mkdirs())throw new IOException("No private backup folder");if(root.getUsableSpace()<128L*1024*1024)throw new IOException("Not enough backup space");
            File dir; synchronized(this){dir=directory();}if(!dir.mkdir())throw new IOException("Transaction folder already exists");
            File allowed=new File(new File(new File(root.getParentFile(),"boot_asset_preparation"),asset.id),"prepared.png");
            if(!asset.output.getCanonicalPath().equals(allowed.getCanonicalPath())||!BootAssetStore.hash(asset.output).equals(asset.sha))throw new IOException("Prepared PNG changed");
            File png=new File(dir,"source.png");try(InputStream in=new FileInputStream(asset.output)){BootAssetStore.copy(in,png,()->false);}if(!BootAssetStore.hash(png).equals(asset.sha))throw new IOException("PNG copy changed");
            byte[] pixels=rgb(png);File before=new File(dir,"before.img");ReadResult read=backend.read(before);
            if(!read.valid()||read.clientUid!=uid||before.length()!=BootLogoLayout.PHYSICAL_BYTES||!BootAssetStore.hash(before).equals(read.sha))throw new IOException("Root backup proof incomplete");
            BootLogoLayout.Candidate c=BootLogoLayout.build(before,pixels);
            if(!c.beforeSha.equals(read.sha))throw new IOException("Backup changed during parse");writeNew(new File(dir,"candidate-window.bin"),c.candidateWindow);writeNew(new File(dir,"restore-window.bin"),c.originalWindow);
            synchronized(this){receipt.put("beforeSha",c.beforeSha).put("afterSha",c.afterSha).put("oldWindowSha",c.originalWindowSha).put("newWindowSha",c.candidateWindowSha).put("seek",c.layout.windowStart/512).put("count",(c.layout.windowEnd-c.layout.windowStart)/512)
                .put("nodeMeta",read.meta).put("device",read.device).put("readDiagnostic",read.diagnostic).put("state","READY").put("backupExported",false).put("preparedAt",System.currentTimeMillis());save();busy=false;
                message="候選已由當次完整備份解析；分區尚未寫入。請先匯出完整備份並讀回核對，確認離線可取得，再另行確認套用。";}
        }catch(Exception|OutOfMemoryError error){stop("UNKNOWN",error.getClass().getSimpleName()+": "+error.getMessage());}
        ui.execute(this::notifyUi);
    }
    private synchronized WriteRequest request(boolean applying)throws Exception{
        if(receipt.optInt("clientUid",-1)!=uid)throw new IOException("Transaction belongs to a different App UID");
        File dir=directory();WriteRequest r=new WriteRequest(applying,receipt.getString("transaction"),receipt.getString("beforeSha"),receipt.getString("afterSha"),receipt.getString("oldWindowSha"),receipt.getString("newWindowSha"),receipt.getString("nodeMeta"),receipt.getInt("seek"),receipt.getInt("count"),uid,new File(dir,"candidate-window.bin"),receipt.has("device")?receipt.getString("device"):(uid==10106?"/dev/block/mmcblk0p1":""));
        if(!BootDeviceSupport.blockPath(r.device)||!BootVideoRoot.sha(r.before)||!BootVideoRoot.sha(r.after)||!BootVideoRoot.sha(r.oldWindow)||!BootVideoRoot.sha(r.newWindow)||!BootLogoRoot.nodeMeta(r.meta)||r.seek<=0||r.count<=0||r.count>6144||(long)r.seek+r.count>65536)throw new IOException("Invalid prepared receipt");return r;
    }
    synchronized boolean apply(BootAssetStore.Asset asset,boolean rescueConfirmed){return rescueConfirmed&&canApply(asset)&&submit(true);}
    synchronized boolean restore(){return canRestore()&&submit(false);}
    private boolean submit(boolean applying){
        WriteRequest request;
        try{request=request(applying);receipt.put("state",applying?"APPLY_PENDING":"RESTORE_PENDING").put("action",applying?"APPLY":"RESTORE").put("rescueConfirmed",true).put("at",System.currentTimeMillis());save();}
        catch(Exception error){stop("UNKNOWN","Cannot persist bounded request");return false;}
        busy=true;message="正在以本 App 要求一次 Root，備份／核對並處理已解析的圖片範圍；不自行重啟。";notifyUi();
        try{worker.execute(()->writeWorker(request));}catch(RuntimeException error){stop("UNKNOWN",error.toString());}return true;
    }
    private void writeWorker(WriteRequest request){
        try{
            File dir; synchronized(this){dir=directory();}File before=new File(dir,"before.img"),png=new File(dir,"source.png");
            if(!BootAssetStore.hash(before).equals(request.before)||!BootAssetStore.hash(png).equals(receipt.optString("sourceSha")))throw new IOException("Saved source/backup changed");
            BootLogoLayout.Candidate c=BootLogoLayout.build(before,rgb(png));
            if(!c.afterSha.equals(request.after)||!c.originalWindowSha.equals(request.oldWindow)||!c.candidateWindowSha.equals(request.newWindow)||c.layout.windowStart/512!=request.seek||(c.layout.windowEnd-c.layout.windowStart)/512!=request.count
                ||!BootAssetStore.hash(new File(dir,"candidate-window.bin")).equals(c.candidateWindowSha)||!BootAssetStore.hash(new File(dir,"restore-window.bin")).equals(c.originalWindowSha))throw new IOException("Bounded candidate changed");
            BootVideoTransactions.Result result=backend.write(request);boolean success=request.complete(result);
            synchronized(this){receipt.put("exit",result.exit).put("timeout",result.timeout).put("rootDiagnostic",result.output).put("finishedAt",System.currentTimeMillis());
                String state=success?(request.apply?"APPLIED":"RESTORED"):(!result.timeout&&result.output.contains("CLBL_NO_TARGET_WRITE\n")&&!result.output.contains("CLBL_TARGET_WRITE_ATTEMPTED")?"FAILED_NO_TARGET_WRITE":"UNKNOWN");
                receipt.put("state",state);save();busy=false;blocked=!success;
                message=success?(request.apply?"開機圖片已套用，完整資源區 SHA 讀回符合候選；仍需重啟人工觀看。":"開機圖片已還原，完整資源區 SHA 符合原備份；仍需重啟人工觀看。"):
                    "圖片操作未通過或結果不明，已鎖定；請匯出紀錄，保留完整備份，不重送、不自行重啟。";}
        }catch(Exception|OutOfMemoryError error){stop("UNKNOWN",error.getClass().getSimpleName()+": "+error.getMessage());}
        ui.execute(this::notifyUi);
    }
    private synchronized void stop(String state,String diagnostic){busy=false;blocked=true;if(diagnostic.length()>300)diagnostic=diagnostic.substring(0,300);try{receipt.put("state",state).put("diagnostic",diagnostic);store.write(receipt.toString());}catch(Exception ignored){}message="圖片交易已停止並鎖定；請保留完整備份與紀錄，不重送、不自行重啟。";}
}
