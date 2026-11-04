package com.gathin.tvhome;

import java.io.IOException;
import java.nio.ByteBuffer;
import org.junit.Test;
import static org.junit.Assert.*;

public class BootVideoDecoderTest {
    @Test public void paddedInterleavedPlaneHonorsOffsetStrideAndReadLimit()throws Exception {
        byte[] data=new byte[20];data[9]=42;data[11]=(byte)231;
        ByteBuffer plane=ByteBuffer.wrap(data).asReadOnlyBuffer();plane.position(2);plane.limit(14);
        assertEquals(42,BootVideoDecoder.sample(plane,5,2,1,1));
        assertEquals(231,BootVideoDecoder.sample(plane,5,2,2,1));
        assertEquals(2,plane.position());assertEquals(14,plane.limit());
    }
    @Test public void truncatedAndOverflowingPlaneCoordinatesAreRejected()throws Exception {
        ByteBuffer plane=ByteBuffer.allocate(10);plane.position(2);plane.limit(9);
        for(int[] c:new int[][]{{5,2,1,1},{Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE,Integer.MAX_VALUE},{0,1,0,0},{1,0,0,0},{1,1,-1,0},{1,1,0,-1}}) {
            try{BootVideoDecoder.sample(plane,c[0],c[1],c[2],c[3]);fail("Invalid plane access accepted");}
            catch(IOException expected){}
        }
    }
    @Test public void limitedRangeBlackWhiteAndPrimaryColorsStayOpaque() {
        assertEquals(0xff000000,BootVideoDecoder.argb(16,128,128,false,false));
        assertEquals(0xffffffff,BootVideoDecoder.argb(235,128,128,false,false));
        assertEquals(0xffff0000,BootVideoDecoder.argb(81,90,240,false,false));
        assertEquals(0xff0000ff,BootVideoDecoder.argb(41,240,110,false,false));
    }
    @Test public void fullRangeBlackWhiteDoNotUseStudioRangeLevels() {
        assertEquals(0xff000000,BootVideoDecoder.argb(0,128,128,false,true));
        assertEquals(0xffffffff,BootVideoDecoder.argb(255,128,128,false,true));
        assertEquals(0xff101010,BootVideoDecoder.argb(16,128,128,false,true));
    }
    @Test public void bt709ThumbnailUsesItsOwnColorMatrixAndClampsExtremes() {
        int red=BootVideoDecoder.argb(63,102,240,true,false);
        assertEquals(255,(red>>>16)&255);assertTrue(((red>>>8)&255)<=2);assertTrue((red&255)<=2);
        assertEquals(255,BootVideoDecoder.argb(255,255,255,true,true)>>>24);
        assertNotEquals(BootVideoDecoder.argb(63,102,240,false,false),red);
    }
}
