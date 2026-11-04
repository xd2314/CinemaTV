package com.gathin.tvhome;

import android.content.Intent;
import android.view.View;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaHomeEntryFocusTest {
    CinemaViewTest t;
    @Before public void setup(){t=new CinemaViewTest();t.create();}
    @After public void close(){t.close();}
    void settle(){t.layout(1920,1080);t.idle();t.layout(1920,1080);}
    void coldEmpty(){t.home.close();t.seed(0);t.home=new CinemaHome(t.activity,t.root,t.adapter,Config.load(t.activity),t.library,t.inputs,t.settings);t.home.resume();settle();t.home.restoreFocus(0);}
    void assertFirst(){assertEquals(0,t.home.selectedIndex);assertEquals(0,t.home.firstVisibleIndex);assertEquals(0,((Integer)ReflectionHelpers.getField(t.root.findFocus(),"appIndex")).intValue());List<AppAdapter.Entry> apps=ReflectionHelpers.getField(t.home,"carouselApps");assertFalse(CinemaHomeActions.isAllApps(apps.get(0)));}
    @Test public void delayedInitialCatalogueDoesNotKeepSyntheticAllApps(){coldEmpty();assertEquals(CinemaHomeActions.ALL_APPS,ReflectionHelpers.getField(t.home,"dockKey"));t.seed(22);settle();assertFirst();assertEquals(CinemaViewTest.PKGS[0],ReflectionHelpers.getField(t.home,"dockKey"));}
    @Test public void repeatedEmptyLoadsStillStartAtFirstRealApp(){coldEmpty();t.seed(0);t.home.homePressed();settle();t.seed(0);t.seed(4);settle();assertFirst();}
    @Test public void readyCatalogueColdEntryUsesFirstApp(){t.home.restoreFocus(0);settle();assertFirst();}
    @Test public void queuedHomeFocusAfterInactiveTrimRebuildsCardAssetsBeforeReuse(){
        t.seed(22);settle();t.go(22);t.home.pause();t.home.trim();
        t.home.homePressed();t.home.resume();settle();assertFirst();
    }
    @Test public void explicitHomeReturnsFromTailAndClosesLibrary()throws Exception{t.seed(22);settle();t.go(22);t.root.findFocus().performClick();assertNotNull(t.home.libraryPanel);t.home.homePressed();settle();assertNull(t.home.libraryPanel);assertFirst();for(int width:new int[]{1920,1280})t.capture(width,width*9/16,"v49-home-first-"+width+".png");}
    @Test public void entryUsesNewlySavedFirstShortcut(){List<String> keys=new ArrayList<>();for(AppAdapter.Entry e:t.adapter.cinemaEntries())keys.add(e.component.flattenToShortString());Collections.reverse(keys);assertTrue(t.adapter.saveCinemaOrder(keys));settle();t.go(4);t.home.homePressed();settle();assertFirst();assertEquals(CinemaViewTest.PKGS[3],ReflectionHelpers.getField(t.home,"dockKey"));}
    @Test public void normalRefreshAndZoneReturnKeepDeliberateSelection(){t.go(4);t.adapter.notifyDataSetChanged();settle();t.home.restoreFocus(0);assertEquals(4,t.home.selectedIndex);t.go(2);View app=t.root.findFocus();t.key(app,19);t.key(t.root.findFocus(),20);assertEquals(2,t.home.selectedIndex);t.key(t.root.findFocus(),20);t.key(t.root.findFocus(),19);assertEquals(2,t.home.selectedIndex);t.home.pause();t.home.resume();settle();assertEquals(2,t.home.selectedIndex);}
    @Test public void rightAfterHomeIsNotOverwrittenByQueuedFocusRestore(){t.go(4);t.home.homePressed();t.layout(1920,1080);t.key(t.root.findFocus(),22);settle();assertEquals(1,t.home.selectedIndex);}
    @Test public void noAppsStillHasUsableAllAppsEntry(){coldEmpty();t.home.homePressed();settle();assertEquals(0,t.home.selectedIndex);assertEquals(1,t.home.dock.getChildCount());t.root.findFocus().performClick();assertNotNull(t.home.libraryPanel);}
    @Test public void explicitHomeUsesFirstLogicalAppInRtl(){t.seed(22);t.home.scene.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);settle();t.go(22);t.home.homePressed();settle();assertFirst();}
    @Test public void pendingInitialCatalogueDoesNotCloseSettings(){coldEmpty();t.home.showSettings();t.seed(4);settle();assertNotNull(t.home.settingsPanel);assertEquals(0,t.home.selectedIndex);t.home.closePanel(false);t.home.restoreFocus(0);assertFirst();}
    @Test public void actualActivityHomeAndLauncherIntentsResetTail(){
        ActivityController<MainActivity> c=Robolectric.buildActivity(MainActivity.class,new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)).setup();
        try{
            MainActivity a=c.get();CinemaHome h=ReflectionHelpers.getField(a,"cinemaHome");AppAdapter adapter=ReflectionHelpers.getField(a,"adapter");List<AppAdapter.Entry> items=ReflectionHelpers.getField(adapter,"items");items.clear();items.addAll(t.adapter.cinemaEntries());adapter.notifyDataSetChanged();
            View root=a.getWindow().getDecorView();root.measure(View.MeasureSpec.makeMeasureSpec(1920,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(1080,View.MeasureSpec.EXACTLY));root.layout(0,0,1920,1080);t.idle();
            for(String category:new String[]{Intent.CATEGORY_HOME,Intent.CATEGORY_LEANBACK_LAUNCHER,Intent.CATEGORY_LAUNCHER}){
                ReflectionHelpers.callInstanceMethod(h,"selectDock",ReflectionHelpers.ClassParameter.from(int.class,4),ReflectionHelpers.ClassParameter.from(boolean.class,true));assertEquals(4,h.selectedIndex);
                c.newIntent(new Intent(Intent.ACTION_MAIN).addCategory(category));t.idle();assertEquals(0,h.selectedIndex);assertEquals(0,h.firstVisibleIndex);
            }
        }finally{c.pause().stop().destroy();}
    }
}
