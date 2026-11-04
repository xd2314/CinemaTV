package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.SystemClock;
import android.view.*;
import java.util.function.LongSupplier;

/** Two preloaded local UI cues; no audio focus request, streaming, permission or system-volume writes. */
final class CinemaUiSounds implements AutoCloseable {
    static final int MOVE=0,SELECT=1;
    interface Host { CinemaUiSounds uiSounds(); }
    interface Player extends AutoCloseable { boolean ready(int cue);void play(int cue,float gain);void stop();void close(); }
    private final ViewGroup root;
    private final View windowRoot;
    private final boolean originalWindowSound;
    private final CinemaSettings settings;
    private final Player player;
    private final LongSupplier clock;
    private boolean active,closed,navigating;
    private int generation;
    private long lastMove=Long.MIN_VALUE,lastSelect=Long.MIN_VALUE;
    private final ViewTreeObserver.OnGlobalFocusChangeListener focus;
    CinemaUiSounds(ViewGroup root,CinemaSettings settings){this(root,settings,new NativePlayer(root.getContext()),SystemClock::elapsedRealtime);}
    CinemaUiSounds(ViewGroup root,CinemaSettings settings,Player player,LongSupplier clock){
        this.root=root;this.settings=settings;this.player=player;this.clock=clock;
        windowRoot=root.getRootView();originalWindowSound=windowRoot.isSoundEffectsEnabled();root.setSoundEffectsEnabled(false);
        focus=(oldView,newView)->{
            if(inside(newView))newView.setSoundEffectsEnabled(false);
            if(oldView!=newView&&inside(oldView)&&inside(newView))moved();
        };
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focus);
    }
    private boolean inside(View view){for(View v=view;v!=null;){if(v==root)return true;ViewParent p=v.getParent();v=p instanceof View?(View)p:null;}return false;}
    static void bindClick(View view,View.OnClickListener action){
        view.setOnClickListener(v->{
            if(v.isEnabled())for(View at=v;at!=null;){
                if(at instanceof Host){CinemaUiSounds sounds=((Host)at).uiSounds();if(sounds!=null)sounds.select();break;}
                ViewParent p=at.getParent();at=p instanceof View?(View)p:null;
            }
            action.onClick(v);
        });
        view.setSoundEffectsEnabled(false); // Avoid stacking Android's stock click with the custom cue.
    }
    void key(KeyEvent event){
        int key=event.getKeyCode();boolean direction=key>=KeyEvent.KEYCODE_DPAD_UP&&key<=KeyEvent.KEYCODE_DPAD_RIGHT;
        if(event.getAction()==KeyEvent.ACTION_DOWN&&direction){
            navigating=true;final int token=++generation;
            // Includes framework focus search after child dispatch, but never later programmatic restores.
            root.post(()->{if(token==generation)navigating=false;});
        }else{navigating=false;generation++;}
    }
    void moved(){
        if(!navigating||!available(MOVE))return;
        long now=clock.getAsLong();if(tooSoon(now,lastMove,140)||tooSoon(now,lastSelect,320))return;
        lastMove=now;player.play(MOVE,settings.uiSoundVolume()/100f*.65f);
    }
    void select(){
        navigating=false;generation++;
        if(!available(SELECT))return;
        long now=clock.getAsLong();if(tooSoon(now,lastSelect,260))return;
        lastSelect=now;player.play(SELECT,settings.uiSoundVolume()/100f*.85f);
    }
    private boolean available(int cue){return active&&!closed&&settings.uiSound()&&settings.uiSoundVolume()>0&&player.ready(cue);}
    private static boolean tooSoon(long now,long previous,long interval){return previous!=Long.MIN_VALUE&&now>=previous&&now-previous<interval;}
    void resume(){if(closed)return;active=true;windowRoot.setSoundEffectsEnabled(false);}
    void pause(){
        active=false;navigating=false;generation++;
        // A successful click can open an app immediately. Let its already-started,
        // bounded 320 ms chime finish; never delay app launch or start audio while paused.
        if(!tooSoon(clock.getAsLong(),lastSelect,320))player.stop();
        windowRoot.setSoundEffectsEnabled(originalWindowSound);
    }
    void settingsChanged(){if(!settings.uiSound()||settings.uiSoundVolume()==0)player.stop();}
    @Override public void close(){if(closed)return;pause();closed=true;ViewTreeObserver observer=root.getViewTreeObserver();if(observer.isAlive())observer.removeOnGlobalFocusChangeListener(focus);player.close();}

    // The box can silence the separate SYSTEM stream while media playback is audible.
    // Keep the current media volume and mute state; never write volume or request focus.
    static AudioAttributes audioAttributes(){
        return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
    }
    static final class NativePlayer implements Player {
        private SoundPool pool;
        private final int[] ids=new int[2];private final boolean[] loaded=new boolean[2];private int stream;
        NativePlayer(Context context){
            try{
                AudioAttributes attributes=audioAttributes();
                pool=new SoundPool.Builder().setMaxStreams(1).setAudioAttributes(attributes).build();
                pool.setOnLoadCompleteListener((p,id,status)->{
                    if(p!=pool)return;
                    for(int i=0;i<ids.length;i++)if(ids[i]==id)loaded[i]=status==0;
                    if(status!=0)android.util.Log.w("CinemaUiSounds","Cue load failed: status="+status);
                });
                ids[MOVE]=pool.load(context,R.raw.cinema_move,1);ids[SELECT]=pool.load(context,R.raw.cinema_select,1);
            }catch(RuntimeException unavailable){android.util.Log.w("CinemaUiSounds","Audio initialization unavailable",unavailable);close();} // Audio failure must never block navigation.
        }
        public boolean ready(int cue){return pool!=null&&loaded[cue];}
        public void play(int cue,float gain){if(!ready(cue))return;try{stop();stream=pool.play(ids[cue],gain,gain,1,0,1);}catch(RuntimeException ignored){close();}}
        public void stop(){if(pool!=null&&stream!=0){try{pool.stop(stream);}catch(RuntimeException ignored){}stream=0;}}
        public void close(){if(pool!=null){SoundPool old=pool;pool=null;try{old.release();}catch(RuntimeException ignored){}}stream=0;loaded[0]=loaded[1]=false;}
    }
}
