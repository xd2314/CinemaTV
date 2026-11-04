package com.gathin.tvhome;
import android.app.Activity;
import android.graphics.*;
import android.media.MediaFormat;
import android.net.Uri;
import org.junit.*;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;
import java.io.*;
import java.nio.*;
import java.util.*;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=29)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class BootAssetsTest {
 Activity a;BootAssetStore store;File folder;
 @Before public void setup(){a=Robolectric.buildActivity(Activity.class).setup().get();store=new BootAssetStore(a);folder=new File(a.getFilesDir(),"boot_asset_preparation");clear(folder);}
 @After public void close(){clear(folder);a.finish();}
 static void clear(File f){if(f.isDirectory())for(File child:Objects.requireNonNull(f.listFiles()))clear(child);f.delete();}
 byte[] png(int w,int h,boolean alpha)throws Exception{Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);b.eraseColor(alpha?0x80FF0000:Color.RED);ByteArrayOutputStream out=new ByteArrayOutputStream();b.compress(Bitmap.CompressFormat.PNG,100,out);b.recycle();return out.toByteArray();}
 BootAssetStore.Asset prepare(byte[] bytes,BootAssetStore.Target t,boolean crop)throws Exception{return store.prepare(t,crop,()->false,()->new ByteArrayInputStream(bytes));}
 @Test public void threeTargetIndexesSurviveImportsAndRecreationWithoutOverwritingEachOther()throws Exception{
  BootAssetStore.Asset logo=prepare(png(80,80,false),BootAssetStore.Target.LOGO,false);store.commit(logo);
  File dir=new File(folder,UUID.randomUUID().toString());assertTrue(dir.mkdir());File source=new File(dir,"source"),output=new File(dir,"prepared.mp4"),poster=new File(dir,"poster.png");
  try(InputStream in=getClass().getResourceAsStream("/boot-assets/valid.mp4")){BootAssetStore.copy(in,source,()->false);}java.nio.file.Files.copy(source.toPath(),output.toPath());java.nio.file.Files.write(poster.toPath(),png(640,360,false));
  BootAssetStore.Asset movie=new BootAssetStore.Asset(dir.getName(),BootAssetStore.Target.VIDEO,false,dir,BootAssetStore.hash(output),BootAssetStore.hash(source),BootAssetStore.hash(poster),"Index fixture only; no decoder claim");store.commit(movie);
  BootAssetStore.Asset wallpaper=prepare(png(80,120,false),BootAssetStore.Target.WALLPAPER,false);store.commit(wallpaper);
  BootAssetStore reopened=new BootAssetStore(a);assertEquals(wallpaper.id,reopened.load().id);assertEquals(logo.id,reopened.load(BootAssetStore.Target.LOGO).id);assertEquals(movie.id,reopened.load(BootAssetStore.Target.VIDEO).id);assertEquals(wallpaper.id,reopened.load(BootAssetStore.Target.WALLPAPER).id);
  BootAssetStore.Asset reframed=store.reframe(logo,true,()->false);store.commit(reframed);assertEquals(movie.id,reopened.load(BootAssetStore.Target.VIDEO).id);assertEquals(wallpaper.id,reopened.load(BootAssetStore.Target.WALLPAPER).id);
 }
 @Test public void schemaOneMigrationIsReadOnlyUntilNextCommitAndRetainsOriginalTarget()throws Exception{
  BootAssetStore.Asset logo=prepare(png(80,80,false),BootAssetStore.Target.LOGO,false);
  org.json.JSONObject old=new org.json.JSONObject().put("schema",1).put("id",logo.id).put("target","LOGO").put("crop",logo.crop).put("sha256",logo.sha).put("sourceSha256",logo.sourceSha).put("posterSha256",logo.posterSha).put("info",logo.info);
  File record=new File(folder,"last.json");java.nio.file.Files.write(record.toPath(),old.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));byte[] before=java.nio.file.Files.readAllBytes(record.toPath());
  assertEquals(logo.id,store.load(BootAssetStore.Target.LOGO).id);assertNull(store.load(BootAssetStore.Target.VIDEO));assertArrayEquals(before,java.nio.file.Files.readAllBytes(record.toPath()));
  BootAssetStore.Asset wallpaper=prepare(png(80,80,false),BootAssetStore.Target.WALLPAPER,false);store.commit(wallpaper);assertEquals(logo.id,store.load(BootAssetStore.Target.LOGO).id);assertEquals(2,new org.json.JSONObject(new String(java.nio.file.Files.readAllBytes(record.toPath()),java.nio.charset.StandardCharsets.UTF_8)).getInt("schema"));
 }
 @Test public void oneDamagedTargetDoesNotSubstituteOrInvalidateAnotherTarget()throws Exception{
  BootAssetStore.Asset logo=prepare(png(80,80,false),BootAssetStore.Target.LOGO,false),wallpaper=prepare(png(80,120,false),BootAssetStore.Target.WALLPAPER,false);store.commit(logo);store.commit(wallpaper);java.nio.file.Files.write(wallpaper.output.toPath(),new byte[]{1,2});
  try{store.load(BootAssetStore.Target.WALLPAPER);fail();}catch(IOException expected){}assertEquals(logo.id,store.load(BootAssetStore.Target.LOGO).id);
 }
 @Test public void imageIsNormalizedToTargetWithoutChangingSource()throws Exception{
  byte[] source=png(80,80,false);BootAssetStore.Asset x=prepare(source,BootAssetStore.Target.LOGO,false);Bitmap b=BitmapFactory.decodeFile(x.output.getPath());assertEquals(1280,b.getWidth());assertEquals(720,b.getHeight());assertEquals(Color.BLACK,b.getPixel(0,360));assertEquals(Color.RED,b.getPixel(640,360));assertArrayEquals(source,java.nio.file.Files.readAllBytes(x.source.toPath()));assertNotEquals(x.sha,x.sourceSha);b.recycle();
 }
 @Test public void fillCropAndTransparentPixelsBecomeOpaque()throws Exception{
  BootAssetStore.Asset x=prepare(png(80,80,true),BootAssetStore.Target.WALLPAPER,true);Bitmap b=BitmapFactory.decodeFile(x.output.getPath());assertEquals(1920,b.getWidth());assertEquals(1080,b.getHeight());assertEquals(255,Color.alpha(b.getPixel(0,500)));assertTrue(Color.red(b.getPixel(0,500))>=127);assertEquals(0,Color.blue(b.getPixel(0,500)));b.recycle();
 }
 @Test public void reframePreservesPriorCandidateAndOriginal()throws Exception{
  BootAssetStore.Asset first=prepare(png(80,80,false),BootAssetStore.Target.LOGO,false);store.commit(first);BootAssetStore.Asset next=store.reframe(first,true,()->false);assertNotEquals(first.id,next.id);assertEquals(first.sourceSha,next.sourceSha);assertNotEquals(first.sha,next.sha);store.verify(first);store.commit(next);assertEquals(next.id,new BootAssetStore(a).load().id);
 }
 @Test public void malformedOrFakeImageNeverReplacesLastRecord()throws Exception{
  BootAssetStore.Asset first=prepare(png(80,80,false),BootAssetStore.Target.LOGO,false);store.commit(first);for(byte[] fake:new byte[][]{new byte[0],"not an image.png".getBytes(),new byte[]{'P','K',3,4}}){try{prepare(fake,BootAssetStore.Target.LOGO,false);fail();}catch(Exception expected){assertEquals(first.id,store.load().id);}}
 }
 @Test public void sourceByteLimitAndCancellationDeleteDraft()throws Exception{
  byte[] oversized=new byte[(int)BootAssetStore.MAX_BYTES+1];try{prepare(oversized,BootAssetStore.Target.LOGO,false);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("20 MiB"));}
  try{store.prepare(BootAssetStore.Target.LOGO,false,()->true,()->new ByteArrayInputStream(new byte[]{1}));fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("取消"));}assertEquals(0,Objects.requireNonNull(folder.list()).length);
 }
 @Test public void oversizedPixelBoundsAndInvalidDimensionsAreRejected()throws Exception{BootAssetStore.bounds(4000,2000);for(int[] size:new int[][]{{3840,2160},{Integer.MAX_VALUE,100},{0,100},{100,-1}}){try{BootAssetStore.bounds(size[0],size[1]);fail();}catch(IOException expected){}}}
 @Test public void exifOrientationAllEightPreservesCornersAndDimensions(){
  Bitmap source=Bitmap.createBitmap(3,2,Bitmap.Config.ARGB_8888);int[] colors={Color.RED,Color.GREEN,Color.BLUE,Color.CYAN,Color.MAGENTA,Color.YELLOW};source.setPixels(colors,0,3,0,0,3,2);
  int[] topLeft={Color.RED,Color.BLUE,Color.YELLOW,Color.CYAN,Color.RED,Color.CYAN,Color.YELLOW,Color.BLUE};
  for(int orientation=1;orientation<=8;orientation++){Bitmap transformed=BootAssetStore.orient(source,orientation);assertEquals(orientation<=4?3:2,transformed.getWidth());assertEquals(orientation<=4?2:3,transformed.getHeight());assertEquals("orientation "+orientation,topLeft[orientation-1],transformed.getPixel(0,0));if(transformed!=source)transformed.recycle();}source.recycle();
 }
 @Test public void contentUriCannotControlPrivateFilenameOrShell()throws Exception{
  Uri uri=Uri.parse("content://documents/a%27%3B%24(foo)%0A.png");Shadows.shadowOf(a.getContentResolver()).registerInputStream(uri,new ByteArrayInputStream(png(80,80,false)));BootAssetStore.Asset x=store.importUri(uri,BootAssetStore.Target.LOGO,false,()->false);assertTrue(x.id.matches("[a-f0-9-]+"));assertEquals("source",x.source.getName());assertTrue(x.source.getCanonicalPath().startsWith(folder.getCanonicalPath()));
  try{store.importUri(Uri.parse("file:///data/local/boot.mp4"),BootAssetStore.Target.VIDEO,false,()->false);fail();}catch(IOException expected){}
 }
 @Test public void tamperedPrivateAssetCannotReloadOrExport()throws Exception{BootAssetStore.Asset x=prepare(png(80,80,false),BootAssetStore.Target.LOGO,false);store.commit(x);java.nio.file.Files.write(x.output.toPath(),new byte[]{1,2});try{store.load();fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("變更"));}}
 @Test public void wrongOrExpiredProviderKeepsPreviousRecord()throws Exception{BootAssetStore.Asset x=prepare(png(80,80,false),BootAssetStore.Target.LOGO,false);store.commit(x);try{store.prepare(BootAssetStore.Target.LOGO,false,()->false,()->{throw new IOException("URI expired");});fail();}catch(IOException expected){}assertEquals(x.id,store.load().id);}
 @Test public void videoCadenceAcceptsReorderedBFramesButRejectsVfrOrWrongLength()throws Exception{
  List<Long> good=new ArrayList<>();for(int i=0;i<120;i++)good.add(Math.round(i*1_000_000d/24));Collections.swap(good,2,3);BootVideoValidator.cadence(good);
  List<Long> bad=new ArrayList<>(good);bad.set(10,bad.get(10)+4000);rejectCadence(bad);bad=new ArrayList<>(good);bad.remove(0);rejectCadence(bad);bad=new ArrayList<>(good);bad.set(10,bad.get(9));rejectCadence(bad);
 }
 void rejectCadence(List<Long> times)throws Exception{try{BootVideoValidator.cadence(times);fail();}catch(IOException expected){}}
 static class Writer {StringBuilder bits=new StringBuilder();void n(int v,int n){for(int i=n-1;i>=0;i--)bits.append((v>>i)&1);}void ue(int v){int x=v+1,n=32-Integer.numberOfLeadingZeros(x);n(0,n-1);n(x,n);}byte[] bytes(){while(bits.length()%8!=0)bits.append('0');byte[] x=new byte[bits.length()/8];for(int i=0;i<x.length;i++)x[i]=(byte)Integer.parseInt(bits.substring(i*8,i*8+8),2);return x;}}
 static byte[] sps(int profile,int level,int depth,int frame){Writer b=new Writer();b.n(profile,8);b.n(0,8);b.n(level,8);b.ue(0);b.ue(1);b.ue(depth);b.ue(0);b.n(0,1);b.n(0,1);b.ue(0);b.ue(0);b.ue(0);b.ue(1);b.n(0,1);b.ue(79);b.ue(44);b.n(frame,1);b.n(1,1);b.n(0,1);b.n(0,1);b.n(1,1);byte[] raw=b.bytes();ByteArrayOutputStream out=new ByteArrayOutputStream();out.write(0);out.write(0);out.write(0);out.write(1);out.write(0x67);int zeros=0;for(byte z:raw){int v=z&255;if(zeros==2&&v<=3){out.write(3);zeros=0;}out.write(v);zeros=v==0?zeros+1:0;}return out.toByteArray();}
 @Test public void actualSpsMustMatchHigh31EightBitProgressive420()throws Exception{BootVideoValidator.sps(sps(100,31,0,1));for(byte[] bad:new byte[][]{sps(66,31,0,1),sps(100,40,0,1),sps(100,31,2,1),sps(100,31,0,0),new byte[]{0,0,1,0x67},new byte[0]}){try{BootVideoValidator.sps(bad);fail();}catch(IOException expected){}}}
 MediaFormat validFormat(){MediaFormat f=MediaFormat.createVideoFormat("video/avc",1280,720);f.setLong(MediaFormat.KEY_DURATION,5_000_000);f.setByteBuffer("csd-0",ByteBuffer.wrap(sps(100,31,0,1)));return f;}
 @Test public void metadataRejectsWrongCodecDimensionsDurationRotationAndHdr()throws Exception{BootVideoValidator.validateFormat(validFormat());for(int mode=0;mode<6;mode++){MediaFormat f=validFormat();switch(mode){case 0:f.setString(MediaFormat.KEY_MIME,"video/hevc");break;case 1:f.setInteger(MediaFormat.KEY_WIDTH,1920);break;case 2:f.setLong(MediaFormat.KEY_DURATION,6_000_000);break;case 3:f.setInteger(MediaFormat.KEY_ROTATION,90);break;case 4:f.setInteger(MediaFormat.KEY_COLOR_TRANSFER,MediaFormat.COLOR_TRANSFER_ST2084);break;default:f.setByteBuffer("csd-0",ByteBuffer.wrap(new byte[0]));}try{BootVideoValidator.validateFormat(f);fail();}catch(IOException expected){}}}
 static byte[] box(String name,byte[] body)throws Exception{ByteArrayOutputStream out=new ByteArrayOutputStream();DataOutputStream d=new DataOutputStream(out);d.writeInt(body.length+8);d.writeBytes(name);d.write(body);return out.toByteArray();}
 File container(String... order)throws Exception{if(!folder.isDirectory())folder.mkdirs();File f=new File(folder,UUID.randomUUID()+".mp4");try(OutputStream out=new FileOutputStream(f)){for(String name:order)out.write(box(name,name.equals("ftyp")?new byte[]{'i','s','o','m',0,0,0,0}:new byte[0]));}return f;}
 @Test public void mp4NeedsFaststartAndCompleteBoxBounds()throws Exception{
  BootVideoValidator.container(container("ftyp","moov","mdat"));for(File bad:new File[]{container("ftyp","mdat","moov"),container("ftyp","moof","moov","mdat"),container("moov","mdat"),container("ftyp","moov","moov","mdat")}){try{BootVideoValidator.container(bad);fail();}catch(IOException expected){}}
  File truncated=container("ftyp","moov","mdat");try(RandomAccessFile f=new RandomAccessFile(truncated,"rw")){f.setLength(f.length()-1);}try{BootVideoValidator.container(truncated);fail();}catch(IOException expected){}
 }
 @Test public void realEncodedFixtureHasValidFaststartAndHigh31Sps()throws Exception{
  if(!folder.isDirectory())folder.mkdirs();File file=new File(folder,"real.mp4");try(InputStream in=getClass().getResourceAsStream("/boot-assets/valid.mp4")){assertNotNull(in);BootAssetStore.copy(in,file,()->false);}BootVideoValidator.container(file);BootVideoValidator.identityMatrices(file);
  try(InputStream in=getClass().getResourceAsStream("/boot-assets/valid-sps.bin")){assertNotNull(in);BootVideoValidator.sps(in.readAllBytes());}
 }

 @Test public void realMovieRotationOrScalingIsRejectedWithoutDecoding()throws Exception{
  if(!folder.isDirectory())folder.mkdirs();File file=new File(folder,"rotated.mp4");byte[] bytes;try(InputStream in=getClass().getResourceAsStream("/boot-assets/valid.mp4")){bytes=in.readAllBytes();}
  int start=-1;for(int i=0;i<bytes.length-4;i++)if(bytes[i]=='t'&&bytes[i+1]=='k'&&bytes[i+2]=='h'&&bytes[i+3]=='d'){start=i+4;break;}assertTrue(start>0);assertEquals(0,bytes[start]);ByteBuffer.wrap(bytes).putInt(start+40,0);java.nio.file.Files.write(file.toPath(),bytes);
  try{BootVideoValidator.identityMatrices(file);fail();}catch(IOException expected){assertTrue(expected.getMessage().contains("矩陣"));}
 }

}
