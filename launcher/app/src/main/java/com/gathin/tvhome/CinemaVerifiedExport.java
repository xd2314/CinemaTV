package com.gathin.tvhome;

import android.content.*;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.security.*;
import java.util.UUID;
/** Ordinary-UID streamed export. A Downloads entry stays pending until content and size verify. */
final class CinemaVerifiedExport {
 static final long MAX_BYTES=48L*1024*1024;
 static final class Proof{final long bytes;final String sha;Proof(long bytes,String sha){this.bytes=bytes;this.sha=sha;}}
 interface Writer{Proof write(OutputStream output)throws Exception;}
 static Proof file(File source,OutputStream output)throws Exception{
  MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;byte[] block=new byte[65536];
  try(InputStream in=new FileInputStream(source)){int n;while((n=in.read(block))!=-1){size+=n;if(size>MAX_BYTES)throw new IOException("Export too large");digest.update(block,0,n);output.write(block,0,n);}}
  output.flush();return new Proof(size,BootWallpaperBackup.hex(digest.digest()));
 }
 static void verify(InputStream input,Proof expected)throws Exception{
  if(input==null||expected.bytes<=0||expected.bytes>MAX_BYTES||!BootVideoRoot.sha(expected.sha))throw new IOException("Invalid export proof");
  MessageDigest digest=MessageDigest.getInstance("SHA-256");long size=0;byte[] block=new byte[65536];int n;
  try(InputStream in=input){while((n=in.read(block))!=-1){size+=n;if(size>expected.bytes)throw new IOException("Export size changed");digest.update(block,0,n);}}
  if(size!=expected.bytes||!BootWallpaperBackup.hex(digest.digest()).equals(expected.sha))throw new IOException("Export readback mismatch");
 }
 static Proof document(Context c,Uri uri,Writer writer)throws Exception{
  Proof result;try(OutputStream output=c.getContentResolver().openOutputStream(uri,"wt")){if(output==null)throw new IOException("No writable destination");result=writer.write(output);}
  verify(c.getContentResolver().openInputStream(uri),result);return result;
 }
 static Uri downloads(Context c,String name,String mime,Writer writer)throws Exception{
  if(Build.VERSION.SDK_INT<29)throw new IOException("請使用系統文件選擇器儲存");
  if(name==null||!name.matches("[a-zA-Z0-9._-]+"))throw new IOException("Invalid export filename");
  ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,UUID.randomUUID()+"-"+name);v.put(MediaStore.MediaColumns.MIME_TYPE,mime);
  v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/CinemaTV/");v.put(MediaStore.MediaColumns.IS_PENDING,1);
  Uri uri=c.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("無法建立下載檔案");boolean success=false;
  try{Proof proof=document(c,uri,writer);v.clear();v.put(MediaStore.MediaColumns.IS_PENDING,0);
   if(c.getContentResolver().update(uri,v,null,null)!=1)throw new IOException("無法完成儲存");verify(c.getContentResolver().openInputStream(uri),proof);success=true;return uri;
  }finally{if(!success)try{c.getContentResolver().delete(uri,null,null);}catch(RuntimeException ignored){}}
 }
 private CinemaVerifiedExport(){}
}
