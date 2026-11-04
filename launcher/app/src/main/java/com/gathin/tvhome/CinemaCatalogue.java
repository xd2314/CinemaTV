package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import java.text.Collator;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** All launchable apps, with bounded thumbnails and at most one current read/delivery per panel. */
final class CinemaCatalogue implements AutoCloseable {
    static final int ICON_SIZE=96,ICON_CACHE_BYTES=1024*1024;
    private static final class Thumbnail {final Bitmap bitmap;final String label;Thumbnail(Bitmap b,String l){bitmap=b;label=l;}}
    private static final LruCache<String,Thumbnail> ICONS=new LruCache<String,Thumbnail>(ICON_CACHE_BYTES){
        @Override protected int sizeOf(String key,Thumbnail item){return item.bitmap.getAllocationByteCount();}
        // Eviction releases our reference only: a visible tile can still use the bitmap.
    };
    static void trimIcons(){ICONS.evictAll();}
    static int iconCacheBytes(){return ICONS.size();}
    private final Context context;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ThreadPoolExecutor worker=new ThreadPoolExecutor(1,1,15,TimeUnit.SECONDS,new LinkedBlockingQueue<>(1));
    private volatile int generation;
    private volatile boolean closed;
    private Future<?> pending;
    private Delivery delivery;
    private final Runnable deliver=()->{
        Delivery ready;
        synchronized(this){ready=delivery;delivery=null;}
        if(ready!=null&&!closed&&ready.token==generation){if(ready.error)ready.failure.accept("暫時無法讀取應用程式，請返回後再開啟。");else ready.result.accept(ready.apps);}
    };
    private static final class Delivery {
        final int token;final List<AppAdapter.Entry> apps;final Consumer<List<AppAdapter.Entry>> result;final Consumer<String> failure;final boolean error;
        Delivery(int t,List<AppAdapter.Entry> a,Consumer<List<AppAdapter.Entry>> r,Consumer<String> f,boolean e){token=t;apps=a;result=r;failure=f;error=e;}
    }
    CinemaCatalogue(Context context){this.context=context.getApplicationContext();worker.allowCoreThreadTimeOut(true);}
    private boolean stale(int token){return closed||token!=generation||Thread.currentThread().isInterrupted();}
    synchronized void load(Set<String> excluded,Consumer<List<AppAdapter.Entry>> result,Consumer<String> failure){
        if(closed)return;
        final int token=++generation;
        if(pending!=null){pending.cancel(true);worker.remove((Runnable)pending);}
        delivery=null;main.removeCallbacks(deliver);
        final Set<String> hidden=new java.util.HashSet<>(excluded);
        pending=worker.submit(()->{
            try{
                PackageManager pm=context.getPackageManager();Map<String,ResolveInfo> found=new LinkedHashMap<>();
                for(String category:new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
                    if(stale(token))return;
                    for(ResolveInfo ri:pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(category),0)){
                        if(ri.activityInfo==null||ri.activityInfo.applicationInfo==null||!ri.activityInfo.enabled||!ri.activityInfo.applicationInfo.enabled)continue;
                        String pkg=ri.activityInfo.packageName;
                        if(pkg.equals(context.getPackageName())||hidden.contains(pkg))continue;
                        if(!found.containsKey(pkg))found.put(pkg,ri);
                    }
                }
                List<AppAdapter.Entry> apps=new ArrayList<>();
                for(ResolveInfo ri:found.values()){
                    if(stale(token))return;
                    try{
                        String pkg=ri.activityInfo.packageName;
                        Thumbnail thumbnail=thumbnail(pm,ri);
                        if(stale(token))return;
                        apps.add(new AppAdapter.Entry(thumbnail.label,new ComponentName(pkg,ri.activityInfo.name),null,new BitmapDrawable(context.getResources(),thumbnail.bitmap)));
                    }catch(PackageManager.NameNotFoundException|RuntimeException ignored){/* Removed or broken apps do not empty the list. */}
                }
                if(stale(token))return;
                Collator collator=Collator.getInstance(context.getResources().getConfiguration().getLocales().get(0));
                apps.sort((a,b)->{int c=collator.compare(a.label,b.label);return c!=0?c:a.pkg.compareTo(b.pkg);});
                publish(new Delivery(token,apps,result,failure,false));
            }catch(RuntimeException e){publish(new Delivery(token,null,result,failure,true));}
        });
    }
    private Thumbnail thumbnail(PackageManager pm,ResolveInfo ri)throws PackageManager.NameNotFoundException{
        PackageInfo info=pm.getPackageInfo(ri.activityInfo.packageName,0);
        long version=android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;
        android.content.res.Configuration config=context.getResources().getConfiguration();
        String key=ri.activityInfo.packageName+'\n'+ri.activityInfo.name+'\n'+version+':'+info.lastUpdateTime+':'+ri.getIconResource()+':'+context.getResources().getDisplayMetrics().densityDpi+':'+config.hashCode();
        Thumbnail cached=ICONS.get(key);if(cached!=null)return cached;
        Drawable icon=ri.loadIcon(pm);Bitmap thumb=Bitmap.createBitmap(ICON_SIZE,ICON_SIZE,Bitmap.Config.ARGB_8888);
        Rect original=icon==null?null:new Rect(icon.getBounds());
        try{
            if(icon!=null){icon.setBounds(0,0,ICON_SIZE,ICON_SIZE);icon.draw(new Canvas(thumb));}
            Thumbnail item=new Thumbnail(thumb,Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)),ri.activityInfo.packageName));ICONS.put(key,item);return item;
        }catch(RuntimeException error){thumb.recycle();throw error;}
        finally{if(icon!=null)icon.setBounds(original);}
    }
    private synchronized void publish(Delivery ready){
        if(closed||ready.token!=generation)return;
        delivery=ready;main.removeCallbacks(deliver);main.post(deliver);
    }
    @Override public synchronized void close(){
        if(closed)return;closed=true;generation++;delivery=null;main.removeCallbacks(deliver);
        if(pending!=null){pending.cancel(true);pending=null;}worker.shutdownNow();
    }
}
