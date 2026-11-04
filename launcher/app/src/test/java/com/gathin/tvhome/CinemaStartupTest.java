package com.gathin.tvhome;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.os.Looper;
import android.provider.Settings;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import java.time.Duration;
import static org.junit.Assert.*;

@RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
public class CinemaStartupTest {
    static final String APP="fixture.startup.player";
    Activity activity;CinemaStartup startup;Spy notice;
    static class Spy implements CinemaStartup.Notice {int shown,failures,lastSeconds;String name;public void show(String label,int n){shown++;lastSeconds=n;name=label;}public void clear(){}public void failed(String text){failures++;}}
    @Before public void setup(){activity=Robolectric.buildActivity(Activity.class).setup().get();CinemaStartup.choices(activity).edit().clear().commit();activity.getSharedPreferences(OpenOnStart.BOOT_PREFS,0).edit().clear().commit();boot(10);registerApp(activity,APP,Intent.CATEGORY_LEANBACK_LAUNCHER);home(activity,activity.getPackageName());notice=new Spy();startup=new CinemaStartup(activity,notice);}
    @After public void close(){startup.close();activity.finish();}
    static Intent homeIntent(){return new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);}
    void boot(int n){Settings.Global.putInt(activity.getContentResolver(),Settings.Global.BOOT_COUNT,n);}
    static void home(Activity activity,String pkg){ResolveInfo r=resolve(activity,pkg);r.isDefault=true;Shadows.shadowOf(activity.getPackageManager()).setResolveInfosForIntent(homeIntent(),java.util.Collections.singletonList(r));}
    static ResolveInfo resolve(Activity activity,String pkg){
        PackageInfo info=new PackageInfo();info.packageName=pkg;info.applicationInfo=new ApplicationInfo();info.applicationInfo.packageName=pkg;info.applicationInfo.enabled=true;info.applicationInfo.nonLocalizedLabel="測試影音";Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
        ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=pkg;r.activityInfo.name=pkg+".Main";r.activityInfo.enabled=true;r.activityInfo.exported=true;r.activityInfo.applicationInfo=info.applicationInfo;return r;
    }
    static void registerApp(Activity activity,String pkg,String category){Shadows.shadowOf(activity.getPackageManager()).addResolveInfoForIntent(new Intent(Intent.ACTION_MAIN).addCategory(category).setPackage(pkg),resolve(activity,pkg));}
    void waitMs(int n){Shadows.shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(n));}
    void prepare(int delay){assertTrue(CinemaStartup.save(activity,APP,delay));boot(11);startup.resume(homeIntent());}
    @Test public void defaultIsOffAndNoLaunch(){startup.resume(homeIntent());waitMs(31000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());assertEquals("",CinemaStartup.target(activity));}
    @Test public void saveIsDurableAndNeverLaunchesThisBoot(){assertTrue(CinemaStartup.save(activity,APP,5));assertEquals(APP,CinemaStartup.target(activity));assertEquals(5,CinemaStartup.delay(activity));startup.resume(homeIntent());waitMs(31000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void countdownThenExactlyOnceEvenWithRepeatedResumeAndRecreation(){
        prepare(5);waitMs(0);assertEquals(5,notice.lastSeconds);waitMs(4000);startup.resume(homeIntent());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());waitMs(1000);
        Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(intent);assertEquals(APP,intent.getComponent().getPackageName());assertTrue(intent.hasCategory(Intent.CATEGORY_LEANBACK_LAUNCHER));
        startup.pause();startup.resume(homeIntent());startup.close();startup=new CinemaStartup(activity,notice);startup.resume(homeIntent());waitMs(31000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
    }
    @Test public void newBootCanLaunchAgain(){prepare(0);waitMs(0);assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());boot(12);startup.resume(homeIntent());waitMs(0);assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void cancelConsumesOnlyThisBoot(){prepare(5);waitMs(1000);startup.cancel();waitMs(10000);startup.resume(homeIntent());waitMs(10000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());boot(12);startup.resume(homeIntent());waitMs(5000);assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void pauseAndDestroyCannotLaunchFromBackground(){prepare(5);startup.pause();waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());startup.resume(homeIntent());startup.close();waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void manualLaunchDoesNotClaimBoot(){assertTrue(CinemaStartup.save(activity,APP,0));boot(11);startup.resume(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER));waitMs(0);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());startup.resume(homeIntent());waitMs(0);assertNotNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void notDefaultHomeDoesNotLaunch(){assertTrue(CinemaStartup.save(activity,APP,0));boot(11);home(activity,"fixture.oem");startup.resume(homeIntent());waitMs(0);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void losingDefaultHomeCancelsCountdown(){prepare(5);home(activity,"fixture.oem");waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void disabledTargetAtDeadlineStaysHomeAndDoesNotRetry(){prepare(5);activity.getPackageManager().setApplicationEnabledSetting(APP,PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,0);waitMs(5000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());assertEquals(1,notice.failures);startup.resume(homeIntent());waitMs(5000);assertEquals(1,notice.failures);}
    @Test public void disabledComponentAndSelfCannotBeChosen(){activity.getPackageManager().setComponentEnabledSetting(new ComponentName(APP,APP+".Main"),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,0);assertFalse(CinemaStartup.save(activity,APP,5));assertFalse(CinemaStartup.save(activity,activity.getPackageName(),5));assertFalse(CinemaStartup.save(activity,"missing.app",5));}
    @Test public void unknownBootStaysHomeAndRejectsEnabling(){boot(-1);assertFalse(CinemaStartup.save(activity,APP,5));CinemaStartup.choices(activity).edit().putString(OpenOnStart.KEY,APP).commit();startup.resume(homeIntent());waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());assertTrue(CinemaStartup.save(activity,"",5));}
    @Test public void turningOffDuringCountdownCancels(){prepare(5);assertTrue(CinemaStartup.save(activity,"",5));waitMs(5000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());boot(12);startup.resume(homeIntent());waitMs(6000);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    @Test public void delayChoicesAreBoundedAndNormalLauncherFallbackWorks(){assertFalse(CinemaStartup.save(activity,APP,-1));assertFalse(CinemaStartup.save(activity,APP,3600));registerApp(activity,"fixture.phone",Intent.CATEGORY_LAUNCHER);Intent i=CinemaStartup.launchIntent(activity,"fixture.phone");assertNotNull(i);assertTrue(i.hasCategory(Intent.CATEGORY_LAUNCHER));}
    @Test public void unavailableAppAtBootIsHandledOnlyOnce(){assertTrue(CinemaStartup.save(activity,APP,0));boot(11);activity.getPackageManager().setApplicationEnabledSetting(APP,PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,0);startup.resume(homeIntent());waitMs(0);assertEquals(1,notice.failures);startup.resume(homeIntent());assertEquals(1,notice.failures);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
    public static class RejectingActivity extends Activity {@Override public void startActivity(Intent intent){throw new SecurityException("simulated platform refusal");}}
    @Test public void platformRefusalDoesNotRetryOnHome(){
        assertTrue(CinemaStartup.save(activity,APP,0));startup.close();activity.finish();
        activity=Robolectric.buildActivity(RejectingActivity.class).setup().get();home(activity,activity.getPackageName());registerApp(activity,APP,Intent.CATEGORY_LEANBACK_LAUNCHER);boot(11);
        startup=new CinemaStartup(activity,notice);startup.resume(homeIntent());waitMs(0);assertEquals(1,notice.failures);startup.resume(homeIntent());waitMs(6000);assertEquals(1,notice.failures);
    }

}
