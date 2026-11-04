package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.json.JSONObject;

/** Fixed-unit maintenance UI. Viewing, cancellation and exporting never invoke Root. */
public final class BootLogoInstallActivity extends Activity {
    static final int EXPORT=7301;
    private final ExecutorService io=Executors.newSingleThreadExecutor();private final Handler main=new Handler(Looper.getMainLooper());
    private BootLogoTransactions model;private BootAssetStore.Asset asset;private TextView title,details,status,read,apply,restore,backup,receipt,back;
    private boolean loaded,capable,closed,exporting;private final Runnable observer=()->refresh();
    @Override public void onCreate(Bundle saved){
        super.onCreate(saved);getWindow().getDecorView().setSystemUiVisibility(5894);model=BootLogoTransactions.get(this);
        capable=BootDeviceSupport.rootMaterials(android.os.Build.VERSION.SDK_INT,android.os.Process.myUid(),getPackageName());
        FrameLayout root=new FrameLayout(this){@Override protected void onMeasure(int w,int h){float u=Math.min(MeasureSpec.getSize(w)/1920f,MeasureSpec.getSize(h)/1080f);
            place(title,90,45,1740,70,u,42);place(details,90,135,1740,140,u,24);place(status,90,290,1740,165,u,24);
            TextView[] buttons={read,apply,restore,backup,receipt,back};for(int i=0;i<buttons.length;i++)place(buttons[i],90,480+i*80,1740,66,u,25);super.onMeasure(w,h);}};
        root.setBackgroundColor(0xff101b2b);title=label("最早開機圖片：備份、套用與還原",42);details=label("正在讀取這項素材…",24);status=label("",24);
        read=button("讀取當次備份並建立候選…","read",()->ask(0));apply=button("套用已核對的開機圖片…","apply",()->ask(1));restore=button("還原本次套用前的圖片…","restore",()->ask(2));
        backup=button("匯出完整資源區備份…","backup",()->export(true));receipt=button("匯出操作紀錄…","receipt",()->export(false));back=button("返回素材頁","back",this::finish);
        for(View v:new View[]{title,details,status,read,apply,restore,backup,receipt,back})root.addView(v);setContentView(root);model.observe(observer);refresh();back.requestFocus();
        io.execute(()->{BootAssetStore.Asset selected=null;try{selected=new BootAssetStore(this).load(BootAssetStore.Target.LOGO);}catch(Exception ignored){}
            BootAssetStore.Asset result=selected;main.post(()->{if(closed)return;asset=result;loaded=true;details.setText((result==null?"沒有準備好的開機圖片；請返回素材頁匯入。":result.info)+"\n只處理最早圖片的已解析範圍；PNG 本身不是分區映像。\n這是非原子分區寫入，必須有完整備份與可用的離線復原方式。");refresh();});});
    }
    private TextView label(String text,int size){TextView v=new TextView(this);v.setText(text);v.setTextSize(0,size);v.setTextColor(Color.WHITE);v.setIncludeFontPadding(false);return v;}
    private TextView button(String text,String tag,Runnable action){TextView v=label(text,25);v.setTag("logo:"+tag);v.setFocusable(true);v.setClickable(true);v.setGravity(Gravity.CENTER_VERTICAL);v.setPadding(24,0,24,0);v.setDefaultFocusHighlightEnabled(false);v.setBackground(new CinemaSurface(false,0xff91b7e5,false,false));v.setOnFocusChangeListener((view,focus)->v.setBackground(new CinemaSurface(focus,0xff91b7e5,false,false)));v.setOnClickListener(view->{if(v.isEnabled())action.run();});return v;}
    private void place(TextView v,int x,int y,int w,int h,float u,int size){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(w*u),Math.round(h*u));p.leftMargin=Math.round(x*u);p.topMargin=Math.round(y*u);v.setLayoutParams(p);v.setTextSize(0,size*u);}
    private void refresh(){if(status==null)return;boolean available=false;try{available=model.backupFile().isFile();}catch(Exception ignored){}
        read.setEnabled(capable&&loaded&&asset!=null&&!exporting&&model.canRead());apply.setEnabled(capable&&!exporting&&model.canApply(asset));restore.setEnabled(capable&&!exporting&&model.canRestore());
        backup.setEnabled(!exporting&&!model.busy()&&available);receipt.setEnabled(!exporting&&!model.busy());
        for(TextView v:new TextView[]{read,apply,restore,backup,receipt}){v.setFocusable(v.isEnabled());v.setAlpha(v.isEnabled()?1f:.4f);}
        status.setText(capable?model.message():"此設備或 Launcher 身分不符合限定策略；不要求 Root、不讀寫分區。");
    }
    private void ask(int op){
        if(!capable||exporting||!(op==0?read.isEnabled():op==1?apply.isEnabled():restore.isEnabled()))return;
        String title=op==0?"確認唯讀備份與候選準備":op==1?"確認更換最早開機圖片":"確認還原最早開機圖片";
        String message=op==0?"本 App 將呼叫一次既有 su 0，先驗證身分，再完整讀取 32 MiB 資源區到 App 私有空間，核對前後雜湊並解析候選。\n\n本步不寫分區、不套用、不重啟。未知或逾時即鎖定，不重送。":
            op==1?"將呼叫一次既有 su 0，重新核對當前完整資源區，保存 Root 專屬完整備份，再寫入由當次 FAT／BMP 解析的對齊範圍，核對完整資源區 SHA。\n\n這會真的更換最早圖片；分區寫入不是原子操作。拒絕、逾時或未知立即停止，不自動重送／還原／重啟。":
            "只在完整資源區仍符合本交易候選且備份完整時，呼叫一次既有 su 0，還原這次保存的圖片範圍並核對完整原 SHA。\n\n不是回原廠整機；未知或逾時即停止，不重送、不自行重啟。";
        CheckBox acknowledged=new CheckBox(this);acknowledged.setText("我已驗證離線復原方式，且完整備份在設備外可取得");acknowledged.setPadding(24,12,24,12);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(title).setMessage(message).setNegativeButton("取消",(d,w)->{})
            .setPositiveButton(op==0?"確認唯讀備份":op==1?"確認套用":"確認還原",(d,w)->{if(op==0)model.prepare(asset);else if(acknowledged.isChecked()){if(op==1)model.apply(asset,true);else model.restore();}refresh();});
        if(op>0)builder.setView(acknowledged);AlertDialog dialog=builder.create();dialog.show();
        if(op>0){dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);acknowledged.setOnCheckedChangeListener((v,checked)->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(checked));}
        View cancel=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);cancel.setFocusableInTouchMode(true);cancel.requestFocus();
    }
    private void export(boolean full){
        if(exporting||model.busy())return;
        try{
            JSONObject current=new JSONObject(model.exportReceipt());String tx=current.optString("transaction","");
            if(full)model.backupFile();
            if(!getPreferences(0).edit().putBoolean("export_full",full).putString("export_tx",tx).putString("export_json",current.toString()).commit())throw new IOException();
            Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(full?"application/octet-stream":"application/json")
                .putExtra(Intent.EXTRA_LOCAL_ONLY,true).putExtra(Intent.EXTRA_TITLE,full?"bootloader-before-"+tx+".img":"cinema-boot-logo-receipt.json");startActivityForResult(i,EXPORT);
        }catch(Exception error){status.setText("無法開啟匯出；備份與紀錄保留，沒有派送 Root。");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(request!=EXPORT)return;
        boolean full=getPreferences(0).getBoolean("export_full",false);String tx=getPreferences(0).getString("export_tx",""),json=getPreferences(0).getString("export_json","");getPreferences(0).edit().clear().apply();
        if(result!=RESULT_OK||data==null||data.getData()==null){status.setText("已取消匯出；沒有派送 Root。");return;}
        android.net.Uri uri=data.getData();if(!"content".equals(uri.getScheme())){status.setText("只接受系統文件選擇器。");return;}
        exporting=true;refresh();io.execute(()->{
            String finished;
            try{
                JSONObject snapshot=new JSONObject(json);if(!new JSONObject(model.exportReceipt()).optString("transaction","").equals(tx))throw new IOException("Transaction changed");
                File original=full?model.backupFile():null;byte[] bytes=full?null:json.getBytes(StandardCharsets.UTF_8);
                String expected=full?snapshot.getString("beforeSha"):hex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if(full&&!BootAssetStore.hash(original).equals(expected))throw new IOException("Backup changed");
                try(InputStream in=full?new FileInputStream(original):new ByteArrayInputStream(bytes);OutputStream out=getContentResolver().openOutputStream(uri,"wt")){
                    if(out==null)throw new IOException();byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1)out.write(buffer,0,n);out.flush();}
                MessageDigest hash=MessageDigest.getInstance("SHA-256");long size=0,limit=full?BootLogoLayout.PHYSICAL_BYTES:32768;
                try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException();byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){size+=n;if(size>limit)throw new IOException();hash.update(buffer,0,n);}}
                if(size!=(full?BootLogoLayout.PHYSICAL_BYTES:bytes.length)||!hex(hash.digest()).equals(expected))throw new IOException("Export readback mismatch");
                if(full){boolean saved=model.confirmBackupExport(expected,size);finished=saved?"完整資源區備份已匯出並讀回核對；請確保離線時可取得。這不含整機韌體。":"完整備份已匯出並讀回核對；交易仍維持停止狀態，沒有解鎖或派送 Root。";}
                else finished="操作紀錄已匯出並讀回核對；不是分區或影片備份。";
            }catch(Exception error){finished="匯出未完成或無法核對；原備份保留，沒有派送 Root。";}
            String message=finished;main.post(()->{exporting=false;if(!closed){refresh();status.setText(message);}});
        });
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    @Override public boolean dispatchKeyEvent(KeyEvent e){if((e.getKeyCode()==23||e.getKeyCode()==66)&&e.getRepeatCount()>0)return true;return super.dispatchKeyEvent(e);}
    @Override protected void onDestroy(){closed=true;model.removeObserver(observer);main.removeCallbacksAndMessages(null);io.shutdownNow();super.onDestroy();}
}
