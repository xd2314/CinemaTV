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
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Non-exported, bounded Project A video transaction page. No automatic Root or reboot. */
public final class BootVideoInstallActivity extends Activity {
    private static final int EXPORT=7201;
    private final ExecutorService io=Executors.newSingleThreadExecutor();
    private final Handler main=new Handler(Looper.getMainLooper());
    private BootVideoTransactions transactions;private BootAssetStore.Asset asset;
    private TextView title,details,status,apply,restore,export,back;
    private boolean loaded,closed,capable,auditing;
    private byte[] exporting;
    private final Runnable observer=()->refresh();
    static boolean reviewedUnit(String hardware,String fingerprint,int sdk,int uid,String pkg){
        return hardware.equals("sun50iw9p1")&&fingerprint.equals("Amlogic/p281/p281:10/QP1A.191105.004/eng.hanxia.20210408.165306:userdebug/test-keys")&&sdk==29&&uid==10106&&pkg.equals("com.gathin.tvhome.cinemalite.dev");
    }
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(5894);
        transactions=BootVideoTransactions.get(this);
        capable=BootDeviceSupport.rootMaterials(Build.VERSION.SDK_INT,Process.myUid(),getPackageName());
        FrameLayout root=new FrameLayout(this){
            @Override protected void onMeasure(int w,int h){
                float u=Math.min(View.MeasureSpec.getSize(w)/1920f,View.MeasureSpec.getSize(h)/1080f);
                place(title,90,50,1740,70,u,42);place(details,90,145,1740,180,u,25);place(status,90,335,1740,190,u,25);
                place(apply,90,550,1740,76,u,27);place(restore,90,646,1740,76,u,27);place(export,90,742,1740,76,u,27);place(back,90,838,1740,76,u,27);
                super.onMeasure(w,h);
            }
        };root.setBackgroundColor(0xff101b2b);
        title=label("開機動畫套用與還原",42);details=label("正在讀取準備好的動畫…",25);status=label("",25);
        apply=button("套用這段開機動畫…","video:apply",()->{if(transactions.canAcknowledgeNoWrite())askAudit();else ask(true);});
        restore=button("還原至這次套用前的動畫…","video:restore",()->ask(false));
        export=button("匯出操作紀錄…","video:receipt",this::export);back=button("返回素材頁","video:back",this::finish);
        for(View view:new View[]{title,details,status,apply,restore,export,back})root.addView(view);
        setContentView(root);transactions.observe(observer);refresh();back.requestFocus();
        io.execute(()->{
            BootAssetStore.Asset ready=null;try{ready=new BootAssetStore(this).load(BootAssetStore.Target.VIDEO);}catch(Exception ignored){}
            BootAssetStore.Asset selected=ready;main.post(()->{if(closed)return;asset=selected;loaded=true;
                details.setText((selected==null?"沒有準備好的動畫；請先返回素材頁匯入。":selected.info)+"\n僅更換開機短影片；不修改最早圖片、系統背景、HOME或ADB。\n每次都先備份、核對；不會自行重啟。");refresh();});
        });
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(0,size);v.setTextColor(Color.WHITE);v.setIncludeFontPadding(false);return v;}
    private TextView button(String text,String tag,Runnable click){TextView v=label(text,27);v.setTag(tag);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(24,0,24,0);v.setFocusable(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(new CinemaSurface(false,0xff91b7e5,false,false));v.setOnFocusChangeListener((view,focus)->v.setBackground(new CinemaSurface(focus,0xff91b7e5,false,false)));v.setOnClickListener(view->{if(v.isEnabled())click.run();});return v;}
    private void place(TextView v,int x,int y,int w,int h,float u,int size){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(w*u),Math.round(h*u));p.leftMargin=Math.round(x*u);p.topMargin=Math.round(y*u);v.setLayoutParams(p);v.setTextSize(0,size*u);}
    private void refresh(){
        if(status==null)return;
        boolean acknowledgement=transactions.canAcknowledgeNoWrite();
        apply.setText(acknowledgement?"唯讀核對上次未寫入紀錄…":"套用這段開機動畫…");
        apply.setEnabled(!auditing&&capable&&(acknowledgement||(loaded&&asset!=null&&transactions.canApply())));restore.setEnabled(!auditing&&capable&&transactions.canRestore());export.setEnabled(!auditing&&!transactions.busy());
        for(TextView v:new TextView[]{apply,restore,export}){v.setFocusable(v.isEnabled());v.setAlpha(v.isEnabled()?1f:.4f);}
        status.setText(capable?transactions.message():"目前的 App 安裝身分不支援此操作；素材仍可保留。");
    }
    private void askAudit(){
        if(!capable||auditing||!transactions.canAcknowledgeNoWrite())return;
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("唯讀核對上次未寫入紀錄")
            .setMessage("只處理已辨識的 v36 雜湊檢查錯誤（exit 16）。\n\n以本 App 普通身分核對舊影片、原廠影片、檔案身分與交易目錄不存在。通過才結束停止狀態，並保留原失敗紀錄。\n\n本步不要求 Root、不寫開機影片；稍後套用仍需另行確認。")
            .setNegativeButton("取消",(d,w)->{}).setPositiveButton("確認唯讀核對",(d,w)->{
                if(auditing||!transactions.canAcknowledgeNoWrite())return;auditing=true;refresh();
                io.execute(()->{transactions.acknowledgeNoWrite(()->BootVideoRoot.auditBeforeAcknowledge(getApplicationContext()));
                    main.post(()->{auditing=false;if(!closed)refresh();});});
            }).create();
        dialog.show();View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
    }
    private void ask(boolean applying){
        if(!capable||!(applying?apply.isEnabled():restore.isEnabled()))return;
        String message=applying?"將用本 App 要求既有 Root，先保存並核對當前開機影片、路徑及讀取方式，再套用這段動畫。已核實韌體的相容修正會將資料區影片設為系統媒體服務專用讀取，App預覽仍使用已匯入素材。若使用韌體 video_file 設定，會把新影片存入資料區，並切換開機影片路徑；既有 boot.mp4 則沿用原路徑。\n\n保留原廠系統影片，不會自行重啟。\n\n逾時或結果不明會停止且鎖定，不能重送。是否繼續？":"將用本 App 要求既有 Root，只在當前影片、路徑設定及備份仍符合這次交易時，還原至此次套用前的影片與路徑。\n\n不會回到原廠整機狀態，也不會自行重啟。未知變更、逾時或失敗會停止且鎖定。是否繼續？";
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(applying?"確認更換開機動畫":"確認還原上次動畫").setMessage(message)
            .setNegativeButton("取消",(d,w)->{}).setPositiveButton(applying?"確認套用":"確認還原",(d,w)->{if(applying)transactions.apply(asset);else transactions.restore();refresh();}).create();
        dialog.show();View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
    }
    private void export(){
        if(transactions.busy())return;
        exporting=transactions.exportReceipt().getBytes(StandardCharsets.UTF_8);
        if(!getPreferences(0).edit().putString("pending_export",new String(exporting,StandardCharsets.UTF_8)).commit()){status.setText("無法保存紀錄匯出狀態；原紀錄保留。");return;}
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/json")
            .putExtra(Intent.EXTRA_LOCAL_ONLY,true).putExtra(Intent.EXTRA_TITLE,"cinema-boot-video-receipt.json");
        try{startActivityForResult(intent,EXPORT);}catch(Exception unavailable){exporting=null;status.setText("沒有可用的紀錄匯出位置；原紀錄仍保留。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT)return;
        byte[] snapshot=exporting!=null?exporting:getPreferences(0).getString("pending_export","").getBytes(StandardCharsets.UTF_8);exporting=null;
        getPreferences(0).edit().remove("pending_export").apply();
        final byte[] bytes=snapshot.length>0?snapshot:null;
        if(result!=RESULT_OK||data==null||data.getData()==null||bytes==null){status.setText("已取消紀錄匯出；沒有派送Root操作。");return;}
        android.net.Uri uri=data.getData();if(!"content".equals(uri.getScheme())){status.setText("只接受系統文件選擇器。");return;}
        io.execute(()->{
            String message;
            try{
                try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new java.io.IOException();out.write(bytes);out.flush();}
                MessageDigest hash=MessageDigest.getInstance("SHA-256");int total=0;
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new java.io.IOException();byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1){total+=n;if(total>32768)throw new java.io.IOException();hash.update(buffer,0,n);}}
                if(total!=bytes.length||!java.util.Arrays.equals(hash.digest(),MessageDigest.getInstance("SHA-256").digest(bytes)))throw new java.io.IOException();
                message="操作紀錄已匯出並讀回核對。這不是影片備份或整機恢復映像。";
            }catch(Exception error){message="操作紀錄匯出未完成或無法核對；App內紀錄仍保留。";}
            String finished=message;main.post(()->{if(!closed)status.setText(finished);});
        });
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event){if((event.getKeyCode()==23||event.getKeyCode()==66)&&event.getRepeatCount()>0)return true;return super.dispatchKeyEvent(event);}
    @Override protected void onDestroy(){closed=true;transactions.removeObserver(observer);main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
