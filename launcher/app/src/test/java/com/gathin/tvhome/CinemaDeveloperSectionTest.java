package com.gathin.tvhome;
import android.view.*;
import android.widget.TextView;
import org.junit.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaDeveloperSectionTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();t.home.showSettings();t.layout(1920,1080);t.idle();}
 @After public void close(){t.close();}
 View v(String tag){return t.root.findViewWithTag(tag);}
 @Test public void sensitiveEntriesStartCollapsedAndHiddenClickDoesNothing(){assertFalse(v("boot:assets").isShown());assertFalse(v("adb:tools").isFocusable());v("adb:tools").performClick();assertNull(t.home.adbPanel);assertTrue(((TextView)v("adb:tools")).getText().toString().startsWith("啟動無線連接"));}
 @Test public void toggleExpandsBothThenCollapsesAndReopenResets(){v("developer:toggle").performClick();t.layout(1920,1080);assertEquals(View.VISIBLE,v("boot:assets").getVisibility());assertTrue(v("adb:tools").isFocusable());v("developer:toggle").performClick();assertFalse(v("boot:assets").isFocusable());assertSame(v("developer:toggle"),t.root.findFocus());t.home.showSettings();t.layout(1920,1080);assertEquals(View.GONE,v("boot:assets").getVisibility());}
 @Test public void screenshotsShowCollapsedAndExpandedAtBothSizes()throws Exception{
  for(int[] s:new int[][]{{1920,1080},{1280,720}}){
   t.capture(s[0],s[1],"developer-size-setup-"+s[0]+".png");
   View toggle=v("developer:toggle");toggle.requestFocus();t.idle();
   org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
   android.graphics.Rect r=new android.graphics.Rect();toggle.getDrawingRect(r);toggle.requestRectangleOnScreen(r,true);
   t.capture(s[0],s[1],"developer-collapsed-"+s[0]+".png");
   toggle.performClick();t.layout(s[0],s[1]);t.idle();
   View adb=v("adb:tools");assertTrue(adb.requestFocus());t.idle();
   org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
   adb.getDrawingRect(r);adb.requestRectangleOnScreen(r,true);
   t.capture(s[0],s[1],"developer-expanded-"+s[0]+".png");
   assertSame(adb,t.root.findFocus());assertTrue(adb.getGlobalVisibleRect(r));assertEquals(adb.getHeight(),r.height());
   toggle.performClick();
  }
 }
}
