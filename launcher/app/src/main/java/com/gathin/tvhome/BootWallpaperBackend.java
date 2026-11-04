package com.gathin.tvhome;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.os.ParcelFileDescriptor;
import java.io.*;
import java.security.MessageDigest;

/** Android 10-12 public API adapter; default-image copies are explicitly labelled, never original files. */
final class BootWallpaperBackend implements BootWallpaperTransactions.Backend {
    private final Context context;private final WallpaperManager manager;
    BootWallpaperBackend(Context context){this.context=context.getApplicationContext();manager=WallpaperManager.getInstance(this.context);}
    private void profile()throws IOException{
        if(!BootDeviceSupport.wallpaper(android.os.Build.VERSION.SDK_INT,android.os.Process.myUid(),context.getPackageName()))throw new IOException("Unreviewed device");
        if(!manager.isWallpaperSupported()||!manager.isSetWallpaperAllowed()||manager.getWallpaperInfo()!=null
            ||!supportedDimensions(manager.getDesiredMinimumWidth(),manager.getDesiredMinimumHeight()))throw new IOException("不支援目前的動態背景或系統背景尺寸；未更換圖片。");
        if(context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE)!=android.content.pm.PackageManager.PERMISSION_GRANTED)throw new SecurityException("備份目前背景需要 Android 10–12 的讀取權限。");
    }
    static boolean supportedDimensions(int width,int height){return width==1920&&height>=1080&&height<=1920;}
    static void validateImage(File file)throws IOException{
        validateSnapshotImage(file);
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);
        if(o.outWidth!=1920||o.outHeight!=1080)throw new IOException("Wallpaper candidate must be 1920x1080");
    }
    static void validateSnapshotImage(File file)throws IOException{
        if(!file.isFile()||file.length()<=0||file.length()>BootAssetStore.MAX_BYTES)throw new IOException("Wallpaper backup size is invalid");
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);
        BootAssetStore.bounds(o.outWidth,o.outHeight);
        if(o.outWidth>4096||o.outHeight>4096||!("image/png".equals(o.outMimeType)||"image/jpeg".equals(o.outMimeType)))throw new IOException("Wallpaper must be bounded PNG/JPEG");
        o.inJustDecodeBounds=false;o.inScaled=false;o.inPreferredConfig=android.graphics.Bitmap.Config.ARGB_8888;android.graphics.Bitmap decoded=BitmapFactory.decodeFile(file.getPath(),o);
        if(decoded==null)throw new IOException("Wallpaper full decode failed");try{if(decoded.getWidth()!=o.outWidth||decoded.getHeight()!=o.outHeight)throw new IOException("Wallpaper decode dimensions changed");}finally{decoded.recycle();}
    }
    // API29-32 getWallpaperFile uses READ_EXTERNAL_STORAGE. API33+ stays disabled.
    @android.annotation.SuppressLint("MissingPermission")
    @Override public BootWallpaperTransactions.Snapshot capture(File output)throws Exception{
        profile();if(output.exists())throw new IOException("Snapshot already exists");
        int id=manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM),lock=manager.getWallpaperId(WallpaperManager.FLAG_LOCK);
        if(id<=0||(lock<=0&&lock!=-1))throw new IOException("Invalid current wallpaper IDs");
        ParcelFileDescriptor system=manager.getWallpaperFile(WallpaperManager.FLAG_SYSTEM);boolean defaultImage=system==null;
        if(defaultImage){
            android.graphics.drawable.Drawable drawable=manager.getBuiltInDrawable(1920,1080,true,.5f,.5f,WallpaperManager.FLAG_SYSTEM);
            if(drawable==null)throw new IOException("系統沒有可讀取的桌布，也無法取得原廠預設圖副本。");
            android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(1920,1080,android.graphics.Bitmap.Config.ARGB_8888);
            try{android.graphics.Canvas canvas=new android.graphics.Canvas(image);canvas.drawColor(android.graphics.Color.BLACK);drawable.setBounds(0,0,1920,1080);drawable.draw(canvas);image.setHasAlpha(false);BootAssetStore.savePng(image,output);}finally{image.recycle();}
        }else try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(system)){BootAssetStore.copy(in,output,()->false);}
        validateSnapshotImage(output);String sha=BootAssetStore.hash(output);
        BitmapFactory.Options dimensions=new BitmapFactory.Options();dimensions.inJustDecodeBounds=true;BitmapFactory.decodeFile(output.getPath(),dimensions);
        if(dimensions.outHeight>manager.getDesiredMinimumHeight())throw new IOException("原背景無法不經縮放還原；沒有更換圖片。");
        // No separate lock wallpaper means its effective image is the system image.
        // FLAG_SYSTEM will migrate that exact image to lock-only, with the old system ID.
        if(lock==-1){
            ParcelFileDescriptor unexpected=manager.getWallpaperFile(WallpaperManager.FLAG_LOCK);
            if(unexpected!=null){unexpected.close();throw new IOException("Unexpected shared lock wallpaper");}
            if(output.getName().equals("before.image")){
                File backup=new File(output.getParentFile(),"before-lock.image");if(!backup.createNewFile())throw new IOException("Lock backup already exists");
                try(InputStream in=new FileInputStream(output)){BootAssetStore.copy(in,backup,()->false);}
            }
            if(id!=manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)||lock!=manager.getWallpaperId(WallpaperManager.FLAG_LOCK))throw new IOException("Wallpaper changed during backup");
            return new BootWallpaperTransactions.Snapshot(id,lock,sha,sha,defaultImage);
        }
        ParcelFileDescriptor lockFile=manager.getWallpaperFile(WallpaperManager.FLAG_LOCK);if(lockFile==null)throw new IOException("No readable lock wallpaper");
        MessageDigest hash=MessageDigest.getInstance("SHA-256");long total=0;FileOutputStream retained=null;
        try(InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(lockFile)){
            try{
                if(output.getName().equals("before.image")){File backup=new File(output.getParentFile(),"before-lock.image");if(!backup.createNewFile())throw new IOException("Lock backup already exists");retained=new FileOutputStream(backup);}
                byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){total+=n;if(total>BootAssetStore.MAX_BYTES)throw new IOException("Lock image too large");hash.update(buffer,0,n);if(retained!=null)retained.write(buffer,0,n);}
                if(retained!=null)retained.getFD().sync();
            }finally{if(retained!=null)retained.close();}
        }
        if(total==0||id!=manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)||lock!=manager.getWallpaperId(WallpaperManager.FLAG_LOCK))throw new IOException("Wallpaper changed during backup");
        StringBuilder digest=new StringBuilder();for(byte b:hash.digest())digest.append(String.format(java.util.Locale.ROOT,"%02x",b&255));
        return new BootWallpaperTransactions.Snapshot(id,lock,sha,digest.toString(),defaultImage);
    }
    @Override public int set(File source,BootWallpaperTransactions.Snapshot expected)throws Exception{
        return set(source,expected,false);
    }
    @Override public int set(File source,BootWallpaperTransactions.Snapshot expected,boolean sharedDefault)throws Exception{
        profile();validateSnapshotImage(source);
        BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(source.getPath(),o);
        if(o.outHeight>manager.getDesiredMinimumHeight())throw new IOException("Wallpaper would require system scaling");
        if(manager.getWallpaperId(WallpaperManager.FLAG_SYSTEM)!=expected.systemId||manager.getWallpaperId(WallpaperManager.FLAG_LOCK)!=expected.lockId)throw new IOException("Wallpaper changed before system request");
        if(sharedDefault&&expected.lockId!=-1)throw new IOException("Shared lock configuration changed");
        int which=sharedDefault?WallpaperManager.FLAG_SYSTEM|WallpaperManager.FLAG_LOCK:WallpaperManager.FLAG_SYSTEM;
        try(InputStream in=new FileInputStream(source)){return manager.setStream(in,new Rect(0,0,o.outWidth,o.outHeight),true,which);}
    }
}
