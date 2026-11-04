package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Local drafts and guarded device transactions share one page but keep separate state. */
public final class BootAppearanceActivity extends Activity {
    private static final int PICK=BootMaterialPicker.DOCUMENT_REQUEST,PICK_CONTENT=BootMaterialPicker.CONTENT_REQUEST,EXPORT=7102,READ_LOCAL=7303,CHECK_WALLPAPER_PERMISSION=7304;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private BootAssetStore store;private BootAssetStore.Asset asset;
    private BootAssetStore.Target target=BootAssetStore.Target.LOGO;
    private Screen screen;private TextView status;private ImageView image;private VideoView video;
    private TextView download,diagnostics,observation,choose,frame,play,export,manage,back,restore,more,backup,receipt,audit,otherPicker,pickDiagnostic,checkWallpaper,checkVideo,transactionDiagnostic,sourceProbe,reviewNoWrite,logoProbe,logoProbeExport,logoReview,logoAdoptReview;
    private boolean wallpaperCheckPermissionPending;
    private String selectionMessage,lastRootReceipt;
    private BootMaterialPicker picker;
    private BootStudioActions actions;private boolean expanded,diagnosticsExpanded;private final List<TextView> buttons=new ArrayList<>();
    private final List<TextView> targets=new ArrayList<>();
    private boolean busy,closed,playing;private CinemaUiSounds sounds;
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if(saved!=null)selectionMessage=saved.getString("selection_message");
        store=new BootAssetStore(this);screen=new Screen();setContentView(screen);actions=new BootStudioActions(this,this::refresh,message->{selectionMessage=null;status.setText(message);});sounds=new CinemaUiSounds(screen,new CinemaSettings(this));
        picker=new BootMaterialPicker(this::startActivityForResult);load();
    }
    private void load(){busy=true;refresh();worker.execute(()->{
        BootAssetStore.Asset loaded=null;Bitmap preview=null;String error=null;
        try{loaded=store.load();if(loaded!=null)preview=poster(loaded);}catch(Exception e){loaded=null;error="先前素材無法驗證；請重新選檔，原始來源未更動。";}
        BootAssetStore.Asset ready=loaded;Bitmap picture=preview;String note=error;main.post(()->{if(closed)return;busy=false;if(ready!=null){asset=ready;target=ready.target;image.setImageBitmap(picture);}if(note!=null&&selectionMessage==null)selectionMessage=note;refresh();screen.focusFirstAction();});
    });}
    private Bitmap poster(BootAssetStore.Asset item)throws IOException{BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(item.poster.getPath(),o);if(o.outWidth!=640||o.outHeight!=360)throw new IOException("預覽無效");o.inJustDecodeBounds=false;Bitmap result=BitmapFactory.decodeFile(item.poster.getPath(),o);if(result==null)throw new IOException("預覽無法解碼");return result;}
    private boolean working(){return busy||(actions!=null&&actions.busy());}
    private boolean ready(){return asset!=null&&asset.target==target;}
    private void select(BootAssetStore.Target next){
        if(working())return;selectionMessage=null;lastRootReceipt=null;stopPreview();target=next;asset=null;busy=true;image.setVisibility(View.INVISIBLE);refresh();status.setText("正在讀取這一項素材…");
        worker.execute(()->{BootAssetStore.Asset selected=null;Bitmap picture=null;String problem=null;
            try{selected=store.load(next);if(selected!=null)picture=poster(selected);}catch(Exception error){problem="這一項素材無法驗證，其他素材保留。";}
            BootAssetStore.Asset result=selected;Bitmap preview=picture;String error=problem;
            main.post(()->{if(closed)return;busy=false;asset=result;if(result!=null)image.setImageBitmap(preview);image.setVisibility(ready()?View.VISIBLE:View.INVISIBLE);selectionMessage=error;refresh();screen.focusTargetActions();});
        });
    }
    private void pick(){pick(false);}
    private void pick(boolean otherApp){
        if(working()||!actions.canEdit(target))return;selectionMessage=null;stopPreview();getPreferences(0).edit().putString("pick_target",target.name()).commit();
        try{getPreferences(0).edit().putString("picker_diagnostic",new org.json.JSONObject().put("target",target.name()).put("mode",otherApp?"content":"document").toString()).commit();}catch(org.json.JSONException ignored){}
        recordPick("OPENING","");
        BootMaterialPicker.Opened opened=picker.open(target,otherApp);
        if(opened==BootMaterialPicker.Opened.NONE){
            getPreferences(0).edit().remove("pick_target").remove("pick_request").apply();
            String message="這台盒子沒有可用的圖片／影片選擇器。請安裝支援選取檔案的檔案管理器，再回來重試。";
            recordPick("NO_PICKER","");selectionMessage(message);Toast.makeText(this,message,Toast.LENGTH_LONG).show();
        }else{getPreferences(0).edit().putInt("pick_request",opened.requestCode).commit();recordPick("OPENED",opened.name());selectionMessage(opened==BootMaterialPicker.Opened.CONTENT?"請在檔案 App 中選擇素材。":"請在系統檔案中選擇素材。");}
    }
    private interface Work {BootAssetStore.Asset run()throws Exception;}
    private void prepare(Work work){
        if(working())return;busy=true;stopPreview();selectionMessage="正在複製、檢查與建立預覽…\n影片會逐幀解碼，請稍候；Back 可離開，設備保持原狀。";refresh();
        worker.execute(()->{try{BootAssetStore.Asset result=work.run();BootAssetStore.checkCancelled(cancelled::get);Bitmap preview=poster(result);BootAssetStore.checkCancelled(cancelled::get);store.commit(result);
            main.post(()->{if(closed)return;busy=false;asset=result;target=result.target;lastRootReceipt=actions.receipt(target);image.setImageBitmap(preview);image.setVisibility(View.VISIBLE);recordPick("READY","");selectionMessage="已選取"+displayTitle(target)+"素材，預覽已更新。\n"+actions.selectionSummary(target,asset);refresh();if(manage.isEnabled())manage.requestFocus();else screen.focusFirstAction();});
        }catch(Exception|OutOfMemoryError error){main.post(()->{if(closed)return;busy=false;recordPick("IMPORT_FAILED",error.getClass().getSimpleName());importFailure("素材未匯入，原素材保留。\n"+safeError(error));refresh();screen.focusFirstAction();});}});
    }
    private static String safeError(Throwable error){if(error instanceof SecurityException)return "檔案管理器未提供讀取權限。請將素材複製到盒子的下載資料夾，再用系統檔案選擇。";if(error instanceof FileNotFoundException)return "無法讀取選取的檔案。請先將素材複製到盒子的下載資料夾，再重新選擇。";String text=error.getMessage();return text==null||text.length()>180?"無法解碼或資源不足，請使用符合規格的本機檔案。":text;}
    private void reframe(){if(!ready()||working()||!actions.canEdit(target)||target==BootAssetStore.Target.VIDEO)return;BootAssetStore.Asset previous=asset;prepare(()->store.reframe(previous,!previous.crop,cancelled::get));}
    private void export(){
        if(!ready()||working())return;selectionMessage=null;stopPreview();getPreferences(0).edit().putString("export_id",asset.id).commit();
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(target==BootAssetStore.Target.VIDEO?"video/mp4":"image/png").putExtra(Intent.EXTRA_TITLE,asset.filename()).putExtra(Intent.EXTRA_LOCAL_ONLY,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        try{startActivityForResult(intent,EXPORT);}catch(android.content.ActivityNotFoundException|SecurityException error){offerMaterialDownload();}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(actions.onResult(request,result,data))return;if(request!=PICK&&request!=PICK_CONTENT&&request!=EXPORT)return;
        if(request==PICK||request==PICK_CONTENT){picked(request,result,data);return;}
        if(result!=RESULT_OK||data==null||data.getData()==null){getPreferences(0).edit().remove("pick_target").remove("export_id").apply();status.setText(request==PICK?"已取消選檔；原素材保留。若選檔頁無法使用，可展開更多選項，改用其他檔案 App。":"已取消匯出；原素材保留。");return;}
        Uri uri=data.getData();if(!"content".equals(uri.getScheme())){getPreferences(0).edit().remove("pick_target").remove("export_id").apply();status.setText("此檔案 App 未提供可讀取的文件連結，請改用系統檔案或其他檔案 App。");return;}
        String id=getPreferences(0).getString("export_id","");getPreferences(0).edit().remove("export_id").apply();afterLoad(()->{if(asset==null||!asset.id.equals(id)){status.setText("素材版本已改變，請重新選擇匯出。");return;}writeExport(uri);});
    }
    private void picked(int request,int result,Intent data){
        // A failed OPEN_DOCUMENT launch can also enqueue RESULT_CANCELED. It must not
        // consume the live GET_CONTENT request, including after Activity recreation.
        if(request!=getPreferences(0).getInt("pick_request",-1)||!getPreferences(0).contains("pick_target")){
            try{android.util.Log.i("CinemaTVPicker",new org.json.JSONObject().put("stage","IGNORED_RESULT").put("request",request).put("result",result).put("shape",BootMaterialInput.shape(data)).put("at",System.currentTimeMillis()).toString());}catch(org.json.JSONException ignored){}
            return;
        }
        String saved=getPreferences(0).getString("pick_target","");getPreferences(0).edit().remove("pick_target").remove("pick_request").commit();
        try{org.json.JSONObject log=new org.json.JSONObject(getPreferences(0).getString("picker_diagnostic","{}"));log.put("request",request).put("result",result).put("shape",BootMaterialInput.shape(data));getPreferences(0).edit().putString("picker_diagnostic",log.toString()).commit();}catch(org.json.JSONException ignored){}
        if(result!=RESULT_OK){recordPick("CANCELLED","");selectionMessage("已取消選檔；原素材保留。若選檔頁無法使用，可展開更多選項，改用其他檔案 App。");return;}
        try{
            BootAssetStore.Target picked=BootAssetStore.Target.valueOf(saved);BootMaterialInput.Selection selected=BootMaterialInput.selected(data);
            recordPick("RECEIVED",selected.route+":"+selected.uri.getScheme());selectionMessage("已收到選取的素材，正在檢查讀取方式…");
            afterLoad(()->importSelected(selected.uri,picked));
        }catch(Exception error){recordPick("INVALID_RESULT",error.getClass().getSimpleName());importFailure(safeError(error));}
    }
    private void importSelected(Uri uri,BootAssetStore.Target picked){
        if(closed||working()){if(!closed)importFailure("目前有操作進行中，請完成後重新選取素材。");return;}
        if(BootMaterialInput.needsReadPermission(uri,android.os.Build.VERSION.SDK_INT,checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)==android.content.pm.PackageManager.PERMISSION_GRANTED)){
            if(!getPreferences(0).edit().putString("local_pick_uri",uri.toString()).putString("local_pick_target",picked.name()).commit()){importFailure("無法保存選檔狀態，請重新選擇。");return;}
            recordPick("READ_PERMISSION_REQUIRED","");selectionMessage("這個檔案管理器傳回本機路徑。請允許讀取檔案，才能匯入預覽；不會套用開機素材。");
            try{requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},READ_LOCAL);}catch(RuntimeException error){clearLocalPick();recordPick("READ_PERMISSION_FAILED",error.getClass().getSimpleName());importFailure("無法詢問檔案讀取權限，請改用系統文件選擇器。");}
            return;
        }
        recordPick("IMPORTING",uri.getScheme());prepare(()->store.prepare(picked,false,cancelled::get,()->BootMaterialInput.open(this,uri)));
    }
    private void clearLocalPick(){getPreferences(0).edit().remove("local_pick_uri").remove("local_pick_target").commit();}
    private void checkVideoRoute(){
        if(working()||target!=BootAssetStore.Target.VIDEO)return;
        busy=true;refresh();selectionMessage("正在唯讀檢查目前開機影片路徑…");
        worker.execute(()->{
            String message;boolean success=false;
            try{message=BootVideoRoute.inspect();success=true;}
            catch(Exception error){message="影片路徑檢查未通過，尚未要求 Root 或套用。\n"+safeError(error);}
            String result=message;boolean passed=success;
            main.post(()->{if(closed)return;busy=false;refresh();selectionMessage(result);android.util.Log.i("CinemaTVVideoRoute",passed?"READ_ONLY_ROUTE_READY":"READ_ONLY_ROUTE_FAILED");});
        });
    }
    private void checkWallpaperBackup(){
        if(working()||!actions.supported(BootAssetStore.Target.WALLPAPER))return;
        if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            wallpaperCheckPermissionPending=true;
            selectionMessage("請允許讀取目前背景以檢查備份；本次只檢查，不套用圖片。");
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},CHECK_WALLPAPER_PERMISSION);return;
        }
        busy=true;refresh();selectionMessage("正在檢查目前背景是否可完整備份；不更換圖片…");
        worker.execute(()->{
            String message;boolean success=false;
            File directory=new File(getCacheDir(),"wallpaper-check-"+UUID.randomUUID());
            try{
                if(!directory.mkdir())throw new IOException("無法建立暫存檢查目錄。");
                BootWallpaperTransactions.Snapshot snapshot=new BootWallpaperBackend(this).capture(new File(directory,"inspect.image"));snapshot.valid();success=true;
                message=snapshot.defaultImage?"系統未提供桌布原檔；已驗證可保存原廠預設圖的畫面副本。沒有獨立鎖屏時，共用背景會一起更換；尚未套用。":"目前背景可完整讀取並備份，鎖屏圖片可保留。尚未套用；請返回上方確認想使用的圖片。";
            }catch(Exception error){message="目前背景檢查未通過，沒有更換圖片。\n"+(error instanceof SecurityException?"請允許讀取目前背景所需的權限。":safeError(error));}
            finally{BootAssetStore.deleteDraft(directory);}
            String result=message;boolean passed=success;
            main.post(()->{if(closed)return;busy=false;refresh();selectionMessage(result);android.util.Log.i("CinemaTVWallpaper",passed?"READ_ONLY_CHECK_READY":"READ_ONLY_CHECK_FAILED");});
        });
    }
    private void selectionMessage(String text){selectionMessage=text;if(status!=null)status.setText(text);}
    private void importFailure(String text){selectionMessage(text);Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private void recordPick(String stage,String detail){
        try{org.json.JSONObject record=new org.json.JSONObject(getPreferences(0).getString("picker_diagnostic","{}"));record.put("stage",stage).put("detail",detail).put("at",System.currentTimeMillis());getPreferences(0).edit().putString("picker_diagnostic",record.toString()).commit();android.util.Log.i("CinemaTVPicker",record.toString());}catch(org.json.JSONException ignored){}
    }
    private void showTransactionDiagnostic(){
        if(working()||actions==null)return;
        String receipt=actions.receipt(target);BootAssetStore.Asset selected=asset;
        busy=true;refresh();
        worker.execute(()->{
            String json;
            try{
                org.json.JSONObject document=new org.json.JSONObject().put("receipt",new org.json.JSONObject(receipt)).put("readOnly",true);
                if(selected!=null&&selected.target==BootAssetStore.Target.VIDEO)document.put("sourceAudit",BootVideoSourceAudit.inspect(selected));
                if(selected!=null&&selected.target==BootAssetStore.Target.LOGO)document.put("logoSourceAudit",BootVideoSourceAudit.inspect(selected).put("assetId",selected.id).put("target",selected.target.name()));
                String probe=getSharedPreferences("boot_video_source_probe_v1",0).getString(new org.json.JSONObject(receipt).optString("transaction"),"");
                if(!probe.isEmpty())document.put("sourceProbe",new org.json.JSONObject(probe));
                if(selected!=null&&selected.target==BootAssetStore.Target.LOGO){String logo=getSharedPreferences("boot_logo_read_probe_v1",0).getString(new org.json.JSONObject(receipt).optString("transaction"),"");if(!logo.isEmpty())document.put("logoReadProbe",new org.json.JSONObject(logo));String exported=getSharedPreferences("boot_logo_probe_export_v1",0).getString(new org.json.JSONObject(receipt).optString("transaction"),"");if(!exported.isEmpty())document.put("logoProbeExport",new org.json.JSONObject(exported));String reviewed=getSharedPreferences("boot_logo_candidate_review_v1",0).getString(new org.json.JSONObject(receipt).optString("transaction"),"");if(!reviewed.isEmpty())document.put("logoCandidateReview",new org.json.JSONObject(reviewed));}
                json=document.toString();
            }catch(Exception error){json=receipt;}
            String result=json;main.post(()->{if(closed)return;busy=false;refresh();showTransactionDocument(result);});
        });
    }
    private void showTransactionDocument(String json){
        TextView text=new TextView(this);text.setText(json);text.setTextSize(16);text.setTextIsSelectable(true);text.setPadding(32,16,32,16);text.setTag("boot:transaction-json");
        ScrollView scroll=new ScrollView(this);scroll.addView(text);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("操作紀錄（唯讀）").setView(scroll).setNegativeButton("關閉",null).create();
        dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private String logoEvidence(String preferences){try{return getSharedPreferences(preferences,0).getString(new org.json.JSONObject(actions.receipt(BootAssetStore.Target.LOGO)).getString("transaction"),"");}catch(Exception invalid){return "";}}
    private boolean canReviewLogo(){return !working()&&actions!=null&&target==BootAssetStore.Target.LOGO&&BootLogoCandidateReview.eligible(actions.receipt(target),logoEvidence("boot_logo_read_probe_v1"),logoEvidence("boot_logo_probe_export_v1"),asset,android.os.Process.myUid());}
    private boolean canPrepareLogoReview(){return canReviewLogo()&&logoEvidence("boot_logo_candidate_review_v1").isEmpty();}
    private boolean canAdoptLogoReview(){
        if(!canReviewLogo()||!logoEvidence("boot_logo_review_adoption_v1").isEmpty())return false;
        try{return new org.json.JSONObject(logoEvidence("boot_logo_candidate_review_v1")).getString("state").equals("PREPARED_READ_ONLY");}catch(Exception invalid){return false;}
    }
    private void prepareLogoReview(){
        if(!canPrepareLogoReview())return;String original=actions.receipt(target),probe=logoEvidence("boot_logo_read_probe_v1"),exported=logoEvidence("boot_logo_probe_export_v1");BootAssetStore.Asset selected=asset;
        final String id;try{id=new org.json.JSONObject(original).getString("transaction");String pending=new org.json.JSONObject().put("state","PENDING").put("readOnly",true).toString();if(!getSharedPreferences("boot_logo_candidate_review_v1",0).edit().putString(id,pending).commit())throw new IOException();}catch(Exception failure){selectionMessage("無法保存候選核對請求，未要求Root。");return;}
        busy=true;refresh();worker.execute(()->{
            org.json.JSONObject result;
            try{result=BootLogoCandidateReview.prepare(this,original,probe,exported,selected);}catch(Exception|OutOfMemoryError failure){result=new org.json.JSONObject();try{result.put("state","STOPPED").put("error",failure.getClass().getSimpleName()+": "+failure.getMessage());}catch(Exception ignored){}}
            boolean saved=getSharedPreferences("boot_logo_candidate_review_v1",0).edit().putString(id,result.toString()).commit();
            main.post(()->{if(closed)return;busy=false;refresh();selectionMessage(saved?"候選核對結果已保存；原UNKNOWN仍鎖定，沒有Root或分區寫入。請查看操作紀錄。":"候選紀錄未保存，請保留現場，不重送。");});
        });
    }
    private void askAdoptLogoReview(){
        if(!canAdoptLogoReview())return;String original=actions.receipt(target),probe=logoEvidence("boot_logo_read_probe_v1"),exported=logoEvidence("boot_logo_probe_export_v1"),review=logoEvidence("boot_logo_candidate_review_v1");BootAssetStore.Asset selected=asset;
        CheckBox rescue=new CheckBox(this);rescue.setText("我有可用的離線復原方式，且完整備份已存於設備外");rescue.setPadding(28,16,28,16);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("人工核對這筆唯讀失敗與候選？")
            .setMessage("僅接受已診斷的這一筆舊唯讀失敗、相同圖片、完成的Root唯讀證據與備份匯出證據。確認後重新核對檔案及候選，將原UNKNOWN完整保留於新的READY交易，不清除歷史。其他UNKNOWN與所有PENDING不適用。本步不要求Root、不寫分區、不重啟，也不自動接續套用；套用仍需另一個明確確認。")
            .setView(rescue).setNegativeButton("先不要",null).setPositiveButton("確認人工核對",(d,w)->{
                if(!rescue.isChecked()||!canAdoptLogoReview()||!original.equals(actions.receipt(target))||!review.equals(logoEvidence("boot_logo_candidate_review_v1")))return;
                final String id;try{id=new org.json.JSONObject(original).getString("transaction");String pending=new org.json.JSONObject().put("state","PENDING").put("at",System.currentTimeMillis()).toString();if(!getSharedPreferences("boot_logo_review_adoption_v1",0).edit().putString(id,pending).commit())throw new IOException();}catch(Exception failure){selectionMessage("無法保存人工核對紀錄，維持原鎖定。");return;}
                busy=true;refresh();worker.execute(()->{boolean accepted=false;String error="";
                    try{accepted=actions.logo().adoptReviewedCandidate(BootLogoCandidateReview.audit(this,original,probe,exported,review,selected),true);}catch(Exception|OutOfMemoryError failure){error=failure.getClass().getSimpleName();}
                    try{getSharedPreferences("boot_logo_review_adoption_v1",0).edit().putString(id,new org.json.JSONObject().put("state",accepted?"SUBMITTED":"STOPPED").put("error",error).toString()).commit();}catch(Exception ignored){}
                    main.post(()->{if(closed)return;busy=false;selectionMessage=null;refresh();});
                });
            }).create();dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(false);rescue.setOnCheckedChangeListener((v,c)->dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setEnabled(c));dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private boolean canExportLogoProbe(){
        if(working()||actions==null||target!=BootAssetStore.Target.LOGO||android.os.Build.VERSION.SDK_INT<29||android.os.Build.VERSION.SDK_INT>32)return false;
        try{String receipt=actions.receipt(target),id=new org.json.JSONObject(receipt).getString("transaction"),probe=getSharedPreferences("boot_logo_read_probe_v1",0).getString(id,"");
            return BootLogoProbeExport.eligible(receipt,probe,android.os.Process.myUid())&&!getSharedPreferences("boot_logo_probe_export_v1",0).contains(id);
        }catch(Exception invalid){return false;}
    }
    private void askExportLogoProbe(){
        if(!canExportLogoProbe())return;String original=actions.receipt(target);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("保存唯讀資源備份到下載資料夾？")
            .setMessage("不要求Root，將已核對的32MiB開機圖片資源備份複製到下載資料夾的CinemaTV-backups，再讀回核對SHA。請之後複製至電腦核對並妥善保管；這不是整機韌體備份。原備份、UNKNOWN及套用鎖定保留，不更換圖片、不重啟。")
            .setNegativeButton("先不要",null).setPositiveButton("確認保存備份",(d,w)->{
                if(!canExportLogoProbe()||!original.equals(actions.receipt(target)))return;
                final String id,probe;final org.json.JSONObject record=new org.json.JSONObject();
                try{id=new org.json.JSONObject(original).getString("transaction");probe=getSharedPreferences("boot_logo_read_probe_v1",0).getString(id,"");
                    record.put("state","PENDING").put("transaction",id).put("at",System.currentTimeMillis());
                    if(!getSharedPreferences("boot_logo_probe_export_v1",0).edit().putString(id,record.toString()).commit())throw new IOException();
                }catch(Exception failure){selectionMessage("無法保存匯出紀錄，沒有讀寫資源分區。");return;}
                busy=true;refresh();worker.execute(()->{
                    try{record.put("result",BootLogoProbeExport.run(this,original,probe)).put("state","FINISHED");}
                    catch(Exception failure){try{record.put("state","STOPPED").put("error",failure.getClass().getSimpleName()+": "+failure.getMessage());}catch(Exception ignored){}}
                    boolean saved=getSharedPreferences("boot_logo_probe_export_v1",0).edit().putString(id,record.toString()).commit();
                    main.post(()->{if(closed)return;busy=false;refresh();selectionMessage(saved?"備份保存結果已記錄，請查看操作紀錄並複製至電腦核對。原UNKNOWN與鎖定保留。":"匯出結果未保存；原備份保留，請勿重送。");});
                });
            }).create();dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private boolean canProbeLogo(){
        if(working()||actions==null||target!=BootAssetStore.Target.LOGO)return false;
        String text=actions.receipt(target);
        try{return BootLogoReadProbe.eligible(text,android.os.Process.myUid())&&!getSharedPreferences("boot_logo_read_probe_v1",0).contains(new org.json.JSONObject(text).getString("transaction"));}catch(Exception invalid){return false;}
    }
    private void askLogoProbe(){
        if(!canProbeLogo())return;String original=actions.receipt(target);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("唯讀檢查開機圖片資源？")
            .setMessage("額外要求一次 su 0，只讀取目前槽位的完整32MiB資源區至App備份，核對SHA及圖片格式。只支援明確的非分槽或_a／_b槽位，不猜測、不讀另一槽。原UNKNOWN紀錄與套用鎖定保留，不寫分區、不套用、不重啟；每筆紀錄只派送一次。")
            .setNegativeButton("先不要",null).setPositiveButton("確認唯讀檢查",(d,w)->{
                if(!canProbeLogo()||!original.equals(actions.receipt(target)))return;
                final String id;final org.json.JSONObject record=new org.json.JSONObject();
                try{id=new org.json.JSONObject(original).getString("transaction");record.put("state","PENDING").put("readOnly",true).put("transaction",id).put("originalReceipt",new org.json.JSONObject(original)).put("at",System.currentTimeMillis());
                    if(!getSharedPreferences("boot_logo_read_probe_v1",0).edit().putString(id,record.toString()).commit())throw new IOException();
                }catch(Exception error){selectionMessage("檢查紀錄無法保存，沒有要求Root。");return;}
                busy=true;refresh();worker.execute(()->{
                    try{record.put("result",BootLogoReadProbe.run(this,original)).put("state","FINISHED");}
                    catch(Exception error){try{record.put("state","STOPPED").put("error",error.getClass().getSimpleName()+": "+error.getMessage());}catch(Exception ignored){}}
                    boolean saved=getSharedPreferences("boot_logo_read_probe_v1",0).edit().putString(id,record.toString()).commit();
                    main.post(()->{if(closed)return;busy=false;refresh();selectionMessage(saved?"唯讀檢查已記錄；原UNKNOWN與套用鎖定保留。請查看操作紀錄。":"檢查結果未能保存，請保留現場，不重送。");});
                });
            }).create();dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private boolean canProbeSource(){
        if(working()||actions==null||target!=BootAssetStore.Target.VIDEO)return false;
        String receipt=actions.receipt(target);
        try{return BootVideoSourceProbe.eligible(receipt,asset,android.os.Process.myUid())&&!getSharedPreferences("boot_video_source_probe_v1",0).contains(new org.json.JSONObject(receipt).getString("transaction"));}
        catch(Exception invalid){return false;}
    }
    private String sourceProbeRecord(){
        try{return getSharedPreferences("boot_video_source_probe_v1",0).getString(new org.json.JSONObject(actions.receipt(BootAssetStore.Target.VIDEO)).getString("transaction"),"");}
        catch(Exception invalid){return "";}
    }
    private boolean canReviewSource(){return !working()&&actions!=null&&target==BootAssetStore.Target.VIDEO&&actions.movie().canReviewSourceNoWrite(sourceProbeRecord(),asset);}
    private void askReviewNoWrite(){
        if(!canReviewSource())return;
        String receipt=actions.receipt(target),probe=sourceProbeRecord();BootAssetStore.Asset selected=asset;
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("確認這筆未寫入紀錄？")
            .setMessage("已取得這筆exit20交易的Root未寫入證據及完整Root唯讀來源檢查。\n\n確認後會以普通App身分重新核對來源、原廠影片、開機屬性及新目錄不存在；全部通過才結束這筆FAILED_NO_TARGET_WRITE停止狀態。原失敗、探針及核對紀錄完整保留。\n\n本步不用Root、不更換影片，不接續套用；之後套用仍需另外確認。PENDING／UNKNOWN及其他失敗不適用。")
            .setNegativeButton("先不要",null).setPositiveButton("確認唯讀核對",(d,w)->{
                if(!canReviewSource()||!receipt.equals(actions.receipt(target))||!probe.equals(sourceProbeRecord()))return;
                busy=true;refresh();worker.execute(()->{actions.movie().acknowledgeSourceNoWrite(probe,selected,()->BootVideoSourceAudit.reviewNoWrite(selected));main.post(()->{if(closed)return;busy=false;selectionMessage=null;refresh();});});
            }).create();
        dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private void askSourceProbe(){
        if(!canProbeSource())return;
        String receipt=actions.receipt(target);BootAssetStore.Asset selected=asset;
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Root 唯讀檢查來源？")
            .setMessage("這會額外要求一次 su 0，只讀取這筆 exit20 失敗交易的來源檔案路徑、權限、SHA及程序身分。\n\n不更換影片、不設定屬性、不建立Root備份目錄，也不解除目前套用／還原鎖定。原失敗紀錄保留。每筆失敗交易只允許派送一次檢查；逾時或失敗不重送。")
            .setNegativeButton("先不要",null).setPositiveButton("確認唯讀檢查",(d,w)->{
                if(!canProbeSource()||!receipt.equals(actions.receipt(target)))return;
                final String id;try{id=new org.json.JSONObject(receipt).getString("transaction");
                    String pending=new org.json.JSONObject().put("state","PENDING").put("readOnly",true).put("transaction",id).put("at",System.currentTimeMillis()).toString();
                    if(!getSharedPreferences("boot_video_source_probe_v1",0).edit().putString(id,pending).commit())throw new IOException();
                }catch(Exception failed){selectionMessage("無法保存檢查紀錄，未要求 Root。");return;}
                busy=true;refresh();selectionMessage("正在執行一次 Root 唯讀來源檢查，不套用素材…");
                worker.execute(()->{
                    org.json.JSONObject record=new org.json.JSONObject();
                    try{BootVideoTransactions.Result result=BootVideoSourceProbe.run(receipt,selected,android.os.Process.myUid());record.put("state","FINISHED").put("exit",result.exit).put("timeout",result.timeout).put("output",result.output);}
                    catch(Exception failure){try{record.put("state","UNKNOWN").put("error",failure.getClass().getSimpleName());}catch(Exception ignored){}}
                    try{record.put("readOnly",true).put("transaction",id).put("finishedAt",System.currentTimeMillis());}catch(Exception ignored){}
                    boolean saved=getSharedPreferences("boot_video_source_probe_v1",0).edit().putString(id,record.toString()).commit();
                    main.post(()->{if(closed)return;busy=false;refresh();selectionMessage(saved?"唯讀檢查已結束，請查看操作紀錄；原交易仍鎖定，沒有接續套用。":"檢查紀錄保存失敗；保留PENDING，不再派送，也不套用。");});
                });
            }).create();
        dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    private void showPickDiagnostic(){
        String json=getPreferences(0).getString("picker_diagnostic","尚未選取素材。");
        TextView text=text(json,20);text.setPadding(28,24,28,24);text.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(text);
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("最近一次選檔診斷").setView(scroll).setNegativeButton("關閉",null).setPositiveButton("複製",(d,w)->{android.content.ClipboardManager clipboard=(android.content.ClipboardManager)getSystemService(CLIPBOARD_SERVICE);clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Cinema TV 選檔診斷",json));}).create();
        dialog.show();dialog.getButton(android.app.AlertDialog.BUTTON_NEGATIVE).requestFocus();
    }
    // Activity may have been recreated while SAF was open. Wait only for the bounded local restore job.
    private void afterLoad(Runnable job){if(closed)return;if(busy){main.postDelayed(()->afterLoad(job),80);return;}job.run();}
    private void downloadMenu(){
        if(working()||android.os.Build.VERSION.SDK_INT<29)return;
        BootAssetStore.Target selectedTarget=target;BootAssetStore.Asset selectedAsset=asset;String snapshot=actions.receipt(target);
        java.util.List<String> labels=new ArrayList<>();java.util.List<Runnable> jobs=new ArrayList<>();
        if(ready()){labels.add("目前素材");jobs.add(this::offerMaterialDownload);}
        labels.add("操作紀錄（JSON）");jobs.add(()->actions.exportToDownloads(selectedTarget,false));
        if(actions.backupAvailable(target)){labels.add("原圖備份");jobs.add(()->actions.exportToDownloads(selectedTarget,true));}
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("儲存到下載 / CinemaTV")
            .setItems(labels.toArray(new String[0]),(d,index)->{
                if(working()||target!=selectedTarget||asset!=selectedAsset||!snapshot.equals(actions.receipt(target))){selectionMessage("素材或紀錄已變更，請重新選擇匯出。");return;}
                jobs.get(index).run();
            }).setNegativeButton("取消",null).create();dialog.show();dialog.getButton(-2).requestFocus();
    }
    private void offerMaterialDownload(){
        if(android.os.Build.VERSION.SDK_INT<29){selectionMessage("沒有可用的文件匯出位置；素材保留。");return;}
        BootAssetStore.Asset selected=asset;if(selected==null||!ready()||working())return;
        getPreferences(0).edit().remove("export_id").apply();
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("儲存素材至下載資料夾？").setMessage("將匯出並讀回核對，位置為下載 / CinemaTV。只儲存素材，不會套用或更換開機畫面。")
            .setNegativeButton("取消",null).setPositiveButton("儲存",(d,w)->{
                if(working()||asset!=selected)return;busy=true;refresh();selectionMessage("正在儲存並核對素材…");
                worker.execute(()->{String note;try{store.verify(selected);CinemaVerifiedExport.downloads(this,selected.filename(),selected.target==BootAssetStore.Target.VIDEO?"video/mp4":"image/png",out->{CinemaVerifiedExport.Proof proof=CinemaVerifiedExport.file(selected.output,out);if(!selected.sha.equals(proof.sha))throw new IOException("Source changed");return proof;});note="已儲存並核對，位置：下載 / CinemaTV。開機畫面未變更。";}catch(Exception error){note="儲存未完成，App 內素材保留。";}
                    String message=note;main.post(()->{if(closed)return;busy=false;refresh();selectionMessage(message);});});
            }).create();dialog.show();dialog.getButton(-2).requestFocus();
    }
    private void writeExport(Uri uri){
        BootAssetStore.Asset selected=asset;busy=true;refresh();status.setText("正在匯出並讀回核對…");
        worker.execute(()->{String message;
            try{store.verify(selected);MessageDigest md=MessageDigest.getInstance("SHA-256");
                try(InputStream in=new FileInputStream(selected.output);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("無法寫入匯出文件");byte[] bytes=new byte[16384];int count;while((count=in.read(bytes))!=-1){BootAssetStore.checkCancelled(cancelled::get);out.write(bytes,0,count);}out.flush();}
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("匯出後無法讀回");byte[] bytes=new byte[16384];long size=0;int count;while((count=in.read(bytes))!=-1){BootAssetStore.checkCancelled(cancelled::get);size+=count;if(size>BootAssetStore.MAX_BYTES)throw new IOException("匯出大小異常");md.update(bytes,0,count);}}
                StringBuilder hash=new StringBuilder();for(byte b:md.digest())hash.append(String.format(Locale.ROOT,"%02x",b&255));if(!selected.sha.contentEquals(hash))throw new IOException("匯出內容與素材不一致");message="匯出完成，檔案一致性核對通過。\n這不代表開機畫面已變更，也不是整機恢復映像。";
            }catch(Exception e){message="匯出未完成或無法核對。請檢查目的檔案；不要將不完整檔案套用。\nApp 內素材保留。\n"+safeError(e);}
            String result=message;main.post(()->{if(closed)return;busy=false;refresh();status.setText(result);if(export.isShown()&&export.isEnabled())export.requestFocus();else screen.focusFirstAction();});
        });
    }
    private void play(){if(working()||!ready()||target!=BootAssetStore.Target.VIDEO)return;if(playing){stopPreview();return;}image.setVisibility(View.INVISIBLE);video.setVisibility(View.VISIBLE);playing=true;play.setText("停止預覽");video.setVideoPath(asset.output.getPath());video.start();}
    private void manage(){if(working())return;selectionMessage=null;stopPreview();actions.primary(target,asset);}
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==CHECK_WALLPAPER_PERMISSION){
            boolean pending=wallpaperCheckPermissionPending;wallpaperCheckPermissionPending=false;
            if(!pending||closed)return;
            if(permissions.length==1&&android.Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[0])&&results.length==1&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED)checkWallpaperBackup();
            else selectionMessage("沒有允許讀取目前背景；未檢查，也未套用圖片。");
            return;
        }
        if(request!=READ_LOCAL){actions.permissionResult(request,results);return;}
        String uri=getPreferences(0).getString("local_pick_uri",""),saved=getPreferences(0).getString("local_pick_target","");clearLocalPick();if(uri.isEmpty()||saved.isEmpty())return;
        if(permissions.length!=1||!android.Manifest.permission.READ_EXTERNAL_STORAGE.equals(permissions[0])||results.length!=1||results[0]!=android.content.pm.PackageManager.PERMISSION_GRANTED){recordPick("READ_PERMISSION_DENIED","");importFailure("未允許讀取檔案，素材未匯入。可改用系統檔案選擇器。");return;}
        try{BootAssetStore.Target picked=BootAssetStore.Target.valueOf(saved);Uri selected=Uri.parse(uri);recordPick("IMPORTING","file");afterLoad(()->prepare(()->store.prepare(picked,false,cancelled::get,()->BootMaterialInput.open(this,selected))));}catch(Exception error){recordPick("IMPORT_FAILED",error.getClass().getSimpleName());importFailure(safeError(error));}
    }
    @Override protected void onSaveInstanceState(Bundle saved){saved.putString("selection_message",selectionMessage);super.onSaveInstanceState(saved);}
    private void stopPreview(){if(video!=null){video.stopPlayback();video.setVisibility(View.GONE);}playing=false;if(image!=null)image.setVisibility(ready()?View.VISIBLE:View.INVISIBLE);if(play!=null)play.setText("播放動畫預覽");}
    private void refresh(){
        if(actions!=null)actions.beginDisplay();try{
        if(screen==null)return;View previousFocus=screen.findFocus();boolean working=working();
        for(int i=0;i<targets.size();i++){TextView b=targets.get(i);BootAssetStore.Target t=BootAssetStore.Target.values()[i];b.setActivated(t==target);b.setText(String.format(java.util.Locale.ROOT,"%02d  %s%s",i+1,displayTitle(t),t==target?"  ·":""));b.setEnabled(!working);style(b);}
        choose.setText(ready()?"更換素材":"選擇素材");choose.setEnabled(!working&&actions!=null&&actions.canEdit(target));
        frame.setVisibility(target==BootAssetStore.Target.VIDEO?View.GONE:View.VISIBLE);frame.setEnabled(!working&&ready()&&actions!=null&&actions.canEdit(target));
        frame.setText(ready()&&asset.crop?"畫面構圖  ·  填滿":"畫面構圖  ·  完整顯示");
        play.setVisibility(target==BootAssetStore.Target.VIDEO?View.VISIBLE:View.GONE);play.setEnabled(!working&&ready()&&target==BootAssetStore.Target.VIDEO);
        export.setEnabled(!working&&ready());more.setEnabled(!working);pickDiagnostic.setEnabled(!working);otherPicker.setEnabled(!working&&actions!=null&&actions.canEdit(target));manage.setText(actions==null?"準備中…":actions.primaryLabel(target,asset));manage.setEnabled(!busy&&actions!=null&&actions.primaryEnabled(target,asset));
        restore.setEnabled(!busy&&actions!=null&&actions.restoreEnabled(target));
        backup.setEnabled(!busy&&actions!=null&&actions.backupAvailable(target));receipt.setEnabled(!working);audit.setEnabled(!busy&&actions!=null&&actions.auditEnabled(target));
        for(TextView b:new TextView[]{export,backup,receipt,audit,otherPicker,pickDiagnostic,transactionDiagnostic})b.setVisibility(expanded?View.VISIBLE:View.GONE);
        download.setVisibility(expanded&&android.os.Build.VERSION.SDK_INT>=29?View.VISIBLE:View.GONE);download.setEnabled(!working);
        transactionDiagnostic.setEnabled(!working);diagnostics.setEnabled(!working);diagnostics.setVisibility(expanded?View.VISIBLE:View.GONE);
        diagnostics.setText(diagnosticsExpanded?"▾  收起故障排查與歷史修復":"▸  故障排查與歷史修復");
        observation.setVisibility(expanded?View.VISIBLE:View.GONE);observation.setEnabled(!working&&ready()&&actions!=null&&!observationKey().isEmpty());
        logoReview.setEnabled(canPrepareLogoReview());logoReview.setVisibility(expanded&&diagnosticsExpanded&&target==BootAssetStore.Target.LOGO?View.VISIBLE:View.GONE);
        logoAdoptReview.setEnabled(canAdoptLogoReview());logoAdoptReview.setVisibility(expanded&&diagnosticsExpanded&&target==BootAssetStore.Target.LOGO?View.VISIBLE:View.GONE);
        logoProbeExport.setEnabled(canExportLogoProbe());logoProbeExport.setVisibility(expanded&&diagnosticsExpanded&&target==BootAssetStore.Target.LOGO?View.VISIBLE:View.GONE);
        logoProbe.setEnabled(canProbeLogo());logoProbe.setVisibility(expanded&&diagnosticsExpanded&&target==BootAssetStore.Target.LOGO?View.VISIBLE:View.GONE);
        sourceProbe.setEnabled(canProbeSource());sourceProbe.setVisibility(expanded&&diagnosticsExpanded&&target==BootAssetStore.Target.VIDEO?View.VISIBLE:View.GONE);
        reviewNoWrite.setEnabled(canReviewSource());reviewNoWrite.setVisibility(expanded&&diagnosticsExpanded&&target==BootAssetStore.Target.VIDEO?View.VISIBLE:View.GONE);
        checkWallpaper.setEnabled(!working&&actions!=null&&actions.supported(BootAssetStore.Target.WALLPAPER));checkWallpaper.setVisibility(expanded&&target==BootAssetStore.Target.WALLPAPER?View.VISIBLE:View.GONE);
        checkVideo.setEnabled(!working);checkVideo.setVisibility(expanded&&target==BootAssetStore.Target.VIDEO?View.VISIBLE:View.GONE);
        more.setText(expanded?"▾  收起更多選項":"▸  更多選項");screen.technical.setVisibility(expanded?View.VISIBLE:View.GONE);
        screen.technical.setText((ready()?asset.info:"圖片：PNG／JPEG，最多 20 MiB、800 萬像素。")+"\n"+(target==BootAssetStore.Target.VIDEO?"影片：H.264 High L3.1 · 720p · 24fps · 5秒 · 無聲\n":"")+(actions==null?"":actions.diagnostic(target)));
        screen.section.setText(displayTitle(target));screen.placeholder.setVisibility(ready()?View.GONE:View.VISIBLE);
        for(TextView b:buttons){boolean focusable=b.isEnabled()&&b.getVisibility()==View.VISIBLE;b.setFocusable(focusable);b.setFocusableInTouchMode(focusable);b.setAlpha(b.isEnabled()?1f:.42f);}
        if(actions!=null){String current=actions.receipt(target);if(lastRootReceipt!=null&&!lastRootReceipt.equals(current))selectionMessage=null;lastRootReceipt=current;status.setText(selectionMessage!=null?selectionMessage:actions.selectionSummary(target,asset)+observationSummary());}
        screen.repairFocus(previousFocus);
        }finally{if(actions!=null)actions.endDisplay();}
    }
    private String observationKey(){return BootViewingRecord.binding(target,asset,actions.record(target));}
    private String observationSummary(){String key=observationKey();if(key.isEmpty())return "";String value=getSharedPreferences("boot_viewing_v1",0).getString(key,"");return value.isEmpty()?"":"\n使用者觀看紀錄："+value+"（不代表新的讀回核對）";}
    private void recordObservation(){
        if(working()||!ready())return;String binding=observationKey();if(binding.isEmpty())return;
        String[] values={"正常","與預期不符","未看清楚"};final int[] choice={-1};
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("已發生的開機觀看結果 · 不會重啟")
            .setSingleChoiceItems(values,-1,(d,index)->{choice[0]=index;((android.app.AlertDialog)d).getButton(-1).setEnabled(true);})
            .setNegativeButton("取消",null).setPositiveButton("保存紀錄",(d,w)->{
                if(choice[0]<0||!binding.equals(observationKey())||working()){selectionMessage("素材或交易已變更，未保存觀看結果。");return;}
                getSharedPreferences("boot_viewing_v1",0).edit().putString(binding,values[choice[0]]+" · "+java.text.DateFormat.getDateTimeInstance().format(new Date())).apply();refresh();
            }).create();dialog.show();dialog.getButton(-1).setEnabled(false);dialog.getButton(-2).requestFocus();
    }
    private void leave(){
        if(!working()){finish();return;}
        android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("目前仍有操作進行中")
            .setMessage("離開頁面不等於取消已派送的系統寫入；寫入結果會保留在交易紀錄。尚未派送的後續步驟不會自動接續。\n\n匯入或本機讀取會在頁面結束時停止；請勿斷電、重啟或重送套用。")
            .setNegativeButton("留在此頁",null).setPositiveButton("返回設定",(d,w)->finish()).create();dialog.show();dialog.getButton(-2).requestFocus();
    }
    static String displayTitle(BootAssetStore.Target t){return t==BootAssetStore.Target.LOGO?"開機圖片":t==BootAssetStore.Target.VIDEO?"開機動畫":"過場圖片";}
    private void style(TextView view){view.setBackground(new CinemaSurface(view.isFocused(),0xFF91B7E5,view.isActivated(),false));}
    private TextView text(String s,int size){TextView t=new TextView(this);t.setText(s);t.setTextSize(0,size);t.setTextColor(Color.WHITE);t.setIncludeFontPadding(false);return t;}
    private TextView button(String name,String tag,Runnable action){TextView b=text(name,24);b.setId(View.generateViewId());b.setTag(tag);b.setFocusable(true);b.setFocusableInTouchMode(true);b.setClickable(true);b.setDefaultFocusHighlightEnabled(false);b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(22,0,18,0);b.setOnFocusChangeListener((v,f)->{style(b);if(f)b.post(()->{android.graphics.Rect r=new android.graphics.Rect();b.getDrawingRect(r);b.requestRectangleOnScreen(r,false);});});style(b);b.setOnTouchListener((v,event)->{if(event.getActionMasked()==MotionEvent.ACTION_DOWN&&v.isEnabled())v.requestFocus();return false;});CinemaUiSounds.bindClick(b,v->{if(b.isEnabled())action.run();});buttons.add(b);return b;}
    private final class Screen extends FrameLayout implements CinemaUiSounds.Host {
        final TextView heading,subtitle,section,placeholder,technical,eyebrow;
        final FrameLayout preview;
        final ScrollView scroll;
        final LinearLayout rows;
        int width=-1,height=-1;
        Screen(){super(BootAppearanceActivity.this);
            setBackground(CinemaPanelStyle.backdrop());
            eyebrow=text("DEVELOPER  /  STARTUP",17);eyebrow.setTextColor(0xffa5bbd8);addView(eyebrow);
            heading=text("開機外觀素材",40);CinemaPanelStyle.title(heading);subtitle=text("為每一段開機畫面，選一個喜歡的樣子。",23);subtitle.setTextColor(0xffbbcbe0);addView(heading);addView(subtitle);
            for(BootAssetStore.Target t:BootAssetStore.Target.values()){TextView b=button(displayTitle(t),"boot:target:"+t.name(),()->select(t));b.setGravity(Gravity.CENTER);targets.add(b);addView(b);}
            preview=new FrameLayout(BootAppearanceActivity.this);preview.setBackground(new CinemaSurface(false,0xff7899c2,true,false));addView(preview);
            image=new ImageView(BootAppearanceActivity.this);image.setScaleType(ImageView.ScaleType.FIT_CENTER);preview.addView(image,new FrameLayout.LayoutParams(-1,-1));
            placeholder=new CinemaStatusView(BootAppearanceActivity.this);((CinemaStatusView)placeholder).show(CinemaStatusView.Kind.EMPTY,"預覽開機素材","選擇素材後顯示預覽；原畫面不會立即改變。");placeholder.setTextColor(0xffa7bdd8);placeholder.setGravity(Gravity.CENTER);preview.addView(placeholder,new FrameLayout.LayoutParams(-1,-1));
            video=new VideoView(BootAppearanceActivity.this);video.setVisibility(GONE);video.setFocusable(false);video.setFocusableInTouchMode(false);video.setOnCompletionListener(v->stopPreview());video.setOnErrorListener((v,w,e)->{stopPreview();selectionMessage("這台設備無法播放此預覽，請檢查影片規格；原動畫保留。");return true;});preview.addView(video,new FrameLayout.LayoutParams(-1,-1));
            status=text("正在讀取素材…",22);status.setTextColor(CinemaPanelStyle.SECONDARY);status.setBackground(new CinemaSurface(false,0,false,false));status.setPadding(20,12,20,12);addView(status);
            rows=new LinearLayout(BootAppearanceActivity.this);rows.setOrientation(LinearLayout.VERTICAL);scroll=new ScrollView(BootAppearanceActivity.this);scroll.setVerticalScrollBarEnabled(false);scroll.setFocusable(false);scroll.addView(rows);addView(scroll);
            section=text("開機圖片",30);rows.addView(section);technical=text("",21);technical.setTextColor(0xffb0c3db);technical.setTag("boot:technical");
            choose=button("選擇素材","boot:pick",()->pick());frame=button("畫面構圖","boot:frame",()->reframe());play=button("播放動畫預覽","boot:play",()->play());
            manage=button("先備份原圖","boot:manage",()->manage());restore=button("還原上次內容","boot:restore",()->actions.restore(target));
            more=button("▸  更多選項","boot:more",()->{expanded=!expanded;if(!expanded)diagnosticsExpanded=false;refresh();more.requestFocus();});
            download=button("儲存到下載資料夾","boot:download",()->downloadMenu());
            diagnostics=button("▸  故障排查與歷史修復","boot:diagnostics",()->{diagnosticsExpanded=!diagnosticsExpanded;refresh();diagnostics.requestFocus();});
            observation=button("記錄實際開機觀看結果","boot:observation",()->recordObservation());
            export=button("匯出目前素材","boot:export",()->export());backup=button("另存原圖備份","boot:backup",()->actions.export(target,true));receipt=button("匯出操作紀錄","boot:receipt",()->actions.export(target,false));audit=button("核對目前狀態","boot:audit",()->actions.audit(target));
            otherPicker=button("改用其他檔案 App","boot:other-picker",()->pick(true));
            pickDiagnostic=button("查看選檔診斷","boot:pick-diagnostic",()->showPickDiagnostic());
            transactionDiagnostic=button("查看操作紀錄","boot:transaction-diagnostic",()->showTransactionDiagnostic());
            logoReview=button("建立開機圖片候選","boot:logo-candidate-review",()->prepareLogoReview());
            logoAdoptReview=button("人工核對並採用候選","boot:logo-adopt-review",()->askAdoptLogoReview());
            logoProbeExport=button("保存唯讀資源備份","boot:logo-probe-export",()->askExportLogoProbe());
            logoProbe=button("Root 唯讀檢查開機圖片","boot:logo-read-probe",()->askLogoProbe());
            sourceProbe=button("Root 唯讀檢查來源","boot:source-probe",()->askSourceProbe());
            reviewNoWrite=button("核對並確認未寫入紀錄","boot:review-no-write",()->askReviewNoWrite());
            checkWallpaper=button("檢查目前背景能否備份","boot:check-wallpaper",()->checkWallpaperBackup());
            checkVideo=button("檢查目前影片路徑","boot:check-video",()->checkVideoRoute());
            for(View b:new View[]{choose,frame,play,manage,restore,more,export,backup,receipt,transactionDiagnostic,observation,audit,otherPicker,pickDiagnostic,checkWallpaper,checkVideo,technical,download,diagnostics,logoProbe,logoProbeExport,logoReview,logoAdoptReview,sourceProbe,reviewNoWrite})rows.addView(b);
            technical.setFocusable(true);technical.setFocusableInTouchMode(true);technical.setDefaultFocusHighlightEnabled(false);technical.setOnFocusChangeListener((v,f)->{style(technical);if(f)technical.post(()->{android.graphics.Rect r=new android.graphics.Rect();technical.getDrawingRect(r);technical.requestRectangleOnScreen(r,false);});});buttons.add(technical);
            back=button("‹  返回設定","boot:back",()->leave());back.setGravity(Gravity.CENTER);addView(back);
        }
        public CinemaUiSounds uiSounds(){return sounds;}
        private boolean eligible(View v){return v!=null&&buttons.contains(v)&&v.getVisibility()==VISIBLE&&v.isEnabled()&&v.isFocusable();}
        private boolean focusAction(int start,int step){
            for(int i=start;i>=0&&i<rows.getChildCount();i+=step){View v=rows.getChildAt(i);if(eligible(v)&&v.requestFocus())return true;}
            return false;
        }
        void focusFirstAction(){if(!focusAction(0,1))back.requestFocus();}
        void focusTargetActions(){scroll.scrollTo(0,0);focusFirstAction();}
        private boolean focusSelectedTab(){TextView v=targets.get(target.ordinal());return eligible(v)&&v.requestFocus();}
        void repairFocus(View previous){
            View current=findFocus();if(eligible(current)&&current.isShown())return;
            // setFocusable(false) can transfer focus automatically. Keep a usable destination.
            int row=rows.indexOfChild(previous);
            if(row>=0&&(focusAction(row,1)||focusAction(row-1,-1)))return;
            if(!focusSelectedTab())back.requestFocus();
        }
        @Override public boolean dispatchKeyEvent(KeyEvent event){
            if(sounds!=null)sounds.key(event);int key=event.getKeyCode();
            if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){if(playing)stopPreview();else leave();}return true;}
            if(event.getAction()==KeyEvent.ACTION_DOWN){
                if((key==23||key==66)&&event.getRepeatCount()>0)return true;
                if(key>=19&&key<=22){View from=findFocus();int tab=targets.indexOf(from);
                    if(tab>=0){
                        if(key==21||key==22){int step=key==21?-1:1;for(int n=tab+step;n>=0&&n<targets.size();n+=step)if(eligible(targets.get(n))&&targets.get(n).requestFocus())break;return true;}
                        if(key==19)back.requestFocus();else focusFirstAction();return true;
                    }
                    if(from==back){if(key==20)focusSelectedTab();return true;}
                    if(key==21){focusSelectedTab();return true;}if(key==22)return true;
                    int row=rows.indexOfChild(from);
                    if(row<0){focusFirstAction();return true;}
                    if(key==19){if(!focusAction(row-1,-1))focusSelectedTab();}
                    else focusAction(row+1,1);
                    return true;
                }
            }return super.dispatchKeyEvent(event);
        }
        @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);if(w!=width||h!=height){width=w;height=h;arrange(w,h);}super.onMeasure(ws,hs);}
        private void arrange(int w,int h){float u=Math.min(w/1920f,h/1080f);
            put(eyebrow,76,34,1300,26,u);put(heading,76,78,1400,62,u);put(subtitle,80,153,1500,40,u);put(back,1616,70,228,66,u);
            eyebrow.setTextSize(0,17*u);heading.setTextSize(0,40*u);subtitle.setTextSize(0,23*u);back.setTextSize(0,23*u);
            for(int i=0;i<targets.size();i++){TextView b=targets.get(i);put(b,76+i*594,220,580,72,u);b.setTextSize(0,25*u);}
            put(preview,76,330,1024,576,u);preview.setPadding((int)(2*u),(int)(2*u),(int)(2*u),(int)(2*u));((CinemaStatusView)placeholder).scale(u);
            put(status,80,932,1010,112,u);status.setTextSize(0,23*u);put(scroll,1150,330,694,710,u);
            section.setTextSize(0,30*u);section.setPadding(0,0,0,(int)(18*u));section.setLayoutParams(new LinearLayout.LayoutParams(-1,(int)(66*u)));
            for(TextView b:buttons){if(b.getParent()!=rows)continue;b.setTextSize(0,24*u);b.setPadding((int)(24*u),0,(int)(18*u),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,(int)((b==manage?84:70)*u));lp.bottomMargin=(int)(12*u);b.setLayoutParams(lp);}
            technical.setTextSize(0,21*u);technical.setPadding((int)(12*u),(int)(18*u),(int)(12*u),(int)(36*u));technical.setLayoutParams(new LinearLayout.LayoutParams(-1,-2));
        }
        private void put(View view,int x,int y,int w,int h,float u){LayoutParams p=new LayoutParams(Math.round(w*u),Math.round(h*u));p.leftMargin=Math.round(x*u);p.topMargin=Math.round(y*u);view.setLayoutParams(p);}
    }
    @Override protected void onResume(){super.onResume();if(sounds!=null)sounds.resume();if(actions!=null){actions.observe();refresh();}}
    @Override protected void onPause(){stopPreview();if(sounds!=null)sounds.pause();super.onPause();}
    @Override protected void onDestroy(){closed=true;if(actions!=null)actions.close();cancelled.set(true);main.removeCallbacksAndMessages(null);worker.shutdownNow();if(sounds!=null)sounds.close();if(image!=null)image.setImageDrawable(null);super.onDestroy();}
}
