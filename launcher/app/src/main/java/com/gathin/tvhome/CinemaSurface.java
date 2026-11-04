package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Cached native gradients give the glass a face, bevel and lower lip; no backdrop read or blur. */
final class CinemaSurface extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF face = new RectF(), lower = new RectF();
    private final boolean selected, artwork, round;
    private final int accent;
    private Shader body, sheen, pool, edge;
    private float radius, lip, line;
    CinemaSurface(boolean selected, int accent, boolean artwork, boolean round) {
        this.selected=selected;this.accent=accent==0?0xFF91B2D7:accent;this.artwork=artwork;this.round=round;
    }
    private static int mix(int a,int b,float f){return Color.rgb((int)(Color.red(a)*(1-f)+Color.red(b)*f),(int)(Color.green(a)*(1-f)+Color.green(b)*f),(int)(Color.blue(a)*(1-f)+Color.blue(b)*f));}
    @Override protected void onBoundsChange(android.graphics.Rect bounds){
        float w=bounds.width(),h=bounds.height(),u=Math.max(.4f,Math.min(1.2f,Math.min(w/(round?68:270),h/(round?68:144))));
        radius=round?Math.min(w,h)/2:Math.min(25*u,h*.18f);lip=round?2*u:6*u;line=Math.max(1,1.15f*u);
        face.set(bounds.left+line,bounds.top+line,bounds.right-line,bounds.bottom-lip-line);
        int top=round?0x456F6375:artwork?mix(0xFF17263D,accent,.3f):selected?mix(0xFF0B0D14,accent,.14f):mix(0xFF24232D,accent,.12f);
        int bottom=round?0x3540334D:artwork?mix(0xFF07101D,accent,.06f):selected?mix(0xFF080B10,accent,.11f):0xF00A0D13;
        body=new LinearGradient(0,face.top,w,face.bottom,new int[]{top,bottom},null,Shader.TileMode.CLAMP);
        sheen=new LinearGradient(0,face.top,w*.65f,face.bottom,new int[]{selected?0x55FFFFFF:0x46FFFFFF,0x08FFFFFF,0x00FFFFFF},new float[]{0,.18f,1},Shader.TileMode.CLAMP);
        pool=new RadialGradient(face.centerX(),face.bottom,Math.max(1,w*.8f),new int[]{(accent&0xFFFFFF)|(selected?0x75000000:0x38000000),0},null,Shader.TileMode.CLAMP);
        edge=new LinearGradient(face.left,face.top,face.right,face.bottom,new int[]{0xFFF8E9DF,0x90E3D8D8,(accent&0xFFFFFF)|0xD0000000},new float[]{0,.45f,1},Shader.TileMode.CLAMP);
    }
    @Override public void draw(Canvas canvas){
        if(body==null)return;
        paint.setShader(null);paint.setStyle(Paint.Style.FILL);paint.setColor(round?0x00000000:0xA502050B);
        lower.set(face);lower.offset(0,lip);canvas.drawRoundRect(lower,radius,radius,paint);
        paint.setColor(0xFFFFFFFF);paint.setShader(body);canvas.drawRoundRect(face,radius,radius,paint);
        if(!round){paint.setShader(pool);canvas.drawRoundRect(face,radius,radius,paint);}
        paint.setShader(sheen);canvas.drawRoundRect(face,radius,radius,paint);paint.setShader(null);
        paint.setShader(round?null:edge);paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(selected?line*1.8f:line);paint.setColor(selected?mix(0xFFFFFFFF,accent,.70f):round?0x30DCE6F6:0x70DCE6F6);canvas.drawRoundRect(face,radius,radius,paint);
        paint.setShader(null);paint.setStrokeWidth(line);paint.setColor(selected?0xB0FFFFFF:round?0x24FFFFFF:0x4AFFFFFF);canvas.drawLine(face.left+radius,face.top+line,face.right-radius,face.top+line,paint);paint.setStyle(Paint.Style.FILL);
    }
    @Override public void getOutline(android.graphics.Outline outline){outline.setRoundRect(getBounds(),radius);}
    @Override public void setAlpha(int alpha) { }
    @Override public void setColorFilter(ColorFilter filter) { }
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
