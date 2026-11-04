package com.gathin.tvhome;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.net.Uri;
import android.view.*;
import android.widget.*;
import org.junit.*;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import java.io.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=29,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BootAppearanceActivityTest {
 ActivityController<BootAppearanceActivity> controller;BootAppearanceActivity activity;ViewGroup root;
 @Before public void start()throws Exception{ReflectionHelpers.callStaticMethod(org.robolectric.shadows.ShadowWindowManagerGlobal.class,"setInTouchMode",ReflectionHelpers.ClassParameter.from(boolean.class,false));Activity plain=Robolectric.buildActivity(Activity.class).setup().get();BootAssetsTest.clear(new File(plain.getFilesDir(),"boot_asset_preparation"));controller=Robolectric.buildActivity(BootAppearanceActivity.class).setup();activity=controller.get();root=activity.findViewById(android.R.id.content);waitReady();layout(1920,1080);}
 @After public void close(){controller.pause().stop().destroy();BootAssetsTest.clear(new File(activity.getFilesDir(),"boot_asset_preparation"));ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",null);ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",null);}
 View v(String tag){return root.findViewWithTag("boot:"+tag);}void layout(int w,int h){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();for(int i=0;i<8;i++){root.measure(View.MeasureSpec.makeMeasureSpec(w,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(h,View.MeasureSpec.EXACTLY));root.layout(0,0,w,h);}}
 void waitReady()throws Exception{for(int i=0;i<500;i++){Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();if(!(Boolean)ReflectionHelpers.getField(activity,"busy"))return;Thread.sleep(10);}fail("background preparation timed out");}
 byte[] picture(){Bitmap b=Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888);b.eraseColor(0xFF5588BB);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();}
 void importPicture()throws Exception{v("pick").performClick();org.robolectric.shadows.ShadowActivity.IntentForResult request=Shadows.shadowOf(activity).getNextStartedActivityForResult();Uri uri=Uri.parse("content://local/picture.png");Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(picture()));activity.onActivityResult(request.requestCode,Activity.RESULT_OK,new Intent().setData(uri));waitReady();layout(1920,1080);}
 String status(){return ((TextView)ReflectionHelpers.getField(activity,"status")).getText().toString();}
 @Test public void returningFromLogoMaintenanceShowsAppliedAndRestoredStatesWithoutRoot()throws Exception{
  importPicture();BootAssetStore.Asset selected=ReflectionHelpers.getField(activity,"asset");
  for(String state:new String[]{"APPLIED","RESTORED"}){
   BootLogoTransactionsTest.Memory memory=new BootLogoTransactionsTest.Memory();memory.value=new org.json.JSONObject().put("schema",1).put("clientUid",10106).put("state",state).put("transaction",BootVideoTransactionsTest.ID).put("sourceSha",selected.sha).put("beforeSha","a".repeat(64)).put("afterSha","b".repeat(64)).put("oldWindowSha","c".repeat(64)).put("newWindowSha","d".repeat(64)).put("nodeMeta","b3:1:0:0:660").put("seek",8525).put("count",5401).toString();
   BootLogoTransactions.Backend backend=new BootLogoTransactions.Backend(){public BootLogoTransactions.ReadResult read(File f){throw new AssertionError("Resume must not request Root");}public BootVideoTransactions.Result write(BootLogoTransactions.WriteRequest r){throw new AssertionError("Resume must not write");}};
   BootLogoTransactions model=new BootLogoTransactions(new File(activity.getFilesDir(),"boot_logo_transactions"),backend,memory,Runnable::run,Runnable::run,10106);ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",model);
   controller.pause().resume();assertTrue(status(),status().contains(state.equals("APPLIED")?"已套用":"已還原"));assertFalse(status(),status().contains("尚未套用"));
  }
 }
 @Test public void returningFromTransitionMaintenanceShowsAppliedAndRestoredStatesWithoutSettingWallpaper()throws Exception{
  v("target:WALLPAPER").performClick();waitReady();importPicture();BootAssetStore.Asset selected=ReflectionHelpers.getField(activity,"asset");
  for(String state:new String[]{"APPLIED","RESTORED"}){
   BootWallpaperTransactionsTest.Memory memory=new BootWallpaperTransactionsTest.Memory();memory.value=new org.json.JSONObject().put("schema",1).put("clientUid",10106).put("state",state).put("transaction",BootVideoTransactionsTest.ID).put("candidateSha",selected.sha).put("beforeSha","a".repeat(64)).toString();
   BootWallpaperTransactions.Backend backend=new BootWallpaperTransactions.Backend(){public BootWallpaperTransactions.Snapshot capture(File f){throw new AssertionError("Resume must not read wallpaper");}public int set(File f,BootWallpaperTransactions.Snapshot s){throw new AssertionError("Resume must not change wallpaper");}};
   BootWallpaperTransactions model=new BootWallpaperTransactions(new File(activity.getFilesDir(),"boot_wallpaper_transactions"),backend,memory,Runnable::run,Runnable::run,(ms,r)->{});ReflectionHelpers.setStaticField(BootWallpaperTransactions.class,"instance",model);
   controller.pause().resume();assertTrue(status(),status().contains(state.equals("APPLIED")?"已套用":"已還原"));assertFalse(status(),status().contains("尚未套用"));
  }
 }
 @Test public void pageHasThreeTargetsAndGuardedInlineActions(){assertNotNull(v("target:LOGO"));assertNotNull(v("target:VIDEO"));assertNotNull(v("target:WALLPAPER"));assertFalse(v("export").isEnabled());assertFalse(v("play").isEnabled());assertNull(v("apply"));assertNotNull(v("restore"));assertFalse(v("restore").isEnabled());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
 @Test public void cancelSourceReviewPreservesFailureAndNeverRequestsRoot()throws Exception{
  Object previous=ReflectionHelpers.getStaticField(BootVideoTransactions.class,"instance");int uid=android.os.Process.myUid();
  BootVideoTransactionsTest.Memory memory=new BootVideoTransactionsTest.Memory();memory.value=BootVideoSourceProbeTest.valid().toString().replace("10106",Integer.toString(uid));String original=memory.value;
  BootVideoTransactionsTest.Queue queue=new BootVideoTransactionsTest.Queue();BootVideoTransactions model=new BootVideoTransactions(r->{throw new AssertionError("Canceled review must not request Root");},memory,queue,Runnable::run,uid);
  android.content.SharedPreferences prefs=activity.getSharedPreferences("boot_video_source_probe_v1",0);String evidence=BootVideoNoWriteReviewTest.probe().toString().replace("10106",Integer.toString(uid));prefs.edit().putString(BootVideoTransactionsTest.ID,evidence).commit();
  try{
   ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",model);controller.pause().resume();
   ReflectionHelpers.setField(activity,"target",BootAssetStore.Target.VIDEO);ReflectionHelpers.setField(activity,"asset",BootVideoTransactionsTest.asset());ReflectionHelpers.callInstanceMethod(activity,"refresh");
   v("more").performClick();assertTrue(v("review-no-write").isEnabled());v("review-no-write").performClick();
   android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(dialog.isShowing());dialog.dismiss();
   assertEquals(original,memory.value);assertEquals(evidence,prefs.getString(BootVideoTransactionsTest.ID,""));assertFalse(model.canApply());assertFalse(model.canRestore());assertTrue(queue.pending.isEmpty());
  }finally{prefs.edit().remove(BootVideoTransactionsTest.ID).commit();ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",previous);}
 }
 @Test public void diagnosticReadsUnknownReceiptWithoutRootOrUnlocking()throws Exception{
  Object previous=ReflectionHelpers.getStaticField(BootVideoTransactions.class,"instance");
  BootVideoTransactionsTest.Memory memory=new BootVideoTransactionsTest.Memory();
  memory.value=new org.json.JSONObject().put("schema",1).put("state","UNKNOWN").put("clientUid",10106).put("diagnostic","fixture-no-root-output").toString();String original=memory.value;
  BootVideoTransactionsTest.Queue queue=new BootVideoTransactionsTest.Queue();
  BootVideoTransactions model=new BootVideoTransactions(r->{throw new AssertionError("Diagnostic viewer must not dispatch Root");},memory,queue,Runnable::run,10106);
  try{
   ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",model);controller.pause().resume();
   v("target:VIDEO").performClick();waitReady();v("more").performClick();v("transaction-diagnostic").performClick();waitReady();
   android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);
   org.json.JSONObject document=new org.json.JSONObject(((TextView)dialog.findViewById(android.R.id.content).findViewWithTag("boot:transaction-json")).getText().toString());
   assertEquals(original,document.getJSONObject("receipt").toString());assertTrue(document.getBoolean("readOnly"));assertFalse(document.has("sourceAudit"));
   assertEquals(original,memory.value);assertTrue(queue.pending.isEmpty());assertFalse(model.canApply());assertFalse(model.canRestore());assertFalse(v("source-probe").isEnabled());dialog.dismiss();
  }finally{ReflectionHelpers.setStaticField(BootVideoTransactions.class,"instance",previous);}
 }
 @Test public void pickerRequestsOnlyDocumentsWithoutStorageOrNetworkPermission(){v("pick").performClick();Intent i=Shadows.shadowOf(activity).getNextStartedActivityForResult().intent;assertEquals(Intent.ACTION_OPEN_DOCUMENT,i.getAction());assertTrue(i.hasCategory(Intent.CATEGORY_OPENABLE));assertTrue(i.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY,false));assertArrayEquals(new String[]{"image/png","image/jpeg"},i.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));}
 @Test public void remoteCenterActuallyOpensPickerOnce(){
  v("pick").requestFocus();
  root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_CENTER));
  root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_CENTER));
  assertNotNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
 }
 @Test public void fallbackSelectedContentIsPreparedBySameValidatedImport()throws Exception{
  java.util.List<String> routes=new java.util.ArrayList<>();
  ReflectionHelpers.setField(activity,"picker",new BootMaterialPicker((i,r)->{routes.add(i.getAction());if(Intent.ACTION_OPEN_DOCUMENT.equals(i.getAction()))throw new android.content.ActivityNotFoundException();activity.startActivityForResult(i,r);}));
  importPicture();
  assertEquals(java.util.List.of(Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_CHOOSER),routes);
  assertEquals(BootAssetStore.Target.LOGO,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).target);
  assertTrue(v("frame").isEnabled());assertTrue(v("export").isEnabled());
 }
 @Test public void failedDocumentCancellationCannotConsumeFallbackSelection()throws Exception{
  ReflectionHelpers.setField(activity,"picker",new BootMaterialPicker((i,r)->{
   if(Intent.ACTION_OPEN_DOCUMENT.equals(i.getAction()))throw new android.content.ActivityNotFoundException();
   activity.startActivityForResult(i,r);
  }));
  v("pick").performClick();int contentRequest=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;
  assertEquals(BootMaterialPicker.CONTENT_REQUEST,contentRequest);
  activity.onActivityResult(BootMaterialPicker.DOCUMENT_REQUEST,Activity.RESULT_CANCELED,null);
  assertTrue(activity.getPreferences(0).contains("pick_target"));
  assertEquals(contentRequest,activity.getPreferences(0).getInt("pick_request",-1));
  assertEquals("OPENED",new org.json.JSONObject(activity.getPreferences(0).getString("picker_diagnostic","")).getString("stage"));
  controller.recreate();activity=controller.get();root=activity.findViewById(android.R.id.content);waitReady();layout(1920,1080);
  assertEquals(contentRequest,activity.getPreferences(0).getInt("pick_request",-1));
  Uri uri=Uri.parse("content://local/fallback.png");Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(picture()));
  activity.onActivityResult(contentRequest,Activity.RESULT_OK,new Intent().setData(uri));waitReady();layout(1920,1080);
  assertTrue(status(),status().contains("預覽已更新"));assertNotNull(ReflectionHelpers.getField(activity,"asset"));
  assertFalse(activity.getPreferences(0).contains("pick_target"));assertFalse(activity.getPreferences(0).contains("pick_request"));
  assertEquals("READY",new org.json.JSONObject(activity.getPreferences(0).getString("picker_diagnostic","")).getString("stage"));
  String hash=((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha;
  activity.onActivityResult(contentRequest,Activity.RESULT_CANCELED,null);controller.pause().resume();
  assertEquals(hash,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha);assertTrue(status(),status().contains("預覽已更新"));
 }
 @Test public void matchingChooserCancellationClearsOnlyItsPendingRequest()throws Exception{
  importPicture();String hash=((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha;
  v("more").performClick();v("other-picker").performClick();
  org.robolectric.shadows.ShadowActivity.IntentForResult request=Shadows.shadowOf(activity).getNextStartedActivityForResult();
  assertEquals(BootMaterialPicker.CONTENT_REQUEST,request.requestCode);
  controller.pause().resume();
  activity.onActivityResult(request.requestCode,Activity.RESULT_CANCELED,null);
  assertFalse(activity.getPreferences(0).contains("pick_request"));assertFalse(activity.getPreferences(0).contains("pick_target"));
  assertEquals(hash,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha);assertTrue(status(),status().contains("取消"));
 }
 @Test public void clipOnlyAndExtraStreamResultsBothUpdatePreview()throws Exception{
  for(boolean clip:new boolean[]{true,false}){
   v("pick").performClick();int request=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;
   Uri uri=Uri.parse("content://local/"+(clip?"clip":"stream")+".png");Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(picture()));
   Intent result=new Intent();if(clip)result.setClipData(android.content.ClipData.newRawUri("material",uri));else result.putExtra(Intent.EXTRA_STREAM,uri);
   activity.onActivityResult(request,Activity.RESULT_OK,result);waitReady();layout(1920,1080);
   assertNotNull(ReflectionHelpers.getField(activity,"asset"));assertTrue(v("frame").isEnabled());assertTrue(status(),status().contains("預覽已更新"));
   controller.pause().resume();assertTrue(status(),status().contains("預覽已更新"));
   assertEquals("READY",new org.json.JSONObject(activity.getPreferences(0).getString("picker_diagnostic","")).getString("stage"));
  }
 }
 @Test public void missingSelectionErrorSurvivesResumeAndPreservesEarlierDraft()throws Exception{
  importPicture();String hash=((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha;
  v("pick").performClick();int request=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;
  activity.onActivityResult(request,Activity.RESULT_OK,new Intent());assertTrue(status(),status().contains("沒有傳回素材連結"));
  controller.pause().resume();assertTrue(status(),status().contains("沒有傳回素材連結"));assertEquals(hash,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha);
  assertEquals("INVALID_RESULT",new org.json.JSONObject(activity.getPreferences(0).getString("picker_diagnostic","")).getString("stage"));
 }
 @Test public void deniedReadPermissionDoesNotImportOrApplyLegacyFile()throws Exception{
  Shadows.shadowOf(activity.getApplication()).denyPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE);
  v("pick").performClick();int request=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;
  activity.onActivityResult(request,Activity.RESULT_OK,new Intent().setData(Uri.parse("file:///sdcard/Download/selected.png")));
  assertEquals("READ_PERMISSION_REQUIRED",new org.json.JSONObject(activity.getPreferences(0).getString("picker_diagnostic","")).getString("stage"));
  assertNull(ReflectionHelpers.getField(activity,"asset"));
  activity.onRequestPermissionsResult(7303,new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
  assertFalse(activity.getPreferences(0).contains("local_pick_uri"));assertNull(ReflectionHelpers.getField(activity,"asset"));assertTrue(status(),status().contains("未允許"));
 }
 @Test public void grantedLegacyReadImportsOnlyToPreviewWithoutApplying()throws Exception{
  Shadows.shadowOf(activity.getApplication()).denyPermissions(android.Manifest.permission.READ_EXTERNAL_STORAGE);
  File folder=new File(android.os.Environment.getExternalStorageDirectory(),"Download");assertTrue(folder.isDirectory()||folder.mkdirs());File file=new File(folder,"cinema-material-test.png");java.nio.file.Files.write(file.toPath(),picture());
  try{
   v("pick").performClick();int request=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;
   activity.onActivityResult(request,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file)));
   assertNull(ReflectionHelpers.getField(activity,"asset"));
   activity.onRequestPermissionsResult(7303,new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE},new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});waitReady();
   assertNotNull(ReflectionHelpers.getField(activity,"asset"));assertTrue(status(),status().contains("預覽已更新"));
   assertFalse(activity.getPreferences(0).contains("local_pick_uri"));assertEquals("IDLE",new org.json.JSONObject(BootLogoTransactions.get(activity).exportReceipt()).getString("state"));
  }finally{assertTrue(file.delete());}
 }
 @Test public void unreadableContentExplainsReadPermissionAndKeepsErrorOnResume()throws Exception{
  v("pick").performClick();int request=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;Uri uri=Uri.parse("content://local/unreadable.png");
  Shadows.shadowOf(activity.getContentResolver()).registerInputStream(uri,new InputStream(){public int read(){throw new SecurityException("private path/token must not be logged");}});
  activity.onActivityResult(request,Activity.RESULT_OK,new Intent().setData(uri));waitReady();assertTrue(status(),status().contains("未提供讀取權限"));
  controller.pause().resume();assertTrue(status(),status().contains("未提供讀取權限"));assertNull(ReflectionHelpers.getField(activity,"asset"));
  String report=activity.getPreferences(0).getString("picker_diagnostic","");assertTrue(report,report.contains("IMPORT_FAILED"));assertFalse(report.contains("private path/token"));assertFalse(report.contains(uri.toString()));
 }
 @Test public void diagnosticCanBeViewedAndCopiedWithoutStorageOrRoot()throws Exception{
  v("pick").performClick();int request=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;activity.onActivityResult(request,Activity.RESULT_OK,new Intent());
  v("more").performClick();layout(1280,720);v("pick-diagnostic").requestFocus();v("pick-diagnostic").performClick();
  android.app.AlertDialog dialog=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(dialog);dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick();Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
  assertTrue(clipboard.getPrimaryClip().getItemAt(0).getText().toString().contains("INVALID_RESULT"));
 }
 @Test public void unavailablePickersExplainFailureAndPreserveExistingAsset()throws Exception{
  importPicture();String hash=((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha;
  java.util.List<String> routes=new java.util.ArrayList<>();
  ReflectionHelpers.setField(activity,"picker",new BootMaterialPicker((i,r)->{routes.add(i.getAction());throw new android.content.ActivityNotFoundException();}));
  v("pick").performClick();assertEquals(2,routes.size());assertTrue(status(),status().contains("沒有可用"));
  assertEquals(hash,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha);
  assertFalse(activity.getPreferences(0).contains("pick_target"));assertTrue(v("pick").isEnabled());
 }
 @Test public void manualAlternativeIsFocusableAndDoesNotChangeAssetOnCancel()throws Exception{
  importPicture();String hash=((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha;
  assertFalse(v("other-picker").isShown());v("more").performClick();layout(1280,720);assertTrue(v("other-picker").isFocusable());v("other-picker").requestFocus();v("other-picker").performClick();
  org.robolectric.shadows.ShadowActivity.IntentForResult request=Shadows.shadowOf(activity).getNextStartedActivityForResult();assertEquals(Intent.ACTION_CHOOSER,request.intent.getAction());assertEquals(Intent.ACTION_GET_CONTENT,((Intent)request.intent.getParcelableExtra(Intent.EXTRA_INTENT)).getAction());
  activity.onActivityResult(request.requestCode,Activity.RESULT_CANCELED,null);
  assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());assertEquals(hash,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha);
 }
 @Test public void cancelledPickerPreservesAssetAndButtons()throws Exception{importPicture();String hash=((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha;v("pick").performClick();int request=Shadows.shadowOf(activity).getNextStartedActivityForResult().requestCode;activity.onActivityResult(request,Activity.RESULT_CANCELED,null);assertEquals(hash,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).sha);assertTrue(v("export").isEnabled());assertTrue(status().contains("取消"));}
 @Test public void dpadReachesInlineOptionsAndTabsWithoutOpeningMaintenance(){v("pick").requestFocus();root.dispatchKeyEvent(new KeyEvent(0,20));assertSame(v("more"),root.findFocus());root.dispatchKeyEvent(new KeyEvent(0,19));assertSame(v("pick"),root.findFocus());root.dispatchKeyEvent(new KeyEvent(0,21));assertSame(v("target:LOGO"),root.findFocus());root.dispatchKeyEvent(new KeyEvent(0,22));assertSame(v("target:VIDEO"),root.findFocus());assertNull(Shadows.shadowOf(activity).getNextStartedActivity());}
 @Test public void previewReframeAndLocalExportRoundTrip()throws Exception{
  importPicture();assertTrue(v("export").isEnabled());assertTrue(v("frame").isEnabled());assertFalse(v("play").isEnabled());BootAssetStore.Asset before=ReflectionHelpers.getField(activity,"asset");v("frame").performClick();waitReady();BootAssetStore.Asset after=ReflectionHelpers.getField(activity,"asset");assertTrue(after.crop);assertNotEquals(before.sha,after.sha);
  v("more").performClick();layout(1920,1080);v("export").performClick();org.robolectric.shadows.ShadowActivity.IntentForResult request=Shadows.shadowOf(activity).getNextStartedActivityForResult();assertEquals(Intent.ACTION_CREATE_DOCUMENT,request.intent.getAction());assertEquals("image/png",request.intent.getType());
  Uri dest=Uri.parse("content://local/export.png");ByteArrayOutputStream bytes=new ByteArrayOutputStream();Shadows.shadowOf(activity.getContentResolver()).registerOutputStream(dest,bytes);
  Shadows.shadowOf(activity.getContentResolver()).registerInputStream(dest,new InputStream(){ByteArrayInputStream in;public int read(){if(in==null)in=new ByteArrayInputStream(bytes.toByteArray());return in.read();}});
  activity.onActivityResult(request.requestCode,Activity.RESULT_OK,new Intent().setData(dest));waitReady();assertTrue(status(),status().contains("一致性核對通過"));assertArrayEquals(java.nio.file.Files.readAllBytes(after.output.toPath()),bytes.toByteArray());
 }
 @Test public void screenshotAndSettingsEntry()throws Exception{
  importPicture();capture(1920,1080,"boot-assets-1920.png");capture(1280,720,"boot-assets-1280.png");android.graphics.Rect r=new android.graphics.Rect();assertTrue(v("back").getGlobalVisibleRect(r));assertEquals(v("back").getHeight(),r.height());assertTrue(r.bottom<=720);
  CinemaViewTest home=new CinemaViewTest();home.create();try{home.home.showSettings();home.layout(1920,1080);home.idle();View entry=home.root.findViewWithTag("boot:assets");assertNotNull(entry);assertFalse(entry.isShown());home.root.findViewWithTag("developer:toggle").performClick();home.layout(1920,1080);entry.performClick();Intent started=Shadows.shadowOf(home.activity).getNextStartedActivity();assertEquals(BootAppearanceActivity.class.getName(),started.getComponent().getClassName());}finally{home.close();}
 }
 @Test public void moreOptionsAndTechnicalTextStayReachableAt720p()throws Exception{
  importPicture();v("more").performClick();layout(1280,720);v("more").requestFocus();
  for(int i=0;i<8&&root.findFocus()!=v("technical");i++){root.dispatchKeyEvent(new KeyEvent(0,20));layout(1280,720);}
  assertSame(v("technical"),root.findFocus());android.graphics.Rect r=new android.graphics.Rect();assertTrue(v("technical").getGlobalVisibleRect(r));assertEquals(v("technical").getHeight(),r.height());
  capture(1280,720,"boot-studio-details-1280.png");v("more").performClick();assertFalse(v("technical").isFocusable());assertSame(v("more"),root.findFocus());
 }
 @Test public void nativeStudioPreviewShowsBothImageTargetsAndPreservesSelection()throws Exception{
  BootStudioActions actions=ReflectionHelpers.getField(activity,"actions");actions.capable=true;BootAssetStore store=new BootAssetStore(activity);
  for(BootAssetStore.Target target:new BootAssetStore.Target[]{BootAssetStore.Target.LOGO,BootAssetStore.Target.WALLPAPER}){
   BootAssetStore.Asset prepared=store.prepare(target,false,()->false,()->activity.getResources().openRawResource(R.drawable.cinema_sunrise));store.commit(prepared);
   v("target:"+target.name()).performClick();waitReady();layout(1920,1080);assertEquals(target,((BootAssetStore.Asset)ReflectionHelpers.getField(activity,"asset")).target);
   capture(1920,1080,"boot-studio-"+target.name()+"-1920.png");capture(1280,720,"boot-studio-"+target.name()+"-1280.png");
  }
  v("target:LOGO").performClick();waitReady();assertNotNull(ReflectionHelpers.getField(activity,"asset"));assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
 }
 BootLogoTransactionsTest.Memory logoState(String state,String sha)throws Exception{
  BootLogoTransactionsTest.Memory m=new BootLogoTransactionsTest.Memory();m.value=new org.json.JSONObject().put("schema",1).put("clientUid",10106).put("state",state).put("transaction",BootVideoTransactionsTest.ID).put("sourceSha",sha).put("beforeSha","a".repeat(64)).put("afterSha","b".repeat(64)).put("oldWindowSha","c".repeat(64)).put("newWindowSha","d".repeat(64)).put("nodeMeta","b3:1:0:0:660").put("seek",8525).put("count",5401).put("backupExported",true).toString();
  BootLogoTransactions model=new BootLogoTransactions(new File(activity.getFilesDir(),"boot_logo_transactions"),new BootLogoTransactions.Backend(){public BootLogoTransactions.ReadResult read(File f){throw new AssertionError("Local UI must not request Root");}public BootVideoTransactions.Result write(BootLogoTransactions.WriteRequest r){throw new AssertionError("Local UI must not write");}},m,Runnable::run,Runnable::run,10106);
  ReflectionHelpers.setStaticField(BootLogoTransactions.class,"instance",model);BootStudioActions actions=ReflectionHelpers.getField(activity,"actions");actions.capable=true;controller.pause().resume();layout(1920,1080);return m;
 }
 void key(int code){root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,code));}
 @Test public void logoLocalSelectionAndReframeDoNotResetAppliedReadyOrUnknown()throws Exception{
  importPicture();
  for(String state:new String[]{"APPLIED","READY","UNKNOWN","FAILED_NO_TARGET_WRITE","APPLY_PENDING"}){
   BootLogoTransactionsTest.Memory m=logoState(state,"f".repeat(64));String saved=m.value;
   assertTrue(state,v("pick").isEnabled());assertTrue(state,v("frame").isEnabled());
   importPicture();v("frame").performClick();waitReady();assertEquals(saved,m.value);assertEquals(state,state.matches("READY|APPLIED"),v("manage").isEnabled());
   assertTrue(status(),status().contains(state.equals("READY")?"自動為新圖建立候選":state.equals("APPLIED")?"先還原":"不會解除套用鎖定"));
  }
 }
 @Test public void tabDownSkipsDisabledPickAndFrameToReplacementActionAtBothSizes()throws Exception{
  importPicture();logoState("APPLIED","f".repeat(64));
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){
   layout(size[0],size[1]);v("pick").setEnabled(false);v("frame").setEnabled(false);v("target:LOGO").requestFocus();key(20);
   assertSame(v("manage"),root.findFocus());key(20);assertSame(v("restore"),root.findFocus());key(20);assertSame(v("more"),root.findFocus());key(19);assertSame(v("restore"),root.findFocus());key(19);assertSame(v("manage"),root.findFocus());key(19);assertSame(v("target:LOGO"),root.findFocus());
  }
 }
 @Test public void historicalRepairToolsStayCollapsedUntilExplicitlyOpened()throws Exception{
  v("more").performClick();layout(1280,720);assertFalse(v("logo-read-probe").isShown());assertNotNull(v("diagnostics"));
  v("diagnostics").performClick();layout(1280,720);assertTrue(v("logo-read-probe").isShown());assertFalse(v("logo-read-probe").isEnabled());
  v("diagnostics").performClick();layout(1280,720);assertFalse(v("logo-read-probe").isFocusable());assertSame(v("diagnostics"),root.findFocus());
 }
 @Test public void busyBackAsksToStayWithoutCancellingOrResendingOperation()throws Exception{
  ReflectionHelpers.setField(activity,"busy",true);root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_BACK));
  android.app.AlertDialog confirm=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertNotNull(confirm);assertFalse(activity.isFinishing());assertTrue(confirm.getButton(-2).hasFocus());confirm.getButton(-2).performClick();assertFalse(activity.isFinishing());assertTrue((Boolean)ReflectionHelpers.getField(activity,"busy"));ReflectionHelpers.setField(activity,"busy",false);
 }
 @Test public void manualDownloadsDoesNotInvokeDocumentsPicker()throws Exception{
  v("more").performClick();layout(1280,720);assertTrue(v("download").isShown());v("download").requestFocus();android.graphics.Rect visible=new android.graphics.Rect();assertTrue(v("download").getGlobalVisibleRect(visible));v("download").performClick();android.app.AlertDialog menu=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(Shadows.shadowOf(menu).getTitle().toString().contains("下載"));assertEquals(1,menu.getListView().getAdapter().getCount());menu.getListView().performItemClick(menu.getListView().getChildAt(0),0,0);android.app.AlertDialog confirm=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(confirm.getButton(-2).hasFocus());confirm.getButton(-2).performClick();assertNull(Shadows.shadowOf(activity).getNextStartedActivityForResult());
 }
 @Test public void lockedTransactionCanReachMoreAndReceiptWithoutSelectableDraft()throws Exception{
  logoState("UNKNOWN","f".repeat(64));v("pick").setEnabled(false);v("target:LOGO").requestFocus();key(20);assertSame(v("more"),root.findFocus());
  v("more").performClick();layout(1280,720);key(20);assertSame(v("receipt"),root.findFocus());key(20);assertSame(v("transaction-diagnostic"),root.findFocus());key(20);assertSame(v("other-picker"),root.findFocus());key(20);assertSame(v("pick-diagnostic"),root.findFocus());key(20);assertSame(v("technical"),root.findFocus());key(20);assertSame(v("download"),root.findFocus());key(20);assertSame(v("diagnostics"),root.findFocus());key(20);assertSame(v("diagnostics"),root.findFocus());key(21);assertSame(v("target:LOGO"),root.findFocus());
 }
 @Test public void temporaryBusyStateAlwaysAllowsReturningAndNavigationRecovers()throws Exception{
  v("pick").requestFocus();ReflectionHelpers.setField(activity,"busy",true);ReflectionHelpers.callInstanceMethod(activity,"refresh");assertSame(v("back"),root.findFocus());key(20);assertSame(v("back"),root.findFocus());
  ReflectionHelpers.setField(activity,"busy",false);ReflectionHelpers.callInstanceMethod(activity,"refresh");key(20);assertSame(v("target:LOGO"),root.findFocus());key(20);assertSame(v("pick"),root.findFocus());key(19);assertSame(v("target:LOGO"),root.findFocus());
 }
 @Test public void firstRowUpReturnsSelectedTabInsteadOfHiddenOrUnselectedActions()throws Exception{
  v("target:WALLPAPER").performClick();waitReady();layout(1280,720);v("pick").requestFocus();key(19);assertSame(v("target:WALLPAPER"),root.findFocus());key(20);assertSame(v("pick"),root.findFocus());
 }
 @Test public void activatingEveryTabMovesToFirstOperationAtBothSizes()throws Exception{
  for(int[] size:new int[][]{{1920,1080},{1280,720}}){
   layout(size[0],size[1]);
   for(String target:new String[]{"VIDEO","WALLPAPER","LOGO","LOGO"}){
    v("target:"+target).requestFocus();v("target:"+target).performClick();waitReady();layout(size[0],size[1]);
    assertSame(target+" must enter operations",v("pick"),root.findFocus());assertFalse(v("back").hasFocus());
    android.graphics.Rect visible=new android.graphics.Rect();assertTrue(v("pick").getGlobalVisibleRect(visible));assertEquals(v("pick").getHeight(),visible.height());
   }
  }
 }
 @Test public void touchModeTabActivationGivesRealFocusToFirstOperation()throws Exception{
  ReflectionHelpers.callStaticMethod(org.robolectric.shadows.ShadowWindowManagerGlobal.class,"setInTouchMode",ReflectionHelpers.ClassParameter.from(boolean.class,true));
  Object viewRoot=ReflectionHelpers.callInstanceMethod(root,"getViewRootImpl");ReflectionHelpers.callInstanceMethod(viewRoot,"ensureTouchModeLocally",ReflectionHelpers.ClassParameter.from(boolean.class,true));
  assertTrue(root.isInTouchMode());
  for(String target:new String[]{"VIDEO","WALLPAPER","LOGO"}){
   v("target:"+target).performClick();waitReady();layout(1920,1080);
   assertSame(target+" must take touch-mode focus",v("pick"),root.findFocus());assertTrue(v("pick").isFocusableInTouchMode());
  }
 }
 @Test public void switchingFromScrolledAdvancedOptionsStartsAtVisibleFirstOperation()throws Exception{
  importPicture();v("more").performClick();layout(1280,720);
  android.widget.ScrollView scroll=ReflectionHelpers.getField(ReflectionHelpers.getField(activity,"screen"),"scroll");scroll.scrollTo(0,100000);assertTrue(scroll.getScrollY()>0);
  v("target:VIDEO").performClick();waitReady();layout(1280,720);assertEquals(0,scroll.getScrollY());assertSame(v("pick"),root.findFocus());
  key(20);assertSame(v("more"),root.findFocus());key(19);assertSame(v("pick"),root.findFocus());
 }
 @Test public void corruptTargetErrorSurvivesResumeAndStillAllowsChoosingReplacement()throws Exception{
  importPicture();BootAssetStore.Asset chosen=ReflectionHelpers.getField(activity,"asset");java.nio.file.Files.write(chosen.output.toPath(),new byte[]{1});
  v("target:VIDEO").performClick();waitReady();v("target:LOGO").performClick();waitReady();layout(1920,1080);
  assertTrue(status(),status().contains("無法驗證"));assertSame(v("pick"),root.findFocus());assertNull(ReflectionHelpers.getField(activity,"asset"));
  controller.pause().resume();assertTrue(status(),status().contains("無法驗證"));assertTrue(v("pick").isEnabled());
 }
 @Test @org.robolectric.annotation.Config(sdk=31) public void android12NativeSingleTapActivatesEachTabAndFocusesOperations()throws Exception{
  ReflectionHelpers.callStaticMethod(org.robolectric.shadows.ShadowWindowManagerGlobal.class,"setInTouchMode",ReflectionHelpers.ClassParameter.from(boolean.class,true));
  Object viewRoot=ReflectionHelpers.callInstanceMethod(root,"getViewRootImpl");ReflectionHelpers.callInstanceMethod(viewRoot,"ensureTouchModeLocally",ReflectionHelpers.ClassParameter.from(boolean.class,true));
  for(String target:new String[]{"VIDEO","WALLPAPER","LOGO"}){
   View tab=v("target:"+target);int[] xy=new int[2],origin=new int[2];tab.getLocationOnScreen(xy);root.getLocationOnScreen(origin);float x=xy[0]-origin[0]+tab.getWidth()/2f,y=xy[1]-origin[1]+tab.getHeight()/2f;long time=android.os.SystemClock.uptimeMillis();
   MotionEvent down=MotionEvent.obtain(time,time,MotionEvent.ACTION_DOWN,x,y,0),up=MotionEvent.obtain(time,time+30,MotionEvent.ACTION_UP,x,y,0);
   try{assertTrue(root.dispatchTouchEvent(down));assertTrue(root.dispatchTouchEvent(up));}finally{down.recycle();up.recycle();}
   waitReady();Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(40));layout(1920,1080);
   assertEquals(target,((BootAssetStore.Target)ReflectionHelpers.getField(activity,"target")).name());assertSame(v("pick"),root.findFocus());
   assertNull(Shadows.shadowOf(activity).getNextStartedActivity());
  }
 }
 void capture(int w,int h,String name)throws Exception{layout(w,h);Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);root.draw(new Canvas(b));File dir=new File("build/cinema-native");dir.mkdirs();try(OutputStream out=new FileOutputStream(new File(dir,name))){b.compress(Bitmap.CompressFormat.PNG,100,out);}b.recycle();}
}
