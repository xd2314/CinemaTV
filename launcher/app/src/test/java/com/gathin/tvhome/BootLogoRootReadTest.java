package com.gathin.tvhome;
import java.io.*;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootLogoRootReadTest {
 String header(String sha){return "CLBL_UID=0\nCLBL_CLIENT_UID=10106\nCLBL_DEVICE=/dev/block/mmcblk0p1\nCLBL_META=b3:1:0:0:660\nCLBL_BYTES=33554432\nCLBL_SHA_BEFORE="+sha+"\nCLBL_DATA_BEGIN\n";}
 @Test public void actualBoundedProcessReaderVerifiesAllFullBackupBytesWithoutRoot()throws Exception{
  File folder=Files.createTempDirectory("logo-fake-root-read-").toFile(),protocol=new File(folder,"protocol"),fake=new File(folder,"fake-reader"),backup=new File(folder,"backup.img");byte[] raw=BootLogoLayoutTest.fixture();String sha=BootLogoLayout.sha(raw);
  try(OutputStream out=new FileOutputStream(protocol)){out.write(header(sha).replace("10106","10234").replace("mmcblk0p1","mmcblk1p2").getBytes(StandardCharsets.US_ASCII));out.write(raw);out.write(("\nCLBL_DATA_END\nCLBL_SHA_AFTER="+sha+"\nCLBL_RESULT=READ_COMPLETE\n").getBytes(StandardCharsets.US_ASCII));}
  Files.write(fake.toPath(),("#!/bin/sh\ncat '"+protocol.getAbsolutePath()+"'\n").getBytes(StandardCharsets.UTF_8));assertTrue(fake.setExecutable(true));
  BootLogoTransactions.ReadResult result=BootLogoRoot.executeRead(fake.getAbsolutePath(),"# fake input only\n",backup,5000,10234);assertTrue(result.valid());assertEquals(10234,result.clientUid);assertEquals("/dev/block/mmcblk1p2",result.device);assertEquals(sha,result.sha);assertArrayEquals(raw,Files.readAllBytes(backup.toPath()));BootAssetsTest.clear(folder);
 }
 @Test public void shortBinaryAndDuplicateRootMarkersAreRejected()throws Exception{
  File folder=Files.createTempDirectory("logo-protocol-test-").toFile();
  for(String text:new String[]{header("a".repeat(64)),header("a".repeat(64)).replace("CLBL_UID=0\n","CLBL_UID=0\nCLBL_UID=0\n"),header("a".repeat(64)).replace("CLBL_BYTES=33554432","CLBL_BYTES=4294967295")}){
   File output=new File(folder,java.util.UUID.randomUUID()+".img");BootLogoRoot.ReadState state=new BootLogoRoot.ReadState();BootLogoRoot.parseRead(new ByteArrayInputStream(text.getBytes(StandardCharsets.US_ASCII)),output,state);assertNotNull(state.error);assertEquals(0,state.done.getCount());
  }BootAssetsTest.clear(folder);
 }
 @Test public void blockedInputAndSilentOutputCannotPreventBoundedTimeoutOrCauseRetry()throws Exception{
  File folder=Files.createTempDirectory("logo-fake-timeout-").toFile(),fake=new File(folder,"fake-reader"),calls=new File(folder,"calls"),backup=new File(folder,"backup.img");
  Files.write(fake.toPath(),("#!/bin/sh\nprintf 'called\\n' >> '"+calls.getAbsolutePath()+"'\nsleep 2\n").getBytes(StandardCharsets.UTF_8));assertTrue(fake.setExecutable(true));long start=System.nanoTime();
  try{BootLogoRoot.executeRead(fake.getAbsolutePath(),"x".repeat(200000),backup,500);fail();}catch(IOException expected){assertTrue(expected.getMessage(),expected.getMessage().contains("timeout"));}
  assertTrue((System.nanoTime()-start)/1000000<1800);assertEquals(java.util.List.of("called"),Files.readAllLines(calls.toPath()));BootAssetsTest.clear(folder);
 }
}
