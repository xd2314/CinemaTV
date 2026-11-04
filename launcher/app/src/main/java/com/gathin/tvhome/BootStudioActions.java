package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.widget.CheckBox;
import android.view.View;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.json.JSONObject;

/** One-page presentation adapter. Transaction engines remain the only device writers. */
final class BootStudioActions {
    static final int EXPORT=7391,PERMISSION=7392;
    private final Activity activity;
    private final Runnable changed;
    private final Consumer<String> notice;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final Runnable observer;
    private boolean closed,exporting,auditing;
    private BootLogoApplyFlow logoFlow;
    private BootAssetStore.Asset permissionAsset;private boolean permissionPending,permissionApply;private String permissionReceipt;
    boolean capable;
    private BootLogoTransactions observedLogo;
    private BootVideoTransactions observedVideo;
    private BootWallpaperTransactions observedWallpaper;
    BootStudioActions(Activity activity,Runnable changed,Consumer<String> notice){
        this.activity=activity;this.changed=changed;this.notice=notice;
        capable=BootDeviceSupport.rootMaterials(android.os.Build.VERSION.SDK_INT,android.os.Process.myUid(),activity.getPackageName());
        observer=()->{if(!closed){changed.run();main.post(()->{if(!closed&&logoFlow!=null)logoFlow.advance();});}};observe();
        // A saved flow is evidence, not permission to resume after recreation.
        try{android.content.SharedPreferences p=activity.getSharedPreferences("boot_logo_apply_flow_v1",0);String saved=p.getString("current","");if(!saved.isEmpty()){JSONObject r=new JSONObject(saved);if(!r.optString("step").matches("COMPLETE|STOPPED|INTERRUPTED")){r.put("step","INTERRUPTED").put("message","頁面重建後不自動接續");p.edit().putString("current",r.toString()).putString("run-"+r.optString("run"),r.toString()).commit();}}}catch(Exception ignored){}
    }
    BootLogoTransactions logo(){return BootLogoTransactions.get(activity);}
    BootVideoTransactions movie(){return BootVideoTransactions.get(activity);}
    BootWallpaperTransactions wallpaper(){return BootWallpaperTransactions.get(activity);}
    void observe(){
        if(observedLogo!=null)observedLogo.removeObserver(observer);if(observedVideo!=null)observedVideo.removeObserver(observer);if(observedWallpaper!=null)observedWallpaper.removeObserver(observer);
        observedLogo=logo();observedVideo=movie();observedWallpaper=wallpaper();observedLogo.observe(observer);observedVideo.observe(observer);observedWallpaper.observe(observer);
    }
    boolean supported(BootAssetStore.Target t){
        return capable&&(t!=BootAssetStore.Target.WALLPAPER||BootDeviceSupport.wallpaper(android.os.Build.VERSION.SDK_INT,android.os.Process.myUid(),activity.getPackageName()));
    }
    boolean operationBusy(){return exporting||auditing||permissionPending||logo().busy()||movie().busy()||wallpaper().busy();}
    boolean busy(){return operationBusy()||(logoFlow!=null&&logoFlow.active());}
    String receipt(BootAssetStore.Target t){return t==BootAssetStore.Target.LOGO?logo().exportReceipt():t==BootAssetStore.Target.VIDEO?movie().exportReceipt():wallpaper().exportReceipt();}
    private final java.util.EnumMap<BootAssetStore.Target,JSONObject> displayRecords=new java.util.EnumMap<>(BootAssetStore.Target.class);
    private boolean displaying;
    void beginDisplay(){displayRecords.clear();displaying=true;}
    void endDisplay(){displaying=false;displayRecords.clear();}
    JSONObject record(BootAssetStore.Target t){
        if(displaying&&Looper.myLooper()==Looper.getMainLooper()){
            JSONObject value=displayRecords.get(t);if(value==null){try{value=new JSONObject(receipt(t));}catch(Exception ignored){value=new JSONObject();}displayRecords.put(t,value);}return value;
        }
        try{return new JSONObject(receipt(t));}catch(Exception ignored){return new JSONObject();}
    }
    String state(BootAssetStore.Target t){return record(t).optString("state","UNKNOWN");}
    String diagnostic(BootAssetStore.Target t){return t==BootAssetStore.Target.LOGO?logo().message():t==BootAssetStore.Target.VIDEO?movie().message():wallpaper().message();}
    String summary(BootAssetStore.Target t,boolean ready){
        String s=state(t);
        if(logoFlow!=null&&logoFlow.active())return logoFlow.message();
        if(busy())return exporting?"正在儲存並讀回核對，請等候完成。":permissionPending?"正在等待讀取權限確認；不會自行重送。":logo().busy()?"正在備份、建立候選或寫入核對。請勿斷電；已派送操作不能以返回取消。":movie().busy()?"正在保存原動畫、套用及讀回核對。請勿斷電或重送。":"正在備份或核對系統過場圖片，請稍候。";
        if(s.contains("PENDING")||s.equals("UNKNOWN")||s.equals("FAILED_NO_TARGET_WRITE"))return "上次操作需要核對。已停止新的套用；請保留備份並匯出紀錄。";
        if(s.equals("APPLIED"))return t==BootAssetStore.Target.VIDEO&&BootVideoRoute.PROPERTY.equals(record(t).optString("lastRoute"))&&!record(t).has("lastReaderProfile")?
            "先前已寫入影片，開機播放尚未通過。此韌體需另行確認套用相容修正。":"已套用並讀回核對。實際開機顯示仍需由你觀看確認。";
        if(s.equals("RESTORED"))return "已還原到上次套用前，備份與紀錄仍保留。";
        if(!supported(t))return "目前 Android 版本或 App 安裝身分不支援此項套用；仍可選擇、預覽與匯出素材。";
        if(s.equals("READY"))return record(t).optBoolean("backupExported")?"備份已匯出並核對。按套用後會自動核對並完成。":"按套用後會核對備份；需要另存時會開啟檔案選擇器，完成後接續套用。";
        return ready?(t==BootAssetStore.Target.LOGO?"圖片已準備好。確認後會用 su 0 檢查本機資源區，格式相容才備份、核對與套用。":"素材已準備好。確認後會先檢查本機目標並保存原內容。") :"選擇一個本機檔案，先看看它出現在這段開機畫面的樣子。";
    }
    // Selecting/reframing a local draft must not reset or unlock an existing system transaction.
    boolean canEdit(BootAssetStore.Target t){return !closed&&!busy();}
    String selectionSummary(BootAssetStore.Target t,BootAssetStore.Asset a){
        String base=summary(t,a!=null&&a.target==t);
        if(busy())return base;
        String state=state(t);
        if(state.contains("PENDING")||state.equals("UNKNOWN")||state.equals("FAILED_NO_TARGET_WRITE"))return base+" 可選圖與預覽，但不會解除套用鎖定。";
        if(t!=BootAssetStore.Target.LOGO){
            if(a==null||a.target!=t)return "尚未選擇素材。"+base;
            String key=t==BootAssetStore.Target.VIDEO?"lastSha":"candidateSha";
            if(state.equals("APPLIED"))return a.sha.equals(record(t).optString(key))?
                "上次紀錄已套用此素材；本次尚未重新核對設備現況。實際開機畫面仍需人工確認。":
                "新素材已準備，尚未套用。上次已套用的是另一份素材；按套用後才會建立本次備份與交易。";
            if(state.equals("RESTORED"))return base+" 目前選取的是待套用素材，不會因選取而自動套用。";
            return "目前選取的素材尚未套用。"+base;
        }
        if(a!=null&&!a.sha.equals(record(t).optString("sourceSha"))){
            if(state.equals("APPLIED"))return "新圖片已準備好。按套用後會先還原上一張，再自動備份、核對並套用新圖；舊備份保留。";
            if(state.equals("READY"))return "按套用後會自動為新圖建立候選。已驗證且內容一致的備份可沿用，舊備份保留。";
        }
        return base;
    }
    String primaryLabel(BootAssetStore.Target t){return t==BootAssetStore.Target.LOGO?"套用開機圖片":t==BootAssetStore.Target.VIDEO?"套用開機動畫":"套用過場圖片";}
    boolean differentLogo(BootAssetStore.Asset a){return a!=null&&a.target==BootAssetStore.Target.LOGO&&!a.sha.equals(record(BootAssetStore.Target.LOGO).optString("sourceSha"));}
    String primaryLabel(BootAssetStore.Target t,BootAssetStore.Asset a){
        return sameAppliedMaterial(t,a)?"重新"+primaryLabel(t):primaryLabel(t);
    }
    private boolean sameAppliedMaterial(BootAssetStore.Target t,BootAssetStore.Asset a){
        String key=t==BootAssetStore.Target.LOGO?"sourceSha":t==BootAssetStore.Target.VIDEO?"lastSha":"candidateSha";
        return a!=null&&a.target==t&&state(t).equals("APPLIED")&&a.sha.equals(record(t).optString(key));
    }
    boolean primaryEnabled(BootAssetStore.Target t,BootAssetStore.Asset a){
        if(closed||!supported(t)||busy()||a==null||a.target!=t)return false;
        if(t==BootAssetStore.Target.LOGO)return logo().canRead()||logo().canApply(a)||logo().canReplacePrepared(a)||(differentLogo(a)&&logo().canRestore())||(state(t).equals("READY")&&!record(t).optBoolean("backupExported")&&backupAvailable(t));
        return t==BootAssetStore.Target.VIDEO?movie().canApply():wallpaper().canApply();
    }
    boolean restoreEnabled(BootAssetStore.Target t){return !closed&&supported(t)&&!busy()&&(t==BootAssetStore.Target.LOGO?logo().canRestore():t==BootAssetStore.Target.VIDEO?movie().canRestore():wallpaper().canRestore());}
    boolean backupAvailable(BootAssetStore.Target t){if(operationBusy())return false;if(t==BootAssetStore.Target.WALLPAPER)return wallpaper().canExportBackup();if(t==BootAssetStore.Target.LOGO)try{return logo().backupFile().isFile();}catch(Exception ignored){}return false;}
    void primary(BootAssetStore.Target t,BootAssetStore.Asset a){
        if(!primaryEnabled(t,a))return;
        String consentReceipt=receipt(t);
        if(t==BootAssetStore.Target.LOGO){
            String plan=state(t).equals("APPLIED")?"會先還原上一張，再備份目前內容、建立新候選並套用。最多三次 Root 操作（還原／備份／套用）。":state(t).equals("READY")&&!differentLogo(a)?"會核對已準備的候選與備份，再套用。一次 Root 寫入。":"會先備份目前內容、建立新候選，再套用。最多兩次 Root 操作（備份／套用）。";
            confirm("套用這張開機圖片？",plan+"\n\n已驗證且內容一致的設備外備份可沿用；需另存新備份時會詢問位置，核對完成後自動接續。舊備份保留。\n\n先用 su 0 確認本機 bootloader 路徑及 FAT／BMP 格式，不再限定舊機型。找不到或格式不相容即停止。\n\n確認現有設備外備份及離線救援可用。過程不可斷電，不會自動重啟；失敗或取消即停止後續步驟，不重送。",true,()->{if(primaryEnabled(t,a)&&receipt(t).equals(consentReceipt))startLogoFlow(a);else notice.accept("狀態已改變，請重新確認本次套用。");});
            return;
        }
        confirm(t==BootAssetStore.Target.VIDEO?"套用這段開機動畫？":"套用這張過場圖片？",
            (sameAppliedMaterial(t,a)?"你選的是上次已套用的素材。重新套用仍會執行新的交易，還原點是這次套用前的內容，並非保證回到最初原廠素材。若只想使用現況，請取消。\n\n":"")+(t==BootAssetStore.Target.VIDEO?"確認後先備份影片、原路徑與讀取方式，再以一次Root套用。此韌體的相容修正會將資料區影片設為系統媒體服務專用讀取，讓開機流程選用；一般App不能直接讀取系統候選，App預覽仍使用已匯入素材。只有已核實的韌體與服務設定可使用此修正。保留原廠系統影片、不改SELinux、不自行重啟；不符或結果不明即停止。":
                "確認後會保存備份、核對並套用系統過場圖片，不需要 Root，不修改 Cinema TV 首頁桌布。已有獨立鎖屏時保留其圖片。若沒有桌布原檔，備份為原廠預設圖的畫面副本；沒有獨立鎖屏時，共用背景會一起更換，還原時一起恢復備份畫面，不還原缺檔狀態。若詢問讀取權限，允許後接續這次套用；拒絕即停止。"),false,()->{
                    if(!primaryEnabled(t,a)||!receipt(t).equals(consentReceipt)){notice.accept("狀態已改變，請重新確認本次套用。");return;}
                    if(t==BootAssetStore.Target.VIDEO)movie().apply(a);else submitWallpaper(a,true);changed.run();
                });
    }
    private void startLogoFlow(BootAssetStore.Asset a){
        File previous=null;try{if(record(BootAssetStore.Target.LOGO).optBoolean("backupExported"))previous=logo().backupFile();}catch(Exception ignored){}
        final File previousBackup=previous;
        android.content.SharedPreferences journal=activity.getSharedPreferences("boot_logo_apply_flow_v1",0);
        final BootLogoApplyFlow[] owner=new BootLogoApplyFlow[1];
        logoFlow=new BootLogoApplyFlow(new BootLogoApplyFlow.Host(){
            public JSONObject snapshot(){return record(BootAssetStore.Target.LOGO);}
            public boolean allowed(){
                if(closed||!capable)return false;
                if(owner[0]==null||owner[0].id().isEmpty())return true;
                try{JSONObject current=new JSONObject(journal.getString("current",""));return current.optString("run").equals(owner[0].id())&&!current.optString("step").matches("INTERRUPTED|STOPPED|COMPLETE");}catch(Exception e){return false;}
            }
            public boolean busy(){return operationBusy();}
            public boolean prepare(){return logo().prepare(a);}
            public boolean restore(){return logo().restore();}
            public boolean apply(){return logo().apply(a,true);}
            public void changed(String message){if(!closed&&logoFlow==owner[0]){BootStudioActions.this.changed.run();notice.accept(message);}}
            public void exportBackup(String runId){export(BootAssetStore.Target.LOGO,true,runId);}
            public void verifyBackup(JSONObject initial){
                auditing=true;changed("正在核對備份…");BootLogoApplyFlow run=logoFlow;JSONObject current=snapshot();
                io.execute(()->{boolean usable=false,needsExport=false;
                    try{
                        File currentBackup=logo().backupFile();String sha=current.getString("beforeSha");
                        if(!BootAssetStore.hash(currentBackup).equals(sha))throw new IOException("Current backup changed");
                        if(current.optBoolean("backupExported"))usable=true;
                        else if(initial.optBoolean("backupExported")&&sha.equals(initial.optString("beforeSha"))){
                            if(previousBackup==null||previousBackup.length()!=BootLogoLayout.PHYSICAL_BYTES||!BootAssetStore.hash(previousBackup).equals(sha))throw new IOException("Previous backup changed");
                            usable=logo().confirmBackupExport(sha,BootLogoLayout.PHYSICAL_BYTES);
                        }else{usable=true;needsExport=true;}
                    }catch(Exception ignored){}
                    boolean ok=usable,save=needsExport;main.post(()->{auditing=false;if(!closed&&logoFlow==run)run.verified(ok,save);});
                });
            }
        },json->{try{
            JSONObject r=new JSONObject(json);String existing=journal.getString("current","");
            if(!r.optString("step").equals("START")&&!existing.isEmpty()&&!new JSONObject(existing).optString("run").equals(r.getString("run"))){journal.edit().putString("run-"+r.getString("run"),json).commit();return false;}
            return journal.edit().putString("current",json).putString("run-"+r.getString("run"),json).commit();
        }catch(Exception error){return false;}},a.sha);
        owner[0]=logoFlow;logoFlow.start();changed.run();
    }
    void restore(BootAssetStore.Target t){
        if(!restoreEnabled(t))return;
        String consentReceipt=receipt(t);
        confirm("還原上次的"+(t==BootAssetStore.Target.VIDEO?"動畫":"圖片")+"？",
            "只在目前內容與這次操作紀錄一致、備份完整時，還原到上次套用前。這不是整機恢復，也不會自行重啟。\n\n"+(t==BootAssetStore.Target.WALLPAPER?"不需要 Root，鎖屏維持原樣；若詢問備份讀取權限，允許後接續本次還原。":"需要一次 Root 授權；結果不明即停止，不重送。")+(t==BootAssetStore.Target.LOGO?"\n最早圖片還原仍是分區範圍寫入，請勿斷電。":""),t==BootAssetStore.Target.LOGO,()->{
                if(!restoreEnabled(t)||!receipt(t).equals(consentReceipt)){notice.accept("狀態已改變，請重新確認本次還原。");return;}
                if(t==BootAssetStore.Target.LOGO)logo().restore();else if(t==BootAssetStore.Target.VIDEO)movie().restore();else submitWallpaper(null,false);changed.run();
            });
    }
    private void submitWallpaper(BootAssetStore.Asset a,boolean apply){
        if(activity.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=PackageManager.PERMISSION_GRANTED){
            permissionAsset=a;permissionApply=apply;permissionReceipt=receipt(BootAssetStore.Target.WALLPAPER);permissionPending=true;
            try{activity.requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},PERMISSION);}catch(RuntimeException e){permissionPending=false;permissionAsset=null;notice.accept("無法詢問備份讀取權限，已停止。");}return;
        }
        if(apply)wallpaper().apply(a);else wallpaper().restore();
    }
    void permissionResult(int request,int[] grants){
        if(request!=PERMISSION||closed)return;
        boolean agreed=permissionPending,apply=permissionApply;BootAssetStore.Asset selected=permissionAsset;String snapshot=permissionReceipt;
        permissionPending=false;permissionAsset=null;permissionReceipt=null;
        if(!agreed||closed||!supported(BootAssetStore.Target.WALLPAPER)||grants.length!=1||grants[0]!=PackageManager.PERMISSION_GRANTED||!receipt(BootAssetStore.Target.WALLPAPER).equals(snapshot)){
            changed.run();notice.accept("未接續套用：權限未允許、頁面已重建或狀態已改變。沒有自動重送。");return;
        }
        if(apply&&selected!=null&&wallpaper().canApply())wallpaper().apply(selected);
        else if(!apply&&wallpaper().canRestore())wallpaper().restore();
        changed.run();
    }
    private void confirm(String title,String text,boolean rescue,Runnable action){
        CheckBox box=new CheckBox(activity);box.setText("我有可用的離線復原方式，且完整備份已存於設備外");box.setPadding(28,16,28,16);
        AtomicBoolean submitted=new AtomicBoolean();
        AlertDialog.Builder builder=new AlertDialog.Builder(activity).setTitle(title).setMessage(text).setNegativeButton("先不要",(d,w)->{})
            .setPositiveButton("確認繼續",(d,w)->{if((!rescue||box.isChecked())&&submitted.compareAndSet(false,true))action.run();});
        if(rescue)builder.setView(box);AlertDialog d=builder.create();d.show();
        if(rescue){d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);box.setOnCheckedChangeListener((v,c)->d.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(c));}
        View cancel=d.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
    }
    boolean auditEnabled(BootAssetStore.Target t){return !closed&&supported(t)&&!busy()&&(t==BootAssetStore.Target.WALLPAPER?wallpaper().canAudit():t==BootAssetStore.Target.VIDEO&&movie().canAcknowledgeNoWrite());}
    void audit(BootAssetStore.Target t){
        if(!auditEnabled(t))return;
        confirm("核對目前狀態？","這一步只讀取目前內容與紀錄，不要求 Root、不更換素材。影片只處理已辨識且可驗證的舊版未寫入紀錄；核對後不會自動套用。",false,()->{
            if(!auditEnabled(t))return;
            if(t==BootAssetStore.Target.WALLPAPER){wallpaper().audit();return;}
            auditing=true;changed.run();io.execute(()->{movie().acknowledgeNoWrite(()->BootVideoRoot.auditBeforeAcknowledge(activity.getApplicationContext()));main.post(()->{auditing=false;if(!closed)changed.run();});});
        });
    }
    void export(BootAssetStore.Target t,boolean full){export(t,full,"");}
    void exportToDownloads(BootAssetStore.Target target,boolean full){
        if(android.os.Build.VERSION.SDK_INT<29||closed||busy()||(full&&!backupAvailable(target)))return;
        String snapshot=receipt(target);
        confirm("儲存到下載資料夾？","將儲存至下載 / CinemaTV，完成後會核對大小與 SHA-256。這仍是設備內副本，備份請另存設備外。本次只匯出，不會套用素材或要求 Root。",false,()->{
            if(closed||busy()||!snapshot.equals(receipt(target))||(full&&!backupAvailable(target))){notice.accept("狀態已變更，請重新選擇匯出；原備份保留。");return;}
            try{String saved=new JSONObject().put("target",target.name()).put("full",full).put("receipt",snapshot).put("flowRun","").toString();exportWorker(saved,null,true);}
            catch(Exception ignored){notice.accept("無法建立匯出紀錄；原備份保留。");}
        });
    }
    private void export(BootAssetStore.Target t,boolean full,String flowRun){
        if(closed||operationBusy()||(flowRun.isEmpty()&&busy())||(full&&!backupAvailable(t))){if(!flowRun.isEmpty()&&logoFlow!=null)logoFlow.exported(flowRun,false);return;}
        try{
            JSONObject pending=new JSONObject().put("target",t.name()).put("full",full).put("receipt",receipt(t)).put("flowRun",flowRun);
            if(!activity.getPreferences(0).edit().putString("studio_export",pending.toString()).commit())throw new IOException();
            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_LOCAL_ONLY,true)
                .setType(full?(t==BootAssetStore.Target.LOGO?"application/octet-stream":"application/zip"):"application/json")
                .putExtra(Intent.EXTRA_TITLE,full?(t==BootAssetStore.Target.LOGO?"cinema-original-boot-image-"+record(t).optString("transaction")+".img":"cinema-original-transition.zip"):"cinema-"+t.name().toLowerCase(java.util.Locale.ROOT)+"-receipt.json");
            exporting=true;changed.run();activity.startActivityForResult(intent,EXPORT);
        }catch(Exception e){String saved=activity.getPreferences(0).getString("studio_export","");if(!saved.isEmpty()){exporting=true;changed.run();offerDownload(saved);}else{exporting=false;notice.accept("無法建立匯出紀錄；原備份保留。");changed.run();if(!flowRun.isEmpty()&&logoFlow!=null)logoFlow.exported(flowRun,false);}}
    }
    boolean onResult(int request,int result,Intent data){
        if(request!=EXPORT)return false;
        String saved=activity.getPreferences(0).getString("studio_export","");if(saved.isEmpty())return true;activity.getPreferences(0).edit().remove("studio_export").apply();
        String runId="";try{runId=new JSONObject(saved).optString("flowRun");}catch(Exception ignored){}final String exportRun=runId;
        if(result!=Activity.RESULT_OK||data==null||data.getData()==null){exporting=false;changed.run();notice.accept("已取消儲存，原備份仍保留。");if(logoFlow!=null)logoFlow.exported(exportRun,false);return true;}
        android.net.Uri uri=data.getData();if(!"content".equals(uri.getScheme())){exporting=false;changed.run();notice.accept("請使用系統文件選擇器。");if(logoFlow!=null)logoFlow.exported(exportRun,false);return true;}
        exportWorker(saved,uri,false);return true;
    }
    private CinemaVerifiedExport.Proof writeSnapshot(String saved,OutputStream output)throws Exception{
        JSONObject op=new JSONObject(saved);BootAssetStore.Target target=BootAssetStore.Target.valueOf(op.getString("target"));boolean full=op.getBoolean("full");
        String json=op.getString("receipt");JSONObject snapshot=new JSONObject(json);
        if(!json.equals(receipt(target)))throw new IOException("Transaction changed");
        if(full&&target==BootAssetStore.Target.LOGO){
            File original=logo().backupFile();if(!BootAssetStore.hash(original).equals(snapshot.getString("beforeSha")))throw new IOException("Backup changed");
            CinemaVerifiedExport.Proof proof=CinemaVerifiedExport.file(original,output);
            if(proof.bytes!=BootLogoLayout.PHYSICAL_BYTES||!proof.sha.equals(snapshot.getString("beforeSha")))throw new IOException("Backup changed during export");return proof;
        }
        if(full&&target==BootAssetStore.Target.WALLPAPER){BootWallpaperBackup.Result proof=BootWallpaperBackup.write(output,wallpaper().backupFiles(),json);return new CinemaVerifiedExport.Proof(proof.bytes,proof.sha);}
        byte[] bytes=json.getBytes(StandardCharsets.UTF_8);output.write(bytes);output.flush();return new CinemaVerifiedExport.Proof(bytes.length,BootWallpaperBackup.hex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    }
    private void exportWorker(String saved,android.net.Uri uri,boolean downloads){
        exporting=true;changed.run();io.execute(()->{String message;boolean exportOk=false;String run="";
            try{
                JSONObject op=new JSONObject(saved);BootAssetStore.Target target=BootAssetStore.Target.valueOf(op.getString("target"));boolean full=op.getBoolean("full");String json=op.getString("receipt");run=op.optString("flowRun");
                if(downloads)CinemaVerifiedExport.downloads(activity,full?(target==BootAssetStore.Target.LOGO?"original-boot-image.img":"original-transition.zip"):"boot-"+target.name()+"-receipt.json",full?(target==BootAssetStore.Target.LOGO?"application/octet-stream":"application/zip"):"application/json",out->writeSnapshot(saved,out));
                else CinemaVerifiedExport.document(activity,uri,out->writeSnapshot(saved,out));
                if(!json.equals(receipt(target)))throw new IOException("Transaction changed after export");
                if(full&&target==BootAssetStore.Target.LOGO){JSONObject snapshot=new JSONObject(json);exportOk=logo().confirmBackupExport(snapshot.getString("beforeSha"),BootLogoLayout.PHYSICAL_BYTES);message=exportOk?"原圖備份已儲存並讀回核對。請另存至設備外。":"檔案已儲存，但交易仍鎖定。";}
                else message=full?"備份已儲存並讀回核對；請另存至設備外。":"操作紀錄已儲存並讀回核對。";
                if(downloads)message+="\n位置：下載 / CinemaTV";
            }catch(Exception error){message="儲存未完成或讀回不一致。原備份仍保留，請勿使用不完整匯出檔。";}
            final String note=message,flow=run;boolean ok=exportOk;main.post(()->{exporting=false;if(!closed){changed.run();notice.accept(note);if(logoFlow!=null)logoFlow.exported(flow,ok);}});
        });
    }
    private void offerDownload(String saved){
        if(android.os.Build.VERSION.SDK_INT<29){cancelExport(saved);notice.accept("沒有可用儲存位置；原備份保留。");return;}
        AlertDialog dialog=new AlertDialog.Builder(activity).setTitle("改存到下載資料夾？")
            .setMessage("這台設備沒有可用的文件選擇器。可存至下載 / CinemaTV，完成後會讀回核對。這仍是設備內副本，備份請另外複製到設備外。")
            .setNegativeButton("取消",(d,w)->cancelExport(saved)).setPositiveButton("儲存",(d,w)->{activity.getPreferences(0).edit().remove("studio_export").apply();exportWorker(saved,null,true);}).create();
        dialog.setOnCancelListener(d->cancelExport(saved));dialog.show();dialog.getButton(-2).requestFocus();
    }
    private void cancelExport(String saved){
        exporting=false;activity.getPreferences(0).edit().remove("studio_export").apply();changed.run();
        try{if(logoFlow!=null)logoFlow.exported(new JSONObject(saved).optString("flowRun"),false);}catch(Exception ignored){}
    }
    private static void copy(InputStream in,OutputStream out)throws IOException{byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);out.flush();}
    void close(){if(logoFlow!=null)logoFlow.close();closed=true;permissionPending=false;permissionAsset=null;if(observedLogo!=null)observedLogo.removeObserver(observer);if(observedVideo!=null)observedVideo.removeObserver(observer);if(observedWallpaper!=null)observedWallpaper.removeObserver(observer);main.removeCallbacksAndMessages(null);io.shutdown();}
}
