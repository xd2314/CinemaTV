package com.gathin.tvhome;
import org.junit.Test;
import static org.junit.Assert.*;
public class CinemaPolicyTest {
 @Test public void referenceFocusScaleAndParallaxStayInsideOverscan(){assertTrue(CinemaPolicy.FOCUS_SCALE>=1.1&&CinemaPolicy.FOCUS_SCALE<=1.42f);assertTrue((CinemaPolicy.OVERSCAN-1)/2>CinemaPolicy.PARALLAX);assertEquals(28.8f,CinemaPolicy.offset(1,1920),.01f);assertEquals(-19.2f,CinemaPolicy.offset(-1,1280),.01f);}
 @Test public void normalizedPositionClamped(){assertEquals(CinemaPolicy.offset(1,1920),CinemaPolicy.offset(10,1920),0);assertEquals(CinemaPolicy.offset(-1,1920),CinemaPolicy.offset(-5,1920),0);}
 @Test public void wallpaperSamplesLargePortraitAndPanorama(){for(int[] size:new int[][]{{8000,8000},{64000,1000},{1000,64000},{1920,1080},{1280,720}}){int n=CinemaPolicy.sampleSize(size[0],size[1],1920,1080);assertEquals(0,n&(n-1));assertTrue((((long)size[0]+n-1)/n)*(((long)size[1]+n-1)/n)<=CinemaPolicy.MAX_DECODE_PIXELS);assertTrue(size[0]/n<=4096);assertTrue(size[1]/n<=4096);}}
 @Test public void visibleLeftNeighborIsNotSwallowed(){float[] x={100,200,300};boolean[] e={true,true,true};assertEquals(0,CinemaPolicy.horizontal(21,1,x,e));assertEquals(1,CinemaPolicy.horizontal(22,0,x,e));}
 @Test public void trueEdgesStayAndSkipHiddenDisabled(){float[] x={100,200,300,400};boolean[] e={true,false,false,true};assertEquals(0,CinemaPolicy.horizontal(21,0,x,e));assertEquals(3,CinemaPolicy.horizontal(22,3,x,e));assertEquals(3,CinemaPolicy.horizontal(22,0,x,e));}
 @Test public void RTLUsesPhysicalCoordinatesNotIndex(){float[] x={400,300,200,100};boolean[] e={true,true,true,true};assertEquals(1,CinemaPolicy.horizontal(21,0,x,e));assertEquals(0,CinemaPolicy.horizontal(22,1,x,e));}
 @Test public void verticalPolicySelectsNearestEligible(){assertEquals(2,CinemaPolicy.nearest(190,new float[]{10,100,200},new boolean[]{true,false,true}));assertEquals(-1,CinemaPolicy.nearest(190,new float[]{10},new boolean[]{false}));}
 @Test public void rapidOkCannotLaunchTwice(){assertTrue(CinemaPolicy.mayLaunch(-1,0,false));assertFalse(CinemaPolicy.mayLaunch(100,120,false));assertFalse(CinemaPolicy.mayLaunch(100,1000,true));assertTrue(CinemaPolicy.mayLaunch(100,601,false));}
 @Test public void unsupportedKeysAreNotConsumedByHorizontalPolicy(){assertEquals(-1,CinemaPolicy.horizontal(19,0,new float[]{10},new boolean[]{true}));assertEquals(-1,CinemaPolicy.horizontal(20,0,new float[]{10},new boolean[]{true}));}
}
