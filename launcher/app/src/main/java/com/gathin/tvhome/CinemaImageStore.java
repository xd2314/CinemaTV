package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.net.Uri;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/** Validated private copies: one worker; no broad storage permission or retained provider grants. */
final class CinemaImageStore implements AutoCloseable {
 static final long MAX_INPUT_BYTES=32L*1024*1024;
 static final class Imported {final String reference;final Bitmap preview;Imported(String r,Bitmap b){reference=r;preview=b;}}
 private final Context context;private final ExecutorService worker;private final Handler main=new Handler(Looper.getMainLooper());private volatile boolean closed;
 private final Set<String> staged=ConcurrentHashMap.newKeySet();
 private final Object lifecycle=new Object();private long generation;
 CinemaImageStore(Context c){this(c,Executors.newSingleThreadExecutor());}
 CinemaImageStore(Context c,ExecutorService executor){context=c.getApplicationContext();worker=executor;}
 static File directory(Context c){return new File(c.getFilesDir(),"cinema_images");}
 static File file(Context c,String reference)throws IOException{
  if(reference==null||!reference.matches("private:[a-f0-9-]+\\.png"))throw new IOException("Invalid image reference");return new File(directory(c),reference.substring(8));
 }
 static Bitmap decode(File f,int width,int height)throws IOException{
  BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(f.getPath(),o);
  if(o.outWidth<=0||o.outHeight<=0)throw new IOException("Not a decodable image");o.inSampleSize=CinemaPolicy.sampleSize(o.outWidth,o.outHeight,width,height);o.inJustDecodeBounds=false;o.inScaled=false;o.inPreferredConfig=Bitmap.Config.ARGB_8888;
  CinemaMemoryPolicy.requireAllocationBudget(((long)o.outWidth+o.inSampleSize-1)/o.inSampleSize*((o.outHeight+(long)o.inSampleSize-1)/o.inSampleSize)*8);
  Bitmap b=BitmapFactory.decodeFile(f.getPath(),o);if(b==null)throw new IOException("Decode failed");
  int orientation=1;try{orientation=new android.media.ExifInterface(f.getPath()).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION,1);}catch(IOException ignored){}
  Bitmap oriented=BootAssetStore.orient(b,orientation);if(oriented!=b)b.recycle();b=oriented;
  float scale=Math.min(1,Math.min(width/(float)b.getWidth(),height/(float)b.getHeight()));
  if(scale<1){Bitmap small=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*scale)),Math.max(1,Math.round(b.getHeight()*scale)),true);if(small!=b)b.recycle();b=small;}return b;
 }
 Imported importNow(Uri uri)throws IOException{
  File dir=directory(context);if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Private storage unavailable");
  String name=UUID.randomUUID().toString();File incoming=new File(dir,name+".tmp"),output=new File(dir,name+".png");Bitmap b=null;boolean success=false;
  try{
   try(InputStream in=BootMaterialInput.open(context,uri);OutputStream out=new FileOutputStream(incoming)){if(in==null)throw new IOException("No image");byte[] buffer=new byte[16384];long count=0;int n;while((n=in.read(buffer))!=-1){count+=n;if(count>MAX_INPUT_BYTES||closed)throw new IOException("Image exceeds limit or cancelled");out.write(buffer,0,n);}}
   b=decode(incoming,2048,2048);try(OutputStream out=new FileOutputStream(output)){if(!b.compress(Bitmap.CompressFormat.PNG,100,out))throw new IOException("Cannot save image");}
   Bitmap preview=Bitmap.createScaledBitmap(b,Math.max(1,Math.round(b.getWidth()*Math.min(1,640f/Math.max(b.getWidth(),b.getHeight())))),Math.max(1,Math.round(b.getHeight()*Math.min(1,640f/Math.max(b.getWidth(),b.getHeight())))),true);
   if(preview!=b)b.recycle();b=null;success=true;String reference="private:"+output.getName();synchronized(lifecycle){staged.add(reference);generation++;}return new Imported(reference,preview);
  }finally{incoming.delete();if(b!=null)b.recycle();if(!success)output.delete();}
 }
 void importImage(Uri uri,Consumer<Imported> done,Consumer<String> failed){if(closed)return;worker.execute(()->{try{Imported result=importNow(uri);main.post(()->{if(closed){discard(result.reference);return;}done.accept(result);});}catch(Exception|OutOfMemoryError error){main.post(()->{if(!closed)failed.accept("圖片無法讀取或超過 32 MB；原設定保留");});}});}
 void committed(String reference){synchronized(lifecycle){generation++;staged.remove(reference);}}
 void discard(String reference){staged.remove(reference);try{File f=file(context,reference);if(!closed)worker.execute(f::delete);else f.delete();}catch(IOException ignored){}}
 void prune(Set<String> references){
  if(closed)return;Set<String> keep=new HashSet<>(references);final long snapshot;
  synchronized(lifecycle){snapshot=generation;}
  worker.execute(()->{synchronized(lifecycle){
   // A settings commit after submission makes this reference snapshot stale. Never delete from it.
   if(snapshot!=generation)return;
   File[] files=directory(context).listFiles();if(files!=null)for(File f:files)
    if((f.getName().endsWith(".png")||f.getName().endsWith(".tmp"))&&!keep.contains("private:"+f.getName())&&!staged.contains("private:"+f.getName()))f.delete();
  }});
 }
 boolean deleteUnused(String reference,java.util.function.Supplier<Set<String>> active)throws IOException{
  synchronized(lifecycle){
   if(closed||staged.contains(reference)||active.get().contains(reference))return false;
   File f=file(context,reference);if(java.nio.file.Files.isSymbolicLink(f.toPath())||!f.getCanonicalFile().getParentFile().equals(directory(context).getCanonicalFile()))throw new IOException("Invalid private image path");
   generation++;return f.isFile()&&f.delete();
  }
 }
 @Override public void close(){closed=true;worker.execute(()->{for(String reference:staged)discard(reference);});worker.shutdown();}
}
