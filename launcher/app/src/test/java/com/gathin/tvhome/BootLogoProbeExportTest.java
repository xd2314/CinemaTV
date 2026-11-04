package com.gathin.tvhome;
import java.io.*;
import java.nio.file.Files;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=31)
public class BootLogoProbeExportTest {
 File dir,file;String receipt,probe,sha;int uid=10234;
 @Before public void prepare()throws Exception{
  dir=Files.createTempDirectory("logo-probe-export-").toFile();file=new File(dir,"before.img");byte[] bytes=BootLogoLayoutTest.fixture();Files.write(file.toPath(),bytes);sha=BootLogoLayout.sha(bytes);
  JSONObject r=new JSONObject().put("schema",1).put("state","UNKNOWN").put("clientUid",uid).put("transaction","11111111-1111-1111-1111-111111111111").put("sourceAssetId","22222222-2222-2222-2222-222222222222").put("sourceSha","a".repeat(64)).put("diagnostic","IOException: Root read incomplete; do not resend");receipt=r.toString();
  JSONObject result=new JSONObject().put("device","/dev/block/mmcblk0p1").put("metadata","b3:1:0:0:600").put("sha",sha).put("bytes",bytes.length).put("layoutSupported",true)
   .put("diagnostic","CLBL_UID=0\nCLBL_CLIENT_UID="+uid+"\nCLBL_BYTES=33554432\nCLBL_SHA_BEFORE="+sha+"\nCLBL_SHA_AFTER="+sha+"\nCLBL_RESULT=READ_COMPLETE\n");
  probe=new JSONObject().put("state","FINISHED").put("readOnly",true).put("transaction",r.getString("transaction")).put("originalReceipt",r).put("result",result).toString();
 }
 @After public void clear(){BootAssetsTest.clear(dir);}
 static final class MemorySink implements BootLogoProbeExport.Sink{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();int opens,reads,publishes;boolean corruptBefore,corruptAfter;
  public OutputStream open(){opens++;return bytes;}
  public InputStream read(){reads++;byte[] b=bytes.toByteArray();if(corruptBefore||corruptAfter&&publishes>0)b[0]^=1;return new ByteArrayInputStream(b);}
  public JSONObject publish()throws Exception{publishes++;return new JSONObject().put("filename","backup.img");}
 }
 @Test public void verifiedFullBackupIsReadTwiceAndDoesNotChangeUnknownReceipt()throws Exception{
  MemorySink s=new MemorySink();JSONObject result=BootLogoProbeExport.copy(file,receipt,probe,uid,s);
  assertEquals(sha,result.getString("sha"));assertEquals(33554432,result.getLong("bytes"));assertEquals(1,s.publishes);assertEquals(2,s.reads);
  assertEquals("UNKNOWN",new JSONObject(receipt).getString("state"));assertEquals(sha,BootAssetStore.hash(file));
 }
 @Test public void alteredPrivateBackupStopsBeforeOpeningDestination()throws Exception{
  try(RandomAccessFile f=new RandomAccessFile(file,"rw")){f.seek(7000000);f.write(123);}
  MemorySink s=new MemorySink();try{BootLogoProbeExport.copy(file,receipt,probe,uid,s);fail();}catch(IOException expected){}
  assertEquals(0,s.opens);assertEquals(0,s.publishes);
 }
 @Test public void corruptReadbackIsNeverPublished()throws Exception{
  MemorySink s=new MemorySink();s.corruptBefore=true;try{BootLogoProbeExport.copy(file,receipt,probe,uid,s);fail();}catch(IOException expected){}
  assertEquals(0,s.publishes);assertTrue(file.isFile());
 }
 @Test public void publishedFileMustStillReadBackCorrectly()throws Exception{
  MemorySink s=new MemorySink();s.corruptAfter=true;try{BootLogoProbeExport.copy(file,receipt,probe,uid,s);fail();}catch(IOException expected){}
  assertEquals(1,s.publishes);assertEquals(2,s.reads);assertEquals(sha,BootAssetStore.hash(file));
 }
 @Test public void incompleteOrForeignProofCannotExport()throws Exception{
  assertFalse(BootLogoProbeExport.eligible(receipt,probe,uid+1));
  assertFalse(BootLogoProbeExport.eligible(new JSONObject(receipt).put("state","APPLY_PENDING").toString(),probe,uid));
  assertFalse(BootLogoProbeExport.eligible(receipt,new JSONObject(probe).put("state","PENDING").toString(),uid));
  JSONObject p=new JSONObject(probe);p.getJSONObject("result").put("layoutSupported",false);assertFalse(BootLogoProbeExport.eligible(receipt,p.toString(),uid));
  p=new JSONObject(probe);p.getJSONObject("result").put("diagnostic","missing Root proof");assertFalse(BootLogoProbeExport.eligible(receipt,p.toString(),uid));
 }
}
