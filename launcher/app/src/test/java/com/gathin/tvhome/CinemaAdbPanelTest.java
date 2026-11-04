package com.gathin.tvhome;
import android.view.*;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaAdbPanelTest {
 CinemaViewTest t;CinemaAdbPanel panel;CinemaAdbAccess access;CinemaAdbAccessTest.Memory store;CinemaAdbAccessTest.Queue queue;List<CinemaAdbAccess.Action> calls;int dismisses;
 @Before public void setup(){t=new CinemaViewTest();t.create();store=new CinemaAdbAccessTest.Memory();queue=new CinemaAdbAccessTest.Queue();calls=new ArrayList<>();access=new CinemaAdbAccess(a->{calls.add(a);return new CinemaAdbAccess.Result(0,false,"CYT_START_OK\n");},store,queue,Runnable::run);panel=new CinemaAdbPanel(t.activity,access,()->dismisses++);t.home.scene.addView(panel,new android.widget.FrameLayout.LayoutParams(-1,-1));t.layout(1920,1080);t.idle();}
 @After public void close(){panel.dispose();t.close();}
 View v(String tag){return panel.findViewWithTag("adb:"+tag);}void click(String tag){v(tag).performClick();t.idle();}void key(int k){panel.dispatchKeyEvent(new KeyEvent(0,k));panel.dispatchKeyEvent(new KeyEvent(1,k));t.idle();}
 @Test public void openingPanelHasOnlyStartAndNeverRunsCommands(){assertTrue(calls.isEmpty());assertTrue(queue.pending.isEmpty());assertTrue(v("enable").isEnabled());assertEquals("adb:enable",panel.findFocus().getTag());assertNull(v("root"));assertNull(v("status"));}
 @Test public void dpadMovesToBackAndClosesWithoutRoot(){key(20);assertEquals("adb:back",panel.findFocus().getTag());key(4);assertEquals(1,dismisses);assertTrue(calls.isEmpty());assertTrue(queue.pending.isEmpty());}
 @Test public void resultsNavigationMatchesItsPositionAboveStart(){v("enable").requestFocus();key(19);assertSame(v("results"),panel.findFocus());key(20);assertSame(v("enable"),panel.findFocus());assertTrue(calls.isEmpty());}
 @Test public void startOpensConfirmationWithoutRootAndDefaultsToCancel(){click("enable");assertEquals("adb:cancel",panel.findFocus().getTag());assertTrue(calls.isEmpty());assertTrue(queue.pending.isEmpty());key(4);assertTrue(store.writes.isEmpty());assertEquals(0,dismisses);}
 @Test public void doubleConfirmationDispatchesOneActionAndDismissDoesNotResubmit(){click("enable");click("confirm");click("confirm");assertEquals(1,queue.pending.size());assertEquals("PENDING",store.state);panel.dispose();queue.run();assertEquals(1,calls.size());assertEquals(CinemaAdbAccess.Action.ENABLE,calls.get(0));assertTrue(queue.pending.isEmpty());}
 @Test public void confirmationAndStatusAreLegibleAt720And1080()throws Exception{click("enable");t.capture(1920,1080,"adb-confirm-1920.png");t.capture(1280,720,"adb-confirm-1280.png");android.graphics.Rect r=new android.graphics.Rect();assertTrue(v("confirm").getGlobalVisibleRect(r));assertEquals(v("confirm").getHeight(),r.height());key(4);t.capture(1920,1080,"adb-tools-1920.png");}
 @Test public void settingsEntryOpensWithoutAnyRootOperation(){panel.dispose();t.home.scene.removeView(panel);t.home.showSettings();t.layout(1920,1080);t.idle();View entry=t.root.findViewWithTag("adb:tools");assertNotNull(entry);assertFalse(entry.isShown());t.root.findViewWithTag("developer:toggle").performClick();entry.performClick();t.layout(1920,1080);t.idle();assertNotNull(t.home.adbPanel);assertTrue(t.root.findViewWithTag("adb:enable").isEnabled());assertNull(t.root.findViewWithTag("adb:root"));key(4);}
}
