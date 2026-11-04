package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.*;

/** Four shared 320px seasonal images, decoded once on a worker; no per-card image IO. */
final class CinemaSeasons implements AutoCloseable {
    static final String[] NAMES={"春","夏","秋","冬"};
    private static final int[] IDS={R.drawable.season_spring,R.drawable.season_summer,R.drawable.season_autumn,R.drawable.season_winter};
    private final Context context;private final Runnable loaded;
    private final Handler main=new Handler(Looper.getMainLooper());
    private final ExecutorService worker=CinemaImageDecoder.worker();
    private static final class Delivery{final Bitmap[] images;final int generation;Delivery(Bitmap[] images,int generation){this.images=images;this.generation=generation;}}
    private final java.util.concurrent.atomic.AtomicReference<Delivery> ready=new java.util.concurrent.atomic.AtomicReference<>();
    private Future<?> task;
    private String[] sources={"season:0","season:1","season:2","season:3"};
    void sources(String[] next){if(java.util.Arrays.equals(sources,next))return;sources=next.clone();generation++;loading=false;images=null;CinemaImageDecoder.cancel(task);if(active)resume();}
    private Bitmap[] images;private volatile boolean active,closed;private boolean loading;private volatile int generation;
    CinemaSeasons(Context c,Runnable loaded){context=c.getApplicationContext();this.loaded=loaded;}
    void resume(){active=true;if(closed||loading||images!=null)return;loading=true;final int token=++generation;final String[] refs=sources.clone();
        try{task=worker.submit(()->{Bitmap[] out=new Bitmap[4];try{BitmapFactory.Options o=new BitmapFactory.Options();o.inSampleSize=2;o.inScaled=false;for(int i=0;i<4;i++){if(closed||!active||token!=generation)return;try{if(refs[i].startsWith("private:"))out[i]=CinemaImageStore.decode(CinemaImageStore.file(context,refs[i]),640,360);}catch(Exception|OutOfMemoryError ignored){} if(out[i]==null){int slot=refs[i].matches("season:[0-3]")?Integer.parseInt(refs[i].substring(7)):i;out[i]=BitmapFactory.decodeResource(context.getResources(),IDS[slot],o);}}}catch(RuntimeException|OutOfMemoryError ignored){/* Native gradient remains usable. */}
            if(closed||token!=generation||!active)return;ready.set(new Delivery(out,token));
            main.post(()->{Delivery result=ready.getAndSet(null);if(result==null||closed||result.generation!=generation||!active)return;loading=false;images=result.images;loaded.run();});});}catch(RejectedExecutionException saturated){loading=false;}
    }
    Drawable image(int n){return images==null||images[Math.floorMod(n,4)]==null?null:new BitmapDrawable(context.getResources(),images[Math.floorMod(n,4)]);}
    int count(){return images==null?0:images.length;}
    void pause(){active=false;generation++;loading=false;ready.set(null);CinemaImageDecoder.cancel(task);}
    void trim(){if(!active)images=null;}
    @Override public void close(){closed=true;pause();images=null;main.removeCallbacksAndMessages(null);}
}
