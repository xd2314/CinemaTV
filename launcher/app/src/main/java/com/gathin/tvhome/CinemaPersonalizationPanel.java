package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Color;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.Consumer;

/** Remote-first editor over cinema_lite settings; drafts/images commit only on explicit confirmation. */
final class CinemaPersonalizationPanel extends FrameLayout {
 private final Activity activity;private final CinemaSettings settings;private final Runnable changed,dismiss;private final Consumer<String> pick;
 private final LinearLayout box,rows;private final TextView heading,headerBack;private final ScrollView scroll;
 private String previewAnchor;
 private Runnable back,cancelPreview;private CinemaWallpaperManager previewLoader;private float unit=1;
 private boolean disposed;
 private final List<CinemaWallpaperManager> thumbnails=new ArrayList<>();
 private final Map<View,Integer> heights=new HashMap<>();private final Map<TextView,Integer> fontSizes=new HashMap<>();
 private CinemaTimePanel timeEditor;private View timeAnchor;
 // Only actual controls participate; ScrollView, headings and preview images never own focus.
 private final List<View> navigationControls=new ArrayList<>();
 private final android.graphics.Rect navigationRect=new android.graphics.Rect();
 private int revision;int revision(){return revision;}
 CinemaPersonalizationPanel(Activity a,CinemaSettings s,Runnable changed,Consumer<String> pick,Runnable dismiss){
  super(a);activity=a;settings=s;this.changed=changed;this.pick=pick;this.dismiss=dismiss;setBackground(CinemaPanelStyle.backdrop());
  box=new LinearLayout(a);box.setOrientation(LinearLayout.VERTICAL);box.setBackground(new CinemaSurface(false,0xFF8CA9C5,true,false));addView(box,new LayoutParams(1100,940,Gravity.CENTER));
  LinearLayout header=new LinearLayout(a);header.setGravity(Gravity.CENTER_VERTICAL);
  heading=text("首頁個人化",40);CinemaPanelStyle.title(heading);header.addView(heading,new LinearLayout.LayoutParams(0,-1,1));
  headerBack=text("‹  返回設定",22);headerBack.setTag("personal:header-back");headerBack.setGravity(Gravity.CENTER);headerBack.setFocusable(true);headerBack.setClickable(true);headerBack.setDefaultFocusHighlightEnabled(false);headerBack.setBackground(new CinemaSurface(false,0,false,false));headerBack.setOnFocusChangeListener((v,f)->v.setBackground(new CinemaSurface(f,0xFF9ABBD8,false,false)));CinemaUiSounds.bindClick(headerBack,v->{if(back!=null)back.run();});
  header.addView(headerBack,new LinearLayout.LayoutParams(220,-1));box.addView(header,new LinearLayout.LayoutParams(-1,84));scroll=new ScrollView(a);scroll.setFocusable(false);rows=new LinearLayout(a);rows.setFocusable(false);rows.setOrientation(LinearLayout.VERTICAL);scroll.addView(rows);box.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));showRoot();
 }
 private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextColor(Color.WHITE);v.setTextSize(0,size*unit);v.setGravity(Gravity.CENTER_VERTICAL);v.setIncludeFontPadding(false);v.setFocusable(false);fontSizes.put(v,size);return v;}
 private void clear(String title,Runnable back){revision++;if(previewLoader!=null){previewLoader.close();previewLoader=null;}for(CinemaWallpaperManager loader:thumbnails)loader.close();thumbnails.clear();heights.clear();fontSizes.keySet().removeIf(v->v!=heading&&v!=headerBack);navigationControls.clear();rows.removeAllViews();heading.setText(title);this.back=back;scroll.scrollTo(0,0);}
 private TextView action(String title,String tag,Runnable run){TextView v=text(title,23);v.setTag(tag);v.setSelected(title.startsWith("✓ "));v.setFocusable(true);v.setClickable(true);v.setDefaultFocusHighlightEnabled(false);v.setPadding(20,6,20,6);v.setMaxLines(2);v.setEllipsize(android.text.TextUtils.TruncateAt.END);v.setBackground(new CinemaSurface(false,0,false,false));v.setOnFocusChangeListener((view,f)->view.setBackground(new CinemaSurface(f,0xFF9ABBD8,false,false)));CinemaUiSounds.bindClick(v,view->run.run());rows.addView(v,new LinearLayout.LayoutParams(-1,Math.round(76*unit)));heights.put(v,76);navigationControls.add(v);return v;}
 private void focusFirst(){post(()->{if(disposed)return;for(View control:navigationControls)if(eligibleControl(control)){focusControl(control);break;}});}
 private void section(String title){TextView h=text(title,24);h.setTextColor(0xFFADCDE7);rows.addView(h,new LinearLayout.LayoutParams(-1,Math.round(62*unit)));heights.put(h,62);}
 private LinearLayout line(){LinearLayout line=new LinearLayout(activity);line.setOrientation(LinearLayout.HORIZONTAL);rows.addView(line,new LinearLayout.LayoutParams(-1,-2));return line;}
 private void inLine(LinearLayout line,TextView v){rows.removeView(v);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,Math.round(76*unit),1);lp.setMargins(3,3,3,3);line.addView(v,lp);v.setGravity(Gravity.CENTER);}
 private void thumb(LinearLayout line,String reference,String fallback,String title){thumb(line,reference,fallback,title,240);}
 private void thumb(LinearLayout line,String reference,String fallback,String title,int height){LinearLayout cell=new LinearLayout(activity);cell.setOrientation(LinearLayout.VERTICAL);line.addView(cell,new LinearLayout.LayoutParams(0,-2,1));ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);cell.addView(image,new LinearLayout.LayoutParams(-1,Math.round(height*unit)));heights.put(image,height);TextView name=text(title,22);cell.addView(name,new LinearLayout.LayoutParams(-1,Math.round(42*unit)));heights.put(name,42);CinemaWallpaperManager loader=new CinemaWallpaperManager(activity,image,ignored->{},false);thumbnails.add(loader);loader.load(reference,480,270,fallback);}
 void showRoot(){showRoot(null);}
 private void showRoot(String anchor){
  clear("首頁個人化",dismiss);
  section("桌布主題 · "+settings.theme().title);
  LinearLayout themes=line();for(WallpaperTheme t:WallpaperTheme.BUILT_INS)inLine(themes,action((settings.theme().id.equals(t.id)?"✓ ":"")+t.title,"theme:"+t.id,()->{settings.theme(t.id);changed.run();showRoot("theme:"+t.id);}));
  LinearLayout previews=line();thumb(previews,settings.image("day"),settings.theme().sunriseImage,"日出預覽");thumb(previews,settings.image("night"),settings.theme().sunsetImage,"日落預覽");
  LinearLayout custom=line();for(String target:new String[]{"day","night"}){String id=settings.theme().id;inLine(custom,action(target.equals("day")?"自訂日出圖片":"自訂日落圖片","wallpaper:"+target,()->pick.accept("wallpaper:"+id+":"+target)));}
  action("恢復本主題內建圖片（保留切換時間）","wallpaper:reset",()->{settings.resetThemeImages();changed.run();showRoot("wallpaper:reset");});
  section("桌布模式");LinearLayout modes=line();String[] labels={"自動切換","固定日出","固定日落"};int n=0;
  for(WallpaperTheme.DisplayMode mode:WallpaperTheme.DisplayMode.values()){String label=labels[n++];inLine(modes,action((settings.displayMode()==mode?"✓ ":"")+label,"display:"+mode.name(),()->{settings.displayMode(mode);changed.run();refreshChoices();}));}
  LinearLayout times=line();inLine(times,action("日出　"+CinemaSchedule.label(settings.dayMinute()),"schedule:day",()->editTime(true)));inLine(times,action("日落　"+CinemaSchedule.label(settings.nightMinute()),"schedule:night",()->editTime(false)));
  section("最近項目背景 · 依位置，不綁定 App");
  LinearLayout recentPreview=line();for(int i=0;i<4;i++)thumb(recentPreview,settings.recentImage(i),"season:"+i,"第 "+(i+1)+" 張 · "+CinemaSeasons.NAMES[i],130);
  for(int i=0;i<4;i++){
   final int position=i;LinearLayout controls=line();
   inLine(controls,action("第 "+(i+1)+" 張 · 更換圖片","recent:"+i+":image",()->pick.accept("recent:"+position)));
   inLine(controls,action("恢復"+CinemaSeasons.NAMES[i]+"季圖片","recent:"+i+":reset",()->{settings.resetRecentImage(position);changed.run();showRoot("recent:"+position+":reset");}));
  }
  action("管理已保存的自訂圖片","images:manage",()->pick.accept("manage_images"));
  section("視覺效果 · 光暈強度");LinearLayout glow=line();for(int value:new int[]{0,50,85,100})inLine(glow,action((settings.glow()==value?"✓ ":"")+value+"%"+(value==85?" 電影級":""),"glow:"+value,()->{settings.glow(value);changed.run();refreshChoices();}));
  action("恢復預設效果（不變更圖片與使用紀錄）","effects:reset",()->{settings.resetEffects();changed.run();showRoot("effects:reset");});action("完成，返回設定","personal:back",dismiss);
  post(()->{if(disposed)return;View target=anchor==null?findViewWithTag("theme:"+settings.theme().id):findViewWithTag(anchor);if(target!=null&&target.isFocusable()){target.requestFocus();android.graphics.Rect rect=new android.graphics.Rect();target.getDrawingRect(rect);target.requestRectangleOnScreen(rect,true);}});
 }
 // These values do not change thumbnail sources. Keep views, scroll and DPAD focus intact.
 private void refreshChoices(){
  revision++;
  String[] labels={"自動切換","固定日出","固定日落"};int n=0;
  for(WallpaperTheme.DisplayMode mode:WallpaperTheme.DisplayMode.values()){
   TextView v=findViewWithTag("display:"+mode.name());if(v!=null){v.setSelected(settings.displayMode()==mode);v.setText((settings.displayMode()==mode?"✓ ":"")+labels[n]);}n++;
  }
  for(int value:new int[]{0,50,85,100}){
   TextView v=findViewWithTag("glow:"+value);if(v!=null){v.setSelected(settings.glow()==value);v.setText((settings.glow()==value?"✓ ":"")+value+"%"+(value==85?" 電影級":""));}
  }
 }
 private void editTime(boolean day){if(timeEditor!=null)return;timeAnchor=findFocus();box.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);timeEditor=new CinemaTimePanel(activity,day?"日出切換時間":"日落切換時間",day?settings.dayMinute():settings.nightMinute(),value->{if(!settings.schedule(day,value))return false;changed.run();return true;},()->{removeView(timeEditor);timeEditor=null;box.setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);showRoot(day?"schedule:day":"schedule:night");});addView(timeEditor,new LayoutParams(-1,-1));}
 private void showWallpapers(){showRoot();}
 private LinearLayout.LayoutParams previewLayout(boolean recent){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(Math.round(960*unit),Math.round((recent?460.8f:540)*unit));lp.gravity=Gravity.CENTER_HORIZONTAL;return lp;}
 private void previewCurrent(String target){clear("圖片預覽",this::showWallpapers);ImageView image=new ImageView(activity);rows.addView(image,previewLayout(false));previewLoader=new CinemaWallpaperManager(activity,image,fallback->{},false);previewLoader.load(settings.image(target),960,540);action("返回","back",this::showWallpapers);focusFirst();}
 void preview(String target,CinemaImageStore.Imported imported,Runnable accept,Runnable discard){clear("預覽新圖片 · 確認後才取代",()->cancel());cancelPreview=discard;previewAnchor=target.startsWith("recent:")?target+":image":null;
  ImageView image=new ImageView(activity);image.setScaleType(ImageView.ScaleType.CENTER_CROP);image.setImageBitmap(imported.preview);rows.addView(image,previewLayout(target.startsWith("recent:")));
  action("使用這張圖片","image:confirm",()->{cancelPreview=null;accept.run();showRoot(previewAnchor);});action("取消，保留原圖","image:cancel",this::cancel);focusFirst();
 }
 private void cancel(){if(cancelPreview!=null){Runnable r=cancelPreview;cancelPreview=null;r.run();}showRoot(previewAnchor);}
 void message(String message){clear(message,this::showRoot);action("返回","back",this::showRoot);focusFirst();}
 void dispose(){disposed=true;for(CinemaWallpaperManager loader:thumbnails)loader.close();thumbnails.clear();if(cancelPreview!=null){cancelPreview.run();cancelPreview=null;}if(previewLoader!=null){previewLoader.close();previewLoader=null;}}
 private boolean eligibleControl(View v){return v.isEnabled()&&v.isFocusable()&&v.isShown();}
 private int controlRow(View control){View child=control;while(child.getParent() instanceof View&&child.getParent()!=rows)child=(View)child.getParent();return child.getParent()==rows?rows.indexOfChild(child):-1;}
 private float controlCenter(View v){v.getDrawingRect(navigationRect);rows.offsetDescendantRectToMyCoords(v,navigationRect);return navigationRect.exactCenterX();}
 private boolean focusControl(View target){
  if(!target.requestFocus())return false;
  target.getDrawingRect(navigationRect);target.requestRectangleOnScreen(navigationRect,true);return true;
 }
 private boolean navigate(int key){
  View current=findFocus();
  if(current==headerBack){if(key==KeyEvent.KEYCODE_DPAD_DOWN)for(View v:navigationControls)if(eligibleControl(v)){focusControl(v);break;}return true;}
  if(current==null||!navigationControls.contains(current)){for(View v:navigationControls)if(eligibleControl(v)&&focusControl(v))return true;return true;}
  int row=controlRow(current);float x=controlCenter(current);View target=null;
  if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
   float distance=Float.MAX_VALUE;int direction=key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1;
   for(View v:navigationControls)if(v!=current&&eligibleControl(v)&&controlRow(v)==row){float delta=(controlCenter(v)-x)*direction;if(delta>0&&delta<distance){distance=delta;target=v;}}
  }else{
   // Vertical movement follows the next actionable row, skipping non-interactive previews.
   // Use content coordinates, not screen distance or viewport visibility; offscreen controls
   // must be reachable so ScrollView can reveal them after the focus transfer.
   int direction=key==KeyEvent.KEYCODE_DPAD_DOWN?1:-1,bestRow=Integer.MAX_VALUE;float distance=Float.MAX_VALUE;
   for(View v:navigationControls)if(v!=current&&eligibleControl(v)){
    int delta=(controlRow(v)-row)*direction;if(delta<=0)continue;float dx=Math.abs(controlCenter(v)-x);
    if(delta<bestRow||delta==bestRow&&dx<distance){bestRow=delta;distance=dx;target=v;}
   }
  }
  if(target!=null)focusControl(target);else if(key==KeyEvent.KEYCODE_DPAD_UP)headerBack.requestFocus();
  return true; // A real edge remains inside this panel; never fall through to the HOME behind it.
 }
 @Override public boolean dispatchKeyEvent(KeyEvent event){
  if(timeEditor!=null)return timeEditor.dispatchKeyEvent(event);
  int key=event.getKeyCode();
  if(key==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP&&back!=null)back.run();return true;}
  if(event.getAction()==KeyEvent.ACTION_DOWN&&key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT){
   // Preserve text cursor/IME behavior; vertical movement still exits a single-line name editor.
   if(findFocus() instanceof EditText&&(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT))return super.dispatchKeyEvent(event);
   return navigate(key);
  }
  return super.dispatchKeyEvent(event);
 }
 private int lastW,lastH;
 @Override protected void onMeasure(int ws,int hs){int w=MeasureSpec.getSize(ws),h=MeasureSpec.getSize(hs);if(w!=lastW||h!=lastH){lastW=w;lastH=h;unit=Math.min(w/1920f,h/1080f);box.setLayoutParams(new LayoutParams(Math.round(1200*unit),Math.round(980*unit),Gravity.CENTER));box.setPadding(Math.round(32*unit),Math.round(20*unit),Math.round(32*unit),Math.round(20*unit));}
  for(Map.Entry<View,Integer> e:heights.entrySet()){ViewGroup.LayoutParams lp=e.getKey().getLayoutParams();int height=Math.round(e.getValue()*unit);if(lp.height!=height){lp.height=height;e.getKey().setLayoutParams(lp);}}
  headerBack.getLayoutParams().width=Math.round(220*unit);((View)heading.getParent()).getLayoutParams().height=Math.round(84*unit);
  for(Map.Entry<TextView,Integer> e:fontSizes.entrySet())e.getKey().setTextSize(0,e.getValue()*unit);
  super.onMeasure(ws,hs);
 }
}
