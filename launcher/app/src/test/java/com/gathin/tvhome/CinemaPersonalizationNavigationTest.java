package com.gathin.tvhome;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaPersonalizationNavigationTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();t.home.showPersonalization();t.idle();t.layout(1920,1080);}
 @After public void cleanup(){t.close();}
 void key(int code){assertTrue(t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code)));t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,code));t.idle();}
 String focused(){assertNotNull(t.root.findFocus());return String.valueOf(t.root.findFocus().getTag());}
 @Test public void downFromThemeCrossesPreviewToCustomImage(){
  assertEquals("theme:mountain",focused());key(20);assertEquals("wallpaper:day",focused());key(22);assertEquals("wallpaper:night",focused());
 }
 @Test public void downTraversesAllSectionsThenUpReturnsWithoutLeavingPanel()throws Exception{
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){t.home.personalization.showRoot();t.idle();t.layout(size[0],size[1]);java.util.Set<String> seen=new java.util.HashSet<>();
   for(int i=0;i<35;i++){seen.add(focused());key(20);}
   assertEquals("personal:back",focused());for(String tag:new String[]{"wallpaper:day","wallpaper:reset","display:","schedule:","recent:0:","recent:1:","recent:2:","recent:3:","glow:","effects:reset"})assertTrue(tag,seen.stream().anyMatch(found->found.startsWith(tag)));
   android.graphics.Rect visible=new android.graphics.Rect();assertTrue(t.root.findFocus().getGlobalVisibleRect(visible));assertEquals(t.root.findFocus().getHeight(),visible.height());
   t.capture(size[0],size[1],"personalization-bottom-"+size[0]+".png");
   for(int i=0;i<35;i++)key(19);assertEquals("personal:header-back",focused());assertNotSame(t.home.dock,t.root.findFocus().getParent());
  }
 }
 @Test public void everyEnabledControlIsReachableByDpadInLtrAndRtl(){
  java.util.Set<String> expected=new java.util.HashSet<>(java.util.Arrays.asList("theme:mountain","theme:oled","theme:autumn","theme:aurora","wallpaper:day","wallpaper:night","wallpaper:reset","display:AUTO","display:SUNRISE","display:SUNSET","schedule:day","schedule:night","glow:0","glow:50","glow:85","glow:100","effects:reset","images:manage","personal:back","personal:header-back"));
  for(int i=0;i<4;i++){expected.add("recent:"+i+":image");expected.add("recent:"+i+":reset");}
  for(int direction:new int[]{View.LAYOUT_DIRECTION_LTR,View.LAYOUT_DIRECTION_RTL}){
   t.home.personalization.setLayoutDirection(direction);t.layout(1920,1080);t.root.findViewWithTag("theme:mountain").requestFocus();java.util.Set<String> reached=new java.util.HashSet<>();java.util.ArrayDeque<String> queue=new java.util.ArrayDeque<>();reached.add(focused());queue.add(focused());
   while(!queue.isEmpty()){String from=queue.remove();for(int key:new int[]{19,20,21,22}){t.root.findViewWithTag(from).requestFocus();key(key);String next=focused();assertTrue("Unexpected focus: "+next,expected.contains(next));if(reached.add(next))queue.add(next);}}
   assertEquals(expected,reached);
  }
 }
 @Test public void horizontalBoundariesStayOnRowAndSkipUnavailableControls(){
  View mountain=t.root.findViewWithTag("theme:mountain"),oled=t.root.findViewWithTag("theme:oled");oled.setEnabled(false);key(22);assertEquals("theme:autumn",focused());key(21);assertSame(mountain,t.root.findFocus());key(21);assertSame(mountain,t.root.findFocus());
  t.root.findViewWithTag("wallpaper:day").setVisibility(View.GONE);key(20);assertEquals("wallpaper:night",focused());key(22);assertEquals("wallpaper:night",focused());
 }
 @Test public void themeRebuildRestoresFocusAndStillAllowsDown(){
  key(22);assertEquals("theme:oled",focused());key(23);t.layout(1920,1080);t.idle();assertEquals("theme:oled",focused());key(20);assertEquals("wallpaper:day",focused());
 }
 @Test public void bottomControlsAndBackReallyRespondToCenterKey(){
  for(int i=0;i<35;i++)key(20);assertEquals("personal:back",focused());key(19);assertEquals("effects:reset",focused());key(19);assertTrue(focused().startsWith("glow:"));
  for(int i=0;i<5;i++)key(21);assertEquals("glow:0",focused());key(23);t.layout(1920,1080);t.idle();assertEquals(0,t.home.options.glow());assertEquals("glow:0",focused());
  for(int i=0;i<4;i++)key(20);assertEquals("personal:back",focused());key(23);assertNull(t.home.personalization);assertNotNull(t.home.settingsPanel);
 }
 @Test public void fixedShortcutEditorIsAbsentAndWallpaperControlsRemainReachable(){
  assertNull(t.root.findViewWithTag("quick:q0:app"));assertNull(t.root.findViewWithTag("quick:reset"));
  key(20);assertEquals("wallpaper:day",focused());for(int i=0;i<20;i++)key(20);assertEquals("personal:back",focused());
 }

}
