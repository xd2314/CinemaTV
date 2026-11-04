package com.gathin.tvhome;

import android.view.*;
import android.provider.Settings;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaStartupPanelTest {
    CinemaViewTest t;
    @Before public void setup(){t=new CinemaViewTest();t.create();CinemaStartup.choices(t.activity).edit().remove(OpenOnStart.KEY).remove(CinemaStartup.DELAY).commit();Settings.Global.putInt(t.activity.getContentResolver(),Settings.Global.BOOT_COUNT,20);CinemaStartupTest.registerApp(t.activity,CinemaViewTest.PKGS[0],android.content.Intent.CATEGORY_LEANBACK_LAUNCHER);}
    @After public void close(){t.close();}
    boolean key(int code){return t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));}
    void back(){t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,4));}
    CinemaStartupPanel panel(){t.home.showStartup();CinemaStartupPanel p=t.home.startupPanel;p.catalogue.close();p.setApps(t.adapter.cinemaEntries());t.layout(1920,1080);t.idle();return p;}
    View app(){return t.root.findViewWithTag("startup:app:"+CinemaViewTest.PKGS[0]);}
    @Test public void entryIsNearFrontAndReachableByRemote(){t.home.showSettings();t.layout(1920,1080);t.idle();assertEquals("personalization",t.home.settingsPanel.rows.getChildAt(0).getTag());View entry=t.home.settingsPanel.rows.getChildAt(1);assertEquals("startup:settings",entry.getTag());t.home.settingsPanel.rows.getChildAt(0).requestFocus();assertTrue(key(20));assertSame(entry,t.root.findFocus());entry.performClick();assertNotNull(t.home.startupPanel);}
    @Test public void chooseAndSaveIsPersistentWithoutLaunching(){panel();app().performClick();t.root.findViewWithTag("startup:delay:10").performClick();assertEquals("",CinemaStartup.target(t.activity));t.root.findViewWithTag("startup:save").performClick();assertEquals(CinemaViewTest.PKGS[0],CinemaStartup.target(t.activity));assertEquals(10,CinemaStartup.delay(t.activity));assertNull(Shadows.shadowOf(t.activity).getNextStartedActivity());t.layout(1920,1080);t.idle();assertEquals("startup:settings",t.root.findFocus().getTag());}
    @Test public void backProtectsDraftAndExplicitDiscardKeepsSavedChoice(){CinemaStartupPanel p=panel();app().performClick();back();assertSame(p,t.home.startupPanel);assertTrue(p.confirmingExit);assertEquals(View.VISIBLE,p.discard.getVisibility());assertEquals("",CinemaStartup.target(t.activity));p.discard.performClick();assertNull(t.home.startupPanel);assertEquals("",CinemaStartup.target(t.activity));}
    @Test public void continueEditingAndOffCanBeSaved(){assertTrue(CinemaStartup.save(t.activity,CinemaViewTest.PKGS[0],5));CinemaStartupPanel p=panel();assertSame(app(),t.root.findFocus());t.root.findViewWithTag("startup:app:").performClick();back();p.cancel.performClick();assertFalse(p.confirmingExit);assertTrue(p.dirty());p.save.performClick();assertEquals("",CinemaStartup.target(t.activity));}
    @Test public void longListCanReachSaveWithoutScrollingToEndAndPreviewsFit()throws Exception{
        t.seed(22);CinemaStartupPanel p=panel();app().performClick();for(int width:new int[]{1920,1280}){t.layout(width,width*9/16);p.list.getChildAt(2).requestFocus();assertTrue(key(22));assertEquals("startup:delay:5",t.root.findFocus().getTag());assertTrue(key(20));assertSame(p.save,t.root.findFocus());android.graphics.Rect bounds=new android.graphics.Rect();assertTrue(p.save.getGlobalVisibleRect(bounds));assertEquals(p.save.getHeight(),bounds.height());t.capture(width,width*9/16,"v48-startup-selected-"+width+".png");p.list.getChildAt(0).requestFocus();for(int i=0;i<40&&t.root.findFocus()!=p.save;i++)key(20);assertSame(p.save,t.root.findFocus());}
    }
    @Test public void offHidesInactiveDelayAndRtlNavigationStaysReachable()throws Exception{
        CinemaStartupPanel p=panel();p.setApps(java.util.Collections.emptyList());t.layout(1920,1080);assertEquals(View.GONE,p.delays.getVisibility());assertTrue(key(22));assertSame(p.save,t.root.findFocus());assertTrue(key(19));assertSame(p.list.getChildAt(0),t.root.findFocus());t.capture(1280,720,"v48-startup-off-1280.png");
        p.setApps(t.adapter.cinemaEntries());app().performClick();p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);t.layout(1920,1080);app().requestFocus();key(21);assertEquals("startup:delay:5",t.root.findFocus().getTag());key(21);assertEquals("startup:delay:10",t.root.findFocus().getTag());key(20);assertSame(p.save,t.root.findFocus());
    }
    @Test public void focusDoesNotChangeSelectionAndLateListDoesNotStealFocus(){CinemaStartupPanel p=panel();app().requestFocus();assertEquals("",CinemaStartup.target(t.activity));assertTrue(p.list.getChildAt(0).isSelected());assertFalse(app().isSelected());key(22);assertSame(p.save,t.root.findFocus());p.setApps(t.adapter.cinemaEntries());assertSame(p.save,t.root.findFocus());}
    @Test public void cleanCompletionDoesNotRewriteBootLedger(){assertTrue(CinemaStartup.save(t.activity,CinemaViewTest.PKGS[0],5));t.activity.getSharedPreferences(OpenOnStart.BOOT_PREFS,0).edit().putInt(OpenOnStart.BOOT_KEY,19).commit();CinemaStartupPanel p=panel();assertFalse(p.dirty());p.save.performClick();assertEquals(19,t.activity.getSharedPreferences(OpenOnStart.BOOT_PREFS,0).getInt(OpenOnStart.BOOT_KEY,-1));}
}
