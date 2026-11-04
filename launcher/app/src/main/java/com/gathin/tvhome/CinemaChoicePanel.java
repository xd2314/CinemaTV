package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Small native action sheet shared by app actions and accent selection. */
final class CinemaChoicePanel extends FrameLayout {
    private final LinearLayout menu;
    private final TextView heading,subtitle;
    private final Runnable dismiss;
    CinemaChoicePanel(Activity activity,String title,String description,String[] choices,IntConsumer choose,Runnable dismiss){
        super(activity);this.dismiss=dismiss;setBackgroundColor(0xB5090E19);
        menu=new LinearLayout(activity);menu.setOrientation(LinearLayout.VERTICAL);menu.setBackground(new CinemaSurface(false,0xFF7698C9,true,false));addView(menu);
        heading=text(activity,title,34,Color.WHITE);subtitle=text(activity,description,20,0xFFB2C5DD);menu.addView(heading);menu.addView(subtitle);
        for(int i=0;i<choices.length;i++){final int index=i;TextView t=text(activity,choices[i],24,0xFFF4F7FC);t.setFocusable(true);t.setDefaultFocusHighlightEnabled(false);t.setClickable(true);t.setBackground(new CinemaSurface(false,0,false,false));menu.addView(t);
            t.setOnFocusChangeListener((v,f)->v.setBackground(new CinemaSurface(f,0xFFACC5E8,false,false)));CinemaUiSounds.bindClick(t,v->choose.accept(index));
            t.setOnKeyListener((v,key,event)->{if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==21||key==22)return true;if(key==19||key==20){int n=menu.indexOfChild(v)+(key==19?-1:1);return n<2||n>=menu.getChildCount()||menu.getChildAt(n).requestFocus();}return false;});
        }
        post(()->{if(menu.getChildCount()>2)menu.getChildAt(2).requestFocus();});
    }
    private TextView text(Activity a,String value,float size,int color){TextView t=new TextView(a);t.setText(value);t.setTextColor(color);t.setTextSize(0,size);t.setGravity(Gravity.CENTER_VERTICAL);t.setIncludeFontPadding(false);return t;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)dismiss.run();return true;}return super.dispatchKeyEvent(event);}
    private int lastWidth,lastHeight;
    @Override protected void onMeasure(int widthSpec,int heightSpec){int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);if(w!=lastWidth||h!=lastHeight){lastWidth=w;lastHeight=h;float u=Math.min(w/1920f,h/1080f);int mh=Math.round((174+(menu.getChildCount()-2)*82)*u);LayoutParams mp=new LayoutParams(Math.round(650*u),mh,Gravity.CENTER);menu.setLayoutParams(mp);menu.setPadding((int)(32*u),(int)(28*u),(int)(32*u),(int)(22*u));for(int i=0;i<menu.getChildCount();i++){TextView t=(TextView)menu.getChildAt(i);t.setTextSize(0,(i==0?34:i==1?20:24)*u);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,Math.round((i==0?52:i==1?48:72)*u));p.bottomMargin=(int)(10*u);t.setLayoutParams(p);t.setPadding((int)(18*u),0,(int)(18*u),0);}}super.onMeasure(widthSpec,heightSpec);}
}
