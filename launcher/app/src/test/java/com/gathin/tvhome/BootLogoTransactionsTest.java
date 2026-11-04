package com.gathin.tvhome;
import android.app.Activity;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.Files;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BootLogoTransactionsTest {
 static class Memory extends BootVideoTransactionsTest.Memory implements BootLogoTransactions.Store{}
 Activity app;File root;BootAssetStore.Asset asset;Memory memory;BootVideoTransactionsTest.Queue queue;Fake backend;BootLogoTransactions model;
 static String readProof(String sha){return "CLBL_UID=0\nCLBL_CLIENT_UID=10106\nCLBL_BYTES=33554432\nCLBL_SHA_BEFORE="+sha+"\nCLBL_SHA_AFTER="+sha+"\nCLBL_RESULT=READ_COMPLETE\n";}
 static String complete(BootLogoTransactions.WriteRequest r){return "CLBL_UID=0\nCLBL_CLIENT_UID="+r.clientUid+"\nCLBL_DEVICE="+r.device+"\nCLBL_TX="+r.id+"\nCLBL_BEFORE_SHA="+r.before+"\nCLBL_CANDIDATE_SHA="+r.after+"\nCLBL_SEEK="+r.seek+"\nCLBL_COUNT="+r.count+"\nCLBL_TARGET_WRITE_ATTEMPTED\nCLBL_AFTER_SHA="+(r.apply?r.after:r.before)+"\nCLBL_RESULT="+(r.apply?"APPLIED":"RESTORED")+"\n";}
 final class Fake implements BootLogoTransactions.Backend{
  int uid=10106;String device="/dev/block/mmcblk0p1";
  byte[] block=BootLogoLayoutTest.fixture(),original=block.clone();int reads,writes;boolean badRead,timeout,brokenProof;int exit;
  public BootLogoTransactions.ReadResult read(File output)throws Exception{reads++;assertTrue(memory.value.contains("READ_PENDING"));Files.write(output.toPath(),block);String sha=BootLogoLayout.sha(block);return new BootLogoTransactions.ReadResult(sha,"b3:1:0:0:660",badRead?"missing proof":readProof(sha).replace("10106",Integer.toString(uid)),uid,device);}
  public BootVideoTransactions.Result write(BootLogoTransactions.WriteRequest r)throws Exception{
   writes++;assertTrue(memory.value.contains(r.apply?"APPLY_PENDING":"RESTORE_PENDING"));
   if(exit!=0)return new BootVideoTransactions.Result(exit,false,"CLBL_UID=0\nCLBL_NO_TARGET_WRITE\n");
   byte[] bytes=r.apply?Files.readAllBytes(r.source.toPath()):java.util.Arrays.copyOfRange(original,r.seek*512,(r.seek+r.count)*512);System.arraycopy(bytes,0,block,r.seek*512,bytes.length);
   String proof=complete(r);if(brokenProof)proof=proof.replace("CLBL_UID=0","CLBL_UID=2000");return new BootVideoTransactions.Result(0,timeout,proof);
  }
 }
 @Before public void start()throws Exception{
  app=Robolectric.buildActivity(Activity.class).setup().get();root=new File(app.getFilesDir(),"boot_logo_transactions");BootAssetsTest.clear(root);BootAssetsTest.clear(new File(app.getFilesDir(),"boot_asset_preparation"));
  Bitmap b=Bitmap.createBitmap(80,80,Bitmap.Config.ARGB_8888);b.eraseColor(0xffaa3366);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();byte[] png=out.toByteArray();BootAssetStore store=new BootAssetStore(app);asset=store.prepare(BootAssetStore.Target.LOGO,false,()->false,()->new ByteArrayInputStream(png));store.commit(asset);
  memory=new Memory();queue=new BootVideoTransactionsTest.Queue();backend=new Fake();model=newModel();
 }
 BootLogoTransactions newModel(){return new BootLogoTransactions(root,backend,memory,queue,Runnable::run,10106);}
 org.json.JSONObject receipt()throws Exception{return new org.json.JSONObject(memory.value);}
 void ready(boolean exported)throws Exception{assertTrue(model.prepare(asset));queue.run();assertEquals("READY",receipt().getString("state"));if(exported)assertTrue(model.confirmBackupExport(receipt().getString("beforeSha"),BootLogoLayout.PHYSICAL_BYTES));}
 @After public void close(){BootAssetsTest.clear(root);BootAssetsTest.clear(new File(app.getFilesDir(),"boot_asset_preparation"));app.finish();}
 @Test public void passiveOpeningDoesNotDispatchRoot(){assertTrue(model.canRead());assertEquals(0,backend.reads);assertEquals(0,backend.writes);assertTrue(memory.value.isEmpty());}
 @Test public void fullReadPreparationDoesNotWriteAndNeedsVerifiedExportAndSeparateRescueConfirmation()throws Exception{ready(false);assertEquals(1,backend.reads);assertEquals(0,backend.writes);assertArrayEquals(backend.original,backend.block);assertFalse(model.canApply(asset));assertFalse(model.confirmBackupExport("a".repeat(64),BootLogoLayout.PHYSICAL_BYTES));assertTrue(model.confirmBackupExport(receipt().getString("beforeSha"),BootLogoLayout.PHYSICAL_BYTES));assertFalse(model.apply(asset,false));assertEquals(0,backend.writes);assertTrue(model.canApply(asset));}
 @Test public void separateSingleApplyAndExactRestoreSurviveRecreation()throws Exception{ready(true);assertTrue(model.apply(asset,true));assertFalse(model.apply(asset,true));assertEquals(0,backend.writes);queue.run();assertEquals(1,backend.writes);assertEquals("APPLIED",receipt().getString("state"));assertTrue(model.preparationNote(asset.sha).contains("已套用"));assertEquals(receipt().getString("afterSha"),BootLogoLayout.sha(backend.block));model=newModel();assertTrue(model.canRestore());assertTrue(model.restore());assertFalse(model.restore());queue.run();assertEquals(2,backend.writes);assertEquals("RESTORED",receipt().getString("state"));assertTrue(model.preparationNote(asset.sha).contains("已還原"));assertArrayEquals(backend.original,backend.block);}
 @Test public void alternateUidAndCanonicalNodePrepareApplyRestoreAndPersist()throws Exception{
  backend.uid=10234;backend.device="/dev/block/mmcblk1p2";
  model=new BootLogoTransactions(root,backend,memory,queue,Runnable::run,10234);ready(true);
  assertEquals(backend.device,receipt().getString("device"));assertEquals(10234,receipt().getInt("clientUid"));
  assertTrue(model.apply(asset,true));queue.run();assertEquals("APPLIED",receipt().getString("state"));
  model=new BootLogoTransactions(root,backend,memory,queue,Runnable::run,10234);
  assertTrue(model.restore());queue.run();assertArrayEquals(backend.original,backend.block);
 }
 @Test public void readFromWrongCallerStopsBeforeWrite()throws Exception{
  backend.uid=10234;assertTrue(model.prepare(asset));queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertEquals(0,backend.writes);
 }
 @Test public void copiedReceiptCannotAuthorizeDifferentUid()throws Exception{
  ready(true);BootLogoTransactions other=new BootLogoTransactions(root,backend,memory,queue,Runnable::run,10234);
  assertFalse(other.canApply(asset));assertFalse(other.canRead());assertFalse(other.canRestore());assertEquals(0,backend.writes);
 }
 @Test public void incompleteReadLocksWithoutRetryOrWrite()throws Exception{backend.badRead=true;assertTrue(model.prepare(asset));queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertFalse(model.canRead());assertFalse(model.prepare(asset));assertEquals(1,backend.reads);assertEquals(0,backend.writes);}
 @Test public void pendingSurvivesRestartWithoutReadOrWrite(){model.prepare(asset);assertFalse(newModel().canRead());assertFalse(newModel().canRestore());assertEquals(0,backend.reads);assertEquals(0,backend.writes);}
 @Test public void timeoutAndWrongRootProofNeverPassOrAutomaticallyRestore()throws Exception{ready(true);backend.timeout=true;model.apply(asset,true);queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertFalse(model.canRestore());assertFalse(model.apply(asset,true));assertEquals(1,backend.writes);}
 @Test public void wrongRootUidStopsDespiteCompleteHashes()throws Exception{ready(true);backend.brokenProof=true;model.apply(asset,true);queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertFalse(model.canRestore());assertEquals(1,backend.writes);}
 @Test public void noWriteFailureStopsAndDoesNotRetry()throws Exception{ready(true);backend.exit=21;model.apply(asset,true);queue.run();assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));assertFalse(model.canApply(asset));assertArrayEquals(backend.original,backend.block);assertEquals(1,backend.writes);}
 @Test public void changedWindowOrBackupPreventsRootWrite()throws Exception{ready(true);File candidate=new File(model.backupFile().getParentFile(),"candidate-window.bin");Files.write(candidate.toPath(),new byte[]{1});model.apply(asset,true);queue.run();assertEquals(0,backend.writes);assertEquals("UNKNOWN",receipt().getString("state"));}
 @Test public void storageFailureBeforeDispatchPreventsRoot(){memory.fail=true;assertFalse(model.prepare(asset));assertTrue(queue.pending.isEmpty());assertEquals(0,backend.reads);assertEquals(0,backend.writes);}
 @Test public void anotherSelectedImageCannotApplyEarlierCandidate()throws Exception{ready(true);BootAssetStore.Asset other=new BootAssetStore.Asset(asset.id,asset.target,false,asset.output.getParentFile(),"f".repeat(64),asset.sourceSha,asset.posterSha,"");assertFalse(model.apply(other,true));assertEquals(0,backend.writes);}
 BootAssetStore.Asset anotherImage()throws Exception{
  Bitmap b=Bitmap.createBitmap(128,72,Bitmap.Config.ARGB_8888);b.eraseColor(0xff2288dd);ByteArrayOutputStream bytes=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,bytes);b.recycle();BootAssetStore store=new BootAssetStore(app);BootAssetStore.Asset next=store.prepare(BootAssetStore.Target.LOGO,false,()->false,()->new ByteArrayInputStream(bytes.toByteArray()));store.commit(next);return next;
 }
 @Test public void differentReadyImageGetsFreshReadAndNewCandidatePreservingOldHistory()throws Exception{
  ready(true);String oldReceipt=memory.value,oldId=receipt().getString("transaction");File before=model.backupFile(),oldWindow=new File(before.getParentFile(),"candidate-window.bin");String oldHash=BootAssetStore.hash(oldWindow);BootAssetStore.Asset next=anotherImage();
  assertTrue(model.canReplacePrepared(next));assertTrue(model.prepare(next));assertFalse(model.prepare(next));assertEquals(1,backend.reads);assertEquals(0,backend.writes);assertEquals("READ_PENDING",receipt().getString("state"));
  String id=receipt().getString("transaction");assertNotEquals(oldId,id);assertEquals(oldId,receipt().getString("previousTransaction"));assertEquals(oldReceipt,new String(Files.readAllBytes(new File(before.getParentFile(),"receipt-before-"+id+".json").toPath()),java.nio.charset.StandardCharsets.UTF_8));
  queue.run();assertEquals(2,backend.reads);assertEquals(0,backend.writes);assertEquals("READY",receipt().getString("state"));assertFalse(receipt().getBoolean("backupExported"));assertNotEquals(oldHash,receipt().getString("newWindowSha"));assertEquals(oldHash,BootAssetStore.hash(oldWindow));assertEquals(BootLogoLayout.sha(backend.original),BootAssetStore.hash(before));
  assertFalse(model.canApply(asset));assertFalse(model.canApply(next));assertTrue(model.confirmBackupExport(receipt().getString("beforeSha"),BootLogoLayout.PHYSICAL_BYTES));assertTrue(model.canApply(next));assertTrue(model.apply(next,true));queue.run();assertEquals(1,backend.writes);assertEquals("APPLIED",receipt().getString("state"));assertTrue(model.restore());queue.run();assertArrayEquals(backend.original,backend.block);
 }
 @Test public void appliedImageMustBeExplicitlyRestoredBeforePreparingAnother()throws Exception{
  ready(true);model.apply(asset,true);queue.run();BootAssetStore.Asset next=anotherImage();assertFalse(model.canReplacePrepared(next));assertFalse(model.prepare(next));assertEquals(1,backend.reads);assertEquals(1,backend.writes);
  assertTrue(model.restore());queue.run();assertEquals("RESTORED",receipt().getString("state"));assertEquals(1,backend.reads);assertTrue(model.prepare(next));assertEquals(1,backend.reads);queue.run();assertEquals(2,backend.reads);assertEquals(2,backend.writes);assertFalse(receipt().getBoolean("backupExported"));
 }
 @Test public void sameCandidateDoesNotTriggerAnotherRead()throws Exception{ready(true);assertFalse(model.canReplacePrepared(asset));assertFalse(model.prepare(asset));assertEquals(1,backend.reads);}
 @Test public void failedReplacementReadPreservesOldBackupAndStaysLocked()throws Exception{
  ready(true);File original=model.backupFile();String oldHash=BootAssetStore.hash(original);BootAssetStore.Asset next=anotherImage();backend.badRead=true;assertTrue(model.prepare(next));queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertEquals(oldHash,BootAssetStore.hash(original));assertFalse(model.prepare(asset));assertFalse(model.prepare(next));assertFalse(newModel().canReplacePrepared(asset));assertEquals(2,backend.reads);assertEquals(0,backend.writes);
 }
 @Test public void replacementPendingRestartCannotReadOrWriteAgain()throws Exception{ready(true);BootAssetStore.Asset next=anotherImage();assertTrue(model.prepare(next));BootLogoTransactions reopened=newModel();assertFalse(reopened.prepare(next));assertFalse(reopened.canApply(next));assertFalse(reopened.canRestore());assertEquals(1,backend.reads);assertEquals(0,backend.writes);}
 @Test public void replacementReceiptFailureNeverDispatchesRoot()throws Exception{ready(true);BootAssetStore.Asset next=anotherImage();File original=model.backupFile();String hash=BootAssetStore.hash(original);memory.fail=true;assertFalse(model.prepare(next));assertTrue(queue.pending.isEmpty());assertFalse(model.canReplacePrepared(next));assertEquals(hash,BootAssetStore.hash(original));assertEquals(1,backend.reads);assertEquals(0,backend.writes);}
 @Test public void replacementDraftTamperingStopsBeforeFreshRootRead()throws Exception{ready(true);BootAssetStore.Asset next=anotherImage();assertTrue(model.prepare(next));Files.write(next.output.toPath(),new byte[]{1});queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertEquals(1,backend.reads);assertEquals(0,backend.writes);}
 @Test public void shellParametersRejectUnboundedRangesForeignUidAndSourceInjection()throws Exception{
  String id=BootVideoTransactionsTest.ID,sha="a".repeat(64);File allowed=new File("/data/data/com.gathin.tvhome.cinemalite.dev/files/boot_logo_transactions/"+id+"/candidate-window.bin");
  BootLogoTransactions.WriteRequest good=new BootLogoTransactions.WriteRequest(true,id,sha,sha,sha,sha,"b3:1:0:0:660",8525,5401,10106,allowed);assertTrue(BootLogoRoot.writeScript("@TX@ @SOURCE@ @SEEK@ @COUNT@",good).contains("8525 5401"));
  for(BootLogoTransactions.WriteRequest bad:new BootLogoTransactions.WriteRequest[]{new BootLogoTransactions.WriteRequest(true,id,sha,sha,sha,sha,"b3:1:0:0:660",8525,5401,2000,allowed),new BootLogoTransactions.WriteRequest(true,id,sha,sha,sha,sha,"b3:1:0:0:660",65000,5401,10106,allowed),new BootLogoTransactions.WriteRequest(true,id,sha,sha,sha,sha,"b3:1:0:0:660\n",8525,5401,10106,allowed),new BootLogoTransactions.WriteRequest(true,id,sha,sha,sha,sha,"b3:1:0:0:660",8525,5401,10106,new File("/sdcard/';evil"))}){try{BootLogoRoot.writeScript("@SOURCE@",bad);fail();}catch(IOException expected){}}
 }
}
