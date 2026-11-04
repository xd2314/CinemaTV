package com.gathin.tvhome;
import java.io.IOException;
import java.nio.*;
import java.util.Arrays;
import org.junit.Test;
import static org.junit.Assert.*;

public class BootLogoLayoutTest {
 static final int FAT=512,FAT_BYTES=256*512,ROOT=FAT+2*FAT_BYTES,DATA=ROOT+512*32,CLUSTER=2048,SIZE=54+BootLogoLayout.RGB_BYTES+2,COUNT=(SIZE+CLUSTER-1)/CLUSTER;
 static void fats(ByteBuffer b,int cluster,int value){b.putShort(FAT+2*cluster,(short)value);b.putShort(FAT+FAT_BYTES+2*cluster,(short)value);}
 static byte[] fixture(){
  byte[] x=new byte[BootLogoLayout.PHYSICAL_BYTES];ByteBuffer b=ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN);b.putShort(11,(short)512);x[13]=4;b.putShort(14,(short)1);x[16]=2;b.putShort(17,(short)512);b.putShort(22,(short)256);b.putInt(32,262144);x[510]=0x55;x[511]=(byte)0xaa;
  byte[] name={'B','O','O','T','L','O','G','O','B','M','P'};System.arraycopy(name,0,x,ROOT,11);x[ROOT+11]=0x20;b.putShort(ROOT+26,(short)2);b.putInt(ROOT+28,SIZE);
  for(int i=0;i<COUNT;i++)fats(b,2+i,i==COUNT-1?0xffff:3+i);
  x[DATA]='B';x[DATA+1]='M';b.putInt(DATA+2,SIZE);b.putInt(DATA+10,54);b.putInt(DATA+14,40);b.putInt(DATA+18,1280);b.putInt(DATA+22,720);b.putShort(DATA+26,(short)1);b.putShort(DATA+28,(short)24);b.putInt(DATA+34,BootLogoLayout.RGB_BYTES);
  Arrays.fill(x,DATA+54,DATA+54+BootLogoLayout.RGB_BYTES,(byte)0x22);x[DATA+SIZE-2]=0x12;x[DATA+SIZE-1]=0x34;return x;
 }
 static void reject(byte[] x)throws Exception{try{BootLogoLayout.parse(x);fail("Malformed image accepted");}catch(IOException expected){}}
 @Test public void geometryAndWriteWindowAreDerivedFromDirectoryAndFat()throws Exception{byte[] x=fixture();BootLogoLayout a=BootLogoLayout.parse(x);assertEquals(DATA,a.fileStart);assertEquals(128L*1024*1024,a.declaredVolumeBytes);assertEquals(0,a.windowStart%512);assertEquals(0,a.windowEnd%512);
  ByteBuffer b=ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN);System.arraycopy(x,DATA,x,DATA+2*CLUSTER,SIZE);for(int c=2;c<COUNT+4;c++)fats(b,c,0);for(int i=0;i<COUNT;i++)fats(b,4+i,i==COUNT-1?0xffff:5+i);b.putShort(ROOT+26,(short)4);BootLogoLayout moved=BootLogoLayout.parse(x);assertEquals(DATA+2*CLUSTER,moved.fileStart);assertNotEquals(a.windowStart,moved.windowStart);
 }
 @Test public void bmpPixelsBecomeBottomUpBgrWhileEveryOtherByteAndExactRestoreRemainIntact()throws Exception{
  byte[] original=fixture(),rgb=new byte[BootLogoLayout.RGB_BYTES];rgb[0]=(byte)255;rgb[(1280-1)*3+1]=(byte)255;rgb[((720-1)*1280)*3+2]=(byte)255;
  BootLogoLayout.Candidate c=BootLogoLayout.build(original,rgb);BootLogoLayout l=c.layout;byte[] applied=original.clone();System.arraycopy(c.candidateWindow,0,applied,l.windowStart,c.candidateWindow.length);
  assertEquals(c.afterSha,BootLogoLayout.sha(applied));assertArrayEquals(new byte[]{(byte)255,0,0},Arrays.copyOfRange(applied,l.pixelStart,l.pixelStart+3));
  int tl=l.pixelStart+(720-1)*1280*3;assertArrayEquals(new byte[]{0,0,(byte)255},Arrays.copyOfRange(applied,tl,tl+3));
  assertArrayEquals(Arrays.copyOfRange(original,0,l.pixelStart),Arrays.copyOfRange(applied,0,l.pixelStart));assertArrayEquals(Arrays.copyOfRange(original,l.pixelEnd,original.length),Arrays.copyOfRange(applied,l.pixelEnd,applied.length));
  System.arraycopy(c.originalWindow,0,applied,l.windowStart,c.originalWindow.length);assertArrayEquals(original,applied);assertEquals(c.beforeSha,BootLogoLayout.sha(applied));
 }
 @Test public void badSignatureGeometryAndPhysicalSizeAreRejected()throws Exception{reject(new byte[1024]);byte[] x=fixture();x[510]=0;reject(x);x=fixture();x[13]=3;reject(x);x=fixture();ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN).putInt(32,65536);reject(x);}
 @Test public void fatMismatchCycleAndOutsidePhysicalChainAreRejected()throws Exception{byte[] x=fixture();x[FAT+4]^=1;reject(x);x=fixture();ByteBuffer b=ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN);fats(b,2,2);reject(x);x=fixture();b=ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN);b.putShort(ROOT+26,(short)20000);fats(b,20000,0xffff);reject(x);}
 @Test public void duplicateDirectoryAndNonContiguousChainAreRejected()throws Exception{byte[] x=fixture();System.arraycopy(x,ROOT,x,ROOT+32,32);reject(x);x=fixture();fats(ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN),2,4);reject(x);}
 @Test public void malformedBmpAndWrongNormalizedPixelsAreRejected()throws Exception{for(int field:new int[]{10,18,22,30}){byte[] x=fixture();ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN).putInt(DATA+field,123);reject(x);}try{BootLogoLayout.build(fixture(),new byte[3]);fail();}catch(IOException expected){}}
 @Test public void knownVendorSizeIncludesTwoTrailerBytesButDoesNotExpandPixelWindow()throws Exception{byte[] x=fixture();ByteBuffer b=ByteBuffer.wrap(x).order(ByteOrder.LITTLE_ENDIAN);b.putInt(DATA+34,BootLogoLayout.RGB_BYTES+2);BootLogoLayout l=BootLogoLayout.parse(x);assertEquals(DATA+54+BootLogoLayout.RGB_BYTES,l.pixelEnd);BootLogoLayout.Candidate c=BootLogoLayout.build(x,new byte[BootLogoLayout.RGB_BYTES]);int trailer=l.pixelEnd-l.windowStart;assertEquals(0x12,c.candidateWindow[trailer]);assertEquals(0x34,c.candidateWindow[trailer+1]);b.putInt(DATA+34,BootLogoLayout.RGB_BYTES+4);reject(x);}
}
