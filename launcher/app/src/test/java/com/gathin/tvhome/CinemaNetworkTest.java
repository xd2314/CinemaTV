package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.provider.Settings;
import android.view.View;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.shadows.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk={29,31},qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaNetworkTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();}
 @After public void close(){t.close();}
 private ConnectivityManager manager(){return t.activity.getSystemService(ConnectivityManager.class);}
 private Network wifi(boolean validated){
  ShadowConnectivityManager cm=Shadows.shadowOf(manager());
  cm.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_WIFI,0,true,true));
  Network n=manager().getActiveNetwork();assertNotNull(n);cm.setNetworkCapabilities(n,caps(NetworkCapabilities.TRANSPORT_WIFI,validated));return n;
 }
 private NetworkCapabilities caps(int transport,boolean validated){
  NetworkCapabilities caps=ShadowNetworkCapabilities.newInstance();Shadows.shadowOf(caps).addTransportType(transport);Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
  if(validated)Shadows.shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);return caps;
 }
 private void resolve(String action,boolean enabled){
  ResolveInfo result=new ResolveInfo();result.activityInfo=new ActivityInfo();result.activityInfo.packageName="fixture.settings";result.activityInfo.name="fixture.settings.Network";
  result.activityInfo.enabled=enabled;result.activityInfo.exported=true;result.activityInfo.applicationInfo=new ApplicationInfo();result.activityInfo.applicationInfo.enabled=true;
  Shadows.shadowOf(t.activity.getPackageManager()).addResolveInfoForIntent(new Intent(action),result);
 }
 @Test public void validatedWifiAndEthernetUseDistinctSymbols(){
  CinemaNetwork.State wifi=CinemaNetwork.from(caps(NetworkCapabilities.TRANSPORT_WIFI,true));assertEquals(CinemaIcon.WIFI,wifi.icon());assertTrue(wifi.description(t.activity).contains("Wi-Fi 已連網"));
  CinemaNetwork.State wired=CinemaNetwork.from(caps(NetworkCapabilities.TRANSPORT_ETHERNET,true));assertEquals(CinemaIcon.ETHERNET,wired.icon());assertTrue(wired.label(t.activity).contains("有線網路"));assertNotEquals(wifi,wired);
 }
 @Test public void connectionWithoutValidationIsNeverClaimedOnline(){
  assertEquals(CinemaIcon.WIFI_LIMITED,CinemaNetwork.from(caps(NetworkCapabilities.TRANSPORT_WIFI,false)).icon());
  assertEquals(CinemaIcon.ETHERNET_LIMITED,CinemaNetwork.from(caps(NetworkCapabilities.TRANSPORT_ETHERNET,false)).icon());
  assertTrue(CinemaNetwork.from(caps(NetworkCapabilities.TRANSPORT_WIFI,false)).description(t.activity).contains("未驗證"));
  assertEquals(CinemaIcon.NETWORK_LIMITED,CinemaNetwork.from(null).icon());assertTrue(CinemaNetwork.from(null).label(t.activity).contains("未知"));
 }
 @Test public void vpnDoesNotGuessUnderlyingPhysicalTransport(){
  NetworkCapabilities caps=caps(NetworkCapabilities.TRANSPORT_WIFI,true);Shadows.shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_VPN);
  CinemaNetwork.State state=CinemaNetwork.from(caps);assertEquals(CinemaNetwork.Link.OTHER,state.link);assertEquals(CinemaIcon.NETWORK,state.icon());assertFalse(state.description(t.activity).contains("Wi-Fi"));
 }
 @Test public void repeatedRefreshDoesNotRebuildIconOrDisturbFocusOrSelection(){
  wifi(true);t.go(2);t.home.networkButton.requestFocus();t.home.refreshNetwork();Object icon=t.home.networkButton.getForeground();
  for(int i=0;i<30;i++)t.home.refreshNetwork();assertSame(icon,t.home.networkButton.getForeground());assertSame(t.home.networkButton,t.root.findFocus());assertEquals(2,t.home.selectedIndex);
  Shadows.shadowOf(manager()).setActiveNetworkInfo(null);t.home.refreshNetwork();assertEquals(CinemaIcon.NETWORK_OFFLINE,t.home.networkButton.getTag());assertTrue(t.home.networkButton.getContentDescription().toString().contains("未連線"));assertSame(t.home.networkButton,t.root.findFocus());
 }
 @Test public void settingsPrefersNetworkPageThenWifiThenGeneral(){
  resolve(Settings.ACTION_SETTINGS,true);assertEquals(Settings.ACTION_SETTINGS,CinemaNetwork.settingsIntent(t.activity).getAction());
  resolve(Settings.ACTION_WIFI_SETTINGS,true);assertEquals(Settings.ACTION_WIFI_SETTINGS,CinemaNetwork.settingsIntent(t.activity).getAction());
  resolve(Settings.ACTION_WIRELESS_SETTINGS,true);assertEquals(Settings.ACTION_WIRELESS_SETTINGS,CinemaNetwork.settingsIntent(t.activity).getAction());
 }
 @Test public void missingOrDisabledSettingsDoesNotLaunchOrWrite(){
  assertNull(CinemaNetwork.settingsIntent(t.activity));t.home.networkButton.performClick();assertNull(Shadows.shadowOf(t.activity).getNextStartedActivity());
  resolve(Settings.ACTION_WIFI_SETTINGS,false);assertNull(CinemaNetwork.settingsIntent(t.activity));
 }
 @Test public void clickOpensOnlySettingsAndDebounces(){
  resolve(Settings.ACTION_WIFI_SETTINGS,true);t.home.networkButton.performClick();t.home.networkButton.performClick();
  Intent intent=Shadows.shadowOf(t.activity).getNextStartedActivity();assertNotNull(intent);assertEquals(Settings.ACTION_WIFI_SETTINGS,intent.getAction());assertNull(intent.getData());assertNull(Shadows.shadowOf(t.activity).getNextStartedActivity());
 }
 @Test public void resumeRefreshesStatusAndSettingsHasNoMemoryEntry(){
  wifi(false);t.home.pause();t.home.resume();assertEquals(CinemaIcon.WIFI_LIMITED,t.home.networkButton.getTag());
  t.home.showSettings();t.layout(1920,1080);assertNull(t.root.findViewWithTag("memory:tools"));t.root.findViewWithTag("developer:toggle").performClick();assertNull(t.root.findViewWithTag("memory:tools"));
 }
 @Test public void existingMainNetworkCallbackUpdatesCinemaWithoutAnotherSubscription(){
  org.robolectric.android.controller.ActivityController<MainActivity> controller=Robolectric.buildActivity(MainActivity.class).setup();
  try{
   MainActivity activity=controller.get();CinemaHome home=ReflectionHelpers.getField(activity,"cinemaHome");assertNotNull(home);
   ConnectivityManager cm=activity.getSystemService(ConnectivityManager.class);ShadowConnectivityManager shadow=Shadows.shadowOf(cm);int before=shadow.getNetworkCallbacks().size();
   shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED,ConnectivityManager.TYPE_WIFI,0,true,true));Network net=cm.getActiveNetwork();
   ConnectivityManager.NetworkCallback callback=ReflectionHelpers.getField(activity,"networkCallback");
   NetworkCapabilities connected=caps(NetworkCapabilities.TRANSPORT_WIFI,true);shadow.setNetworkCapabilities(net,connected);callback.onCapabilitiesChanged(net,connected);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(CinemaIcon.WIFI,home.networkButton.getTag());
   NetworkCapabilities unvalidated=caps(NetworkCapabilities.TRANSPORT_WIFI,false);shadow.setNetworkCapabilities(net,unvalidated);callback.onCapabilitiesChanged(net,unvalidated);Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(CinemaIcon.WIFI_LIMITED,home.networkButton.getTag());assertEquals(before,shadow.getNetworkCallbacks().size());
  }finally{controller.pause().stop().destroy();}
 }
 @Test public void screenshotNetworkFocusedAndNoMemoryInSettings()throws Exception{
  wifi(true);t.home.refreshNetwork();t.home.networkButton.requestFocus();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(300));t.capture(1920,1080,"v68-network-1920.png");t.capture(1280,720,"v68-network-1280.png");
  t.home.showSettings();t.layout(1920,1080);t.root.findViewWithTag("developer:toggle").performClick();t.capture(1920,1080,"v68-settings-1920.png");
 }
}
