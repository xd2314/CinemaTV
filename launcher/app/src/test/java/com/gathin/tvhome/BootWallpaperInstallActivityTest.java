package com.gathin.tvhome;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BootWallpaperInstallActivityTest {
 ActivityController<BootWallpaperInstallActivity> c;BootWallpaperInstallActivity a;BootWallpaperTransactionsTest.Memory memory;BootVideoTransactionsTest.Queue queue;int calls;
 @Before public void start()throws Exception{
  memory=new BootWallpaperTransactionsTest.Memory();queue=new BootVideoTransactionsTest.Queue();
  BootWallpaperTransactions.Backend backend=new BootWallpaperTransactions.Backend(){public BootWallpaperTransactions.Snapshot capture(File f){calls++;throw new AssertionError("No implicit wallpaper access");}public int set(File f,BootWallpaperTransactions.Snapshot s){calls++;throw new AssertionError("No implicit wallpaper change");}};
  BootWallpaperTransactions model=new BootWallpaperTransactions(new File("unused-private-folder"),backend,memory,queue,Runnable::run,(ms,r)->{});ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",model);
  c=Robolectric.buildActivity(BootWallpaperInstallActivity.class).setup();a=c.get();for(int i=0;i<500&&!(Boolean)ReflectionHelpers.getField(a,"loaded");i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5);}
 }
 @After public void close(){c.pause().stop().destroy();ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",null);}
 View v(String tag){return a.findViewById(android.R.id.content).findViewWithTag("wallpaper:"+tag);}
 void fixture(){ReflectionHelpers.setField(a,"capable",true);BootAssetStore.Asset x=BootVideoTransactionsTest.asset();x=new BootAssetStore.Asset(x.id,BootAssetStore.Target.WALLPAPER,false,x.source.getParentFile(),x.sha,x.sourceSha,x.posterSha,"UI fixture only");ReflectionHelpers.setField(a,"asset",x);ReflectionHelpers.callInstanceMethod(a,"refresh");}
 @Test public void openingOrExportingReceiptDoesNotReadOrSetSystemWallpaper(){assertEquals(0,calls);assertFalse(v("apply").isEnabled());v("receipt").performClick();assertEquals(Intent.ACTION_CREATE_DOCUMENT,Shadows.shadowOf(a).getNextStartedActivityForResult().intent.getAction());assertEquals(0,calls);assertTrue(memory.value.isEmpty());}
 @Test public void confirmationDefaultsToCancelAndCancelDoesNotPersistOrDispatch(){fixture();v("apply").performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(0,calls);assertTrue(queue.pending.isEmpty());assertTrue(memory.value.isEmpty());}
 @Test public void runtimePermissionResultDoesNotAutomaticallyApply(){fixture();a.onRequestPermissionsResult(7202,new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});assertEquals(0,calls);assertTrue(queue.pending.isEmpty());assertTrue(memory.value.isEmpty());}
 @Test public void controlsFit720pAnd1080p()throws Exception{fixture();for(int[] size:new int[][]{{1920,1080},{1280,720}}){View root=a.findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);android.graphics.Rect bounds=new android.graphics.Rect();assertTrue(v("back").getGlobalVisibleRect(bounds));assertEquals(v("back").getHeight(),bounds.height());assertTrue(bounds.bottom<=size[1]);android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(size[0],size[1],android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));File folder=new File("build/cinema-native");folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(folder,"boot-wallpaper-"+size[0]+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}assertEquals(0,calls);}

 @Test @Config(sdk=31) public void android12CanOfferWallpaperWithoutImplicitReadOrWrite(){
  assertTrue((Boolean)ReflectionHelpers.getField(a,"capable"));fixture();assertTrue(v("apply").isEnabled());assertEquals(0,calls);assertTrue(memory.value.isEmpty());
 }
 @Test @Config(sdk=33) public void android13StillCannotApplyWithUnavailableBackupContract(){
  assertFalse((Boolean)ReflectionHelpers.getField(a,"capable"));assertFalse(v("apply").isEnabled());assertEquals(0,calls);
 }
}
