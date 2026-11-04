package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Cinema presentation composed over the existing launcher, retaining its catalogue and launch path. */
final class CinemaHome implements AutoCloseable {
    static final int PICK_IMAGE=CinemaImagePicker.CONTENT;
    final Activity activity;
    final Scene scene;
    final ImageView wallpaper;
    final CinemaStage stage;
    final CinemaReadabilityShade readability=new CinemaReadabilityShade();
    final CinemaFocusLight focusLight;
    final CinemaSeasons seasons;
    final CinemaImageStore imageStore;
    final TextView networkButton;
    private CinemaNetwork.State networkState;
    CinemaPersonalizationPanel personalization;
    CinemaStartupPanel startupPanel;
    CinemaOrderPanel orderPanel;
    private TextView startupNotice;
    private boolean clockRegistered;
    private final android.content.BroadcastReceiver clockReceiver=new android.content.BroadcastReceiver(){@Override public void onReceive(android.content.Context context,Intent intent){if(active&&!closed){updateDate();refreshWallpaper();}}};
    final LinearLayout top;
    final View libraryButton, inputsButton, settingsButton;
    final HorizontalScrollView dockScroll, recentScroll;
    final LinearLayout dock, recent;
    private final TextClock time;
    private final TextView date;
    private final TextView recentTitle,empty;
    
