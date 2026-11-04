package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/** Private launcher preferences only: no services, permission changes or network. */
final class CinemaSettings {
    static final String[] EFFECTS={"parallax","focus","ambient","reflection","recents","low_power","light_streaks"};
    final SharedPreferences prefs;
    private java.util.function.LongSupplier wallClock=System::currentTimeMillis;
    CinemaSettings(Context c){prefs=c.getSharedPreferences("cinema_lite",Context.MODE_PRIVATE);migrateThemes();migrateRecentImages();}
    // Backgrounds belong to positions, never to favourite app assignments or history entries.
    private void migrateRecentImages(){
        if(prefs.getInt("recent_images_schema",0)>=1)return;
        SharedPreferences.Editor edit=prefs.edit();
        if(prefs.contains("quick_launch_v1")){
            java.util.List<QuickLaunchItem> old=QuickLaunchItem.read(this,java.util.Collections.emptyList());
            for(int i=0;i<4;i++)if(!prefs.contains("recent_image."+i)&&old.get(i).image.startsWith("private:"))
                edit.putString("recent_image."+i,old.get(i).image);
        }
        edit.putInt("recent_images_schema",1).apply();
    }
    private static void checkRecentPosition(int position){if(position<0||position>=4)throw new IllegalArgumentException("recent position");}
    String recentImage(int position){checkRecentPosition(position);return prefs.getString("recent_image."+position,"season:"+position);}
    String[] recentImages(){return new String[]{recentImage(0),recentImage(1),recentImage(2),recentImage(3)};}
    void recentImage(int position,String reference){checkRecentPosition(position);preserveImage(recentImage(position));prefs.edit().putString("recent_image."+position,reference).apply();}
    void resetRecentImage(int position){checkRecentPosition(position);preserveImage(recentImage(position));prefs.edit().remove("recent_image."+position).apply();}
    // Product-fixed sound profile; legacy hidden mute/volume preferences must not attenuate it.
    boolean uiSound(){return true;}
    int uiSoundVolume(){return 100;}
    String mode(){return prefs.getString("mode","Cinema");}
    void mode(String value){
        if(!value.equals("Cinema")&&!value.equals("Lite")&&!value.equals("Static"))return;
        SharedPreferences.Editor e=prefs.edit().putString("mode",value);
        e.putBoolean("parallax",value.equals("Cinema")).putBoolean("focus",!value.equals("Static"))
            .putBoolean("ambient",true).putBoolean("reflection",value.equals("Cinema")).apply();
    }
    boolean on(String key){
        boolean def=key.equals("recents")||key.equals("ambient")||key.equals("light_streaks")
                || (key.equals("focus")&&!mode().equals("Static"))
                || ((key.equals("parallax")||key.equals("reflection"))&&mode().equals("Cinema"));
        return prefs.getBoolean(key,def);
    }
    void set(String key,boolean value){prefs.edit().putBoolean(key,value).apply();}
    boolean motion(){return on("focus")&&!on("low_power")&&!mode().equals("Static");}
    boolean parallax(){return on("parallax")&&!on("low_power")&&!mode().equals("Static");}
    boolean ambient(){return on("ambient")&&!on("low_power");}
    boolean reflection(){return on("reflection")&&!on("low_power");}
    private void migrateThemes(){
        if(prefs.getInt("theme_schema",0)>=1)return;
        String fixed=prefs.getString("wallpaper","lake");boolean auto=prefs.getBoolean("auto_wallpaper",fixed.equals("lake"));
        String style=fixed.equals("oled")||fixed.equals("autumn")?fixed:"mountain";
        SharedPreferences.Editor e=prefs.edit().putString("theme_id",style).putString("display_mode",auto?"AUTO":fixed.equals("sunrise")?"SUNRISE":"SUNSET");
        e.putString("theme.mountain.day",prefs.getString("sunrise_image","sunrise"));e.putString("theme.mountain.night",prefs.getString("sunset_image","lake"));
        if(!auto)e.putString("theme."+style+(fixed.equals("sunrise")?".day":".night"),fixed);
        e.putInt("theme_schema",1).apply();
    }
    WallpaperTheme theme(){return WallpaperTheme.find(prefs.getString("theme_id","mountain"));}
    void theme(String id){if(!WallpaperTheme.exists(id))return;prefs.edit().putString("theme_id",id).apply();}
    WallpaperTheme.DisplayMode displayMode(){try{return WallpaperTheme.DisplayMode.valueOf(prefs.getString("display_mode","AUTO"));}catch(IllegalArgumentException e){return WallpaperTheme.DisplayMode.AUTO;}}
    void displayMode(WallpaperTheme.DisplayMode mode){prefs.edit().putString("display_mode",mode.name()).putBoolean("auto_wallpaper",mode==WallpaperTheme.DisplayMode.AUTO).apply();}
    String wallpaper(){return prefs.getString("wallpaper","lake");}
    void wallpaper(String value){
        if(value.equals("sunrise")||value.equals("lake"))theme("mountain");else if(value.equals("oled")||value.equals("autumn"))theme(value);
        boolean day=value.equals("sunrise");image(theme().id,day?"day":"night",value);
        prefs.edit().putString("wallpaper",value).apply();displayMode(day?WallpaperTheme.DisplayMode.SUNRISE:WallpaperTheme.DisplayMode.SUNSET);
    }
    boolean autoWallpaper(){return displayMode()==WallpaperTheme.DisplayMode.AUTO;}
    void autoWallpaper(boolean enabled){displayMode(enabled?WallpaperTheme.DisplayMode.AUTO:WallpaperTheme.DisplayMode.SUNSET);}
    int dayMinute(){return prefs.getInt("day_minute",360);}
    int nightMinute(){return prefs.getInt("night_minute",1080);}
    boolean schedule(boolean day,int value){int d=day?value:dayMinute(),n=day?nightMinute():value;if(!CinemaSchedule.valid(d,n))return false;prefs.edit().putInt("day_minute",d).putInt("night_minute",n).apply();return true;}
    private boolean daytime(int minute){return displayMode()==WallpaperTheme.DisplayMode.SUNRISE||autoWallpaper()&&CinemaSchedule.daylight(minute,dayMinute(),nightMinute());}
    private int minute(){java.util.Calendar c=java.util.Calendar.getInstance();c.setTimeInMillis(wallClock.getAsLong());return c.get(java.util.Calendar.HOUR_OF_DAY)*60+c.get(java.util.Calendar.MINUTE);}
    String effectiveWallpaper(int minute){return image(daytime(minute)?"day":"night");}
    String effectiveWallpaper(){return effectiveWallpaper(minute());}
    String effectiveDefaultWallpaper(){return theme().image(daytime(minute()));}
    String image(String target){return target.equals("fixed")?wallpaper():themeImage(theme().id,target);}
    String themeImage(String id,String target){return prefs.getString("theme."+id+"."+target,WallpaperTheme.find(id).image(target.equals("day")));}
    void image(String target,String reference){if(target.equals("fixed"))wallpaper(reference);else image(theme().id,target,reference);}
    void image(String id,String target,String reference){
        if(!WallpaperTheme.exists(id)||!(target.equals("day")||target.equals("night")))return;
        preserveImage(themeImage(id,target));prefs.edit().putString("theme."+id+"."+target,reference).apply();
    }
    void preserveImage(String ref){if(!ref.startsWith("private:")&&!ref.startsWith("content:"))return;java.util.Set<String> keep=new java.util.HashSet<>(prefs.getStringSet("preserved_images",java.util.Collections.emptySet()));keep.add(ref);prefs.edit().putStringSet("preserved_images",keep).apply();}
    void resetThemeImages(){String id=theme().id;preserveImage(image("day"));preserveImage(image("night"));prefs.edit().remove("theme."+id+".day").remove("theme."+id+".night").apply();}
    void resetImages(){resetThemeImages();displayMode(WallpaperTheme.DisplayMode.AUTO);}
    int glow(){int v=prefs.getInt("glow_intensity",85);return v==0||v==50||v==85||v==100?v:85;}
    void glow(int v){if(v==0||v==50||v==85||v==100)prefs.edit().putInt("glow_intensity",v).apply();}
    void resetEffects(){SharedPreferences.Editor e=prefs.edit().remove("mode").remove("glow_intensity").remove("renderer_stage");for(String key:EFFECTS)e.remove(key);e.apply();}
    java.util.Set<String> imageReferences(java.util.List<AppAdapter.Entry> apps){
        java.util.Set<String> refs=new java.util.HashSet<>(prefs.getStringSet("preserved_images",java.util.Collections.emptySet()));
        for(Object value:prefs.getAll().values())if(value instanceof String){String s=(String)value;if(s.startsWith("private:")||s.startsWith("content:"))refs.add(s);}
        for(WallpaperTheme t:WallpaperTheme.BUILT_INS){refs.add(themeImage(t.id,"day"));refs.add(themeImage(t.id,"night"));}
        for(QuickLaunchItem q:QuickLaunchItem.read(this,apps))refs.add(q.image);return refs;
    }
    java.util.Set<String> activeImageReferences(java.util.List<AppAdapter.Entry> apps){
        java.util.Set<String> refs=new java.util.HashSet<>();
        for(Object value:prefs.getAll().values())if(value instanceof String){String valueString=(String)value;if(valueString.startsWith("private:")||valueString.startsWith("content:"))refs.add(valueString);}
        for(WallpaperTheme t:WallpaperTheme.BUILT_INS){refs.add(themeImage(t.id,"day"));refs.add(themeImage(t.id,"night"));}
        for(QuickLaunchItem q:QuickLaunchItem.read(this,apps))refs.add(q.image);return refs;
    }
    void forgetPreservedImage(String ref){java.util.Set<String> keep=new java.util.HashSet<>(prefs.getStringSet("preserved_images",java.util.Collections.emptySet()));keep.remove(ref);prefs.edit().putStringSet("preserved_images",keep).apply();}
    int color(String pkg){return prefs.getInt("accent."+pkg,0);}
    void color(String pkg,int color){prefs.edit().putInt("accent."+pkg,color).apply();}
}
