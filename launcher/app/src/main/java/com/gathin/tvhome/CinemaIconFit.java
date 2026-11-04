package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

/** Optical inset only: original logo pixels, colours, aspect ratio and full content remain intact. */
final class CinemaIconFit extends Drawable {
    private final Drawable source;
    private final Rect inner=new Rect(),saved=new Rect();
    final float scale;
    private CinemaIconFit(Drawable source){this.source=source;scale=measure(source);}
    static Drawable wrap(Drawable source){return source==null?null:new CinemaIconFit(source);}
    private static float measure(Drawable source){
        if(!(source instanceof BitmapDrawable))return .96f;
        Bitmap bitmap=((BitmapDrawable)source).getBitmap();
        if(bitmap==null||bitmap.isRecycled())return .96f;
        int visible=0,bright=0,edge=0,edgeBright=0;
        try{
            for(int y=0;y<16;y++)for(int x=0;x<16;x++){
                int c=bitmap.getPixel(Math.min(bitmap.getWidth()-1,x*bitmap.getWidth()/16),Math.min(bitmap.getHeight()-1,y*bitmap.getHeight()/16));
                boolean opaque=Color.alpha(c)>220;boolean light=opaque&&Color.red(c)>220&&Color.green(c)>220&&Color.blue(c)>220;
                if(opaque)visible++;if(light)bright++;
                if(x==0||y==0||x==15||y==15){edge++;if(light)edgeBright++;}
            }
        }catch(IllegalStateException unavailable){return .96f;}
        // A large white backing reads heavier than a transparent or coloured mark.
        if(edgeBright>edge*.60f&&bright>256*.42f)return .86f;
        return visible<256*.70f?1f:.94f;
    }
    @Override protected void onBoundsChange(Rect b){int dx=Math.round(b.width()*(1-scale)/2),dy=Math.round(b.height()*(1-scale)/2);inner.set(b.left+dx,b.top+dy,b.right-dx,b.bottom-dy);}
    @Override public void draw(Canvas c){saved.set(source.getBounds());try{source.setBounds(inner);source.draw(c);}finally{source.setBounds(saved);}}
    @Override public int getIntrinsicWidth(){return source.getIntrinsicWidth();}
    @Override public int getIntrinsicHeight(){return source.getIntrinsicHeight();}
    @Override public void setAlpha(int a){source.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){source.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
