package com.gathin.tvhome;
import android.content.Intent;
import android.view.*;
import org.junit.*;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaUiSoundsTest {
 CinemaViewTest t;CinemaUiSounds sounds;FakePlayer player;long[] clock={1000};
 static final class FakePlayer implements CinemaUiSounds.Player {
  List<Integer> cues=new ArrayList<>();List<Float> gains=new ArrayList<>();boolean loaded=true;int stops,closes;
  public boolean ready(int cue){return loaded;}public void play(int cue,float gain){cues.add(cue);gains.add(gain);}public void stop(){stops++;}public void close(){closes++;}
 }
 @Before public void setup(){t=new CinemaViewTest();t.create();t.home.sounds.close();player=new FakePlayer();sounds=new CinemaUiSounds(t.home.scene,t.home.options,player,()->clock[0]);ReflectionHelpers.setField(t.home,"sounds",sounds);sounds.resume();t.home.homePressed();t.idle();}
 @After public void close(){t.close();}
 void key(int code){t.root.dispatchKeyEvent(new KeyEvent(0,code));t.root.dispatchKeyEvent(new KeyEvent(1,code));t.idle();clock[0]+=400;}
 @Test public void acceptedMovePlaysOnceButRealBoundaryStaysSilent(){
  key(21);assertTrue(player.cues.isEmpty());key(22);assertEquals(Arrays.asList(CinemaUiSounds.MOVE),player.cues);assertEquals(1,t.home.selectedIndex);assertEquals(.65f,player.gains.get(0),.0001);
 }
 @Test public void rapidDpadIsRateLimitedWithoutDelayingNavigationOrQueueingAudio(){
  t.seed(22);t.layout(1920,1080);t.go(0);t.idle();
  for(int i=0;i<40;i++){int code=i%2==0?22:21;t.root.dispatchKeyEvent(new KeyEvent(0,code));t.root.dispatchKeyEvent(new KeyEvent(1,code));clock[0]+=10;}
  assertEquals(0,t.home.selectedIndex);assertTrue(player.cues.size()>0);assertTrue(player.cues.size()<=3);int count=player.cues.size();t.idle();clock[0]+=10000;t.idle();assertEquals(count,player.cues.size());
 }
 @Test public void recycledCarouselViewsStillEmitOneCuePerSuccessfulStep(){
  t.seed(22);t.layout(1920,1080);t.go(0);t.idle();for(int i=0;i<12;i++)key(22);assertEquals(12,t.home.selectedIndex);assertEquals(12,player.cues.size());
 }
 @Test public void okKeyRepeatEmitsOnlyOneConfirmationAndPreservesLaunch(){
  t.root.dispatchKeyEvent(new KeyEvent(0,23));t.root.dispatchKeyEvent(new KeyEvent(0,1,KeyEvent.ACTION_DOWN,23,1));t.root.dispatchKeyEvent(new KeyEvent(0,2,KeyEvent.ACTION_DOWN,23,2));t.root.dispatchKeyEvent(new KeyEvent(1,23));
  assertEquals(Arrays.asList(CinemaUiSounds.SELECT),player.cues);Intent intent=Shadows.shadowOf(t.activity).getNextStartedActivity();assertNotNull(intent);assertEquals(CinemaViewTest.PKGS[0],intent.getComponent().getPackageName());assertEquals(.85f,player.gains.get(0),.0001);
 }
 @Test public void programmaticRestoreAndResumeDoNotMakeMovementSounds(){
  t.home.dock.getChildAt(2).requestFocus();t.home.homePressed();t.idle();t.home.pause();t.home.resume();t.idle();assertTrue(player.cues.isEmpty());key(22);int count=player.cues.size();t.home.dock.getChildAt(0).requestFocus();t.idle();assertEquals(count,player.cues.size());
 }
 @Test public void topRecentAndPersonalizationAreWiredToTheSameSoundHost(){
  key(20);assertSame(t.home.recent,t.root.findFocus().getParent());key(22);assertEquals(2,player.cues.size());key(19);key(19);assertSame(t.home.top,t.root.findFocus().getParent());
  t.home.showPersonalization();t.layout(1920,1080);t.idle();int before=player.cues.size();key(20);assertEquals("wallpaper:day",t.root.findFocus().getTag());assertEquals(before+1,player.cues.size());
  t.root.findViewWithTag("glow:50").performClick();assertEquals(Integer.valueOf(CinemaUiSounds.SELECT),player.cues.get(player.cues.size()-1));assertEquals(50,t.home.options.glow());
 }
 @Test public void hiddenLegacySettingsCannotMuteOrAttenuateNewSoundProfile(){
  android.media.AudioManager audio=(android.media.AudioManager)t.activity.getSystemService(android.content.Context.AUDIO_SERVICE);int original=audio.getStreamVolume(android.media.AudioManager.STREAM_SYSTEM);
  t.home.options.prefs.edit().putBoolean("ui_sound",false).putInt("ui_sound_volume",0).commit();
  assertTrue(new CinemaSettings(t.activity).uiSound());assertEquals(100,new CinemaSettings(t.activity).uiSoundVolume());key(22);key(23);assertEquals(Arrays.asList(0,1),player.cues);assertEquals(.65f,player.gains.get(0),.001);assertEquals(.85f,player.gains.get(1),.001);
  t.home.options.resetEffects();assertTrue(t.home.options.uiSound());assertEquals(100,t.home.options.uiSoundVolume());assertEquals(original,audio.getStreamVolume(android.media.AudioManager.STREAM_SYSTEM));
 }
 @Test public void confirmTakesPriorityAndSuppressesImmediateMovementTail(){
  sounds.key(new KeyEvent(0,22));sounds.moved();sounds.select();clock[0]+=150;sounds.key(new KeyEvent(0,22));sounds.moved();assertEquals(Arrays.asList(0,1),player.cues);clock[0]+=200;sounds.moved();assertEquals(Arrays.asList(0,1,0),player.cues);
 }
 @Test public void unloadedPausedAndClosedAudioNeverReplaysMissedKeys(){
  player.loaded=false;key(22);key(23);assertTrue(player.cues.isEmpty());player.loaded=true;t.idle();assertTrue(player.cues.isEmpty());sounds.pause();sounds.select();assertTrue(player.cues.isEmpty());sounds.resume();sounds.select();assertEquals(1,player.cues.size());sounds.close();sounds.close();clock[0]+=500;sounds.select();assertEquals(1,player.cues.size());assertEquals(1,player.closes);
 }
 @Test public void stockWindowSoundIsRestoredOnPauseAndClickViewsDisableDoubleBeep(){
  assertFalse(t.root.getRootView().isSoundEffectsEnabled());assertFalse(t.home.dock.getChildAt(0).isSoundEffectsEnabled());assertFalse(t.library.isSoundEffectsEnabled());sounds.pause();assertTrue(t.root.getRootView().isSoundEffectsEnabled());sounds.resume();assertFalse(t.root.getRootView().isSoundEffectsEnabled());sounds.close();assertTrue(t.root.getRootView().isSoundEffectsEnabled());
 }
 @Test public void legacyVolumeValuesNeverExceedSoundPoolGainRange(){
  for(int old:new int[]{-50,0,25,50,100,999}){t.home.options.prefs.edit().putInt("ui_sound_volume",old).commit();assertEquals(100,new CinemaSettings(t.activity).uiSoundVolume());clock[0]+=500;sounds.select();assertEquals(.85f,player.gains.get(player.gains.size()-1),.001);}
 }
 @Test public void leavingHomeKeepsOnlyAlreadyStartedShortConfirmationTail(){
  sounds.select();int stops=player.stops;sounds.pause();assertEquals(stops,player.stops);clock[0]+=400;sounds.key(new KeyEvent(0,22));sounds.moved();sounds.select();assertEquals(Arrays.asList(1),player.cues);
  sounds.resume();sounds.key(new KeyEvent(0,22));sounds.moved();assertEquals(Arrays.asList(1,0),player.cues);sounds.pause();assertTrue(player.stops>stops);
 }
 @Test public void appLibraryConfirmationIsWiredWithoutChangingCallback(){
  t.home.openLibrary(false);t.home.libraryPanel.catalogue.close();t.home.libraryPanel.setApps(t.adapter.cinemaEntries());t.layout(1920,1080);t.idle();t.home.libraryPanel.tabs.getChildAt(1).performClick();assertEquals(Arrays.asList(1),player.cues);assertEquals(4,t.home.libraryPanel.grid.getChildCount());
 }

 @Test public void simplifiedSettingsHideSoundAndDeveloperControlsButKeepAdbReachable()throws Exception{
  t.home.showSettings();t.layout(1920,1080);t.idle();assertNull(t.root.findViewWithTag("sound:enabled"));assertNull(t.root.findViewWithTag("sound:volume"));assertNull(t.root.findViewWithTag("renderer:stage"));assertNull(t.root.findViewWithTag("effect:reflection"));
  View adb=t.root.findViewWithTag("adb:tools");assertNotNull(adb);assertFalse(adb.isShown());t.root.findViewWithTag("developer:toggle").performClick();t.layout(1920,1080);
  for(int i=0;i<30&&t.root.findFocus()!=adb;i++)key(20);
  assertSame(adb,t.root.findFocus());android.graphics.Rect r=new android.graphics.Rect();adb.getDrawingRect(r);adb.requestRectangleOnScreen(r,true);t.layout(1920,1080);assertTrue(adb.getGlobalVisibleRect(r));assertEquals(adb.getHeight(),r.height());
  t.capture(1920,1080,"simplified-settings-1920.png");t.capture(1280,720,"simplified-settings-1280.png");
 }
}
