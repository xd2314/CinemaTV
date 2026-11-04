package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** One coherent TV app library for All Apps and Search, with real package data and bounded focus. */
final class CinemaAppPanel extends FrameLayout implements AutoCloseable {
    final EditText query;
    final GridLayout grid;
    final ScrollView scroll;
    final TextView count,empty;
    final LinearLayout tabs;
    final CinemaCatalogue catalogue;
    private final Activity activity;
    private final Predicate<String> onHome;
    private final Consumer<AppAdapter.Entry> launch;
    private final Runnable dismiss,changed;
    private final TextView title,kicker,footer,back;
    private final List<AppAdapter.Entry> all=new ArrayList<>();
    private final CinemaPalette palette=new CinemaPalette();
    private final java.util.Map<AppAdapter.Entry,String> searchTerms=new java.util.IdentityHashMap<>();
    private final List<AppAdapter.Entry> rendered=new ArrayList<>();
    private final List<Boolean> renderedShortcuts=new ArrayList<>();
    private String filter="全部";private float unit=1;
    private boolean closed,loading=true;private String loadError;
    private FrameLayout actions;
    private View actionAnchor;
    private final Runnable revealFocused=this::revealFocused;
    private void revealFocused(){
        if(closed||actions!=null)return;
        View focused=findFocus();
        if(focused!=null&&focused.getParent()==grid){Rect bounds=new Rect();focused.getDrawingRect(bounds);focused.requestRectangleOnScreen(bounds,true);}
    }
    CinemaAppPanel(Activity activity,Config config,List<AppAdapter.Entry> initial,boolean search,
                   Predicate<String> onHome,Consumer<AppAdapter.Entry> launch,Runnable changed,Runnable dismiss){
        super(activity);this.activity=activity;this.onHome=onHome;this.launch=launch;this.changed=changed;this.dismiss=dismiss;
        setBackground(CinemaPanelStyle.backdrop());
        setFocusable(true);setFocusableInTouchMode(true);
        kicker=text("CINEMA TV  /  APP LIBRARY",16,0xFF95AAC6);title=text(search?"搜尋應用程式":"所有應用程式",48,Color.WHITE);title.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));
        count=text("",20,0xFFB2C2DA);back=text("‹  返回",22,Color.WHITE);back.setGravity(Gravity.CENTER);interactive(back);CinemaUiSounds.bindClick(back,v->dismiss.run());
        query=new EditText(activity);query.setSingleLine(true);query.setTextColor(Color.WHITE);query.setHintTextColor(0xFF95A5BE);query.setHint("搜尋名稱，快速找到應用程式");query.setBackground(new CinemaSurface(false,0,false,false));query.setPadding(24,0,24,0);query.setTextSize(22);query.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        query.setOnEditorActionListener((v,action,event)->{((InputMethodManager)activity.getSystemService(Activity.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(query.getWindowToken(),0);focusFirst();return true;});
        tabs=new LinearLayout(activity);tabs.setGravity(Gravity.CENTER_VERTICAL);
        for(String name:new String[]{"全部","首頁捷徑","最近使用"}){TextView t=text(name,21,0xFFCFD9E9);t.setGravity(Gravity.CENTER);interactive(t);t.setTag(name);tabs.addView(t);CinemaUiSounds.bindClick(t,v->{filter=name;fill();updateTabs();t.requestFocus();});t.setOnKeyListener((v,k,e)->headerKey(v,k,e));}
        grid=new GridLayout(activity);grid.setColumnCount(6);grid.setClipChildren(false);grid.setClipToPadding(false);grid.setPadding(12,12,12,28);
        scroll=new ScrollView(activity);scroll.setFillViewport(false);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        empty=new CinemaStatusView(activity);
        footer=text("方向鍵 選擇    ·    OK 開啟    ·    長按 管理捷徑    ·    返回 關閉",19,0xFF899AB4);
        for(View view:new View[]{kicker,title,count,back,query,tabs,scroll,empty,footer})addView(view);
        query.setOnKeyListener((v,key,event)->{if(event.getAction()==KeyEvent.ACTION_DOWN&&key==KeyEvent.KEYCODE_DPAD_DOWN){if(grid.getChildCount()>0)focusFirst();else tabs.getChildAt(0).requestFocus();return true;}return false;});
        query.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int n,int after){}public void onTextChanged(CharSequence s,int start,int before,int n){fill();}public void afterTextChanged(Editable e){}});
        bindApps(initial);catalogue=new CinemaCatalogue(activity);
        catalogue.load(new HashSet<>(config.libraryExcludedApps),this::setApps,this::loadFailed);
        post(()->{if(search)query.requestFocus();else if(grid.getChildCount()>0)focusFirst();else query.requestFocus();});
    }
    private TextView text(String value,float size,int color){TextView t=new TextView(activity);t.setText(value);t.setTextColor(color);t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX,size);t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    private void interactive(View v){v.setFocusable(true);v.setDefaultFocusHighlightEnabled(false);v.setClickable(true);v.setBackground(new CinemaSurface(false,0,false,false));v.setOnFocusChangeListener((view,focus)->view.setBackground(new CinemaSurface(focus,0xFF9BB9ED,false,false)));}
    static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFKD).replaceAll("\\p{M}","").toLowerCase(Locale.ROOT).trim();}
    void setApps(List<AppAdapter.Entry> entries){loading=false;loadError=null;bindApps(entries);}
    void loadFailed(String error){if(closed)return;loading=false;loadError=error;updateStatus(grid.getChildCount());if(!all.isEmpty())android.widget.Toast.makeText(activity,error,android.widget.Toast.LENGTH_SHORT).show();}
    private void bindApps(List<AppAdapter.Entry> entries){if(closed)return;all.clear();all.addAll(entries);searchTerms.clear();for(AppAdapter.Entry entry:all)searchTerms.put(entry,normalized(entry.label+" "+entry.pkg));fill();}
    private void updateTabs(){for(int i=0;i<tabs.getChildCount();i++){TextView t=(TextView)tabs.getChildAt(i);boolean selected=t.getTag().equals(filter);t.setSelected(selected);t.setText((selected?"✓  ":"")+t.getTag().toString());t.setTextColor(selected?Color.WHITE:0xFFB4C1D2);t.setBackground(new CinemaSurface(t.isFocused(),0xFF87A8D7,false,false));}}
    void refreshShortcuts(){fill();}
    private void fill(){
        if(closed)return;String previous=null;View focus=findFocus();if(focus!=null&&focus.getTag() instanceof AppAdapter.Entry)previous=((AppAdapter.Entry)focus.getTag()).pkg;
        String q=normalized(query.getText().toString());int matches=0;
        List<AppAdapter.Entry> ordered=new ArrayList<>(all);if(filter.equals("最近使用"))ordered.sort((a,b)->Long.compare(LastOpened.get(activity,b.pkg),LastOpened.get(activity,a.pkg)));
        List<AppAdapter.Entry> matching=new ArrayList<>();List<Boolean> shortcuts=new ArrayList<>();
        for(AppAdapter.Entry entry:ordered){
            if(!searchTerms.get(entry).contains(q))continue;boolean shortcut=onHome.test(entry.pkg);
            if(filter.equals("首頁捷徑")&&!shortcut)continue;
            if(filter.equals("最近使用")&&LastOpened.get(activity,entry.pkg)<=0)continue;
            matching.add(entry);shortcuts.add(shortcut);
        }
        // A query with identical visible results must not recreate every drawable, tile and focus.
        if(matching.equals(rendered)&&shortcuts.equals(renderedShortcuts)&&grid.getChildCount()==matching.size()){
            updateStatus(matching.size());updateTabs();return;
        }
        java.util.Map<AppAdapter.Entry,FrameLayout> reusable=new java.util.IdentityHashMap<>();
        for(int i=0;i<grid.getChildCount();i++){FrameLayout tile=(FrameLayout)grid.getChildAt(i);reusable.put((AppAdapter.Entry)tile.getTag(),tile);}
        rendered.clear();rendered.addAll(matching);renderedShortcuts.clear();renderedShortcuts.addAll(shortcuts);grid.removeAllViews();
        for(AppAdapter.Entry entry:matching){
            FrameLayout reused=reusable.get(entry);if(reused!=null){((TextView)reused.getChildAt(2)).setText(onHome.test(entry.pkg)?"首頁捷徑":"應用程式");grid.addView(reused);matches++;continue;}
            FrameLayout tile=new FrameLayout(activity);tile.setTag(entry);tile.setFocusable(true);tile.setDefaultFocusHighlightEnabled(false);tile.setClickable(true);int accent=palette.color(entry.pkg,entry.icon);tile.setBackground(new CinemaSurface(false,accent,true,false));
            ImageView icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);icon.setImageDrawable(CinemaIconFit.wrap(entry.icon!=null&&entry.icon.getConstantState()!=null?entry.icon.getConstantState().newDrawable(activity.getResources()).mutate():entry.icon));tile.addView(icon);
            TextView label=text(entry.label,23,0xFFF1F4FA);label.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));label.setGravity(Gravity.CENTER);label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);tile.addView(label);
            TextView badge=text(onHome.test(entry.pkg)?"首頁捷徑":"應用程式",13,0xFFB1C2D8);badge.setGravity(Gravity.CENTER);tile.addView(badge);
            tile.setContentDescription(entry.label);CinemaUiSounds.bindClick(tile,v->launch.accept(entry));tile.setOnLongClickListener(v->{actions(entry,tile);return true;});
            tile.setOnFocusChangeListener((v,focused)->{v.setBackground(new CinemaSurface(focused,accent,true,false));v.setElevation(focused?8*unit:0);if(focused){removeCallbacks(revealFocused);postOnAnimation(revealFocused);}});
            tile.setOnKeyListener((v,k,e)->gridKey(v,k,e));grid.addView(tile);matches++;
        }
        updateStatus(matches);sizeGrid();updateTabs();
        if(previous!=null){for(int i=0;i<grid.getChildCount();i++)if(((AppAdapter.Entry)grid.getChildAt(i).getTag()).pkg.equals(previous)){grid.getChildAt(i).requestFocus();return;}if(grid.getChildCount()>0)focusFirst();else query.requestFocus();}
    }
    private void updateStatus(int matches){
        count.setText((loading&&all.isEmpty())?"正在整理清單":loadError!=null?matches+" 個應用程式 · 讀取未完成":matches+" 個應用程式");
        empty.setVisibility(matches==0?VISIBLE:GONE);if(matches!=0)return;
        CinemaStatusView status=(CinemaStatusView)empty;
        if(loading&&all.isEmpty())status.show(CinemaStatusView.Kind.LOADING,"正在整理應用程式","稍候即可選擇；也可以按返回離開。");
        else if(loadError!=null&&all.isEmpty())status.show(CinemaStatusView.Kind.ERROR,"暫時無法讀取清單","請返回後再開啟；原有 App 與首頁捷徑會保留。");
        else if(all.isEmpty())status.show(CinemaStatusView.Kind.EMPTY,"沒有可開啟的應用程式","返回首頁後，可再次開啟清單確認。");
        else if(!query.getText().toString().trim().isEmpty())status.show(CinemaStatusView.Kind.EMPTY,"找不到相符的應用程式","試試其他名稱，或切換到「全部」。");
        else if(filter.equals("最近使用"))status.show(CinemaStatusView.Kind.EMPTY,"尚無最近使用","從首頁或此清單開啟 App 後，會顯示在這裡。");
        else status.show(CinemaStatusView.Kind.EMPTY,"尚無首頁捷徑","切換到「全部」，長按 App 即可加入首頁。");
    }
    private boolean headerKey(View from,int key,KeyEvent event){if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key==19){query.requestFocus();return true;}if(key==20){focusFirst();return true;}if(key==21||key==22)return horizontal(from,tabs,key);return false;}
    private boolean horizontal(View from,ViewGroup group,int key){float[] x=new float[group.getChildCount()];boolean[] e=new boolean[x.length];int current=-1;for(int i=0;i<x.length;i++){View v=group.getChildAt(i);x[i]=center(v,true);e[i]=eligible(v);if(v==from)current=i;}int n=CinemaPolicy.horizontal(key,current,x,e);return n>=0&&(n==current||group.getChildAt(n).requestFocus());}
    private boolean eligible(View v){return v.isShown()&&v.isEnabled()&&v.isFocusable();}
    private float center(View v,boolean horizontal){int[] p=new int[2];v.getLocationOnScreen(p);return horizontal?p[0]+v.getWidth()/2f:p[1]+v.getHeight()/2f;}
    private boolean gridKey(View from,int key,KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;if(key<19||key>22)return false;
        float x=center(from,true),y=center(from,false),score=Float.MAX_VALUE;View best=null;
        for(int i=0;i<grid.getChildCount();i++){View v=grid.getChildAt(i);if(v==from||!eligible(v))continue;float dx=center(v,true)-x,dy=center(v,false)-y;boolean vertical=key==19||key==20;
            if(vertical?(key==19?dy>=-1:dy<=1):(key==21?dx>=-1:dx<=1))continue;
            if(!vertical&&Math.abs(dy)>from.getHeight()*.6f)continue;
            float candidate=vertical?Math.abs(dy)+Math.abs(dx)*2:Math.abs(dx)+Math.abs(dy)*2;
            if(candidate<score){score=candidate;best=v;}
        }
        if(best!=null)return best.requestFocus();if(key==19){for(int i=0;i<tabs.getChildCount();i++)if(tabs.getChildAt(i).getTag().equals(filter))return tabs.getChildAt(i).requestFocus();}return true;
    }
    void focusFirst(){if(grid.getChildCount()>0)grid.getChildAt(0).requestFocus();}
    private void actions(AppAdapter.Entry entry,View anchor){
        if(actions!=null)return;actionAnchor=anchor;String[] choices={onHome.test(entry.pkg)?"從首頁移除捷徑":"加入首頁捷徑","應用程式資訊","取消"};
        actions=new CinemaChoicePanel(activity,entry.label,"管理這個應用程式的首頁捷徑",choices,n->{
            closeActions(anchor);
            if(n==0){if(LibraryApps.save(activity,entry.pkg,!onHome.test(entry.pkg))){changed.run();fill();}else android.widget.Toast.makeText(activity,"無法儲存捷徑，原設定已保留",android.widget.Toast.LENGTH_SHORT).show();}
            else if(n==1)try{activity.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+entry.pkg)));}catch(android.content.ActivityNotFoundException|SecurityException ignored){android.widget.Toast.makeText(activity,"無法開啟應用程式資訊",android.widget.Toast.LENGTH_SHORT).show();}
        },()->closeActions(anchor));addView(actions,new LayoutParams(-1,-1));
    }
    private void closeActions(View anchor){if(anchor==null)anchor=actionAnchor;actionAnchor=null;if(actions!=null){removeView(actions);actions=null;}if(anchor!=null&&anchor.isAttachedToWindow())anchor.requestFocus();else focusFirst();}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP){if(actions!=null)closeActions(null);else dismiss.run();}return true;}return super.dispatchKeyEvent(event);}
    @Override public void onRtlPropertiesChanged(int direction){
        super.onRtlPropertiesChanged(direction);
        if(grid!=null)grid.setLayoutDirection(direction);
        if(tabs!=null)tabs.setLayoutDirection(direction);
    }
    private int layoutWidth=-1,layoutHeight=-1;
    @Override protected void onMeasure(int widthSpec,int heightSpec){
        int w=MeasureSpec.getSize(widthSpec),h=MeasureSpec.getSize(heightSpec);
        if(w!=layoutWidth||h!=layoutHeight){layoutWidth=w;layoutHeight=h;layoutPanel(w,h);}
        super.onMeasure(widthSpec,heightSpec);
    }
    private void layoutPanel(int w,int h){unit=Math.min(w/1920f,h/1080f);put(kicker,80,44,900,30);put(title,76,90,1300,72);put(count,1430,117,300,40);put(back,1734,68,110,64);put(query,76,190,1768,76);put(tabs,76,292,1700,60);put(scroll,64,380,1792,620);put(empty,76,500,1768,240);put(footer,80,1022,1760,34);
        kicker.setTextSize(0,16*unit);title.setTextSize(0,40*unit);count.setTextSize(0,20*unit);back.setTextSize(0,22*unit);query.setTextSize(0,24*unit);((CinemaStatusView)empty).scale(unit);footer.setTextSize(0,19*unit);grid.setPadding((int)(12*unit),(int)(12*unit),(int)(12*unit),(int)(28*unit));query.setPadding((int)(28*unit),0,(int)(28*unit),0);
        for(int i=0;i<tabs.getChildCount();i++){TextView t=(TextView)tabs.getChildAt(i);t.setTextSize(0,21*unit);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams((int)(180*unit),-1);p.setMarginEnd((int)(18*unit));t.setLayoutParams(p);}sizeGrid();}
    private void sizeGrid(){for(int i=0;i<grid.getChildCount();i++){FrameLayout tile=(FrameLayout)grid.getChildAt(i);GridLayout.LayoutParams p=new GridLayout.LayoutParams();p.width=Math.round(274*unit);p.height=Math.round(184*unit);p.setMargins(Math.round(10*unit),Math.round(10*unit),Math.round(10*unit),Math.round(18*unit));tile.setLayoutParams(p);ImageView icon=(ImageView)tile.getChildAt(0);FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(Math.round(82*unit),Math.round(82*unit),Gravity.TOP|Gravity.CENTER_HORIZONTAL);ip.topMargin=Math.round(18*unit);icon.setLayoutParams(ip);TextView label=(TextView)tile.getChildAt(1);FrameLayout.LayoutParams lp=new FrameLayout.LayoutParams(-1,Math.round(37*unit),Gravity.BOTTOM);lp.bottomMargin=Math.round(29*unit);lp.setMarginStart((int)(12*unit));lp.setMarginEnd((int)(12*unit));label.setLayoutParams(lp);label.setTextSize(0,23*unit);TextView badge=(TextView)tile.getChildAt(2);FrameLayout.LayoutParams bp=new FrameLayout.LayoutParams(-1,Math.round(24*unit),Gravity.BOTTOM);bp.bottomMargin=Math.round(9*unit);badge.setLayoutParams(bp);badge.setTextSize(0,14*unit);}}
    private void put(View v,float x,float y,float w,float h){LayoutParams p=new LayoutParams(Math.round(w*unit),Math.round(h*unit));p.leftMargin=Math.round(x*unit);p.topMargin=Math.round(y*unit);v.setLayoutParams(p);}
    void stop(){closed=true;removeCallbacks(revealFocused);catalogue.close();}
    @Override public void close(){stop();palette.clear();grid.removeAllViews();all.clear();searchTerms.clear();rendered.clear();renderedShortcuts.clear();}
}
