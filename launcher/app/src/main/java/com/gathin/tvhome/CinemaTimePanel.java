package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.function.IntPredicate;

/** Remote-first time editor. Draft values never persist until Save succeeds. */
final class CinemaTimePanel extends FrameLayout {
    private final LinearLayout box,fields,actions;
    private final TextView title,hint,hour,minute,cancel,save;
    private final Runnable dismiss;
    private int value,lastWidth=-1,lastHeight=-1;
    private final LayoutParams boxLayout=new LayoutParams(0,0,Gravity.CENTER);
    private final LinearLayout.LayoutParams titleLayout=new LinearLayout.LayoutParams(-1,0),hintLayout=new LinearLayout.LayoutParams(-1,0),fieldsLayout=new LinearLayout.LayoutParams(-1,0),actionsLayout=new LinearLayout.LayoutParams(-1,0);
    private final LinearLayout.LayoutParams[] buttonLayouts={new LinearLayout.LayoutParams(0,-1,1),new LinearLayout.LayoutParams(0,-1,1),new LinearLayout.LayoutParams(0,-1,1),new LinearLayout.LayoutParams(0,-1,1)};
    private final TextView[] buttons;
    CinemaTimePanel(Activity activity,String name,int initial,IntPredicate commit,Runnable dismiss){
        super(activity);this.dismiss=dismiss;value=initial;setBackgroundColor(0xDA080E18);
        box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(new CinemaSurface(false,0xFF779CC5,true,false));addView(box);
        title=text(activity,name,32);box.addView(title);
        hint=text(activity,"左右選擇時／分，上下調整；儲存後生效",20);box.addView(hint);
        fields=new LinearLayout(activity);box.addView(fields);
        hour=button(activity,"time:hour");minute=button(activity,"time:minute");fields.addView(hour);fields.addView(minute);
        actions=new LinearLayout(activity);box.addView(actions);cancel=button(activity,"time:cancel");cancel.setText("取消");save=button(activity,"time:save");save.setText("儲存");actions.addView(cancel);actions.addView(save);buttons=new TextView[]{hour,minute,cancel,save};
        CinemaUiSounds.bindClick(cancel,v->dismiss.run());CinemaUiSounds.bindClick(save,v->{if(commit.test(value))dismiss.run();else hint.setText("日出與日落時間不能相同，請調整時間");});
        CinemaUiSounds.bindClick(hour,v->step(true,1));CinemaUiSounds.bindClick(minute,v->step(false,1));
        refresh();post(hour::requestFocus);
    }
    private TextView text(Activity a,String s,int size){TextView t=new TextView(a);t.setText(s);t.setTextColor(Color.WHITE);t.setTextSize(0,size);t.setGravity(Gravity.CENTER);return t;}
    private TextView button(Activity a,String tag){TextView t=text(a,"",28);t.setTag(tag);t.setFocusable(true);t.setDefaultFocusHighlightEnabled(false);t.setClickable(true);t.setBackground(new CinemaSurface(false,0,false,false));t.setOnFocusChangeListener((v,f)->v.setBackground(new CinemaSurface(f,0xFF85BED9,false,false)));return t;}
    private void step(boolean hours,int delta){int h=value/60,m=value%60;if(hours)h=Math.floorMod(h+delta,24);else m=Math.floorMod(m+delta,60);value=h*60+m;refresh();}
    private void refresh(){hour.setText(String.format(java.util.Locale.ROOT,"%02d 時",value/60));minute.setText(String.format(java.util.Locale.ROOT,"%02d 分",value%60));}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int key=event.getKeyCode();if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)dismiss.run();return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN){View focus=findFocus();
            if((key==19||key==20)&&(focus==hour||focus==minute)){step(focus==hour,key==19?1:-1);return true;}
            if(key==21||key==22){View[] order={hour,minute,cancel,save};int at=0;for(int i=0;i<4;i++)if(order[i]==focus)at=i;int next=at+(key==21?-1:1);return next<0||next>3||order[next].requestFocus();}
            if((key==19||key==20)&&(focus==cancel||focus==save))return key==19?hour.requestFocus():true;
        }return super.dispatchKeyEvent(event);
    }
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);if(w==lastWidth&&h==lastHeight){super.onMeasure(ws,hs);return;}lastWidth=w;lastHeight=h;float u=Math.min(MeasureSpec.getSize(ws)/1920f,MeasureSpec.getSize(hs)/1080f);
        boxLayout.width=Math.round(740*u);boxLayout.height=Math.round(390*u);box.setLayoutParams(boxLayout);box.setPadding((int)(30*u),(int)(28*u),(int)(30*u),(int)(28*u));
        titleLayout.height=(int)(56*u);hintLayout.height=(int)(54*u);title.setLayoutParams(titleLayout);hint.setLayoutParams(hintLayout);title.setTextSize(0,32*u);hint.setTextSize(0,20*u);
        fieldsLayout.height=(int)(110*u);actionsLayout.height=(int)(88*u);fields.setLayoutParams(fieldsLayout);actions.setLayoutParams(actionsLayout);
        for(int i=0;i<buttons.length;i++){TextView t=buttons[i];LinearLayout.LayoutParams lp=buttonLayouts[i];lp.setMargins((int)(8*u),(int)(8*u),(int)(8*u),(int)(8*u));t.setLayoutParams(lp);t.setTextSize(0,(t==hour||t==minute?36:25)*u);}
        super.onMeasure(ws,hs);
    }
}
