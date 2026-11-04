package com.gathin.tvhome;
import org.json.JSONObject;
import org.junit.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=29)
public class BootLogoApplyFlowTest {
 String journal="";boolean failSave;BootLogoApplyFlow flow;Fake host;
 static final String OLD="11111111-1111-4111-8111-111111111111",NEW="22222222-2222-4222-8222-222222222222",SHA="a".repeat(64);
 final class Fake implements BootLogoApplyFlow.Host {
  JSONObject state=new JSONObject();int reads,restores,applies,verifies,exports;boolean busy,allowed=true;String exportRun;
  public JSONObject snapshot(){return state;}public boolean allowed(){return allowed;}public boolean busy(){return busy;}
  void put(String k,Object v){try{state.put(k,v);}catch(Exception e){throw new AssertionError(e);}}
  void claim(String stage){try{assertEquals(stage,new JSONObject(journal).getString("step"));}catch(Exception e){throw new AssertionError(e);}busy=true;}
  public boolean prepare(){claim("READ");reads++;put("state","READ_PENDING");put("transaction",NEW);put("sourceSha",SHA);put("backupExported",false);return true;}
  public boolean restore(){claim("RESTORE");restores++;put("state","RESTORE_PENDING");return true;}
  public boolean apply(){claim("APPLY");applies++;put("state","APPLY_PENDING");return true;}
  public void verifyBackup(JSONObject initial){verifies++;}public void exportBackup(String id){exports++;exportRun=id;}public void changed(String text){}
  void complete(String s){busy=false;put("state",s);flow.advance();}
 }
 void setup(String state,boolean same,boolean exported){host=new Fake();host.put("state",state);host.put("transaction",state.equals("IDLE")?"":OLD);host.put("sourceSha",same?SHA:"b".repeat(64));host.put("backupExported",exported);flow=new BootLogoApplyFlow(host,s->{if(failSave)return false;journal=s;return true;},SHA);}
 @Test public void verifiedReadyUsesOneApplyWithoutReadOrRestore(){setup("READY",true,true);assertTrue(flow.start());assertFalse(flow.start());assertEquals(1,host.verifies);flow.verified(true,false);assertEquals(1,host.applies);flow.advance();flow.verified(true,false);assertEquals(1,host.applies);host.complete("APPLIED");flow.advance();assertFalse(flow.active());assertTrue(journal.contains("COMPLETE"));assertEquals(0,host.reads+host.restores);}
 @Test public void replacementRunsRestoreReadVerifyApplyInOrder(){setup("APPLIED",false,true);flow.start();assertEquals(1,host.restores);host.complete("RESTORED");assertEquals(1,host.reads);host.complete("READY");assertEquals(1,host.verifies);host.put("backupExported",true);flow.verified(true,false);assertEquals(1,host.applies);host.complete("APPLIED");assertFalse(flow.active());}
 @Test public void missingExportWaitsThenContinuesOnce(){setup("IDLE",false,false);flow.start();host.complete("READY");flow.verified(true,true);assertEquals(1,host.exports);assertEquals(0,host.applies);host.put("backupExported",true);flow.exported("stale",true);assertEquals(0,host.applies);flow.exported(host.exportRun,true);flow.exported(host.exportRun,true);assertEquals(1,host.applies);host.complete("APPLIED");assertFalse(flow.active());}
 @Test public void cancellingExportAfterRestoreDoesNotApplyOrUndoAgain(){setup("APPLIED",false,false);flow.start();host.complete("RESTORED");host.complete("READY");flow.verified(true,true);flow.exported(host.exportRun,false);assertFalse(flow.active());assertEquals(1,host.restores);assertEquals(0,host.applies);assertTrue(flow.message().contains("不自動回復"));}
 @Test public void unknownReadOrRestoreStopsWithoutNextStep(){for(String start:new String[]{"IDLE","APPLIED"}){setup(start,false,false);flow.start();host.complete("UNKNOWN");assertFalse(flow.active());assertEquals(0,host.applies+host.verifies);if(start.equals("APPLIED"))assertEquals(0,host.reads);}}
 @Test public void sourceOrTransactionChangeStopsBeforeWrite(){setup("READY",true,true);flow.start();host.put("transaction",NEW);flow.verified(true,false);assertFalse(flow.active());assertEquals(0,host.applies);}
 @Test public void persistenceFailureCannotDispatchNextRoot(){setup("READY",true,true);flow.start();failSave=true;flow.verified(true,false);assertFalse(flow.active());assertEquals(0,host.applies);}
 @Test public void closeNeverResumesAfterInflightReadCompletes(){setup("IDLE",false,false);flow.start();flow.close();host.complete("READY");flow.verified(true,false);assertEquals(0,host.applies+host.verifies);assertFalse(flow.active());}
 @Test public void refusedPermissionOrInvalidBackupStops(){setup("READY",true,true);flow.start();flow.verified(false,false);assertFalse(flow.active());assertEquals(0,host.applies);setup("READY",true,true);flow.start();host.allowed=false;flow.verified(true,false);assertEquals(0,host.applies);}
 @Test public void unknownAndPendingNeverStart(){for(String s:new String[]{"UNKNOWN","READ_PENDING","APPLY_PENDING","FAILED_NO_TARGET_WRITE"}){setup(s,false,false);assertFalse(flow.start());assertEquals(0,host.reads+host.restores+host.applies);}}
}
