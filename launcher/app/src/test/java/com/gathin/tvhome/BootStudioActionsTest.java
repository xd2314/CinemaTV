package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.io.*;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
public class BootStudioActionsTest {
 ActivityController<Activity> controller;Activity a;BootStudioActions actions;String note="";int backendCalls;
 BootVideoTransactionsTest.Queue queue;BootLogoTransactionsTest.Memory logoStore;BootWallpaperTransactionsTest.Memory wallpaperStore;File root;BootAssetStore.Asset asset;
 @Before public void setup()throws Exception{
  controller=Robolectric.buildActivity(Activity.class).setup();a=controller.get();root=new File(a.getCacheDir(),"studio-"+java.util.UUID.randomUUID());assertTrue(root.mkdirs());queue=new BootVideoTransactionsTest.Queue();logoStore=new BootLogoTransactionsTest.Memory();wallpaperStore=new BootWallpaperTransactionsTest.Memory();
  installLogo();ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",new BootVideoTransactions(r->{backendCalls++;throw new AssertionError("No implicit Root");},new BootVideoTransactionsTest.Memory(),queue,Runnable::run,10106));
  ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",new BootWallpaperTransactions(root,new BootWallpaperTransactions.Backend(){public BootWallpaperTransactions.Snapshot capture(File f){backendCalls++;throw new AssertionError("No implicit capture");}public int set(File f,BootWallpaperTransactions.Snapshot s){backendCalls++;throw new AssertionError("No implicit setter");}},wallpaperStore,queue,Runnable::run,(ms,r)->{}));
  actions=new BootStudioActions(a,()->{},s->note=s);actions.capable=true;asset=new BootAssetStore.Asset(BootVideoTransactionsTest.ID,BootAssetStore.Target.LOGO,false,root,"f".repeat(64),"e".repeat(64),"d".repeat(64),"fixture");
 }
 void installLogo(){ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",new BootLogoTransactions(root,new BootLogoTransactions.Backend(){public BootLogoTransactions.ReadResult read(File f){backendCalls++;throw new AssertionError("No implicit read");}public BootVideoTransactions.Result write(BootLogoTransactions.WriteRequest r){backendCalls++;throw new AssertionError("No implicit write");}},logoStore,queue,Runnable::run,10106));}
 @After public void close(){actions.close();controller.pause().stop().destroy();ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",null);ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",null);ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",null);BootAssetsTest.clear(root);}
 AlertDialog dialog(){return org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();}
 void idle(){Shadows.shadowOf(Looper.getMainLooper()).idle();}
 void waitQueue()throws Exception{for(int i=0;i<1000;i++){idle();if(!queue.pending.isEmpty())return;Thread.sleep(5);}fail("No queued action");}
 void agree(){CheckBox c=checkbox(dialog().getWindow().getDecorView());if(c!=null)c.setChecked(true);dialog().getButton(-1).performClick();}
 void settle()throws Exception{for(int i=0;i<1000;i++){idle();if(!actions.busy())return;Thread.sleep(5);}fail("export did not settle");}
 void ready(boolean exported)throws Exception{
  File dir=new File(root,BootVideoTransactionsTest.ID);assertTrue(dir.mkdir());File before=new File(dir,"before.img");try(RandomAccessFile f=new RandomAccessFile(before,"rw")){f.setLength(BootLogoLayout.PHYSICAL_BYTES);}
  logoStore.value=new JSONObject().put("schema",1).put("clientUid",10106).put("state","READY").put("transaction",BootVideoTransactionsTest.ID).put("sourceSha",asset.sha).put("beforeSha",BootAssetStore.hash(before)).put("afterSha","b".repeat(64)).put("oldWindowSha","c".repeat(64)).put("newWindowSha","d".repeat(64)).put("nodeMeta","b3:1:0:0:660").put("seek",8525).put("count",5401).put("backupExported",exported).toString();installLogo();actions.observe();
 }
 @Test public void videoAndWallpaperDraftsDoNotInheritAppliedStatus()throws Exception{
  for(BootAssetStore.Target target:new BootAssetStore.Target[]{BootAssetStore.Target.VIDEO,BootAssetStore.Target.WALLPAPER}){
   JSONObject historical=new JSONObject().put("state","APPLIED").put(target==BootAssetStore.Target.VIDEO?"lastSha":"candidateSha","a".repeat(64));
   if(target==BootAssetStore.Target.VIDEO){
    BootVideoTransactionsTest.Memory memory=new BootVideoTransactionsTest.Memory();memory.value=new JSONObject(historical.toString()).put("schema",1).put("clientUid",10106).put("lastId",BootVideoTransactionsTest.ID).put("lastBefore","c".repeat(64)).toString();
    ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",new BootVideoTransactions(r->{backendCalls++;throw new AssertionError("No implicit Root");},memory,queue,Runnable::run,10106));
   }else{
    BootWallpaperTransactionsTest.Memory memory=new BootWallpaperTransactionsTest.Memory();memory.value=new JSONObject(historical.toString()).put("schema",1).put("transaction",BootVideoTransactionsTest.ID).put("beforeSha","c".repeat(64)).toString();
    ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",new BootWallpaperTransactions(root,null,memory,queue,Runnable::run,(ms,r)->{}));
   }
   BootStudioActions presentation=new BootStudioActions(a,()->{},s->note=s);
   presentation.capable=true;String before=historical.toString();
   try{
    BootAssetStore.Asset other=new BootAssetStore.Asset(BootVideoTransactionsTest.ID,target,false,root,"b".repeat(64),"e".repeat(64),"d".repeat(64),"draft");
    assertTrue(presentation.selectionSummary(target,other).contains("尚未套用"));assertFalse(presentation.primaryLabel(target,other).startsWith("重新"));
    BootAssetStore.Asset same=new BootAssetStore.Asset(BootVideoTransactionsTest.ID,target,false,root,"a".repeat(64),"e".repeat(64),"d".repeat(64),"same");
    assertTrue(presentation.selectionSummary(target,same).contains("尚未重新核對"));assertTrue(presentation.primaryLabel(target,same).startsWith("重新"));
    assertEquals(before,historical.toString());assertEquals(0,backendCalls);assertTrue(queue.pending.isEmpty());
   }finally{presentation.close();}
  }
 }
 @Test public void directDownloadCancelDoesNotDispatchRoot()throws Exception{
  actions.exportToDownloads(BootAssetStore.Target.VIDEO,false);assertTrue(dialog().getButton(-2).hasFocus());dialog().getButton(-2).performClick();assertEquals(0,backendCalls);assertTrue(queue.pending.isEmpty());assertFalse(actions.busy());assertNull(Shadows.shadowOf(a).getNextStartedActivityForResult());
 }
 @Test public void directDownloadReceiptVerifiesWithoutRoot()throws Exception{
  CinemaVerifiedExportTest.Downloads provider=new CinemaVerifiedExportTest.Downloads();android.content.pm.ProviderInfo info=new android.content.pm.ProviderInfo();info.authority="media";provider.attachInfo(a,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider);actions.exportToDownloads(BootAssetStore.Target.VIDEO,false);agree();settle();assertEquals(1,provider.publishes);assertEquals(2,provider.reads);assertTrue(note,note.contains("下載 / CinemaTV"));assertEquals(0,backendCalls);assertTrue(queue.pending.isEmpty());
 }
 @Test public void directDownloadRejectsReceiptChangeWhileDialogOpen()throws Exception{
  actions.exportToDownloads(BootAssetStore.Target.VIDEO,false);BootVideoTransactionsTest.Memory changed=new BootVideoTransactionsTest.Memory();changed.value=new JSONObject().put("schema",1).put("state","IDLE").put("marker","changed").toString();ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",new BootVideoTransactions(r->{backendCalls++;throw new AssertionError("No Root");},changed,queue,Runnable::run,10106));actions.observe();agree();idle();assertTrue(note,note.contains("狀態已變更"));assertFalse(actions.busy());assertTrue(queue.pending.isEmpty());assertEquals(0,backendCalls);
 }
 @Test public void openingAndSummariesNeverRequestRootOrWallpaper(){assertEquals(0,backendCalls);for(BootAssetStore.Target t:BootAssetStore.Target.values()){actions.summary(t,false);actions.diagnostic(t);actions.primaryEnabled(t,null);}assertEquals(0,backendCalls);assertTrue(queue.pending.isEmpty());}
 @Test public void firstLogoActionOnlyQueuesReadAfterSeparateConfirmation(){assertEquals("套用開機圖片",actions.primaryLabel(BootAssetStore.Target.LOGO));actions.primary(BootAssetStore.Target.LOGO,asset);assertTrue(dialog().getButton(-2).hasFocus());assertTrue(queue.pending.isEmpty());dialog().getButton(-2).performClick();idle();assertEquals("",logoStore.value);actions.primary(BootAssetStore.Target.LOGO,asset);agree();dialog().getButton(-1).performClick();idle();assertEquals(1,queue.pending.size());assertTrue(logoStore.value.contains("READ_PENDING"));assertEquals(0,backendCalls);}
 @Test public void readyRoutesToBackupExportWithoutRootAndDoesNotUnlockBeforeReadback()throws Exception{ready(false);actions.primary(BootAssetStore.Target.LOGO,asset);agree();for(int i=0;i<1000&&a.getPreferences(0).getString("studio_export","").isEmpty();i++){idle();Thread.sleep(5);}assertEquals(Intent.ACTION_CREATE_DOCUMENT,Shadows.shadowOf(a).getNextStartedActivityForResult().intent.getAction());assertFalse(new JSONObject(logoStore.value).optBoolean("backupExported"));actions.onResult(BootStudioActions.EXPORT,Activity.RESULT_CANCELED,null);assertTrue(queue.pending.isEmpty());assertEquals(0,backendCalls);}
 @Test public void preparedLogoOffersSeparatelyConfirmedFreshBackupForDifferentDraft()throws Exception{assertTrue(actions.canEdit(BootAssetStore.Target.LOGO));ready(true);String before=logoStore.value;assertTrue(actions.canEdit(BootAssetStore.Target.LOGO));BootAssetStore.Asset other=new BootAssetStore.Asset(BootVideoTransactionsTest.ID,BootAssetStore.Target.LOGO,false,root,"a".repeat(64),"e".repeat(64),"d".repeat(64),"different");assertTrue(actions.primaryEnabled(BootAssetStore.Target.LOGO,other));assertTrue(actions.selectionSummary(BootAssetStore.Target.LOGO,other).contains("自動為新圖建立候選"));assertEquals("套用開機圖片",actions.primaryLabel(BootAssetStore.Target.LOGO,other));actions.primary(BootAssetStore.Target.LOGO,other);assertTrue(dialog().getButton(-2).hasFocus());dialog().getButton(-2).performClick();idle();assertTrue(actions.primaryEnabled(BootAssetStore.Target.LOGO,asset));assertEquals(before,logoStore.value);assertTrue(actions.canEdit(BootAssetStore.Target.VIDEO));assertEquals(0,backendCalls);}
 @Test public void corruptedExportCannotUnlockLogoApply()throws Exception{
  ready(false);actions.export(BootAssetStore.Target.LOGO,true);Uri uri=Uri.parse("content://fixture/bad.img");Shadows.shadowOf(a.getContentResolver()).registerOutputStream(uri,new ByteArrayOutputStream());Shadows.shadowOf(a.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(new byte[]{1}));actions.onResult(BootStudioActions.EXPORT,Activity.RESULT_OK,new Intent().setData(uri));settle();assertFalse(new JSONObject(logoStore.value).optBoolean("backupExported"));assertEquals("套用開機圖片",actions.primaryLabel(BootAssetStore.Target.LOGO));assertEquals(0,backendCalls);
 }
 @Test public void exactExportEnablesSeparateApplyButNeverAutoApplies()throws Exception{
  ready(false);actions.export(BootAssetStore.Target.LOGO,true);Uri uri=Uri.parse("content://fixture/good.img");ByteArrayOutputStream out=new ByteArrayOutputStream();Shadows.shadowOf(a.getContentResolver()).registerOutputStream(uri,out);Shadows.shadowOf(a.getContentResolver()).registerInputStream(uri,new InputStream(){ByteArrayInputStream in;public int read(){if(in==null)in=new ByteArrayInputStream(out.toByteArray());return in.read();}public int read(byte[] b,int off,int len){if(in==null)in=new ByteArrayInputStream(out.toByteArray());return in.read(b,off,len);}});actions.onResult(BootStudioActions.EXPORT,Activity.RESULT_OK,new Intent().setData(uri));settle();assertTrue(new JSONObject(logoStore.value).getBoolean("backupExported"));assertEquals("READY",actions.state(BootAssetStore.Target.LOGO));assertTrue(actions.primaryEnabled(BootAssetStore.Target.LOGO,asset));assertEquals(0,backendCalls);assertTrue(queue.pending.isEmpty());
 }
 CheckBox checkbox(View v){if(v instanceof CheckBox)return(CheckBox)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){CheckBox c=checkbox(g.getChildAt(i));if(c!=null)return c;}}return null;}
 @Test public void applyingLogoStillRequiresRescueAcknowledgementAndOneConfirmation()throws Exception{ready(true);actions.primary(BootAssetStore.Target.LOGO,asset);AlertDialog d=dialog();assertTrue(d.getButton(-2).hasFocus());assertFalse(d.getButton(-1).isEnabled());CheckBox c=checkbox(d.getWindow().getDecorView());assertNotNull(c);c.setChecked(true);d.getButton(-1).performClick();d.getButton(-1).performClick();waitQueue();assertEquals(1,queue.pending.size());assertTrue(logoStore.value.contains("APPLY_PENDING"));assertEquals(0,backendCalls);}
 @Test public void unreviewedProfileCannotReadWriteOrRestore(){actions.capable=false;assertFalse(actions.primaryEnabled(BootAssetStore.Target.LOGO,asset));actions.primary(BootAssetStore.Target.LOGO,asset);actions.restore(BootAssetStore.Target.LOGO);assertTrue(queue.pending.isEmpty());assertEquals(0,backendCalls);}
 @Test public void unknownStateCannotBeUnlockedByOpeningOrConfirming()throws Exception{logoStore.value=new JSONObject().put("schema",1).put("clientUid",10106).put("state","UNKNOWN").put("transaction",BootVideoTransactionsTest.ID).toString();installLogo();actions.observe();assertFalse(actions.primaryEnabled(BootAssetStore.Target.LOGO,asset));actions.primary(BootAssetStore.Target.LOGO,asset);assertFalse(actions.restoreEnabled(BootAssetStore.Target.LOGO));assertEquals("UNKNOWN",actions.state(BootAssetStore.Target.LOGO));assertTrue(actions.canEdit(BootAssetStore.Target.LOGO));assertTrue(actions.selectionSummary(BootAssetStore.Target.LOGO,asset).contains("不會解除套用鎖定"));assertTrue(queue.pending.isEmpty());}
 @Test public void replacementConfirmationQueuesOnlyOneReadAndNoApply()throws Exception{
  ready(true);File dir=actions.logo().backupFile().getParentFile();BootAssetStore.Asset next=new BootAssetStore.Asset("22222222-2222-4222-8222-222222222222",BootAssetStore.Target.LOGO,false,root,"a".repeat(64),"e".repeat(64),"d".repeat(64),"different");String oldReceipt=logoStore.value;
  actions.primary(BootAssetStore.Target.LOGO,next);assertTrue(queue.pending.isEmpty());agree();dialog().getButton(-1).performClick();idle();assertEquals(1,queue.pending.size());assertEquals("READ_PENDING",actions.state(BootAssetStore.Target.LOGO));assertEquals(0,backendCalls);String id=new JSONObject(logoStore.value).getString("transaction");assertEquals(oldReceipt,new String(java.nio.file.Files.readAllBytes(new File(dir,"receipt-before-"+id+".json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
 }
 @Test public void appliedReplacementRoutesOnlyToGuardedRestoreNotReadOrApply()throws Exception{
  ready(true);logoStore.value=new JSONObject(logoStore.value).put("state","APPLIED").toString();installLogo();actions.observe();BootAssetStore.Asset next=new BootAssetStore.Asset("22222222-2222-4222-8222-222222222222",BootAssetStore.Target.LOGO,false,root,"a".repeat(64),"e".repeat(64),"d".repeat(64),"different");assertEquals("套用開機圖片",actions.primaryLabel(BootAssetStore.Target.LOGO,next));assertTrue(actions.primaryEnabled(BootAssetStore.Target.LOGO,next));actions.primary(BootAssetStore.Target.LOGO,next);AlertDialog d=dialog();assertTrue(d.getButton(-2).hasFocus());assertFalse(d.getButton(-1).isEnabled());checkbox(d.getWindow().getDecorView()).setChecked(true);d.getButton(-1).performClick();idle();assertEquals(1,queue.pending.size());assertEquals("RESTORE_PENDING",actions.state(BootAssetStore.Target.LOGO));assertEquals(0,backendCalls);
 }
 @Test public void consentedWallpaperPermissionContinuesOnlyOnce(){
  BootAssetStore.Asset image=new BootAssetStore.Asset(BootVideoTransactionsTest.ID,BootAssetStore.Target.WALLPAPER,false,root,"f".repeat(64),"e".repeat(64),"d".repeat(64),"fixture");
  actions.primary(BootAssetStore.Target.WALLPAPER,image);dialog().getButton(-1).performClick();idle();assertTrue(queue.pending.isEmpty());actions.permissionResult(BootStudioActions.PERMISSION,new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});actions.permissionResult(BootStudioActions.PERMISSION,new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});assertEquals(1,queue.pending.size());assertEquals(0,backendCalls);
 }
 @Test public void permissionAfterCloseDoesNotContinue(){
  BootAssetStore.Asset image=new BootAssetStore.Asset(BootVideoTransactionsTest.ID,BootAssetStore.Target.WALLPAPER,false,root,"f".repeat(64),"e".repeat(64),"d".repeat(64),"fixture");actions.primary(BootAssetStore.Target.WALLPAPER,image);dialog().getButton(-1).performClick();idle();actions.close();actions.permissionResult(BootStudioActions.PERMISSION,new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});assertTrue(queue.pending.isEmpty());
 }
 @Test public void changedReceiptDuringConfirmationCannotExpandItsScope()throws Exception{
  ready(true);actions.primary(BootAssetStore.Target.LOGO,asset);logoStore.value=new JSONObject(logoStore.value).put("state","RESTORED").toString();installLogo();actions.observe();agree();idle();assertTrue(queue.pending.isEmpty());assertTrue(note.contains("狀態已改變"));assertEquals(0,backendCalls);
 }
 @Test public void consentDeliveredAfterPageClosesCannotStartRoot(){actions.primary(BootAssetStore.Target.LOGO,asset);actions.close();agree();idle();assertTrue(queue.pending.isEmpty());assertEquals(0,backendCalls);}
 @Test public void permissionGrantDoesNotQueueWallpaperApply(){actions.permissionResult(BootStudioActions.PERMISSION,new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});assertTrue(note.contains("未接續套用"));assertTrue(queue.pending.isEmpty());assertEquals(0,backendCalls);}
}
