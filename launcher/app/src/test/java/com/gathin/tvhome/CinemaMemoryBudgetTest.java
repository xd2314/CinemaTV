package com.gathin.tvhome;
import android.app.Activity;
import android.graphics.*;
import android.widget.ImageView;
import org.junit.*;
import org.robolectric.*;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=29,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaMemoryBudgetTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();}
 @After public void close(){t.close();}
 @Test public void backgroundBudgetHandles4kAndLowHeapWithoutChangingViewGeometry(){
  assertArrayEquals(new int[]{1920,1080},CinemaMemoryPolicy.background(3840,2160,256,false));
  assertArrayEquals(new int[]{1280,720},CinemaMemoryPolicy.background(3840,2160,128,false));
  assertArrayEquals(new int[]{1280,720},CinemaMemoryPolicy.background(1920,1080,256,true));
  assertArrayEquals(new int[]{1280,720},CinemaMemoryPolicy.background(1280,720,256,false));
 }
 @Test public void rendererCachesAreBoundedFor200AppsAndRepeatedNavigation(){
  t.seed(200);t.layout(1920,1080);for(int i=0;i<200;i+=3){t.go(i);t.layout(1920,1080);}
  Map<?,?> assets=ReflectionHelpers.getField(t.home,"cardAssets");assertTrue(assets.size()<=CinemaMemoryPolicy.MAX_CARD_ASSETS);
  assertTrue(t.home.focusLight.cachedInkCount()<=CinemaMemoryPolicy.MAX_LIGHT_INKS);assertTrue(t.home.focusLight.cachedBitmapBytes()<2L*1024*1024);
  assertEquals(7,t.home.dock.getChildCount());assertTrue(CinemaImageDecoder.queued()<=32);
 }
 @Test public void returningWithoutHistoryChangesReusesRecentViews(){
  android.view.View first=t.home.recent.getChildAt(0);t.home.pause();t.home.resume();assertSame(first,t.home.recent.getChildAt(0));
  LastOpened.record(t.activity,CinemaViewTest.PKGS[1]);t.home.pause();t.home.resume();assertNotSame(first,t.home.recent.getChildAt(0));
 }
 @Test public void pausedTrimDropsRendererReferencesAndResumeRestoresUsableCards(){
  t.home.pause();t.home.trim();assertEquals(0,t.home.focusLight.cachedInkCount());assertNull(ReflectionHelpers.getField(t.home,"featuredCard"));
  Map<?,?> assets=ReflectionHelpers.getField(t.home,"cardAssets");assertEquals(0,assets.size());t.home.resume();t.layout(1920,1080);t.home.homePressed();assertEquals(CinemaViewTest.PKGS[0],t.root.findFocus().getContentDescription()==null?"":((AppAdapter.Entry)ReflectionHelpers.getField(t.root.findFocus(),"entry")).pkg);
 }
 @Test public void sharedDecoderDoesNotDieWhenOneThumbnailCloses(){
  CinemaWallpaperManager a=new CinemaWallpaperManager(t.activity,new ImageView(t.activity),fallback->{},false),b=new CinemaWallpaperManager(t.activity,new ImageView(t.activity),fallback->{},false);
  assertSame(ReflectionHelpers.getField(a,"decoder"),ReflectionHelpers.getField(b,"decoder"));a.close();assertFalse(CinemaImageDecoder.worker().isShutdown());b.close();
 }
 @Test public void thumbnailCacheRemainsUnderFourMiB()throws Exception{
  CinemaWallpaperManager.trimThumbnails();List<CinemaWallpaperManager> managers=new ArrayList<>();
  for(String source:new String[]{"lake","sunrise","oled_sunrise","oled_sunset","autumn_sunrise","autumn_sunset","aurora_sunrise","aurora_sunset"}){
   CinemaWallpaperManager m=new CinemaWallpaperManager(t.activity,new ImageView(t.activity),fallback->{},false);managers.add(m);m.load(source,480,270);
  }
  for(int i=0;i<400;i++){Thread.sleep(10);t.idle();boolean ready=true;for(CinemaWallpaperManager m:managers)ready&=!m.currentKey().isEmpty();if(ready)break;}
  assertTrue(CinemaWallpaperManager.thumbnailBytes()<=CinemaMemoryPolicy.THUMBNAIL_CACHE_BYTES);for(CinemaWallpaperManager m:managers){assertFalse(m.currentKey().isEmpty());m.close();}
 }
 @Test public void imageCleanupCannotDeleteActiveOrStagedPictures()throws Exception{
  File dir=CinemaImageStore.directory(t.activity);dir.mkdirs();File f=new File(dir,"abc-123.png");java.nio.file.Files.write(f.toPath(),new byte[]{1});String ref="private:"+f.getName();
  assertFalse(t.home.imageStore.deleteUnused(ref,()->Collections.singleton(ref)));assertTrue(f.exists());assertTrue(t.home.imageStore.deleteUnused(ref,Collections::emptySet));assertFalse(f.exists());
  File rootBackup=new File(t.activity.getFilesDir(),"before.img");java.nio.file.Files.write(rootBackup.toPath(),new byte[]{2});try{t.home.imageStore.deleteUnused("private:../before.img",Collections::emptySet);fail();}catch(IOException expected){}assertTrue(rootBackup.exists());
 }
 @Test public void posterPolicySkipsBlackFrameAndIsBounded(){
  Bitmap black=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888),bright=Bitmap.createBitmap(640,360,Bitmap.Config.ARGB_8888);bright.eraseColor(Color.CYAN);
  assertEquals(0,CinemaPosterPolicy.score(black));assertTrue(CinemaPosterPolicy.score(bright)>12);int count=0;for(int frame=1;frame<=120;frame++)if(CinemaPosterPolicy.sample(frame))count++;assertEquals(10,count);black.recycle();bright.recycle();
 }
 @Test public void explicitRecordingHonorsOriginalDeadlineAndDoesNotResumeAfterRecreation()throws Exception{
  CinemaMemoryDiagnostics monitor=new CinemaMemoryDiagnostics(t.activity,org.json.JSONObject::new);
  try{
   assertTrue(monitor.start());long started=monitor.record().getLong("startedAt");assertFalse(monitor.start());
   for(int i=0;i<27;i++){
    for(int n=0;n<100&&(Boolean)ReflectionHelpers.getField(monitor,"busy");n++){Thread.sleep(5);t.idle();}
    Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(5000));
   }
   org.json.JSONObject report=monitor.record();assertEquals(started,report.getLong("startedAt"));assertTrue(report.getJSONArray("samples").length()<=CinemaMemoryDiagnostics.MAX_SAMPLES);assertEquals("COMPLETED_WINDOW",report.getString("state"));
  }finally{monitor.close();}
  t.activity.getSharedPreferences("cinema_memory_v1",0).edit().putString("record","{\"state\":\"RUNNING\"}").commit();CinemaMemoryDiagnostics recreated=new CinemaMemoryDiagnostics(t.activity,org.json.JSONObject::new);
  try{assertEquals("INTERRUPTED",recreated.record().getString("state"));assertFalse((Boolean)ReflectionHelpers.getField(recreated,"running"));}finally{recreated.close();}
 }
 @Test public void delayedMemorySampleCannotExtendOriginalObservationWindow()throws Exception{
  CinemaMemoryDiagnostics monitor=new CinemaMemoryDiagnostics(t.activity,org.json.JSONObject::new);java.util.concurrent.ExecutorService worker=ReflectionHelpers.getField(monitor,"worker");java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
  try{worker.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));assertTrue(monitor.start());
   Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(121000));release.countDown();
   for(int n=0;n<200&&(Boolean)ReflectionHelpers.getField(monitor,"busy");n++){Thread.sleep(5);t.idle();}
   assertEquals("COMPLETED_WINDOW",monitor.record().getString("state"));assertEquals(0,monitor.record().getJSONArray("samples").length());
  }finally{release.countDown();monitor.close();}
 }
 @Test public void fullImageQueueKeepsPreviousViewInsteadOfCrashing()throws Exception{
  java.util.concurrent.ThreadPoolExecutor shared=(java.util.concurrent.ThreadPoolExecutor)CinemaImageDecoder.worker();
  java.util.concurrent.CountDownLatch entered=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);java.util.List<java.util.concurrent.Future<?>> queued=new java.util.ArrayList<>();
  CinemaWallpaperManager manager=new CinemaWallpaperManager(t.activity,new ImageView(t.activity),fallback->{},false);
  try{shared.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
   while(shared.getQueue().remainingCapacity()>0)queued.add(shared.submit(()->{}));manager.load("lake",480,270);assertEquals(32,CinemaImageDecoder.queued());assertEquals("",manager.currentKey());
  }finally{for(java.util.concurrent.Future<?> f:queued)CinemaImageDecoder.cancel(f);release.countDown();manager.close();}
 }
 @Test public void memoryDiagnosticsEntryIsAbsentEvenWhenDeveloperToolsExpanded(){
  t.home.showSettings();t.layout(1920,1080);assertNull(t.root.findViewWithTag("memory:tools"));
  android.view.View toggle=t.root.findViewWithTag("developer:toggle");assertNotNull(toggle);toggle.performClick();t.layout(1920,1080);
  assertNull(t.root.findViewWithTag("memory:tools"));assertTrue(t.root.findViewWithTag("boot:assets").isShown());assertTrue(t.root.findViewWithTag("adb:tools").isShown());
 }
}
