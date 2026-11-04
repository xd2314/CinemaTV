package com.gathin.tvhome;

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.view.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.util.ReflectionHelpers;
import java.util.*;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaOrderPanelTest {
    CinemaViewTest t;
    @Before public void setup(){t=new CinemaViewTest();t.create();Order.file(t.activity).delete();}
    @After public void close(){t.close();}
    boolean key(int k){return t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,k));}
    void back(){t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,4));}
    CinemaOrderPanel panel(){t.home.showOrder();t.layout(1920,1080);t.idle();return t.home.orderPanel;}
    List<String> keys(){List<String> out=new ArrayList<>();for(AppAdapter.Entry e:t.adapter.cinemaEntries())out.add(e.component.flattenToShortString());return out;}
    @Test public void settingsEntryIsVisibleNearFront(){t.home.showSettings();t.layout(1920,1080);assertEquals("order:settings",t.home.settingsPanel.rows.getChildAt(2).getTag());t.home.settingsPanel.rows.getChildAt(2).performClick();assertNotNull(t.home.orderPanel);}
    @Test public void movingDraftDoesNotLaunchOrChangeHomeUntilSave(){List<String> before=keys();List<String> recent=LastOpened.recentPackages(t.activity,4);CinemaOrderPanel p=panel();View carried=p.selected;carried.performClick();key(20);key(20);assertEquals(before,keys());assertEquals(before.get(0),p.keys().get(2));assertTrue(p.carrying);assertSame(carried,t.root.findFocus());carried.performClick();p.save.performClick();assertEquals(before.get(0),keys().get(2));assertEquals(recent,LastOpened.recentPackages(t.activity,4));assertNull(Shadows.shadowOf(t.activity).getNextStartedActivity());assertEquals(keys(),Order.load(Order.file(t.activity)).keys);List<AppAdapter.Entry> homeApps=ReflectionHelpers.getField(t.home,"carouselApps");assertEquals(keys().get(0).split("/")[0],homeApps.get(0).pkg);assertTrue(CinemaHomeActions.isAllApps(homeApps.get(homeApps.size()-1)));}
    @Test public void backDropsThenProtectsDraftAndDiscardKeepsOriginal(){List<String> before=keys();CinemaOrderPanel p=panel();p.selected.performClick();key(20);back();assertFalse(p.carrying);assertFalse(p.confirmingExit);back();assertTrue(p.confirmingExit);p.discard.performClick();assertEquals(before,keys());assertNull(Order.load(Order.file(t.activity)));}
    @Test public void saveSurvivesActualAdapterReload(){CinemaOrderPanel p=panel();p.move(1);List<String> wanted=p.keys();p.save.performClick();Order saved=Order.load(Order.file(t.activity));Config cfg=Config.load(t.activity);t.adapter.setSavedOrder(Order.ranks(saved,cfg.dockApps),Order.docked(saved,cfg.dockApps));List<ResolveInfo> apps=new ArrayList<>();for(String pkg:CinemaViewTest.PKGS){ResolveInfo ri=CinemaStartupTest.resolve(t.activity,pkg);ri.activityInfo.name=".Main";apps.add(ri);}t.adapter.reload(Collections.emptyList(),apps);assertEquals(wanted,keys());}
    @Test public void invalidOrStalePermutationCannotOverwriteSavedOrder(){List<String> original=keys();assertTrue(t.adapter.saveCinemaOrder(original));List<String> bad=new ArrayList<>(original);bad.set(1,bad.get(0));assertFalse(t.adapter.saveCinemaOrder(bad));bad=new ArrayList<>(original);bad.remove(0);assertFalse(t.adapter.saveCinemaOrder(bad));bad.add("missing/.App");assertFalse(t.adapter.saveCinemaOrder(bad));assertEquals(original,Order.load(Order.file(t.activity)).keys);assertEquals(original,keys());}
    @Test public void writeFailureLeavesLiveOrderUntouched()throws Exception{List<String> before=keys();java.io.File blocked=new java.io.File(t.activity.getFilesDir(),"not-a-directory");java.nio.file.Files.write(blocked.toPath(),new byte[]{1});ReflectionHelpers.setField(t.adapter,"ctx",new android.content.ContextWrapper(t.activity){@Override public java.io.File getFilesDir(){return blocked;}});CinemaOrderPanel p=panel();p.move(1);p.save.performClick();assertSame(p,t.home.orderPanel);assertEquals(before,keys());assertTrue(p.status.getText().toString().contains("未能儲存"));}
    @Test public void boundariesLongListAndRtlStayReachable()throws Exception{t.seed(22);CinemaOrderPanel p=panel();p.selected.performClick();key(19);assertEquals(0,p.list.indexOfChild(p.selected));for(int i=0;i<30;i++)key(20);assertEquals(21,p.list.indexOfChild(p.selected));for(int width:new int[]{1920,1280}){t.layout(width,width*9/16);t.capture(width,width*9/16,"v48-order-moving-"+width+".png");}p.selected.performClick();p.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);t.layout(1280,720);key(21);assertSame(p.moves.getChildAt(0),t.root.findFocus());key(21);assertSame(p.moves.getChildAt(1),t.root.findFocus());key(20);assertSame(p.save,t.root.findFocus());}
    @Test public void emptyListHasWorkingFinishAndNoTrap(){t.seed(0);CinemaOrderPanel p=panel();assertSame(p.save,t.root.findFocus());assertFalse(p.dirty());key(19);assertSame(p.save,t.root.findFocus());p.save.performClick();assertNull(t.home.orderPanel);}
    @Test public void physicalKeyRoutePicksMovesAndDropsWithoutLaunch(){CinemaOrderPanel p=panel();View row=p.selected;key(23);t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,23));assertTrue(p.carrying);key(20);assertSame(row,t.root.findFocus());key(23);t.root.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,23));assertFalse(p.carrying);assertNull(Shadows.shadowOf(t.activity).getNextStartedActivity());}
    @Test public void removingAppsCannotPersistAnInvalidDockCount(){t.adapter.setSavedOrder(Collections.emptyMap(),7);List<String> wanted=keys();Collections.reverse(wanted);assertTrue(t.adapter.saveCinemaOrder(wanted));Order saved=Order.load(Order.file(t.activity));assertNotNull(saved);assertEquals(wanted,saved.keys);assertEquals(wanted.size(),saved.docked);}
    @Test public void nonAppEntriesStayInPlace(){List<AppAdapter.Entry> items=ReflectionHelpers.getField(t.adapter,"items");AppAdapter.Entry extra=new AppAdapter.Entry(AppAdapter.Entry.CARD,"fixture card",new ComponentName("fixture.card",".Main"),null,null,"fixture",0);items.add(1,extra);List<String> reordered=keys();Collections.reverse(reordered);assertTrue(t.adapter.saveCinemaOrder(reordered));assertSame(extra,items.get(1));assertTrue(Order.load(Order.file(t.activity)).keys.contains("fixture.card/.Main"));}
}
