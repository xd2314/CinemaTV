package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** A cached, local caption band; uses the already-decoded wallpaper, never another bitmap. */
final class CinemaReadabilityShade extends Drawable {
    private final Paint paint=new Paint();
    private final RectF area=new RectF();
    private int strength=54;
    private Shader band;
    void wallpaper(Drawable source){
        int next=54;
        if(source instanceof BitmapDrawable){
            Bitmap b=((BitmapDrawable)source).getBitmap();
            try{
                if(b!=null&&!b.isRecycled()){
                    float brightness=0;
                    for(int y=0;y<4;y++)for(int x=0;x<12;x++){
                        int c=b.getPixel(Math.min(b.getWidth()-1,Math.round(b.getWidth()*(.06f+x*.08f))),Math.min(b.getHeight()-1,Math.round(b.getHeight()*(.65f+y*.025f))));
                        brightness+=(.2126f*Color.red(c)+.7152f*Color.green(c)+.0722f*Color.blue(c))/255f;
                    }
                    next=Math.round(12+118*Math.max(0,Math.min(1,brightness/48)));
                }
            }catch(IllegalStateException unavailable){/* Hardware/unavailable bitmaps retain a moderate band. */}
        }
        if(next==strength)return;strength=next;rebuild();invalidateSelf();
    }
    int strength(){return strength;}
    @Override protected void onBoundsChange(Rect bounds){area.set(bounds);rebuild();}
    private void rebuild(){
        if(area.height()<=0)return;
        band=new LinearGradient(0,area.top,0,area.bottom,new int[]{0,0,(strength<<24)|0x040912,0,0},new float[]{0,.635f,.704f,.765f,1},Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas c){if(band==null)return;paint.setShader(band);c.drawRect(area,paint);}
    @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
