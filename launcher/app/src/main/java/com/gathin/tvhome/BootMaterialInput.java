package com.gathin.tvhome;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;

/** Reads one user-selected material using the ordinary App UID, never su or a real-path query. */
final class BootMaterialInput {
    static final class Selection {
        final Uri uri;final String route;
        Selection(Uri uri,String route){this.uri=uri;this.route=route;}
    }
    static Selection selected(Intent result)throws IOException {
        if(result==null)throw new IOException("檔案管理器沒有傳回素材連結，請改用其他選檔方式。");
        Set<Uri> choices=new LinkedHashSet<>();String route="";
        try {
            if(result.getData()!=null){choices.add(result.getData().normalizeScheme());route="data";}
            ClipData clip=result.getClipData();
            if(clip!=null){
                if(clip.getItemCount()>8)throw new IOException("請一次只選擇一個素材。");
                for(int i=0;i<clip.getItemCount();i++){Uri uri=clip.getItemAt(i).getUri();if(uri!=null){choices.add(uri.normalizeScheme());if(route.isEmpty())route="clip";}}
            }
            Object stream=result.getExtras()==null?null:result.getExtras().get(Intent.EXTRA_STREAM);
            if(stream!=null){
                if(stream instanceof Uri)choices.add(((Uri)stream).normalizeScheme());
                else if(stream instanceof java.util.ArrayList){
                    java.util.ArrayList<?> items=(java.util.ArrayList<?>)stream;if(items.size()>8)throw new IOException("請一次只選擇一個素材。");
                    for(Object item:items){if(!(item instanceof Uri))throw new IOException("檔案管理器傳回的素材格式無法識別。");choices.add(((Uri)item).normalizeScheme());}
                }else throw new IOException("檔案管理器傳回的素材格式無法識別。");
                if(route.isEmpty())route="stream";
            }
        }catch(android.os.BadParcelableException|ClassCastException e){throw new IOException("無法讀取檔案管理器傳回的資料，請改用其他選檔方式。",e);}
        if(choices.size()!=1)throw new IOException(choices.isEmpty()?"檔案管理器沒有傳回素材連結，請改用其他選檔方式。":"請一次只選擇一個素材。");
        Uri uri=choices.iterator().next();
        if(!"content".equals(uri.getScheme())&&!"file".equals(uri.getScheme()))throw new IOException("只支援本機圖片／影片文件，請先將素材放入下載資料夾再選擇。");
        if("content".equals(uri.getScheme())&&(uri.getAuthority()==null||uri.getAuthority().isEmpty()))throw new IOException("素材文件連結不完整，請重新選擇。");
        return new Selection(uri,route);
    }
    static boolean needsReadPermission(Uri uri,int sdk,boolean granted){return "file".equals(uri.getScheme())&&sdk<=29&&!granted;}
    static InputStream open(Context context,Uri uri)throws IOException {
        if("content".equals(uri.getScheme())){
            InputStream stream=context.getContentResolver().openInputStream(uri);
            if(stream==null)throw new IOException("檔案管理器沒有提供可讀取的素材，請先複製到下載資料夾再選擇。");
            return stream;
        }
        File file=sharedFile(uri,Environment.getExternalStorageDirectory());
        return new FileInputStream(file);
    }
    static File sharedFile(Uri uri,File primary)throws IOException {
        if(!"file".equals(uri.getScheme())||uri.getPath()==null||(uri.getAuthority()!=null&&!uri.getAuthority().isEmpty()))throw new IOException("不是可讀取的本機素材路徑。");
        File original=new File(uri.getPath());if(!original.isAbsolute())throw new IOException("素材路徑無效。");
        File file=original.getCanonicalFile(),root=primary.getCanonicalFile();
        if(inside(file,root))return file;
        // Accept a selected removable-storage volume, without scanning directories.
        for(String prefix:new String[]{"/storage/","/mnt/media_rw/"})if(uri.getPath().startsWith(prefix)){
            String volume=uri.getPath().substring(prefix.length()).split("/",2)[0];
            if(!volume.matches("[a-zA-Z0-9_-]+")||volume.equals("emulated")||volume.equals("self"))continue;
            File external=new File(prefix+volume).getCanonicalFile();String canonical=external.getPath();
            if((canonical.startsWith("/storage/")||canonical.startsWith("/mnt/media_rw/"))&&inside(file,external))return file;
        }
        throw new IOException("請從下載資料夾或 USB 公開資料夾選取素材；不讀取系統或 App 私人路徑。");
    }
    private static boolean inside(File file,File root){
        String prefix=root.getPath()+File.separator;if(!file.getPath().startsWith(prefix))return false;
        String relative=file.getPath().substring(prefix.length()).toLowerCase(java.util.Locale.ROOT);
        return !relative.equals("android/data")&&!relative.startsWith("android/data/")&&!relative.equals("android/obb")&&!relative.startsWith("android/obb/");
    }
    static String shape(Intent result){
        if(result==null)return "data=none;clip=0;stream=false";
        try{return "data="+(result.getData()==null?"none":result.getData().getScheme())+";clip="+(result.getClipData()==null?0:result.getClipData().getItemCount())+";stream="+result.hasExtra(Intent.EXTRA_STREAM);}
        catch(RuntimeException error){return "unreadable-result";}
    }
}
