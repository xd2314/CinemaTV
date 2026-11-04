package com.gathin.tvhome;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Foreground border remains visible above the thumbnail, unlike a background stroke. */
final class CinemaQuickFocus extends Drawable {
 private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();
 @Override public void draw(Canvas c){float u=getBounds().width()/300f;r.set(getBounds());r.inset(3*u,3*u);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(6*u);p.setColor(0x608BDFFF);c.drawRoundRect(r,20*u,20*u,p);p.setStrokeWidth(2.4f*u);p.setColor(0xFFF0FAFF);c.drawRoundRect(r,20*u,20*u,p);}
 @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
