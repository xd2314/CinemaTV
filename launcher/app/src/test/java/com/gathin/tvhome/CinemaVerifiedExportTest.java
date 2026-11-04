package com.gathin.tvhome;
import android.content.*;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.junit.*;
import org.robolectric.*;
import java.io.*;
import java.util.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=29)
public class CinemaVerifiedExportTest {
 static final class Downloads extends ContentProvider {
  Map<String,File> files=new HashMap<>();int inserts,publishes,deletes,reads;boolean corrupt,failPublish;
  public boolean onCreate(){return true;}
  public Uri insert(Uri uri,ContentValues values){inserts++;try{Uri result=Uri.parse("content://media/external/downloads/"+inserts);File file=new File(getContext().getCacheDir(),"export-"+inserts);files.put(result.toString(),file);return result;}catch(Exception e){throw new IllegalStateException(e);}}
  public int update(Uri uri,ContentValues values,String selection,String[] args){if(failPublish)return 0;publishes++;return 1;}
  public int delete(Uri uri,String selection,String[] args){deletes++;File f=files.remove(uri.toString());if(f!=null)f.delete();return 1;}
  public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){return null;}
  public String getType(Uri uri){return "application/octet-stream";}
  @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
   File f=files.get(uri.toString());if(f==null)throw new FileNotFoundException();
   if(mode.equals("r")){reads++;if(corrupt)try(FileOutputStream out=new FileOutputStream(f)){out.write(9);}catch(IOException e){throw new FileNotFoundException();}}
   return ParcelFileDescriptor.open(f,mode.equals("r")?ParcelFileDescriptor.MODE_READ_ONLY:ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
  }
 }
 Downloads provider;Context context;
 @Before public void setup(){context=RuntimeEnvironment.getApplication();provider=new Downloads();ProviderInfo info=new ProviderInfo();info.authority="media";provider.attachInfo(context,info);org.robolectric.shadows.ShadowContentResolver.registerProviderInternal("media",provider);}
 CinemaVerifiedExport.Proof write(OutputStream out)throws Exception{byte[] bytes={1,2,3};out.write(bytes);return new CinemaVerifiedExport.Proof(bytes.length,BootWallpaperBackup.hex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes)));}
 @Test public void downloadsPublishOnlyAfterReadbackThenVerifyPublishedContent()throws Exception{
  Uri uri=CinemaVerifiedExport.downloads(context,"receipt.json","application/json",this::write);assertNotNull(uri);assertEquals(1,provider.publishes);assertEquals(2,provider.reads);assertEquals(0,provider.deletes);
 }
 @Test public void corruptedDestinationIsRemovedAndNeverPublished()throws Exception{
  provider.corrupt=true;try{CinemaVerifiedExport.downloads(context,"backup.img","application/octet-stream",this::write);fail();}catch(IOException expected){}
  assertEquals(0,provider.publishes);assertEquals(1,provider.deletes);assertTrue(provider.files.isEmpty());
 }
 @Test public void failedPublicationRemovesOwnedIncompleteEntry()throws Exception{
  provider.failPublish=true;try{CinemaVerifiedExport.downloads(context,"material.png","image/png",this::write);fail();}catch(IOException expected){}assertEquals(1,provider.deletes);
 }
 @Test public void invalidFilenameNeverCreatesProviderEntry()throws Exception{
  try{CinemaVerifiedExport.downloads(context,"../private.key","image/png",this::write);fail();}catch(IOException expected){}assertEquals(0,provider.inserts);
 }
 @Test public void shortAndOversizeReadbackAreRejected()throws Exception{
  for(byte[] data:new byte[][]{new byte[]{1},new byte[]{1,2,3,4}})try{CinemaVerifiedExport.verify(new ByteArrayInputStream(data),new CinemaVerifiedExport.Proof(3,"a".repeat(64)));fail();}catch(IOException expected){}
 }
}
