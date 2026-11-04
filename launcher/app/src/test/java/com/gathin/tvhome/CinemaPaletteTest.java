package com.gathin.tvhome;
import android.graphics.*;
import android.graphics.drawable.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=28) @GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaPaletteTest {
 Bitmap bitmap(int color){Bitmap b=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);b.eraseColor(color);return b;}
 Drawable icon(Bitmap b){return new BitmapDrawable(android.content.res.Resources.getSystem(),b);}
 float hue(int c){float[] hsv=new float[3];Color.colorToHSV(c,hsv);return hsv[0];}
 @Test public void blackBackgroundDoesNotDominateRedBrand(){Bitmap b=bitmap(Color.BLACK);for(int y=8;y<24;y++)for(int x=12;x<20;x++)b.setPixel(x,y,Color.RED);int c=new CinemaPalette().color("test",icon(b));assertTrue(hue(c)<12||hue(c)>348);assertTrue(Color.red(c)>Color.blue(c)*2);}
 @Test public void whiteGrayAndTinySaturatedNoiseUseNeutral(){Bitmap b=bitmap(Color.LTGRAY);b.setPixel(3,4,Color.MAGENTA);assertEquals(CinemaPalette.NEUTRAL,new CinemaPalette().color("test",icon(b)));b.eraseColor(Color.WHITE);assertEquals(CinemaPalette.NEUTRAL,new CinemaPalette().color("test",icon(b)));}
 @Test public void transparentNoiseCannotOverrideBlue(){Bitmap b=bitmap(Color.TRANSPARENT);for(int y=10;y<25;y++)for(int x=10;x<25;x++)b.setPixel(x,y,Color.BLUE);b.setPixel(0,0,0x20FF0000);assertEquals(245,hue(new CinemaPalette().color("test",icon(b))),6);}
 @Test public void dominantClusterDoesNotAverageRedAndGreenToYellow(){Bitmap b=bitmap(Color.RED);for(int y=0;y<8;y++)for(int x=0;x<32;x++)b.setPixel(x,y,Color.GREEN);assertEquals(5,hue(new CinemaPalette().color("test",icon(b))),6);}
 @Test public void validIconTakesPriorityOverLegacyMapping(){Bitmap b=bitmap(Color.BLUE);assertEquals(245,hue(new CinemaPalette().color("com.ubdata.ublive",icon(b))),6);}
 @Test public void brightnessAndSaturationAreBounded(){for(int raw:new int[]{0xFFFF0000,0xFF001500,0xFFFFFF00}){float[] hsv=new float[3];Color.colorToHSV(CinemaPalette.normalize(raw),hsv);assertTrue(hsv[1]<=.81);assertTrue(hsv[2]>=.47&&hsv[2]<=.87);}}
 @Test public void iconIdentityCacheInvalidatesOnlyChangedIcon(){CinemaPalette p=new CinemaPalette();Drawable a=icon(bitmap(Color.BLUE));int c=p.color("one",a);for(int i=0;i<20;i++)assertEquals(c,p.color("one",a));assertEquals(1,p.samples);Drawable b=icon(bitmap(Color.RED));assertNotEquals(c,p.color("one",b));assertEquals(2,p.samples);}
 @Test public void samplingRestoresDrawableBounds(){Drawable d=icon(bitmap(Color.GREEN));d.setBounds(3,4,27,29);new CinemaPalette().color("test",d);assertEquals(new Rect(3,4,27,29),d.getBounds());}
}
