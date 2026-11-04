package com.gathin.tvhome;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
/** Cached unit-circle shader, scaled into a soft ellipse; transparent at all four bounds. */
final class CinemaGlowDrawable extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    CinemaGlowDrawable(int color){this(color,117);}
    CinemaGlowDrawable(int color,int peak){int rgb=color&0xFFFFFF;paint.setShader(new RadialGradient(0,0,1,new int[]{rgb|(peak<<24),rgb|((peak/3)<<24),rgb},new float[]{0,.4f,1},Shader.TileMode.CLAMP));}
    @Override public void draw(Canvas canvas){
        if(getBounds().isEmpty())return;canvas.save();canvas.translate(getBounds().exactCenterX(),getBounds().exactCenterY());canvas.scale(getBounds().width()/2f,getBounds().height()/2f);canvas.drawCircle(0,0,1,paint);canvas.restore();
    }
    @Override public void setAlpha(int value){paint.setAlpha(value);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter value){paint.setColorFilter(value);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
