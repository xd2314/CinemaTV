package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaHomeActionsTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();}
 @After public void close(){t.close();}
 @Test public void tailIsUniqueAndLeavesRealCatalogueAndHistoryAlone(){
  List<AppAdapter.Entry> original=t.adapter.cinemaEntries(),row=CinemaHomeActions.carousel(original);
  assertEquals(4,original.size());assertEquals(5,row.size());for(int i=0;i<4;i++)assertSame(original.get(i),row.get(i));
  assertTrue(CinemaHomeActions.isAllApps(row.get(4)));
  t.go(4);View tail=t.root.findFocus();assertEquals("所有應用程式",tail.getContentDescription());
  assertFalse(tail.performLongClick());assertTrue(tail.performClick());assertNotNull(t.home.libraryPanel);
  List<AppAdapter.Entry> actual=ReflectionHelpers.getField(t.home,"apps");assertEquals(4,actual.size());
  assertEquals(0,LastOpened.get(t.activity,CinemaHomeActions.ALL_APPS));assertNull(Shadows.shadowOf(t.activity).getNextStartedActivity());
  t.home.closePanel(true);assertSame(tail,t.root.findFocus());assertEquals(4,t.home.selectedIndex);
 }
 @Test public void emptyCatalogueStillHasReachableLibraryAction(){
  t.seed(0);t.layout(1920,1080);t.home.homePressed();t.idle();assertEquals(1,t.home.dock.getChildCount());
  View tail=t.root.findFocus();assertTrue(t.key(tail,21));assertSame(tail,t.root.findFocus());assertTrue(t.key(tail,22));assertSame(tail,t.root.findFocus());tail.performClick();assertNotNull(t.home.libraryPanel);
 }
 @Test public void recycledTailAndReturnPreserveSelectionAndRealLaunch(){
  t.seed(22);t.layout(1920,1080);t.go(22);View tail=t.root.findFocus();assertTrue(t.key(tail,22));assertEquals(22,t.home.selectedIndex);
  tail.performClick();t.home.closePanel(true);assertEquals(22,t.home.selectedIndex);
  t.go(0);t.go(22);t.root.findFocus().performClick();t.home.closePanel(true);t.go(1);
  t.root.findFocus().performClick();Intent intent=Shadows.shadowOf(t.activity).getNextStartedActivity();assertNotNull(intent);assertEquals(CinemaViewTest.PKGS[1],intent.getComponent().getPackageName());
 }
 @Test public void rtlTailHasCorrectPhysicalBoundary(){
  t.home.scene.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);t.layout(1920,1080);t.go(4);View tail=t.root.findFocus();assertTrue(t.key(tail,21));assertEquals(4,t.home.selectedIndex);assertTrue(t.key(tail,22));assertEquals(3,t.home.selectedIndex);
 }
 @Test public void networkIsImmediatelyLeftOfAllAppsAndBackDownKeepsApp(){
  View network=t.home.top.getChildAt(0);assertSame(t.home.networkButton,network);assertTrue(network.getContentDescription().toString().contains("網路"));assertSame(t.library,t.home.top.getChildAt(1));assertTrue(network.getLeft()<t.library.getLeft());
  t.go(2);network.requestFocus();assertTrue(t.key(network,22));assertSame(t.library,t.root.findFocus());assertTrue(t.key(t.library,21));assertSame(network,t.root.findFocus());assertTrue(t.key(network,20));assertEquals(2,t.home.selectedIndex);
 }
 @Test public void finalUiNativePreviews()throws Exception{
  t.seed(7);t.layout(1920,1080);t.go(7);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));t.capture(1920,1080,"v43-tail-action-1920.png");t.capture(1280,720,"v43-tail-action-1280.png");
  t.home.top.getChildAt(0).requestFocus();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));t.capture(1920,1080,"v43-play-focus-1920.png");
 }
}
