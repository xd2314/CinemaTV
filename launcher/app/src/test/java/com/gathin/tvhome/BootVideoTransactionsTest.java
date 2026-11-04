package com.gathin.tvhome;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootVideoTransactionsTest {
    static final String ID="12345678-1234-1234-1234-123456789abc";
    static final String SHA="f".repeat(64),BEFORE="a".repeat(64);
    static class Memory implements BootVideoTransactions.Store {
        String value="";boolean fail;
        public String read(){return value;}
        public boolean write(String s){if(fail)return false;value=s;return true;}
    }
    static class Queue implements Executor {
        List<Runnable> pending=new ArrayList<>();public void execute(Runnable r){pending.add(r);}void run(){pending.remove(0).run();}
    }
    static BootAssetStore.Asset asset(){return new BootAssetStore.Asset(ID,BootAssetStore.Target.VIDEO,false,new File("/data/user/0/com.gathin.tvhome.cinemalite.dev/files/boot_asset_preparation/"+ID),SHA,"b".repeat(64),"c".repeat(64),"fixture");}
    static String complete(BootVideoTransactions.Request r){return "CLBV_UID=0\nCLBV_CLIENT_UID=10106\nCLBV_TX="+r.id+"\nCLBV_BEFORE_SHA="+BEFORE+"\nCLBV_CANDIDATE_SHA="+r.sha+"\nCLBV_AFTER_SHA="+(r.apply?r.sha:BEFORE)+"\nCLBV_RESULT="+(r.apply?"APPLIED":"RESTORED")+"\n";}
    static String guardFailure()throws Exception{return new org.json.JSONObject().put("schema",1).put("state","FAILED_NO_TARGET_WRITE")
        .put("action","APPLY").put("transaction",ID).put("candidateSha",SHA).put("clientUid",10106).put("exit",16).put("timeout",false)
        .put("diagnostic","CLBV_UID=0\nCLBV_CLIENT_UID=10106\nCLBV_TX="+ID+"\nCLBV_NO_TARGET_WRITE\n").toString();}
    @Test public void diagnosedGuardFailureNeedsSeparateAuditAndPreservesEvidenceWithoutRoot()throws Exception{
        Memory m=new Memory();m.value=guardFailure();Queue q=new Queue();int[] audits={0};
        BootVideoTransactions t=new BootVideoTransactions(r->{fail("Audit must never call Root runner");return null;},m,q,Runnable::run,10106);
        assertFalse(t.canApply());assertTrue(t.canAcknowledgeNoWrite());assertEquals(0,audits[0]);
        assertTrue(t.acknowledgeNoWrite(()->audits[0]++));assertEquals(1,audits[0]);assertTrue(t.canApply());assertTrue(q.pending.isEmpty());
        org.json.JSONObject saved=new org.json.JSONObject(m.value);assertEquals("IDLE",saved.getString("state"));assertEquals(ID,saved.getJSONObject("acknowledgedNoWrite").getString("transaction"));
        assertFalse(t.acknowledgeNoWrite(()->fail()));
    }
    @Test public void auditFailureOrStorageFailureKeepsOriginalReceiptLocked()throws Exception{
        for(boolean storage:new boolean[]{false,true}){Memory m=new Memory();String original=guardFailure();m.value=original;
            BootVideoTransactions t=new BootVideoTransactions(r->{fail();return null;},m,Runnable::run,Runnable::run,10106);m.fail=storage;
            assertFalse(t.acknowledgeNoWrite(()->{if(!storage)throw new java.io.IOException();}));assertEquals(original,m.value);assertFalse(t.canApply());}
    }
    @Test public void unknownPendingWrongUidAndOtherFailuresCannotBeAcknowledged()throws Exception{
        for(String broken:new String[]{guardFailure().replace("FAILED_NO_TARGET_WRITE","UNKNOWN"),guardFailure().replace("FAILED_NO_TARGET_WRITE","PENDING"),
            guardFailure().replace("\"exit\":16","\"exit\":18"),guardFailure().replace("\"timeout\":false","\"timeout\":true"),
            guardFailure().replace("CLBV_UID=0","CLBV_UID=2000"),guardFailure().replace("CLBV_NO_TARGET_WRITE","CLBV_TARGET_WRITE_ATTEMPTED")}){
            Memory m=new Memory();m.value=broken;BootVideoTransactions t=new BootVideoTransactions(r->{fail();return null;},m,Runnable::run,Runnable::run,10106);
            assertFalse(t.canAcknowledgeNoWrite());assertFalse(t.acknowledgeNoWrite(()->fail()));assertFalse(t.canApply());}
    }
    @Test public void openingAndReadingReceiptNeverRunsRoot(){Memory m=new Memory();Queue q=new Queue();List<Object> calls=new ArrayList<>();BootVideoTransactions t=new BootVideoTransactions(r->{calls.add(r);return null;},m,q,Runnable::run,10106);assertTrue(t.canApply());assertFalse(t.canRestore());t.message();t.exportReceipt();assertTrue(calls.isEmpty());assertTrue(q.pending.isEmpty());assertEquals("",m.value);}
    @Test public void durablePendingPrecedesSingleDispatchAndRestoreReturnsBefore(){Memory m=new Memory();Queue q=new Queue();List<BootVideoTransactions.Request> calls=new ArrayList<>();BootVideoTransactions t=new BootVideoTransactions(r->{assertTrue(m.value.contains("PENDING"));calls.add(r);return new BootVideoTransactions.Result(0,false,complete(r));},m,q,Runnable::run,10106);assertTrue(t.apply(asset()));assertFalse(t.apply(asset()));assertEquals(1,q.pending.size());q.run();assertEquals(1,calls.size());assertTrue(t.canRestore());assertTrue(t.restore());assertFalse(t.restore());q.run();assertEquals(2,calls.size());assertFalse(calls.get(1).apply);assertEquals(calls.get(0).id,calls.get(1).id);assertTrue(t.canApply());assertFalse(t.canRestore());assertTrue(m.value.contains("RESTORED"));}
    @Test public void storageFailurePreventsWorkerOrRoot(){Memory m=new Memory();m.fail=true;Queue q=new Queue();BootVideoTransactions t=new BootVideoTransactions(r->{fail();return null;},m,q,Runnable::run,10106);assertFalse(t.apply(asset()));assertTrue(q.pending.isEmpty());assertFalse(t.canApply());}
    @Test public void pendingSurvivesProcessDeathWithoutResubmission(){Memory m=new Memory();Queue q=new Queue();BootVideoTransactions t=new BootVideoTransactions(r->{fail();return null;},m,q,Runnable::run,10106);t.apply(asset());BootVideoTransactions reopened=new BootVideoTransactions(r->{fail();return null;},m,new Queue(),Runnable::run,10106);assertFalse(reopened.canApply());assertFalse(reopened.restore());assertFalse(reopened.apply(asset()));}
    @Test public void timeoutWithSuccessMarkersStillLocksDurably(){Memory m=new Memory();Queue q=new Queue();BootVideoTransactions t=new BootVideoTransactions(r->new BootVideoTransactions.Result(0,true,complete(r)),m,q,Runnable::run,10106);t.apply(asset());q.run();assertFalse(t.canApply());assertTrue(m.value.contains("UNKNOWN"));BootVideoTransactions again=new BootVideoTransactions(r->{fail();return null;},m,q,Runnable::run,10106);assertFalse(again.apply(asset()));assertTrue(q.pending.isEmpty());}
    @Test public void wrongReadbackOrMissingRootProofCannotBecomeApplied(){for(String broken:new String[]{"CLBV_UID=0","CLBV_AFTER_SHA="+SHA,"CLBV_TX="}){Memory m=new Memory();Queue q=new Queue();BootVideoTransactions t=new BootVideoTransactions(r->new BootVideoTransactions.Result(0,false,complete(r).replace(broken,"BROKEN")),m,q,Runnable::run,10106);t.apply(asset());q.run();assertFalse(t.canRestore());assertFalse(t.canApply());}}
    @Test public void explicitNoTargetWriteFailureAlsoStopsTheLane(){Memory m=new Memory();Queue q=new Queue();BootVideoTransactions t=new BootVideoTransactions(r->new BootVideoTransactions.Result(18,false,"CLBV_NO_TARGET_WRITE\n"),m,q,Runnable::run,10106);t.apply(asset());q.run();assertFalse(t.canApply());assertTrue(m.value.contains("FAILED_NO_TARGET_WRITE"));assertTrue(q.pending.isEmpty());}
    @Test public void receiptWriteFailureAfterRootLeavesPersistedPending(){Memory m=new Memory();Queue q=new Queue();BootVideoTransactions t=new BootVideoTransactions(r->{m.fail=true;return new BootVideoTransactions.Result(0,false,complete(r));},m,q,Runnable::run,10106);t.apply(asset());q.run();assertFalse(t.canApply());assertTrue(m.value.contains("PENDING"));}
    @Test public void malformedReceiptAndExecutorFailureNeverAutomaticallyRetry(){Memory m=new Memory();m.value="broken";BootVideoTransactions t=new BootVideoTransactions(r->{fail();return null;},m,Runnable::run,Runnable::run,10106);assertFalse(t.canApply());Memory m2=new Memory();BootVideoTransactions t2=new BootVideoTransactions(r->{fail();return null;},m2,r->{throw new RuntimeException();},Runnable::run,10106);t2.apply(asset());assertFalse(t2.canApply());assertTrue(m2.value.contains("PENDING"));}
    @Test public void restoreNeedsPreviouslyVerifiedApply(){Memory m=new Memory();Queue q=new Queue();BootVideoTransactions t=new BootVideoTransactions(r->{fail();return null;},m,q,Runnable::run,10106);assertFalse(t.restore());assertTrue(q.pending.isEmpty());}
    @Test public void shellParametersRejectInjectionAndForeignUid()throws Exception {
        String template="@TX@ @OP@ @SOURCE@ @SHA@ @UID@";
        String source=asset().output.getAbsolutePath();
        assertTrue(BootVideoRoot.script(template,new BootVideoTransactions.Request(true,ID,SHA,source,10106)).contains("APPLY"));
        for(BootVideoTransactions.Request r:new BootVideoTransactions.Request[]{new BootVideoTransactions.Request(true,ID,SHA,source+"'; touch /tmp/pwn; '",10106),new BootVideoTransactions.Request(true,"../evil",SHA,source,10106),new BootVideoTransactions.Request(true,ID,SHA+"\n",source,10106),new BootVideoTransactions.Request(true,ID,SHA,source,2000)}){
            try{BootVideoRoot.script(template,r);fail();}catch(java.io.IOException expected){}
        }
    }
    @Test public void blockedScriptInputCannotPreventTimeoutOrCauseAnotherProcess()throws Exception {
        File dir=java.nio.file.Files.createTempDirectory("video-fake-su-").toFile();File log=new File(dir,"calls"),fake=new File(dir,"fake-su");
        java.nio.file.Files.write(fake.toPath(),("#!/bin/sh\nprintf 'called\\n' >> '"+log.getAbsolutePath()+"'\nsleep 2\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));fake.setExecutable(true);
        long start=System.nanoTime();BootVideoTransactions.Result result=BootVideoRoot.execute(fake.getAbsolutePath(),"x".repeat(200000),500);
        assertTrue(result.timeout);assertTrue((System.nanoTime()-start)/1000000<1600);
        assertEquals(java.util.List.of("called"),java.nio.file.Files.readAllLines(log.toPath()));
    }
    @Test public void largeOutputIsDrainedAndBoundedWithoutRoot()throws Exception {
        File fake=File.createTempFile("video-fake-su-output-",".sh");java.nio.file.Files.write(fake.toPath(),"#!/bin/sh\ni=0\nwhile [ $i -lt 20000 ]; do printf x; i=$((i+1)); done\nexit 7\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));fake.setExecutable(true);
        BootVideoTransactions.Result result=BootVideoRoot.execute(fake.getAbsolutePath(),"",5000);
        assertFalse(result.timeout);assertEquals(7,result.exit);assertEquals(8000,result.output.length());
    }

    @Test public void newUidCanApplyRestoreButForeignReceiptAndDuplicateProofStayBlocked()throws Exception{
        Memory m=new Memory();Queue q=new Queue();
        BootVideoTransactions t=new BootVideoTransactions(r->new BootVideoTransactions.Result(0,false,complete(r).replace("10106","10234")),m,q,Runnable::run,10234);
        assertTrue(t.apply(asset()));q.run();assertTrue(t.canRestore());
        BootVideoTransactions foreign=new BootVideoTransactions(r->{fail();return null;},m,q,Runnable::run,10106);assertFalse(foreign.canApply());assertFalse(foreign.canRestore());
        assertTrue(t.restore());q.run();assertTrue(m.value.contains("RESTORED"));
        BootVideoTransactions.Request r=new BootVideoTransactions.Request(true,ID,SHA,"-",10234);
        assertFalse(new BootVideoTransactions.Result(0,false,complete(r).replace("10106","10234")+"CLBV_UID=0\n").complete(r));
    }
}
