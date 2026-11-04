package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Non-exported, bounded Project A video transaction page. No automatic Root or reboot. */
public final class BootWallpaperInstallActivity extends Activity {
    private static final int EXPORT=7201;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private BootWallpaperTransactions transactions;private BootAssetStore.Asset asset;
    private TextView title,details,status,apply,restore,audit,backup,export,back;
    private boolean loaded,closed,capable,exportBusy;
    private byte[] exporting;
    private final Runnable observer=()->refresh();
    static boolean reviewedUnit(String hardware,String fingerprint,int sdk,int uid,String pkg){
        return BootDeviceSupport.wallpaper(sdk,uid,pkg);
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(5894);
        transactions=BootWallpaperTransactions.get(this);
        capable=reviewedUnit(Build.HARDWARE,Build.FINGERPRINT,Build.VERSION.SDK_INT,Process.myUid(),getPackageName());
        FrameLayout root=new FrameLayout(this){
            @Override protected void onMeasure(int w,int h){
                float u=Math.min(View.MeasureSpec.getSize(w)/1920f,View.MeasureSpec.getSize(h)/1080f);
                place(title,90,50,1740,70,u,42);place(details,90,145,1740,180,u,25);place(status,90,335,1740,155,u,25);
                TextView[] buttons={apply,restore,audit,backup,export,back};for(int i=0;i<buttons.length;i++)place(buttons[i],90,500+i*80,1740,64,u,25);
                super.onMeasure(w,h);
            }
        };root.setBackgroundColor(0xff101b2b);
        title=label("過場圖片套用與還原",42);details=label("正在讀取準備好的圖片…",25);status=label("",25);
        apply=button("套用這張過場圖片…","wallpaper:apply",()->ask(true));
        restore=button("還原至這次套用前的圖片…","wallpaper:restore",()->ask(false));
        audit=button("唯讀核對目前背景與鎖屏","wallpaper:audit",()->{transactions.audit();refresh();});
        backup=button("匯出原背景與鎖屏備份…","wallpaper:backup",()->export(true));
        export=button("匯出操作紀錄…","wallpaper:receipt",()->export(false));back=button("返回素材頁","wallpaper:back",this::finish);
        for(View view:new View[]{title,details,status,apply,restore,audit,backup,export,back})root.addView(view);
        setContentView(root);transactions.observe(observer);refresh();back.requestFocus();
        io.execute(()->{
            BootAssetStore.Asset ready=null;try{BootAssetStore.Asset a=new BootAssetStore(this).load(BootAssetStore.Target.WALLPAPER);if(a!=null&&a.target==BootAssetStore.Target.WALLPAPER)ready=a;}catch(Exception ignored){}
            BootAssetStore.Asset selected=ready;main.post(()->{if(closed)return;asset=selected;loaded=true;
                details.setText((selected==null?"沒有準備好的過場圖片；請先返回素材頁匯入。":selected.info)+"\n已有獨立鎖屏保留；共用預設背景依確認內容處理。不修改首頁桌布、HOME或ADB。\n每次都先備份、核對；不會自行重啟。");refresh();});
        });
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(0,size);v.setTextColor(Color.WHITE);v.setIncludeFontPadding(false);return v;}
    private TextView button(String text,String tag,Runnable click){TextView v=label(text,27);v.setTag(tag);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(24,0,24,0);v.setFocusable(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(new CinemaSurface(false,0xff91b7e5,false,false));v.setOnFocusChangeListener((view,focus)->v.setBackground(new CinemaSurface(focus,0xff91b7e5,false,false)));v.setOnClickListener(view->{if(v.isEnabled())click.run();});return v;}
    private void place(TextView v,int x,int y,int w,int h,float u,int size){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(w*u),Math.round(h*u));p.leftMargin=Math.round(x*u);p.topMargin=Math.round(y*u);v.setLayoutParams(p);v.setTextSize(0,size*u);}
    private void refresh(){
        if(status==null)return;
        apply.setEnabled(!exportBusy&&capable&&loaded&&asset!=null&&transactions.canApply());restore.setEnabled(!exportBusy&&capable&&transactions.canRestore());audit.setEnabled(!exportBusy&&capable&&transactions.canAudit());backup.setEnabled(!exportBusy&&transactions.canExportBackup());export.setEnabled(!exportBusy&&!transactions.busy());
        for(TextView v:new TextView[]{apply,restore,audit,backup,export}){v.setFocusable(v.isEnabled());v.setAlpha(v.isEnabled()?1f:.4f);}
        status.setText(capable?transactions.message():"此 Android 版本尚不支援保留原圖的系統桌布替換；可選圖與預覽。\n支援 Android 10–12 的可讀取靜態背景；需要備份權限。");
    }
    private void ask(boolean applying){
        if(!capable||!(applying?apply.isEnabled():restore.isEnabled()))return;
        String message=applying?"以本 App 普通身分保存備份，再透過 Android 更換過場圖片。\n\n已有獨立鎖屏保留。若沒有桌布原檔，備份為原廠預設畫面副本；沒有獨立鎖屏時共用背景一起更換，還原時一起恢復備份畫面，不還原缺檔狀態。不修改 Cinema TV 首頁桌布；不要求 Root、不寫分區、不自行重啟。\n\n若尚未允許備份所需的讀取權限，這次只會詢問權限；允許後仍需再次明確確認才套用。逾時或未知即停止。":"將透過 Android 還原已保存的備份畫面；若原本為預設圖副本，共用背景一起還原，不還原缺檔狀態。\n\nAndroid 會分配新的桌布 ID；本步不還原整個系統或桌布歷史 ID。不要求 Root、不自行重啟。";
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(applying?"確認更換過場圖片":"確認還原上次圖片").setMessage(message)
            .setNegativeButton("取消",(d,w)->{}).setPositiveButton(applying?"確認套用":"確認還原",(d,w)->{requestOrSubmit(applying);}).create();
        dialog.show();View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
    }
    private void requestOrSubmit(boolean applying){
        if(checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},7202);return;
        }
        if(applying)transactions.apply(asset);else transactions.restore();refresh();
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);if(request!=7202)return;
        status.setText(results.length==1&&results[0]==android.content.pm.PackageManager.PERMISSION_GRANTED?
            "備份讀取權限已可用；尚未套用，請另行明確確認。":"沒有允許讀取目前背景，未套用；原圖片保留。");
    }
    private void export(boolean full){
        if(exportBusy||transactions.busy()||(full&&!transactions.canExportBackup()))return;
        exporting=transactions.exportReceipt().getBytes(StandardCharsets.UTF_8);
        if(!getPreferences(0).edit().putBoolean("pending_full",full).putString("pending_export",new String(exporting,StandardCharsets.UTF_8)).commit()){status.setText("無法保存匯出狀態；原備份與紀錄保留。");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(full?"application/zip":"application/json")
            .putExtra(Intent.EXTRA_LOCAL_ONLY,true).putExtra(Intent.EXTRA_TITLE,full?"cinema-wallpaper-original-backup.zip":"cinema-boot-wallpaper-receipt.json");
        try{startActivityForResult(intent,EXPORT);}catch(Exception unavailable){exporting=null;status.setText("沒有可用的紀錄匯出位置；原紀錄仍保留。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT)return;
        byte[] snapshot=exporting!=null?exporting:getPreferences(0).getString("pending_export","").getBytes(StandardCharsets.UTF_8);exporting=null;
        boolean full=getPreferences(0).getBoolean("pending_full",false);getPreferences(0).edit().remove("pending_export").remove("pending_full").apply();
        final byte[] bytes=snapshot.length>0?snapshot:null;
        if(result!=RESULT_OK||data==null||data.getData()==null||bytes==null){status.setText("已取消紀錄匯出；沒有要求系統更換背景。");return;}
        android.net.Uri uri=data.getData();if(!"content".equals(uri.getScheme())){status.setText("只接受系統文件選擇器。");return;}
        exportBusy=true;refresh();io.execute(()->{
            String message;
            try{
                long expectedSize;String expectedSha;
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null)throw new java.io.IOException();
                    if(full){String json=new String(bytes,StandardCharsets.UTF_8);org.json.JSONObject original=new org.json.JSONObject(json),current=new org.json.JSONObject(transactions.exportReceipt());if(!original.getString("transaction").equals(current.getString("transaction")))throw new java.io.IOException("Transaction changed");File[] files=transactions.backupFiles();BootWallpaperBackup.Result archiveResult=BootWallpaperBackup.write(out,files,json);expectedSize=archiveResult.bytes;expectedSha=archiveResult.sha;}
                    else{out.write(bytes);out.flush();expectedSize=bytes.length;expectedSha=BootWallpaperBackup.hex(MessageDigest.getInstance("SHA-256").digest(bytes));}
                }
                MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0,limit=full?48L*1024*1024:32768;
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException();byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){total+=n;if(total>limit)throw new java.io.IOException();hash.update(buffer,0,n);}}
                if(total!=expectedSize||!BootWallpaperBackup.hex(hash.digest()).equals(expectedSha))throw new java.io.IOException();
                message=full?"原背景與鎖屏圖片備份已匯出並讀回核對；請保存至設備外。這不是整機韌體。":"操作紀錄已匯出並讀回核對。這不是影片備份或整機恢復映像。";
            }catch(Exception error){message="操作紀錄匯出未完成或無法核對；App內紀錄仍保留。";}
            String finished=message;main.post(()->{exportBusy=false;if(!closed){refresh();status.setText(finished);}});
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){if((event.getKeyCode()==23||event.getKeyCode()==66)&&event.getRepeatCount()>0)return true;return super.dispatchKeyEvent(event);}
    @Override protected void onDestroy(){closed=true;transactions.removeObserver(observer);main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
