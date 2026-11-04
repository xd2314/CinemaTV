package com.gathin.tvhome;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootVideoReaderProfileTest {
    @Test public void exactLibraryAndBothServiceContractsAreRequired(){
        String[] expected={BootVideoReaderProfile.LIB_SHA,BootVideoReaderProfile.MEDIA_LIB_SHA,BootVideoReaderProfile.BOOT_SHA,BootVideoReaderProfile.MEDIA_SHA};
        assertTrue(BootVideoReaderProfile.matches(expected[0],expected[1],expected[2],expected[3]));
        for(int i=0;i<expected.length;i++){String[] bad=expected.clone();bad[i]="0".repeat(64);assertFalse(BootVideoReaderProfile.matches(bad[0],bad[1],bad[2],bad[3]));bad[i]=null;assertFalse(BootVideoReaderProfile.matches(bad[0],bad[1],bad[2],bad[3]));}
    }
    @Test public void metadataProofMustBeUniqueAndMatchMediaOwnerReadOnlyMode(){
        String proof="CLBV_READER_PROFILE="+BootVideoReaderProfile.NAME+"\nCLBV_TARGET_METADATA_AFTER="+BootVideoReaderProfile.METADATA+"\n";
        assertTrue(BootVideoReaderProfile.proof(new BootVideoTransactions.Result(0,false,proof)));
        for(String bad:new String[]{proof.replace("1013:0:400","0:0:644"),proof.replace("1013:0:400","1014:0:400"),proof+"CLBV_TARGET_METADATA_AFTER=1013:0:400\n",proof+"CLBV_READER_PROFILE="+BootVideoReaderProfile.NAME+"\n",""})
            assertFalse(BootVideoReaderProfile.proof(new BootVideoTransactions.Result(0,false,bad)));
    }
    @Test public void appliedReaderProfileIsDurableAndInvalidExplicitProofCannotPass()throws Exception{
        BootVideoTransactionsTest.Memory memory=new BootVideoTransactionsTest.Memory();BootVideoTransactionsTest.Queue queue=new BootVideoTransactionsTest.Queue();
        BootVideoTransactions t=new BootVideoTransactions(r->{String out=BootVideoTransactionsTest.complete(r)+"CLBV_ROUTE=PROPERTY_MP4_V1\nCLBV_PROPERTY_BEFORE="+BootVideoRoute.ACTIVE+"\nCLBV_PROPERTY_AFTER="+BootVideoRoute.ACTIVE+"\nCLBV_STOCK_SHA="+"b".repeat(64)+"\nCLBV_READER_PROFILE="+BootVideoReaderProfile.NAME+"\nCLBV_TARGET_METADATA_AFTER=1013:0:400\n";
            assertTrue(new BootVideoTransactions.Result(0,false,out).complete(r));assertFalse(new BootVideoTransactions.Result(0,false,out.replace("1013:0:400","0:0:644")).complete(r));return new BootVideoTransactions.Result(0,false,out);},memory,queue,Runnable::run,10106,true);
        assertTrue(t.apply(BootVideoTransactionsTest.asset()));queue.run();assertTrue(t.canRestore());assertEquals(BootVideoReaderProfile.NAME,new JSONObject(memory.value).getString("lastReaderProfile"));
        BootVideoTransactions reopened=new BootVideoTransactions(r->{throw new AssertionError("Reopening never runs Root");},memory,queue,Runnable::run,10106,true);assertTrue(reopened.canRestore());assertTrue(queue.pending.isEmpty());
        memory.value=new JSONObject(memory.value).put("lastReaderProfile","unknown").toString();assertFalse(new BootVideoTransactions(r->{fail();return null;},memory,queue,Runnable::run,10106,true).canApply());
    }
}
