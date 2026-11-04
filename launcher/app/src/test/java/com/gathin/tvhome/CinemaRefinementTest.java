package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.graphics.*;
import android.media.ExifInterface;
import android.net.Uri;
import android.view.View;
import android.widget.TextView;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import org.robolectric.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaRefinementTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();}
 @After public void cleanup(){t.close();}
 CinemaImagePicker picker(){return new CinemaImagePicker(t.activity,t.home.options.prefs);}
 Uri uri(){return Uri.parse("content://fixture/image");}
 @Test public void chooserUsesSeparateIdentityAndIgnoresDelayedDocumentCancellation(){
  CinemaImagePicker p=picker();assertTrue(p.open("recent:0"));
  org.robolectric.shadows.ShadowActivity.IntentForResult next=Shadows.shadowOf(t.activity).getNextStartedActivityForResult();
  assertEquals(CinemaImagePicker.CONTENT,next.requestCode);assertEquals(Intent.ACTION_CHOOSER,next.intent.getAction());
  assertNull(p.receive(CinemaImagePicker.DOCUMENT,Activity.RESULT_CANCELED,null));
  CinemaImagePicker.Result r=p.receive(CinemaImagePicker.CONTENT,Activity.RESULT_OK,new Intent().setData(uri()));
  assertEquals("recent:0",r.target);assertEquals(uri(),r.uri);assertNull(r.error);
 }
 public static class NoContentActivity extends Activity {
  @Override public void startActivityForResult(Intent intent,int request){
   if(request==CinemaImagePicker.CONTENT)throw new ActivityNotFoundException("fixture");
   super.startActivityForResult(intent,request);
  }
 }
 public static class NoPickerActivity extends Activity {
  @Override public void startActivityForResult(Intent intent,int request){throw new SecurityException("fixture");}
 }
 @Test public void missingContentPickerUsesDocumentRequestAndIgnoresOldContentResult(){
  org.robolectric.android.controller.ActivityController<NoContentActivity> controller=Robolectric.buildActivity(NoContentActivity.class).setup();
  try{
   CinemaImagePicker p=new CinemaImagePicker(controller.get(),t.home.options.prefs);assertTrue(p.open("recent:1"));
   assertEquals(CinemaImagePicker.DOCUMENT,Shadows.shadowOf(controller.get()).getNextStartedActivityForResult().requestCode);
   assertNull(p.receive(CinemaImagePicker.CONTENT,Activity.RESULT_CANCELED,null));
   assertEquals("recent:1",p.receive(CinemaImagePicker.DOCUMENT,Activity.RESULT_OK,new Intent().setData(uri())).target);
  }finally{controller.pause().stop().destroy();}
 }
 @Test public void deniedPickersClearPendingAndPreserveSettings(){
  org.robolectric.android.controller.ActivityController<NoPickerActivity> controller=Robolectric.buildActivity(NoPickerActivity.class).setup();
  try{
   CinemaImagePicker p=new CinemaImagePicker(controller.get(),t.home.options.prefs);assertFalse(p.open("recent:1"));
   assertEquals(0,t.home.options.prefs.getInt("image_pick_request",0));assertFalse(t.home.options.prefs.contains("image_pick_target"));
  }finally{controller.pause().stop().destroy();}
 }
 @Test public void clipAndStreamResultsWorkWithoutGetData(){
  Intent clip=new Intent();clip.setClipData(ClipData.newRawUri("image",uri()));
  for(Intent intent:new Intent[]{clip,new Intent().putExtra(Intent.EXTRA_STREAM,uri())}){
   CinemaImagePicker p=picker();assertTrue(p.open("wallpaper:mountain:day"));
   assertEquals(uri(),p.receive(CinemaImagePicker.CONTENT,Activity.RESULT_OK,intent).uri);
  }
 }
 @Test public void ambiguousReturnShowsErrorAndKeepsImage(){
  t.home.options.image("day","private:aaaa.png");CinemaImagePicker p=picker();p.open("wallpaper:day");
  Intent result=new Intent().setData(uri());result.setClipData(ClipData.newRawUri("other",Uri.parse("content://fixture/other")));
  assertNotNull(p.receive(CinemaImagePicker.CONTENT,Activity.RESULT_OK,result).error);
  assertEquals("private:aaaa.png",t.home.options.image("day"));
 }
 @Test public void emptySuccessfulResultReportsErrorAndAllowsAnotherSelection(){
  CinemaImagePicker p=picker();p.open("recent:0");assertNotNull(p.receive(CinemaImagePicker.CONTENT,Activity.RESULT_OK,new Intent()).error);assertTrue(p.open("recent:1"));
 }
 @Test public void finalCancelClearsPendingWithoutChangingPreferences(){
  CinemaImagePicker p=picker();p.open("recent:2");
  CinemaImagePicker.Result result=p.receive(CinemaImagePicker.CONTENT,Activity.RESULT_CANCELED,null);assertNull(result.uri);assertNull(result.error);
  assertEquals(0,t.home.options.prefs.getInt("image_pick_request",0));assertTrue(p.open("recent:3"));
 }
 @Test public void pickerRecreationRetainsTargetAndRejectsDuplicateOpen(){
  picker().open("wallpaper:aurora:night");assertFalse(picker().open("recent:0"));
  assertEquals("wallpaper:aurora:night",picker().receive(CinemaImagePicker.CONTENT,Activity.RESULT_OK,new Intent().setData(uri())).target);
 }
 @Test public void invalidTargetDoesNotLaunchPicker(){assertFalse(picker().open("wallpaper:../../secret"));assertNull(Shadows.shadowOf(t.activity).getNextStartedActivityForResult());}
 @Test public void visualPresetKeepsRecentVisibilityAndPowerPreference(){
  t.home.options.set("recents",false);t.home.options.set("low_power",true);
  for(String mode:new String[]{"Static","Cinema","Lite"}){t.home.options.mode(mode);assertFalse(t.home.options.on("recents"));assertTrue(t.home.options.on("low_power"));}
  CinemaSettings restored=new CinemaSettings(t.activity);assertFalse(restored.on("recents"));assertTrue(restored.on("low_power"));
 }
 @Test public void glowAndDisplayChangesKeepThumbnailLoadersAndControls(){
  t.home.showPersonalization();t.layout(1920,1080);CinemaPersonalizationPanel panel=ReflectionHelpers.getField(t.home,"personalization");
  List<?> loaders=ReflectionHelpers.getField(panel,"thumbnails");List<?> before=new ArrayList<>(loaders);
  View glow=panel.findViewWithTag("glow:50"),display=panel.findViewWithTag("display:SUNSET");glow.requestFocus();glow.performClick();
  assertSame(glow,panel.findViewWithTag("glow:50"));assertSame(glow,panel.findFocus());assertTrue(((TextView)glow).getText().toString().startsWith("✓"));
  display.performClick();assertSame(display,panel.findViewWithTag("display:SUNSET"));assertEquals(before,loaders);assertEquals(50,t.home.options.glow());assertEquals(WallpaperTheme.DisplayMode.SUNSET,t.home.options.displayMode());
 }
 @Test public void equivalentSearchKeepsExistingTilesAndStillLaunchesCorrectEntry(){
  t.home.openLibrary(true);t.layout(1920,1080);CinemaAppPanel panel=ReflectionHelpers.getField(t.home,"libraryPanel");
  assertTrue(panel.grid.getChildCount()>0);AppAdapter.Entry entry=(AppAdapter.Entry)panel.grid.getChildAt(0).getTag();
  panel.query.setText(entry.label);assertTrue(panel.grid.getChildCount()>0);View tile=panel.grid.getChildAt(0);
  panel.query.setText("  "+entry.label.toUpperCase(java.util.Locale.ROOT)+"  ");assertSame(tile,panel.grid.getChildAt(0));
  assertSame(entry,panel.grid.getChildAt(0).getTag());panel.query.setText("no-such-app-zzzz");assertEquals(0,panel.grid.getChildCount());
  panel.query.setText(entry.pkg);assertTrue(panel.grid.getChildCount()>0);assertEquals(entry.pkg,((AppAdapter.Entry)panel.grid.getChildAt(0).getTag()).pkg);
 }
 @Test public void narrowedSearchReusesMatchingTileWithoutRetainingRemovedViews(){
  t.home.openLibrary(true);t.layout(1920,1080);CinemaAppPanel panel=ReflectionHelpers.getField(t.home,"libraryPanel");
  android.view.View tile=panel.grid.getChildAt(0);AppAdapter.Entry entry=(AppAdapter.Entry)tile.getTag();int before=panel.grid.getChildCount();
  panel.query.setText(entry.pkg);assertTrue(panel.grid.getChildCount()<before);assertSame(tile,panel.grid.getChildAt(0));
 }
 @Test public void stalePruneCannotDeleteImageCommittedAfterSubmission()throws Exception{
  ExecutorService worker=Executors.newSingleThreadExecutor();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
  CinemaImageStore store=new CinemaImageStore(t.activity,worker);
  try{
   worker.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
   Bitmap b=Bitmap.createBitmap(50,25,Bitmap.Config.ARGB_8888);ByteArrayOutputStream bytes=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,bytes);b.recycle();
   Shadows.shadowOf(t.activity.getContentResolver()).registerInputStream(uri(),new ByteArrayInputStream(bytes.toByteArray()));
   CinemaImageStore.Imported imported=store.importNow(uri());File file=CinemaImageStore.file(t.activity,imported.reference);
   store.prune(Collections.emptySet());store.committed(imported.reference);release.countDown();worker.submit(()->{}).get(5,TimeUnit.SECONDS);assertTrue(file.exists());
   store.prune(Collections.emptySet());worker.submit(()->{}).get(5,TimeUnit.SECONDS);assertFalse(file.exists());imported.preview.recycle();
  }finally{release.countDown();store.close();}
 }
 @Test public void jpegExifOrientationIsNormalizedBeforePrivatePngSave()throws Exception{
  File jpeg=new File(t.activity.getCacheDir(),"orientation.jpg");Bitmap b=Bitmap.createBitmap(100,50,Bitmap.Config.ARGB_8888);b.eraseColor(Color.BLUE);
  try(FileOutputStream out=new FileOutputStream(jpeg)){assertTrue(b.compress(Bitmap.CompressFormat.JPEG,95,out));}b.recycle();
  ExifInterface exif=new ExifInterface(jpeg.getPath());exif.setAttribute(ExifInterface.TAG_ORIENTATION,"6");exif.saveAttributes();
  Shadows.shadowOf(t.activity.getContentResolver()).registerInputStream(uri(),new FileInputStream(jpeg));
  CinemaImageStore.Imported imported=t.home.imageStore.importNow(uri());Bitmap normalized=BitmapFactory.decodeFile(CinemaImageStore.file(t.activity,imported.reference).getPath());
  assertEquals(50,normalized.getWidth());assertEquals(100,normalized.getHeight());normalized.recycle();imported.preview.recycle();t.home.imageStore.discard(imported.reference);
 }
}
