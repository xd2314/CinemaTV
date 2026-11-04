package com.gathin.tvhome;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Preserve the seasonal photograph above a consistent readable caption strip. */
final class CinemaRecentShade extends Drawable {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF area=new RectF();
    private Shader shade;
    private float radius,line;
    private int alpha=255;
    @Override protected void onBoundsChange(Rect b){area.set(b);float unit=b.height()/144f;radius=22*unit;line=Math.max(1,unit);
        shade=new LinearGradient(0,b.top,0,b.bottom,new int[]{0,0x18070D18,0xCC070D18,0xEB070D18},new float[]{0,.42f,.70f,1},Shader.TileMode.CLAMP);}
    @Override public void draw(Canvas c){if(shade==null)return;paint.setStyle(Paint.Style.FILL);paint.setColor(Color.WHITE);paint.setAlpha(alpha);paint.setShader(shade);c.drawRoundRect(area,radius,radius,paint);paint.setShader(null);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(line);paint.setColor((Math.round(56*alpha/255f)<<24)|0xD8E4F4);c.drawRoundRect(area.left+line,area.top+line,area.right-line,area.bottom-line,radius,radius,paint);paint.setStyle(Paint.Style.FILL);}
    @Override public void setAlpha(int a){alpha=a;invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
