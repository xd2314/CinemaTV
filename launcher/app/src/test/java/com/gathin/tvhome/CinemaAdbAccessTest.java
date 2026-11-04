package com.gathin.tvhome;

import org.junit.Test;
import java.util.*;
import java.util.concurrent.Executor;
import static org.junit.Assert.*;

public class CinemaAdbAccessTest {
 static class Memory implements CinemaAdbAccess.Store {
  String state="";boolean writable=true;List<String> writes=new ArrayList<>();
  public String read(){return state;}public boolean write(String s){writes.add(s);if(!writable)return false;state=s;return true;}
 }
 static class Queue implements Executor {List<Runnable> pending=new ArrayList<>();public void execute(Runnable r){pending.add(r);}void run(){pending.remove(0).run();}}
 static class Fake implements CinemaAdbAccess.Runner {
  List<CinemaAdbAccess.Action> calls=new ArrayList<>();CinemaAdbAccess.Result answer=new CinemaAdbAccess.Result(0,false,"0\n");Memory store;
  public CinemaAdbAccess.Result run(CinemaAdbAccess.Action a){if(a==CinemaAdbAccess.Action.ENABLE)assertEquals("PENDING",store.state);calls.add(a);return answer;}
 }
 Memory store=new Memory();Queue worker=new Queue();Fake runner=new Fake();
 CinemaAdbAccess access(){runner.store=store;return new CinemaAdbAccess(runner,store,worker,Runnable::run);}
 @Test public void openingModelNeverExecutesOrRequestsRoot(){CinemaAdbAccess a=access();assertTrue(a.canEnable());assertFalse(a.busy());assertTrue(runner.calls.isEmpty());assertTrue(store.writes.isEmpty());}
 @Test public void onlyEnableActionExists(){assertArrayEquals(new CinemaAdbAccess.Action[]{CinemaAdbAccess.Action.ENABLE},CinemaAdbAccess.Action.values());}
 @Test public void receiptIsDurableBeforeDispatchAndDoublePressDoesNotQueue(){CinemaAdbAccess a=access();assertTrue(a.enable());assertEquals("PENDING",store.state);assertFalse(a.enable());assertEquals(1,worker.pending.size());runner.answer=new CinemaAdbAccess.Result(0,false,"CYT_PORT_OK\nCYT_STOP_OK\nCYT_START_OK\n");worker.run();assertEquals("COMPLETED",store.state);assertEquals(1,runner.calls.size());assertTrue(worker.pending.isEmpty());assertTrue(a.canEnable());}
 @Test public void receiptFailurePreventsAnyPrivilegedEnable(){CinemaAdbAccess a=access();store.writable=false;assertFalse(a.enable());assertTrue(runner.calls.isEmpty());assertTrue(worker.pending.isEmpty());}
 @Test public void pendingReceiptAfterProcessDeathBlocksWrites(){store.state="PENDING";CinemaAdbAccess a=access();assertFalse(a.canEnable());assertFalse(a.enable());assertTrue(runner.calls.isEmpty());assertEquals("PENDING",store.state);}
 @Test public void partialFailureNeverAutomaticallyRetried(){CinemaAdbAccess a=access();a.enable();runner.answer=new CinemaAdbAccess.Result(22,false,"CYT_PORT_OK\nCYT_STOP_OK\n");worker.run();assertEquals("UNKNOWN",store.state);assertFalse(a.enable());assertFalse(access().enable());assertEquals(1,runner.calls.size());}
 @Test public void evenCompletedMarkersWithTimeoutRemainUnknown(){CinemaAdbAccess a=access();a.enable();runner.answer=new CinemaAdbAccess.Result(-1,true,"CYT_START_OK\n");worker.run();assertEquals("UNKNOWN",store.state);assertFalse(a.enable());}
 @Test public void uidFailureInsideEnableAllowsOnlyExplicitNewAttempt(){CinemaAdbAccess a=access();a.enable();runner.answer=new CinemaAdbAccess.Result(11,false,"");worker.run();assertEquals("NO_WRITE",store.state);assertTrue(a.canEnable());assertEquals(1,runner.calls.size());assertTrue(worker.pending.isEmpty());}
 @Test public void uidCommandFailureIsNoWrite(){CinemaAdbAccess a=access();a.enable();runner.answer=new CinemaAdbAccess.Result(10,false,"");worker.run();assertEquals("NO_WRITE",store.state);assertTrue(worker.pending.isEmpty());}
 @Test public void timeoutWaitingForRootIsUnknownAndNotRetried(){CinemaAdbAccess a=access();a.enable();runner.answer=new CinemaAdbAccess.Result(-1,true,"");worker.run();assertEquals("UNKNOWN",store.state);assertFalse(a.canEnable());assertTrue(worker.pending.isEmpty());}
 @Test public void completedReceiptFailureLocksFurtherDispatch(){CinemaAdbAccess a=access();a.enable();runner.answer=new CinemaAdbAccess.Result(0,false,"CYT_START_OK\n");store.writable=false;worker.run();assertFalse(a.canEnable());assertEquals("PENDING",store.state);assertFalse(access().enable());}
 @Test public void removedObserverIsNotCalledOnAsyncCompletion(){CinemaAdbAccess a=access();int[] callbacks={0};Runnable cb=()->callbacks[0]++;a.observe(cb);a.enable();assertEquals(1,callbacks[0]);a.removeObserver(cb);worker.run();assertEquals(1,callbacks[0]);}
 @Test public void fixedScriptGuardsEveryMutationAndDoesNotWriteOtherSettings(){
  String s=CinemaAdbProcess.ENABLE_SCRIPT;
  assertTrue(s.indexOf("[ \"$uid\" = 0 ]")<s.indexOf("/system/bin/setprop"));assertTrue(s.contains("setprop service.adb.tcp.port 5555 || exit 20"));assertTrue(s.contains("stop adbd || exit 21"));assertTrue(s.contains("start adbd || exit 22"));
  for(String forbidden:new String[]{"persist.","reboot","setenforce","set-home","pm ","tcpip","chmod"})assertFalse(forbidden,s.contains(forbidden));
 }
 @Test public void boundedProcessRunnerDrainsHugeOutputAndReturnsExitCode()throws Exception{
  CinemaAdbAccess.Result r=CinemaAdbProcess.execute(new String[]{"/bin/sh"},"i=0; while [ $i -lt 10000 ]; do echo abcdefghijklmnopqrstuvwxyz; i=$((i+1)); done\nexit 7\n",3000);
  assertEquals(7,r.exit);assertFalse(r.timeout);assertEquals(6000,r.output.length());
 }
 @Test public void boundedProcessRunnerTimesOutWithoutReexecuting()throws Exception{
  long start=System.nanoTime();CinemaAdbAccess.Result r=CinemaAdbProcess.execute(new String[]{"/bin/sh"},"sleep 2\n",80);assertTrue(r.timeout);assertTrue((System.nanoTime()-start)/1000000<1800);
 }
}
