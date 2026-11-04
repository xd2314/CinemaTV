package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;

/** Remote-operated local maintenance panel. Opening it never invokes su or any shell. */
final class CinemaAdbPanel extends FrameLayout {
    private final CinemaAdbAccess access;
    private final Runnable dismiss,update=this::refresh;
    private final LinearLayout sheet,normal,confirmation;
    private final TextView title,description,status,enable,back,cancel,confirm,warning;
    private final ScrollView results;
    private boolean confirming,disposed;
    private int layoutWidth=-1,layoutHeight=-1;
    CinemaAdbPanel(Activity activity,CinemaAdbAccess access,Runnable dismiss){
        super(activity);this.access=access;this.dismiss=dismiss;setBackgroundColor(0xDD08101C);
        sheet=new LinearLayout(activity);sheet.setOrientation(LinearLayout.VERTICAL);sheet.setBackground(new CinemaSurface(false,0xFF779CC5,true,false));addView(sheet);
        title=text(activity,"啟動無線連接",34);sheet.addView(title);
        description=text(activity,"讓同一網路的電腦透過 ADB 連接盒子。按下確認後才要求 Root。",21);sheet.addView(description);
        normal=new LinearLayout(activity);normal.setOrientation(LinearLayout.VERTICAL);sheet.addView(normal,new LinearLayout.LayoutParams(-1,0,1));
        enable=button(activity,"啟動無線連接","adb:enable",this::ask);normal.addView(enable);
        back=button(activity,"返回設定","adb:back",dismiss);normal.addView(back);
        results=new ScrollView(activity);results.setFillViewport(true);results.setFocusable(true);results.setId(View.generateViewId());results.setTag("adb:results");
        status=text(activity,"",22);results.addView(status);normal.addView(results,0,new LinearLayout.LayoutParams(-1,0,1));
        confirmation=new LinearLayout(activity);confirmation.setOrientation(LinearLayout.VERTICAL);sheet.addView(confirmation,new LinearLayout.LayoutParams(-1,0,1));
        warning=text(activity,"確認啟動無線連接？\n\n會開啟 ADB 5555 並重新啟動連接服務，現有電腦連線可能短暫中斷。這不會替你連接 Wi-Fi。\n\n此盒子已知沒有 RSA 主機驗證，網路來源隔離未確認；請僅在可信任網路、需要連線時操作。\n\n只供開發與維護使用，不會設定成開機自動啟動。若逾時或結果不明，會停止並保留紀錄。",24);confirmation.addView(warning,new LinearLayout.LayoutParams(-1,0,1));
        cancel=button(activity,"取消，不修改","adb:cancel",this::cancel);confirmation.addView(cancel);
        confirm=button(activity,"確認，啟動無線連接","adb:confirm",()->{if(!confirming||!access.canEnable())return;confirming=false;access.enable();refresh();back.requestFocus();});confirmation.addView(confirm);
        access.observe(update);refresh();post(enable::requestFocus);
    }
    private TextView text(Activity a,String value,int size){TextView v=new TextView(a);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(0,size);v.setIncludeFontPadding(false);return v;}
    private TextView button(Activity a,String label,String tag,Runnable click){TextView v=text(a,label,23);v.setGravity(Gravity.CENTER_VERTICAL);v.setId(View.generateViewId());v.setTag(tag);v.setFocusable(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);v.setBackground(new CinemaSurface(false,0,false,false));v.setOnFocusChangeListener((view,focused)->view.setBackground(new CinemaSurface(focused,0xFF9AB9E6,false,false)));CinemaUiSounds.bindClick(v,view->click.run());return v;}
    private void ask(){if(!access.canEnable())return;confirming=true;refresh();cancel.requestFocus();}
    private void cancel(){confirming=false;refresh();if(enable.isEnabled())enable.requestFocus();else back.requestFocus();}
    private void refresh(){
        if(disposed)return;
        normal.setVisibility(confirming?GONE:VISIBLE);confirmation.setVisibility(confirming?VISIBLE:GONE);
        enable.setEnabled(access.canEnable());
        for(TextView v:new TextView[]{enable}){v.setFocusable(v.isEnabled());v.setAlpha(v.isEnabled()?1f:.45f);}
        status.setText(access.message());
        if(!confirming){View current=findFocus();if(current==null||!current.isEnabled())back.requestFocus();}
    }
    void dispose(){disposed=true;access.removeObserver(update);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){
        int code=event.getKeyCode();
        if(code==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){if(confirming)cancel();else dismiss.run();}return true;}
        if((code==23||code==66)&&event.getAction()==KeyEvent.ACTION_DOWN&&event.getRepeatCount()>0)return true;
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(code>=19&&code<=22)){
            View focus=findFocus();if(focus==results){if(code==21||code==22)return true;if(code==19&&!results.canScrollVertically(-1))return true;if(code==20&&!results.canScrollVertically(1))return enable.isEnabled()?enable.requestFocus():back.requestFocus();return super.dispatchKeyEvent(event);}
            View[] order=confirming?new View[]{cancel,confirm}:new View[]{results,enable,back};
            if(code==21||code==22)return true;
            int at=-1;for(int i=0;i<order.length;i++)if(order[i]==focus)at=i;
            int delta=code==19?-1:1;
            for(int next=at+delta;next>=0&&next<order.length;next+=delta)if(order[next].isEnabled()&&order[next].isFocusable())return order[next].requestFocus();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    @Override protected void onMeasure(int ws,int hs){
        int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);
        if(w!=layoutWidth||h!=layoutHeight){layoutWidth=w;layoutHeight=h;layoutPanel(w,h);}
        super.onMeasure(ws,hs);
    }
    private void layoutPanel(int width,int height){
        float u=Math.min(width/1920f,height/1080f);
        sheet.setLayoutParams(new LayoutParams((int)(1240*u),(int)(760*u),Gravity.CENTER));sheet.setPadding((int)(40*u),(int)(26*u),(int)(40*u),(int)(26*u));
        title.setTextSize(0,34*u);title.setLayoutParams(new LinearLayout.LayoutParams(-1,(int)(65*u)));
        description.setTextSize(0,21*u);description.setLayoutParams(new LinearLayout.LayoutParams(-1,(int)(76*u)));
        warning.setTextSize(0,24*u);status.setTextSize(0,22*u);status.setPadding(0,(int)(16*u),0,(int)(16*u));
        for(TextView v:new TextView[]{enable,back,cancel,confirm}){v.setTextSize(0,23*u);v.setPadding((int)(20*u),0,(int)(20*u),0);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,(int)(70*u));lp.bottomMargin=(int)(12*u);v.setLayoutParams(lp);}
    }
}
