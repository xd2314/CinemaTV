package com.gathin.tvhome;
import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.CheckBox;
import java.io.*;
import java.nio.file.Files;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
import org.robolectric.*;
import org.robolectric.util.ReflectionHelpers;
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=29)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BootLogoApplyIntegrationTest {
 BootLogoTransactionsTest f;BootStudioActions actions;String note;
 @Before public void setup()throws Exception{
  f=new BootLogoTransactionsTest();f.start();ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",f.model);ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",null);ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",null);
  actions=new BootStudioActions(f.app,()->{},s->note=s);actions.capable=true;
 }
 @After public void close(){actions.close();f.close();ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",null);ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",null);ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",null);}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void until(BooleanSupplier condition)throws Exception{for(int i=0;i<2000;i++){idle();if(!f.queue.pending.isEmpty())f.queue.run();idle();if(condition.getAsBoolean())return;Thread.sleep(3);}fail("Flow timeout: "+note+" / "+f.memory.value);}
 CheckBox checkbox(View v){if(v instanceof CheckBox)return(CheckBox)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){CheckBox c=checkbox(g.getChildAt(i));if(c!=null)return c;}}return null;}
 void start(BootAssetStore.Asset a){actions.primary(BootAssetStore.Target.LOGO,a);AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.getButton(-2).hasFocus());checkbox(d.getWindow().getDecorView()).setChecked(true);d.getButton(-1).performClick();d.getButton(-1).performClick();idle();}
 String journal(){return f.app.getSharedPreferences("boot_logo_apply_flow_v1",0).getString("current","");}
 @Test public void matchingReadyBackupAppliesToCompletionWithoutPickerOrNewRead()throws Exception{f.ready(true);start(f.asset);until(()->!actions.busy());assertEquals(1,f.backend.reads);assertEquals(1,f.backend.writes);assertEquals("APPLIED",f.receipt().getString("state"));assertTrue(journal().contains("COMPLETE"));assertNull(Shadows.shadowOf(f.app).getNextStartedActivityForResult());}
 @Test public void readyReplacementReusesOnlyMatchingVerifiedBackupAndRetainsOldFiles()throws Exception{
  f.ready(true);File before=f.model.backupFile();String hash=BootAssetStore.hash(before);BootAssetStore.Asset next=f.anotherImage();start(next);until(()->!actions.busy());assertEquals(2,f.backend.reads);assertEquals(1,f.backend.writes);assertEquals(hash,BootAssetStore.hash(before));assertEquals(next.sha,f.receipt().getString("sourceSha"));assertTrue(f.receipt().getBoolean("backupExported"));assertTrue(journal().contains("COMPLETE"));assertNull(Shadows.shadowOf(f.app).getNextStartedActivityForResult());
 }
 @Test public void appliedReplacementRestoresReadsAndAppliesUnderOneConsent()throws Exception{
  f.ready(true);f.model.apply(f.asset,true);f.queue.run();idle();BootAssetStore.Asset next=f.anotherImage();start(next);until(()->!actions.busy());assertEquals(2,f.backend.reads);assertEquals(3,f.backend.writes);assertEquals("APPLIED",f.receipt().getString("state"));assertEquals(next.sha,f.receipt().getString("sourceSha"));assertTrue(journal().contains("COMPLETE"));
 }
 @Test public void unrecordedBackupAsksForSaveThenAutomaticallyAppliesAfterReadback()throws Exception{
  f.ready(false);start(f.asset);until(()->!f.app.getPreferences(0).getString("studio_export","").isEmpty());assertEquals(0,f.backend.writes);Intent intent=Shadows.shadowOf(f.app).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_CREATE_DOCUMENT,intent.getAction());assertTrue(intent.getStringExtra(Intent.EXTRA_TITLE).contains(f.receipt().getString("transaction")));
  Uri uri=Uri.parse("content://fixture/new-backup");ByteArrayOutputStream out=new ByteArrayOutputStream();Shadows.shadowOf(f.app.getContentResolver()).registerOutputStream(uri,out);Shadows.shadowOf(f.app.getContentResolver()).registerInputStream(uri,new InputStream(){ByteArrayInputStream in;public int read(){if(in==null)in=new ByteArrayInputStream(out.toByteArray());return in.read();}public int read(byte[] b,int o,int l){if(in==null)in=new ByteArrayInputStream(out.toByteArray());return in.read(b,o,l);}});
  actions.onResult(BootStudioActions.EXPORT,Activity.RESULT_OK,new Intent().setData(uri));until(()->!actions.busy());assertEquals(1,f.backend.writes);assertTrue(journal().contains("COMPLETE"));
 }
 @Test public void cancelledSaveAfterRestoreStopsAndDoesNotClaimNothingChanged()throws Exception{
  f.ready(true);f.model.apply(f.asset,true);f.queue.run();f.memory.value=new JSONObject(f.memory.value).put("backupExported",false).toString();f.model=f.newModel();ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",f.model);actions.observe();BootAssetStore.Asset next=f.anotherImage();start(next);until(()->!f.app.getPreferences(0).getString("studio_export","").isEmpty());assertEquals(2,f.backend.writes);actions.onResult(BootStudioActions.EXPORT,Activity.RESULT_CANCELED,null);idle();assertFalse(actions.busy());assertEquals(2,f.backend.writes);assertArrayEquals(f.backend.original,f.backend.block);assertTrue(note,note.contains("不自動回復"));assertTrue(journal().contains("STOPPED"));
 }
 @Test public void corruptedPreviousBackupCannotBeReusedForNewCandidate()throws Exception{
  f.ready(true);File before=f.model.backupFile();try(RandomAccessFile r=new RandomAccessFile(before,"rw")){r.seek(500);r.writeByte(99);}start(f.anotherImage());until(()->!actions.busy());assertEquals(2,f.backend.reads);assertEquals(0,f.backend.writes);assertTrue(journal().contains("STOPPED"));
 }
 @Test public void closingDuringReadPreventsAnyLaterApplyOrAutomaticReplay()throws Exception{
  start(f.asset);assertEquals("queued: "+journal()+" / "+f.memory.value,1,f.queue.pending.size());actions.close();f.queue.run();idle();assertEquals("read: "+f.memory.value,1,f.backend.reads);assertEquals(0,f.backend.writes);actions=new BootStudioActions(f.app,()->{},s->note=s);actions.capable=true;idle();assertEquals(0,f.backend.writes);assertFalse(actions.busy());assertTrue(journal().contains("STOPPED"));
 }
 @Test public void anotherPageRevokesOldFlowBeforeItsReadCompletes()throws Exception{
  start(f.asset);assertEquals(1,f.queue.pending.size());BootStudioActions previous=actions;
  actions=new BootStudioActions(f.app,()->{},s->note=s);actions.capable=true;f.queue.run();idle();assertEquals(1,f.backend.reads);assertEquals(0,f.backend.writes);previous.close();idle();assertEquals(0,f.backend.writes);
 }
 @Test public void failedApplyStopsWithNoAutomaticRestoreOrRetry()throws Exception{
  f.ready(true);f.backend.timeout=true;start(f.asset);until(()->!actions.busy());assertEquals(1,f.backend.writes);assertEquals("UNKNOWN",f.receipt().getString("state"));assertTrue(journal().contains("STOPPED"));idle();assertEquals(1,f.backend.writes);
 }
}
