package com.gathin.tvhome;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.*;
import android.view.Gravity;
import android.widget.TextView;

/** A static, non-focusable native empty/loading/error state; no timers, bitmaps or animations. */
final class CinemaStatusView extends TextView {
    enum Kind { LOADING, EMPTY, ERROR }
    private Kind kind=Kind.EMPTY;private String heading="",detail="";private float unit=1;
    CinemaStatusView(Context context){super(context);setGravity(Gravity.CENTER);setIncludeFontPadding(false);setFocusable(false);setClickable(false);setTextColor(CinemaPanelStyle.SECONDARY);}
    void show(Kind kind,String heading,String detail){this.kind=kind;this.heading=heading;this.detail=detail;style();}
    void scale(float unit){this.unit=unit;style();}
    private void style(){
        SpannableString text=new SpannableString(heading+"\n"+detail);
        text.setSpan(new AbsoluteSizeSpan(Math.max(1,Math.round(26*unit))),0,heading.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new StyleSpan(Typeface.BOLD),0,heading.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setSpan(new ForegroundColorSpan(CinemaPanelStyle.TEXT),0,heading.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        setTextSize(0,20*unit);setText(text);setLineSpacing(10*unit,1);setCompoundDrawablePadding(Math.round(16*unit));
        Drawable icon=new Symbol(kind);icon.setBounds(0,0,Math.round(44*unit),Math.round(44*unit));setCompoundDrawables(null,icon,null,null);
    }
    private static final class Symbol extends Drawable {
        private int opacity=255;final Kind kind;final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
        Symbol(Kind kind){this.kind=kind;}
        public void draw(Canvas canvas){int save=canvas.save();canvas.translate(getBounds().left,getBounds().top);canvas.scale(getBounds().width()/44f,getBounds().height()/44f);paint.setColor(0xFFAAC5EA);paint.setAlpha(opacity);paint.setStrokeWidth(2);paint.setStyle(Paint.Style.STROKE);
            if(kind==Kind.ERROR){canvas.drawCircle(22,22,17,paint);canvas.drawLine(22,12,22,25,paint);canvas.drawPoint(22,31,paint);}
            else if(kind==Kind.LOADING){canvas.drawCircle(22,22,17,paint);canvas.drawLine(22,11,22,22,paint);canvas.drawLine(22,22,30,26,paint);}
            else for(int y=8;y<=26;y+=18)for(int x=8;x<=26;x+=18)canvas.drawRoundRect(x,y,x+10,y+10,2,2,paint);
            canvas.restoreToCount(save);
        }
        public void setAlpha(int a){opacity=a;invalidateSelf();}public void setColorFilter(ColorFilter f){paint.setColorFilter(f);invalidateSelf();}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
