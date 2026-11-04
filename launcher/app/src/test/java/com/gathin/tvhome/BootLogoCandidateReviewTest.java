package com.gathin.tvhome;
import android.app.Activity;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.Files;
import java.util.UUID;
import org.json.JSONObject;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class BootLogoCandidateReviewTest {
 Activity app;File base,backup,reviewDir;BootAssetStore.Asset asset;JSONObject old,record;BootLogoTransactions.ReadResult read;int uid=10234;
 static String proof(int uid,String sha){return "CLBL_UID=0\nCLBL_CLIENT_UID="+uid+"\nCLBL_BYTES=33554432\nCLBL_SHA_BEFORE="+sha+"\nCLBL_SHA_AFTER="+sha+"\nCLBL_RESULT=READ_COMPLETE\n";}
 @Before public void setup()throws Exception{
  app=Robolectric.buildActivity(Activity.class).setup().get();base=Files.createTempDirectory("logo-review-").toFile();backup=new File(base,"original.img");byte[] bytes=BootLogoLayoutTest.fixture();Files.write(backup.toPath(),bytes);
  File source=new File(base,"asset");source.mkdir();Bitmap b=Bitmap.createBitmap(1280,720,Bitmap.Config.ARGB_8888);b.eraseColor(0xff253345);b.setHasAlpha(false);BootAssetStore.savePng(b,new File(source,"prepared.png"));b.recycle();String sha=BootAssetStore.hash(new File(source,"prepared.png"));
  asset=new BootAssetStore.Asset(UUID.randomUUID().toString(),BootAssetStore.Target.LOGO,false,source,sha,sha,sha,"");
  old=new JSONObject().put("schema",1).put("state","UNKNOWN").put("transaction",UUID.randomUUID().toString()).put("sourceSha",sha).put("sourceAssetId",asset.id).put("clientUid",uid).put("at",1L).put("diagnostic","IOException: Root read incomplete; do not resend");
  String before=BootLogoLayout.sha(bytes);read=new BootLogoTransactions.ReadResult(before,"b3:1:0:0:600",proof(uid,before),uid,"/dev/block/mmcblk0p1");reviewDir=new File(base,UUID.randomUUID().toString());record=BootLogoCandidateReview.build(reviewDir,backup,asset,read,old.toString());
 }
 @After public void clear(){BootAssetsTest.clear(base);app.finish();}
 @Test public void buildingCandidateIsReadOnlyAndPreservesOriginalResource()throws Exception{
  BootLogoCandidateReview.verify(reviewDir,record);assertEquals("PREPARED_READ_ONLY",record.getString("state"));assertEquals(read.sha,BootAssetStore.hash(backup));assertEquals(old.toString(),record.getJSONObject("originalReceipt").toString());assertNotEquals(record.getString("beforeSha"),record.getString("afterSha"));
 }
 @Test public void candidateOrBackupTamperingFailsRevalidation()throws Exception{
  File window=new File(reviewDir,"candidate-window.bin");try(RandomAccessFile f=new RandomAccessFile(window,"rw")){f.seek(100);f.write(17);}
  try{BootLogoCandidateReview.verify(reviewDir,record);fail();}catch(IOException expected){}
 }
 @Test public void adoptingVerifiedTokenNeedsSeparateConsentAndDoesNotDispatchRoot()throws Exception{
  class Store implements BootLogoTransactions.Store{String value=old.toString();public String read(){return value;}public boolean write(String text){value=text;return true;}}
  Store store=new Store();BootLogoTransactions.Backend never=new BootLogoTransactions.Backend(){public BootLogoTransactions.ReadResult read(File f){throw new AssertionError("No Root read");}public BootVideoTransactions.Result write(BootLogoTransactions.WriteRequest r){throw new AssertionError("No Root write");}};
  BootLogoTransactions lane=new BootLogoTransactions(new File(base,"transactions"),never,store,Runnable::run,Runnable::run,uid);
  // The token factory has its own frozen production eligibility tests below. This injects a verified fixture token solely to test the ordinary state transition.
  BootLogoCandidateReview.Ready token=ReflectionHelpers.callConstructor(BootLogoCandidateReview.Ready.class,ReflectionHelpers.ClassParameter.from(File.class,reviewDir),ReflectionHelpers.ClassParameter.from(JSONObject.class,record),ReflectionHelpers.ClassParameter.from(String.class,old.toString()));
  assertFalse(lane.canApply(asset));assertFalse(lane.adoptReviewedCandidate(token,false));assertEquals(old.toString(),store.value);
  assertTrue(lane.adoptReviewedCandidate(token,true));JSONObject next=new JSONObject(store.value);assertEquals("READY",next.getString("state"));assertEquals(old.toString(),next.getJSONObject("reviewedReadFailure").toString());assertTrue(next.getBoolean("backupExported"));assertTrue(lane.canApply(asset));assertFalse(lane.adoptReviewedCandidate(token,true));
 }
 @Test public void pendingAndChangedOriginalCannotAdoptEvenWithVerifiedToken()throws Exception{
  for(String state:new String[]{"READ_PENDING","APPLY_PENDING","RESTORE_PENDING"}){
   String text=new JSONObject(old.toString()).put("state",state).toString();BootLogoTransactions.Store s=new BootLogoTransactions.Store(){public String read(){return text;}public boolean write(String value){fail("Must remain locked");return false;}};
   BootLogoTransactions lane=new BootLogoTransactions(new File(base,"unused"),null,s,Runnable::run,Runnable::run,uid);assertFalse(lane.adoptReviewedCandidate(null,true));assertFalse(lane.canRead());
  }
  String changed=new JSONObject(old.toString()).put("at",2L).toString();BootLogoTransactions.Store other=new BootLogoTransactions.Store(){public String read(){return changed;}public boolean write(String s){fail("Changed original must stay locked");return false;}};
  BootLogoTransactions lane=new BootLogoTransactions(new File(base,"changed"),null,other,Runnable::run,Runnable::run,uid);
  BootLogoCandidateReview.Ready token=ReflectionHelpers.callConstructor(BootLogoCandidateReview.Ready.class,ReflectionHelpers.ClassParameter.from(File.class,reviewDir),ReflectionHelpers.ClassParameter.from(JSONObject.class,record),ReflectionHelpers.ClassParameter.from(String.class,old.toString()));
  assertFalse(lane.adoptReviewedCandidate(token,true));
 }
 @Test public void adoptionStoreFailureKeepsLaneBlockedAndQueuesUiNotifications()throws Exception{
  class Store implements BootLogoTransactions.Store{String text=old.toString();public String read(){return text;}public boolean write(String value){return false;}}Store store=new Store();
  java.util.List<Runnable> callbacks=new java.util.ArrayList<>();int[] notified={0};
  BootLogoTransactions lane=new BootLogoTransactions(new File(base,"failed-transactions"),null,store,Runnable::run,callbacks::add,uid);lane.observe(()->notified[0]++);
  BootLogoCandidateReview.Ready token=ReflectionHelpers.callConstructor(BootLogoCandidateReview.Ready.class,ReflectionHelpers.ClassParameter.from(File.class,reviewDir),ReflectionHelpers.ClassParameter.from(JSONObject.class,record),ReflectionHelpers.ClassParameter.from(String.class,old.toString()));
  assertTrue(lane.adoptReviewedCandidate(token,true));assertEquals(0,notified[0]);assertFalse(lane.canRead());assertFalse(lane.canApply(asset));assertEquals("UNKNOWN",new JSONObject(lane.exportReceipt()).getString("state"));
  for(Runnable callback:callbacks)callback.run();assertTrue(notified[0]>0);
 }
 @Test public void productionFactoryAcceptsOnlyTheFrozenDiagnosedReadFailure()throws Exception{
  int owner=10092;String before=BootLogoCandidateReview.BEFORE;JSONObject r=new JSONObject().put("schema",1).put("state","UNKNOWN").put("transaction",BootLogoCandidateReview.OLD_ID).put("sourceSha",BootLogoCandidateReview.OLD_SOURCE).put("sourceAssetId",BootLogoCandidateReview.OLD_ASSET).put("clientUid",owner).put("at",1700000000000L).put("diagnostic","IOException: Root read incomplete; do not resend");
  JSONObject result=new JSONObject().put("device","/dev/block/mmcblk0p1").put("metadata","b3:1:0:0:600").put("sha",before).put("bytes",33554432).put("layoutSupported",true).put("diagnostic",proof(owner,before));
  JSONObject p=new JSONObject().put("state","FINISHED").put("readOnly",true).put("transaction",BootLogoCandidateReview.OLD_ID).put("originalReceipt",r).put("result",result);
  JSONObject e=new JSONObject().put("state","FINISHED").put("transaction",BootLogoCandidateReview.OLD_ID).put("result",new JSONObject().put("readOnlyBackup",true).put("bytes",33554432).put("sha",before));
  BootAssetStore.Asset frozen=new BootAssetStore.Asset(BootLogoCandidateReview.OLD_ASSET,BootAssetStore.Target.LOGO,false,base,BootLogoCandidateReview.OLD_SOURCE,"","","");
  assertTrue(BootLogoCandidateReview.eligible(r.toString(),p.toString(),e.toString(),frozen,owner));
  BootAssetStore.Asset samePictureReimported=new BootAssetStore.Asset(UUID.randomUUID().toString(),BootAssetStore.Target.LOGO,false,base,BootLogoCandidateReview.OLD_SOURCE,"","","");
  assertTrue(BootLogoCandidateReview.eligible(r.toString(),p.toString(),e.toString(),samePictureReimported,owner));
  BootAssetStore.Asset differentPicture=new BootAssetStore.Asset(samePictureReimported.id,BootAssetStore.Target.LOGO,false,base,"b".repeat(64),"","","");
  assertFalse(BootLogoCandidateReview.eligible(r.toString(),p.toString(),e.toString(),differentPicture,owner));
  assertFalse(BootLogoCandidateReview.eligible(r.toString(),p.toString(),e.toString(),frozen,owner+1));assertFalse(BootLogoCandidateReview.eligible(old.toString(),p.toString(),e.toString(),asset,uid));
  r.put("reviewAdoptionError","present");assertFalse(BootLogoCandidateReview.eligible(r.toString(),p.toString(),e.toString(),frozen,owner));
 }
}
