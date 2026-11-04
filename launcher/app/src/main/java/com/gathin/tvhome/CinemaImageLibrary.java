package com.gathin.tvhome;

import android.app.*;
import android.graphics.Bitmap;
import android.os.*;
import android.widget.*;
import java.io.*;
import java.util.*;
/** User-driven private home-image inventory. Never enumerates or deletes boot/Root backups. */
final class CinemaImageLibrary {
 static void open(Activity a,CinemaSettings settings,CinemaImageStore store,List<AppAdapter.Entry> apps){
  open(a,settings,store,apps,0,0);
 }
 private static void open(Activity a,CinemaSettings settings,CinemaImageStore store,List<AppAdapter.Entry> apps,int offset){open(a,settings,store,apps,offset,0);}
 private static void open(Activity a,CinemaSettings settings,CinemaImageStore store,List<AppAdapter.Entry> apps,int offset,int selected){
  Handler main=new Handler(Looper.getMainLooper());
  submit(a,()->{
   File[] all=CinemaImageStore.directory(a).listFiles();List<File> files=new ArrayList<>();long total=0;
   if(all!=null)for(File f:all)if(f.isFile()&&f.getName().matches("[a-f0-9-]+\\.png")){total+=f.length();files.add(f);}
   files.sort((x,y)->Long.compare(y.lastModified(),x.lastModified()));long bytes=total;
   main.post(()->{if(a.isFinishing()||a.isDestroyed())return;Set<String> active=settings.activeImageReferences(apps);
    int start=Math.max(0,Math.min(offset,((Math.max(1,files.size())-1)/200)*200));String[] labels=new String[Math.min(200,files.size()-start)];for(int i=0;i<labels.length;i++){File f=files.get(start+i);labels[i]=(active.contains("private:"+f.getName())?"使用中 · ":"已保留 · ")+new java.text.SimpleDateFormat("MM/dd HH:mm",Locale.getDefault()).format(new Date(f.lastModified()))+String.format(Locale.ROOT," · %.1f MiB",f.length()/1048576f);}
    AlertDialog.Builder builder=new AlertDialog.Builder(a).setTitle(String.format(Locale.ROOT,"自訂圖片 · %d 張 · %.1f MiB",files.size(),bytes/1048576f)).setNegativeButton("返回",null);
    if(labels.length==0)builder.setMessage("尚無自訂圖片。這裡只管理首頁圖片，不包含開機素材或系統備份。");else builder.setItems(labels,(d,index)->preview(a,settings,store,apps,files.get(start+index),start,index));
    if(start>0)builder.setNeutralButton("較新的圖片",(d,w)->open(a,settings,store,apps,Math.max(0,start-200)));
    if(start+labels.length<files.size())builder.setPositiveButton("較舊的圖片",(d,w)->open(a,settings,store,apps,start+labels.length));
    AlertDialog dialog=builder.show();CinemaPanelStyle.dialog(dialog);if(labels.length>0){ListView list=dialog.getListView();list.setSelection(Math.max(0,Math.min(selected,labels.length-1)));list.requestFocus();}
   });
  });
 }
 private static void preview(Activity a,CinemaSettings settings,CinemaImageStore store,List<AppAdapter.Entry> apps,File file){preview(a,settings,store,apps,file,0,0);}
 private static void preview(Activity a,CinemaSettings settings,CinemaImageStore store,List<AppAdapter.Entry> apps,File file,int offset,int selected){
  Runnable returnToList=()->open(a,settings,store,apps,offset,selected);
  String ref="private:"+file.getName();boolean used=settings.activeImageReferences(apps).contains(ref);
  ImageView image=new ImageView(a);image.setAdjustViewBounds(true);image.setMaxHeight(360);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
  AlertDialog dialog=new AlertDialog.Builder(a).setTitle(used?"使用中的首頁圖片":"已保留的首頁圖片").setMessage(used?"目前設定正在使用，保留原圖。":"可另存這張圖片，或明確刪除。刪除只影響此首頁圖片，不處理開機或 Root 備份。")
   .setView(image).setNegativeButton("返回清單",(d,w)->returnToList.run()).setNeutralButton("另存至下載",null).setPositiveButton(used?"保留":"刪除此圖片",null).create();
  dialog.show();CinemaPanelStyle.dialog(dialog);dialog.setOnCancelListener(d->returnToList.run());dialog.getButton(-2).requestFocus();dialog.getButton(-1).setEnabled(!used);
  submit(a,()->{Bitmap decoded=null;try{decoded=CinemaImageStore.decode(file,640,360);}catch(Exception|OutOfMemoryError ignored){}Bitmap result=decoded;
   new Handler(Looper.getMainLooper()).post(()->{if(!a.isFinishing()&&!a.isDestroyed()&&dialog.isShowing())image.setImageBitmap(result);});});
  dialog.setOnDismissListener(d->image.setImageDrawable(null));
  dialog.getButton(-3).setEnabled(Build.VERSION.SDK_INT>=29);dialog.getButton(-3).setOnClickListener(v->{dialog.getButton(-3).setEnabled(false);boolean accepted=submit(a,()->{boolean ok=false;try{CinemaVerifiedExport.downloads(a,"home-image.png","image/png",out->CinemaVerifiedExport.file(file,out));ok=true;}catch(Exception ignored){}boolean success=ok;
   new Handler(Looper.getMainLooper()).post(()->{if(dialog.isShowing())dialog.getButton(-3).setEnabled(true);Toast.makeText(a,success?"已儲存並核對：下載 / CinemaTV":"儲存未完成，原圖保留",Toast.LENGTH_LONG).show();});});if(!accepted)dialog.getButton(-3).setEnabled(true);});
  dialog.getButton(-1).setOnClickListener(v->{AlertDialog confirm=new AlertDialog.Builder(a).setTitle("刪除此保留圖片？").setMessage("此動作不能復原；使用中的圖片與系統備份不會刪除。可先另存至下載資料夾。")
   .setNegativeButton("保留",null).setPositiveButton("刪除",(d,w)->{dialog.getButton(-1).setEnabled(false);boolean accepted=submit(a,()->{boolean removed=false;try{removed=store.deleteUnused(ref,()->settings.activeImageReferences(apps));}catch(IOException ignored){}boolean ok=removed;
    new Handler(Looper.getMainLooper()).post(()->{if(ok){settings.forgetPreservedImage(ref);CinemaWallpaperManager.trimThumbnails();}Toast.makeText(a,ok?"已刪除這張保留圖片":"圖片仍在使用、準備中或無法刪除，已保留",Toast.LENGTH_LONG).show();dialog.dismiss();returnToList.run();});});if(!accepted)dialog.getButton(-1).setEnabled(true);}).create();confirm.show();CinemaPanelStyle.dialog(confirm);confirm.getButton(-2).requestFocus();});
 }
 private static boolean submit(Activity a,Runnable action){try{CinemaImageDecoder.worker().execute(action);return true;}catch(java.util.concurrent.RejectedExecutionException busy){Toast.makeText(a,"圖片處理忙碌中，請稍後再試；原圖保留",Toast.LENGTH_LONG).show();return false;}}
 private CinemaImageLibrary(){}
}
