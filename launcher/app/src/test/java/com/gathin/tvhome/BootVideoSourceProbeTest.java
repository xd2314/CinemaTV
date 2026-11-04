package com.gathin.tvhome;

import java.io.IOException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootVideoSourceProbeTest {
    private static final String ID=BootVideoTransactionsTest.ID,SHA=BootVideoTransactionsTest.SHA;
    static JSONObject valid()throws Exception{
        return new JSONObject().put("schema",1).put("state","FAILED_NO_TARGET_WRITE").put("action","APPLY").put("transaction",ID).put("candidateSha",SHA).put("clientUid",10106).put("route",BootVideoRoute.AUTO).put("exit",20).put("timeout",false)
            .put("diagnostic","CLBV_UID=0\nCLBV_CLIENT_UID=10106\nCLBV_TX="+ID+"\nCLBV_ROUTE=PROPERTY_MP4_V1\nCLBV_NO_TARGET_WRITE\n");
    }
    @Test public void onlyExactNoWriteSourceFailureCanOfferSeparateProbe()throws Exception{
        assertTrue(BootVideoSourceProbe.eligible(valid().toString(),BootVideoTransactionsTest.asset(),10106));
        assertTrue(BootVideoSourceProbe.eligible(valid().put("route",BootVideoRoute.PROPERTY).toString(),BootVideoTransactionsTest.asset(),10106));
        assertFalse(BootVideoSourceProbe.eligible(valid().toString(),null,10106));
        assertFalse(BootVideoSourceProbe.eligible(valid().toString(),BootVideoTransactionsTest.asset(),10234));
        assertFalse(BootVideoSourceProbe.eligible("broken",BootVideoTransactionsTest.asset(),10106));
    }
    @Test public void pendingUnknownOtherFailuresForeignOrMalformedProofStayIneligible()throws Exception{
        for(String state:new String[]{"PENDING","UNKNOWN","IDLE","APPLIED","RESTORED"})assertFalse(BootVideoSourceProbe.eligible(valid().put("state",state).toString(),BootVideoTransactionsTest.asset(),10106));
        for(JSONObject bad:new JSONObject[]{valid().put("exit",16),valid().put("timeout",true),valid().put("lastId",ID),valid().put("clientUid",10234),valid().put("candidateSha","a".repeat(64)),valid().put("route",BootVideoRoute.LOCAL),valid().put("action","RESTORE"),valid().put("transaction","../bad"),valid().put("schema",2)})
            assertFalse(BootVideoSourceProbe.eligible(bad.toString(),BootVideoTransactionsTest.asset(),10106));
        for(String proof:new String[]{valid().getString("diagnostic")+"CLBV_TARGET_WRITE_ATTEMPTED\n",valid().getString("diagnostic")+"CLBV_UID=0\n",valid().getString("diagnostic").replace("CLBV_UID=0","CLBV_UID=2000"),valid().getString("diagnostic").replace("PROPERTY_MP4_V1","LOCAL_FILE_V1")})
            assertFalse(BootVideoSourceProbe.eligible(valid().put("diagnostic",proof).toString(),BootVideoTransactionsTest.asset(),10106));
    }
    @Test public void payloadContainsOnlyFixedReadCommandsAndNoMutatingFallback()throws Exception{
        String source=BootVideoTransactionsTest.asset().output.getPath();
        String script=BootVideoSourceProbe.payload(new BootVideoTransactions.Request(true,ID,SHA,source,10106));
        assertFalse(script.contains("@"));assertTrue(script.contains("CLVS_COMPLETE=1"));assertTrue(script.contains("/proc/self/status"));assertTrue(script.contains("/system/bin/sha256sum \"$SOURCE\""));
        for(String forbidden:new String[]{"setprop","chmod","chown","chcon","mkdir","rmdir","/system/bin/rm","/system/bin/mv","reboot","remount","su 0",">","boot_video_transaction"})assertFalse(forbidden,script.contains(forbidden));
    }
    @Test public void probeSourceCannotInjectShellOrInspectForeignPaths()throws Exception{
        for(BootVideoTransactions.Request bad:new BootVideoTransactions.Request[]{
            new BootVideoTransactions.Request(false,ID,SHA,null,10106),new BootVideoTransactions.Request(true,ID,SHA,"/other/file",10106),
            new BootVideoTransactions.Request(true,ID,SHA,BootVideoTransactionsTest.asset().output.getPath()+"';evil",10106),
            new BootVideoTransactions.Request(true,ID,SHA,BootVideoTransactionsTest.asset().output.getPath(),2000)})
            try{BootVideoSourceProbe.payload(bad);fail();}catch(IOException expected){}
    }
}
