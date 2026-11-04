package com.gathin.tvhome;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootVideoRouteTest {
    private static final String ID=BootVideoTransactionsTest.ID,SHA=BootVideoTransactionsTest.SHA,BEFORE=BootVideoTransactionsTest.BEFORE;
    private static final String STOCK_SHA="b".repeat(64);
    private static BootVideoTransactions.Request request(boolean apply,String route){
        return new BootVideoTransactions.Request(apply,ID,SHA,BootVideoTransactionsTest.asset().output.getPath(),10106,route,BEFORE,BootVideoRoute.ACTIVE,STOCK_SHA);
    }
    private static String proof(BootVideoTransactions.Request r){
        return BootVideoTransactionsTest.complete(r)+"CLBV_ROUTE=PROPERTY_MP4_V1\nCLBV_PROPERTY_BEFORE="+BootVideoRoute.ACTIVE+"\nCLBV_PROPERTY_AFTER="+BootVideoRoute.ACTIVE+"\nCLBV_STOCK_SHA="+STOCK_SHA+"\n";
    }
    @Test public void propertyRouteWinsAndLegacyRequiresEmptyProperty()throws Exception{
        assertEquals(BootVideoRoute.PROPERTY,BootVideoRoute.select(BootVideoRoute.STOCK,false));
        assertEquals(BootVideoRoute.PROPERTY,BootVideoRoute.select(BootVideoRoute.ACTIVE,true));
        assertEquals(BootVideoRoute.LOCAL,BootVideoRoute.select("",true));
        for(String value:new String[]{"","/other/movie.mp4",BootVideoRoute.STOCK+";evil",BootVideoRoute.STOCK+"\n"}){
            try{BootVideoRoute.select(value,false);fail();}catch(IOException expected){}
            if(!value.isEmpty())try{BootVideoRoute.select(value,true);fail();}catch(IOException expected){}
        }
    }
    @Test public void propertyProofNeedsExactRoutePathsStockHashAndRestoreBindings(){
        for(boolean apply:new boolean[]{true,false}){
            BootVideoTransactions.Request r=request(apply,BootVideoRoute.PROPERTY);String valid=proof(r);
            assertTrue(new BootVideoTransactions.Result(0,false,valid).complete(r));
            assertFalse(new BootVideoTransactions.Result(0,true,valid).complete(r));
            assertFalse(new BootVideoTransactions.Result(9,false,valid).complete(r));
            for(String broken:new String[]{valid.replace("PROPERTY_MP4_V1","LOCAL_FILE_V1"),valid.replace("CLBV_STOCK_SHA="+STOCK_SHA,"CLBV_STOCK_SHA=ABSENT"),
                valid.replace("CLBV_PROPERTY_BEFORE="+BootVideoRoute.ACTIVE,"CLBV_PROPERTY_BEFORE=/other"),valid.replace("CLBV_PROPERTY_AFTER="+BootVideoRoute.ACTIVE,"CLBV_PROPERTY_AFTER=/other"),
                valid+"CLBV_ROUTE=PROPERTY_MP4_V1\n",valid+"CLBV_STOCK_SHA="+STOCK_SHA+"\n",valid.replace("CLBV_ROUTE=PROPERTY_MP4_V1\n","")})
                assertFalse(broken,new BootVideoTransactions.Result(0,false,broken).complete(r));
        }
        BootVideoTransactions.Request restore=request(false,BootVideoRoute.PROPERTY);
        assertFalse(new BootVideoTransactions.Result(0,false,proof(restore).replace(STOCK_SHA,"c".repeat(64))).complete(restore));
        String stockBefore=proof(request(true,BootVideoRoute.PROPERTY)).replace("CLBV_PROPERTY_BEFORE="+BootVideoRoute.ACTIVE,"CLBV_PROPERTY_BEFORE="+BootVideoRoute.STOCK);
        assertFalse(new BootVideoTransactions.Result(0,false,stockBefore).complete(request(true,BootVideoRoute.PROPERTY)));
        assertTrue(new BootVideoTransactions.Result(0,false,stockBefore.replace("CLBV_STOCK_SHA="+STOCK_SHA,"CLBV_STOCK_SHA="+BEFORE)).complete(request(true,BootVideoRoute.PROPERTY)));
    }
    @Test public void legacyAndAutoCannotSilentlyReinterpretHistoricalReceipts(){
        BootVideoTransactions.Request local=request(true,BootVideoRoute.LOCAL),auto=request(true,BootVideoRoute.AUTO);
        assertTrue(new BootVideoTransactions.Result(0,false,BootVideoTransactionsTest.complete(local)).complete(local));
        assertFalse(new BootVideoTransactions.Result(0,false,proof(local)).complete(local));
        assertFalse(new BootVideoTransactions.Result(0,false,BootVideoTransactionsTest.complete(auto)).complete(auto));
        assertTrue(new BootVideoTransactions.Result(0,false,proof(auto)).complete(auto));
        assertFalse(new BootVideoTransactions.Result(0,false,BootVideoTransactionsTest.complete(local)+"CLBV_ROUTE=LOCAL_FILE_V1\nCLBV_ROUTE=LOCAL_FILE_V1\n").complete(local));
    }
    @Test public void savedRouteIsRetainedAcrossProcessDeathAndRestoreDoesNotRediscover()throws Exception{
        BootVideoTransactionsTest.Memory memory=new BootVideoTransactionsTest.Memory();List<BootVideoTransactions.Request> calls=new ArrayList<>();
        BootVideoTransactions first=new BootVideoTransactions(r->{calls.add(r);assertEquals(BootVideoRoute.AUTO,r.route);return new BootVideoTransactions.Result(0,false,proof(r));},memory,Runnable::run,Runnable::run,10106,true);
        assertTrue(first.apply(BootVideoTransactionsTest.asset()));assertTrue(first.canRestore());
        JSONObject saved=new JSONObject(memory.value);assertEquals(BootVideoRoute.PROPERTY,saved.getString("lastRoute"));assertEquals(BootVideoRoute.ACTIVE,saved.getString("lastBeforeProperty"));assertEquals(STOCK_SHA,saved.getString("lastStockSha"));
        BootVideoTransactions reopened=new BootVideoTransactions(r->{calls.add(r);assertEquals(BootVideoRoute.PROPERTY,r.route);assertEquals(BEFORE,r.restoreBefore);assertEquals(BootVideoRoute.ACTIVE,r.restoreProperty);assertEquals(STOCK_SHA,r.restoreStock);return new BootVideoTransactions.Result(0,false,proof(r));},memory,Runnable::run,Runnable::run,10106,true);
        assertTrue(reopened.restore());assertEquals(2,calls.size());assertFalse(reopened.canRestore());assertTrue(reopened.canApply());
    }
    @Test public void replacingAppliedVideoPinsBothNewAndHistoricalRoutes()throws Exception{
        for(boolean property:new boolean[]{true,false}){
            BootVideoTransactionsTest.Memory memory=new BootVideoTransactionsTest.Memory();List<String> routes=new ArrayList<>();
            BootVideoTransactions first=new BootVideoTransactions(r->{routes.add(r.route);return new BootVideoTransactions.Result(0,false,property?proof(r):BootVideoTransactionsTest.complete(r)+"CLBV_ROUTE=LOCAL_FILE_V1\n");},memory,Runnable::run,Runnable::run,10106,true);
            assertTrue(first.apply(BootVideoTransactionsTest.asset()));assertTrue(first.canRestore());
            // Also cover historical local receipts that predate lastRoute.
            if(!property){JSONObject historical=new JSONObject(memory.value);historical.remove("lastRoute");memory.value=historical.toString();}
            BootVideoTransactions again=new BootVideoTransactions(r->{routes.add(r.route);return new BootVideoTransactions.Result(0,false,property?proof(r):BootVideoTransactionsTest.complete(r));},memory,Runnable::run,Runnable::run,10106,true);
            assertTrue(again.apply(BootVideoTransactionsTest.asset()));assertTrue(again.canRestore());
            assertEquals(java.util.List.of(BootVideoRoute.AUTO,property?BootVideoRoute.PROPERTY:BootVideoRoute.LOCAL),routes);
        }
    }
    @Test public void invalidSavedPropertyRouteAndOldPendingRemainLocked()throws Exception{
        JSONObject base=new JSONObject().put("schema",1).put("state","APPLIED").put("clientUid",10106).put("lastId",ID).put("lastSha",SHA).put("lastBefore",BEFORE).put("lastRoute",BootVideoRoute.PROPERTY).put("lastBeforeProperty",BootVideoRoute.ACTIVE).put("lastStockSha",STOCK_SHA);
        for(String broken:new String[]{new JSONObject(base.toString()).put("lastRoute","UNKNOWN_ROUTE").toString(),new JSONObject(base.toString()).put("lastBeforeProperty","/other").toString(),new JSONObject(base.toString()).put("lastStockSha","ABSENT").toString(),new JSONObject(base.toString()).put("lastBeforeProperty",BootVideoRoute.STOCK).toString(),new JSONObject(base.toString()).put("state","PENDING").toString(),new JSONObject(base.toString()).put("state","UNKNOWN").toString()}){
            BootVideoTransactionsTest.Memory memory=new BootVideoTransactionsTest.Memory();memory.value=broken;
            BootVideoTransactions t=new BootVideoTransactions(r->{fail();return null;},memory,Runnable::run,Runnable::run,10106,true);
            assertFalse(t.canApply());assertFalse(t.canRestore());assertFalse(t.canAcknowledgeNoWrite());assertEquals(broken,memory.value);
        }
    }
    @Test public void propertyShellParametersCannotInjectOrDropRestoreBindings()throws Exception{
        String template="@TX@ @OP@ @SOURCE@ @SHA@ @UID@ @EXPECTED_PROPERTY@ @RESTORE_BEFORE@ @RESTORE_PROPERTY@ @RESTORE_STOCK@";
        String payload=BootVideoRoot.propertyScript(template,request(true,BootVideoRoute.PROPERTY),BootVideoRoute.STOCK);
        assertFalse(payload.contains("@"));assertTrue(payload.endsWith(" - - -"));
        assertTrue(BootVideoRoot.propertyScript(template,request(false,BootVideoRoute.PROPERTY),BootVideoRoute.ACTIVE).endsWith(BEFORE+" "+BootVideoRoute.ACTIVE+" "+STOCK_SHA));
        for(String property:new String[]{"",BootVideoRoute.STOCK+"';evil",BootVideoRoute.STOCK+"\n","/other"})try{BootVideoRoot.propertyScript(template,request(true,BootVideoRoute.PROPERTY),property);fail();}catch(IOException expected){}
        try{BootVideoRoot.propertyScript(template,request(false,BootVideoRoute.PROPERTY),BootVideoRoute.STOCK);fail();}catch(IOException expected){}
        try{BootVideoRoot.propertyScript(template,request(true,BootVideoRoute.LOCAL),BootVideoRoute.STOCK);fail();}catch(IOException expected){}
        BootVideoTransactions.Request broken=new BootVideoTransactions.Request(false,ID,SHA,null,10106,BootVideoRoute.PROPERTY,BEFORE,"/other",STOCK_SHA);
        try{BootVideoRoot.propertyScript(template,broken,BootVideoRoute.ACTIVE);fail();}catch(IOException expected){}
    }
}
