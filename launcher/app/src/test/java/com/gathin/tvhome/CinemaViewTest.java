package com.gathin.tvhome;

import android.app.Activity;
import android.content.ComponentName;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaViewTest {
 java.util.Locale previousLocale;
 Activity activity;CinemaHome home;AppAdapter adapter;FrameLayout root;View library,inputs,settings;
 static final String[] PKGS={"com.ubdata.ublive","com.ubdata.hdtv","com.ubdata.uvod","com.google.android.youtube.tv"};
 static final String[] LABELS={"TTV","UPLIVE","UPMOVIE","YouTube"};
 @Before public void create(){
  previousLocale=java.util.Locale.getDefault();java.util.Locale.setDefault(java.util.Locale.TRADITIONAL_CHINESE);
  ReflectionHelpers.callStaticMethod(org.robolectric.shadows.ShadowWindowManagerGlobal.class,"setInTouchMode",ReflectionHelpers.ClassParameter.from(boolean.class,false));
  android.os.SystemClock.setCurrentTimeMillis(java.time.ZonedDateTime.of(2026,10,30,18,24,0,0,java.time.ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli());
  activity=Robolectric.buildActivity(Activity.class).setup().get();activity.setTheme(R.style.Theme_TvHome);root=new FrameLayout(activity);activity.setContentView(root);android.provider.Settings.System.putString(activity.getContentResolver(),android.provider.Settings.System.TIME_12_24,"24");root.getViewTreeObserver().addOnGlobalFocusChangeListener((before,after)->Shadows.shadowOf(activity).setCurrentFocus(after));
  activity.getSharedPreferences("cinema_lite",0).edit().clear().commit();activity.getSharedPreferences("last_opened",0).edit().clear().commit();
  LinearLayout old=new LinearLayout(activity);root.addView(old);library=button(R.id.btnLibraryTop,"所有 App");inputs=button(R.id.btnInputs,"輸入來源");settings=button(R.id.btnSettings,"設定");old.addView(library);old.addView(inputs);old.addView(settings);
  AppAdapter.TileListener listener=new AppAdapter.TileListener(){public void onTileFocused(int p,View v){}public boolean onTileVerticalKey(int p,int k){return false;}public void onLibraryRequested(){}public void onLibraryFocused(){}public void onTileLongPress(int p,View v){}public void onLibraryUp(){}public void onOpenInput(String id){}};
  adapter=new AppAdapter(activity,listener,4,com.gathin.tvhome.Config.load(activity));seed(4);
  // Real launch history fixture, newest first. Production never seeds recent items.
  for(int i=3;i>=0;i--)LastOpened.record(activity,PKGS[i]);
  home=new CinemaHome(activity,root,adapter,com.gathin.tvhome.Config.load(activity),library,inputs,settings);home.resume();layout(1920,1080);
 }
 TextView button(int id,String label){TextView t=new TextView(activity);t.setId(id);t.setText(label);return t;}
 void seed(int count){
  List<AppAdapter.Entry> items=ReflectionHelpers.getField(adapter,"items");items.clear();
  for(int i=0;i<count;i++){String pkg=i<4?PKGS[i]:"fixture.app"+i;String label=i<4?LABELS[i]:"A very long installed application name "+i;
   if((count==12||count==7)&&i>=4)label=new String[]{"Netflix","SmartTube","UPTV","音樂","相簿","媒體中心","瀏覽器","設定"}[i-4];
   android.content.pm.PackageInfo info=new android.content.pm.PackageInfo();info.packageName=pkg;info.applicationInfo=new android.content.pm.ApplicationInfo();info.applicationInfo.packageName=pkg;info.applicationInfo.enabled=true;info.applicationInfo.nonLocalizedLabel=label;Shadows.shadowOf(activity.getPackageManager()).installPackage(info);
   items.add(new AppAdapter.Entry(label,new ComponentName(pkg,".Main"),null,fixtureIcon(i)));
  }
  adapter.notifyDataSetChanged();
 }
 BitmapDrawable fixtureIcon(int i){
  Bitmap b=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);int[] colors={0xFFCB5E41,0xFF189E94,0xFF5C68CA,0xFFEB253D};p.setColor(colors[i%4]);c.drawRoundRect(7,17,137,127,26,26,p);p.setColor(0xFFFFFFFF);
  if(i==3){android.graphics.Path path=new android.graphics.Path();path.moveTo(60,45);path.lineTo(60,99);path.lineTo(103,72);path.close();c.drawPath(path,p);}else{p.setTextAlign(Paint.Align.CENTER);p.setTextSize(28);p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);c.drawText(new String[]{"TTV","LIVE","MOVIE"}[i%3],72,81,p);}
  return new BitmapDrawable(activity.getResources(),b);
 }
 void layout(int w,int h){
  for(int i=0;i<8;i++){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);if(i==0)home.scene.layoutDesign();}
 }

 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 boolean key(View v,int key){return v.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,key));}
 @After public void close(){home.close();activity.finish();java.util.Locale.setDefault(previousLocale);}
 @Test public void topContainsNetworkLibrarySettingsAndNoInputsOrSearch(){assertFalse(inputs.isShown());assertFalse(inputs.isFocusable());assertEquals(3,home.top.getChildCount());View search=home.top.getChildAt(2);assertTrue(library.requestFocus());assertTrue(key(library,22));assertSame(search,root.findFocus());assertTrue(key(search,21));assertSame(library,root.findFocus());}
 @Test public void topHiddenAndDisabledViewsAreSkipped(){View search=home.top.getChildAt(2);home.top.getChildAt(0).setVisibility(View.GONE);library.setVisibility(View.GONE);search.requestFocus();assertTrue(key(search,21));assertSame(search,root.findFocus());library.setVisibility(View.VISIBLE);search.setEnabled(false);library.requestFocus();assertTrue(key(library,22));assertSame(library,root.findFocus());}
 @Test public void upDownEdgesAndRapidDirectionsStayInDock(){home.options.mode("Static");home.homePressed();idle();layout(1920,1080);View first=home.dock.getChildAt(0);first.requestFocus();assertTrue(key(first,21));assertSame(first,root.findFocus());for(int i=0;i<30;i++){View v=root.findFocus();assertTrue(key(v,i%2==0?22:21));assertSame(home.dock,root.findFocus().getParent());}View v=root.findFocus();assertTrue(key(v,19));assertSame(home.top,root.findFocus().getParent());assertTrue(key(root.findFocus(),20));assertSame(home.dock,root.findFocus().getParent());}
 @Test public void largerCatalogueScrollsWithoutLosingFocus(){seed(22);layout(1920,1080);go(21);assertEquals(21,home.selectedIndex);assertEquals(16,home.firstVisibleIndex);assertEquals(7,home.dock.getChildCount());assertEquals(0,home.dockScroll.getScrollX());}
 @Test public void modeSettingsAndActualRecentHistoryPersist(){activity.getSharedPreferences("last_opened",0).edit().clear().commit();assertTrue(LastOpened.recentPackages(activity,6).isEmpty());LastOpened.record(activity,PKGS[0]);LastOpened.record(activity,PKGS[1]);home.resume();assertEquals(2,home.recent.getChildCount());home.options.mode("Lite");assertFalse(home.options.parallax());assertFalse(home.options.reflection());home.options.mode("Static");assertFalse(new CinemaSettings(activity).motion());home.options.wallpaper("oled");assertEquals("oled",new CinemaSettings(activity).wallpaper());home.options.set("recents",false);home.resume();assertEquals(View.GONE,home.recentScroll.getVisibility());}
 @Test public void mainActivityMountsAndClosesCinemaLayer(){
  org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
  CinemaHome cinema=ReflectionHelpers.getField(controller.get(),"cinemaHome");assertNotNull(cinema);assertNotNull(cinema.scene.getParent());
  controller.pause().stop().destroy();
 }
 @Test public void RTLAndHiddenTopUsePhysicalNeighbors(){home.scene.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layout(1920,1080);View search=home.top.getChildAt(2);search.requestFocus();assertTrue(key(search,22));assertSame(library,root.findFocus());assertTrue(key(library,21));assertSame(search,root.findFocus());search.setVisibility(View.GONE);library.requestFocus();assertTrue(key(library,21));assertSame(library,root.findFocus());}
 @Test public void cardDisabledAndRepeatedOkCannotDuplicateLaunch(){home.dock.getChildAt(1).setEnabled(false);View first=home.dock.getChildAt(0);first.requestFocus();assertTrue(key(first,22));assertSame(home.dock.getChildAt(2),root.findFocus());ReflectionHelpers.setField(adapter,"leanbackPackages",new java.util.HashSet<String>(java.util.Arrays.asList(PKGS)));first.requestFocus();first.performClick();first.performClick();android.content.Intent launched=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(launched);assertEquals(PKGS[0],launched.getComponent().getPackageName());assertTrue(launched.hasCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER));assertTrue((launched.getFlags()&android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)!=0);assertNull(Shadows.shadowOf(activity).getNextStartedActivity());assertTrue(LastOpened.get(activity,PKGS[0])>0);}
 @Test public void unfocusableControlsAreSkippedAndRecentsCanHide(){View search=home.top.getChildAt(2);search.setFocusable(false);library.requestFocus();assertTrue(key(library,22));assertSame(library,root.findFocus());LastOpened.record(activity,PKGS[0]);home.resume();layout(1920,1080);home.recent.getChildAt(0).requestFocus();home.options.set("recents",false);home.resume();idle();assertEquals(View.GONE,home.recentScroll.getVisibility());assertSame(home.dock,root.findFocus().getParent());}
 @Test public void rapidWallpaperReplacementKeepsOnlyLatestAndReleasesOnClose()throws Exception{for(int i=0;i<18;i++){home.options.wallpaper(i==17?"autumn":i%2==0?"lake":"oled");home.wallpapers.invalidate();layout(1920,1080);}for(int i=0;i<150&&!home.wallpapers.currentKey().startsWith("autumn@");i++){Thread.sleep(20);idle();}assertTrue(home.wallpapers.currentKey().startsWith("autumn@"));assertEquals(1,home.wallpapers.retainedBitmapCount());assertTrue(home.wallpapers.pendingDeliveryCount()<=1);home.close();idle();assertEquals(0,home.wallpapers.retainedBitmapCount());}
 @Test public void focusMotionSettlesAndStopsAtRequestedScale(){home.options.mode("Cinema");home.resume();layout(1920,1080);View card=home.dock.getChildAt(1);assertTrue(card.requestFocus());Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));assertEquals(CinemaPolicy.FOCUS_SCALE,card.getScaleX(),.001);float x=card.getScaleX();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));assertEquals(x,card.getScaleX(),0);home.options.mode("Static");home.resume();assertFalse(home.options.motion());}
 @Test public void longNamesEllipsizeWithinActualCardBounds(){seed(22);layout(1920,1080);go(21);layout(1920,1080);View last=root.findFocus();TextView name=(TextView)((LinearLayout)last).getChildAt(1);assertEquals(1,name.getLineCount());assertTrue(name.getLayout().getEllipsisCount(0)>0);assertTrue(name.getWidth()<=last.getWidth());}
 @Test public void haloActuallyFadesToTransparentAtItsBounds(){Bitmap b=Bitmap.createBitmap(600,200,Bitmap.Config.ARGB_8888);CinemaGlowDrawable d=new CinemaGlowDrawable(0xFF7E8BEB);d.setBounds(0,0,600,200);d.draw(new Canvas(b));assertTrue(android.graphics.Color.alpha(b.getPixel(300,100))>50);assertTrue(android.graphics.Color.alpha(b.getPixel(300,0))<3);assertTrue(android.graphics.Color.alpha(b.getPixel(0,100))<3);b.recycle();}
 @Test public void emptyTopDownIsBoundaryAndHomeSkipsUnavailableFirstCard(){seed(0);layout(1920,1080);library.requestFocus();assertTrue(key(library,20));assertSame(home.dock.getChildAt(0),root.findFocus());seed(4);layout(1920,1080);go(0);home.dock.getChildAt(0).setFocusable(false);home.homePressed();idle();assertSame(home.dock.getChildAt(1),root.findFocus());}
 @Test public void emptyAppsHaveRealEmptyStateAndReachableLibrary(){seed(0);home.homePressed();idle();assertTrue(library.requestFocus());assertEquals(1,home.dock.getChildCount());}
 @Test public void screenshotsAreActualNativeViewsAtBothResolutions()throws Exception{
  // Fixtures exist only in src/test: not packaged into the application or claimed as real installed apps.
  for(String p:PKGS)LastOpened.record(activity,p);home.resume();home.options.mode("Cinema");
  render(1920,1080,"cinema-native-1920x1080.png");render(1280,720,"cinema-native-1280x720.png");
 }
 String wallpaperKey(int w,int h){int[] size=CinemaMemoryPolicy.background(activity,w,h);return "@"+size[0]+"x"+size[1];}
 void render(int w,int h,String name)throws Exception{
  org.robolectric.shadows.ShadowDisplay display=Shadows.shadowOf(org.robolectric.shadows.ShadowDisplay.getDefaultDisplay());display.setWidth(w);display.setHeight(h);display.setRealWidth(w);display.setRealHeight(h);activity.getWindow().setLayout(w,h);
  layout(w,h);home.dock.getChildAt(2).requestFocus();
  for(int i=0;i<150&&!home.wallpapers.currentKey().endsWith(wallpaperKey(w,h));i++){Thread.sleep(20);idle();}
  assertTrue("wallpaper must decode for the current viewport",home.wallpapers.currentKey().endsWith(wallpaperKey(w,h)));
  layout(w,h);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));layout(w,h);
  for(View v:new View[]{home.top,home.dockScroll,home.recentScroll}){assertTrue(v.getLeft()>=0);assertTrue(v.getTop()>=0);assertTrue(v.getRight()<=w);assertTrue(v.getBottom()<=h);}
  View halo=ReflectionHelpers.getField(home,"focusLight");
  if(halo.getAlpha()>0){assertTrue(halo.getX()>=0);assertTrue(halo.getX()+halo.getWidth()<=w);assertTrue(halo.getY()>=0);assertTrue(halo.getY()+halo.getHeight()<=h);}
  Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));
  File out=new File(System.getProperty("cinema.outputDir", "build/cinema-native"));assertTrue(out.isDirectory()||out.mkdirs());
  try(FileOutputStream stream=new FileOutputStream(new File(out,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}bitmap.recycle();
 }
 @Test public void wallpaperFallbackAndTrimHaveBoundedRetention()throws Exception{
  home.options.wallpaper("content://missing/document/not-found");home.wallpapers.invalidate();layout(1920,1080);
  for(int i=0;i<100&&home.wallpapers.currentKey().isEmpty();i++){Thread.sleep(20);idle();}
  assertFalse(home.wallpapers.currentKey().isEmpty());assertEquals(1,home.wallpapers.retainedBitmapCount());home.pause();home.trim();assertEquals(0,home.wallpapers.retainedBitmapCount());
 }

 java.util.List<AppAdapter.Entry> panelFixtures(){
  List<AppAdapter.Entry> entries=new ArrayList<>(adapter.cinemaEntries());String[] names={"檔案管理器","相簿","音樂","瀏覽器","螢幕鏡像","媒體中心","工具箱","設定"};
  for(int i=0;i<names.length;i++)entries.add(new AppAdapter.Entry(names[i],new ComponentName("fixture.library"+i,".Main"),null,fixtureIcon(i)));return entries;
 }
 CinemaAppPanel panel(boolean search){home.openLibrary(search);home.libraryPanel.catalogue.close();home.libraryPanel.setApps(panelFixtures());idle();layout(1920,1080);return home.libraryPanel;}
 @Test public void libraryAndSearchHaveRealGridForAppsOutsideHome(){CinemaAppPanel panel=panel(false);assertEquals(12,panel.grid.getChildCount());assertEquals(5,home.dock.getChildCount());panel.query.setText("檔案");assertEquals(1,panel.grid.getChildCount());assertEquals("fixture.library0",((AppAdapter.Entry)panel.grid.getChildAt(0).getTag()).pkg);assertEquals(1,panel.grid.getChildAt(0) instanceof android.widget.FrameLayout?1:0);}
 @Test public void searchEmptyStateAndCaseInsensitiveMatching(){CinemaAppPanel panel=panel(true);assertSame(panel.query,root.findFocus());panel.query.setText("youtube");assertEquals(1,panel.grid.getChildCount());panel.query.setText("nothing matches");assertEquals(0,panel.grid.getChildCount());assertEquals(View.VISIBLE,panel.empty.getVisibility());assertTrue(key(panel.query,20));assertTrue(panel.findFocus()!=null);panel.query.setText("");assertEquals(12,panel.grid.getChildCount());}
 @Test public void galleryNavigationNeverEscapesAndBackRestoresAnchor(){CinemaAppPanel panel=panel(false);panel.focusFirst();layout(1920,1080);View first=panel.grid.getChildAt(0);first.requestFocus();assertTrue(key(first,21));assertSame(first,root.findFocus());assertTrue(key(first,20));assertSame(panel.grid.getChildAt(6),root.findFocus());assertTrue(key(root.findFocus(),20));assertSame(panel.grid.getChildAt(6),root.findFocus());assertTrue(key(root.findFocus(),19));assertSame(first,root.findFocus());assertTrue(panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_BACK)));assertTrue(panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));assertNull(home.libraryPanel);assertSame(library,root.findFocus());}
 @Test public void homeClosesLibraryWithoutChangingAnySystemRole(){panel(false);home.homePressed();idle();assertNull(home.libraryPanel);assertSame(home.dock,root.findFocus().getParent());}
 @Test public void settingsTogglesStayOpenAndBackReturnsToSettingsButton(){home.showSettings();idle();layout(1920,1080);CinemaSettingsPanel panel=home.settingsPanel;assertNotNull(panel);View toggle=panel.rows.findViewWithTag("effect:ambient");assertNotNull(toggle);boolean old=home.options.on("ambient");toggle.performClick();assertEquals(!old,home.options.on("ambient"));assertSame(panel,home.settingsPanel);panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));assertNull(home.settingsPanel);assertSame(settings,root.findFocus());}
 @Test public void focusHasPerspectiveButStaticFlattensAllCards(){home.options.mode("Cinema");home.resume();layout(1920,1080);home.dock.getChildAt(2).requestFocus();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(450));assertTrue(home.dock.getChildAt(0).getRotationY()<0);assertTrue(home.dock.getChildAt(3).getRotationY()>0);assertEquals(0,home.dock.getChildAt(2).getRotationY(),.01);home.options.mode("Static");home.resume();for(int i=0;i<home.dock.getChildCount();i++)assertEquals(0,home.dock.getChildAt(i).getRotationY(),.01);}
 @Test public void panelScreenshotsAtBothResolutions()throws Exception{
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){int w=size[0],h=size[1];
   CinemaAppPanel library=panel(false);layout(w,h);library.focusFirst();capture(w,h,"library-native-"+w+"x"+h+".png");home.closePanel(true);
   CinemaAppPanel search=panel(true);search.query.setText("UP");layout(w,h);capture(w,h,"search-native-"+w+"x"+h+".png");home.closePanel(true);
   home.showSettings();idle();layout(w,h);capture(w,h,"settings-native-"+w+"x"+h+".png");home.closePanel(true);
  }
 }
 void capture(int w,int h,String name)throws Exception{
  org.robolectric.shadows.ShadowDisplay display=Shadows.shadowOf(org.robolectric.shadows.ShadowDisplay.getDefaultDisplay());display.setWidth(w);display.setHeight(h);display.setRealWidth(w);display.setRealHeight(h);activity.getWindow().setLayout(w,h);
  layout(w,h);idle();layout(w,h);assertEquals(w,root.getWidth());
  if(home.libraryPanel!=null){CinemaAppPanel q=home.libraryPanel;assertEquals(w,q.getWidth());assertEquals(Math.round(76*w/1920f),q.query.getHeight());assertTrue(q.grid.getWidth()<=q.getWidth());}
  if(home.settingsPanel!=null){assertEquals(w,home.settingsPanel.getWidth());assertTrue(home.settingsPanel.scroll.getWidth()>w/2);assertTrue(home.settingsPanel.scroll.getWidth()<w*0.6f);}
  Bitmap bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(bitmap));File out=new File(System.getProperty("cinema.outputDir","build/cinema-native"));assertTrue(out.isDirectory()||out.mkdirs());try(FileOutputStream stream=new FileOutputStream(new File(out,name))){assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,stream));}bitmap.recycle();}

 @Test public void appActionsBackReturnsToTheSameTile(){CinemaAppPanel panel=panel(false);View target=panel.grid.getChildAt(5);target.requestFocus();assertTrue(target.performLongClick());idle();layout(1920,1080);assertNotSame(target,root.findFocus());panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));assertSame(target,root.findFocus());}
 @Test public void galleryFiltersUseHomeMembershipAndRealRecentOrder(){CinemaAppPanel panel=panel(false);panel.tabs.getChildAt(1).performClick();assertEquals(4,panel.grid.getChildCount());activity.getSharedPreferences("last_opened",0).edit().clear().putLong(PKGS[0],100).putLong(PKGS[1],200).commit();panel.tabs.getChildAt(2).performClick();assertEquals(2,panel.grid.getChildCount());assertEquals(PKGS[1],((AppAdapter.Entry)panel.grid.getChildAt(0).getTag()).pkg);}
 @Test public void realCatalogueQueriesNonHomeAppsAndPrefersLeanback()throws Exception{
  android.content.pm.PackageManager pm=activity.getPackageManager();org.robolectric.shadows.ShadowPackageManager shadow=Shadows.shadowOf(pm);
  android.content.pm.PackageInfo pkg=new android.content.pm.PackageInfo();pkg.packageName="fixture.not.on.home";pkg.applicationInfo=new android.content.pm.ApplicationInfo();pkg.applicationInfo.packageName=pkg.packageName;pkg.applicationInfo.enabled=true;pkg.applicationInfo.nonLocalizedLabel="Outside Home";shadow.installPackage(pkg);shadow.setApplicationIcon(pkg.packageName,fixtureIcon(0));
  android.content.pm.ResolveInfo tv=new android.content.pm.ResolveInfo();tv.nonLocalizedLabel="Outside Home";tv.activityInfo=new android.content.pm.ActivityInfo();tv.activityInfo.packageName=pkg.packageName;tv.activityInfo.name="fixture.not.on.home.Tv";tv.activityInfo.enabled=true;tv.activityInfo.exported=true;tv.activityInfo.applicationInfo=pkg.applicationInfo;
  android.content.pm.ResolveInfo phone=new android.content.pm.ResolveInfo();phone.nonLocalizedLabel="Outside Home";phone.activityInfo=new android.content.pm.ActivityInfo(tv.activityInfo);phone.activityInfo.name="fixture.not.on.home.Phone";
  shadow.addResolveInfoForIntent(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LEANBACK_LAUNCHER),tv);shadow.addResolveInfoForIntent(new android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER),phone);
  CinemaCatalogue catalogue=new CinemaCatalogue(activity);java.util.concurrent.atomic.AtomicReference<List<AppAdapter.Entry>> result=new java.util.concurrent.atomic.AtomicReference<>();java.util.concurrent.atomic.AtomicReference<String> error=new java.util.concurrent.atomic.AtomicReference<>();
  try{catalogue.load(java.util.Collections.emptySet(),result::set,error::set);for(int i=0;i<100&&result.get()==null&&error.get()==null;i++){Thread.sleep(15);idle();}assertNull(error.get());assertNotNull(result.get());List<AppAdapter.Entry> matches=new ArrayList<>();for(AppAdapter.Entry e:result.get())if(e.pkg.equals(pkg.packageName))matches.add(e);assertEquals(1,matches.size());assertEquals(tv.activityInfo.name,matches.get(0).component.getClassName());assertFalse(adapter.containsPackage(matches.get(0).pkg));}finally{catalogue.close();}
 }
 @Test public void galleryRTLArrowUsesActualVisualNeighbor(){CinemaAppPanel panel=panel(false);panel.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layout(1920,1080);View first=panel.grid.getChildAt(0);first.requestFocus();assertTrue(key(first,21));assertSame(panel.grid.getChildAt(1),root.findFocus());assertTrue(key(root.findFocus(),22));assertSame(first,root.findFocus());}
 @Test public void accentSheetBackRestoresCardAndDoesNotLaunch(){View card=home.dock.getChildAt(2);card.requestFocus();assertTrue(card.performLongClick());idle();layout(1920,1080);View focused=root.findFocus();assertNotSame(card,focused);assertTrue(root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK)));assertSame(card,root.findFocus());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
 @Test public void largerTightCardsRevealEdgesWithManyApps()throws Exception{
  seed(12);layout(1920,1080);home.dock.getChildAt(0).requestFocus();
  assertEquals(228,home.dock.getChildAt(0).getWidth());
  assertEquals(12,home.dock.getChildAt(1).getLeft()-home.dock.getChildAt(0).getRight());
  assertTrue(home.dockScroll.getClipChildren());
  for(int i=0;i<12;i++){go(i);View card=root.findFocus();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
   assertEquals(0,home.wallpaper.getTranslationX(),0);
   assertTrue(plateBounds(card).left-home.dockScroll.getScrollX()>=7);
   assertTrue(plateBounds(card).right-home.dockScroll.getScrollX()<=home.dockScroll.getWidth()-7);
  }
  home.dockScroll.scrollTo(0,0);render(1920,1080,"dock-many-native-1920x1080.png");render(1280,720,"dock-many-native-1280x720.png");
 }
 @Test public void topFocusKeepsFeaturedHierarchyWithoutStealingFocus()throws Exception{
  seed(12);layout(1920,1080);View featured=home.dock.getChildAt(2);featured.requestFocus();idle();
  assertTrue(library.requestFocus());Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
  assertSame(library,root.findFocus());assertEquals(1,featured.getScaleX(),.001f);
  View plate=((LinearLayout)featured).getChildAt(0);assertSame(ReflectionHelpers.getField(featured,"premium"),plate.getBackground());
  for(int i=0;i<150&&!home.wallpapers.currentKey().endsWith(wallpaperKey(1920,1080));i++){Thread.sleep(20);idle();}
  assertTrue(home.wallpapers.currentKey().endsWith(wallpaperKey(1920,1080)));
  capture(1920,1080,"top-focus-many-native-1920x1080.png");
  View next=home.dock.getChildAt(3);next.requestFocus();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
  assertEquals(1,featured.getScaleX(),.001f);assertEquals(CinemaPolicy.FOCUS_SCALE,next.getScaleX(),.001f);
 }
 @Test public void fixedDDoesNotBuildLegacyReflectionButDrawableStillFadesOut(){
  assertFalse(CinemaLighting.reflection(home.options));home.options.prefs.edit().putInt("renderer_stage",4).apply();home.options.set("reflection",true);home.resume();layout(1920,1080);
  go(1);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));
  java.util.Map<String,CinemaReflection> cache=ReflectionHelpers.getField(home,"reflectionCache");assertTrue(cache.isEmpty());assertEquals(3,CinemaLighting.stage(home.options));assertFalse(CinemaLighting.reflection(home.options));go(2);go(1);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));assertTrue(cache.isEmpty());
  CinemaReflection d=new CinemaReflection(fixtureIcon(3),0xFFFF0000);d.setBounds(0,0,96,72);Bitmap b=Bitmap.createBitmap(96,72,Bitmap.Config.ARGB_8888);d.draw(new Canvas(b));
  int top=0,bottom=0;for(int y=0;y<72;y++)for(int x=0;x<96;x++){int alpha=android.graphics.Color.alpha(b.getPixel(x,y));if(y<24)top=Math.max(top,alpha);if(y>=54)bottom=Math.max(bottom,alpha);}
  assertTrue(top>10);assertTrue(bottom<5);b.recycle();
 }
 @Test public void rapidFocusReusesFacesAndDoesNotWaitForAnimation(){
  View first=home.dock.getChildAt(0),second=home.dock.getChildAt(1);first.requestFocus();
  View plate=((LinearLayout)first).getChildAt(0);android.graphics.drawable.Drawable selected=plate.getBackground();
  for(int i=0;i<60;i++){assertTrue(key(root.findFocus(),i%2==0?22:21));assertSame(i%2==0?second:first,root.findFocus());}
  assertSame(selected,plate.getBackground());assertEquals(0,home.dockScroll.getScrollX());assertEquals(0,home.wallpaper.getTranslationX(),0);
 }
 @Test public void noHistoryShowsNoCardsThenOnlyRealHistory(){
  activity.getSharedPreferences("last_opened",0).edit().clear().commit();home.resume();assertEquals(0,home.recent.getChildCount());assertTrue(LastOpened.recentPackages(activity,6).isEmpty());
  TextView title=ReflectionHelpers.getField(home,"recentTitle");assertEquals("最近項目",title.getText());
  LastOpened.record(activity,PKGS[2]);home.resume();assertEquals(1,home.recent.getChildCount());assertEquals("最近項目",title.getText());
 }
 @Test public void backdropReferencesReleasedWhenInactiveAndTrimmed(){
  home.pause();home.trim();for(int i=0;i<home.recent.getChildCount();i++){android.widget.ImageView art=ReflectionHelpers.getField(home.recent.getChildAt(i),"art");assertNull(art.getDrawable());}
 }
 @Test public void overflowingDockEdgeRevealKeepsFocusedCardInsideViewport(){
  seed(22);layout(1920,1080);home.dock.getChildAt(0).requestFocus();
  for(int i=1;i<22;i++){assertTrue(key(root.findFocus(),22));View c=root.findFocus();layout(1920,1080);assertTrue(plateBounds(c).left>=0);assertTrue(plateBounds(c).right<=home.dockScroll.getWidth());}
  for(int i=20;i>=0;i--){assertTrue(key(root.findFocus(),21));assertEquals(i,home.selectedIndex);assertEquals(i,((Integer)ReflectionHelpers.getField(root.findFocus(),"appIndex")).intValue());}
 }

 @Test public void largeDockRTLNavigationStaysReachable(){
  seed(12);home.scene.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);layout(1920,1080);
  home.dock.getChildAt(0).requestFocus();
  for(int i=1;i<12;i++){assertTrue(key(root.findFocus(),21));assertEquals(i,home.selectedIndex);assertEquals(i,((Integer)ReflectionHelpers.getField(root.findFocus(),"appIndex")).intValue());}
  for(int i=10;i>=0;i--){assertTrue(key(root.findFocus(),22));assertEquals(i,home.selectedIndex);assertEquals(i,((Integer)ReflectionHelpers.getField(root.findFocus(),"appIndex")).intValue());}
 }

 void awaitWallpaper(String source)throws Exception{for(int i=0;i<150&&!home.wallpapers.currentKey().startsWith(source+"@");i++){Thread.sleep(20);idle();}assertTrue(home.wallpapers.currentKey(),home.wallpapers.currentKey().startsWith(source+"@"));}
 void setLocalTime(int hour,int minute){long millis=java.time.ZonedDateTime.of(2026,10,31,hour,minute,0,0,java.time.ZoneId.of("Asia/Taipei")).toInstant().toEpochMilli();ReflectionHelpers.setField(home.options,"wallClock",(java.util.function.LongSupplier)()->millis);}
 @Test public void schedulePreferencesManualOverrideAndPersistence(){
  assertTrue(home.options.autoWallpaper());assertEquals(360,home.options.dayMinute());assertEquals(1080,home.options.nightMinute());
  assertTrue(home.options.schedule(false,1177));assertEquals(1177,new CinemaSettings(activity).nightMinute());
  assertFalse(home.options.schedule(true,1177));assertEquals(360,home.options.dayMinute());
  home.options.wallpaper("sunrise");assertFalse(home.options.autoWallpaper());assertEquals("sunrise",home.options.effectiveWallpaper(1200));
  home.options.autoWallpaper(true);assertEquals("lake",home.options.effectiveWallpaper(1200));
 }
 @Test public void minuteBroadcastSwitchesAtEighteenAndResumeCatchesUp()throws Exception{
  java.util.TimeZone before=java.util.TimeZone.getDefault();try{java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Taipei"));
   setLocalTime(17,59);home.refreshWallpaper();awaitWallpaper("sunrise");Object focus=root.findFocus();
   setLocalTime(18,0);activity.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_TIME_TICK));idle();awaitWallpaper("lake");assertSame(focus,root.findFocus());
   home.pause();setLocalTime(7,0);activity.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_TIME_TICK));idle();assertTrue(home.wallpapers.currentKey().startsWith("lake@"));
   home.resume();awaitWallpaper("sunrise");assertNotNull(home.wallpapers.stage());
  }finally{java.util.TimeZone.setDefault(before);}
 }
 @Test public void timezoneBroadcastReevaluatesWithoutChangingStoredTimes()throws Exception{
  java.util.TimeZone before=java.util.TimeZone.getDefault();try{java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("Asia/Taipei"));setLocalTime(20,0);home.refreshWallpaper();awaitWallpaper("lake");
   java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));activity.sendBroadcast(new android.content.Intent(android.content.Intent.ACTION_TIMEZONE_CHANGED));idle();awaitWallpaper("sunrise");assertEquals(1080,home.options.nightMinute());
  }finally{java.util.TimeZone.setDefault(before);}
 }
 @Test public void scheduleEditorSavesOnlyOnSaveAndBackRestoresAnchor(){
  home.showPersonalization();idle();layout(1920,1080);CinemaPersonalizationPanel panel=home.personalization;View night=panel.findViewWithTag("schedule:night");night.requestFocus();night.performClick();idle();layout(1920,1080);
  View hour=panel.findViewWithTag("time:hour");hour.requestFocus();assertTrue(key(panel,19));assertEquals(1080,home.options.nightMinute());
  panel.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));assertEquals(1080,home.options.nightMinute());idle();assertEquals("schedule:night",root.findFocus().getTag());
  night=panel.findViewWithTag("schedule:night");night.performClick();idle();layout(1920,1080);hour=panel.findViewWithTag("time:hour");hour.requestFocus();key(panel,19);key(panel,22);key(panel,19);panel.findViewWithTag("time:save").performClick();
  assertEquals(1141,home.options.nightMinute());idle();assertEquals("schedule:night",root.findFocus().getTag());assertNull(panel.findViewWithTag("time:save"));
 }
 @Test public void editorRejectsEqualTimesAndMinuteWrapDoesNotChangeHour(){
  home.options.schedule(true,1079);home.showPersonalization();idle();layout(1920,1080);CinemaPersonalizationPanel panel=home.personalization;panel.findViewWithTag("schedule:day").performClick();idle();layout(1920,1080);
  View hour=panel.findViewWithTag("time:hour"),minute=panel.findViewWithTag("time:minute");minute.requestFocus();key(panel,19);assertEquals("00 分",((TextView)minute).getText());hour.requestFocus();key(panel,19);panel.findViewWithTag("time:save").performClick();
  assertEquals(1079,home.options.dayMinute());assertNotNull(panel.findViewWithTag("time:save"));
 }
 @Test public void manualSunriseButtonTurnsOffAutomaticMode(){home.showPersonalization();idle();layout(1920,1080);home.personalization.findViewWithTag("display:SUNRISE").performClick();assertFalse(home.options.autoWallpaper());assertEquals("sunrise",home.options.effectiveWallpaper(1200));home.personalization.findViewWithTag("display:AUTO").performClick();assertTrue(home.options.autoWallpaper());}
 @Test public void seasonalCardsUseFourSharedAssetsAndNoDoubleFocusHighlight()throws Exception{
  for(int i=0;i<100&&home.seasons.count()!=4;i++){Thread.sleep(20);idle();}assertEquals(4,home.seasons.count());
  java.util.Set<Bitmap> images=new java.util.HashSet<>();for(int i=0;i<4;i++){View card=home.recent.getChildAt(i);android.widget.ImageView art=ReflectionHelpers.getField(card,"art");assertNotNull(art.getDrawable());images.add(((BitmapDrawable)art.getDrawable()).getBitmap());TextView label=ReflectionHelpers.getField(card,"name"),meta=ReflectionHelpers.getField(card,"meta");assertEquals(View.GONE,meta.getVisibility());assertEquals("",meta.getText().toString());}
  assertEquals(4,images.size());assertFalse(home.dock.getChildAt(0).getDefaultFocusHighlightEnabled());
  home.pause();home.trim();assertEquals(0,home.seasons.count());assertNull(home.wallpapers.stage());
 }
 @Test public void sunriseSunsetAndTimeEditorPreviews()throws Exception{
  seed(7);for(String key:new String[]{"sunrise","lake"}){android.widget.TextClock displayClock=ReflectionHelpers.getField(home,"time");displayClock.setFormat24Hour(key.equals("sunrise")?"\'07:30\'":"\'18:24\'");home.options.wallpaper(key);home.refreshWallpaper();awaitWallpaper(key);
   render(1920,1080,key+"-seasonal-native-1920x1080.png");render(1280,720,key+"-seasonal-native-1280x720.png");}
  home.showPersonalization();idle();layout(1920,1080);home.personalization.findViewWithTag("schedule:night").performClick();idle();layout(1920,1080);capture(1920,1080,"time-editor-native-1920x1080.png");capture(1280,720,"time-editor-native-1280x720.png");
 }

 @Test public void returningToVisibleWallpaperCancelsStalePendingChoice()throws Exception{
  home.options.wallpaper("lake");home.refreshWallpaper();awaitWallpaper("lake");
  home.wallpapers.load("sunrise",1920,1080);home.wallpapers.load("lake",1920,1080);
  for(int i=0;i<30;i++){Thread.sleep(10);idle();}assertTrue(home.wallpapers.currentKey().startsWith("lake@"));
 }


 android.graphics.RectF plateBounds(View card){
  View plate=((LinearLayout)card).getChildAt(0);
  android.graphics.RectF b=new android.graphics.RectF(0,0,card.getWidth(),plate.getHeight());
  card.getMatrix().mapRect(b);b.offset(card.getLeft(),card.getTop());return b;
 }
 List<View> physicalCards(){List<View> cards=new ArrayList<>();for(int i=0;i<home.dock.getChildCount();i++)cards.add(home.dock.getChildAt(i));cards.sort((a,b)->Integer.compare(a.getLeft(),b.getLeft()));return cards;}
 void checkRenderedGaps(int width,int height){
  List<View> cards=physicalCards();float unit=Math.min(width/1920f,height/1080f);
  for(int i=1;i<cards.size();i++){
   float gap=plateBounds(cards.get(i)).left-plateBounds(cards.get(i-1)).right;
   assertEquals("projected plate gap at pair "+i,14*unit,gap,.08f);
  }
 }
 @Test public void carouselHasSpecifiedAnglesAndScaleAtEveryDistance(){
  seed(11);home.options.mode("Cinema");home.resume();layout(1920,1080);
  go(5);layout(1920,1080);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
  float[] angles={-8,-5,-3,0,3,5,8};
  float[] scales={.93f,.96f,1,1.35f,1,.96f,.93f};
  List<View> cards=physicalCards();
  for(int i=0;i<cards.size();i++){assertEquals(angles[i],cards.get(i).getRotationY(),.001);assertEquals(scales[i],cards.get(i).getScaleX(),.001);assertTrue(cards.get(i).getTranslationZ()<=4);}
  go(0);layout(1920,1080);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));assertEquals(10,home.dock.getChildAt(6).getRotationY(),.001);
  checkRenderedGaps(1920,1080);
 }
 @Test public void carouselProjectionStaysTightAcrossSizesRTLAndInterruptedAnimations()throws Exception{
  seed(12);home.options.mode("Cinema");home.resume();
  for(int direction:new int[]{View.LAYOUT_DIRECTION_LTR,View.LAYOUT_DIRECTION_RTL}){
   home.dock.setLayoutDirection(direction);
   for(int[] size:new int[][]{{1672,940},{1920,1080},{1280,720}}){
    org.robolectric.shadows.ShadowDisplay display=Shadows.shadowOf(org.robolectric.shadows.ShadowDisplay.getDefaultDisplay());display.setWidth(size[0]);display.setHeight(size[1]);display.setRealWidth(size[0]);display.setRealHeight(size[1]);activity.getWindow().setLayout(size[0],size[1]);
    layout(size[0],size[1]);
    for(int index:new int[]{0,2,3,8,11,4,1}){
     go(index);layout(size[0],size[1]);
     for(int frame=0;frame<5;frame++){
      Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));checkRenderedGaps(size[0],size[1]);
     }
    }
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));checkRenderedGaps(size[0],size[1]);
    for(View card:physicalCards()){float angle=card.getRotationY();if(card.getLeft()<root.findFocus().getLeft())assertTrue(angle<=0);else if(card.getLeft()>root.findFocus().getLeft())assertTrue(angle>=0);}
   }
  }
 }
 @Test public void carouselReferenceFocusProducesMeasuredPreview()throws Exception{
  seed(7);home.options.wallpaper("lake");home.options.mode("Cinema");home.resume();
  StringBuilder report=new StringBuilder("width,height,card,rotation,scale,left,right,top,bottom,gap_from_previous\n");
  for(int[] size:new int[][]{{1672,940},{1920,1080},{1280,720}}){
   render(size[0],size[1],"carousel-reference-"+size[0]+"x"+size[1]+".png");checkRenderedGaps(size[0],size[1]);
   float previous=Float.NaN;int i=0;
   for(View card:physicalCards()){
    android.graphics.RectF b=plateBounds(card);float offset=home.dockScroll.getLeft()-home.dockScroll.getScrollX();
    report.append(size[0]+","+size[1]+","+(i++)+","+card.getRotationY()+","+card.getScaleX()+","+(b.left+offset)+","+(b.right+offset)+","+(b.top+home.dockScroll.getTop())+","+(b.bottom+home.dockScroll.getTop())+","+(b.left-previous)+"\n");previous=b.right;
    assertTrue(b.left+offset>=0);assertTrue(b.right+offset<=size[0]);
   }
  }
  File out=new File(System.getProperty("cinema.outputDir","build/cinema-native"));java.nio.file.Files.write(new File(out,"carousel-projected-edges.csv").toPath(),report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void carouselBottomEdgesAlignAndOnlyEdgeFocusScrolls(){
  seed(7);home.options.mode("Cinema");home.resume();layout(1920,1080);home.dockScroll.scrollTo(0,0);
  for(int i:new int[]{2,3,2}){home.dock.getChildAt(i).requestFocus();Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));assertEquals(0,home.dockScroll.getScrollX());}
  float bottom=plateBounds(home.dock.getChildAt(2)).bottom;
  for(View card:physicalCards())assertEquals(bottom,plateBounds(card).bottom,.1);
 }

 void go(int index){
  if(root.findFocus()==null||root.findFocus().getParent()!=home.dock)home.restoreFocus(0);
  int guard=0;
  while(home.selectedIndex!=index){assertTrue("stuck selected="+home.selectedIndex+" goal="+index,++guard<100);int step=index>home.selectedIndex?1:-1;boolean rtl=home.dock.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL;assertTrue("key rejected selected="+home.selectedIndex+" goal="+index,key(root.findFocus(),(step>0)^rtl?22:21));}
 }
 @Test public void sevenWindowMatrixHasOnlyValidChildrenAndExactIndexRange()throws Exception{
  StringBuilder report=new StringBuilder("width,total,selected,first,visible,child_count\n");
  for(int width:new int[]{1920,1280}){
   int height=width*9/16;
   org.robolectric.shadows.ShadowDisplay display=Shadows.shadowOf(org.robolectric.shadows.ShadowDisplay.getDefaultDisplay());display.setWidth(width);display.setHeight(height);activity.getWindow().setLayout(width,height);
   for(int count:new int[]{1,3,6,7,8,22}){
    seed(count-1);layout(width,height);home.restoreFocus(0);go(0);
    for(int index=0;index<count;index++){
     go(index);layout(width,height);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));
     int visible=Math.min(7,count),first=Math.max(0,Math.min(index-3,count-visible));
     assertEquals(visible,home.dock.getChildCount());assertEquals(first,home.firstVisibleIndex);assertEquals(visible,home.visibleCount);
     int selectedCount=0;
     for(int i=0;i<visible;i++){
      View card=home.dock.getChildAt(i);assertEquals(first+i,((Integer)ReflectionHelpers.getField(card,"appIndex")).intValue());
      android.graphics.RectF b=plateBounds(card);assertTrue(b.left>=0);assertTrue(b.right<=home.dockScroll.getWidth());
      if(first+i==index){selectedCount++;assertEquals(1.35f,card.getScaleX(),.001);assertEquals(1f,(Float)ReflectionHelpers.getField(card,"focusLevel"),.001);assertSame(card,root.findFocus());}
      else assertEquals(0f,(Float)ReflectionHelpers.getField(card,"focusLevel"),.001);
      // Include glow spill and reflection in the same matrix; decorations need their own safe space.
      View plate=((LinearLayout)card).getChildAt(0);float h=plate.getHeight(),w=card.getWidth();
      android.graphics.RectF fx=new android.graphics.RectF(-w*.11f,-12*width/1920f,w*1.11f,h*1.5f);
      card.getMatrix().mapRect(fx);fx.offset(card.getLeft(),card.getTop());
      assertTrue("left effect clipped "+fx,fx.left>=-1);assertTrue("right effect clipped "+fx,fx.right<=home.dockScroll.getWidth()+1);assertTrue(fx.top>=0);assertTrue("floor clipped "+fx,fx.bottom<=home.dockScroll.getHeight()+1);
     }
     assertEquals(1,selectedCount);checkRenderedGaps(width,height);
     report.append(width+","+count+","+index+","+first+","+visible+","+home.dock.getChildCount()+"\n");
    }
    assertTrue(key(root.findFocus(),22));assertEquals(count-1,home.selectedIndex);
    for(int index=count-1;index>=0;index--)go(index);
    assertTrue(key(root.findFocus(),21));assertEquals(0,home.selectedIndex);
   }
  }
  File out=new File(System.getProperty("cinema.outputDir","build/cinema-native"));out.mkdirs();java.nio.file.Files.write(new File(out,"seven-window-matrix.csv").toPath(),report.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void windowReuseKeepsIdentityAndOkDuringMotionLaunchesModelSelection(){
  seed(22);layout(1920,1080);go(3);View retained=root.findFocus();go(4);assertSame(retained,home.dock.getChildAt(2));
  for(int i=0;i<5;i++){go(21);go(0);}go(19);
  String expected="fixture.app19";
  ReflectionHelpers.setField(adapter,"leanbackPackages",new java.util.HashSet<String>(java.util.Arrays.asList(expected)));
  assertTrue(root.findFocus().performClick());android.content.Intent intent=Shadows.shadowOf(activity).getNextStartedActivity();assertNotNull(intent);assertEquals(expected,intent.getComponent().getPackageName());
  home.pause();home.resume();layout(1920,1080);home.restoreFocus(0);assertEquals(19,home.selectedIndex);assertEquals(16,home.firstVisibleIndex);
  assertEquals(19,((Integer)ReflectionHelpers.getField(root.findFocus(),"appIndex")).intValue());
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));assertEquals(1.35f,root.findFocus().getScaleX(),.001);
 }
 @Test public void paletteIsStableThroughFocusAndLightsConvergeWithoutLoop(){
  seed(7);layout(1920,1080);CinemaPalette palette=ReflectionHelpers.getField(home,"palette");int before=palette.samples;
  go(0);for(int i=0;i<10;i++){go(6);go(0);}go(4);
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));assertEquals(before,palette.samples);
  for(View card:physicalCards()){int i=ReflectionHelpers.getField(card,"appIndex");assertEquals(i==4?1f:0f,(Float)ReflectionHelpers.getField(card,"focusLevel"),.001);}
  android.animation.ValueAnimator animation=ReflectionHelpers.getField(home,"carouselAnimator");assertTrue(animation==null||!animation.isRunning());
 }
 @Test public void premiumFirstMiddleLastPreviewsHaveNoEighthCard()throws Exception{
  seed(22);home.options.wallpaper("lake");home.resume();awaitWallpaper("lake");
  for(int width:new int[]{1920,1280}){
   int height=width*9/16;org.robolectric.shadows.ShadowDisplay d=Shadows.shadowOf(org.robolectric.shadows.ShadowDisplay.getDefaultDisplay());d.setWidth(width);d.setHeight(height);activity.getWindow().setLayout(width,height);layout(width,height);
   for(int selected:new int[]{0,10,21}){go(selected);layout(width,height);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));assertEquals(7,home.dock.getChildCount());capture(width,height,"premium-window-"+selected+"-"+width+"x"+height+".png");}
  }
 }

 @Test public void windowShiftKeepsOneBoundedFocusAnimationInsteadOfSnapping(){
  org.robolectric.shadows.ShadowChoreographer.setPaused(true);org.robolectric.shadows.ShadowChoreographer.setFrameDelay(java.time.Duration.ofMillis(16));
  seed(15);home.options.mode("Cinema");home.resume();layout(1920,1080);go(3);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
  go(4);layout(1920,1080);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(80));
  android.animation.ValueAnimator a=ReflectionHelpers.getField(home,"carouselAnimator");assertNotNull("motion="+home.options.motion()+" enabled="+android.animation.ValueAnimator.areAnimatorsEnabled()+" dims="+home.scene.getWidth()+"x"+home.scene.getHeight(),a);assertTrue("scale="+root.findFocus().getScaleX()+" duration="+a.getDuration(),a.isRunning());
  a.setCurrentPlayTime(80);assertTrue("intermediate scale="+root.findFocus().getScaleX(),root.findFocus().getScaleX()>1);assertTrue(root.findFocus().getScaleX()<1.35f);
  checkRenderedGaps(1920,1080);
  go(5);go(6);go(5);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
  android.animation.ValueAnimator last=ReflectionHelpers.getField(home,"carouselAnimator");if(last!=null)last.end();assertEquals(5,home.selectedIndex);assertEquals(1.35f,root.findFocus().getScaleX(),.001f);
 }
 @Test public void transparentCarouselMarginsContainNoClippedDecorativePixels(){
  seed(22);home.options.mode("Cinema");home.resume();layout(1920,1080);
  for(int index:new int[]{0,10,21}){
   go(index);layout(1920,1080);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
   Bitmap b=Bitmap.createBitmap(home.dockScroll.getWidth(),home.dockScroll.getHeight(),Bitmap.Config.ARGB_8888);home.dockScroll.draw(new Canvas(b));
   for(int y=0;y<b.getHeight();y++)for(int x:new int[]{0,1,b.getWidth()-2,b.getWidth()-1})assertEquals("edge residue x="+x+" y="+y,0,android.graphics.Color.alpha(b.getPixel(x,y)));
   b.recycle();
  }
 }

 @Test public void reusableSlotsAllocateNothingOnWarmDirectionalPath(){
  seed(22);home.options.mode("Cinema");home.options.prefs.edit().putInt("renderer_stage",3).apply();home.resume();layout(1920,1080);go(0);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
  java.util.Set<View> slots=new java.util.HashSet<>();for(View c:physicalCards())slots.add(c);
  CinemaProbe.enabled=true;CinemaProbe.reset();try{
   for(int i=0;i<4;i++){go(21);go(0);}assertEquals(0,CinemaProbe.cards);assertEquals(0,CinemaProbe.reflections);assertEquals(0,CinemaProbe.surfaces);assertEquals(0,CinemaProbe.animators);assertEquals(0,CinemaProbe.rowLayouts);
   for(View c:physicalCards())assertTrue(slots.contains(c));assertEquals(7,home.dock.getChildCount());
  }finally{CinemaProbe.enabled=false;}
 }
 @Test public void rendererIsFixedDDespiteSavedStagesAndResetButOtherTogglesStillWork(){
  assertEquals(3,CinemaLighting.stage(home.options));home.showSettings();layout(1920,1080);assertNull(home.settingsPanel.findViewWithTag("renderer:stage"));
  for(int legacy:new int[]{4,0,1,2,3,-1,999}){home.options.prefs.edit().putInt("renderer_stage",legacy).commit();home.resume();assertEquals(3,CinemaLighting.stage(new CinemaSettings(activity)));assertTrue(CinemaLighting.border(home.options));assertTrue(CinemaLighting.pool(home.options));assertFalse(CinemaLighting.reflection(home.options));}
  home.options.resetEffects();assertEquals(3,CinemaLighting.stage(home.options));home.options.set("ambient",false);assertFalse(CinemaLighting.border(home.options));assertFalse(CinemaLighting.pool(home.options));home.options.set("focus",false);assertFalse(CinemaLighting.animation(home.options));home.closePanel(true);
 }
 @Test public void sharedContactIsConcentratedAndPoolFadesWithoutHardEdges(){
  CinemaFocusLight light=new CinemaFocusLight(activity);light.layout(0,0,800,500);light.prepare(0xFF2299DD);light.select(0xFF2299DD);light.position(new android.graphics.RectF(250,100,550,300),1,1,true);
  Bitmap b=Bitmap.createBitmap(800,500,Bitmap.Config.ARGB_8888);light.draw(new Canvas(b));
  int core=android.graphics.Color.alpha(b.getPixel(400,301)),side=android.graphics.Color.alpha(b.getPixel(250,301)),floor=android.graphics.Color.alpha(b.getPixel(400,330));assertTrue(core>200);assertTrue(core>side*2);assertTrue(floor>35);
  for(int y=0;y<500;y++)assertEquals(0,android.graphics.Color.alpha(b.getPixel(0,y)));assertEquals(0,android.graphics.Color.alpha(b.getPixel(400,499)));b.recycle();
 }
 @Test public void rendererParametersStayWithinSafeRanges(){assertTrue(CinemaLighting.CONTACT_LIGHT_WIDTH_RATIO>=.45&&CinemaLighting.CONTACT_LIGHT_WIDTH_RATIO<=.65);assertTrue(CinemaLighting.GLOW_WIDTH_RATIO>=1.5&&CinemaLighting.GLOW_WIDTH_RATIO<=1.8);assertTrue(CinemaLighting.GLOW_HEIGHT_RATIO>=.65&&CinemaLighting.GLOW_HEIGHT_RATIO<=.85);assertEquals(200,CinemaLighting.FOCUS_ANIMATION_MS);assertEquals(220,CinemaLighting.GLOW_TRANSITION_MS);assertTrue(CinemaLighting.REFLECTION_ALPHA<.35);}
 @Test public void lightingFocusPreviewsAndProfiles()throws Exception{
  seed(7);home.options.wallpaper("sunrise");home.options.mode("Cinema");home.resume();awaitWallpaper("sunrise");layout(1920,1080);
  for(int selected:new int[]{0,1,2,3,4}){go(selected);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(500));capture(1920,1080,"lighting-focus-"+selected+"-1920x1080.png");}
  go(3);assertEquals(3,CinemaLighting.stage(home.options));capture(1920,1080,"renderer-fixed-D-1920x1080.png");
 }
 @Test public void glassHasLocalizedHighlightAndTintedDepth(){
  CinemaCardSurface surface=new CinemaCardSurface(0xFFEE1933);surface.setBounds(0,0,228,174);surface.focus(1);
  Bitmap b=Bitmap.createBitmap(228,174,Bitmap.Config.ARGB_8888);surface.draw(new Canvas(b));
  int highlight=b.getPixel(30,20),middle=b.getPixel(114,90),bottom=b.getPixel(114,160);
  assertTrue(ColorValue.luma(highlight)>ColorValue.luma(middle)+35);
  assertTrue(android.graphics.Color.red(bottom)>android.graphics.Color.blue(bottom)+25);
  assertEquals(0,android.graphics.Color.alpha(b.getPixel(0,0)));b.recycle();
 }
 private static class ColorValue{static int luma(int c){return (android.graphics.Color.red(c)*3+android.graphics.Color.green(c)*6+android.graphics.Color.blue(c))/10;}}
 @Test public void floorKeepsDistinctNeighborColorsAndWhiteHotCore(){
  CinemaFocusLight light=new CinemaFocusLight(activity);light.layout(0,0,1200,500);
  light.prepare(0xFFFF1020);light.prepare(0xFF10A0FF);light.select(0xFFFF1020);light.beginNeighbors();
  light.neighbor(new android.graphics.RectF(120,130,330,300),0xFF10A0FF,0);
  light.position(new android.graphics.RectF(500,100,800,300),1,1,true);
  Bitmap b=Bitmap.createBitmap(1200,500,Bitmap.Config.ARGB_8888);light.draw(new Canvas(b));
  int core=b.getPixel(650,301),pool=b.getPixel(650,340),neighbor=b.getPixel(225,320);
  assertTrue(android.graphics.Color.green(core)>175);assertTrue(android.graphics.Color.blue(core)>175);
  assertTrue(android.graphics.Color.red(pool)>android.graphics.Color.blue(pool)+100);
  assertTrue(android.graphics.Color.blue(neighbor)>android.graphics.Color.red(neighbor)+100);
  assertTrue(android.graphics.Color.alpha(pool)>android.graphics.Color.alpha(neighbor));
  assertEquals(0,android.graphics.Color.alpha(b.getPixel(650,499)));b.recycle();
 }
 @Test public void opticalLayerHasNoHardClippedFloorAtViewportEdges(){
  seed(22);home.options.mode("Cinema");home.resume();layout(1920,1080);
  View light=ReflectionHelpers.getField(home,"focusLight");
  for(int index:new int[]{0,3,10,21}){
   go(index);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
   Bitmap b=Bitmap.createBitmap(light.getWidth(),light.getHeight(),Bitmap.Config.ARGB_8888);light.draw(new Canvas(b));
   for(int x=0;x<b.getWidth();x++)assertTrue("bottom clipping",android.graphics.Color.alpha(b.getPixel(x,b.getHeight()-1))<3);
   for(int y=0;y<b.getHeight();y++)for(int x:new int[]{0,b.getWidth()-1})assertTrue("side clipping",android.graphics.Color.alpha(b.getPixel(x,y))<3);
   b.recycle();
  }
 }
 // Framework state restoration requests focus without a user DPAD event.
 void frameworkRestoreSelection(int index){ReflectionHelpers.callInstanceMethod(home,"selectDock",ReflectionHelpers.ClassParameter.from(int.class,index),ReflectionHelpers.ClassParameter.from(boolean.class,true));}
 @Test public void firstWindowEntryOverridesFrameworkRestoredLastCard(){
  seed(12);layout(1920,1080);frameworkRestoreSelection(11);home.windowFocusChanged(true);
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));layout(1920,1080);
  assertEquals(0,home.selectedIndex);assertEquals(0,home.firstVisibleIndex);assertEquals(0,((Integer)ReflectionHelpers.getField(root.findFocus(),"appIndex")).intValue());
 }
 @Test public void laterWindowReturnsKeepTheAppTheUserChose(){
  home.windowFocusChanged(true);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
  go(3);home.windowFocusChanged(false);home.pause();home.resume();home.windowFocusChanged(true);
  Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));assertEquals(3,home.selectedIndex);
  home.homePressed();idle();assertEquals(0,home.selectedIndex);
 }
 @Test public void firstWindowWaitsForRealCatalogueBeforeChoosingFirstApp(){
  home.close();seed(0);home=new CinemaHome(activity,root,adapter,com.gathin.tvhome.Config.load(activity),library,inputs,settings);home.resume();layout(1920,1080);
  home.windowFocusChanged(true);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
  seed(12);layout(1920,1080);frameworkRestoreSelection(11);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));
  assertEquals(0,home.selectedIndex);assertEquals(0,home.firstVisibleIndex);
 }
 @Test public void touchModeEntryAndHomeKeepRealFocusOnFirstApp(){
  seed(12);layout(1920,1080);
  ReflectionHelpers.callStaticMethod(org.robolectric.shadows.ShadowWindowManagerGlobal.class,"setInTouchMode",ReflectionHelpers.ClassParameter.from(boolean.class,true));
  Object viewRoot=ReflectionHelpers.callInstanceMethod(root,"getViewRootImpl");
  ReflectionHelpers.callInstanceMethod(viewRoot,"ensureTouchModeLocally",ReflectionHelpers.ClassParameter.from(boolean.class,true));
  home.windowFocusChanged(true);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(100));layout(1920,1080);
  assertTrue(root.isInTouchMode());assertEquals(0,home.selectedIndex);assertSame(home.dock.getChildAt(0),root.findFocus());
  go(11);home.homePressed();idle();assertEquals(0,home.selectedIndex);assertEquals(0,home.firstVisibleIndex);assertSame(home.dock.getChildAt(0),root.findFocus());
 }
}
