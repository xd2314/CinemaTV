package com.gathin.tvhome;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class) @Config(sdk=29)
public class BootMaterialInputTest {
    Uri uri=Uri.parse("content://local/selected-image.png");
    Intent withClip(ClipData clip){Intent result=new Intent();result.setClipData(clip);return result;}
    @Test public void ordinaryDataUriIsSupported()throws Exception{
        BootMaterialInput.Selection s=BootMaterialInput.selected(new Intent().setData(uri));assertEquals(uri,s.uri);assertEquals("data",s.route);
    }
    @Test public void clipOnlyAndStreamOnlyAreSupported()throws Exception{
        BootMaterialInput.Selection clip=BootMaterialInput.selected(withClip(ClipData.newRawUri("material",uri)));
        assertEquals(uri,clip.uri);assertEquals("clip",clip.route);
        BootMaterialInput.Selection stream=BootMaterialInput.selected(new Intent().putExtra(Intent.EXTRA_STREAM,uri));
        assertEquals(uri,stream.uri);assertEquals("stream",stream.route);
    }
    @Test public void SameUriRepeatedAcrossFieldsIsOneSelection()throws Exception{
        assertEquals(uri,BootMaterialInput.selected(withClip(ClipData.newRawUri("material",uri)).setData(uri).putExtra(Intent.EXTRA_STREAM,uri)).uri);
    }
    @Test public void singleStreamListWorksButMultipleStreamsAreRejected()throws Exception{
        java.util.ArrayList<Uri> items=new java.util.ArrayList<>();items.add(uri);
        assertEquals(uri,BootMaterialInput.selected(new Intent().putParcelableArrayListExtra(Intent.EXTRA_STREAM,items)).uri);
        items.add(Uri.parse("content://local/other.png"));
        try{BootMaterialInput.selected(new Intent().putParcelableArrayListExtra(Intent.EXTRA_STREAM,items));fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("一個"));}
    }
    @Test public void conflictingOrMultipleFilesAreNotSilentlyReducedToFirst()throws Exception{
        ClipData clip=ClipData.newRawUri("material",uri);clip.addItem(new ClipData.Item(Uri.parse("content://local/other.png")));
        for(Intent i:new Intent[]{withClip(clip),new Intent().setData(uri).putExtra(Intent.EXTRA_STREAM,Uri.parse("content://local/other.png"))}){
            try{BootMaterialInput.selected(i);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("一個"));}
        }
    }
    @Test public void missingRemoteAndMalformedResultsExplainFailure()throws Exception{
        for(Intent i:new Intent[]{null,new Intent(),new Intent().setData(Uri.parse("https://example.invalid/image.png")),new Intent().setData(Uri.parse("content:/missing-authority"))}){
            try{BootMaterialInput.selected(i);fail();}catch(IOException expected){assertFalse(expected.getMessage().isEmpty());}
        }
    }
    @Test public void localFilesRequireOrdinaryLegacyReadPermissionOnlyWhereApplicable(){
        Uri local=Uri.parse("file:///sdcard/Download/test.png");
        assertTrue(BootMaterialInput.needsReadPermission(local,29,false));assertFalse(BootMaterialInput.needsReadPermission(local,29,true));
        assertFalse(BootMaterialInput.needsReadPermission(local,30,false));assertFalse(BootMaterialInput.needsReadPermission(uri,29,false));
    }
    @Test public void sharedFilesAreCanonicalAndCannotEscapeViaTraversalOrSymlink()throws Exception{
        File parent=Files.createTempDirectory("material-file-test-").toFile(),shared=new File(parent,"shared"),privateDir=new File(parent,"private");assertTrue(shared.mkdir());assertTrue(privateDir.mkdir());
        File selected=new File(shared,"picture.png"),outside=new File(privateDir,"secret.png");Files.write(selected.toPath(),new byte[]{1});Files.write(outside.toPath(),new byte[]{2});
        assertEquals(selected.getCanonicalFile(),BootMaterialInput.sharedFile(Uri.fromFile(selected),shared));
        File alias=new File(shared,"alias.png");Files.createSymbolicLink(alias.toPath(),outside.toPath());
        for(Uri rejected:new Uri[]{Uri.fromFile(outside),Uri.fromFile(alias),Uri.fromFile(new File(shared,"../private/secret.png")),Uri.parse("file://remote/sdcard/test.png")}){
            try{BootMaterialInput.sharedFile(rejected,shared);fail();}catch(IOException expected){assertFalse(expected.getMessage().isEmpty());}
        }
        Files.delete(alias.toPath());BootAssetsTest.clear(parent);
    }
    @Test public void diagnosticsContainShapeWithoutPrivateFileNameOrUri(){
        Intent result=new Intent().setData(Uri.parse("content://private-account/customer-photo.png?token=secret"));
        String shape=BootMaterialInput.shape(result);assertTrue(shape.contains("data=content"));assertFalse(shape.contains("secret"));assertFalse(shape.contains("customer"));assertFalse(shape.contains("private-account"));
    }
    @Test public void otherAppPrivateExternalFoldersAreNotLegacyImportSources()throws Exception{
        File shared=Files.createTempDirectory("material-private-test-").toFile();
        try{for(String path:new String[]{"Android/data/other.app/image.png","Android/obb/other.app/image.png"}){
            try{BootMaterialInput.sharedFile(Uri.fromFile(new File(shared,path)),shared);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("私人"));}
        }}finally{BootAssetsTest.clear(shared);}
    }
}
