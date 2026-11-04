package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/** Explicit local actions only. No boot hook, exported API, automatic retry or arbitrary command. */
final class CinemaAdbAccess {
    enum Action { ENABLE }
    interface Runner { Result run(Action action) throws Exception; }
    interface Store { String read(); boolean write(String state); }
    static final class Result {
        final int exit; final boolean timeout; final String output;
        Result(int exit, boolean timeout, String output) { this.exit=exit; this.timeout=timeout; this.output=output; }
        boolean enabled() { return !timeout && exit==0 && output.contains("CYT_START_OK\n"); }
    }
    private static CinemaAdbAccess instance;
    static synchronized CinemaAdbAccess get(Context context) {
        if(instance==null) {
            SharedPreferences prefs=context.getApplicationContext().getSharedPreferences("cinema_adb_receipt",0);
            Handler main=new Handler(Looper.getMainLooper());
            instance=new CinemaAdbAccess(new CinemaAdbProcess(),new Store(){
                public String read(){return prefs.getString("state","");}
                public boolean write(String state){return prefs.edit().putString("state",state).putLong("at",System.currentTimeMillis()).commit();}
            },Executors.newSingleThreadExecutor(),main::post);
        }
        return instance;
    }
    private final Runner runner; private final Store store; private final Executor worker,ui;
    private Runnable observer;
    private boolean busy,blocked;
    private String message="按「啟動無線連接」並確認後，才會要求 Root 並啟動網路 ADB 5555。";
    CinemaAdbAccess(Runner runner,Store store,Executor worker,Executor ui) {
        this.runner=runner;this.store=store;this.worker=worker;this.ui=ui;
        String previous=store.read();blocked=previous.equals("PENDING")||previous.equals("UNKNOWN");
        if(blocked)message="上次啟用結果不明，已鎖定再次派送。請保留現場並人工確認裝置狀態；本頁不會重送。";
    }
    synchronized boolean busy(){return busy;}
    synchronized boolean canEnable(){return !busy&&!blocked;}
    synchronized String message(){return message;}
    synchronized void observe(Runnable callback){observer=callback;}
    synchronized void removeObserver(Runnable callback){if(observer==callback)observer=null;}
    private void notifyUi(){Runnable current; synchronized(this){current=observer;}if(current!=null)current.run();}
    // Only the confirmation screen starts su; uid is verified inside that same shell.
    boolean enable(){return submit(Action.ENABLE);}
    private synchronized boolean submit(Action action) {
        if(!canEnable())return false;
        if(!store.write("PENDING")){blocked=true;message="無法保存派送收據，未執行任何 ADB 變更。";notifyUi();return false;}
        busy=true;message="正在要求 Root 並啟動 ADB（最多 45 秒）…\n若出現授權提示，請自行允許或拒絕。現有 ADB 連線可能中斷，請勿重複操作。";
        notifyUi();
        worker.execute(()->{
            Result result;
            try{result=runner.run(action);}catch(Exception ex){result=new Result(-1,false,"無法執行；可能沒有相容 su、授權被拒絕或系統限制。");}
            Result completed=result;ui.execute(()->finish(completed));
        });
        return true;
    }
    private synchronized void finish(Result r) {
        busy=false;
        boolean accepted=r.enabled();
        boolean noWrite=!r.timeout&&(r.exit==10||r.exit==11)&&!r.output.contains("CYT_PORT_OK");
        String state=accepted?"COMPLETED":noWrite?"NO_WRITE":"UNKNOWN";
        blocked=state.equals("UNKNOWN");
        if(!store.write(state))blocked=true;
        message=accepted?"設定與啟動命令已完成。\n請由電腦確認 ADB 連線；命令完成不代表連線已驗證。":noWrite?"Root 檢查未通過，沒有送出 ADB 設定或服務控制。":"結果不明或部分失敗，已停止且禁止自動重送。\n即使逾時，已送出的動作仍可能生效；請人工確認裝置狀態，不要重複操作。";
        if(blocked&&accepted)message+="\n結果收據保存失敗，已鎖定再次派送。";
        message+="\n"+r.output;
        notifyUi();
    }
}
