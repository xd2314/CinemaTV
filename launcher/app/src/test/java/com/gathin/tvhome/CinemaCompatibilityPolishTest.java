package com.gathin.tvhome;
import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Looper;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.json.JSONObject;
import org.junit.*;
import org.robolectric.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk={29,31},qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaCompatibilityPolishTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();t.activity.getSharedPreferences("cinema_memory_v1",0).edit().clear().commit();}
 @After public void close(){t.close();}
 void settle(CinemaMemoryDiagnostics monitor)throws Exception{for(int n=0;n<200&&(Boolean)ReflectionHelpers.getField(monitor,"busy");n++){Thread.sleep(5);t.idle();}assertFalse((Boolean)ReflectionHelpers.getField(monitor,"busy"));}
 @Test public void failedReadIsUnknownAndExceptionDetailsStayPrivate()throws Exception{
  CinemaMemoryDiagnostics m=new CinemaMemoryDiagnostics(t.activity,JSONObject::new,(a,r)->{throw new IOException("private-secret");});
  try{m.show();settle(m);JSONObject record=m.record();assertEquals("FAILED",record.getJSONObject("latest").getString("readStatus"));String text=CinemaMemoryDiagnostics.description(record);assertTrue(text.contains("讀取失敗"));assertTrue(text.contains("App PSS：未知"));assertFalse(text.contains("0.0 MiB"));assertFalse(record.toString().contains("private-secret"));
   AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertEquals("Cinema TV · 記憶體檢查",Shadows.shadowOf(d).getTitle().toString());assertTrue(text.contains("Android API "+android.os.Build.VERSION.SDK_INT));
  }finally{m.close();}
 }
 @Test public void failedReadStillRestoresRecordButtonAndCountsOnlyActualPss()throws Exception{
  CinemaMemoryDiagnostics m=new CinemaMemoryDiagnostics(t.activity,JSONObject::new,(a,r)->{throw new IOException();});
  try{m.show();settle(m);AlertDialog d=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();assertTrue(d.getButton(-1).isEnabled());assertTrue(m.start());assertEquals("停止記錄",d.getButton(-1).getText().toString());settle(m);assertEquals(1,m.record().getInt("failedReads"));assertFalse(m.record().has("peakPssKiB"));assertEquals(0,m.record().getInt("validPssSamples"));}finally{m.close();}
 }
 @Test public void laterFailureKeepsEarlierPeakWithoutInventingCurrentZero()throws Exception{
  AtomicInteger reads=new AtomicInteger();CinemaMemoryDiagnostics m=new CinemaMemoryDiagnostics(t.activity,JSONObject::new,(a,r)->{if(reads.getAndIncrement()>0)throw new IOException();return new JSONObject().put("pssKiB",10240);});
  try{assertTrue(m.start());settle(m);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(5000));settle(m);assertEquals(10240,m.record().getLong("peakPssKiB"));assertEquals(1,m.record().getInt("validPssSamples"));assertEquals(1,m.record().getInt("failedReads"));String text=CinemaMemoryDiagnostics.description(m.record());assertTrue(text.contains("App PSS：未知"));assertTrue(text.contains("抽樣最高 PSS：10.0 MiB"));}finally{m.close();}
 }
 @Test public void partialDataDoesNotHideOtherAvailableMetrics()throws Exception{
  String text=CinemaMemoryDiagnostics.description(new JSONObject().put("latest",new JSONObject().put("pssKiB",0).put("javaUsedBytes",32L*1024*1024)));assertTrue(text.contains("App PSS：未知"));assertTrue(text.contains("Java 使用／上限：32.0 MiB / 未知"));assertTrue(text.contains("尚無有效樣本"));
 }
 @Test public void saturatedImageQueueRestoresButtonsAndRetainsImage()throws Exception{
  ThreadPoolExecutor shared=(ThreadPoolExecutor)CinemaImageDecoder.worker();CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);List<Future<?>> queued=new ArrayList<>();File dir=CinemaImageStore.directory(t.activity);dir.mkdirs();File file=new File(dir,"67-abc.png");Bitmap bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
  try{shared.submit(()->{entered.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});assertTrue(entered.await(5,TimeUnit.SECONDS));while(shared.getQueue().remainingCapacity()>0)queued.add(shared.submit(()->{}));
   ReflectionHelpers.callStaticMethod(CinemaImageLibrary.class,"preview",ReflectionHelpers.ClassParameter.from(android.app.Activity.class,t.activity),ReflectionHelpers.ClassParameter.from(CinemaSettings.class,t.home.options),ReflectionHelpers.ClassParameter.from(CinemaImageStore.class,t.home.imageStore),ReflectionHelpers.ClassParameter.from(List.class,new ArrayList<AppAdapter.Entry>()),ReflectionHelpers.ClassParameter.from(File.class,file));
   AlertDialog preview=org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog();preview.getButton(-3).performClick();assertTrue(preview.getButton(-3).isEnabled());preview.getButton(-1).performClick();org.robolectric.shadows.ShadowAlertDialog.getLatestAlertDialog().getButton(-1).performClick();assertTrue(preview.getButton(-1).isEnabled());assertTrue(file.exists());preview.dismiss();
  }finally{for(Future<?> f:queued)CinemaImageDecoder.cancel(f);release.countDown();}
 }
 @Test public void optionalPosterCanFailWithoutDiscardingExistingCover()throws Exception{
  assertNull(CinemaPosterPolicy.readCandidate(false,()->{throw new IOException();}));assertNull(CinemaPosterPolicy.readCandidate(false,()->null));Bitmap bitmap=Bitmap.createBitmap(16,9,Bitmap.Config.ARGB_8888);assertSame(bitmap,CinemaPosterPolicy.readCandidate(true,()->bitmap));assertFalse(bitmap.isRecycled());bitmap.recycle();
 }
 @Test public void firstPosterAndRealDecoderErrorsRemainMandatory()throws Exception{
  try{CinemaPosterPolicy.readCandidate(true,()->null);fail();}catch(IOException expected){}try{CinemaPosterPolicy.readCandidate(true,()->{throw new IOException();});fail();}catch(IOException expected){}try{CinemaPosterPolicy.readCandidate(false,()->{throw new IllegalStateException();});fail();}catch(IllegalStateException expected){}
 }
}
