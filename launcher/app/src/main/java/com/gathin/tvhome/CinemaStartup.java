package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

/** Visible-HOME-only, once-per-boot launch. Reuses the original boot ledger and app choice. */
final class CinemaStartup implements AutoCloseable {
    static final String DELAY="cinema_start_delay_seconds";
    static final int[] DELAYS={0,5,10,30};
    interface Notice { void show(String name,int seconds);void clear();void failed(String message); }
    private final Activity activity;
    private final Notice notice;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean active,closed;
    private String pending;
    private long deadline;
    CinemaStartup(Activity activity,Notice notice){this.activity=activity;this.notice=notice;}
    static SharedPreferences choices(Context c){return c.getSharedPreferences(Config.SETTINGS_NAME,Context.MODE_PRIVATE);}
    static String target(Context c){return choices(c).getString(OpenOnStart.KEY,"");}
    static int delay(Context c){int n=choices(c).getInt(DELAY,5);for(int allowed:DELAYS)if(n==allowed)return n;return 5;}
    static int boot(Context c){try{return OpenOnStart.boot(c);}catch(SecurityException unavailable){return -1;}}
    static String label(Context c,String pkg){
        if(pkg.isEmpty())return "關閉，停留在 Cinema TV";
        try{return String.valueOf(c.getPackageManager().getApplicationLabel(c.getPackageManager().getApplicationInfo(pkg,0)));}
        catch(PackageManager.NameNotFoundException|SecurityException missing){return "App 已移除（"+pkg+"）";}
    }
    /** No launch on save, including changing the delay. Receipt is durable before changing the choice. */
    static boolean save(Context c,String pkg,int seconds){
        boolean validDelay=false;for(int allowed:DELAYS)validDelay|=seconds==allowed;
        if(!validDelay||pkg==null)return false;
        int current=boot(c);
        if(!pkg.isEmpty()&&(current<0||launchIntent(c,pkg)==null))return false;
        if(current>=0&&!c.getSharedPreferences(OpenOnStart.BOOT_PREFS,Context.MODE_PRIVATE)
                .edit().putInt(OpenOnStart.BOOT_KEY,current).commit())return false;
        return choices(c).edit().putString(OpenOnStart.KEY,pkg).putInt(DELAY,seconds).commit();
    }
    static boolean defaultHome(Context c){
        try{
            ResolveInfo r=c.getPackageManager().resolveActivity(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),PackageManager.MATCH_DEFAULT_ONLY);
            return r!=null&&r.activityInfo!=null&&c.getPackageName().equals(r.activityInfo.packageName);
        }catch(RuntimeException unavailable){return false;}
    }
    static boolean disabled(int state){return state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED||state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER||state==PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;}
    static Intent launchIntent(Context c,String pkg){
        if(pkg==null||pkg.isEmpty()||pkg.equals(c.getPackageName()))return null;
        PackageManager pm=c.getPackageManager();
        try{
            ApplicationInfo app=pm.getApplicationInfo(pkg,0);
            if(!app.enabled||disabled(pm.getApplicationEnabledSetting(pkg)))return null;
            for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
                Intent query=new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg);
                for(ResolveInfo r:pm.queryIntentActivities(query,0)){
                    ActivityInfo a=r.activityInfo;
                    if(a==null||!pkg.equals(a.packageName)||!a.enabled||!a.exported)continue;
                    ComponentName component=new ComponentName(pkg,a.name);
                    if(disabled(pm.getComponentEnabledSetting(component)))continue;
                    return query.setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                }
            }
        }catch(PackageManager.NameNotFoundException|SecurityException|IllegalArgumentException unavailable){return null;}
        return null;
    }
    void resume(Intent entry){
        if(closed)return;active=true;
        if(pending!=null)return;
        // Opening this app from another launcher is not a boot-HOME event.
        if(entry==null||!Intent.ACTION_MAIN.equals(entry.getAction())||!entry.hasCategory(Intent.CATEGORY_HOME)||!defaultHome(activity))return;
        String pkg=target(activity);int current=boot(activity);
        SharedPreferences ledger=activity.getSharedPreferences(OpenOnStart.BOOT_PREFS,Context.MODE_PRIVATE);
        if(!OpenOnStart.due(current,ledger.getInt(OpenOnStart.BOOT_KEY,-1),pkg,activity.getPackageName()))return;
        if(!ledger.edit().putInt(OpenOnStart.BOOT_KEY,current).commit())return;
        if(launchIntent(activity,pkg)==null){notice.failed("開機 App 目前無法開啟，已留在 Cinema TV");return;}
        pending=pkg;deadline=SystemClock.elapsedRealtime()+delay(activity)*1000L;
        handler.post(tick);
    }
    private final Runnable tick=new Runnable(){@Override public void run(){
        if(closed||!active||pending==null)return;
        if(!pending.equals(target(activity))||!defaultHome(activity)){cancel();return;}
        long remaining=deadline-SystemClock.elapsedRealtime();
        if(remaining>0){notice.show(label(activity,pending),(int)((remaining+999)/1000));handler.postDelayed(this,Math.min(1000,remaining));return;}
        String pkg=pending;pending=null;notice.clear();
        Intent intent=launchIntent(activity,pkg);
        if(intent==null){notice.failed("開機 App 已移除或停用，已留在 Cinema TV");return;}
        try{activity.startActivity(intent);LastOpened.record(activity,pkg);}
        catch(RuntimeException failure){notice.failed("開機 App 無法啟動，已留在 Cinema TV");}
    }};
    void cancel(){handler.removeCallbacks(tick);pending=null;notice.clear();}
    void pause(){active=false;cancel();}
    @Override public void close(){closed=true;pause();}
}
