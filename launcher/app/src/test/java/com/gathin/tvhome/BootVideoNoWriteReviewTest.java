package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootVideoNoWriteReviewTest {
    static final String ID=BootVideoTransactionsTest.ID,SHA=BootVideoTransactionsTest.SHA;
    static String output()throws Exception{
        String path=BootVideoTransactionsTest.asset().output.getCanonicalPath();
        return "CLVS_UID=0\nCLVS_CLIENT_UID=10106\nCLVS_TX="+ID+"\nCLVS_CANDIDATE_SHA="+SHA+"\nCLVS_SOURCE="+path+"\nCLVS_REGULAR=1\nCLVS_SYMLINK=0\nCLVS_COMPLETE=1\nCLVS_CANONICAL\n"+path+"\nCLVS_METADATA\n10106:10106:600:30\nCLVS_SHA256\n"+SHA+"  "+path+"\n";
    }
    static JSONObject probe()throws Exception{return new JSONObject().put("state","FINISHED").put("exit",0).put("timeout",false).put("readOnly",true).put("transaction",ID).put("output",output());}
    static JSONObject audit()throws Exception{return new JSONObject().put("readOnly",true).put("newRootJournalAbsent",true).put("dataTargetDirectoryAbsent",true).put("property",BootVideoRoute.STOCK).put("stockSha","a".repeat(64))
        .put("source",new JSONObject().put("appUid",10106).put("uid",10106).put("gid",10106).put("mode","600").put("regular",true).put("canonicalMatches",true).put("readlinkExit",0).put("actualSha",SHA).put("javaCanonical",BootVideoTransactionsTest.asset().output.getCanonicalPath()).put("length",30));}
    static BootVideoTransactions model(BootVideoTransactionsTest.Memory memory,BootVideoTransactionsTest.Queue queue,int uid){return new BootVideoTransactions(r->{throw new AssertionError("Review must never request Root");},memory,queue,Runnable::run,uid);}
    @Test public void exactProbeCanBeReviewedButOpeningNeverUnlocksOrDispatches()throws Exception{
        BootVideoTransactionsTest.Memory m=new BootVideoTransactionsTest.Memory();m.value=BootVideoSourceProbeTest.valid().toString();String original=m.value;BootVideoTransactionsTest.Queue q=new BootVideoTransactionsTest.Queue();BootVideoTransactions t=model(m,q,10106);
        assertTrue(t.canReviewSourceNoWrite(probe().toString(),BootVideoTransactionsTest.asset()));assertFalse(t.canApply());assertFalse(t.canRestore());assertEquals(original,m.value);assertTrue(q.pending.isEmpty());
    }
    @Test public void explicitReviewPreservesAllEvidenceAndDoesNotAutomaticallyApply()throws Exception{
        BootVideoTransactionsTest.Memory m=new BootVideoTransactionsTest.Memory();m.value=BootVideoSourceProbeTest.valid().toString();String original=m.value,p=probe().toString();BootVideoTransactionsTest.Queue q=new BootVideoTransactionsTest.Queue();BootVideoTransactions t=model(m,q,10106);int[] calls={0};
        assertTrue(t.acknowledgeSourceNoWrite(p,BootVideoTransactionsTest.asset(),()->{calls[0]++;return audit();}));assertEquals(1,calls[0]);assertTrue(t.canApply());assertFalse(t.canRestore());assertTrue(q.pending.isEmpty());
        JSONObject saved=new JSONObject(m.value);assertEquals("IDLE",saved.getString("state"));assertEquals(original,saved.getJSONObject("acknowledgedSourceNoWrite").toString());assertEquals(p,saved.getJSONObject("sourceProbeEvidence").toString());assertTrue(saved.getJSONObject("sourceReviewAudit").getBoolean("readOnly"));
        assertFalse(t.acknowledgeSourceNoWrite(p,BootVideoTransactionsTest.asset(),()->{fail();return null;}));assertTrue(model(m,q,10106).canApply());assertTrue(q.pending.isEmpty());
    }
    @Test public void pendingUnknownAndOtherFailuresNeverReachAudit()throws Exception{
        for(String state:new String[]{"PENDING","UNKNOWN","APPLIED","RESTORED","IDLE"}){
            BootVideoTransactionsTest.Memory m=new BootVideoTransactionsTest.Memory();m.value=BootVideoSourceProbeTest.valid().put("state",state).toString();String original=m.value;BootVideoTransactions t=model(m,new BootVideoTransactionsTest.Queue(),10106);
            assertFalse(t.canReviewSourceNoWrite(probe().toString(),BootVideoTransactionsTest.asset()));assertFalse(t.acknowledgeSourceNoWrite(probe().toString(),BootVideoTransactionsTest.asset(),()->{fail();return null;}));assertEquals(original,m.value);
        }
    }
    @Test public void incompleteForeignDuplicateOrChangedRootProofIsRejected()throws Exception{
        String valid=output();
        for(String bad:new String[]{valid.replace("CLVS_UID=0","CLVS_UID=2000"),valid+"CLVS_UID=0\n",valid.replace("CLVS_REGULAR=1","CLVS_REGULAR=0"),valid.replace("CLVS_SYMLINK=0","CLVS_SYMLINK=1"),valid.replace("CLVS_COMPLETE=1","CLVS_COMPLETE=0"),valid.replace("10106:10106:600:30","10106:10106:644:30"),valid.replace("10106:10106:600:30","10107:10106:600:30"),valid.replace("10106:10106:600:30","10106:10106:600:0"),valid.replace("10106:10106:600:30","10106:10106:600:20971521"),valid.replace("CLVS_SHA256\n"+SHA,"CLVS_SHA256\n"+"b".repeat(64)),valid+"CLVS_CANONICAL\n/other\n"})
            assertFalse(BootVideoNoWriteReview.verified(BootVideoSourceProbeTest.valid().toString(),probe().put("output",bad).toString(),BootVideoTransactionsTest.asset(),10106));
        for(JSONObject bad:new JSONObject[]{probe().put("state","PENDING"),probe().put("state","UNKNOWN"),probe().put("exit",1),probe().put("timeout",true),probe().put("readOnly",false),probe().put("transaction","22345678-1234-1234-1234-123456789abc")})
            assertFalse(BootVideoNoWriteReview.verified(BootVideoSourceProbeTest.valid().toString(),bad.toString(),BootVideoTransactionsTest.asset(),10106));
    }
    @Test public void failedAuditAndChangedFilesystemSnapshotKeepOriginalLock()throws Exception{
        for(JSONObject invalid:new JSONObject[]{new JSONObject(),audit().put("newRootJournalAbsent",false),audit().put("dataTargetDirectoryAbsent",false),audit().put("property",BootVideoRoute.ACTIVE),audit().put("stockSha","bad"),audit().put("source",audit().getJSONObject("source").put("uid",10234)),audit().put("source",audit().getJSONObject("source").put("actualSha","b".repeat(64)))}){
            BootVideoTransactionsTest.Memory m=new BootVideoTransactionsTest.Memory();m.value=BootVideoSourceProbeTest.valid().toString();String original=m.value;BootVideoTransactions t=model(m,new BootVideoTransactionsTest.Queue(),10106);
            assertFalse(t.acknowledgeSourceNoWrite(probe().toString(),BootVideoTransactionsTest.asset(),()->invalid));assertFalse(t.canApply());assertEquals(original,m.value);
        }
        BootVideoTransactionsTest.Memory m=new BootVideoTransactionsTest.Memory();m.value=BootVideoSourceProbeTest.valid().toString();String original=m.value;BootVideoTransactions t=model(m,new BootVideoTransactionsTest.Queue(),10106);
        assertFalse(t.acknowledgeSourceNoWrite(probe().toString(),BootVideoTransactionsTest.asset(),()->{throw new java.io.IOException();}));assertFalse(t.canApply());assertEquals(original,m.value);
    }
    @Test public void storageFailureDoesNotDropOriginalEvidence()throws Exception{
        BootVideoTransactionsTest.Memory m=new BootVideoTransactionsTest.Memory();m.value=BootVideoSourceProbeTest.valid().toString();String original=m.value;BootVideoTransactions t=model(m,new BootVideoTransactionsTest.Queue(),10106);m.fail=true;
        assertFalse(t.acknowledgeSourceNoWrite(probe().toString(),BootVideoTransactionsTest.asset(),BootVideoNoWriteReviewTest::audit));assertFalse(t.canApply());assertEquals(original,m.value);
    }
    @Test public void matchingNormalUidWorksAndForeignUidCannotUseProof()throws Exception{
        BootVideoTransactionsTest.Memory m=new BootVideoTransactionsTest.Memory();m.value=BootVideoSourceProbeTest.valid().toString().replace("10106","12000");String p=probe().toString().replace("10106","12000");BootVideoTransactionsTest.Queue q=new BootVideoTransactionsTest.Queue();
        assertFalse(model(m,q,10106).canReviewSourceNoWrite(p,BootVideoTransactionsTest.asset()));BootVideoTransactions t=model(m,q,12000);
        assertTrue(t.acknowledgeSourceNoWrite(p,BootVideoTransactionsTest.asset(),()->new JSONObject(audit().toString().replace("10106","12000"))));assertTrue(q.pending.isEmpty());assertTrue(t.canApply());
    }
}
