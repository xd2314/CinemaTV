package com.gathin.tvhome;
/** Stable style IDs. Image tokens resolve to packaged resources, not fabricated file paths. */
final class WallpaperTheme {
 enum DisplayMode { AUTO, SUNRISE, SUNSET }
 final String id,title,sunriseImage,sunsetImage;
 final DisplayMode defaultDisplayMode=DisplayMode.AUTO;final boolean isBuiltIn=true;
 private WallpaperTheme(String id,String title,String day,String night){this.id=id;this.title=title;sunriseImage=day;sunsetImage=night;}
 static final WallpaperTheme[] BUILT_INS={
  new WallpaperTheme("mountain","山湖景色","sunrise","lake"),
  new WallpaperTheme("oled","OLED 夜色","oled_sunrise","oled_sunset"),
  new WallpaperTheme("autumn","秋色","autumn_sunrise","autumn_sunset"),
  new WallpaperTheme("aurora","極光","aurora_sunrise","aurora_sunset")};
 static WallpaperTheme find(String id){for(WallpaperTheme t:BUILT_INS)if(t.id.equals(id))return t;return BUILT_INS[0];}
 static boolean exists(String id){for(WallpaperTheme t:BUILT_INS)if(t.id.equals(id))return true;return false;}
 String image(boolean day){return day?sunriseImage:sunsetImage;}
}
