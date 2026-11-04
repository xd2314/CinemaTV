package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import java.io.IOException;

/** Separate request identities prevent a delayed document cancellation consuming a fallback result. */
final class CinemaImagePicker {
    static final int DOCUMENT=44016, CONTENT=44015;
    private final Activity activity;
    private final SharedPreferences prefs;
    static final class Result {
        final String target, error; final Uri uri;
        Result(String target, Uri uri, String error) { this.target=target; this.uri=uri; this.error=error; }
    }
    CinemaImagePicker(Activity activity, SharedPreferences prefs) { this.activity=activity; this.prefs=prefs; }
    boolean open(String target) {
        if (!validTarget(target)) return false;
        if (prefs.getInt("image_pick_request",0)!=0) return false;
        prefs.edit().putString("image_pick_target",target).apply();
        // We immediately make a private copy, so GET_CONTENT needs no persistent grant.
        // Prefer it to firmware document providers known to fail before returning a URI.
        if(launch(CONTENT) || launch(DOCUMENT))return true;
        clear();return false;
    }
    private boolean launch(int request) {
        Intent intent=new Intent(request==DOCUMENT?Intent.ACTION_OPEN_DOCUMENT:Intent.ACTION_GET_CONTENT)
            .setType("image/*").addCategory(Intent.CATEGORY_OPENABLE)
            .putExtra(Intent.EXTRA_LOCAL_ONLY,true).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        prefs.edit().putInt("image_pick_request",request).apply();
        try {
            activity.startActivityForResult(request==CONTENT?Intent.createChooser(intent,"選擇圖片"):intent,request);
            return true;
        } catch (android.content.ActivityNotFoundException|SecurityException error) {
            prefs.edit().remove("image_pick_request").apply();
            return false;
        }
    }
    // Null means an unrelated or stale result; it must not consume the active picker.
    Result receive(int request,int result,Intent data) {
        if(request!=DOCUMENT&&request!=CONTENT || request!=prefs.getInt("image_pick_request",0))return null;
        String target=prefs.getString("image_pick_target","");
        if(result!=Activity.RESULT_OK) {
            clear();return new Result(target,null,null);
        }
        clear();
        if(!validTarget(target))return new Result(target,null,"圖片選取狀態已失效；請重新選擇，原圖保留。");
        try { return new Result(target,BootMaterialInput.selected(data).uri,null); }
        catch(IOException|RuntimeException error) {return new Result(target,null,"檔案管理器沒有提供單一可讀取圖片；請改用其他選檔 App，原圖保留。");}
    }
    private void clear(){prefs.edit().remove("image_pick_target").remove("image_pick_request").apply();}
    static boolean validTarget(String target){return target!=null&&target.matches("(recent:[0-3]|wallpaper:((mountain|oled|autumn|aurora):)?(fixed|day|night))");}
}
