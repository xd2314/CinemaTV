package com.gathin.tvhome;

import android.view.View;
import android.view.ViewGroup;
import java.util.function.BooleanSupplier;

/** Interruptible native alpha only. One retiring view at most, never an input/DPAD queue. */
final class CinemaPanelMotion {
    private final ViewGroup host;private final BooleanSupplier enabled;
    private View incoming,retiring;private Runnable cleanup;
    private final Runnable expire=this::finishRetiring;
    CinemaPanelMotion(ViewGroup host,BooleanSupplier enabled){this.host=host;this.enabled=enabled;}
    void enter(View view){
        finishIncoming();incoming=view;
        if(!enabled.getAsBoolean()){view.setAlpha(1);return;}
        view.setAlpha(.85f);view.animate().alpha(1).setDuration(140).start();
    }
    void retire(View view,Runnable release){
        finishRetiring();if(incoming==view)finishIncoming();
        if(!enabled.getAsBoolean()||view.getWidth()==0||view.getHeight()==0){host.removeView(view);release.run();return;}
        if(view instanceof ViewGroup)((ViewGroup)view).setDescendantFocusability(ViewGroup.FOCUS_BLOCK_DESCENDANTS);
        view.clearFocus();view.setFocusable(false);host.removeView(view);
        retiring=view;cleanup=release;host.getOverlay().add(view);
        view.animate().alpha(0).setDuration(110).withEndAction(this::finishRetiring).start();
        host.postDelayed(expire,150); // Bounded cleanup even when animation callbacks are cancelled.
    }
    void finishRetiring(){
        View old=retiring;Runnable release=cleanup;retiring=null;cleanup=null;host.removeCallbacks(expire);
        if(old!=null){old.animate().withEndAction(null).cancel();host.getOverlay().remove(old);}
        if(release!=null)release.run();
    }
    private void finishIncoming(){if(incoming!=null){incoming.animate().withEndAction(null).cancel();incoming.setAlpha(1);incoming=null;}}
    void finish(){finishIncoming();finishRetiring();}
    boolean hasRetiring(){return retiring!=null;}
}
