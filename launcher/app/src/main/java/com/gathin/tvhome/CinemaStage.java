package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Static low-resolution colour field under the dock; no frame-by-frame backdrop blur. */
final class CinemaStage extends View {
    private Bitmap image;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
    private final Rect target=new Rect();
    CinemaStage(Context context){super(context);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);setFocusable(false);}
    void image(Bitmap bitmap){image=bitmap;invalidate();}
    @Override protected void onDraw(Canvas canvas){if(image==null)return;target.set(0,0,getWidth(),getHeight());canvas.drawBitmap(image,null,target,paint);}
}
