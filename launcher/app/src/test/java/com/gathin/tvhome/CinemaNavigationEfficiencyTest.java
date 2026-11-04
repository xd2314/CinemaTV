package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk={29,31},qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaNavigationEfficiencyTest {
 CinemaViewTest t;
 @Before public void setup(){CinemaCatalogue.trimIcons();t=new CinemaViewTest();t.create();t.idle();}
 @After public void close(){t.close();CinemaCatalogue.trimIcons();}
 private void install(String pkg,int version){
  PackageInfo info=new PackageInfo();info.packageName=pkg;info.versionCode=version;info.lastUpdateTime=version;
  info.applicationInfo=new ApplicationInfo();info.applicationInfo.packageName=pkg;info.applicationInfo.enabled=true;info.applicationInfo.nonLocalizedLabel=pkg;
  org.robolectric.shadows.ShadowPackageManager pm=Shadows.shadowOf(t.activity.getPackageManager());pm.installPackage(info);Drawable icon=new ColorDrawable(Color.BLUE);pm.setApplicationIcon(pkg,icon);
  ResolveInfo ri=new ResolveInfo();ri.nonLocalizedLabel=pkg;ri.activityInfo=new ActivityInfo();ri.activityInfo.name=pkg+".Home";ri.activityInfo.packageName=pkg;ri.activityInfo.enabled=true;ri.activityInfo.applicationInfo=info.applicationInfo;
  pm.addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),ri);
 }
 private List<AppAdapter.Entry> load(CinemaCatalogue catalogue)throws Exception{
  AtomicReference<List<AppAdapter.Entry>> result=new AtomicReference<>();AtomicReference<String> error=new AtomicReference<>();catalogue.load(Collections.emptySet(),result::set,error::set);
  for(int i=0;i<400&&result.get()==null&&error.get()==null;i++){Thread.sleep(5);t.idle();}
  assertNull(error.get());assertNotNull(result.get());return result.get();
 }
 @Test public void warmCatalogueReusesThumbnailAndTrimDoesNotRecycleVisibleImage()throws Exception{
  install("efficiency.one",1);Bitmap first;
  CinemaCatalogue one=new CinemaCatalogue(t.activity);try{first=((BitmapDrawable)load(one).get(0).icon).getBitmap();}finally{one.close();}
  CinemaCatalogue two=new CinemaCatalogue(t.activity);
  try{assertSame(first,((BitmapDrawable)load(two).get(0).icon).getBitmap());assertTrue(CinemaCatalogue.iconCacheBytes()>0);
   CinemaCatalogue.trimIcons();assertEquals(0,CinemaCatalogue.iconCacheBytes());assertFalse(first.isRecycled());assertNotSame(first,((BitmapDrawable)load(two).get(0).icon).getBitmap());
  }finally{two.close();}
 }
 @Test public void packageUpdateInvalidatesThumbnail()throws Exception{
  install("efficiency.update",1);CinemaCatalogue c=new CinemaCatalogue(t.activity);
  try{Bitmap before=((BitmapDrawable)load(c).get(0).icon).getBitmap();install("efficiency.update",2);Bitmap after=((BitmapDrawable)load(c).get(0).icon).getBitmap();assertNotSame(before,after);assertFalse(before.isRecycled());}finally{c.close();}
 }
 @Test public void cacheNeverExceedsOneMiBForLargeCatalogue()throws Exception{
  for(int i=0;i<40;i++)install("efficiency.many"+i,1);CinemaCatalogue c=new CinemaCatalogue(t.activity);
  try{List<AppAdapter.Entry> apps=load(c);assertEquals(40,apps.size());assertTrue(CinemaCatalogue.iconCacheBytes()<=CinemaCatalogue.ICON_CACHE_BYTES);for(AppAdapter.Entry e:apps){Bitmap image=((BitmapDrawable)e.icon).getBitmap();assertFalse(image.isRecycled());assertEquals(96,image.getWidth());}}finally{c.close();}
 }
 @Test public void newestRequestReplacesQueuedWorkAndCopiesExclusions()throws Exception{
  install("efficiency.latest",1);CinemaCatalogue c=new CinemaCatalogue(t.activity);ThreadPoolExecutor worker=ReflectionHelpers.getField(c,"worker");CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);List<Integer> delivered=new ArrayList<>();
  try{worker.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));
   for(int i=0;i<15;i++){final int n=i;Set<String> excluded=new HashSet<>();excluded.add("efficiency.latest");c.load(excluded,apps->{assertTrue(apps.isEmpty());delivered.add(n);},error->fail(error));excluded.clear();assertTrue(worker.getQueue().size()<=1);}
   release.countDown();for(int i=0;i<400&&delivered.isEmpty();i++){Thread.sleep(5);t.idle();}assertEquals(Collections.singletonList(14),delivered);
  }finally{release.countDown();c.close();}
 }
 @Test public void closeDiscardsAlreadyPostedCatalogueWithoutTouchingUi()throws Exception{
  install("efficiency.closed",1);CinemaCatalogue c=new CinemaCatalogue(t.activity);AtomicBoolean called=new AtomicBoolean();
  try{c.load(Collections.emptySet(),apps->called.set(true),error->called.set(true));ThreadPoolExecutor worker=ReflectionHelpers.getField(c,"worker");worker.submit(()->{}).get(5,TimeUnit.SECONDS);c.close();assertNull(ReflectionHelpers.getField(c,"delivery"));t.idle();assertFalse(called.get());c.load(Collections.emptySet(),apps->called.set(true),error->called.set(true));assertFalse(called.get());}finally{c.close();}
 }
 @Test public void queuedHomeRestoreCannotOverwriteNewDirection(){
  t.home.homePressed();View first=t.root.findFocus();assertTrue(t.key(first,22));assertEquals(1,t.home.selectedIndex);t.idle();assertEquals(1,t.home.selectedIndex);assertSame(t.home.dock,t.root.findFocus().getParent());
 }
 @Test public void pendingInitialFocusCannotStealFocusFromTopAfterUserInput(){
  ReflectionHelpers.setField(t.home,"initialFocusPending",true);t.home.windowFocusChanged(true);View first=t.home.dock.getChildAt(0);first.requestFocus();assertTrue(t.key(first,19));Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));assertSame(t.home.top,t.root.findFocus().getParent());
 }
 @Test public void lateFirstAppsCannotStealTopFocus(){
  t.seed(0);t.layout(1920,1080);ReflectionHelpers.setField(t.home,"initialAppsPending",true);ReflectionHelpers.setField(t.home,"initialFocusPending",true);
  t.home.dock.getChildAt(0).requestFocus();assertTrue(t.key(t.root.findFocus(),19));View top=t.root.findFocus();assertSame(t.home.top,top.getParent());
  t.seed(4);t.layout(1920,1080);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));assertSame(top,t.root.findFocus());assertEquals(0,t.home.selectedIndex);assertTrue(t.key(top,20));assertSame(t.home.dock,t.root.findFocus().getParent());
 }
 @Test public void pendingRebindRestoreCannotOverrideNewTopNavigation(){
  t.go(2);t.home.bindApps();t.layout(1920,1080);t.home.dock.getChildAt(2).requestFocus();assertTrue(t.key(t.root.findFocus(),19));View top=t.root.findFocus();t.idle();assertSame(top,t.root.findFocus());assertEquals(2,t.home.selectedIndex);
 }
 @Test public void rapidZoneChangesKeepLatestFocusAndSelectedApp(){
  t.go(2);for(int i=0;i<40;i++){assertTrue(t.key(t.root.findFocus(),i%2==0?19:20));}assertEquals(2,t.home.selectedIndex);assertSame(t.home.dock,t.root.findFocus().getParent());Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));assertEquals(2,t.home.selectedIndex);assertEquals(CinemaPolicy.FOCUS_SCALE,t.root.findFocus().getScaleX(),.001f);
 }
 @Test public void personalizationBackRestoresSettingsEntry(){
  t.home.showSettings();t.layout(1920,1080);t.root.findViewWithTag("personalization").performClick();assertNotNull(t.home.personalization);t.home.personalization.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));t.layout(1920,1080);t.idle();assertNotNull(t.home.settingsPanel);assertEquals("personalization",t.root.findFocus().getTag());
 }
 @Test public void returnedDeveloperEntryIsVisibleAndFreshSettingsStayCollapsed(){
  t.home.showSettings();t.layout(1280,720);t.home.settingsPanel.focusEntry("adb:tools");t.idle();t.layout(1280,720);assertTrue(t.root.findViewWithTag("adb:tools").isShown());assertEquals("adb:tools",t.root.findFocus().getTag());
  t.home.closePanel(true);t.home.showSettings();t.layout(1280,720);assertFalse(t.root.findViewWithTag("adb:tools").isShown());assertNull(t.root.findViewWithTag("memory:tools"));
 }
 @Test public void settingsLongTextCanGrowAndNewestDpadBeatsDefaultFocus(){
  t.home.showSettings();t.layout(1280,720);CinemaSettingsPanel p=t.home.settingsPanel;TextView row=p.findViewWithTag("order:settings");row.setText(String.join("",Collections.nCopies(18,"較長的設定說明文字")));t.layout(1280,720);assertTrue(row.getHeight()>Math.round(86*1280/1920f));row.requestFocus();p.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));View latest=t.root.findFocus();t.idle();assertSame(latest,t.root.findFocus());assertNotSame(row,latest);
 }
 @Test public void galleryRevealUsesLatestTileAndCloseCancelsPendingScroll(){
  t.seed(70);t.home.openLibrary(false);CinemaAppPanel panel=t.home.libraryPanel;panel.catalogue.close();panel.setApps(t.adapter.cinemaEntries());t.layout(1280,720);t.idle();
  View last=panel.grid.getChildAt(65),first=panel.grid.getChildAt(0),target=panel.grid.getChildAt(50);assertNotNull(last);last.requestFocus();first.requestFocus();target.requestFocus();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));assertSame(target,t.root.findFocus());Rect r=new Rect();assertTrue(target.getGlobalVisibleRect(r));assertTrue(r.height()>0);t.home.closePanel(true);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));assertNull(t.home.libraryPanel);assertSame(t.library,t.root.findFocus());
 }
}
