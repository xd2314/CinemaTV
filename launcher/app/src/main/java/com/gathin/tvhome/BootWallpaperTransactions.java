package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.io.*;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.json.JSONObject;

/** Static system wallpaper only. No Root, file replacement in /data/system, or automatic retry. */
final class BootWallpaperTransactions {
    interface Store{String read();boolean write(String value);}
    interface Backend{Snapshot capture(File output)throws Exception;int set(File source,Snapshot expected)throws Exception;
        default int set(File source,Snapshot expected,boolean sharedDefault)throws Exception{if(sharedDefault)throw new IOException("Default shared background not supported by this backend");return set(source,expected);}}
    interface Timer{void after(long millis,Runnable action);}
    static final class Snapshot {
        final int systemId,lockId;final String sha,lockSha;final boolean defaultImage;
        Snapshot(int systemId,int lockId,String sha,String lockSha){this(systemId,lockId,sha,lockSha,false);}
        Snapshot(int systemId,int lockId,String sha,String lockSha,boolean defaultImage){this.systemId=systemId;this.lockId=lockId;this.sha=sha;this.lockSha=lockSha;this.defaultImage=defaultImage;}
        boolean same(Snapshot other){return systemId==other.systemId&&lockId==other.lockId&&sha.equals(other.sha)&&lockSha.equals(other.lockSha)&&defaultImage==other.defaultImage;}
        void valid()throws IOException{if(systemId<=0||(lockId<=0&&lockId!=-1)||!BootVideoRoot.sha(sha)||!BootVideoRoot.sha(lockSha)||(lockId==-1&&!sha.equals(lockSha)))throw new IOException("Unverified wallpaper snapshot");}
        boolean preservesLock(Snapshot before){
            return before.lockId>0?lockId==before.lockId&&lockSha.equals(before.lockSha):
                before.lockId==-1&&lockId==before.systemId&&lockSha.equals(before.sha)&&before.lockSha.equals(before.sha);
        }
    }
    private static BootWallpaperTransactions instance;
    static synchronized BootWallpaperTransactions get(Context context){
        if(instance==null){Context app=context.getApplicationContext();SharedPreferences p=app.getSharedPreferences("boot_wallpaper_transaction_v1",0);Handler h=new Handler(Looper.getMainLooper());
            instance=new BootWallpaperTransactions(new File(app.getFilesDir(),"boot_wallpaper_transactions"),new BootWallpaperBackend(app),
                new Store(){public String read(){return p.getString("receipt","");}public boolean write(String s){return p.edit().putString("receipt",s).commit();}},
                Executors.newSingleThreadExecutor(),h::post,(ms,r)->h.postDelayed(r,ms));}
        return instance;
    }
    private final File root;private final Backend backend;private final Store store;private final Executor worker,ui;private final Timer timer;
    private JSONObject receipt;private boolean busy,blocked;private String token="";private Runnable observer;
    private String message="只更換影片後的靜態系統背景；先備份、核對，再要求系統套用。\n已有獨立鎖屏不變；預設共用背景依確認內容一起更換。不修改 Cinema TV 首頁桌布或分區，不要求 Root。";
    BootWallpaperTransactions(File root,Backend backend,Store store,Executor worker,Executor ui,Timer timer){
        this.root=root;this.backend=backend;this.store=store;this.worker=worker;this.ui=ui;this.timer=timer;
        try{String saved=store.read();receipt=saved.isEmpty()?new JSONObject().put("schema",1).put("state","IDLE"):new JSONObject(saved);
            if(receipt.getInt("schema")!=1||!receipt.getString("state").matches("IDLE|PENDING|APPLIED|RESTORED|UNKNOWN|FAILED_NO_TARGET_WRITE"))throw new IOException();
            String state=receipt.getString("state");blocked=state.matches("PENDING|UNKNOWN|FAILED_NO_TARGET_WRITE")||receipt.optBoolean("auditPending")||(receipt.has("lastAudit")&&!receipt.getJSONObject("lastAudit").optBoolean("passed"));
            if(state.matches("APPLIED|RESTORED")){if(!BootVideoRoot.uuid(receipt.getString("transaction"))||!BootVideoRoot.sha(receipt.getString("beforeSha"))||!BootVideoRoot.sha(receipt.getString("candidateSha")))throw new IOException();}
            if(state.matches("APPLIED|RESTORED")&&receipt.optBoolean("sharedDefault")){
                String expectedLock=receipt.getString(state.equals("APPLIED")?"candidateSha":"beforeSha");
                if(!receipt.optString("beforeKind").equals("DEFAULT_IMAGE_COPY")||receipt.getInt("beforeLockId")!=-1||receipt.getInt("lockId")!=-1
                    ||!receipt.getString("beforeLockSha").equals(receipt.getString("beforeSha"))||!receipt.getString("lockSha").equals(expectedLock))throw new IOException();
            }
            if(blocked)message="前次背景交易需人工核對，已鎖定；不自動重送或還原。請保留紀錄與備份。";
            else if(state.equals("APPLIED"))message=receipt.optBoolean("sharedDefault")?"背景已套用並讀回；原廠預設畫面副本已保存，共用鎖屏一起更換。":"背景已套用並讀回；仍需實體重啟觀看。可還原這次套用前的圖片。";
            else if(state.equals("RESTORED"))message=receipt.optBoolean("sharedDefault")?"原廠預設畫面副本與共用鎖屏已還原並讀回；不還原缺檔狀態。":"原背景圖片已還原並讀回；仍需實體重啟觀看。系統桌布 ID 會由 Android 重新分配。";
        }catch(Exception error){receipt=new JSONObject();blocked=true;message="背景交易紀錄無法驗證；維持鎖定，請保留備份。";}
    }
    synchronized boolean canApply(){return !busy&&!blocked;}
    synchronized boolean canRestore(){return canApply()&&receipt.optString("state").equals("APPLIED");}
    synchronized boolean busy(){return busy;}
    synchronized boolean canAudit(){return !busy&&!blocked&&receipt.optString("state").matches("APPLIED|RESTORED");}
    synchronized boolean canExportBackup(){return !busy&&receipt.optString("state").matches("PENDING|APPLIED|RESTORED|UNKNOWN|FAILED_NO_TARGET_WRITE")&&BootVideoRoot.uuid(receipt.optString("transaction"))&&BootVideoRoot.sha(receipt.optString("beforeSha"))&&BootVideoRoot.sha(receipt.optString("lockSha"));}
    synchronized File[] backupFiles()throws Exception{
        if(!canExportBackup())throw new IOException("No complete wallpaper backup");String id=receipt.getString("transaction");File dir=new File(root,id);
        File[] files={new File(dir,"before.image"),new File(dir,"before-lock.image")};String[] hashes={receipt.getString("beforeSha"),receipt.optString("beforeLockSha",receipt.getString("lockSha"))};
        for(int i=0;i<files.length;i++){File expected=new File(new File(root.getCanonicalFile(),id),files[i].getName());if(!files[i].isFile()||!files[i].getCanonicalPath().equals(expected.getAbsolutePath())||!BootAssetStore.hash(files[i]).equals(hashes[i]))throw new IOException("Backup changed");}return files;
    }
    synchronized String message(){return message;}
    synchronized String preparationNote(String sha){
        if(busy||blocked)return "這項過場圖片交易進行中或需人工核對；請到維護頁查看，不重送或自行重啟。";
        String state=receipt.optString("state");
        if(state.equals("APPLIED"))return receipt.optString("candidateSha").equals(sha)?"上次操作紀錄：此過場圖片已套用並讀回核對；鎖屏依確認內容處理，仍需人工觀看驗收。":"上次操作紀錄：另一張過場圖片已套用；請到維護頁核對目前交易。";
        if(state.equals("RESTORED"))return "上次操作紀錄：備份畫面已還原並讀回核對。";
        return "過場圖片素材已保存；系統背景的備份／套用／還原需另行確認。";
    }
    synchronized String exportReceipt(){return receipt.toString();}
    synchronized void observe(Runnable r){observer=r;}
    synchronized void removeObserver(Runnable r){if(observer==r)observer=null;}
    private void notifyUi(){Runnable r;synchronized(this){r=observer;}if(r!=null)r.run();}
    synchronized boolean apply(BootAssetStore.Asset asset){
        if(!canApply()||asset==null||asset.target!=BootAssetStore.Target.WALLPAPER||!BootVideoRoot.uuid(asset.id)||!BootVideoRoot.sha(asset.sha))return false;
        return submit(true,UUID.randomUUID().toString(),asset);
    }
    synchronized boolean restore(){return canRestore()&&submit(false,receipt.optString("transaction"),null);}
    synchronized boolean audit(){
        if(!canAudit())return false;
        try{receipt.put("auditPending",true);if(!store.write(receipt.toString()))throw new IOException();}
        catch(Exception error){blocked=true;message="無法保存唯讀核對請求；沒有更換背景。";notifyUi();return false;}
        busy=true;token=UUID.randomUUID().toString();String active=token;message="正在唯讀核對目前系統圖片與鎖屏；不要求 Root、不更換圖片。";notifyUi();
        try{timer.after(45000,()->expire(active));worker.execute(()->{
            try{
                String id,expected;int expectedId,lockId;String lockSha;
                synchronized(this){guard(active);id=receipt.getString("transaction");expected=receipt.getString(receipt.getString("state").equals("APPLIED")?"candidateSha":"beforeSha");expectedId=receipt.getInt("afterId");lockId=receipt.getInt("lockId");lockSha=receipt.getString("lockSha");}
                Snapshot now=capture(active,new File(new File(root,id),"audit-"+UUID.randomUUID()+".image"));
                boolean pass=now.systemId==expectedId&&now.sha.equals(expected)&&now.lockId==lockId&&now.lockSha.equals(lockSha);
                synchronized(this){guard(active);receipt.put("auditPending",false).put("lastAudit",new JSONObject().put("passed",pass).put("at",System.currentTimeMillis()).put("systemId",now.systemId).put("sha",now.sha).put("lockId",now.lockId).put("lockSha",now.lockSha));save(active);busy=false;token="";blocked=!pass;message=pass?"目前系統圖片與交易紀錄一致，鎖屏未變；唯讀核對完成。":"目前背景或鎖屏與交易不符，已停止後續操作；本次唯讀核對沒有更換圖片。";}
            }catch(Exception error){synchronized(this){if(busy&&token.equals(active)){busy=false;blocked=true;token="";try{receipt.put("state","UNKNOWN").put("auditError",error.getClass().getSimpleName());store.write(receipt.toString());}catch(Exception ignored){}message="唯讀核對未完成，已停止後續操作；沒有要求系統更換背景。";}}}
            ui.execute(this::notifyUi);
        });}catch(RuntimeException error){expire(active);}return true;
    }
    private boolean submit(boolean applying,String id,BootAssetStore.Asset asset){
        try{
            receipt.remove("lastAudit");receipt.remove("auditPending");
            receipt.put("schema",1).put("state","PENDING").put("action",applying?"APPLY":"RESTORE").put("transaction",id)
                .put("at",System.currentTimeMillis()).put("writeAttempted",false);
            if(applying)receipt.put("candidateSha",asset.sha);
            if(!store.write(receipt.toString()))throw new IOException();
        }catch(Exception error){blocked=true;message="無法保存背景交易，沒有要求系統更換。";notifyUi();return false;}
        busy=true;token=UUID.randomUUID().toString();String active=token;message="正在備份、核對並執行一次系統背景操作；不要求 Root、不自行重啟。";notifyUi();
        try{timer.after(45000,()->expire(active));worker.execute(()->run(active,applying,id,asset));}
        catch(RuntimeException failure){expire(active);}
        return true;
    }
    private synchronized void guard(String active)throws IOException{if(!busy||!token.equals(active))throw new IOException("Stopped transaction");}
    private synchronized void save(String active)throws Exception{guard(active);if(!store.write(receipt.toString()))throw new IOException("Receipt persistence failed");}
    private synchronized void attempted(String active)throws Exception{guard(active);receipt.put("writeAttempted",true);save(active);}
    private Snapshot capture(String active,File file)throws Exception{guard(active);Snapshot s=backend.capture(file);s.valid();guard(active);return s;}
    private void run(String active,boolean applying,String id,BootAssetStore.Asset asset){
        try{
            guard(active);if(!root.isDirectory()&&!root.mkdirs())throw new IOException("No private backup folder");
            File dir=new File(root,id),before=new File(dir,"before.image"),candidate=new File(dir,"candidate.png");
            Snapshot current;String expected,lockSha;int lockId;boolean sharedDefault;
            if(applying){
                if(root.getUsableSpace()<256L*1024*1024||!dir.mkdir())throw new IOException("No safe transaction space");
                File allowed=new File(new File(new File(root.getParentFile(),"boot_asset_preparation"),asset.id),"prepared.png");
                if(!allowed.getCanonicalPath().equals(asset.output.getCanonicalPath()))throw new IOException("Untrusted prepared image path");
                if(!asset.sha.equals(BootAssetStore.hash(asset.output)))throw new IOException("Prepared image changed");
                BootWallpaperBackend.validateImage(asset.output);
                try(InputStream in=new FileInputStream(asset.output)){BootAssetStore.copy(in,candidate,()->false);}
                if(!asset.sha.equals(BootAssetStore.hash(candidate)))throw new IOException("Candidate copy mismatch");
                current=capture(active,before);
                if(!current.sha.equals(BootAssetStore.hash(before)))throw new IOException("Backup mismatch");
                File lockBackup=new File(dir,"before-lock.image");if(!lockBackup.isFile()||!BootAssetStore.hash(lockBackup).equals(current.lockSha))throw new IOException("Independent lock backup mismatch");
                sharedDefault=current.defaultImage&&current.lockId==-1;
                synchronized(this){guard(active);receipt.put("beforeSha",current.sha).put("beforeId",current.systemId).put("beforeKind",current.defaultImage?"DEFAULT_IMAGE_COPY":"CONFIGURED_FILE").put("sharedDefault",sharedDefault).put("beforeLockId",current.lockId).put("beforeLockSha",current.lockSha).put("lockId",current.lockId).put("lockSha",current.lockSha);save(active);}
                if(!current.same(capture(active,new File(dir,"precheck.image"))))throw new IOException("Wallpaper changed before apply");
                expected=asset.sha;lockSha=current.lockSha;lockId=current.lockId;
            }else{
                String beforeSha,candidateSha;int appliedId;
                String originalLockSha;
                synchronized(this){guard(active);beforeSha=receipt.getString("beforeSha");candidateSha=receipt.getString("candidateSha");appliedId=receipt.getInt("appliedId");lockId=receipt.getInt("lockId");lockSha=receipt.getString("lockSha");originalLockSha=receipt.optString("beforeLockSha",lockSha);sharedDefault=receipt.optBoolean("sharedDefault",false);}
                File allowedBackup=new File(new File(root.getCanonicalFile(),id),"before.image");
                if(!before.isFile()||!before.getCanonicalPath().equals(allowedBackup.getAbsolutePath())||!BootAssetStore.hash(before).equals(beforeSha))throw new IOException("Original backup changed");
                File lockBackup=new File(dir,"before-lock.image"),allowedLock=new File(new File(root.getCanonicalFile(),id),"before-lock.image");
                if(!lockBackup.isFile()||!lockBackup.getCanonicalPath().equals(allowedLock.getAbsolutePath())||!BootAssetStore.hash(lockBackup).equals(originalLockSha))throw new IOException("Original lock backup changed");
                BootWallpaperBackend.validateSnapshotImage(before);
                current=capture(active,new File(dir,"restore-precheck.image"));
                if(current.systemId!=appliedId||!current.sha.equals(candidateSha)||current.lockId!=lockId||!current.lockSha.equals(lockSha))throw new IOException("Current wallpaper is outside transaction");
                expected=beforeSha;
            }
            // Durable marker precedes the only mutating API call. No fallback or retry.
            File toSet=applying?candidate:before;if(!BootAssetStore.hash(toSet).equals(expected))throw new IOException("Staged image changed");
            attempted(active);int setId=backend.set(toSet,current,sharedDefault);
            Snapshot after=capture(active,new File(dir,applying?"after.image":"restored.image"));
            boolean lockVerified=sharedDefault?current.lockId==-1&&after.lockId==-1&&after.lockSha.equals(after.sha):after.preservesLock(current);
            if(setId<=0||setId==current.systemId||after.systemId!=setId||after.defaultImage||!after.sha.equals(expected)||!lockVerified)throw new IOException("System readback or lock wallpaper mismatch");
            synchronized(this){guard(active);receipt.put("state",applying?"APPLIED":"RESTORED").put("afterSha",after.sha).put("afterId",after.systemId).put("lockId",after.lockId).put("lockSha",after.lockSha).put("finishedAt",System.currentTimeMillis());
                if(applying)receipt.put("appliedId",after.systemId);save(active);busy=false;blocked=false;token="";
                message=sharedDefault?(applying?"過場圖片已套用並讀回，共用背景的鎖屏一起更換；原廠預設圖副本已保存。":"原廠預設圖副本已還原並讀回；共用鎖屏一起還原。這是畫面副本，不還原缺少桌布檔案的狀態。"):
                    applying?"過場圖片已套用，系統回讀一致、鎖屏未變；仍需重啟人工觀看。":"原過場圖片已還原，系統回讀一致、鎖屏未變；仍需重啟人工觀看。\nAndroid 會分配新的桌布 ID。";}
        }catch(Exception failure){synchronized(this){if(busy&&token.equals(active)){
            boolean attempted=receipt.optBoolean("writeAttempted",false);busy=false;blocked=true;token="";
            String reason=failure.getMessage();if(reason!=null&&reason.length()>180)reason=reason.substring(0,180);
            try{receipt.put("state",attempted?"UNKNOWN":"FAILED_NO_TARGET_WRITE").put("diagnostic",failure.getClass().getSimpleName()+": "+reason).put("finishedAt",System.currentTimeMillis());store.write(receipt.toString());}catch(Exception ignored){}
            message=attempted?"背景操作結果不明或回讀不符，已鎖定；不重送、不自動還原，請保留備份與紀錄。":"背景檢查未通過，尚未要求系統更換；已停止，請保留紀錄。";
        }}}
        ui.execute(this::notifyUi);
    }
    private synchronized void expire(String active){
        if(!busy||!token.equals(active))return;busy=false;blocked=true;token="";
        try{receipt.put("state","UNKNOWN").put("timeout",true);store.write(receipt.toString());}catch(Exception ignored){}
        message="背景操作逾時或結果不明，已鎖定。不要重送、清資料或自行重啟；請保留備份與紀錄。";notifyUi();
    }
}
