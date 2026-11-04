package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;

/** Tiny cached icon impression. Built only after focus settles; never captures a View. */
final class CinemaReflection extends Drawable {
    private final Bitmap image;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    CinemaReflection(Drawable icon,int color){if(CinemaProbe.enabled)CinemaProbe.reflections++;
        final int w=16,h=12;
        Bitmap face=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(face);
        // A small icon impression only; no second rectangular glass card.
        if(icon!=null){Rect saved=new Rect(icon.getBounds());icon.setBounds(1,0,15,12);icon.draw(c);icon.setBounds(saved);}
        image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);c=new Canvas(image);
        c.save();c.translate(0,h);c.scale(1,-1);c.drawBitmap(face,0,0,paint);c.restore();
        paint.setShader(new LinearGradient(0,0,0,h,new int[]{0x68000000,0x10000000,0},new float[]{0,.22f,.62f},Shader.TileMode.CLAMP));
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));c.drawRect(0,0,w,h,paint);
        paint.setShader(null);paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));paint.setColor((color&0xFFFFFF)|0x12000000);c.drawRect(0,0,w,h,paint);paint.setColor(Color.WHITE);paint.setXfermode(null);face.recycle();
    }
    @Override public void draw(Canvas canvas){canvas.drawBitmap(image,null,getBounds(),paint);}
    @Override public void setAlpha(int a){paint.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
