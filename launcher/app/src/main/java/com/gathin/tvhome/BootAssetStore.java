package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.util.AtomicFile;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.function.BooleanSupplier;

/** Local asset preparation only. Never accesses a block device, boot.mp4 or system wallpaper. */
final class BootAssetStore {
    static final long MAX_BYTES=20L*1024*1024,MAX_PIXELS=8_000_000;
    enum Target {
        LOGO("開機圖片",1280,720), VIDEO("開機動畫",1280,720), WALLPAPER("過場圖片",1920,1080);
        final String title;final int width,height;
        Target(String title,int width,int height){this.title=title;this.width=width;this.height=height;}
    }
    static final class Asset {
        final String id,sha,sourceSha,posterSha,info;final Target target;final boolean crop;final File source,output,poster;
        Asset(String id,Target target,boolean crop,File directory,String sha,String sourceSha,String posterSha,String info){
            this.id=id;this.target=target;this.crop=crop;this.sha=sha;this.sourceSha=sourceSha;this.posterSha=posterSha;this.info=info;
            source=new File(directory,"source");output=new File(directory,target==Target.VIDEO?"prepared.mp4":"prepared.png");poster=new File(directory,"poster.png");
        }
        String filename(){return target==Target.VIDEO?"boot-animation-720p-24fps-5s.mp4":target==Target.LOGO?"boot-image-1280x720.png":"boot-background-1920x1080.png";}
    }
    private static final Object INDEX_LOCK=new Object();
    private final Context context;private final File root;private final AtomicFile record;
    BootAssetStore(Context context){this.context=context.getApplicationContext();root=new File(context.getFilesDir(),"boot_asset_preparation");record=new AtomicFile(new File(root,"last.json"));}
    static void checkCancelled(BooleanSupplier cancelled)throws IOException{if(cancelled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new IOException("已取消；原素材保留");}
    static void copy(InputStream in,File file,BooleanSupplier cancelled)throws IOException{
        if(in==null)throw new IOException("無法讀取選取的文件");
        try(FileOutputStream out=new FileOutputStream(file)){byte[] buffer=new byte[16384];long size=0;int n;
            while((n=in.read(buffer))!=-1){checkCancelled(cancelled);size+=n;if(size>MAX_BYTES)throw new IOException("來源檔案超過 20 MiB");out.write(buffer,0,n);}if(size==0)throw new IOException("檔案是空白的");out.getFD().sync();
        }
    }
    Asset importUri(Uri uri,Target target,boolean crop,BooleanSupplier cancelled)throws Exception{
        if(uri==null||!"content".equals(uri.getScheme()))throw new IOException("請使用系統文件選擇器");
        return prepare(target,crop,cancelled,()->context.getContentResolver().openInputStream(uri));
    }
    Asset reframe(Asset asset,boolean crop,BooleanSupplier cancelled)throws Exception{
        verify(asset);return prepare(asset.target,crop,cancelled,()->new FileInputStream(asset.source));
    }
    interface Source {InputStream open()throws IOException;}
    Asset prepare(Target target,boolean crop,BooleanSupplier cancelled,Source provider)throws Exception{
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("App 私有空間無法建立");
        if(root.getUsableSpace()<64L*1024*1024)throw new IOException("可用空間不足 64 MiB；原素材保留");
        String id=UUID.randomUUID().toString();File dir=new File(root,id);if(!dir.mkdir())throw new IOException("無法建立素材暫存");
        File source=new File(dir,"source"),output=new File(dir,target==Target.VIDEO?"prepared.mp4":"prepared.png"),poster=new File(dir,"poster.png");boolean success=false;
        try{
            try(InputStream in=provider.open()){copy(in,source,cancelled);}checkCancelled(cancelled);
            String info;
            if(target==Target.VIDEO){info=BootVideoValidator.validate(source,poster,cancelled);try(InputStream in=new FileInputStream(source)){copy(in,output,cancelled);}}
            else{renderImage(source,output,poster,target,crop,cancelled);info=target.width+" × "+target.height+" · PNG · 不透明 RGB\n"+(crop?"填滿裁切":"等比留黑")+"；已處理圖片方向";}
            checkCancelled(cancelled);Asset asset=new Asset(id,target,crop,dir,hash(output),hash(source),hash(poster),info);success=true;return asset;
        }finally{if(!success)deleteDraft(dir);}
    }
    static void bounds(int width,int height)throws IOException{if(width<=0||height<=0||(long)width*height>MAX_PIXELS)throw new IOException("圖片須可解碼且不超過 800 萬像素");}
    static void renderImage(File input,File output,File poster,Target target,boolean crop,BooleanSupplier cancelled)throws Exception{
        BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeFile(input.getPath(),options);
        if(!"image/png".equals(options.outMimeType)&&!"image/jpeg".equals(options.outMimeType))throw new IOException("只接受實際內容為 PNG 或 JPEG 的圖片");
        bounds(options.outWidth,options.outHeight);int orientation=ExifInterface.ORIENTATION_NORMAL;
        try{orientation=new ExifInterface(input.getPath()).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL);}catch(IOException unsupported){if("image/jpeg".equals(options.outMimeType))throw new IOException("無法驗證 JPEG 方向資料");}
        options.inJustDecodeBounds=false;options.inScaled=false;options.inPreferredConfig=Bitmap.Config.ARGB_8888;
        options.inSampleSize=CinemaPolicy.sampleSize(options.outWidth,options.outHeight,target.width,target.height);
        CinemaMemoryPolicy.requireAllocationBudget(((long)options.outWidth+options.inSampleSize-1)/options.inSampleSize*((options.outHeight+(long)options.inSampleSize-1)/options.inSampleSize)*8+target.width*(long)target.height*4);
        Bitmap original=BitmapFactory.decodeFile(input.getPath(),options);if(original==null)throw new IOException("圖片解碼失敗");Bitmap oriented=null,canvasBitmap=null,preview=null;
        try{
            oriented=orient(original,orientation);checkCancelled(cancelled);
            canvasBitmap=Bitmap.createBitmap(target.width,target.height,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(canvasBitmap);canvas.drawColor(Color.BLACK);
            float scale=crop?Math.max(target.width/(float)oriented.getWidth(),target.height/(float)oriented.getHeight()):Math.min(target.width/(float)oriented.getWidth(),target.height/(float)oriented.getHeight());
            float width=oriented.getWidth()*scale,height=oriented.getHeight()*scale;
            canvas.drawBitmap(oriented,null,new RectF((target.width-width)/2,(target.height-height)/2,(target.width+width)/2,(target.height+height)/2),new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG));
            canvasBitmap.setHasAlpha(false);savePng(canvasBitmap,output);preview=Bitmap.createScaledBitmap(canvasBitmap,640,360,true);savePng(preview,poster);
        }finally{if(preview!=null)preview.recycle();if(canvasBitmap!=null)canvasBitmap.recycle();if(oriented!=null&&oriented!=original)oriented.recycle();original.recycle();}
    }
    static Bitmap orient(Bitmap bitmap,int orientation){
        Matrix m=new Matrix();switch(orientation){case 2:m.setScale(-1,1);break;case 3:m.setRotate(180);break;case 4:m.setScale(1,-1);break;case 5:m.setRotate(90);m.postScale(-1,1);break;case 6:m.setRotate(90);break;case 7:m.setRotate(-90);m.postScale(-1,1);break;case 8:m.setRotate(-90);break;default:return bitmap;}
        return Bitmap.createBitmap(bitmap,0,0,bitmap.getWidth(),bitmap.getHeight(),m,true);
    }
    static void savePng(Bitmap bitmap,File path)throws IOException{try(FileOutputStream out=new FileOutputStream(path)){if(!bitmap.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("無法儲存圖片");out.getFD().sync();}}
    static String hash(File file)throws Exception{MessageDigest md=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1)md.update(buffer,0,n);}StringBuilder text=new StringBuilder();for(byte b:md.digest())text.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return text.toString();}
    void verify(Asset asset)throws Exception{if(!asset.sha.equals(hash(asset.output))||!asset.sourceSha.equals(hash(asset.source))||!asset.posterSha.equals(hash(asset.poster)))throw new IOException("素材已變更或遺失；請重新匯入");}
    void commit(Asset asset)throws Exception{synchronized(INDEX_LOCK){
        verify(asset);JSONObject json=readIndex();
        JSONObject item=new JSONObject().put("id",asset.id).put("target",asset.target.name()).put("crop",asset.crop).put("sha256",asset.sha).put("sourceSha256",asset.sourceSha).put("posterSha256",asset.posterSha).put("info",asset.info);
        json.getJSONObject("assets").put(asset.target.name(),item);json.put("last",asset.target.name());
        FileOutputStream out=null;try{out=record.startWrite();out.write(json.toString().getBytes(StandardCharsets.UTF_8));record.finishWrite(out);}catch(Exception e){if(out!=null)record.failWrite(out);throw e;}
    }}
    private JSONObject readIndex()throws Exception{
        if(!record.getBaseFile().exists())return new JSONObject().put("schema",2).put("assets",new JSONObject());
        JSONObject j=new JSONObject(new String(record.readFully(),StandardCharsets.UTF_8));
        if(j.getInt("schema")==1){String target=Target.valueOf(j.getString("target")).name();return new JSONObject().put("schema",2).put("last",target).put("assets",new JSONObject().put(target,j));}
        if(j.getInt("schema")!=2||!j.has("assets"))throw new IOException("素材紀錄無效");
        return j;
    }
    Asset load()throws Exception{synchronized(INDEX_LOCK){JSONObject index=readIndex();return index.has("last")?decode(index.getJSONObject("assets").getJSONObject(index.getString("last")),Target.valueOf(index.getString("last"))):null;}}
    Asset load(Target target)throws Exception{synchronized(INDEX_LOCK){JSONObject items=readIndex().getJSONObject("assets");return items.has(target.name())?decode(items.getJSONObject(target.name()),target):null;}}
    private Asset decode(JSONObject j,Target expected)throws Exception{
        String id=j.getString("id");
        if(!id.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")||!j.getString("target").equals(expected.name()))throw new IOException("素材紀錄無效");
        Asset asset=new Asset(id,expected,j.getBoolean("crop"),new File(root,id),j.getString("sha256"),j.getString("sourceSha256"),j.getString("posterSha256"),j.getString("info"));verify(asset);return asset;
    }
    static void deleteDraft(File dir){File[] files=dir.listFiles();if(files!=null)for(File file:files)file.delete();dir.delete();}
}
