package com.gathin.tvhome;
import org.junit.*;
import org.junit.rules.TemporaryFolder;
import java.io.*;
import java.nio.file.Files;
import java.util.Random;
import static org.junit.Assert.*;
public class BootLogoFileCandidateTest {
 @Rule public TemporaryFolder temporary=new TemporaryFolder();
 @Test public void streamedCandidateMatchesEveryLegacyHashAndPixelWindow()throws Exception{
  byte[] original=BootLogoLayoutTest.fixture(),rgb=new byte[BootLogoLayout.RGB_BYTES];new Random(67).nextBytes(rgb);File source=temporary.newFile();Files.write(source.toPath(),original);
  BootLogoLayout.Candidate legacy=BootLogoLayout.build(original,rgb),bounded=BootLogoLayout.build(source,rgb);
  assertEquals(legacy.beforeSha,bounded.beforeSha);assertEquals(legacy.afterSha,bounded.afterSha);assertEquals(legacy.originalWindowSha,bounded.originalWindowSha);assertEquals(legacy.candidateWindowSha,bounded.candidateWindowSha);
  assertArrayEquals(legacy.originalWindow,bounded.originalWindow);assertArrayEquals(legacy.candidateWindow,bounded.candidateWindow);
  assertEquals(legacy.layout.windowStart,bounded.layout.windowStart);assertEquals(legacy.layout.windowEnd,bounded.layout.windowEnd);assertEquals(BootLogoLayout.sha(original),BootAssetStore.hash(source));
 }
 @Test public void shortBackupAndInvalidPixelLengthFailClosed()throws Exception{
  File file=temporary.newFile();Files.write(file.toPath(),new byte[512]);try{BootLogoLayout.parse(file);fail();}catch(IOException expected){}
  try{BootLogoLayout.build(file,new byte[1]);fail();}catch(IOException expected){}
 }
 @Test public void malformedFileRetainsSameFatAndHeaderGuards()throws Exception{
  for(int position:new int[]{510,BootLogoLayoutTest.FAT_BYTES+BootLogoLayoutTest.FAT+8,BootLogoLayoutTest.DATA,BootLogoLayoutTest.DATA+28}){
   byte[] fixture=BootLogoLayoutTest.fixture();fixture[position]^=1;File file=temporary.newFile();Files.write(file.toPath(),fixture);
   try{BootLogoLayout.parse(fixture);fail();}catch(IOException expected){}
   try{BootLogoLayout.parse(file);fail();}catch(IOException expected){}
  }
 }
}
