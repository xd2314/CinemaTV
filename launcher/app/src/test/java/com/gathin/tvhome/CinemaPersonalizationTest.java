package com.gathin.tvhome;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;

@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaPersonalizationTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();}
 @After public void cleanup(){t.close();}
 List<AppAdapter.Entry> apps(){return ReflectionHelpers.getField(t.home,"apps");}
 void click(String tag){View v=t.root.findViewWithTag(tag);assertNotNull(tag,v);assertTrue(v.performClick());t.layout(1920,1080);}
 void idleUntil(java.util.function.BooleanSupplier condition)throws Exception{for(int i=0;i<150&&!condition.getAsBoolean();i++){Thread.sleep(15);t.idle();}assertTrue(condition.getAsBoolean());}
 Uri image(String name,Bitmap.CompressFormat format,int width,int height)throws Exception{Bitmap b=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);b.eraseColor(0xFF2797CC);ByteArrayOutputStream out=new ByteArrayOutputStream();assertTrue(b.compress(format,95,out));b.recycle();Uri uri=Uri.parse("content://fixture/"+name);Shadows.shadowOf(t.activity.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(out.toByteArray()));return uri;}
 @Test public void fourSlotsPersistNamesComponentsImagesAndOrder(){
  List<QuickLaunchItem> list=QuickLaunchItem.read(t.home.options,apps());list.get(0).displayName="我的直播";list.get(0).image="private:1234.png";Collections.swap(list,0,3);QuickLaunchItem.save(t.home.options,list);
  List<QuickLaunchItem> restored=QuickLaunchItem.read(new CinemaSettings(t.activity),apps());assertEquals("q3",restored.get(0).id);assertEquals("我的直播",restored.get(3).displayName);assertEquals("private:1234.png",restored.get(3).image);assertEquals(apps().get(0).component.getClassName(),restored.get(3).activityName);
  LastOpened.record(t.activity,apps().get(1).pkg);t.home.resume();assertEquals(4,t.home.recent.getChildCount());assertEquals(apps().get(1).pkg,((AppAdapter.Entry)ReflectionHelpers.getField(t.home.recent.getChildAt(0),"entry")).pkg);
 }
 @Test public void obsoleteShortcutSettingsCannotReplaceRecentHistory(){
  t.home.options.prefs.edit().putString("quick_launch_v1","broken").apply();t.home.resume();
  assertEquals(CinemaViewTest.PKGS[0],((AppAdapter.Entry)ReflectionHelpers.getField(t.home.recent.getChildAt(0),"entry")).pkg);
  assertEquals("broken",t.home.options.prefs.getString("quick_launch_v1",""));
 }

 @Test public void personalizationHasNoFixedAppNameOrOrderingControls(){
  t.home.showPersonalization();t.layout(1920,1080);
  for(String tag:new String[]{"quick:q0:app","quick:q0:name","quick:q0:image","quick:q0:later","quick:reset"})assertNull(t.root.findViewWithTag(tag));
  assertNotNull(t.root.findViewWithTag("wallpaper:day"));assertNotNull(t.root.findViewWithTag("glow:85"));
 }

 @Test public void wallpaperModesReuseExistingScheduleAndPersist(){
  t.home.options.image("day","private:aaaa.png");t.home.options.image("night","private:bbbb.png");t.home.options.image("fixed","private:cccc.png");assertFalse(t.home.options.autoWallpaper());
  t.home.options.autoWallpaper(true);CinemaSettings restored=new CinemaSettings(t.activity);assertEquals("private:aaaa.png",restored.effectiveWallpaper(360));assertEquals("private:cccc.png",restored.effectiveWallpaper(1080));assertEquals("private:cccc.png",restored.effectiveWallpaper(359));
  assertTrue(restored.schedule(true,420));restored.resetImages();assertEquals(420,restored.dayMinute());assertEquals("sunrise",restored.effectiveWallpaper(420));assertEquals("lake",restored.effectiveWallpaper(1080));
 }
 @Test public void importerBoundsDecodesPngJpegWebpAndRejectsTraversal()throws Exception{
  for(Bitmap.CompressFormat f:new Bitmap.CompressFormat[]{Bitmap.CompressFormat.PNG,Bitmap.CompressFormat.JPEG,Bitmap.CompressFormat.WEBP}){
   CinemaImageStore.Imported result=t.home.imageStore.importNow(image(f.name(),f,2200,1100));File file=CinemaImageStore.file(t.activity,result.reference);assertTrue(file.isFile());BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(file.getPath(),o);assertTrue(o.outWidth<=2048);assertEquals(2,o.outWidth/o.outHeight);assertTrue(result.preview.getWidth()<=640);t.home.imageStore.discard(result.reference);
  }
  try{CinemaImageStore.file(t.activity,"private:../../secret.png");fail();}catch(IOException expected){}
 }
 @Test public void invalidImageAndOversizeKeepExistingSettings()throws Exception{
  t.home.options.wallpaper("oled");Uri uri=Uri.parse("content://fixture/invalid");Shadows.shadowOf(t.activity.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(new byte[]{1,2,3}));
  try{t.home.imageStore.importNow(uri);fail();}catch(IOException expected){}assertEquals("oled",t.home.options.wallpaper());
  Uri huge=Uri.parse("content://fixture/oversize");Shadows.shadowOf(t.activity.getContentResolver()).registerInputStream(huge,new InputStream(){long remaining=CinemaImageStore.MAX_INPUT_BYTES+1;public int read(){return remaining-->0?0:-1;}public int read(byte[] b,int off,int len){if(remaining<=0)return -1;int n=(int)Math.min(remaining,len);Arrays.fill(b,off,off+n,(byte)0);remaining-=n;return n;}});
  try{t.home.imageStore.importNow(huge);fail();}catch(IOException expected){}assertEquals("oled",t.home.options.wallpaper());
 }
 @Test public void imagePreviewCancelRetainsOldThenConfirmCommits()throws Exception{
  t.home.options.wallpaper("oled");t.home.showPersonalization();t.home.chooseImage("wallpaper:fixed");int request=ReflectionHelpers.getStaticField(CinemaHome.class,"PICK_IMAGE");
  t.home.activityResult(request,Activity.RESULT_OK,new Intent().setData(image("cancel",Bitmap.CompressFormat.PNG,600,300)));idleUntil(()->t.root.findViewWithTag("image:confirm")!=null);assertEquals("oled",t.home.options.wallpaper());click("image:cancel");assertEquals("oled",t.home.options.wallpaper());
  t.home.chooseImage("wallpaper:day");t.home.activityResult(request,Activity.RESULT_OK,new Intent().setData(image("accept",Bitmap.CompressFormat.PNG,600,300)));idleUntil(()->t.root.findViewWithTag("image:confirm")!=null);assertEquals("oled_sunrise",t.home.options.image("day"));click("image:confirm");assertTrue(t.home.options.image("day").startsWith("private:"));assertTrue(CinemaImageStore.file(t.activity,t.home.options.image("day")).exists());
 }
 @Test public void stagedImagesSurvivePruningUntilCancelAndConfirmedFilesRemain()throws Exception{
  CinemaImageStore.Imported imported=t.home.imageStore.importNow(image("prune",Bitmap.CompressFormat.PNG,100,100));File file=CinemaImageStore.file(t.activity,imported.reference);t.home.imageStore.prune(Collections.emptySet());Thread.sleep(50);assertTrue(file.exists());
  t.home.options.image("day",imported.reference);t.home.imageStore.committed(imported.reference);t.home.imageStore.prune(t.home.options.imageReferences(apps()));Thread.sleep(50);assertTrue(file.exists());t.home.options.resetImages();t.home.imageStore.prune(t.home.options.imageReferences(apps()));assertTrue(file.exists());assertTrue(t.home.options.imageReferences(apps()).contains(imported.reference));
 }
 @Test public void missingPrivateWallAndQuickImagesFallBackWithoutCrash()throws Exception{
  List<QuickLaunchItem> list=QuickLaunchItem.read(t.home.options,apps());list.get(0).image="private:dead.png";QuickLaunchItem.save(t.home.options,list);t.home.options.wallpaper("private:dead.png");t.home.resume();t.layout(1920,1080);
  idleUntil(()->t.home.seasons.count()==4&&t.home.wallpapers.currentKey().startsWith("private:dead.png"));assertNotNull(((ImageView)ReflectionHelpers.getField(t.home.recent.getChildAt(0),"art")).getDrawable());assertEquals(1,t.home.wallpapers.retainedBitmapCount());
 }
 @Test public void visibleTopIconPixelsAreCenteredIndependentOfTextBaseline(){
  for(int size:new int[]{68,102})for(int kind:new int[]{CinemaIcon.GRID,CinemaIcon.SEARCH,CinemaIcon.SETTINGS}){
   CinemaButtonIcon icon=new CinemaButtonIcon(kind);icon.setBounds(0,0,size,size);Bitmap b=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888);icon.draw(new Canvas(b));int left=size,top=size,right=0,bottom=0;
   for(int y=0;y<size;y++)for(int x=0;x<size;x++)if(Color.alpha(b.getPixel(x,y))>40){left=Math.min(left,x);right=Math.max(right,x);top=Math.min(top,y);bottom=Math.max(bottom,y);}
   assertEquals(size/2f,(left+right+1)/2f,1.2f);assertEquals(size/2f,(top+bottom+1)/2f,1.2f);b.recycle();
  }
  for(int i=0;i<t.home.top.getChildCount();i++){TextView button=(TextView)t.home.top.getChildAt(i);assertNotNull(button.getForeground());assertEquals(0,button.getPaddingTop());}
 }
 @Test public void seasonTextIsRemovedAndPersonalizationPreviews()throws Exception{
  for(int i=0;i<4;i++){TextView meta=ReflectionHelpers.getField(t.home.recent.getChildAt(i),"meta");assertEquals("",meta.getText().toString());assertEquals(View.GONE,meta.getVisibility());}
  t.home.showSettings();t.layout(1920,1080);click("personalization");t.capture(1920,1080,"personalization-menu-1920x1080.png");t.root.findViewWithTag("glow:85").requestFocus();t.idle();t.capture(1920,1080,"personalization-effects-1920x1080.png");
  t.home.showPersonalization();t.layout(1920,1080);t.idle();t.capture(1920,1080,"wallpaper-editor-1920x1080.png");
 }
 @Test public void recreatedHomeKeepsConfirmedImagesAndOrderedSlots()throws Exception{
  CinemaImageStore.Imported image=t.home.imageStore.importNow(image("recreate",Bitmap.CompressFormat.PNG,100,100));
  List<QuickLaunchItem> list=QuickLaunchItem.read(t.home.options,apps());list.get(0).image=image.reference;list.get(0).displayName="保留名稱";Collections.swap(list,0,2);QuickLaunchItem.save(t.home.options,list);t.home.options.image("night",image.reference);t.home.imageStore.committed(image.reference);
  t.home.close();t.root.removeAllViews();LinearLayout original=new LinearLayout(t.activity);t.root.addView(original);t.library=t.button(R.id.btnLibraryTop,"所有 App");t.inputs=t.button(R.id.btnInputs,"輸入來源");t.settings=t.button(R.id.btnSettings,"設定");original.addView(t.library);original.addView(t.inputs);original.addView(t.settings);
  t.home=new CinemaHome(t.activity,t.root,t.adapter,Config.load(t.activity),t.library,t.inputs,t.settings);t.home.resume();t.layout(1920,1080);
  assertEquals(image.reference,t.home.options.image("night"));assertEquals(CinemaViewTest.PKGS[0],((AppAdapter.Entry)ReflectionHelpers.getField(t.home.recent.getChildAt(0),"entry")).pkg);assertEquals("保留名稱",QuickLaunchItem.read(t.home.options,apps()).get(2).displayName);assertTrue(CinemaImageStore.file(t.activity,image.reference).exists());
 }
 @Test public void removedAppDoesNotReplaceOtherConfiguredSlots(){
  List<QuickLaunchItem> before=QuickLaunchItem.read(t.home.options,apps());QuickLaunchItem.save(t.home.options,before);t.seed(3);t.home.resume();assertEquals(3,t.home.recent.getChildCount());assertNull(t.home.recent.getChildAt(3));assertEquals(before.get(3).packageName,QuickLaunchItem.read(t.home.options,apps()).get(3).packageName);
 }
 @Test public void reflectionDegradeKeepsWideBloomAndNewFocusHasNoOldColor(){
  CinemaFocusLight light=new CinemaFocusLight(t.activity);light.layout(0,0,900,600);light.prepare(Color.RED);light.prepare(Color.BLUE);light.select(Color.RED);light.position(new RectF(250,80,550,300),1,1,true);light.select(Color.BLUE);light.position(new RectF(250,80,550,300),1,1,true);
  Bitmap a=Bitmap.createBitmap(900,600,Bitmap.Config.ARGB_8888);light.draw(new Canvas(a));light.streaks(false);Bitmap b=Bitmap.createBitmap(900,600,Bitmap.Config.ARGB_8888);light.draw(new Canvas(b));
  assertEquals(a.getPixel(280,340),b.getPixel(280,340));int pixel=b.getPixel(400,360);assertTrue(Color.blue(pixel)>Color.red(pixel)+100);assertTrue(Color.alpha(pixel)>70);assertTrue(Color.alpha(b.getPixel(170,320))>0);a.recycle();b.recycle();
 }
 @Test public void leavingImporterDoesNotReopenPreviewOrChangeWallpaper()throws Exception{
  t.home.options.wallpaper("oled");t.home.showPersonalization();t.home.chooseImage("wallpaper:fixed");int request=ReflectionHelpers.getStaticField(CinemaHome.class,"PICK_IMAGE");
  t.home.activityResult(request,Activity.RESULT_OK,new Intent().setData(image("abandon",Bitmap.CompressFormat.PNG,100,100)));t.home.personalization.showRoot();
  java.util.concurrent.ExecutorService worker=ReflectionHelpers.getField(t.home.imageStore,"worker");worker.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);t.idle();worker.submit(()->{}).get(5,java.util.concurrent.TimeUnit.SECONDS);
  assertNull(t.root.findViewWithTag("image:confirm"));assertEquals("oled",t.home.options.wallpaper());assertNotNull(t.root.findViewWithTag("theme:oled"));
 }
}
