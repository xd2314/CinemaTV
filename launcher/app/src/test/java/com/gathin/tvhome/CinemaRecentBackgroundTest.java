package com.gathin.tvhome;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.widget.*;
import android.view.*;
import org.junit.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaRecentBackgroundTest {
 CinemaViewTest t;CinemaPersonalizationTest helper;
 @Before public void setup(){t=new CinemaViewTest();t.create();helper=new CinemaPersonalizationTest();helper.t=t;}
 @After public void close(){t.close();}
 void root(){t.home.showPersonalization();t.layout(1920,1080);t.idle();}
 @Test public void fourPositionsSaveIndependentlyAndResetOnlyOne(){
  for(int i=0;i<4;i++){assertEquals("season:"+i,t.home.options.recentImage(i));t.home.options.recentImage(i,"private:slot"+i+".png");}
  CinemaSettings restored=new CinemaSettings(t.activity);for(int i=0;i<4;i++)assertEquals("private:slot"+i+".png",restored.recentImage(i));
  restored.resetRecentImage(2);assertEquals("season:2",new CinemaSettings(t.activity).recentImage(2));assertEquals("private:slot0.png",restored.recentImage(0));assertEquals("private:slot3.png",restored.recentImage(3));assertTrue(restored.imageReferences(Collections.emptyList()).contains("private:slot2.png"));
 }
 @Test public void legacyCustomImagesMigrateBySavedPositionOnceWithoutRestoringFavourites(){
  t.home.options.prefs.edit().clear().commit();List<AppAdapter.Entry> apps=ReflectionHelpers.getField(t.home,"apps");List<QuickLaunchItem> old=QuickLaunchItem.defaults(apps);old.get(0).image="private:old.png";Collections.swap(old,0,2);QuickLaunchItem.save(t.home.options,old);String before=t.home.options.prefs.getString("quick_launch_v1","");
  CinemaSettings migrated=new CinemaSettings(t.activity);assertEquals("private:old.png",migrated.recentImage(2));assertEquals("season:0",migrated.recentImage(0));assertEquals(before,migrated.prefs.getString("quick_launch_v1",""));
  migrated.resetRecentImage(2);assertEquals("season:2",new CinemaSettings(t.activity).recentImage(2));assertTrue(migrated.imageReferences(apps).contains("private:old.png"));
 }
 @Test public void eachImageControlTargetsItsPositionWithoutCreatingHomeCards(){
  t.activity.getSharedPreferences("last_opened",0).edit().clear().commit();t.home.resume();root();assertEquals(0,t.home.recent.getChildCount());
  for(int i=0;i<4;i++){helper.click("recent:"+i+":image");assertEquals("recent:"+i,t.home.options.prefs.getString("image_pick_target",""));t.home.activityResult(CinemaHome.PICK_IMAGE,Activity.RESULT_CANCELED,null);assertEquals("season:"+i,t.home.options.recentImage(i));assertNotNull(t.root.findViewWithTag("recent:"+i+":reset"));}
  assertEquals(0,t.home.recent.getChildCount());assertNull(t.root.findViewWithTag("quick:q0:app"));
 }
 @Test public void cancelKeepsOldAndConfirmationUsesCapturedPositionAfterAppReorder()throws Exception{
  t.home.options.recentImage(2,"private:original.png");root();t.home.chooseImage("recent:2");
  t.home.activityResult(CinemaHome.PICK_IMAGE,Activity.RESULT_OK,new Intent().setData(helper.image("cancel-recent",Bitmap.CompressFormat.PNG,640,320)));helper.idleUntil(()->t.root.findViewWithTag("image:cancel")!=null);helper.click("image:cancel");t.idle();assertEquals("private:original.png",t.home.options.recentImage(2));assertEquals("recent:2:image",t.root.findFocus().getTag());
  t.home.chooseImage("recent:2");t.home.activityResult(CinemaHome.PICK_IMAGE,Activity.RESULT_OK,new Intent().setData(helper.image("confirm-recent",Bitmap.CompressFormat.PNG,640,320)));helper.idleUntil(()->t.root.findViewWithTag("image:confirm")!=null);
  LastOpened.record(t.activity,CinemaViewTest.PKGS[3]);helper.click("image:confirm");t.idle();String saved=t.home.options.recentImage(2);assertNotEquals("private:original.png",saved);assertTrue(CinemaImageStore.file(t.activity,saved).exists());assertEquals("season:0",t.home.options.recentImage(0));assertEquals(saved,new CinemaSettings(t.activity).recentImage(2));assertEquals("recent:2:image",t.root.findFocus().getTag());
  String[] sources=ReflectionHelpers.getField(t.home.seasons,"sources");assertEquals(saved,sources[2]);assertEquals("season:0",sources[0]);
 }
 @Test public void resetControlRefreshesHomeAndRetainsOriginalImageFile()throws Exception{
  CinemaImageStore.Imported imported=t.home.imageStore.importNow(helper.image("reset-recent",Bitmap.CompressFormat.PNG,200,100));t.home.options.recentImage(1,imported.reference);t.home.imageStore.committed(imported.reference);root();helper.click("recent:1:reset");assertEquals("season:1",t.home.options.recentImage(1));assertEquals("season:1",((String[])ReflectionHelpers.getField(t.home.seasons,"sources"))[1]);
  java.util.concurrent.ExecutorService worker=ReflectionHelpers.getField(t.home.imageStore,"worker");worker.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);assertTrue(CinemaImageStore.file(t.activity,imported.reference).exists());assertTrue(t.home.options.imageReferences(Collections.emptyList()).contains(imported.reference));
 }
 @Test public void missingCustomImagesUseSeasonFallbackAndFocusDoesNotReload()throws Exception{
  t.home.options.recentImage(0,"private:not-found.png");t.home.resume();helper.idleUntil(()->t.home.seasons.count()==4);Object bitmapArray=ReflectionHelpers.getField(t.home.seasons,"images");for(int i=0;i<4;i++)assertNotNull(((ImageView)ReflectionHelpers.getField(t.home.recent.getChildAt(i),"art")).getDrawable());
  t.layout(1920,1080);t.home.recent.getChildAt(0).requestFocus();for(int i=0;i<20;i++){t.key(t.root.findFocus(),i%2==0?22:21);}assertSame(bitmapArray,ReflectionHelpers.getField(t.home.seasons,"images"));
 }
 @Test public void invalidImageTargetCannotWriteOutsideFourPositions()throws Exception{
  root();t.home.options.prefs.edit().putString("image_pick_target","recent:4").apply();t.home.activityResult(CinemaHome.PICK_IMAGE,Activity.RESULT_OK,new Intent().setData(helper.image("invalid-position",Bitmap.CompressFormat.PNG,100,100)));t.idle();assertNull(t.root.findViewWithTag("image:confirm"));assertArrayEquals(new String[]{"season:0","season:1","season:2","season:3"},t.home.options.recentImages());
 }
 @Test public void nativePersonalizationShowsFourBackgroundEditorsAtBothSizes()throws Exception{
  root();helper.idleUntil(()->{List<CinemaWallpaperManager> loaders=ReflectionHelpers.getField(t.home.personalization,"thumbnails");for(CinemaWallpaperManager l:loaders)if(l.currentKey().isEmpty())return false;return true;});
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){t.layout(size[0],size[1]);View control=t.root.findViewWithTag("recent:3:reset");control.requestFocus();android.graphics.Rect r=new android.graphics.Rect();control.getDrawingRect(r);control.requestRectangleOnScreen(r,true);t.capture(size[0],size[1],"recent-background-editor-"+size[0]+".png");}
 }
}
