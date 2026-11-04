package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** A live-preview side panel. Every option is an actual focusable control, no dialog restarts. */
final class CinemaSettingsPanel extends FrameLayout implements AutoCloseable {
    private final Activity activity;
    private final CinemaSettings options;
    private final Runnable apply,dismiss;
    private final TextView eyebrow,title,caption,hint,back;
    final LinearLayout rows;
    final ScrollView scroll;
    private final FrameLayout sheet;
    private final List<TextView> controls=new ArrayList<>();
    private final List<Runnable> refreshers=new ArrayList<>();
    private float unit=1;
    private CinemaTimePanel timeEditor;
    private View timeAnchor;
    private boolean closed;
    private long focusRevision;
    static final class State {
        final String tag;final float scrollY;final boolean developersExpanded;
        State(String tag,float scrollY,boolean expanded){this.tag=tag;this.scrollY=scrollY;this.developersExpanded=expanded;}
    }
    State state(){View focus=findFocus();return new State(focus!=null&&focus.getTag() instanceof String?(String)focus.getTag():null,scroll.getScrollY()/Math.max(.01f,unit),developersExpanded);}
    void restore(State state,String preferred,boolean returning){
        if(state==null&&preferred==null)return;
        if(returning&&state!=null&&state.developersExpanded&&developerToggle!=null)expandDevelopers(true);
        String tag=preferred!=null?preferred:state.tag;
        View target=tag==null?null:findViewWithTag(tag);
        if(target!=null&&developerRows.contains(target)&&!developersExpanded){if(returning)expandDevelopers(true);else target=developerToggle;}
        if(target==null||!target.isFocusable())target=findViewWithTag("personalization");
        final View anchor=target;final long token=++focusRevision;
        post(()->{if(closed||token!=focusRevision||anchor==null)return;anchor.requestFocus();
            if(state!=null&&(preferred==null||preferred.equals(state.tag)))scroll.scrollTo(0,Math.round(state.scrollY*unit));
        });
    }
    private final Runnable revealFocused=()->{
        if(closed||timeEditor!=null)return;
        View focused=findFocus();
        if(focused!=null&&inside(focused)){android.graphics.Rect bounds=new android.graphics.Rect();focused.getDrawingRect(bounds);focused.requestRectangleOnScreen(bounds,true);}
    };
    private void scheduleFocus(View target){long revision=++focusRevision;post(()->{if(!closed&&revision==focusRevision&&target.isShown()&&target.isFocusable())target.requestFocus();});}
    void focusEntry(String tag){View row=findViewWithTag(tag);if(row==null)return;if(developerRows.contains(row))expandDevelopers(true);scheduleFocus(row);}
    private void expandDevelopers(boolean expanded){developersExpanded=expanded;developerToggle.setText((expanded?"▾":"▸")+"  開發人員工具\n開機外觀與無線連接");for(TextView row:developerRows){row.setVisibility(expanded?VISIBLE:GONE);row.setFocusable(expanded);}}
    @Override public void close(){closed=true;focusRevision++;removeCallbacks(revealFocused);}
    private TextView developerToggle;private boolean developersExpanded;private final List<TextView> developerRows=new ArrayList<>();
    CinemaSettingsPanel(Activity activity,CinemaSettings options,Runnable apply,Consumer<String> wallpaper,
                        Runnable localImage,Runnable system,Runnable advanced,Runnable dismiss){
        super(activity);this.activity=activity;this.options=options;this.apply=apply;this.dismiss=dismiss;
        setBackgroundColor(0x36000000);
        caption=label("讓首頁，成為你的風景。",34,Color.WHITE);caption.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));addView(caption);
        hint=label("即時預覽調整效果\n返回即可繼續使用首頁",22,0xFFD4DDEB);addView(hint);
        sheet=new FrameLayout(activity);sheet.setBackground(new CinemaSurface(false,0xFF7B9EC5,true,false));addView(sheet);
        eyebrow=label("CINEMA TV  /  PERSONALIZE",15,0xFFAAC0DB);title=label("外觀與操作",40,Color.WHITE);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));back=label("‹  返回首頁",22,Color.WHITE);back.setTag("settings:back");back.setGravity(Gravity.CENTER);control(back);CinemaUiSounds.bindClick(back,v->dismiss.run());
        sheet.addView(eyebrow);sheet.addView(title);sheet.addView(back);
        rows=new LinearLayout(activity);rows.setOrientation(LinearLayout.VERTICAL);scroll=new ScrollView(activity);scroll.setVerticalScrollBarEnabled(false);scroll.addView(rows);sheet.addView(scroll);
        heading("視覺模式");LinearLayout modes=new LinearLayout(activity);rows.addView(modes);
        for(String mode:new String[]{"Cinema","Lite","Static"}){TextView choice=label(mode,22,Color.WHITE);choice.setTag("mode:"+mode);choice.setGravity(Gravity.CENTER);control(choice);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,68,1);p.setMarginEnd(10);modes.addView(choice,p);CinemaUiSounds.bindClick(choice,v->{options.mode(mode);apply.run();refresh();});refreshers.add(()->{boolean selected=options.mode().equals(mode);choice.setSelected(selected);choice.setText((selected?"✓  ":"")+mode);choice.setBackground(new CinemaSurface(choice.isFocused(),0xFF90AED7,false,false));});}
        heading("效果與內容");
        String[] names={"背景視差","焦點動畫","App 環境光","柔和倒影","最近項目","低效能模式","垂直反射光紋"};
        String[] descriptions={"隨焦點輕微移動場景","短暫放大，清楚辨識選取位置","以 App 色彩襯托選中卡片","焦點穩定後才顯示","顯示最近從此首頁開啟的四個 App","關閉移動與光效，保留清楚焦點","關閉可簡化反射；保留主光池亮度"};
        for(int i=1;i<names.length;i++){if(i==3)continue;final String key=CinemaSettings.EFFECTS[i],name=names[i],description=descriptions[i];TextView row=action(name,description,()->{options.set(key,!options.on(key));apply.run();refresh();});row.setTag("effect:"+key);refreshers.add(()->row.setText(name+"                        "+(options.on(key)?"● 開":"○ 關")+"\n"+description));}
        heading("更多設定");action("系統設定","開啟 Android 設定",system).setTag("settings:system");action("進階 Launcher 設定","捷徑排序、隱藏與既有選項",advanced).setTag("settings:advanced");
        refresh();if(controls.size()>1)scheduleFocus(controls.get(1));
    }
    void personalization(Runnable open){TextView row=action("首頁個人化","桌布主題、最近項目背景及光暈強度",open);row.setTag("personalization");rows.removeView(row);rows.addView(row,0);scheduleFocus(row);}
    void startup(Runnable open){String pkg=CinemaStartup.target(activity);TextView row=action("開機自動開啟 App",pkg.isEmpty()?"目前關閉 · 開機停留首頁":CinemaStartup.label(activity,pkg)+" · "+(CinemaStartup.delay(activity)==0?"立即開啟":"等待 "+CinemaStartup.delay(activity)+" 秒"),open);row.setTag("startup:settings");rows.removeView(row);rows.addView(row,Math.min(1,rows.getChildCount()));}
    void shortcutOrder(Runnable open){TextView row=action("首頁捷徑排序","把常用 App 移到前面，自訂中央選單順序",open);row.setTag("order:settings");rows.removeView(row);rows.addView(row,Math.min(2,rows.getChildCount()));}
    private void developerEntry(String title,String description,String tag,Runnable open){
        if(developerToggle==null){developerToggle=action("▸  開發人員工具","開機外觀與無線連接",()->{
            expandDevelopers(!developersExpanded);
            developerToggle.requestFocus();
        });developerToggle.setTag("developer:toggle");}
        TextView row=action(title,description,()->{if(developersExpanded)open.run();});row.setTag(tag);row.setVisibility(GONE);row.setFocusable(false);developerRows.add(row);
    }
    void adbTools(Runnable open){developerEntry("啟動無線連接","供電腦使用 ADB 連線，確認後才啟動","adb:tools",open);}
    void bootAssets(Runnable open){developerEntry("開機外觀素材","開機圖片、動畫與過場圖片","boot:assets",open);}
    private void editTime(boolean day){
        if(timeEditor!=null)return;timeAnchor=findFocus();sheet.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        timeEditor=new CinemaTimePanel(activity,day?"日出切換時間":"日落切換時間",day?options.dayMinute():options.nightMinute(),value->{if(!options.schedule(day,value))return false;apply.run();refresh();return true;},this::closeTime);
        addView(timeEditor,new LayoutParams(-1,-1));
    }
    private void closeTime(){if(timeEditor==null)return;removeView(timeEditor);timeEditor=null;sheet.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);if(timeAnchor!=null)timeAnchor.requestFocus();}
    private TextView label(String text,float size,int color){TextView t=new TextView(activity);t.setText(text);t.setTextColor(color);t.setTextSize(0,size);t.setIncludeFontPadding(false);return t;}
    private void control(TextView t){t.setFocusable(true);t.setDefaultFocusHighlightEnabled(false);t.setClickable(true);t.setBackground(new CinemaSurface(false,0,false,false));controls.add(t);t.setOnFocusChangeListener((v,f)->{v.setBackground(new CinemaSurface(f,0xFF9AB9E6,false,false));refresh();if(f){removeCallbacks(revealFocused);postOnAnimation(revealFocused);}});}
    private void heading(String title){TextView t=label(title,19,0xFF9AB0CF);t.setTag("heading");t.setPadding(0,24,0,12);rows.addView(t,new LinearLayout.LayoutParams(-1,64));}
    private TextView action(String title,String subtitle,Runnable click){TextView t=label(title+"\n"+subtitle,21,0xFFE2EAF7);t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(18,10,18,10);t.setLineSpacing(6,1);control(t);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,86);p.bottomMargin=10;rows.addView(t,p);CinemaUiSounds.bindClick(t,v->click.run());return t;}
    private void refresh(){for(Runnable r:refreshers)r.run();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(timeEditor!=null)return timeEditor.dispatchKeyEvent(event);if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)dismiss.run();return true;}
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getKeyCode()>=19&&event.getKeyCode()<=22)){
            focusRevision++;View from=findFocus();if(from==null)return super.dispatchKeyEvent(event);View next=from.focusSearch(event.getKeyCode()==19?View.FOCUS_UP:event.getKeyCode()==20?View.FOCUS_DOWN:event.getKeyCode()==21?View.FOCUS_LEFT:View.FOCUS_RIGHT);
            if(next!=null&&inside(next))return next.requestFocus();return true;
        }return super.dispatchKeyEvent(event);}
    private boolean inside(View view){for(android.view.ViewParent p=view.getParent();p!=null;p=p.getParent())if(p==this)return true;return false;}
    private int layoutWidth=-1,layoutHeight=-1;
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
        if(w!=layoutWidth||h!=layoutHeight){layoutWidth=w;layoutHeight=h;layoutPanel(w,h);}
        super.onMeasure(widthSpec,heightSpec);
    }
    private void layoutPanel(int w,int h){unit=Math.min(w/1920f,h/1080f);put(caption,84,250,640,110);put(hint,88,370,600,120);caption.setTextSize(0,34*unit);hint.setTextSize(0,22*unit);put(sheet,800,40,1080,1000);
        put(eyebrow,38,32,800,28);put(title,36,82,720,64);put(back,850,76,190,64);put(scroll,24,166,1032,800);eyebrow.setTextSize(0,15*unit);title.setTextSize(0,40*unit);back.setTextSize(0,22*unit);
        for(int i=0;i<rows.getChildCount();i++){View row=rows.getChildAt(i);if(row instanceof TextView){TextView t=(TextView)row;boolean heading="heading".equals(t.getTag());t.setTextSize(0,(heading?19:21)*unit);t.setMinHeight((int)((heading?64:86)*unit));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,heading?(int)(64*unit):LinearLayout.LayoutParams.WRAP_CONTENT);p.bottomMargin=heading?0:(int)(10*unit);row.setLayoutParams(p);t.setPadding((int)((heading?0:18)*unit),(int)((heading?24:10)*unit),(int)(18*unit),(int)(10*unit));}else if(row instanceof LinearLayout){LinearLayout line=(LinearLayout)row;for(int j=0;j<line.getChildCount();j++){TextView t=(TextView)line.getChildAt(j);t.setTextSize(0,21*unit);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,(int)(64*unit),1);p.setMarginEnd((int)(10*unit));t.setLayoutParams(p);}}}}
    private void put(View v,float x,float y,float w,float h){LayoutParams p=new LayoutParams((int)(w*unit),(int)(h*unit));p.leftMargin=(int)(x*unit);p.topMargin=(int)(y*unit);v.setLayoutParams(p);}
}
