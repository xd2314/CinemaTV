package com.gathin.tvhome;

import android.app.*;
import android.graphics.*;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk={29,31},qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaPanelExperienceTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();t.idle();}
 @After public void close(){t.close();}
 void settle(){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(220));}
 @Test public void settingsPaneUsesMajorityOfScreenAndBackRemainsReachableAtBothSizes()throws Exception{
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){t.home.showSettings();t.capture(size[0],size[1],"v71-settings-layout-"+size[0]+".png");CinemaSettingsPanel p=t.home.settingsPanel;
   assertTrue("pane too narrow at "+size[0],p.scroll.getWidth()>size[0]/2);assertTrue("pane too wide at "+size[0],p.scroll.getWidth()<size[0]*.6f);TextView back=p.findViewWithTag("settings:back");assertTrue("back width at "+size[0]+" is "+back.getWidth(),back.getWidth()>100*size[0]/1920f);assertTrue("back focus at "+size[0],back.requestFocus());assertTrue("back visible at "+size[0],back.getGlobalVisibleRect(new Rect()));assertFalse("developer collapsed at "+size[0],p.findViewWithTag("adb:tools").isShown());assertEquals("Cinema",t.home.options.mode());t.home.closePanel(true);
  }
 }
 @Test public void nestedReturnRestoresAnchorAndScrollAndFreshEntryKeepsDeveloperCollapsed(){
  t.home.showSettings();t.layout(1280,720);t.idle();CinemaSettingsPanel first=t.home.settingsPanel;first.focusEntry("adb:tools");t.idle();t.layout(1280,720);settle();first.scroll.scrollTo(0,360);CinemaSettingsPanel.State state=first.state();
  t.home.showPersonalization();t.layout(1280,720);t.idle();t.home.personalization.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));t.layout(1280,720);t.idle();assertEquals("personalization",t.root.findFocus().getTag());
  CinemaSettingsPanel restored=t.home.settingsPanel;restored.restore(state,"adb:tools",true);t.idle();t.layout(1280,720);settle();assertEquals("adb:tools",t.root.findFocus().getTag());assertTrue(restored.findViewWithTag("adb:tools").isShown());assertTrue(restored.scroll.getScrollY()>0);
  t.home.closePanel(true);t.home.showSettings();t.layout(1280,720);t.idle();assertFalse(t.root.findViewWithTag("adb:tools").isShown());assertEquals("developer:toggle",t.root.findFocus().getTag());
 }
 @Test public void pendingRestoreCannotOverrideNewDpad(){
  t.home.showSettings();t.layout(1920,1080);t.idle();CinemaSettingsPanel p=t.home.settingsPanel;p.restore(new CinemaSettingsPanel.State("settings:advanced",500,false),null,false);
  View anchor=p.findViewWithTag("startup:settings");anchor.requestFocus();p.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));View latest=t.root.findFocus();t.idle();settle();assertSame(latest,t.root.findFocus());assertNotEquals("settings:advanced",latest.getTag());
 }
 @Test public void retiringPanelsAreBoundedAndResourcesAreReleasedOnceWithoutWaitingForKeys(){
  FrameLayout host=new FrameLayout(t.activity);t.root.addView(host,new FrameLayout.LayoutParams(800,600));t.layout(1920,1080);CinemaPanelMotion motion=new CinemaPanelMotion(host,()->true);AtomicInteger released=new AtomicInteger();
  for(int i=0;i<12;i++){TextView panel=new TextView(t.activity);panel.setFocusable(true);host.addView(panel,new FrameLayout.LayoutParams(800,600));t.layout(1920,1080);motion.enter(panel);motion.retire(panel,released::incrementAndGet);assertEquals(0,host.getChildCount());assertTrue(motion.hasRetiring());assertEquals(i,released.get());}
  motion.finish();assertFalse(motion.hasRetiring());assertEquals(12,released.get());settle();motion.finish();assertEquals(12,released.get());
 }
 @Test public void staticModeSkipsTransitionsAndPauseReleasesGallery(){
  t.home.options.mode("Static");CinemaAppPanel p=t.panel(false);View first=p.grid.getChildAt(0);first.requestFocus();assertEquals(1,p.getAlpha(),0);t.home.closePanel(true);assertSame(t.library,t.root.findFocus());assertEquals(0,p.grid.getChildCount());CinemaPanelMotion motion=ReflectionHelpers.getField(t.home,"panelMotion");assertFalse(motion.hasRetiring());
  t.home.options.mode("Cinema");p=t.panel(false);t.home.closePanel(true);assertNull(t.home.libraryPanel);t.home.pause();assertEquals(0,p.grid.getChildCount());assertFalse(motion.hasRetiring());
 }
 @Test public void statusDistinguishesLoadingEmptyErrorAndDoesNotReplaceAvailableApps(){
  t.seed(0);t.home.openLibrary(false);CinemaAppPanel p=t.home.libraryPanel;p.catalogue.close();assertTrue(p.empty.getText().toString().contains("正在整理"));assertFalse(p.empty.isFocusable());p.setApps(Collections.emptyList());assertTrue(p.empty.getText().toString().contains("沒有可開啟"));p.loadFailed("fixture failure");assertTrue(p.empty.getText().toString().contains("暫時無法"));p.setApps(t.panelFixtures());int n=p.grid.getChildCount();assertTrue(n>0);p.loadFailed("fixture partial");assertEquals(n,p.grid.getChildCount());assertEquals(View.GONE,p.empty.getVisibility());
 }
 @Test public void emptyFiltersExplainNextStepAndSearchCanRecover(){
  CinemaAppPanel p=t.panel(false);t.activity.getSharedPreferences("last_opened",0).edit().clear().commit();p.tabs.getChildAt(2).performClick();assertTrue(p.empty.getText().toString().contains("尚無最近使用"));assertFalse(p.empty.isFocusable());p.tabs.getChildAt(0).performClick();p.query.setText("no such application");assertTrue(p.empty.getText().toString().contains("找不到相符"));p.query.setText("");assertEquals(12,p.grid.getChildCount());assertEquals(View.GONE,p.empty.getVisibility());
 }
 @Test public void personalHeaderBackIsReachableAndReturnsToOriginalSettingsEntry(){
  t.home.showSettings();t.layout(1920,1080);t.idle();t.root.findViewWithTag("personalization").performClick();t.layout(1920,1080);t.idle();CinemaPersonalizationPanel p=t.home.personalization;View first=p.findViewWithTag("theme:mountain");first.requestFocus();assertTrue(p.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP)));assertEquals("personal:header-back",t.root.findFocus().getTag());p.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));assertTrue(t.root.findFocus().getTag().toString().startsWith("theme:"));p.findViewWithTag("personal:header-back").performClick();t.layout(1920,1080);t.idle();assertEquals("personalization",t.root.findFocus().getTag());
 }
 @Test public void imagePreviewBackRestoresSamePageAndSelectedItem()throws Exception{
  File dir=CinemaImageStore.directory(t.activity);assertTrue(dir.isDirectory()||dir.mkdirs());File selected=null;
  for(int i=0;i<205;i++){File f=new File(dir,String.format(Locale.ROOT,"%08x.png",i));try(FileOutputStream out=new FileOutputStream(f)){Bitmap b=Bitmap.createBitmap(2,2,Bitmap.Config.ARGB_8888);b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();}f.setLastModified(10000+i);if(i==1)selected=f;}
  ReflectionHelpers.callStaticMethod(CinemaImageLibrary.class,"preview",ReflectionHelpers.ClassParameter.from(Activity.class,t.activity),ReflectionHelpers.ClassParameter.from(CinemaSettings.class,t.home.options),ReflectionHelpers.ClassParameter.from(CinemaImageStore.class,t.home.imageStore),ReflectionHelpers.ClassParameter.from(List.class,new ArrayList<AppAdapter.Entry>()),ReflectionHelpers.ClassParameter.from(File.class,selected),ReflectionHelpers.ClassParameter.from(int.class,200),ReflectionHelpers.ClassParameter.from(int.class,3));
  AlertDialog preview=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();preview.getButton(-2).performClick();AlertDialog list=preview;
  for(int i=0;i<200&&list==preview;i++){Thread.sleep(5);t.idle();list=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();}
  assertNotSame(preview,list);assertEquals(5,list.getListView().getAdapter().getCount());assertEquals(3,list.getListView().getSelectedItemPosition());list.dismiss();
 }
 @Test public void nativePanelPreviewsAt720And1080()throws Exception{
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){int w=size[0],h=size[1];t.home.showSettings();t.layout(w,h);t.idle();settle();t.capture(w,h,"v71-settings-"+w+".png");t.home.showPersonalization();t.layout(w,h);t.idle();settle();t.capture(w,h,"v71-personalization-"+w+".png");CinemaAppPanel p=t.panel(false);p.query.setText("no such application");t.layout(w,h);settle();t.capture(w,h,"v71-library-empty-"+w+".png");}
 }
}
