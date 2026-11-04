package com.gathin.tvhome;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Centered circular face and ring, independent from text font padding. */
final class CinemaActionSurface extends Drawable {
 private final boolean focused;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);private final RectF r=new RectF();
 CinemaActionSurface(boolean focused){this.focused=focused;}
 @Override public void draw(Canvas c){float u=Math.min(getBounds().width(),getBounds().height())/68f;r.set(getBounds());r.inset(3*u,3*u);p.setStyle(Paint.Style.FILL);p.setColor(focused?0x806B7C94:0x48394555);c.drawOval(r,p);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth((focused?2.4f:1)*u);p.setColor(focused?0xFFF1FAFF:0x487F8C9F);c.drawOval(r,p);if(focused){p.setStrokeWidth(5*u);p.setColor(0x398BDFFF);c.drawOval(r,p);}}
 @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
