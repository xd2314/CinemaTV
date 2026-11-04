package com.gathin.tvhome;
import android.content.*;
import android.content.pm.*;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaRecentItemsTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();}
 @After public void close(){t.close();}
 void refresh(){t.home.resume();t.layout(1920,1080);t.idle();}
 String pkg(int index){return ((AppAdapter.Entry)ReflectionHelpers.getField(t.home.recent.getChildAt(index),"entry")).pkg;}
 void clear(){t.activity.getSharedPreferences("last_opened",0).edit().clear().commit();}
 @Test public void emptyHistoryHasNoCardsAndDpadStaysOnCarousel(){
  clear();refresh();assertEquals(0,t.home.recent.getChildCount());assertEquals(View.GONE,t.home.recentScroll.getVisibility());
  t.go(1);View current=t.root.findFocus();assertTrue(t.key(current,20));assertSame(current,t.root.findFocus());assertNull(Shadows.shadowOf(t.activity).getNextStartedActivity());
 }
 @Test public void latestFourAreUniqueAndRepeatedOpenMovesToFront(){
  t.seed(7);clear();for(int i=0;i<4;i++)LastOpened.record(t.activity,CinemaViewTest.PKGS[i]);LastOpened.record(t.activity,"fixture.app4");LastOpened.record(t.activity,CinemaViewTest.PKGS[1]);refresh();
  assertEquals(Arrays.asList(CinemaViewTest.PKGS[1],"fixture.app4",CinemaViewTest.PKGS[3],CinemaViewTest.PKGS[2]),Arrays.asList(pkg(0),pkg(1),pkg(2),pkg(3)));
 }
 @Test public void disabledOrRemovedRecordsDoNotPreventFillingFourValidItems(){
  t.seed(7);clear();for(int i=0;i<4;i++)LastOpened.record(t.activity,CinemaViewTest.PKGS[i]);LastOpened.record(t.activity,"fixture.app4");LastOpened.record(t.activity,"missing.removed");
  t.activity.getPackageManager().setApplicationEnabledSetting("fixture.app4",PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER,0);refresh();
  assertEquals(Arrays.asList(CinemaViewTest.PKGS[3],CinemaViewTest.PKGS[2],CinemaViewTest.PKGS[1],CinemaViewTest.PKGS[0]),Arrays.asList(pkg(0),pkg(1),pkg(2),pkg(3)));
 }
 @Test public void disabledActivityIsNotALaunchTarget(){
  t.activity.getPackageManager().setComponentEnabledSetting(new ComponentName(CinemaViewTest.PKGS[0],".Main"),PackageManager.COMPONENT_ENABLED_STATE_DISABLED,0);refresh();
  assertEquals(CinemaViewTest.PKGS[1],pkg(0));assertEquals(3,t.home.recent.getChildCount());assertNull(t.home.recent.getChildAt(3));
 }
 @Test public void galleryAppOutsideCarouselCanAppearInHistory(){
  String name="fixture.gallery.only";PackageInfo p=new PackageInfo();p.packageName=name;p.applicationInfo=new ApplicationInfo();p.applicationInfo.packageName=name;p.applicationInfo.enabled=true;p.applicationInfo.nonLocalizedLabel="Gallery app";Shadows.shadowOf(t.activity.getPackageManager()).installPackage(p);Shadows.shadowOf(t.activity.getPackageManager()).setApplicationIcon(name,t.fixtureIcon(2));
  ResolveInfo r=new ResolveInfo();r.activityInfo=new ActivityInfo();r.activityInfo.packageName=name;r.activityInfo.name=name+".Tv";r.activityInfo.enabled=true;r.activityInfo.exported=true;r.activityInfo.applicationInfo=p.applicationInfo;
  Intent query=new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER).setPackage(name);Shadows.shadowOf(t.activity.getPackageManager()).addResolveInfoForIntent(query,r);
  LastOpened.record(t.activity,name);refresh();assertEquals(name,pkg(0));
 }
 @Test public void seasonalArtFollowsPositionNotLegacySlotOrApp(){
  List<AppAdapter.Entry> apps=ReflectionHelpers.getField(t.home,"apps");List<QuickLaunchItem> old=QuickLaunchItem.read(t.home.options,apps);old.get(0).image="private:keep.png";old.get(0).displayName="Old favourite";Collections.reverse(old);QuickLaunchItem.save(t.home.options,old);String saved=t.home.options.prefs.getString("quick_launch_v1","");
  LastOpened.record(t.activity,CinemaViewTest.PKGS[3]);refresh();assertEquals(CinemaViewTest.PKGS[3],pkg(0));
  String[] sources=ReflectionHelpers.getField(t.home.seasons,"sources");assertArrayEquals(new String[]{"season:0","season:1","season:2","season:3"},sources);
  for(int i=0;i<4;i++)assertEquals(i,(int)ReflectionHelpers.getField(t.home.recent.getChildAt(i),"seasonIndex"));
  assertEquals(saved,t.home.options.prefs.getString("quick_launch_v1",""));assertTrue(t.home.options.imageReferences(apps).contains("private:keep.png"));
 }
 @Test public void recentOkLaunchesHighlightedAppAndDoesNotOpenShortcutEditor(){
  t.home.recent.getChildAt(2).requestFocus();View chosen=t.root.findFocus();assertFalse(chosen.isLongClickable());
  chosen.dispatchKeyEvent(new KeyEvent(0,23));chosen.dispatchKeyEvent(new KeyEvent(1,23));Intent launch=Shadows.shadowOf(t.activity).getNextStartedActivity();assertNotNull(launch);assertEquals(CinemaViewTest.PKGS[2],launch.getComponent().getPackageName());assertEquals(CinemaViewTest.PKGS[2],LastOpened.recentPackages(t.activity,4).get(0));assertNull(t.home.personalization);
 }
 @Test public void orderSurvivesPreferenceReopenAndClockTies(){
  clear();for(int i=0;i<50;i++)LastOpened.record(t.activity,CinemaViewTest.PKGS[i%4]);
  assertEquals(Arrays.asList(CinemaViewTest.PKGS[1],CinemaViewTest.PKGS[0],CinemaViewTest.PKGS[3],CinemaViewTest.PKGS[2]),LastOpened.recentPackages(t.activity.getApplicationContext(),4));
  t.activity.getSharedPreferences("last_opened",0).edit().clear().putLong("z",20).putLong("a",20).commit();assertEquals(Arrays.asList("a","z"),LastOpened.recentPackages(t.activity,4));assertTrue(LastOpened.recentPackages(t.activity,0).isEmpty());
 }
 @Test public void screenshotRecentAndEmptyStatesAtBothResolutions()throws Exception{
  t.seed(7);refresh();
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){t.home.options.theme("mountain");t.home.options.displayMode(WallpaperTheme.DisplayMode.SUNSET);t.home.refreshWallpaper();t.awaitWallpaper("lake");t.home.recent.getChildAt(1).requestFocus();t.capture(size[0],size[1],"recent-items-"+size[0]+".png");}
  clear();refresh();t.capture(1920,1080,"recent-empty-1920.png");
 }
 @Test public void oneTwoThreeAndFourItemsUseOnlyCorrespondingSeasonPositions()throws Exception{
  clear();
  for(int n=1;n<=4;n++){
   LastOpened.record(t.activity,CinemaViewTest.PKGS[n-1]);refresh();assertEquals(n,t.home.recent.getChildCount());assertNull(t.home.recent.getChildAt(n));assertEquals(View.VISIBLE,t.home.recentScroll.getVisibility());
   for(int i=0;i<n;i++){assertEquals(i,(int)ReflectionHelpers.getField(t.home.recent.getChildAt(i),"seasonIndex"));assertTrue(t.home.recent.getChildAt(i).isFocusable());}
   View last=t.home.recent.getChildAt(n-1);last.requestFocus();assertTrue(t.key(last,22));assertSame(last,t.root.findFocus());assertTrue(t.key(last,19));assertSame(t.home.dock,t.root.findFocus().getParent());
  }
  clear();LastOpened.record(t.activity,CinemaViewTest.PKGS[0]);refresh();t.seed(7);t.layout(1920,1080);t.home.options.displayMode(WallpaperTheme.DisplayMode.SUNSET);t.home.refreshWallpaper();t.awaitWallpaper("lake");
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){t.home.recent.getChildAt(0).requestFocus();t.capture(size[0],size[1],"recent-single-"+size[0]+".png");}
 }
 @Test public void removingLastHistoryItemReturnsFocusToCarousel(){
  clear();LastOpened.record(t.activity,CinemaViewTest.PKGS[0]);refresh();t.home.recent.getChildAt(0).requestFocus();clear();refresh();assertEquals(0,t.home.recent.getChildCount());assertSame(t.home.dock,t.root.findFocus().getParent());
 }

}
