package com.gathin.tvhome;

import android.app.AlertDialog;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BootVideoInstallActivityTest {
    ActivityController<BootVideoInstallActivity> controller;BootVideoInstallActivity a;
    BootVideoTransactionsTest.Memory memory;BootVideoTransactionsTest.Queue queue;int calls;
    @Before public void start()throws Exception {
        memory=new BootVideoTransactionsTest.Memory();queue=new BootVideoTransactionsTest.Queue();
        BootVideoTransactions model=new BootVideoTransactions(r->{calls++;return new BootVideoTransactions.Result(0,false,BootVideoTransactionsTest.complete(r));},memory,queue,Runnable::run,10106);
        ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",model);
        controller=Robolectric.buildActivity(BootVideoInstallActivity.class).setup();a=controller.get();
        for(int i=0;i<500&&!(Boolean)ReflectionHelpers.getField(a,"loaded");i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();Thread.sleep(5);}
    }
    @After public void close(){controller.pause().stop().destroy();ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",null);}
    View v(String tag){return a.findViewById(android.R.id.content).findViewWithTag("video:"+tag);}
    void enableKnownFixture(){ReflectionHelpers.setField(a,"capable",true);ReflectionHelpers.setField(a,"asset",BootVideoTransactionsTest.asset());ReflectionHelpers.callInstanceMethod(a,"refresh");}
    @Test public void openingUnreviewedDeviceOrReceiptExportNeverRequestsRoot(){assertFalse(v("apply").isEnabled());assertFalse(v("restore").isEnabled());v("receipt").performClick();Intent intent=Shadows.shadowOf(a).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.getAction());assertEquals("application/json",intent.getType());assertEquals(0,calls);assertTrue(queue.pending.isEmpty());}
    @Test public void confirmationDefaultsToCancelAndCancelDoesNotPersistOrRunRoot(){enableKnownFixture();v("apply").performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);assertTrue(dialog.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();assertEquals(0,calls);assertTrue(queue.pending.isEmpty());assertEquals("",memory.value);}
    @Test public void acknowledgementDialogDefaultsToCancelAndDoesNotClearFailureOrRunRoot()throws Exception{
        memory.value=BootVideoTransactionsTest.guardFailure();BootVideoTransactions model=new BootVideoTransactions(r->{calls++;return null;},memory,queue,Runnable::run,10106);
        ReflectionHelpers.setField(a,"transactions",model);enableKnownFixture();assertTrue(((TextView)v("apply")).getText().toString().startsWith("唯讀核對"));
        String before=memory.value;v("apply").performClick();AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();
        assertTrue(d.getButton(AlertDialog.BUTTON_NEGATIVE).hasFocus());d.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        assertEquals(before,memory.value);assertEquals(0,calls);assertTrue(queue.pending.isEmpty());assertFalse(model.canApply());
    }
    @Test public void onlyPositiveConfirmationDispatchesOneDurableAction(){enableKnownFixture();v("apply").performClick();AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();assertEquals(1,queue.pending.size());assertTrue(memory.value.contains("PENDING"));assertEquals(0,calls);queue.run();assertEquals(1,calls);assertTrue(v("restore").isEnabled());}
    @Test public void controlsFit720pAnd1080pWithoutRoot()throws Exception{enableKnownFixture();for(int[] size:new int[][]{{1920,1080},{1280,720}}){View root=a.findViewById(android.R.id.content);root.measure(View.MeasureSpec.makeMeasureSpec(size[0],View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(size[1],View.MeasureSpec.EXACTLY));root.layout(0,0,size[0],size[1]);android.graphics.Rect r=new android.graphics.Rect();assertTrue(v("back").getGlobalVisibleRect(r));assertTrue(r.bottom<=size[1]);assertEquals(v("back").getHeight(),r.height());android.graphics.Bitmap image=android.graphics.Bitmap.createBitmap(size[0],size[1],android.graphics.Bitmap.Config.ARGB_8888);root.draw(new android.graphics.Canvas(image));java.io.File folder=new java.io.File("build/cinema-native");folder.mkdirs();try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(folder,"boot-video-transactions-"+size[0]+".png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}assertEquals(0,calls);}
}
