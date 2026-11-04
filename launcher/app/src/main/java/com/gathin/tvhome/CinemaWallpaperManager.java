package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Latest-request-wins loader: one displayed bitmap, one pending delivery, one decode worker. */
final class CinemaWallpaperManager implements AutoCloseable {
    interface Callback { void loaded(boolean fallback); }
    private final Context context;
    private final ImageView target;
    private final ExecutorService decoder=CinemaImageDecoder.worker();
    private final boolean floorEnabled;
    private static final android.util.LruCache<String,Bitmap> THUMBNAILS=new android.util.LruCache<String,Bitmap>((int)CinemaMemoryPolicy.THUMBNAIL_CACHE_BYTES){
        @Override protected int sizeOf(String key,Bitmap value){return value.getAllocationByteCount();}
    };
    private final Handler main=new Handler(Looper.getMainLooper());
    private final AtomicInteger generation=new AtomicInteger();
    private Future<?> pending;
    private static final class Delivery {
        final Bitmap image,stage; final boolean fallback; final String key,fallbackSource; final int generation;
        Delivery(Bitmap image,Bitmap stage,boolean fallback,String key,String fallbackSource,int generation){this.image=image;this.stage=stage;this.fallback=fallback;this.key=key;this.fallbackSource=fallbackSource;this.generation=generation;}
    }
    private final AtomicReference<Delivery> ready=new AtomicReference<>();
    private Bitmap current,stage;
    private String requestedKey="",requestedFallback="",currentFallback="";
    private String currentKey="";
    private boolean active=true,closed;
    private final Callback callback;
    CinemaWallpaperManager(Context context,ImageView target,Callback callback){
        this(context,target,callback,true);
    }
    CinemaWallpaperManager(Context context,ImageView target,Callback callback,boolean floorEnabled){
        this.context=context.getApplicationContext();this.target=target;this.callback=callback;this.floorEnabled=floorEnabled;
        target.setScaleType(ImageView.ScaleType.CENTER_CROP);
        target.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{0xFF162333,0xFF090E18}));
    }
    void load(String source,int width,int height){
        load(source,width,height,"lake");
    }
    void load(String source,int width,int height,String fallbackSource){
        if(closed||!active||width<=0||height<=0)return;
        int[] bounded=floorEnabled?CinemaMemoryPolicy.background(context,width,height):new int[]{Math.min(width,960),Math.min(height,540)};
        final int decodeWidth=bounded[0],decodeHeight=bounded[1];
        String key=source+"@"+decodeWidth+"x"+decodeHeight;
        if(current!=null&&key.equals(currentKey)&&fallbackSource.equals(currentFallback)){
            if(!requestedKey.isEmpty()&&(!key.equals(requestedKey)||!fallbackSource.equals(requestedFallback))){generation.incrementAndGet();clearReady();CinemaImageDecoder.cancel(pending);requestedKey="";}
            return;
        }
        if(key.equals(requestedKey)&&fallbackSource.equals(requestedFallback))return;
        requestedKey=key;requestedFallback=fallbackSource;
        int request=generation.incrementAndGet();clearReady();CinemaImageDecoder.cancel(pending);
        try{pending=decoder.submit(()->{
            if(request!=generation.get())return;
            Bitmap result=null;boolean fallback=false;
            try{result=floorEnabled?decode(source,decodeWidth,decodeHeight):THUMBNAILS.get(key);if(result==null)result=decode(source,decodeWidth,decodeHeight);}catch(Exception|OutOfMemoryError ignored){fallback=true;}
            if(result==null){fallback=true;try{result=decode(fallbackSource,decodeWidth,decodeHeight);}catch(Exception|OutOfMemoryError ignored){/* Drawable stays usable. */}}
            if(request!=generation.get()){return;}
            Bitmap floor=null;try{floor=floorEnabled?makeStage(result):null;}catch(RuntimeException|OutOfMemoryError ignored){/* Wallpaper can still be shown without the floor. */}
            if(request!=generation.get()){if(floor!=null)floor.recycle();return;}
            if(!floorEnabled&&result!=null&&!fallback)THUMBNAILS.put(key,result);
            Delivery old=ready.getAndSet(new Delivery(result,floor,fallback,key,fallbackSource,request));
            if(old!=null){if(old.stage!=null)old.stage.recycle();}
            // Runnables capture no bitmap: a busy UI queue cannot accumulate decoded images.
            main.post(this::deliverLatest);
        });}catch(java.util.concurrent.RejectedExecutionException saturated){requestedKey="";callback.loaded(true);}
    }
    private void deliverLatest(){
        Delivery delivery=ready.getAndSet(null);if(delivery==null)return;
        if(closed||!active||delivery.generation!=generation.get()){if(delivery.stage!=null)delivery.stage.recycle();return;}
        // Displayed bitmaps are GC-managed: never recycle one referenced by a display list.
        if(delivery.image==null){requestedKey="";callback.loaded(true);return;}
        target.setImageBitmap(delivery.image);current=delivery.image;stage=delivery.stage;currentKey=delivery.key;currentFallback=delivery.fallbackSource;requestedKey="";callback.loaded(delivery.fallback);
    }
    private void clearReady(){Delivery old=ready.getAndSet(null);if(old!=null){if(old.stage!=null)old.stage.recycle();}}
    private InputStream open(String source)throws Exception{
        int id;
        switch(source){
            case "oled_sunrise":id=R.drawable.theme_oled_sunrise;break;
            case "oled_sunset":id=R.drawable.theme_oled_sunset;break;
            case "autumn_sunrise":id=R.drawable.theme_autumn_sunrise;break;
            case "autumn_sunset":id=R.drawable.theme_autumn_sunset;break;
            case "aurora_sunrise":id=R.drawable.theme_aurora_sunrise;break;
            case "aurora_sunset":id=R.drawable.theme_aurora_sunset;break;
            case "season:0":id=R.drawable.season_spring;break;
            case "season:1":id=R.drawable.season_summer;break;
            case "season:2":id=R.drawable.season_autumn;break;
            case "season:3":id=R.drawable.season_winter;break;
            case "sunrise":id=R.drawable.cinema_sunrise;break;
            case "oled":id=R.raw.cinema_oled;break;
            case "autumn":id=R.raw.cinema_autumn;break;
            default:id=R.raw.cinema_lake;
        }
        if(source.startsWith("private:"))return new java.io.FileInputStream(CinemaImageStore.file(context,source));
        if(source.startsWith("content:"))return context.getContentResolver().openInputStream(Uri.parse(source));
        return context.getResources().openRawResource(id);
    }
    private Bitmap decode(String source,int width,int height)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;
        try(InputStream in=open(source)){if(in==null)throw new java.io.IOException("Missing image");BitmapFactory.decodeStream(in,null,options);}
        if(options.outWidth<=0||options.outHeight<=0)throw new java.io.IOException("Invalid image bounds");
        options.inSampleSize=CinemaPolicy.sampleSize(options.outWidth,options.outHeight,width,height);
        options.inJustDecodeBounds=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;options.inScaled=false;
        CinemaMemoryPolicy.requireAllocationBudget(((long)options.outWidth+options.inSampleSize-1)/options.inSampleSize*((options.outHeight+(long)options.inSampleSize-1)/options.inSampleSize)*8);
        Bitmap bitmap;
        try(InputStream in=open(source)){bitmap=BitmapFactory.decodeStream(in,null,options);}
        if(bitmap==null)return null;
        // Downscale on this decoder worker, preserve aspect; never allocate an upscaled bitmap.
        float ratio=Math.max(width*CinemaPolicy.OVERSCAN/bitmap.getWidth(),height*CinemaPolicy.OVERSCAN/bitmap.getHeight());
        if(ratio<.97f){
            Bitmap scaled=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*ratio)),Math.max(1,Math.round(bitmap.getHeight()*ratio)),true);
            if(scaled!=bitmap)bitmap.recycle();bitmap=scaled;
        }
        return bitmap;
    }
    static long thumbnailBytes(){return THUMBNAILS.size();}
    static void trimThumbnails(){THUMBNAILS.evictAll();}
    long bitmapBytes(){return (current==null?0:current.getAllocationByteCount())+(stage==null?0:stage.getAllocationByteCount());}
    Bitmap stage(){return stage;}
    private static Bitmap makeStage(Bitmap source){
        if(source==null)return null;
        // 32×12: sampled on the decoder worker, then smoothly expanded into a soft reflective floor.
        Bitmap small=Bitmap.createBitmap(32,12,Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas=new android.graphics.Canvas(small);
        android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG);
        canvas.drawBitmap(source,new android.graphics.Rect(0,(int)(source.getHeight()*.57f),source.getWidth(),source.getHeight()),new android.graphics.Rect(0,0,32,12),paint);
        Bitmap out=Bitmap.createBitmap(128,48,Bitmap.Config.ARGB_8888);canvas=new android.graphics.Canvas(out);
        canvas.drawBitmap(small,null,new android.graphics.Rect(0,0,128,48),paint);small.recycle();
        paint.setShader(new android.graphics.LinearGradient(0,0,0,48,new int[]{0,0xE8000000,0xFF000000},new float[]{0,.3f,1},android.graphics.Shader.TileMode.CLAMP));
        paint.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN));canvas.drawRect(0,0,128,48,paint);
        return out;
    }
    void invalidate(){generation.incrementAndGet();clearReady();currentKey="";requestedKey="";}
    void pause(){active=false;requestedKey="";generation.incrementAndGet();clearReady();CinemaImageDecoder.cancel(pending);}
    void resume(){active=true;}
    void trim(){if(!active){target.setImageDrawable(null);current=null;stage=null;currentKey="";}}
    int retainedBitmapCount(){return current==null?0:1;}
    int pendingDeliveryCount(){return ready.get()==null?0:1;}
    String currentKey(){return currentKey;}
    @Override public void close(){
        if(closed)return;closed=true;pause();CinemaImageDecoder.cancel(pending);target.setImageDrawable(null);current=null;stage=null;currentKey="";
    }
}
