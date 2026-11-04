package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Shared native, remote-first editor shell. Focus outline and saved selection are independent. */
abstract class CinemaSetupPanel extends FrameLayout {
    final Activity activity;
    final Runnable dismiss;
    final FrameLayout sheet,details,footer;
    final LinearLayout list,actions;
    final ScrollView scroll;
    final TextView title,hint,listTitle,listHint,status,cancel,save,discard;
    final Map<TextView,Integer> fonts=new LinkedHashMap<>();
    final List<View> buttons=new ArrayList<>();
    float unit=1;
    boolean confirmingExit,closed;
    View lastListFocus;
    CinemaSetupPanel(Activity activity,Runnable dismiss,String heading,String subtitle){
        super(activity);this.activity=activity;this.dismiss=dismiss;
        setBackgroundColor(0xB5060B13);
        sheet=new FrameLayout(activity);addView(sheet);
        title=text(heading,42,Color.WHITE);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));sheet.addView(title);
        hint=text(subtitle,23,0xFFB8C8DC);sheet.addView(hint);
        listTitle=text("",24,Color.WHITE);sheet.addView(listTitle);
        listHint=text("",19,0xFF99ACC4);sheet.addView(listHint);
        list=new LinearLayout(activity);list.setOrientation(LinearLayout.VERTICAL);
        scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(true);scroll.setClipToPadding(false);scroll.addView(list);sheet.addView(scroll);
        details=new FrameLayout(activity);sheet.addView(details);
        footer=new FrameLayout(activity);sheet.addView(footer);
        status=text("",21,0xFFAEBFD4);status.setMaxLines(2);footer.addView(status);
        actions=new LinearLayout(activity);actions.setGravity(Gravity.CENTER_VERTICAL);footer.addView(actions);
        cancel=button("返回","editor:cancel");discard=button("放棄變更","editor:discard");save=button("儲存設定","editor:save");
        actions.addView(cancel);actions.addView(discard);actions.addView(save);discard.setVisibility(GONE);
        CinemaUiSounds.bindClick(cancel,v->back());CinemaUiSounds.bindClick(discard,v->dismiss.run());CinemaUiSounds.bindClick(save,v->saveDraft());
    }
    abstract boolean dirty();
    abstract void saveDraft();
    abstract void refresh();
    abstract void layoutDetails();
    abstract View detailFocus();
    abstract View navigateDetail(View from,int key);
    void back(){
        if(confirmingExit){confirmingExit=false;refreshExit();refresh();listFocus().requestFocus();}
        else if(dirty()){confirmingExit=true;refreshExit();cancel.requestFocus();}
        else dismiss.run();
    }
    void refreshExit(){
        discard.setVisibility(confirmingExit?VISIBLE:GONE);cancel.setText(confirmingExit?"繼續編輯":"返回");
        if(confirmingExit)status.setText("尚未儲存\n要保留變更，請選擇儲存設定。");
        requestLayout();
    }
    void changed(){confirmingExit=false;refreshExit();refresh();}
    TextView text(String value,int size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(0,size);t.setIncludeFontPadding(false);t.setGravity(Gravity.CENTER_VERTICAL);fonts.put(t,size);return t;}
    TextView button(String label,String tag){TextView b=text(label,23,Color.WHITE);b.setGravity(Gravity.CENTER);b.setSingleLine();b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setTag(tag);control(b);return b;}
    void control(View view){view.setFocusable(true);view.setClickable(true);view.setDefaultFocusHighlightEnabled(false);buttons.add(view);style(view);view.setOnFocusChangeListener((v,f)->{
        style(v);if(f&&v.getParent()==list){lastListFocus=v;v.post(()->{if(!closed&&v.hasFocus()){Rect r=new Rect();v.getDrawingRect(r);v.requestRectangleOnScreen(r,true);}});}
    });}
    GradientDrawable surface(int top,int bottom,int border,float radius){GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{top,bottom});d.setCornerRadius(radius*unit);d.setStroke(Math.max(1,Math.round(unit*(border==0xFFF0F6FF?2.5f:1))),border);return d;}
    void style(View v){boolean f=v.hasFocus(),s=v.isSelected();v.setBackground(surface(f?0xFF344D6B:s?0xFF1D3948:0x401A283B,f?0xFF20374E:s?0xFF122632:0x30111E30,f?0xFFF0F6FF:s?0xFF75CFC9:0x305A7393,14));}
    Drawable copy(Drawable d){if(d==null)return activity.getDrawable(R.drawable.ic_livetv);return d.getConstantState()==null?d:d.getConstantState().newDrawable(activity.getResources()).mutate();}
    final class AppRow extends LinearLayout {
        final String key,label;
        final ImageView icon;
        final TextView name,mark;
        AppRow(String key,String label,Drawable art){super(activity);this.key=key;this.label=label;setTag("startup:app:"+key);setGravity(Gravity.CENTER_VERTICAL);icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImageDrawable(copy(art));addView(icon);name=text(label,25,Color.WHITE);name.setMaxLines(1);name.setEllipsize(android.text.TextUtils.TruncateAt.END);addView(name);mark=text("",21,0xFF9EE6DB);mark.setGravity(Gravity.CENTER);addView(mark);setContentDescription(label);control(this);}
        void size(){setPadding(px(16),0,px(16),0);icon.setLayoutParams(new LinearLayout.LayoutParams(px(52),px(52)));LinearLayout.LayoutParams n=new LinearLayout.LayoutParams(0,-1,1);n.setMargins(px(18),0,px(8),0);name.setLayoutParams(n);mark.setLayoutParams(new LinearLayout.LayoutParams(px(54),-1));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,px(84));p.bottomMargin=px(10);setLayoutParams(p);}
        void chosen(boolean selected){setSelected(selected);mark.setText(selected?"✓":"");setContentDescription(label+(selected?"，已選擇":""));style(this);}
    }
    View listFocus(){return lastListFocus!=null&&lastListFocus.getParent()==list?lastListFocus:list.getChildAt(0);}
    boolean forward(int key){return key==(getLayoutDirection()==LAYOUT_DIRECTION_RTL?21:22);}
    View horizontal(LinearLayout group,View from,int key){int step=(key==22?1:-1)*(getLayoutDirection()==LAYOUT_DIRECTION_RTL?-1:1);int i=group.indexOfChild(from)+step;while(i>=0&&i<group.getChildCount()){View v=group.getChildAt(i);if(v.getVisibility()==VISIBLE&&v.isEnabled()&&v.isFocusable())return v;i+=step;}return from;}
    @Override public boolean dispatchKeyEvent(KeyEvent e){int key=e.getKeyCode();if(key==4){if(e.getAction()==KeyEvent.ACTION_UP)back();return true;}
        if(e.getAction()==KeyEvent.ACTION_DOWN&&key>=19&&key<=22){View f=findFocus(),next=f;if(f==null)next=listFocus();
            else if(f.getParent()==list){int i=list.indexOfChild(f);if(key==19&&i>0)next=list.getChildAt(i-1);if(key==20)next=i+1<list.getChildCount()?list.getChildAt(i+1):save;if((key==21||key==22)&&forward(key))next=detailFocus();}
            else if(f.getParent()==actions){if(key==19)next=f==save?detailFocus():listFocus();else if(key==21||key==22)next=horizontal(actions,f,key);if(next==save&&f==save&&key==19)next=listFocus();}
            else next=navigateDetail(f,key);
            if(next!=null&&next!=f)next.requestFocus();return true;
        }return super.dispatchKeyEvent(e);
    }
    int px(float n){return Math.round(n*unit);}
    void place(FrameLayout parent,View child,float x,float y,float w,float h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(px(w),px(h));p.setMarginStart(px(x));p.topMargin=px(y);p.gravity=Gravity.TOP|Gravity.START;child.setLayoutParams(p);}
    @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);float u=Math.min(w/1920f,h/1080f);boolean resized=u!=unit||sheet.getLayoutParams().width!=Math.round(1680*u);unit=u;
        if(resized){sheet.setLayoutParams(new LayoutParams(px(1680),px(920),Gravity.CENTER));sheet.setBackground(surface(0xFF18293E,0xFF091421,0x666782A3,30));details.setBackground(surface(0x661C3048,0x40101C2E,0x264F6C8E,22));for(View v:buttons)style(v);}
        place(sheet,title,44,28,1550,62);place(sheet,hint,44,94,1550,44);place(sheet,listTitle,44,161,650,40);place(sheet,listHint,44,201,650,34);place(sheet,scroll,40,249,664,512);place(sheet,details,740,161,896,600);place(sheet,footer,44,789,1592,100);
        place(footer,status,0,0,590,100);place(footer,actions,650,4,942,88);
        for(int i=0;i<actions.getChildCount();i++){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,px(70),i==2?1.35f:1);p.setMarginStart(px(12));actions.getChildAt(i).setLayoutParams(p);}
        for(int i=0;i<list.getChildCount();i++)((AppRow)list.getChildAt(i)).size();
        layoutDetails();for(Map.Entry<TextView,Integer> f:fonts.entrySet())f.getKey().setTextSize(0,f.getValue()*unit);super.onMeasure(ws,hs);
    }
}
