package com.gathin.tvhome;

import android.content.*;
import android.os.Looper;
import android.provider.Settings;
import android.view.KeyEvent;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
public class CinemaStartupActivityTest {
    ActivityController<MainActivity> controller;MainActivity activity;
    void start(){
        controller=Robolectric.buildActivity(MainActivity.class,CinemaStartupTest.homeIntent()).create();activity=controller.get();
        CinemaStartupTest.registerApp(activity,CinemaStartupTest.APP,Intent.CATEGORY_LEANBACK_LAUNCHER);CinemaStartupTest.home(activity,activity.getPackageName());
        Settings.Global.putInt(activity.getContentResolver(),Settings.Global.BOOT_COUNT,50);assertTrue(CinemaStartup.save(activity,CinemaStartupTest.APP,5));
        Settings.Global.putInt(activity.getContentResolver(),Settings.Global.BOOT_COUNT,51);controller.start().resume().visible();
    }
    void waitMs(int n){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(n));}
    @After public void stop(){if(controller!=null)controller.pause().stop().destroy();}
    @Test public void realActivityLifecycleLaunchesOnceThenPhysicalHomeDoesNotRepeat(){start();waitMs(5000);Intent launched=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(launched);assertEquals(CinemaStartupTest.APP,launched.getComponent().getPackageName());controller.pause();controller.newIntent(CinemaStartupTest.homeIntent());controller.resume();waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void realRemoteInputCancelsCountdownWithoutBlockingNavigation(){start();waitMs(1000);activity.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());CinemaHome home=ReflectionHelpers.getField(activity,"cinemaHome");assertNotNull(home);}
    @Test public void homeIntentAndFocusLossCancelThePendingLaunch(){start();waitMs(1000);controller.newIntent(CinemaStartupTest.homeIntent());activity.onWindowFocusChanged(false);waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
}
