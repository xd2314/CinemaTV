package com.gathin.tvhome;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaPhase1HTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();t.seed(7);t.layout(1920,1080);}
 @After public void cleanup(){t.close();}
 void settle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(300));}
 @Test public void crossZoneDimsWithoutMovingSlotsAndRestoresRememberedApp(){
  t.go(4);settle();View selected=t.root.findFocus();float[] x=new float[7];for(int i=0;i<7;i++)x[i]=t.home.dock.getChildAt(i).getTranslationX();
  assertTrue(t.key(selected,20));settle();assertEquals(CinemaHome.FocusZone.RECENT_ITEMS,t.home.focusZone);assertEquals(4,t.home.selectedIndex);assertEquals(1,selected.getScaleX(),.001);
  assertEquals(.25f,(float)ReflectionHelpers.getField(t.home.focusLight,"zone"),.001);assertEquals(0,(float)ReflectionHelpers.getField(selected,"focusLevel"),.001);
  for(int i=0;i<7;i++)assertEquals(x[i],t.home.dock.getChildAt(i).getTranslationX(),.001);
  t.home.recent.getChildAt(0).requestFocus();assertTrue(t.key(t.root.findFocus(),19));settle();assertSame(selected,t.root.findFocus());assertEquals(1.35f,selected.getScaleX(),.001);assertEquals(1f,(float)ReflectionHelpers.getField(t.home.focusLight,"zone"),.001);
  t.key(selected,19);settle();assertEquals(CinemaHome.FocusZone.TOP_ACTIONS,t.home.focusZone);assertEquals(1,selected.getScaleX(),.001);t.key(t.root.findFocus(),20);settle();assertSame(selected,t.root.findFocus());
 }
 @Test public void rapidInterruptedZoneAndHorizontalEventsConverge(){
  t.go(3);settle();CinemaProbe.reset();CinemaProbe.enabled=true;
  for(int i=0;i<40;i++){assertTrue(t.key(t.root.findFocus(),20));assertTrue(t.key(t.root.findFocus(),19));assertTrue(t.key(t.root.findFocus(),i%2==0?22:21));assertTrue(t.key(t.root.findFocus(),19));assertTrue(t.key(t.root.findFocus(),20));}
  settle();assertEquals(3,t.home.selectedIndex);assertEquals(CinemaHome.FocusZone.APP_CAROUSEL,t.home.focusZone);assertSame(t.home.dock,t.root.findFocus().getParent());assertEquals(1.35f,t.root.findFocus().getScaleX(),.001);assertEquals(0,CinemaProbe.cards);assertEquals(0,CinemaProbe.animators);CinemaProbe.enabled=false;
 }
 @Test public void quickAndTopHaveOneHighContrastActiveMarker(){
  t.go(2);t.key(t.root.findFocus(),20);settle();View q=t.root.findFocus();FrameLayout plate=ReflectionHelpers.getField(q,"plate");assertTrue(plate.getForeground() instanceof CinemaQuickFocus);assertEquals(1.05f,q.getScaleX(),.001);
  t.key(q,19);t.key(t.root.findFocus(),19);settle();assertNull(plate.getForeground());assertEquals(1,q.getScaleX(),.001);assertEquals(1.08f,t.root.findFocus().getScaleX(),.001);assertEquals(3,t.home.top.getChildCount());
 }
 @Test public void wallpaperDoesNotDecodeOrQueueWhenFocusMoves()throws Exception{
  t.home.options.wallpaper("lake");t.home.refreshWallpaper();t.awaitWallpaper("lake");Object bitmap=ReflectionHelpers.getField(t.home.wallpapers,"current");String key=t.home.wallpapers.currentKey();
  t.go(3);for(int i=0;i<25;i++){t.key(t.root.findFocus(),20);t.key(t.root.findFocus(),19);t.key(t.root.findFocus(),19);t.key(t.root.findFocus(),20);}settle();assertEquals(key,t.home.wallpapers.currentKey());assertSame(bitmap,ReflectionHelpers.getField(t.home.wallpapers,"current"));assertEquals(0,t.home.wallpapers.pendingDeliveryCount());
 }
 @Test public void allThemePairsAreDistinctRealWideResources()throws Exception{
  assertEquals(4,WallpaperTheme.BUILT_INS.length);for(WallpaperTheme theme:WallpaperTheme.BUILT_INS){assertNotEquals(theme.sunriseImage,theme.sunsetImage);for(String source:new String[]{theme.sunriseImage,theme.sunsetImage}){java.io.InputStream stream=ReflectionHelpers.callInstanceMethod(t.home.wallpapers,"open",ReflectionHelpers.ClassParameter.from(String.class,source));try(java.io.InputStream in=stream){BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeStream(in,null,options);assertTrue(options.outWidth>=1600);assertEquals(16f/9,options.outWidth/(float)options.outHeight,.01);}}}
 }
 @Test public void legacyFourChoicesMigrateWithoutLosingOriginalOrCustomImages(){
  for(String old:new String[]{"sunrise","lake","oled","autumn","private:old.png"}){
   t.home.options.prefs.edit().clear().putString("wallpaper",old).putBoolean("auto_wallpaper",false).putString("sunrise_image","private:day.png").putString("sunset_image","private:night.png").commit();CinemaSettings s=new CinemaSettings(t.activity);
   assertEquals(old,s.effectiveWallpaper(600));assertTrue(s.imageReferences(java.util.Collections.emptyList()).contains("private:day.png"));assertTrue(s.imageReferences(java.util.Collections.emptyList()).contains("private:night.png"));
   assertEquals(old,new CinemaSettings(t.activity).effectiveWallpaper(1200));
  }
 }
 @Test public void eachThemeOwnsItsPairAndModeSurvivesSettingsRecreation(){
  CinemaSettings s=t.home.options;
  for(WallpaperTheme theme:WallpaperTheme.BUILT_INS){s.theme(theme.id);s.image("day","private:"+theme.id+"day.png");s.image("night","private:"+theme.id+"night.png");}
  for(WallpaperTheme theme:WallpaperTheme.BUILT_INS){s.theme(theme.id);s.displayMode(WallpaperTheme.DisplayMode.SUNRISE);assertEquals("private:"+theme.id+"day.png",new CinemaSettings(t.activity).effectiveWallpaper(1200));s.displayMode(WallpaperTheme.DisplayMode.SUNSET);assertEquals("private:"+theme.id+"night.png",new CinemaSettings(t.activity).effectiveWallpaper(400));}
  s.resetThemeImages();assertEquals("aurora_sunrise",s.image("day"));assertTrue(s.imageReferences(java.util.Collections.emptyList()).contains("private:auroraday.png"));s.theme("mountain");assertEquals("private:mountainday.png",s.image("day"));
 }
 @Test public void autoModeCrossesMidnightAndHonorsExactBoundaries(){
  CinemaSettings s=t.home.options;s.theme("aurora");s.displayMode(WallpaperTheme.DisplayMode.AUTO);s.schedule(true,1200);s.schedule(false,360);
  assertEquals("aurora_sunrise",s.effectiveWallpaper(1200));assertEquals("aurora_sunrise",s.effectiveWallpaper(0));assertEquals("aurora_sunset",s.effectiveWallpaper(360));assertEquals("aurora_sunset",s.effectiveWallpaper(1199));assertFalse(s.schedule(false,1200));
 }
 @Test public void flatControlsExistWithoutOpeningCategoryMenusAndEntryIsFirst(){
  t.home.showSettings();t.idle();assertEquals("personalization",t.home.settingsPanel.rows.getChildAt(0).getTag());t.home.settingsPanel.rows.getChildAt(0).performClick();t.idle();t.layout(1920,1080);
  for(String tag:new String[]{"theme:mountain","theme:oled","theme:autumn","theme:aurora","wallpaper:day","wallpaper:night","display:AUTO","display:SUNRISE","display:SUNSET","schedule:day","schedule:night","glow:0","glow:85","effects:reset"})assertNotNull(tag,t.root.findViewWithTag(tag));
  for(int i=0;i<4;i++)for(String action:new String[]{"app","image","name","earlier","later"})assertNull(t.root.findViewWithTag("quick:q"+i+":"+action));
 }
 @Test public void glowZeroDisablesOpticalLightAndDefaultPreservesRatios(){
  CinemaFocusLight light=t.home.focusLight;light.layout(0,0,900,600);light.select(Color.BLUE);light.position(new RectF(250,80,550,300),1,1,true);light.intensity(0);Bitmap off=Bitmap.createBitmap(900,600,Bitmap.Config.ARGB_8888);light.draw(new Canvas(off));assertEquals(0,Color.alpha(off.getPixel(400,310)));light.intensity(85);assertEquals(1f,(float)ReflectionHelpers.getField(light,"poolGain"),0);light.intensity(50);assertNotEquals((float)ReflectionHelpers.getField(light,"poolGain"),(float)ReflectionHelpers.getField(light,"contactGain"),.01);off.recycle();
  t.home.options.glow(50);assertEquals(50,new CinemaSettings(t.activity).glow());t.home.options.resetEffects();assertEquals(85,new CinemaSettings(t.activity).glow());
 }
 @Test public void changingThemeFallbackForMissingSharedImageReloadsCorrectBuiltin()throws Exception{
  CinemaWallpaperManager loader=t.home.wallpapers;loader.load("private:missing.png",640,360,"aurora_sunrise");
  for(int i=0;i<150&&!"aurora_sunrise".equals(ReflectionHelpers.getField(loader,"currentFallback"));i++){Thread.sleep(15);t.idle();}
  assertEquals("aurora_sunrise",ReflectionHelpers.getField(loader,"currentFallback"));Object first=ReflectionHelpers.getField(loader,"current");
  loader.load("private:missing.png",640,360,"oled_sunset");for(int i=0;i<150&&!"oled_sunset".equals(ReflectionHelpers.getField(loader,"currentFallback"));i++){Thread.sleep(15);t.idle();}
  assertEquals("oled_sunset",ReflectionHelpers.getField(loader,"currentFallback"));assertNotSame(first,ReflectionHelpers.getField(loader,"current"));
  loader.load("private:missing.png",640,360,"aurora_sunrise");loader.load("private:missing.png",640,360,"oled_sunset");Thread.sleep(100);t.idle();assertEquals("oled_sunset",ReflectionHelpers.getField(loader,"currentFallback"));
 }
 @Test public void pendingImportCommitsToCapturedThemeNotNewSelection()throws Exception{
  CinemaPersonalizationTest helper=new CinemaPersonalizationTest();helper.t=t;t.home.showPersonalization();t.home.chooseImage("wallpaper:aurora:day");t.home.options.theme("autumn");
  t.home.activityResult(CinemaHome.PICK_IMAGE,android.app.Activity.RESULT_OK,new android.content.Intent().setData(helper.image("captured",Bitmap.CompressFormat.PNG,200,100)));
  helper.idleUntil(()->t.root.findViewWithTag("image:confirm")!=null);t.root.findViewWithTag("image:confirm").performClick();
  assertTrue(t.home.options.themeImage("aurora","day").startsWith("private:"));assertEquals("autumn_sunrise",t.home.options.image("day"));
 }
 @Test public void quickCenterKeyLaunchesTheActuallyFocusedItem(){
  ReflectionHelpers.setField(t.adapter,"leanbackPackages",new java.util.HashSet<String>(java.util.Arrays.asList(CinemaViewTest.PKGS)));
  t.go(2);t.home.recent.getChildAt(0).requestFocus();t.key(t.root.findFocus(),22);View focused=t.root.findFocus();assertSame(t.home.recent.getChildAt(1),focused);
  focused.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));focused.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
  android.content.Intent started=Shadows.shadowOf(t.activity).getNextStartedActivity();assertNotNull(started);assertEquals(CinemaViewTest.PKGS[1],started.getComponent().getPackageName());assertEquals(2,t.home.selectedIndex);
 }
 @Test public void recentFocusFollowsSameAppAfterHistoryReorders(){
  t.home.recent.getChildAt(2).requestFocus();LastOpened.record(t.activity,CinemaViewTest.PKGS[2]);
  t.home.pause();t.home.resume();t.layout(1920,1080);t.idle();t.home.restoreFocus(0);
  assertSame(t.home.recent.getChildAt(0),t.root.findFocus());assertEquals(CinemaViewTest.PKGS[2],((AppAdapter.Entry)ReflectionHelpers.getField(t.root.findFocus(),"entry")).pkg);
 }

 @Test public void nativeFocusAndFlatPersonalizationPreviews()throws Exception{
  t.home.options.theme("aurora");t.home.options.displayMode(WallpaperTheme.DisplayMode.SUNSET);t.home.refreshWallpaper();t.awaitWallpaper("aurora_sunset");t.go(2);settle();t.capture(1920,1080,"phase1h-carousel.png");t.key(t.root.findFocus(),20);settle();t.capture(1920,1080,"phase1h-quick-focus.png");t.key(t.root.findFocus(),19);t.key(t.root.findFocus(),19);settle();t.capture(1920,1080,"phase1h-top-focus.png");
  t.home.showPersonalization();t.layout(1920,1080);t.idle();java.util.List<CinemaWallpaperManager> loaders=ReflectionHelpers.getField(t.home.personalization,"thumbnails");for(int n=0;n<150;n++){Thread.sleep(15);t.idle();boolean ready=true;for(CinemaWallpaperManager l:loaders)ready&=!l.currentKey().isEmpty();if(ready)break;}
  t.capture(1920,1080,"phase1h-personalization.png");t.root.findViewWithTag("glow:85").requestFocus();t.idle();t.capture(1920,1080,"recent-settings-bottom.png");t.capture(1280,720,"recent-settings-bottom-720.png");
 }
}
