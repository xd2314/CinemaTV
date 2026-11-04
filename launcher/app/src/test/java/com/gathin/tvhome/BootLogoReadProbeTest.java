package com.gathin.tvhome;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class BootLogoReadProbeTest {
 JSONObject receipt()throws Exception{return new JSONObject().put("schema",1).put("state","UNKNOWN").put("clientUid",10092).put("transaction","00000000-0000-4000-8000-000000000001").put("sourceAssetId","00000000-0000-4000-8000-000000000002").put("sourceSha","a".repeat(64)).put("diagnostic","IOException: Root read incomplete; do not resend");}
 @Test public void onlyReadFailureMayRequestSeparateEvidenceAndOriginalRemainsLocked()throws Exception{
  JSONObject r=receipt();String original=r.toString();assertTrue(BootLogoReadProbe.eligible(original,10092));assertEquals(original,r.toString());
  BootLogoTransactions.Store store=new BootLogoTransactions.Store(){public String read(){return original;}public boolean write(String s){fail("No receipt mutation");return false;}};
  BootLogoTransactions lane=new BootLogoTransactions(new java.io.File("unused"),null,store,Runnable::run,Runnable::run,10092);assertFalse(lane.canRead());assertFalse(lane.canRestore());
 }
 @Test public void pendingWritesForeignUidAndUnboundFailuresNeverQualify()throws Exception{
  assertFalse(BootLogoReadProbe.eligible(receipt().toString(),10093));
  for(String state:new String[]{"IDLE","READ_PENDING","APPLY_PENDING","APPLIED","RESTORE_PENDING","FAILED_NO_TARGET_WRITE"})assertFalse(BootLogoReadProbe.eligible(receipt().put("state",state).toString(),10092));
  for(String marker:new String[]{"beforeSha","afterSha","seek","count","nodeMeta","readDiagnostic"})assertFalse(BootLogoReadProbe.eligible(receipt().put(marker,"present").toString(),10092));
  assertFalse(BootLogoReadProbe.eligible(receipt().put("diagnostic","write failed").toString(),10092));
 }
}
