package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import java.io.*;
import java.nio.file.Files;
import java.util.*;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BootWallpaperTransactionsTest {
    static class Memory implements BootWallpaperTransactions.Store{String value="";boolean fail;public String read(){return value;}public boolean write(String s){if(fail)return false;value=s;return true;}}
    Activity app;File root;BootAssetStore store;BootAssetStore.Asset asset;Memory memory;BootVideoTransactionsTest.Queue queue;List<Runnable> deadlines;Fake backend;BootWallpaperTransactions model;
    byte[] picture(int color)throws Exception{Bitmap b=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);b.eraseColor(color);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();}
    final class Fake implements BootWallpaperTransactions.Backend{
        byte[] system,lock;int id=4,lockId=3,captures,sets;String lockSha,originalLockSha;Runnable beforeSet=()->{};boolean externalDuringPrecheck,badReadback,throwAfter,denyRead,changeLock,wrongMigrationId,defaultImage;int sharedSets;
        public BootWallpaperTransactions.Snapshot capture(File output)throws Exception{
            if(denyRead)throw new SecurityException("No wallpaper read permission");captures++;
            if(externalDuringPrecheck&&captures==2)id++;
            Files.write(output.toPath(),system);
            if(output.getName().equals("before.image"))Files.write(new File(output.getParentFile(),"before-lock.image").toPath(),lock);
            return new BootWallpaperTransactions.Snapshot(id,lockId,BootAssetStore.hash(output),lockSha,defaultImage);
        }
        public int set(File file,BootWallpaperTransactions.Snapshot expected)throws Exception{return set(file,expected,false);}
        public int set(File file,BootWallpaperTransactions.Snapshot expected,boolean sharedDefault)throws Exception{
            assertTrue(memory.value.contains("PENDING"));assertTrue(memory.value.contains("\"writeAttempted\":true"));
            assertEquals(id,expected.systemId);assertEquals(lockId,expected.lockId);beforeSet.run();sets++;if(sharedDefault){assertEquals(-1,lockId);sharedSets++;}if(lockId==-1&&!sharedDefault){lockId=id+(wrongMigrationId?1:0);lock=system.clone();lockSha=BootLogoLayout.sha(lock);}system=Files.readAllBytes(file.toPath());id++;defaultImage=false;if(sharedDefault){lock=system.clone();lockSha=BootLogoLayout.sha(lock);}
            if(badReadback)system=picture(0xff123456);if(changeLock){lock=picture(0xff5555ff);lockSha=BootLogoLayout.sha(lock);}if(throwAfter)throw new IOException("After dispatch");return id;
        }
    }
    @Before public void setup()throws Exception{
        app=Robolectric.buildActivity(Activity.class).setup().get();root=new File(app.getFilesDir(),"boot_wallpaper_transactions");BootAssetsTest.clear(root);BootAssetsTest.clear(new File(app.getFilesDir(),"boot_asset_preparation"));
        store=new BootAssetStore(app);byte[] bytes=picture(0xff3366aa);asset=store.prepare(BootAssetStore.Target.WALLPAPER,false,()->false,()->new ByteArrayInputStream(bytes));store.commit(asset);
        memory=new Memory();queue=new BootVideoTransactionsTest.Queue();deadlines=new ArrayList<>();backend=new Fake();backend.system=picture(0xff224422);backend.lock=picture(0xff555555);backend.lockSha=BootLogoLayout.sha(backend.lock);backend.originalLockSha=backend.lockSha;model=newModel();
    }
    BootWallpaperTransactions newModel(){return new BootWallpaperTransactions(root,backend,memory,queue,Runnable::run,(ms,r)->deadlines.add(r));}
    @After public void close(){BootAssetsTest.clear(root);BootAssetsTest.clear(new File(app.getFilesDir(),"boot_asset_preparation"));app.finish();}
    org.json.JSONObject receipt()throws Exception{return new org.json.JSONObject(memory.value);}
    @Test public void passiveOpenDoesNotReadWallpaperOrRequestAnyChange(){assertEquals(0,backend.captures);assertEquals(0,backend.sets);assertTrue(memory.value.isEmpty());assertTrue(model.canApply());assertFalse(model.canRestore());}
    @Test public void oneDispatchBacksUpThenAppliesAndExactRestorationSurvivesRecreation()throws Exception{
        byte[] original=backend.system.clone();assertTrue(model.apply(asset));assertFalse(model.apply(asset));assertEquals(1,queue.pending.size());assertEquals(0,backend.sets);queue.run();
        assertEquals("APPLIED",receipt().getString("state"));assertEquals(asset.sha,receipt().getString("afterSha"));assertTrue(model.canRestore());
        File before=new File(new File(root,receipt().getString("transaction")),"before.image");assertArrayEquals(original,Files.readAllBytes(before.toPath()));
        model=newModel();assertTrue(model.canRestore());assertTrue(model.restore());assertFalse(model.restore());queue.run();assertEquals(2,backend.sets);assertArrayEquals(original,backend.system);assertEquals(3,backend.lockId);assertEquals(backend.originalLockSha,backend.lockSha);assertEquals("RESTORED",receipt().getString("state"));assertFalse(model.canRestore());
    }
    @Test public void permissionFailureKeepsWallpaperAndDoesNotCallSetter()throws Exception{backend.denyRead=true;model.apply(asset);queue.run();assertEquals(0,backend.sets);assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));assertFalse(model.canApply());}
    @Test public void externalChangeDuringBackupPreventsApply()throws Exception{backend.externalDuringPrecheck=true;model.apply(asset);queue.run();assertEquals(0,backend.sets);assertFalse(model.canApply());assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));}
    @Test public void unexpectedReadbackNeverPassesOrAutomaticallyRollsBack()throws Exception{backend.badReadback=true;model.apply(asset);queue.run();assertEquals(1,backend.sets);assertEquals("UNKNOWN",receipt().getString("state"));assertFalse(model.canRestore());assertTrue(queue.pending.isEmpty());}
    @Test public void lockWallpaperChangeIsFailure()throws Exception{backend.changeLock=true;model.apply(asset);queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertEquals(1,backend.sets);}
    @Test public void setterExceptionAfterDispatchLocksWithoutRetry()throws Exception{backend.throwAfter=true;model.apply(asset);queue.run();assertEquals("UNKNOWN",receipt().getString("state"));assertEquals(1,backend.sets);assertFalse(model.apply(asset));}
    @Test public void timeoutBeforeWorkerPreventsDispatch()throws Exception{model.apply(asset);deadlines.get(0).run();queue.run();assertEquals(0,backend.sets);assertEquals("UNKNOWN",receipt().getString("state"));assertTrue(receipt().getBoolean("timeout"));}
    @Test public void lateSuccessAfterTimeoutCannotUnlockOrBecomePass()throws Exception{backend.beforeSet=()->deadlines.get(0).run();model.apply(asset);queue.run();assertEquals(1,backend.sets);assertEquals("UNKNOWN",receipt().getString("state"));assertFalse(model.canApply());assertFalse(model.canRestore());}
    @Test public void changedCurrentIdOrImagePreventsRestore()throws Exception{model.apply(asset);queue.run();backend.id++;assertTrue(model.restore());queue.run();assertEquals(1,backend.sets);assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));}
    @Test public void corruptedOriginalBackupPreventsRestore()throws Exception{model.apply(asset);queue.run();File before=new File(new File(root,receipt().getString("transaction")),"before.image");Files.write(before.toPath(),new byte[]{1,2});assertTrue(model.restore());queue.run();assertEquals(1,backend.sets);assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));}
    @Test public void failedPendingReceiptPreventsWorker(){memory.fail=true;assertFalse(model.apply(asset));assertEquals(0,backend.sets);assertTrue(queue.pending.isEmpty());}
    @Test public void persistedPendingAndMalformedReceiptsNeverResume(){memory.value="{\"schema\":1,\"state\":\"PENDING\"}";assertFalse(newModel().canApply());memory.value="broken";assertFalse(newModel().canApply());assertEquals(0,backend.sets);}
    @Test public void incorrectTargetAndForgedSourceAreRejected()throws Exception{
        BootAssetStore.Asset wrong=new BootAssetStore.Asset(asset.id,BootAssetStore.Target.LOGO,false,asset.source.getParentFile(),asset.sha,asset.sourceSha,asset.posterSha,"");assertFalse(model.apply(wrong));
        File foreign=Files.createTempDirectory("foreign-wallpaper").toFile();Files.copy(asset.output.toPath(),new File(foreign,"prepared.png").toPath());BootAssetStore.Asset forged=new BootAssetStore.Asset(asset.id,BootAssetStore.Target.WALLPAPER,false,foreign,asset.sha,asset.sourceSha,asset.posterSha,"");assertTrue(model.apply(forged));queue.run();assertEquals(0,backend.sets);assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));BootAssetsTest.clear(foreign);
    }
    @Test public void explicitReadOnlyAuditAfterRecreationDoesNotCallSetter()throws Exception{model.apply(asset);queue.run();model=newModel();assertTrue(model.audit());queue.run();assertEquals(1,backend.sets);assertTrue(receipt().getJSONObject("lastAudit").getBoolean("passed"));assertTrue(model.canRestore());model.restore();queue.run();model=newModel();assertTrue(model.audit());queue.run();assertEquals(2,backend.sets);assertEquals("RESTORED",receipt().getString("state"));assertTrue(receipt().getJSONObject("lastAudit").getBoolean("passed"));}
    @Test public void readOnlyAuditDetectsExternalImageAndLocksWithoutSetting()throws Exception{model.apply(asset);queue.run();backend.id++;model.audit();queue.run();assertEquals(1,backend.sets);assertFalse(receipt().getJSONObject("lastAudit").getBoolean("passed"));assertFalse(model.canRestore());assertFalse(newModel().canRestore());}
    @Test public void exportedZipContainsExactOriginalSystemAndLockImagesAndReceipt()throws Exception{
        byte[] system=backend.system.clone(),lock=backend.lock.clone();model.apply(asset);queue.run();File[] files=model.backupFiles();ByteArrayOutputStream out=new ByteArrayOutputStream();BootWallpaperBackup.Result archive=BootWallpaperBackup.write(out,files,model.exportReceipt());
        assertEquals(out.size(),archive.bytes);assertEquals(BootLogoLayout.sha(out.toByteArray()),archive.sha);java.util.Map<String,byte[]> entries=new java.util.HashMap<>();
        try(java.util.zip.ZipInputStream in=new java.util.zip.ZipInputStream(new ByteArrayInputStream(out.toByteArray()))){java.util.zip.ZipEntry entry;while((entry=in.getNextEntry())!=null)entries.put(entry.getName(),in.readAllBytes());}
        assertEquals(3,entries.size());assertArrayEquals(system,entries.get("system-wallpaper.image"));assertArrayEquals(lock,entries.get("lock-wallpaper.image"));assertEquals(receipt().getString("beforeSha"),new org.json.JSONObject(new String(entries.get("receipt.json"),java.nio.charset.StandardCharsets.UTF_8)).getString("beforeSha"));assertEquals(1,backend.sets);
    }
    @Test public void corruptedLockBackupCannotBeExportedOrUsedForRestore()throws Exception{model.apply(asset);queue.run();File[] files=model.backupFiles();Files.write(files[1].toPath(),new byte[]{1,2});try{BootWallpaperBackup.write(new ByteArrayOutputStream(),files,model.exportReceipt());fail();}catch(IOException expected){}model.restore();queue.run();assertEquals(1,backend.sets);assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));}
    @Test public void insufficientPrivateSpacePreventsReadingOrSetting(){File empty=new File(root.getPath()){@Override public long getUsableSpace(){return 0;}};model=new BootWallpaperTransactions(empty,backend,memory,queue,Runnable::run,(ms,r)->{});model.apply(asset);queue.run();assertEquals(0,backend.captures);assertEquals(0,backend.sets);}
    @Test public void truncatedWallpaperBackupIsRejectedBeforeRestore()throws Exception{File file=new File(app.getCacheDir(),"header-only.png");byte[] valid=picture(0xffeeeeee);Files.write(file.toPath(),java.util.Arrays.copyOf(valid,33));try{BootWallpaperBackend.validateImage(file);fail();}catch(IOException expected){}file.delete();}

    void sharedLock()throws Exception{backend.lockId=-1;backend.lock=backend.system.clone();backend.lockSha=BootLogoLayout.sha(backend.lock);backend.originalLockSha=backend.lockSha;}
    @Test public void inheritedLockMigratesOnlyToOldSystemIdAndKeepsExactPixels()throws Exception{
        sharedLock();byte[] original=backend.system.clone();int oldSystemId=backend.id;
        assertTrue(model.apply(asset));queue.run();assertEquals("APPLIED",receipt().getString("state"));
        assertEquals(-1,receipt().getInt("beforeLockId"));assertEquals(oldSystemId,receipt().getInt("lockId"));
        assertArrayEquals(original,backend.lock);assertEquals(backend.originalLockSha,backend.lockSha);
        model=newModel();assertTrue(model.audit());queue.run();assertTrue(receipt().getJSONObject("lastAudit").getBoolean("passed"));
        assertTrue(model.restore());queue.run();assertEquals("RESTORED",receipt().getString("state"));
        assertArrayEquals(original,backend.system);assertArrayEquals(original,backend.lock);assertEquals(oldSystemId,backend.lockId);
        assertEquals(2,backend.sets);
    }
    @Test public void inheritedLockWithUnexpectedNewIdLocksWithoutRetry()throws Exception{
        sharedLock();backend.wrongMigrationId=true;model.apply(asset);queue.run();
        assertEquals("UNKNOWN",receipt().getString("state"));assertEquals(1,backend.sets);assertFalse(model.canRestore());assertTrue(queue.pending.isEmpty());
    }
    @Test public void inheritedLockWithChangedPixelsNeverPasses()throws Exception{
        sharedLock();backend.changeLock=true;model.apply(asset);queue.run();
        assertEquals("UNKNOWN",receipt().getString("state"));assertEquals(1,backend.sets);assertFalse(model.canApply());
    }
    @Test public void inheritedLockSnapshotMustMatchSystemContent()throws Exception{
        BootWallpaperTransactions.Snapshot valid=new BootWallpaperTransactions.Snapshot(2,-1,"a".repeat(64),"a".repeat(64));valid.valid();
        try{new BootWallpaperTransactions.Snapshot(2,-1,"a".repeat(64),"b".repeat(64)).valid();fail();}catch(IOException expected){}
        assertFalse(new BootWallpaperTransactions.Snapshot(3,-1,"c".repeat(64),"c".repeat(64)).preservesLock(valid));
    }
    @Test public void squareSystemBackupDoesNotRelaxPreparedCandidateDimensions()throws Exception{
        Bitmap image=Bitmap.createBitmap(1920,1920,Bitmap.Config.ARGB_8888);File file=new File(app.getCacheDir(),"square-wallpaper.png");
        try{try(OutputStream out=new FileOutputStream(file)){image.compress(Bitmap.CompressFormat.PNG,100,out);}BootWallpaperBackend.validateSnapshotImage(file);
            try{BootWallpaperBackend.validateImage(file);fail();}catch(IOException expected){}
        }finally{image.recycle();file.delete();}
        assertTrue(BootWallpaperBackend.supportedDimensions(1920,1920));assertTrue(BootWallpaperBackend.supportedDimensions(1920,1080));
        assertFalse(BootWallpaperBackend.supportedDimensions(3840,2160));assertFalse(BootWallpaperBackend.supportedDimensions(1920,10800));
    }

    @Test public void defaultImageCopyAndSharedLockAreLabelledBackedUpAndRestorable()throws Exception{
        sharedLock();backend.defaultImage=true;byte[] original=backend.system.clone();
        model.apply(asset);queue.run();assertEquals("APPLIED",receipt().getString("state"));
        assertEquals("DEFAULT_IMAGE_COPY",receipt().getString("beforeKind"));assertTrue(receipt().getBoolean("sharedDefault"));assertEquals(-1,receipt().getInt("lockId"));
        assertEquals(asset.sha,receipt().getString("lockSha"));assertEquals(BootLogoLayout.sha(original),receipt().getString("beforeLockSha"));
        assertArrayEquals(original,Files.readAllBytes(model.backupFiles()[1].toPath()));
        ByteArrayOutputStream archive=new ByteArrayOutputStream();BootWallpaperBackup.write(archive,model.backupFiles(),model.exportReceipt());assertTrue(archive.size()>0);
        model=newModel();assertTrue(model.canRestore());model.audit();queue.run();assertTrue(receipt().getJSONObject("lastAudit").getBoolean("passed"));
        model.restore();queue.run();assertEquals("RESTORED",receipt().getString("state"));assertEquals(2,backend.sharedSets);assertEquals(-1,backend.lockId);
        assertArrayEquals(original,backend.system);assertArrayEquals(original,backend.lock);assertTrue(model.message().contains("副本"));
    }
    @Test public void defaultImageWithIndependentLockNeverUsesBothWallpaperFlags()throws Exception{
        backend.defaultImage=true;byte[] originalLock=backend.lock.clone();int oldLock=backend.lockId;
        model.apply(asset);queue.run();assertEquals("APPLIED",receipt().getString("state"));assertFalse(receipt().getBoolean("sharedDefault"));
        assertEquals(0,backend.sharedSets);assertEquals(oldLock,backend.lockId);assertArrayEquals(originalLock,backend.lock);
        model.restore();queue.run();assertEquals("RESTORED",receipt().getString("state"));assertArrayEquals(originalLock,backend.lock);
    }
    @Test public void defaultImageExternalChangeBeforeDispatchStillPreventsMutation()throws Exception{
        sharedLock();backend.defaultImage=true;backend.externalDuringPrecheck=true;model.apply(asset);queue.run();
        assertEquals(0,backend.sets);assertEquals("FAILED_NO_TARGET_WRITE",receipt().getString("state"));assertFalse(model.canApply());
    }
    @Test public void invalidPersistedSharedDefaultScopeCannotAuthorizeRestore()throws Exception{
        sharedLock();backend.defaultImage=true;model.apply(asset);queue.run();
        memory.value=receipt().put("beforeLockId",42).toString();assertFalse(newModel().canRestore());assertEquals(1,backend.sets);
    }
}
