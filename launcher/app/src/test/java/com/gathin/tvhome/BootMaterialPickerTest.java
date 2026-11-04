package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootMaterialPickerTest {
    Intent chosen(Intent chooser){return chooser.getParcelableExtra(Intent.EXTRA_INTENT);}
    @Test public void availableSystemPickerLaunchesOnlyOnce() {
        List<Intent> calls=new ArrayList<>();
        BootMaterialPicker picker=new BootMaterialPicker((i,r)->{assertEquals(7101,r);calls.add(i);});
        assertEquals(BootMaterialPicker.Opened.DOCUMENT,picker.open(BootAssetStore.Target.LOGO,false));
        assertEquals(1,calls.size());assertEquals(Intent.ACTION_OPEN_DOCUMENT,calls.get(0).getAction());
    }
    @Test public void missingSystemPickerUsesChooserAndDistinctResultCode() {
        List<Intent> calls=new ArrayList<>();
        BootMaterialPicker picker=new BootMaterialPicker((i,r)->{
            assertEquals(Intent.ACTION_OPEN_DOCUMENT.equals(i.getAction())?7101:7103,r);calls.add(i);
            if(Intent.ACTION_OPEN_DOCUMENT.equals(i.getAction()))throw new ActivityNotFoundException();
        });
        assertEquals(BootMaterialPicker.Opened.CONTENT,picker.open(BootAssetStore.Target.VIDEO,false));
        assertEquals(2,calls.size());assertEquals(Intent.ACTION_CHOOSER,calls.get(1).getAction());
        assertEquals(Intent.ACTION_GET_CONTENT,chosen(calls.get(1)).getAction());
        assertEquals("video/mp4",chosen(calls.get(1)).getType());
    }
    @Test public void blockedSystemPickerAlsoFallsBack() {
        List<Intent> calls=new ArrayList<>();
        BootMaterialPicker picker=new BootMaterialPicker((i,r)->{calls.add(i);if(calls.size()==1)throw new SecurityException();});
        assertEquals(BootMaterialPicker.Opened.CONTENT,picker.open(BootAssetStore.Target.WALLPAPER,false));
        assertEquals(2,calls.size());assertEquals("image/*",chosen(calls.get(1)).getType());
    }
    @Test public void noHandlersStopsAfterTwoAttempts() {
        List<Intent> calls=new ArrayList<>();
        BootMaterialPicker picker=new BootMaterialPicker((i,r)->{calls.add(i);throw new ActivityNotFoundException();});
        assertEquals(BootMaterialPicker.Opened.NONE,picker.open(BootAssetStore.Target.LOGO,false));
        assertEquals(2,calls.size());
    }
    @Test public void explicitOtherAppDoesNotLaunchSystemPicker() {
        List<Intent> calls=new ArrayList<>();
        BootMaterialPicker picker=new BootMaterialPicker((i,r)->calls.add(i));
        assertEquals(BootMaterialPicker.Opened.CONTENT,picker.open(BootAssetStore.Target.LOGO,true));
        assertEquals(1,calls.size());assertEquals(Intent.ACTION_CHOOSER,calls.get(0).getAction());assertEquals(Intent.ACTION_GET_CONTENT,chosen(calls.get(0)).getAction());
    }
    @Test public void allTargetsAndBothRoutesRemainLocalReadOnlyOpenableImports() {
        for(BootAssetStore.Target target:BootAssetStore.Target.values())for(String action:new String[]{Intent.ACTION_OPEN_DOCUMENT,Intent.ACTION_GET_CONTENT}) {
            Intent i=BootMaterialPicker.intent(target,action);
            assertTrue(i.hasCategory(Intent.CATEGORY_OPENABLE));assertTrue(i.getBooleanExtra(Intent.EXTRA_LOCAL_ONLY,false));
            assertEquals(Intent.FLAG_GRANT_READ_URI_PERMISSION,i.getFlags());assertNull(i.getComponent());
            if(target==BootAssetStore.Target.VIDEO){assertEquals("video/mp4",i.getType());assertNull(i.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));}
            else {assertEquals("image/*",i.getType());assertArrayEquals(new String[]{"image/png","image/jpeg"},i.getStringArrayExtra(Intent.EXTRA_MIME_TYPES));}
        }
    }
}
