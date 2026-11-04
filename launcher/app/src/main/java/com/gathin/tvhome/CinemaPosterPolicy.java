package com.gathin.tvhome;
import android.graphics.Bitmap;
/** Bounded thumbnail selection, independent from the mandatory full video validation. */
final class CinemaPosterPolicy {
 static boolean sample(int frame){return frame==1||frame<=109&&(frame-1)%12==0;}
 static int score(Bitmap image){int total=0,visible=0;for(int y=0;y<9;y++)for(int x=0;x<16;x++){
  int c=image.getPixel(Math.min(image.getWidth()-1,x*image.getWidth()/16),Math.min(image.getHeight()-1,y*image.getHeight()/9));
  int l=(((c>>16)&255)*54+((c>>8)&255)*183+(c&255)*19)>>8;total+=l;if(l>18)visible++;
 }return visible<8?0:total/144;}
 interface FrameReader{Bitmap read()throws java.io.IOException;}
 static Bitmap readCandidate(boolean required,FrameReader reader)throws java.io.IOException{
  try{Bitmap candidate=reader.read();if(candidate==null&&required)throw new java.io.IOException("軟體解碼器無法提供第一張縮圖像素");return candidate;}
  catch(java.io.IOException unavailable){if(required)throw unavailable;return null;}
 }
 private CinemaPosterPolicy(){}
}
