package com.gathin.tvhome;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Foreground centered by the icon's visible 32-unit viewport, independent of font metrics. */
final class CinemaButtonIcon extends Drawable {
 private final CinemaIcon icon;
 CinemaButtonIcon(int kind){icon=new CinemaIcon(kind);}
 @Override public void draw(Canvas c){Rect r=getBounds();int size=Math.round(Math.min(r.width(),r.height())*28f/68f);int x=r.centerX()-size/2,y=r.centerY()-size/2;icon.setBounds(x,y,x+size,y+size);icon.draw(c);}
 @Override public void setAlpha(int a){icon.setAlpha(a);invalidateSelf();}
 @Override public void setColorFilter(ColorFilter f){icon.setColorFilter(f);invalidateSelf();}
 @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
