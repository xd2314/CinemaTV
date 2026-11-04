/* SPDX-License-Identifier: MIT */
package com.gathin.tvhome;

import android.content.ComponentName;
import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk={29,31},qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaOriginalDistributionTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();t.idle();}
 @After public void close(){t.close();}
 @Test public void allSixteenOriginalImagesAreBundledWithoutSubstitution()throws Exception{
  String[][] images={
   {"cinema_autumn","raw","a0333b2343dc21deb60819372d885941c4661420c1a2b5871bcc20edbb14adf1"},
   {"cinema_lake","raw","0ed6d3c522b4580c57e60aa564f5ffc70bb6702788690c54476199944afc7f23"},
   {"cinema_oled","raw","bac0a76ba155222e630157219320a2dba5f0e5c17152606c04ab11ba6a64a855"},
   {"banner","drawable","839c222dd3b2408f955e9af2b1fd5c22314c8873768b55d941c743dcc49f89ef"},
   {"cinema_sunrise","drawable","3aa755b23fcb933d3a9d5abbd2f4c8811bae2c416cf26cfb9147cfb1d101a7c8"},
   {"season_autumn","drawable","084cbcdf27a1575e7788ec11fbdb3b4990b88e3737e5ba1fff4a48d1bcb4b8d3"},
   {"season_spring","drawable","9374ef920228240389e08b0c55750be07eef306edc1495697bc74e1223b9a841"},
   {"season_summer","drawable","1509a1c21c85781dc20b08a1775a93daa6576fc23deaa5f419573b1d1524de06"},
   {"season_winter","drawable","2ff534742e1e09f8913ba71363bba3543487d74475274210b5647d0be4db4da9"},
   {"theme_aurora_sunrise","drawable","0ce61446f899cc1d499cbbac8e5a7161d85731def5e545ef3fff53f76bf1e106"},
   {"theme_aurora_sunset","drawable","b48ffc0240e6ecdd48e92261aa7f86a13aa27a655ef5b8589752ee0584dfbbb1"},
   {"theme_autumn_sunrise","drawable","84ecd18be9991386eed34e4827d6bf3377c7be637a691c5e5b74bac00d044df3"},
   {"theme_autumn_sunset","drawable","5d76fe0296fc872737d879c08585b0fe1a743193846fe618b4e6b6183693f873"},
   {"theme_oled_sunrise","drawable","401d4bda10c4205fefcee510e9f5290b8a958d7bbfc103dc089e526abe11d0c2"},
   {"theme_oled_sunset","drawable","27b8593d7eab4293be339766732d9c81578d9b60f8e1857d184599ae8eed6c13"},
   {"ic_launcher","mipmap","6460d3b890488434417f50633b843af88914bdd82fb8f8c5488046e59c3af100"}
  };
  for(String[] expected:images){int id=t.activity.getResources().getIdentifier(expected[0],expected[1],t.activity.getPackageName());assertTrue(expected[0],id!=0);byte[] bytes;try(java.io.InputStream input=t.activity.getResources().openRawResource(id)){bytes=input.readAllBytes();}byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder hex=new StringBuilder();for(byte b:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));assertEquals(expected[0],expected[2],hex.toString());BitmapFactory.Options options=new BitmapFactory.Options();options.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(bytes,0,bytes.length,options);assertTrue(expected[0],options.outWidth>0&&options.outHeight>0);}
 }
 @Test public void originalArtworkNativePreviewUsesSyntheticSampleApps()throws Exception{
  List<AppAdapter.Entry> items=ReflectionHelpers.getField(t.adapter,"items");items.clear();String[] labels={"Cinema","Live","Music","Video","Gallery","Podcasts","Library"};int[] colours={0xffE89A67,0xff53B9AA,0xff8C95DC,0xffDD7E92,0xff73A8C2,0xffB6A274,0xff7E9FB0};
  t.activity.getSharedPreferences("last_opened",0).edit().clear().commit();
  for(int i=0;i<labels.length;i++){Bitmap image=Bitmap.createBitmap(144,144,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(image);Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);paint.setColor(colours[i]);canvas.drawRoundRect(12,12,132,132,28,28,paint);paint.setColor(Color.WHITE);Path triangle=new Path();triangle.moveTo(57,45);triangle.lineTo(57,99);triangle.lineTo(100,72);triangle.close();canvas.drawPath(triangle,paint);String pkg="example.cinematv.sample"+i;android.content.pm.PackageInfo info=new android.content.pm.PackageInfo();info.packageName=pkg;info.applicationInfo=new android.content.pm.ApplicationInfo();info.applicationInfo.packageName=pkg;info.applicationInfo.enabled=true;info.applicationInfo.nonLocalizedLabel=labels[i];Shadows.shadowOf(t.activity.getPackageManager()).installPackage(info);items.add(new AppAdapter.Entry(labels[i],new ComponentName(pkg,pkg+".Main"),null,new BitmapDrawable(t.activity.getResources(),image)));if(i<4)LastOpened.record(t.activity,pkg);}
  t.adapter.notifyDataSetChanged();t.home.resume();t.layout(1920,1080);assertEquals(4,t.home.recent.getChildCount());t.go(3);
  for(int i=0;i<200&&t.home.wallpapers.currentKey().isEmpty();i++){Thread.sleep(10);t.idle();}
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));t.capture(1920,1080,"original-art-home-1920.png");t.capture(1280,720,"original-art-home-1280.png");
  t.home.showSettings();t.capture(1280,720,"original-art-settings-1280.png");
 }
}
