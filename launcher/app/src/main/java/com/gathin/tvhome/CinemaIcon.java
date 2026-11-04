package com.gathin.tvhome;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
/** Small vector-like native icons; no raster labels or font-dependent glyphs. */
final class CinemaIcon extends Drawable {
    static final int SEARCH=0, SETTINGS=1, POWER=2, GRID=3, NETWORK=4, WIFI=5, ETHERNET=6, NETWORK_OFFLINE=7, NETWORK_LIMITED=8, WIFI_LIMITED=9, ETHERNET_LIMITED=10;
    private final int kind;private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);
    CinemaIcon(int kind){this.kind=kind;p.setColor(0xFFF3F5FA);p.setStrokeWidth(1.8f);p.setStyle(Paint.Style.STROKE);p.setStrokeCap(Paint.Cap.ROUND);}
    @Override public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/32f,getBounds().height()/32f);
        if(kind==GRID){p.setStyle(Paint.Style.FILL);for(int y=0;y<3;y++)for(int x=0;x<3;x++)c.drawRoundRect(4.5f+x*9,4.5f+y*9,9.5f+x*9,9.5f+y*9,1.5f,1.5f,p);p.setStyle(Paint.Style.STROKE);}
        else if(kind==WIFI||kind==WIFI_LIMITED){
            c.drawArc(new RectF(2,6,30,30),225,90,false,p);
            c.drawArc(new RectF(7,12,25,30),225,90,false,p);
            c.drawArc(new RectF(12,18,20,26),225,90,false,p);
            p.setStyle(Paint.Style.FILL);c.drawCircle(16,25,1.6f,p);p.setStyle(Paint.Style.STROKE);
            if(kind==WIFI_LIMITED){c.drawLine(28,19,28,24,p);c.drawPoint(28,28,p);}
        }
        else if(kind==ETHERNET||kind==ETHERNET_LIMITED){
            c.drawRoundRect(5,5,27,22,3,3,p);c.drawLine(16,22,16,27,p);c.drawLine(8,27,24,27,p);
            for(int x=11;x<=21;x+=5)c.drawLine(x,9,x,14,p);
            if(kind==ETHERNET_LIMITED){c.drawLine(29,18,29,23,p);c.drawPoint(29,27,p);}
        }
        else if(kind==NETWORK||kind==NETWORK_OFFLINE||kind==NETWORK_LIMITED){
            c.drawCircle(16,16,12,p);c.drawOval(new RectF(10,4,22,28),p);c.drawLine(4,16,28,16,p);
            if(kind==NETWORK_OFFLINE)c.drawLine(5,27,27,5,p);
            if(kind==NETWORK_LIMITED){c.drawLine(29,19,29,24,p);c.drawPoint(29,28,p);}
        }
        else if(kind==SEARCH){c.drawCircle(13,13,8,p);c.drawLine(19,19,27,27,p);}
        else if(kind==POWER){c.drawArc(new RectF(5,5,27,27),-48,276,false,p);c.drawLine(16,2,16,15,p);}
        else{android.graphics.Path gear=new android.graphics.Path();for(int i=0;i<32;i++){double angle=Math.PI*2*i/32;float radius=i%4==1||i%4==2?13:10.5f;float x=16+(float)Math.cos(angle)*radius,y=16+(float)Math.sin(angle)*radius;if(i==0)gear.moveTo(x,y);else gear.lineTo(x,y);}gear.close();c.drawPath(gear,p);c.drawCircle(16,16,4,p);}
        c.restore();}
    @Override public void setAlpha(int a){p.setAlpha(a);invalidateSelf();}
    @Override public void setColorFilter(ColorFilter f){p.setColorFilter(f);invalidateSelf();}
    @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
