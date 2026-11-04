package com.gathin.tvhome;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowSoundPool;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

/** Exercises the real NativePlayer adapter with simulated platform load callbacks, not real speakers. */
@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28)
public class CinemaNativeAudioTest {
    CinemaUiSounds.NativePlayer player;
    SoundPool pool;
    ShadowSoundPool shadow;
    @Before public void setup(){
        player=new CinemaUiSounds.NativePlayer(RuntimeEnvironment.getApplication());
        pool=ReflectionHelpers.getField(player,"pool");assertNotNull(pool);shadow=Shadows.shadowOf(pool);
    }
    @After public void close(){player.close();}
    @Test public void usesMediaVolumeWithSonificationContent(){
        AudioAttributes attributes=CinemaUiSounds.audioAttributes();
        assertEquals(AudioAttributes.USAGE_MEDIA,attributes.getUsage());
        assertEquals(AudioAttributes.CONTENT_TYPE_SONIFICATION,attributes.getContentType());
        assertEquals(AudioManager.STREAM_MUSIC,attributes.getVolumeControlStream());
    }
    @Test public void bothActualRawResourceIdsLoadAndPlayWithoutLoop(){
        assertFalse(player.ready(CinemaUiSounds.MOVE));assertFalse(player.ready(CinemaUiSounds.SELECT));
        shadow.notifyResourceLoaded(R.raw.cinema_move,true);shadow.notifyResourceLoaded(R.raw.cinema_select,true);
        assertTrue(player.ready(CinemaUiSounds.MOVE));assertTrue(player.ready(CinemaUiSounds.SELECT));
        player.play(CinemaUiSounds.MOVE,.65f);player.play(CinemaUiSounds.SELECT,.85f);
        assertTrue(shadow.wasResourcePlayed(R.raw.cinema_move));assertTrue(shadow.wasResourcePlayed(R.raw.cinema_select));
        assertEquals(1,shadow.getResourcePlaybacks(R.raw.cinema_move).size());assertEquals(1,shadow.getResourcePlaybacks(R.raw.cinema_select).size());
    }
    @Test public void failedLoadNeverPlaysOrReplaysOldEvents(){
        shadow.notifyResourceLoaded(R.raw.cinema_move,false);assertFalse(player.ready(CinemaUiSounds.MOVE));player.play(CinemaUiSounds.MOVE,.65f);assertFalse(shadow.wasResourcePlayed(R.raw.cinema_move));
        shadow.notifyResourceLoaded(R.raw.cinema_move,true);assertTrue(player.ready(CinemaUiSounds.MOVE));assertFalse(shadow.wasResourcePlayed(R.raw.cinema_move));
    }
    @Test public void releaseRejectsLateCallbacksAndPlayback(){
        player.close();shadow.notifyResourceLoaded(R.raw.cinema_move,true);assertFalse(player.ready(CinemaUiSounds.MOVE));player.play(CinemaUiSounds.MOVE,1);assertFalse(shadow.wasResourcePlayed(R.raw.cinema_move));player.close();
    }
    @Test public void mediaAndSystemVolumeAreNeverRaisedOrUnmuted(){
        AudioManager audio=(AudioManager)RuntimeEnvironment.getApplication().getSystemService(Context.AUDIO_SERVICE);
        audio.setStreamVolume(AudioManager.STREAM_SYSTEM,0,0);audio.setStreamVolume(AudioManager.STREAM_MUSIC,0,0);
        shadow.notifyResourceLoaded(R.raw.cinema_select,true);player.play(CinemaUiSounds.SELECT,.85f);player.stop();player.close();
        assertEquals(0,audio.getStreamVolume(AudioManager.STREAM_SYSTEM));assertEquals(0,audio.getStreamVolume(AudioManager.STREAM_MUSIC));
    }
}
