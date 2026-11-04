package com.gathin.tvhome;
import android.app.AlertDialog;
import android.content.Intent;
import android.view.*;
import android.widget.CheckBox;
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
public class BootLogoInstallActivityTest {
 ActivityController<BootLogoInstallActivity> c;BootLogoInstallActivity a;BootLogoTransactionsTest.Memory memory;BootVideoTransactionsTest.Queue queue;BootLogoTransactions model;int calls;
 BootLogoTransactions create(){return new BootLogoTransactions(new File("unused-logo-test-folder"),new BootLogoTransactions.Backend(){public BootLogoTransactions.ReadResult read(File f){calls++;throw new AssertionError("Unexpected Root read");}public BootVideoTransactions.Result write(BootLogoTransactions.WriteRequest r){calls++;throw new AssertionError("Unexpected Root write");}},memory,queue,Runnable::run,10106);}
 @Before public void start()throws Exception{memory=new BootLogoTransactionsTest.Memory();queue=new BootVideoTransactionsTest.Queue();model=create();ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",model);c=Robolectric.buildActivity(BootLogoInstallActivity.class).setup();a=c.get();for(int i=0;i<500&&!(Boolean)ReflectionHelpers.getField(a,"loaded");i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5);}}
 @After public void close(){c.pause().stop().destroy();ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",null);}
 View v(String tag){return a.findViewById(android.R.id.content).findViewWithTag("logo:"+tag);}
 void fixture(){BootAssetStore.Asset x=BootVideoTransactionsTest.asset();ReflectionHelpers.setField(a,"asset",new BootAssetStore.Asset(x.id,BootAssetStore.Target.LOGO,false,x.output.getParentFile(),x.sha,x.sourceSha,x.posterSha,"UI fixture only"));ReflectionHelpers.setField(a,"capable",true);ReflectionHelpers.callInstanceMethod(a,"refresh");}
 @Test public void openAndReceiptExportNeverRequestRoot(){assertEquals(0,calls);assertFalse(v("read").isEnabled());assertFalse(v("apply").isEnabled());v("receipt").performClick();assertEquals(Intent.ACTION_CREATE_DOCUMENT,Shadows.shadowOf(a).getNextStartedActivityForResult().intent.getAction());assertEquals(0,calls);assertTrue(queue.pending.isEmpty());}
 @Test public void readConfirmationDefaultsToCancelAndCancelNeverPersistsOrDispatches(){fixture();v("read").performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(memory.value.isEmpty());assertTrue(queue.pending.isEmpty());assertEquals(0,calls);}
 @Test public void positiveReadConfirmationCreatesOneDurablePendingBeforeBackend(){fixture();assertTrue(model.exportReceipt()+model.message(),v("read").isEnabled());v("read").performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();d.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(memory.value+" / "+model.message(),1,queue.pending.size());assertTrue(memory.value.contains("READ_PENDING"));assertEquals(0,calls);}
 CheckBox checkbox(View view){if(view instanceof CheckBox)return(CheckBox)view;if(view instanceof ViewGroup){ViewGroup g=(ViewGroup)view;for(int i=0;i<g.getChildCount();i++){CheckBox box=checkbox(g.getChildAt(i));if(box!=null)return box;}}return null;}
 @Test public void applyRequiresFreshSeparateRescueAcknowledgement()throws Exception{
  String sha="f".repeat(64);memory.value=new org.json.JSONObject().put("schema",1).put("clientUid",10106).put("state","READY").put("transaction",BootVideoTransactionsTest.ID).put("sourceSha",sha).put("beforeSha",sha).put("afterSha",sha).put("oldWindowSha",sha).put("newWindowSha",sha).put("nodeMeta","b3:1:0:0:660").put("seek",8525).put("count",5401).put("backupExported",true).toString();
  model=create();ReflectionHelpers.setField(a,"model",model);fixture();assertTrue(v("apply").isEnabled());v("apply").performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();CheckBox box=checkbox(d.getWindow().getDecorView());assertNotNull(box);assertFalse(box.isChecked());assertFalse(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());assertTrue(d.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());
  box.setChecked(true);assertTrue(d.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled());d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertTrue(queue.pending.isEmpty());assertEquals(0,calls);
 }
 @Test public void allSixControlsFit720pAnd1080pWithoutRoot()throws Exception{fixture();for(int[] size:new int[][]{{1920,1080},{1280,720}}){View root=a.findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);android.graphics.Rect bounds=new android.graphics.Rect();assertTrue(v("back").getGlobalVisibleRect(bounds));assertEquals(v("back").getHeight(),bounds.height());assertTrue(bounds.bottom<=size[1]);android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(size[0],size[1],android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));File dir=new File("build/cinema-native");dir.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new File(dir,"boot-logo-"+size[0]+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}assertEquals(0,calls);}
}