    private final AppAdapter adapter;
    private final Config config;
    final CinemaSettings options;
    final CinemaWallpaperManager wallpapers;
    private final CinemaPalette palette=new CinemaPalette();
    private final List<View> topViews=new ArrayList<>();
    private final List<Card> dockCards=new ArrayList<>(),recentCards=new ArrayList<>();
    private List<AppAdapter.Entry> apps=Collections.emptyList();
    private List<AppAdapter.Entry> carouselApps=Collections.emptyList();
    private String dockKey="",returnKey="",returnZone="dock";
    private boolean launched,active,closed;
    private CinemaSettingsPanel.State settingsState;private CinemaPanelMotion panelMotion;
    private long lastLaunch=-1;
    private float unit=1f;
    private Card featuredCard,animatedCard;
    private final ArrayList<Card> spareCards=new ArrayList<>();
    private final Map<String,CardAsset> cardAssets=new java.util.LinkedHashMap<String,CardAsset>(16,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,CardAsset> e){return size()>CinemaMemoryPolicy.MAX_CARD_ASSETS;}
    };
    private final Map<String,CinemaReflection> reflectionCache=new java.util.LinkedHashMap<String,CinemaReflection>(8,.75f,true){@Override protected boolean removeEldestEntry(Map.Entry<String,CinemaReflection> e){return size()>8;}};
    private final Runnable settledReflection=this::settleReflection;
    private void settleReflection(){
        if(!active||closed||!CinemaLighting.reflection(options)||featuredCard==null)return;
        Card c=featuredCard;CinemaReflection mirror=reflectionCache.get(c.entry.pkg);
        if(mirror==null){mirror=new CinemaReflection(c.icon.getDrawable(),c.accent());mirror.setAlpha(Math.round(255*CinemaLighting.REFLECTION_ALPHA));reflectionCache.put(c.entry.pkg,mirror);}
        focusLight.reflection(mirror);
    }
    private final class CardAsset {
        final AppAdapter.Entry source;final Drawable icon;final int color;final CinemaCardSurface surface;
        CardAsset(AppAdapter.Entry entry){source=entry;Drawable original=entry.icon!=null?entry.icon:entry.banner;icon=CinemaIconFit.wrap(original!=null&&original.getConstantState()!=null?original.getConstantState().newDrawable(activity.getResources()).mutate():original);
            int explicit=options.color(entry.pkg);color=explicit!=0?CinemaPalette.normalize(explicit):palette.color(entry.pkg,original);surface=new CinemaCardSurface(color);}
    }
    private CardAsset assetFor(AppAdapter.Entry entry){
        CardAsset a=cardAssets.get(entry.pkg);int explicit=options.color(entry.pkg);
        int color=explicit!=0?CinemaPalette.normalize(explicit):palette.color(entry.pkg,entry.icon!=null?entry.icon:entry.banner);
        if(a==null||a.source!=entry||a.color!=color){a=new CardAsset(entry);cardAssets.put(entry.pkg,a);reflectionCache.remove(entry.pkg);}
        a.surface.setBounds(0,0,Math.round(CinemaCarousel.CARD_WIDTH*unit),Math.round(CinemaCarousel.CARD_HEIGHT*unit));
        a.surface.lighting(CinemaLighting.border(options));focusLight.prepare(a.color);return a;
    }
    private void prepareAssets(){
        int first=CinemaCarousel.first(Math.max(0,selectedIndex),carouselApps.size());
        int end=carouselApps.size()<=CinemaMemoryPolicy.MAX_CARD_ASSETS?carouselApps.size():Math.min(carouselApps.size(),first+CinemaCarousel.MAX_VISIBLE_ITEMS+1);
        int begin=carouselApps.size()<=CinemaMemoryPolicy.MAX_CARD_ASSETS?0:Math.max(0,first-1);
        for(int i=begin;i<end;i++)assetFor(carouselApps.get(i));
    }
    int selectedIndex=-1,firstVisibleIndex,visibleCount;
    private boolean initialAppsPending=true,initialFocusPending=true,windowFocused;
    private long focusRevision;
    private boolean updatingWindow,windowLayoutPending,windowAnimatePending;
    private android.animation.ValueAnimator carouselAnimator;
    enum FocusZone { TOP_ACTIONS, APP_CAROUSEL, RECENT_ITEMS, OVERLAY }
    FocusZone focusZone=FocusZone.APP_CAROUSEL;
    private boolean zoneTransition;
    private float zoneStart=1,zoneEnd=1,zoneStrength=1;
    private void enterZone(FocusZone next){
        if(focusZone==next)return;
        boolean wasCarousel=focusZone==FocusZone.APP_CAROUSEL;
        focusZone=next;
        if(next==FocusZone.APP_CAROUSEL)return; // selectDock drives the single reusable animator.
        if(!wasCarousel||featuredCard==null)return;
        if(carouselAnimator!=null)carouselAnimator.cancel();
        zoneTransition=true;animatedCard=featuredCard;zoneStart=zoneStrength;zoneEnd=.25f;
        depthOrder.clear();depthOrder.addAll(dockCards);
        for(Card c:depthOrder){c.startFocus=c.focusLevel;c.targetFocus=0;c.startScale=c.getScaleX();c.targetScale=c==featuredCard?1:c.getScaleX();}
        startCarouselAnimation();
    }
    private void placeZone(float fraction){
        float t=1-(1-fraction)*(1-fraction);zoneStrength=zoneStart+(zoneEnd-zoneStart)*t;focusLight.zone(zoneStrength);
        focusLight.beginNeighbors();
        for(Card c:depthOrder){c.lighting(c.startFocus+(c.targetFocus-c.startFocus)*t);float scale=c.startScale+(c.targetScale-c.startScale)*t;c.setScaleX(scale);c.setScaleY(scale);projectedBounds(c);focusLight.neighbor(c.projected,c.accent(),c==featuredCard?1:0,Math.abs(c.appIndex-selectedIndex));}
        if(featuredCard!=null)focusLight.position(featuredCard.projected,1,unit,CinemaLighting.pool(options));
    }
    private void paintTop(View view,boolean focused){
        view.animate().cancel();view.setBackground(new CinemaActionSurface(focused));
        if(view.getForeground()!=null)view.getForeground().setAlpha(focused?255:215);
        float scale=focused?1.08f:1;
        if(motion())view.animate().scaleX(scale).scaleY(scale).setDuration(200).start();else{view.setScaleX(scale);view.setScaleY(scale);}
    }
    private final ArrayList<Card> depthOrder=new ArrayList<>();
    private float carouselAnchorStart,carouselAnchorEnd;
    CinemaAppPanel libraryPanel;
    CinemaSettingsPanel settingsPanel;
    CinemaAdbPanel adbPanel;
    private View overlay,overlayAnchor;
    private final TextView topLabel,recentHint;
    private final RecyclerView.AdapterDataObserver observer=new RecyclerView.AdapterDataObserver(){
        @Override public void onChanged(){bindApps();}
    };
    final CinemaUiSounds sounds;
    CinemaHome(Activity activity,FrameLayout root,AppAdapter adapter,Config config,View library,View inputs,View settings){
        this.activity=activity;this.adapter=adapter;this.config=config;
        libraryButton=library;inputsButton=inputs;settingsButton=settings;options=new CinemaSettings(activity);
        // The original hierarchy stays allocated for its menus/listeners, but draws and focuses nothing.
        for(int i=0;i<root.getChildCount();i++)root.getChildAt(i).setVisibility(View.GONE);
        root.setBackgroundColor(0xFF0B111B);
        scene=new Scene(activity);scene.setClipChildren(false);scene.setClipToPadding(false);
        root.addView(scene,new FrameLayout.LayoutParams(-1,-1));
        sounds=new CinemaUiSounds(scene,options);
        wallpaper=new ImageView(activity);wallpaper.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        wallpaper.setFocusable(false);scene.addView(wallpaper,new FrameLayout.LayoutParams(-1,-1));
        stage=new CinemaStage(activity);
        wallpapers=new CinemaWallpaperManager(activity,wallpaper,this::wallpaperLoaded);
        seasons=new CinemaSeasons(activity,()->{for(Card c:recentCards)c.updateArt();});
        imageStore=new CinemaImageStore(activity);

        View scrim=new View(activity);scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,new int[]{0x18040A12,0x10040A12,0xAD09101C}));
        scrim.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);scene.addView(stage);scene.addView(scrim,new FrameLayout.LayoutParams(-1,-1));
        View captionShade=new View(activity);captionShade.setFocusable(false);captionShade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);captionShade.setBackground(readability);scene.addView(captionShade,new FrameLayout.LayoutParams(-1,-1));
        focusLight=new CinemaFocusLight(activity);scene.addView(focusLight);
        time=new TextClock(activity);time.setFormat12Hour("h:mm");time.setFormat24Hour("HH:mm");textStyle(time,64,0xFFF2F4F7,"sans-serif-thin");
        date=new TextView(activity);textStyle(date,22,0xFFD5DDE8,"sans-serif");updateDate();
        scene.addView(time);scene.addView(date);
        top=new LinearLayout(activity);top.setOrientation(LinearLayout.HORIZONTAL);top.setGravity(Gravity.CENTER_VERTICAL);top.setClipChildren(false);scene.addView(top);
        networkButton=iconButton(CinemaIcon.NETWORK_OFFLINE,activity.getString(R.string.cinema_network_offline));take(networkButton);CinemaUiSounds.bindClick(networkButton,v->openNetworkSettings());
        take(library);library.setVisibility(View.VISIBLE);library.setContentDescription("所有應用程式");library.setTag(CinemaIcon.GRID);((TextView)library).setText(null);CinemaUiSounds.bindClick(library,v->openLibrary(false));
        inputs.setVisibility(View.GONE);inputs.setFocusable(false);
        topLabel=new TextView(activity);textStyle(topLabel,18,0xFFE0E8F5,"sans-serif-medium");topLabel.setGravity(Gravity.END);scene.addView(topLabel);
        take(settings);((TextView)settings).setText(null);((TextView)settings).setCompoundDrawables(null,null,null,null);
        settings.setContentDescription(activity.getString(R.string.cinema_title));settings.setBackground(glass(false,0));CinemaUiSounds.bindClick(settings,v->showSettings());
        // Reuse the existing accessibility capability only; never enable a helper or use shell shutdown.
        if(HomeKeyService.canOpenPowerMenu()){
            TextView power=iconButton(CinemaIcon.POWER,activity.getString(R.string.power_menu));top.addView(power);topViews.add(power);CinemaUiSounds.bindClick(power,v->HomeKeyService.openPowerMenu());
        }
        dock=new LinearLayout(activity);dock.setGravity(Gravity.TOP);dock.setOrientation(LinearLayout.HORIZONTAL);dock.setClipChildren(false);dock.setClipToPadding(false);
        dockScroll=scroll(dock);dockScroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->followDockScroll());scene.addView(dockScroll);
        recentTitle=new TextView(activity);recentTitle.setText(R.string.cinema_recent);textStyle(recentTitle,22,0xFFE0E6EF,"sans-serif-medium");scene.addView(recentTitle);
        recent=new LinearLayout(activity);recent.setOrientation(LinearLayout.HORIZONTAL);recent.setClipChildren(false);recent.setClipToPadding(false);
        recentScroll=scroll(recent);scene.addView(recentScroll);
        recentHint=new TextView(activity);textStyle(recentHint,18,0xFF9BAAC2,"sans-serif");recentHint.setText("由此首頁開啟的應用程式");recentHint.setGravity(Gravity.END);scene.addView(recentHint);
        empty=new TextView(activity);empty.setText(R.string.cinema_empty);textStyle(empty,22,0xFFA6AFBF,"sans-serif-light");scene.addView(empty);
        for(View v:topViews){v.setDefaultFocusHighlightEnabled(false);v.setOnKeyListener((view,key,event)->keys(view,"top",key,event));v.setOnFocusChangeListener((view,focus)->{
            if(focus)enterZone(FocusZone.TOP_ACTIONS);paintTop(view,focus);topLabel.setText(focus?(view==networkButton&&networkState!=null?networkState.label(activity):view.getContentDescription()):"");
        });}
        refreshNetwork();adapter.registerAdapterDataObserver(observer);bindApps();imageStore.prune(options.imageReferences(apps));
    }
    private void take(View v){
        if(v.getParent() instanceof ViewGroup)((ViewGroup)v.getParent()).removeView(v);
        v.setFocusable(true);v.setClickable(true);top.addView(v,new LinearLayout.LayoutParams(-2,-2));topViews.add(v);
    }
    private TextView iconButton(int kind,String description){
        TextView b=new TextView(activity);b.setId(View.generateViewId());b.setGravity(Gravity.CENTER);b.setFocusable(true);b.setClickable(true);b.setContentDescription(description);b.setTag(kind);b.setBackground(glass(false,0));return b;
    }
    private HorizontalScrollView scroll(LinearLayout row){
        HorizontalScrollView s=new HorizontalScrollView(activity){
            @Override protected void dispatchDraw(android.graphics.Canvas canvas){int saved=canvas.save();canvas.clipRect(0,0,getWidth(),getHeight());super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
            // One scroll owner: native focus scrolling must not compete with our edge reveal.
            @Override public void requestChildFocus(View child,View focused){boolean before=isSmoothScrollingEnabled();setSmoothScrollingEnabled(false);super.requestChildFocus(child,focused);setSmoothScrollingEnabled(before);}
            @Override public boolean requestChildRectangleOnScreen(View child,Rect rectangle,boolean immediate){return false;}
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect){return 0;}
        };s.setHorizontalScrollBarEnabled(false);s.setOverScrollMode(View.OVER_SCROLL_NEVER);s.setClipChildren(true);s.setClipToPadding(true);s.setFillViewport(false);s.addView(row,new ViewGroup.LayoutParams(-2,-1));return s;
    }
    private void textStyle(TextView v,float size,int color,String font){v.setTextColor(color);v.setFontFeatureSettings("tnum");v.setTypeface(Typeface.create(font,Typeface.NORMAL));v.setIncludeFontPadding(false);v.setTextSize(TypedValue.COMPLEX_UNIT_PX,size*unit);}
    private GradientDrawable glass(boolean focus,int accent){
        GradientDrawable d=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{focus?0xD9303848:0xB8353B47,0xBB101621});
        d.setCornerRadius(24*unit);d.setStroke(Math.max(1,Math.round((focus?2.4f:1.2f)*unit)),focus?(accent==0?0xFFF4F7FF:accent):0x70D6DFEC);return d;
    }
    void bindApps(){
        if(closed)return;boolean hadCarouselFocus=dock.hasFocus();cancelAnimations();depthOrder.clear();apps=adapter.cinemaEntries();carouselApps=CinemaHomeActions.carousel(apps);cardAssets.clear();reflectionCache.clear();prepareAssets();
        // The initial empty catalogue contains only the synthetic All Apps action.
        // Its temporary focus must not become a remembered selection when real apps arrive.
        boolean firstApps=initialAppsPending&&!apps.isEmpty();
        if(firstApps){initialAppsPending=false;selectedIndex=0;dockKey=apps.get(0).pkg;returnKey=dockKey;returnZone="dock";}
        int keep=-1;for(int i=0;i<carouselApps.size();i++)if(carouselApps.get(i).pkg.equals(dockKey)){keep=i;break;}
        selectedIndex=carouselApps.isEmpty()?-1:keep>=0?keep:Math.max(0,Math.min(selectedIndex,carouselApps.size()-1));
        updatingWindow=true;try{dock.removeAllViews();dockCards.clear();spareCards.clear();featuredCard=null;}finally{updatingWindow=false;}
        firstVisibleIndex=-1;visibleCount=0;syncWindow();
        if(carouselApps.isEmpty())empty.setText(R.string.cinema_no_apps);else empty.setText(R.string.cinema_empty);
        bindRecent();if(libraryPanel!=null)libraryPanel.refreshShortcuts();scene.layoutDesign();scheduleInitialFocus();
        if(active&&overlay==null&&(hadCarouselFocus||(firstApps&&focusZone==FocusZone.APP_CAROUSEL))){long revision=focusRevision;scene.post(()->{if(revision==focusRevision)restoreFocus(0);});}
    }
    private Card cardAt(int index){for(Card c:dockCards)if(c.appIndex==index)return c;return null;}
    private void syncWindow(){
        int count=CinemaCarousel.count(carouselApps.size()),first=CinemaCarousel.first(selectedIndex,carouselApps.size());
        if(first==firstVisibleIndex&&count==visibleCount){featuredCard=cardAt(selectedIndex);return;}
        if(carouselAnimator!=null)carouselAnimator.cancel();depthOrder.clear();
        windowLayoutPending=true;windowAnimatePending=visibleCount>0&&visibleCount==count&&active;
        updatingWindow=true;
        try{
            for(int i=dockCards.size()-1;i>=0;i--){Card c=dockCards.get(i);if(c.appIndex<first||c.appIndex>=first+count){c.animate().cancel();dock.removeView(c);dockCards.remove(i);spareCards.add(c);}}
            for(int i=first;i<first+count;i++)if(cardAt(i)==null){Card c;if(spareCards.isEmpty()){c=new Card(carouselApps.get(i),false);c.appIndex=i;c.size();}else{c=spareCards.remove(spareCards.size()-1);c.rebind(carouselApps.get(i),i);}dockCards.add(i-first,c);dock.addView(c,i-first);}
            // Reused slots retain their measured bounds; framework lays out only the changed row.
            firstVisibleIndex=first;visibleCount=count;featuredCard=cardAt(selectedIndex);dock.scrollTo(0,0);dockScroll.scrollTo(0,0);
        }finally{updatingWindow=false;}
    }
    private boolean selectDock(int index,boolean request){
        if(index<0||index>=carouselApps.size())return false;
        scene.removeCallbacks(settledReflection);
        int previousIndex=selectedIndex;
        selectedIndex=index;dockKey=carouselApps.get(index).pkg;returnKey=dockKey;returnZone="dock";syncWindow();
        Card c=cardAt(index);if(c==null)return false;
        if(request&&!c.isFocused()){boolean accepted=c.requestFocus();if(accepted&&previousIndex!=index)sounds.moved();return accepted;}
        c.present(c.isFocused());arrangeDepth(c);if(previousIndex!=index&&c.isFocused())sounds.moved();return true;
    }
    private void bindRecent(){
        boolean hadFocus=recent.hasFocus();String remembered=returnKey;
        List<AppAdapter.Entry> history=new ArrayList<>();
        // History can contain removed or disabled apps. Fill four valid slots, not just
        // the first four raw records. Never substitute favourites for missing history.
        for(String pkg:LastOpened.recentPackages(activity,Integer.MAX_VALUE)){
            AppAdapter.Entry found=null;
            for(AppAdapter.Entry e:apps)if(e.pkg.equals(pkg)){found=e;break;}
            if(found==null)found=installedEntry(pkg);
            if(found!=null&&recentAvailable(found)){history.add(found);if(history.size()==4)break;}
        }
        boolean same=history.size()==recentCards.size();
        for(int i=0;same&&i<history.size();i++){
            AppAdapter.Entry a=history.get(i),b=recentCards.get(i).entry;
            same=a.pkg.equals(b.pkg)&&a.label.equals(b.label)&&a.component.equals(b.component)&&a.icon==b.icon;
        }
        if(!same){recent.removeAllViews();recentCards.clear();}
        // Create only real history items: 0–4 cards, with no placeholder Views.
        if(!same)for(AppAdapter.Entry entry:history){
            Card c=new Card(entry,true);c.meta.setVisibility(View.GONE);c.meta.setText("");
            recentCards.add(c);recent.addView(c);
        }
        // Artwork belongs to screen position, independently of app history; each position can have a custom image.
        seasons.sources(options.recentImages());
        recentTitle.setText("最近項目");recentHint.setText(history.isEmpty()?"從此首頁開啟 App 後會顯示於此":"");
        boolean show=options.on("recents");recentHint.setVisibility(show&&history.isEmpty()?View.VISIBLE:View.GONE);
        recentTitle.setVisibility(show?View.VISIBLE:View.GONE);recentScroll.setVisibility(show&&!history.isEmpty()?View.VISIBLE:View.GONE);empty.setVisibility(View.GONE);
        if(hadFocus&&!same){returnZone="recent";returnKey=remembered;scene.post(()->restoreFocus(0));}
    }

    private boolean recentAvailable(AppAdapter.Entry entry){
        try{
            PackageManager pm=activity.getPackageManager();
            if(!pm.getApplicationInfo(entry.pkg,0).enabled||entry.pkg.equals(activity.getPackageName()))return false;
            int app=pm.getApplicationEnabledSetting(entry.pkg),component=pm.getComponentEnabledSetting(entry.component);
            return app!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED&&app!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER&&app!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED
                &&component!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED&&component!=PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER;
        }catch(PackageManager.NameNotFoundException|IllegalArgumentException|SecurityException unavailable){return false;}
    }
    private AppAdapter.Entry installedEntry(String pkg){
        try{
            PackageManager pm=activity.getPackageManager();ApplicationInfo ai=pm.getApplicationInfo(pkg,0);if(!ai.enabled||pkg.equals(activity.getPackageName()))return null;
            Intent i=pm.getLeanbackLaunchIntentForPackage(pkg);if(i==null)i=pm.getLaunchIntentForPackage(pkg);if(i==null||i.getComponent()==null)return null;
            return new AppAdapter.Entry(Cards.cleanLabel(String.valueOf(ai.loadLabel(pm)),pkg),i.getComponent(),null,ai.loadIcon(pm));
        }catch(PackageManager.NameNotFoundException|SecurityException ignored){return null;}
    }
    private List<? extends View> row(String zone){return zone.equals("top")?topViews:zone.equals("recent")?recentCards:dockCards;}
    private boolean eligible(View v){return v.getVisibility()==View.VISIBLE&&v.isShown()&&v.isEnabled()&&v.isFocusable();}
    private float center(View v){int[] p=new int[2];v.getLocationOnScreen(p);return p[0]+v.getWidth()/2f;}
    private void userNavigation(KeyEvent event){
        int key=event.getKeyCode();
        if(event.getAction()==KeyEvent.ACTION_DOWN&&((key>=19&&key<=23)||key==66||key==160)&&overlay==null){
            View focused=scene.findFocus();
            if(focused!=null&&eligible(focused)){focusRevision++;if(windowFocused)initialFocusPending=false;}
        }
    }
    private boolean keys(View from,String zone,int key,KeyEvent event){
        if(event.getAction()!=KeyEvent.ACTION_DOWN)return false;
        userNavigation(event);
        List<? extends View> views=row(zone);
        if(key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT){
            if(zone.equals("dock")){
                int step=(key==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1)*(dock.getLayoutDirection()==View.LAYOUT_DIRECTION_RTL?-1:1);
                for(int i=selectedIndex+step;i>=0&&i<carouselApps.size();i+=step){Card c=cardAt(i);if(c==null||eligible(c))return selectDock(i,true);}
                return true;
            }
            float[] x=new float[views.size()];boolean[] valid=new boolean[x.length];int current=-1;
            for(int i=0;i<x.length;i++){View v=views.get(i);x[i]=center(v);valid[i]=eligible(v);if(v==from)current=i;}
            int target=CinemaPolicy.horizontal(key,current,x,valid);
            if(target==current&&target>=0)return true;
            // Consume a transfer only when the expected actual View accepted focus.
            return target>=0&&views.get(target).requestFocus();
        }
        if(key==KeyEvent.KEYCODE_DPAD_UP){if(zone.equals("top"))return true;if(zone.equals("recent"))return returnToCarousel();nearestFocus(from,topViews);return true;}
        if(key==KeyEvent.KEYCODE_DPAD_DOWN){
            if(zone.equals("top")){return returnToCarousel();}
            if(zone.equals("recent")||!options.on("recents")||recentCards.isEmpty())return true;
            nearestFocus(from,recentCards);return true;
        }
        return false;
    }
    private boolean returnToCarousel(){if(dockCards.isEmpty())return true;returnZone="dock";restoreFocus(0);return true;}
    private boolean nearestFocus(View from,List<? extends View> candidates){
        float[] x=new float[candidates.size()];boolean[] e=new boolean[x.length];for(int i=0;i<x.length;i++){x[i]=center(candidates.get(i));e[i]=eligible(candidates.get(i));}
        int n=CinemaPolicy.nearest(center(from),x,e);return n>=0&&candidates.get(n).requestFocus();
    }
    void restoreFocus(int fallback){
        if(closed||overlay!=null)return;
        // A queued focus restore can run after an inactive trim, before onResume.
        // Rebinding a recycled card still requires its icon/surface cache.
        prepareAssets();
        if(returnZone.equals("recent")&&options.on("recents"))for(Card c:recentCards)if(c.entry.pkg.equals(returnKey)&&eligible(c)){c.requestFocus();return;}
        if(!carouselApps.isEmpty()){
            int start=Math.max(0,Math.min(selectedIndex<0?fallback:selectedIndex,carouselApps.size()-1));
            for(int i=0;i<carouselApps.size();i++){int index=(start+i)%carouselApps.size();Card c=cardAt(index);if((c==null||eligible(c))&&selectDock(index,true))return;}
        }
        if(eligible(libraryButton))libraryButton.requestFocus();else for(View v:topViews)if(eligible(v)&&v.requestFocus())return;
    }
    // First entry is committed after Android restores the window focus, not during catalogue binding.
    void windowFocusChanged(boolean focused){windowFocused=focused;if(focused)scheduleInitialFocus();}
    private void scheduleInitialFocus(){
        if(!initialFocusPending||initialAppsPending||!active||!windowFocused||closed||overlay!=null)return;
        final long revision=focusRevision;
        scene.postOnAnimation(()->{
            if(revision!=focusRevision||!initialFocusPending||initialAppsPending||!active||!windowFocused||closed||overlay!=null)return;
            initialFocusPending=false;returnZone="dock";returnKey="";dockKey="";selectedIndex=0;restoreFocus(0);
        });
    }
    void homePressed(){
        final long revision=++focusRevision;
        closePanel(false);returnZone="dock";returnKey="";dockKey="";selectedIndex=0;
        // Commit the new starting selection before onResume/layout can reuse the old focused card.
        // restoreFocus skips unavailable views; an empty catalogue still keeps All Apps usable.
        restoreFocus(0);
        scene.post(()->{if(revision==focusRevision)restoreFocus(0);});
    }
    void resume(){updateDate();refreshNetwork();active=true;sounds.resume();launched=false;wallpapers.resume();seasons.resume();
        if(!clockRegistered){android.content.IntentFilter f=new android.content.IntentFilter(Intent.ACTION_TIME_TICK);f.addAction(Intent.ACTION_TIME_CHANGED);f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            if(android.os.Build.VERSION.SDK_INT>=33)activity.registerReceiver(clockReceiver,f,android.content.Context.RECEIVER_NOT_EXPORTED);else activity.registerReceiver(clockReceiver,f);clockRegistered=true;}
        applyEffects();scheduleInitialFocus();}
    private void wallpaperLoaded(boolean fallback){if(fallback&&active)toast(R.string.cinema_wallpaper_error);stage.image(wallpapers.stage());readability.wallpaper(wallpaper.getDrawable());}
    void refreshWallpaper(){wallpapers.load(options.effectiveWallpaper(),scene.getWidth(),scene.getHeight(),options.effectiveDefaultWallpaper());}
    void pause(){if(panelMotion!=null)panelMotion.finish();active=false;sounds.pause();if(clockRegistered){activity.unregisterReceiver(clockReceiver);clockRegistered=false;}cancelAnimations();wallpapers.pause();seasons.pause();}
    void trim(){CinemaCatalogue.trimIcons();wallpapers.trim();seasons.trim();CinemaWallpaperManager.trimThumbnails();if(!active){stage.image(null);palette.clear();cardAssets.clear();reflectionCache.clear();focusLight.clear();depthOrder.clear();featuredCard=null;animatedCard=null;for(Card c:dockCards){c.premium=null;c.plate.setBackground(null);c.icon.setImageDrawable(null);}for(Card c:recentCards)c.art.setImageDrawable(null);}}
    private void cancelAnimations(){scene.removeCallbacks(settledReflection);if(carouselAnimator!=null)carouselAnimator.cancel();wallpaper.animate().cancel();for(Card c:dockCards)c.animate().cancel();for(Card c:recentCards)c.animate().cancel();}
    private long duration(){return options.mode().equals("Lite")?100:140;} // Existing recent-card animation stays unchanged.
    private boolean motion(){return !scene.restoringGeometry&&options.motion()&&!config.reducedMotion&&android.animation.ValueAnimator.areAnimatorsEnabled();}
    private void applyEffects(){
        sounds.settingsChanged();cancelAnimations();
        focusLight.streaks(options.on("light_streaks"));focusLight.intensity(options.glow());
        for(Card c:recentCards)if(!c.isFocused()){c.setScaleX(1);c.setScaleY(1);c.setTranslationY(0);}
        if(!options.parallax()){wallpaper.setTranslationX(0);}
        
        dockScroll.setSmoothScrollingEnabled(motion());recentScroll.setSmoothScrollingEnabled(motion());
        prepareAssets();for(Card c:dockCards)c.size();bindRecent();scene.layoutDesign();
        View focus=activity.getCurrentFocus();if(focus instanceof Card)((Card)focus).selected(true);else if(featuredCard!=null){featuredCard.present(false);arrangeDepth(featuredCard);}
        if(returnZone.equals("recent")&&!options.on("recents")){returnZone="dock";restoreFocus(0);}
    }
    private void activateCarousel(Card card){
        if(CinemaHomeActions.isAllApps(card.entry))openLibrary(false,card);
        else launch(card.entry);
    }
    void refreshNetwork(){
        if(closed)return;
        CinemaNetwork.State next=CinemaNetwork.read(activity);
        if(next.equals(networkState))return;
        networkState=next;networkButton.setTag(next.icon());
        networkButton.setContentDescription(next.description(activity));
        networkButton.setForeground(new CinemaButtonIcon(next.icon()));
        networkButton.getForeground().setAlpha(networkButton.isFocused()?255:215);
        if(networkButton.isFocused())topLabel.setText(next.label(activity));
    }
    private void openNetworkSettings(){
        Intent intent=CinemaNetwork.settingsIntent(activity);
        if(intent==null){toast(R.string.not_available);return;}
        long now=SystemClock.elapsedRealtime();if(!CinemaPolicy.mayLaunch(lastLaunch,now,launched))return;
        launched=true;lastLaunch=now;open(intent);
        scene.postDelayed(()->{if(active)launched=false;},550);
    }
    private void launch(AppAdapter.Entry entry){
        long now=SystemClock.elapsedRealtime();if(!CinemaPolicy.mayLaunch(lastLaunch,now,launched))return;
        launched=true;lastLaunch=now;adapter.launchForCinema(entry);
        // If an activity could not start, permit another attempt after the normal debounce interval.
        scene.postDelayed(()->{if(active)launched=false;},550);
    }
    private void followDockScroll(){/* Fixed seven-card viewport; optical geometry follows placeCarousel. */}
    private void focusGlow(Card card){
        // Wallpaper stays fixed; optical geometry is updated by placeCarousel.
        
        wallpaper.animate().cancel();wallpaper.setTranslationX(0);
    }
    private final class Card extends LinearLayout {
        AppAdapter.Entry entry;final boolean small;final int seasonIndex;final FrameLayout plate;final ImageView icon,art;final TextView name,meta;
        private CinemaSurface normalFace,focusedFace;
        private final CinemaQuickFocus quickRing=new CinemaQuickFocus();
        private CinemaCardSurface premium;
        int appIndex=-1;
        float focusLevel,startFocus,targetFocus;
        final android.graphics.RectF projected=new android.graphics.RectF();
        float frameTranslation,startScale,targetScale,startRotation,targetRotation,startDepth,targetDepth;
        Card(AppAdapter.Entry entry,boolean small){super(activity);if(CinemaProbe.enabled&&!small)CinemaProbe.cards++;this.entry=entry;this.small=small;this.seasonIndex=recentCards.size()%4;setDefaultFocusHighlightEnabled(false);setId(View.generateViewId());setOrientation(VERTICAL);setGravity(Gravity.CENTER_HORIZONTAL);setFocusable(true);setFocusableInTouchMode(true);setClickable(true);setClipChildren(false);setClipToPadding(false);setContentDescription(entry.label);
            plate=new FrameLayout(activity){
                private final android.graphics.Path mask=new android.graphics.Path();
                @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);mask.reset();mask.addRoundRect(new android.graphics.RectF(0,0,w,h),22*unit,22*unit,android.graphics.Path.Direction.CW);}
                @Override protected void dispatchDraw(android.graphics.Canvas canvas){if(!small){super.dispatchDraw(canvas);return;}int saved=canvas.save();canvas.clipPath(mask);super.dispatchDraw(canvas);canvas.restoreToCount(saved);}
            };plate.setBackground(new CinemaSurface(false,0,small,false));plate.setElevation(small?5*unit:0);addView(plate);setCameraDistance(CinemaCarousel.CAMERA_DISTANCE*unit);
            art=new ImageView(activity);if(small){art.setScaleType(ImageView.ScaleType.CENTER_CROP);art.setAlpha(1f);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);plate.setClipToOutline(true);plate.addView(art);View shade=new View(activity);shade.setBackground(new CinemaRecentShade());plate.addView(shade,new FrameLayout.LayoutParams(-1,-1));updateArt();}
            icon=new ImageView(activity);icon.setScaleType(ImageView.ScaleType.FIT_CENTER);Drawable d=entry.icon!=null?entry.icon:entry.banner;
            icon.setImageDrawable(CinemaIconFit.wrap(d!=null&&d.getConstantState()!=null?d.getConstantState().newDrawable(activity.getResources()).mutate():d));plate.addView(icon);
            name=new TextView(activity);name.setText(entry.label);name.setGravity(Gravity.CENTER);name.setMaxLines(1);name.setEllipsize(TextUtils.TruncateAt.END);textStyle(name,small?21:23,0xFFD8E0EB,small?"sans-serif-medium":"sans-serif");if(small){name.setMaxLines(1);name.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);plate.addView(name);}else addView(name);
            meta=new TextView(activity);meta.setText("");textStyle(meta,16,0xFFB3C2D8,"sans-serif");if(small)plate.addView(meta);
            setOnFocusChangeListener((v,focused)->selected(focused));setOnKeyListener((v,key,event)->keys(v,small?"recent":"dock",key,event));CinemaUiSounds.bindClick(this,v->{if(small){launch(this.entry);}else if(appIndex==selectedIndex&&appIndex>=0&&appIndex<carouselApps.size()&&carouselApps.get(appIndex).pkg.equals(this.entry.pkg))activateCarousel(this);});if(!small)setOnLongClickListener(v->{if(CinemaHomeActions.isAllApps(this.entry))return false;colorMenu(this.entry);return true;});
        }
        void updateArt(){if(!small)return;art.setImageDrawable(seasons.image(seasonIndex));}
        void rebind(AppAdapter.Entry next,int index){
            animate().cancel();entry=next;appIndex=index;focusLevel=0;setAlpha(1);setScaleX(1);setScaleY(1);setRotationY(0);setTranslationX(0);setTranslationY(0);setTranslationZ(0);
            setEnabled(true);setFocusable(true);setVisibility(View.VISIBLE);setContentDescription(next.label);name.setText(next.label);name.setTextColor(0xFFD8E0EB);CardAsset asset=assetFor(next);icon.setImageDrawable(asset.icon);premium=asset.surface;premium.focus(0);plate.setBackground(premium);
        }
        int accent(){CardAsset asset=cardAssets.get(entry.pkg);if(!small&&asset!=null)return asset.color;int color=options.color(entry.pkg);return color!=0?CinemaPalette.normalize(color):palette.color(entry.pkg,entry.icon!=null?entry.icon:entry.banner);}
        void size(){
            if(CinemaProbe.enabled&&!small)CinemaProbe.sizes++;
            setCameraDistance(CinemaCarousel.CAMERA_DISTANCE*unit);
            int w=Math.round((small?300:CinemaCarousel.CARD_WIDTH)*unit),h=Math.round((small?144:CinemaCarousel.CARD_HEIGHT)*unit);
            LinearLayout.LayoutParams outer=new LinearLayout.LayoutParams(w,Math.round((small?150:240)*unit));outer.setMarginEnd(Math.round(12*unit));setLayoutParams(outer);
            plate.setLayoutParams(new LinearLayout.LayoutParams(-1,h));if(small)art.setLayoutParams(new FrameLayout.LayoutParams(-1,-1));
            if(small){normalFace=new CinemaSurface(false,accent(),true,false);focusedFace=new CinemaSurface(true,accent(),true,false);plate.setBackground(isFocused()?focusedFace:normalFace);}else{CardAsset a=assetFor(entry);premium=a.surface;premium.focus(focusLevel);icon.setImageDrawable(a.icon);plate.setBackground(premium);}
            if(!small){setPivotY(h);setPivotX(w/2f);}
            FrameLayout.LayoutParams ip=new FrameLayout.LayoutParams(Math.round((small?44:128)*unit),Math.round((small?44:128)*unit),small?(Gravity.START|Gravity.BOTTOM):Gravity.CENTER);if(small){ip.setMarginStart(Math.round(20*unit));ip.bottomMargin=Math.round(18*unit);}icon.setLayoutParams(ip);
            if(small){FrameLayout.LayoutParams np=new FrameLayout.LayoutParams(-1,Math.round(30*unit));np.topMargin=Math.round(88*unit);np.setMarginStart(Math.round(78*unit));np.setMarginEnd(Math.round(20*unit));name.setLayoutParams(np);FrameLayout.LayoutParams mp=new FrameLayout.LayoutParams(-1,Math.round(20*unit));mp.topMargin=Math.round(112*unit);mp.setMarginStart(Math.round(82*unit));meta.setLayoutParams(mp);meta.setTextSize(TypedValue.COMPLEX_UNIT_PX,16*unit);}else{LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(-1,Math.round(38*unit));np.topMargin=Math.round(8*unit);name.setLayoutParams(np);name.setPadding(Math.round(6*unit),0,Math.round(6*unit),0);}name.setShadowLayer(1.5f*unit,0,unit,0xC0050A12);name.setTextSize(TypedValue.COMPLEX_UNIT_PX,(small?21:23)*unit);
        }
        void selected(boolean focused){
            if(updatingWindow)return;
            if(focused){
                if(!small){enterZone(FocusZone.APP_CAROUSEL);selectDock(appIndex,false);focusGlow(this);return;}
                enterZone(FocusZone.RECENT_ITEMS);
                returnZone="recent";returnKey=entry.pkg;
            }
            present(focused);
            if(focused){focusGlow(this);ensureVisible(this,recentScroll);}
        }
        void lighting(float level){
            if(Math.abs(focusLevel-level)<.001f)return;
            focusLevel=level;if(premium!=null)premium.focus(level);name.setTextColor(android.graphics.Color.rgb(Math.round(216+39*level),Math.round(224+31*level),Math.round(235+20*level)));
        }
        void present(boolean focused){
            if(!small){lighting(focusLevel);return;}
            animate().cancel();float scale=focused?1.05f:1;
            plate.setForeground(focused?quickRing:null);plate.setElevation((focused?9:5)*unit);
            name.setTextColor(focused?Color.WHITE:0xFFE2E8F1);
            if(normalFace!=null)plate.setBackground(focused?focusedFace:normalFace);
            invalidate();
            if(motion())animate().scaleX(scale).scaleY(scale).translationY(0).setDuration(duration()).setInterpolator(new DecelerateInterpolator()).start();
            else{setScaleX(!options.on("low_power")?scale:1);setScaleY(getScaleX());setTranslationY(0);}
        }
    }
    private void projectedBounds(Card c){
        c.projected.set(0,0,c.getWidth(),c.plate.getHeight());
        c.getMatrix().mapRect(c.projected);
        c.projected.offset(c.getLeft(),c.getTop());
    }
    private void arrangeDepth(Card focused){
        if(carouselAnimator!=null)carouselAnimator.cancel();
        zoneTransition=false;zoneStart=zoneStrength;zoneEnd=focusZone==FocusZone.APP_CAROUSEL?1:.25f;
        depthOrder.clear();
        for(Card c:dockCards)if(c.getVisibility()!=View.GONE&&c.getWidth()>0)depthOrder.add(c);
        // Layout coordinates, not transformed centers or fixed indices, define physical order.
        depthOrder.sort((a,b)->Integer.compare(a.getLeft(),b.getLeft()));
        int at=depthOrder.indexOf(focused);if(at<0)return;
        projectedBounds(focused);
        carouselAnchorStart=focused.projected.centerX();
        carouselAnchorEnd=focused.getLeft()+focused.getWidth()/2f;
        boolean depth=options.motion()&&!config.reducedMotion;
        for(int i=0;i<depthOrder.size();i++){
            Card c=depthOrder.get(i);c.animate().cancel();int distance=i-at;
            c.startFocus=c.focusLevel;c.targetFocus=c.appIndex==selectedIndex&&focusZone==FocusZone.APP_CAROUSEL?1:0;
            c.startScale=c.getScaleX();c.targetScale=options.on("low_power")||distance==0&&focusZone!=FocusZone.APP_CAROUSEL?1:CinemaCarousel.scale(distance);
            c.startRotation=c.getRotationY();c.targetRotation=depth?CinemaCarousel.rotation(distance):0;
            c.startDepth=c.getTranslationZ();c.targetDepth=depth?(distance==0?4:Math.abs(distance)==1?1:0)*unit:0;
        }
        animatedCard=focused;focusLight.select(focused.accent());
        startCarouselAnimation();
    }
    private void startCarouselAnimation(){
        if(!motion()||!CinemaLighting.animation(options)){if(zoneTransition)placeZone(1);else placeCarousel(animatedCard,1);queueReflection();return;}
        if(carouselAnimator==null){
            if(CinemaProbe.enabled)CinemaProbe.animators++;carouselAnimator=android.animation.ValueAnimator.ofFloat(0,1);
            carouselAnimator.setInterpolator(new android.view.animation.LinearInterpolator());
            carouselAnimator.addUpdateListener(a->{if(animatedCard!=null){if(zoneTransition)placeZone((float)a.getAnimatedValue());else placeCarousel(animatedCard,(float)a.getAnimatedValue());}});
            carouselAnimator.addListener(new android.animation.AnimatorListenerAdapter(){private boolean cancelled;@Override public void onAnimationStart(android.animation.Animator a){cancelled=false;}@Override public void onAnimationCancel(android.animation.Animator a){cancelled=true;}@Override public void onAnimationEnd(android.animation.Animator a){if(!cancelled)queueReflection();}});
        }
        carouselAnimator.setDuration(CinemaLighting.GLOW_TRANSITION_MS);
        carouselAnimator.start();
    }
    private void queueReflection(){scene.removeCallbacks(settledReflection);if(CinemaLighting.reflection(options))scene.postDelayed(settledReflection,CinemaLighting.REFLECTION_SETTLE_MS);}
    private void placeCarousel(Card focused,float fraction){
        float g=Math.min(1,fraction*CinemaLighting.GLOW_TRANSITION_MS/CinemaLighting.FOCUS_ANIMATION_MS);float geometry=1-(1-g)*(1-g);fraction=1-(1-fraction)*(1-fraction);
        zoneStrength=zoneStart+(zoneEnd-zoneStart)*fraction;focusLight.zone(zoneStrength);
        int at=depthOrder.indexOf(focused);if(at<0)return;
        for(Card c:depthOrder){
            c.lighting(c.startFocus+(c.targetFocus-c.startFocus)*fraction);
            float scale=c.startScale+(c.targetScale-c.startScale)*geometry;
            c.setScaleX(scale);c.setScaleY(scale);c.setTranslationY(0);
            c.setRotationY(c.startRotation+(c.targetRotation-c.startRotation)*geometry);
            c.setTranslationZ(c.startDepth+(c.targetDepth-c.startDepth)*geometry);
            c.frameTranslation=c.getTranslationX();projectedBounds(c);
        }
        // Matrix-mapped plate bounds include scale, perspective and the bottom pivot.
        // Pack every animation frame, so growing/shrinking neighbors never cross.
        float anchor=carouselAnchorStart+(carouselAnchorEnd-carouselAnchorStart)*fraction;
        shiftCard(focused,focused.frameTranslation+anchor-focused.projected.centerX());
        float gap=CinemaCarousel.GAP*unit;
        for(int i=at-1;i>=0;i--){Card c=depthOrder.get(i);shiftCard(c,c.frameTranslation+depthOrder.get(i+1).projected.left-gap-c.projected.right);}
        for(int i=at+1;i<depthOrder.size();i++){Card c=depthOrder.get(i);shiftCard(c,c.frameTranslation+depthOrder.get(i-1).projected.right+gap-c.projected.left);}
        // All mounted cards form one centered, bounded window. No offscreen siblings exist.
        if(!depthOrder.isEmpty()){
            float left=depthOrder.get(0).projected.left,right=depthOrder.get(depthOrder.size()-1).projected.right;
            float shift=(dockScroll.getWidth()-(right-left))/2-left;
            for(Card c:depthOrder)shiftCard(c,c.frameTranslation+shift);
        }
        focusLight.origin(dockScroll.getLeft());focusLight.beginNeighbors();
        for(Card c:depthOrder){c.setTranslationX(c.frameTranslation);focusLight.neighbor(c.projected,c.accent(),c==focused?1:c.focusLevel,Math.abs(depthOrder.indexOf(c)-at));}
        focusLight.position(focused.projected,fraction,unit,CinemaLighting.pool(options));
        dockScroll.scrollTo(0,0);
    }
    private void shiftCard(Card c,float delta){float change=delta-c.frameTranslation;c.frameTranslation=delta;c.projected.offset(change,0);}
    private void ensureVisible(Card card,HorizontalScrollView scroll){
        if(!card.small)return; // Seven-item window already fits the safe viewport.
        // Reveal the full scaled card only at a viewport edge. No re-centering each key press.
        int margin=Math.round(8*unit),left,right;
        if(card.small){left=card.getLeft()-Math.round(18*unit);right=card.getRight()+Math.round(18*unit);}
        else{projectedBounds(card);left=(int)Math.floor(card.projected.left)-margin;right=(int)Math.ceil(card.projected.right)+margin;}
        int x=scroll.getScrollX(),target=x;
        if(left<x)target=left;else if(right>x+scroll.getWidth())target=right-scroll.getWidth();
        target=Math.max(0,Math.min(target,Math.max(0,scroll.getChildAt(0).getWidth()-scroll.getWidth())));
        if(target!=x)scroll.scrollTo(target,0);
    }
    void updateDate(){
        java.util.Locale locale=activity.getResources().getConfiguration().getLocales().get(0);
        String pattern=locale.getLanguage().equals("zh")?"M月d日 EEE":android.text.format.DateFormat.getBestDateTimePattern(locale,"MMMdEEE");
        if(date!=null)date.setText(new java.text.SimpleDateFormat(pattern,locale).format(new java.util.Date()));
    }
    final class Scene extends FrameLayout implements CinemaUiSounds.Host {
        public CinemaUiSounds uiSounds(){return sounds;}
        @Override public boolean dispatchKeyEvent(KeyEvent event){userNavigation(event);sounds.key(event);return super.dispatchKeyEvent(event);}
        boolean restoringGeometry;
        private int lastCardWidth,lastRowWidth,lastSceneWidth,lastSceneHeight;
        Scene(Activity a){super(a);}
        @Override protected void onLayout(boolean changed,int l,int t,int r,int b){
            super.onLayout(changed,l,t,r,b);
            int cardWidth=dockCards.isEmpty()?0:dockCards.get(0).getWidth();
            boolean sizing=lastCardWidth!=cardWidth||lastRowWidth!=dock.getWidth()||lastSceneWidth!=getWidth()||lastSceneHeight!=getHeight();
            lastCardWidth=cardWidth;lastRowWidth=dock.getWidth();lastSceneWidth=getWidth();lastSceneHeight=getHeight();
            if(featuredCard!=null&&!closed&&(sizing||windowLayoutPending)){
                restoringGeometry=sizing||!windowAnimatePending;windowLayoutPending=false;windowAnimatePending=false;
                try{arrangeDepth(featuredCard);}finally{restoringGeometry=false;}
            }
        }
        @Override protected void onSizeChanged(int w,int h,int oldW,int oldH){super.onSizeChanged(w,h,oldW,oldH);layoutDesign();}
        void layoutDesign(){
            int w=getWidth(),h=getHeight();if(w<=0||h<=0)return;unit=Math.min(w/1920f,h/1080f);
            put(wallpaper,-w*.03f,-h*.03f,w*CinemaPolicy.OVERSCAN,h*CinemaPolicy.OVERSCAN);put(stage,0,h*.64f,w,h*.36f);
            put(time,84*unit,49*unit,590*unit,88*unit);put(date,87*unit,145*unit,590*unit,38*unit);time.setTextSize(TypedValue.COMPLEX_UNIT_PX,67*unit);date.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*unit);
            float topW=0;
            for(View v:topViews){boolean pill=false;int bw=Math.round((pill?150:68)*unit);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(bw,Math.round(68*unit));lp.setMarginStart(Math.round(16*unit));v.setLayoutParams(lp);v.setPadding(Math.round(12*unit),0,Math.round(12*unit),0);v.setBackground(new CinemaActionSurface(v.isFocused()));
                if(v instanceof TextView){TextView t=(TextView)v;t.setTextColor(0xFFE9EDF6);t.setTextSize(TypedValue.COMPLEX_UNIT_PX,20*unit);t.setGravity(Gravity.CENTER);if(v==settingsButton||v.getTag() instanceof Integer){int kind=v==settingsButton?CinemaIcon.SETTINGS:(Integer)v.getTag();CinemaIcon icon=new CinemaIcon(kind);icon.setBounds(0,0,Math.round(28*unit),Math.round(28*unit));t.setCompoundDrawables(null,null,null,null);t.setPadding(0,0,0,0);t.setIncludeFontPadding(false);t.setForeground(new CinemaButtonIcon(kind));t.getForeground().setAlpha(v.isFocused()?255:215);}}
                topW+=bw+16*unit;
            }
            put(top,w-84*unit-topW,63*unit,topW,70*unit);put(topLabel,w-84*unit-topW,147*unit,topW,30*unit);topLabel.setTextSize(TypedValue.COMPLEX_UNIT_PX,18*unit);
            prepareAssets();for(Card c:dockCards)c.size();for(Card c:recentCards)c.size();
            int pad=Math.round(48*unit);dock.setPadding(pad,Math.round(88*unit),pad,Math.round(26*unit));
            // Four apps stay centered; larger catalogues scroll without shrinking labels or clipping focus.
            int dockWidth=Math.min(Math.round(w-144*unit),Math.round((visibleCount*240+208)*unit));dockWidth=Math.max(Math.round(320*unit),dockWidth);
            put(dockScroll,(w-dockWidth)/2f,h*.423f,dockWidth,CinemaCarousel.VIEWPORT_HEIGHT*unit);put(focusLight,0,h*.423f,w,Math.min(530*unit,h-h*.423f));
            put(recentTitle,84*unit,h*.78f,w-168*unit,34*unit);recentTitle.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*unit);
            recent.setPadding(Math.round(12*unit),Math.round(12*unit),Math.round(24*unit),Math.round(8*unit));
            put(recentScroll,72*unit,h*.82f,w-144*unit,174*unit);put(recentHint,84*unit,h*.78f,w-168*unit,34*unit);recentHint.setTextSize(TypedValue.COMPLEX_UNIT_PX,18*unit);put(empty,84*unit,h*.84f,w-168*unit,48*unit);empty.setTextSize(TypedValue.COMPLEX_UNIT_PX,22*unit);
            refreshWallpaper();
        }
        private void put(View view,float x,float y,float w,float h){FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(w),Math.round(h));p.leftMargin=Math.round(x);p.topMargin=Math.round(y);view.setLayoutParams(p);}
    }
    private void toast(int id){Toast.makeText(activity,id,Toast.LENGTH_SHORT).show();}
    void startupStatus(String name,int seconds){
        if(startupNotice==null){startupNotice=new TextView(activity);textStyle(startupNotice,24,Color.WHITE,"sans-serif-medium");startupNotice.setGravity(Gravity.CENTER);startupNotice.setFocusable(false);startupNotice.setBackground(new CinemaSurface(false,0xFF7B9EC5,true,false));scene.addView(startupNotice);}
        startupNotice.setText(name+"　"+seconds+" 秒後開啟 · 按任意鍵取消");startupNotice.setVisibility(View.VISIBLE);
        FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(Math.round(1120*unit),Math.round(70*unit));p.leftMargin=Math.round((scene.getWidth()-1120*unit)/2);p.topMargin=Math.round(192*unit);startupNotice.setLayoutParams(p);
    }
    void clearStartupStatus(){if(startupNotice!=null)startupNotice.setVisibility(View.GONE);}
    void showStartup(){closePanel(false);overlayAnchor=settingsButton;startupPanel=new CinemaStartupPanel(activity,()->returnToSettings("startup:settings"));attachPanel(startupPanel);}
    private void returnToSettings(String tag){showSettings(tag,true);}
    void showOrder(){closePanel(false);overlayAnchor=settingsButton;orderPanel=new CinemaOrderPanel(activity,adapter,()->returnToSettings("order:settings"));attachPanel(orderPanel);}
    void showSettings(){showSettings(null,false);}
    private void showSettings(String anchor,boolean returning){
        closePanel(false);overlayAnchor=settingsButton;
        settingsPanel=new CinemaSettingsPanel(activity,options,this::applyEffects,key->{options.wallpaper(key);wallpapers.invalidate();scene.layoutDesign();},this::chooseLocalImage,
            ()->open(SystemSettings.intent(activity,Settings.ACTION_SETTINGS)),()->open(new Intent(activity,LauncherSettingsActivity.class)),()->closePanel(true));
        settingsPanel.personalization(()->showPersonalization());settingsPanel.startup(this::showStartup);settingsPanel.shortcutOrder(this::showOrder);settingsPanel.bootAssets(()->open(new Intent(activity,BootAppearanceActivity.class)));settingsPanel.adbTools(this::showAdbTools);attachPanel(settingsPanel);settingsPanel.restore(settingsState,anchor,returning);
    }
    void showAdbTools(){
        closePanel(false);overlayAnchor=settingsButton;
        adbPanel=new CinemaAdbPanel(activity,CinemaAdbAccess.get(activity),()->returnToSettings("adb:tools"));attachPanel(adbPanel);
    }
    void openLibrary(boolean search){openLibrary(search,libraryButton);}
    private void openLibrary(boolean search,View anchor){
        closePanel(false);overlayAnchor=anchor;
        libraryPanel=new CinemaAppPanel(activity,config,apps,search,adapter::containsPackage,this::launch,()->{if(activity instanceof MainActivity)((MainActivity)activity).refreshCinemaApps();},()->closePanel(true));attachPanel(libraryPanel);
    }
    private void attachPanel(View panel){enterZone(FocusZone.OVERLAY);overlay=panel;top.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);dock.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);recent.setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);scene.addView(panel,new FrameLayout.LayoutParams(-1,-1));if(panelMotion==null)panelMotion=new CinemaPanelMotion(scene,()->!closed&&motion());panelMotion.enter(panel);}
    void closePanel(boolean returnFocus){
        if(orderPanel!=null){orderPanel.closed=true;orderPanel=null;}
        if(startupPanel!=null){startupPanel.close();startupPanel=null;}
        if(adbPanel!=null){adbPanel.dispose();adbPanel=null;}
        if(personalization!=null){personalization.dispose();personalization=null;}
        CinemaAppPanel outgoingLibrary=libraryPanel;libraryPanel=null;
        if(outgoingLibrary!=null)outgoingLibrary.stop();
        if(settingsPanel!=null){settingsState=settingsPanel.state();settingsPanel.close();settingsPanel=null;}
        if(overlay!=null){View outgoing=overlay;overlay=null;Runnable release=()->{if(outgoingLibrary!=null)outgoingLibrary.close();};
            if(panelMotion!=null)panelMotion.retire(outgoing,release);else{scene.removeView(outgoing);release.run();}
        }else if(outgoingLibrary!=null)outgoingLibrary.close();
        top.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);dock.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);recent.setDescendantFocusability(ViewGroup.FOCUS_AFTER_DESCENDANTS);
        if(returnFocus){if(overlayAnchor!=null&&eligible(overlayAnchor))overlayAnchor.requestFocus();else restoreFocus(0);}overlayAnchor=null;
    }
    void showPersonalization(){
        closePanel(false);overlayAnchor=settingsButton;
        personalization=new CinemaPersonalizationPanel(activity,options,()->{applyEffects();imageStore.prune(options.imageReferences(apps));},this::chooseImage,()->returnToSettings("personalization"));
        attachPanel(personalization);
    }
    private void chooseLocalImage(){chooseImage("wallpaper:fixed");}
    void chooseImage(String target){
        if(target.equals("manage_images")){CinemaImageLibrary.open(activity,options,imageStore,apps);return;}
        if(!new CinemaImagePicker(activity,options.prefs).open(target)){
            if(personalization==null)showPersonalization();
            personalization.message("無法開啟圖片選擇器，或已有選取尚未完成；原圖保留。");
        }
    }
    void activityResult(int request,int result,Intent data){
        CinemaImagePicker.Result selected=new CinemaImagePicker(activity,options.prefs).receive(request,result,data);
        if(selected==null)return;
        if(selected.error!=null){if(personalization==null)showPersonalization();personalization.message(selected.error);return;}
        if(selected.uri==null)return;
        String target=selected.target;
        if(personalization==null)showPersonalization();personalization.message("正在驗證並建立圖片預覽…");
        final CinemaPersonalizationPanel waiting=personalization;final int revision=waiting.revision();
        imageStore.importImage(selected.uri,imported->{
            if(closed||personalization!=waiting||waiting.revision()!=revision){imageStore.discard(imported.reference);return;}
            personalization.preview(target,imported,()->{
                if(target.startsWith("recent:"))options.recentImage(Integer.parseInt(target.substring(7)),imported.reference);
                else {String[] parts=target.substring(10).split(":");if(parts.length==2)options.image(parts[0],parts[1],imported.reference);else options.image(parts[0],imported.reference);}
                imageStore.committed(imported.reference);applyEffects();imageStore.prune(options.imageReferences(apps));
            },()->imageStore.discard(imported.reference));
        },message->{if(personalization==waiting&&waiting.revision()==revision)personalization.message(message);});
    }
    private void colorMenu(AppAdapter.Entry entry){
        View anchor=activity.getCurrentFocus();closePanel(false);overlayAnchor=anchor;
        final int[] colors={0,0xFFED595D,0xFF55C8B8,0xFF8A9CF1,0xFFF3AF63};
        attachPanel(new CinemaChoicePanel(activity,"環境光色彩",entry.label,new String[]{"自動擷取 App 色彩","珊瑚紅","湖水綠","暮色藍","暖琥珀"},n->{options.color(entry.pkg,colors[n]);closePanel(true);View focus=activity.getCurrentFocus();if(focus instanceof Card){((Card)focus).size();((Card)focus).selected(true);}},()->closePanel(true)));
    }
    private void open(Intent i){try{activity.startActivity(i);}catch(android.content.ActivityNotFoundException|SecurityException e){toast(R.string.not_available);}}
    @Override public void close(){if(closed)return;closed=true;closePanel(false);pause();sounds.close();adapter.unregisterAdapterDataObserver(observer);wallpapers.close();seasons.close();imageStore.close();stage.image(null);palette.clear();cardAssets.clear();reflectionCache.clear();focusLight.clear();for(Card c:recentCards)c.art.setImageDrawable(null);dockCards.clear();recentCards.clear();depthOrder.clear();spareCards.clear();cardAssets.clear();reflectionCache.clear();focusLight.clear();}
}
