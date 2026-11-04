package com.gathin.tvhome;
import org.junit.Test;
import android.graphics.*;
import android.view.View;
import android.os.Looper;
import org.robolectric.Shadows;
import org.robolectric.util.ReflectionHelpers;
import java.io.File;
import static org.junit.Assert.*;
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk=28,qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
public class CinemaPerfBenchmarkTest {
 @Test public void profile()throws Exception{
  CinemaViewTest t=new CinemaViewTest();t.create();try{
  t.seed(22);t.home.options.wallpaper("lake");t.home.resume();t.layout(1920,1080);t.awaitWallpaper("lake");
  Bitmap output=Bitmap.createBitmap(1920,1080,Bitmap.Config.ARGB_8888);Canvas canvas=new Canvas(output);
  StringBuilder csv=new StringBuilder("stage,moves,cards,sizes,animators,row_layouts,reflection_builds,surface_builds,outer_glow_strokes,palette_samples,key_cpu_p50_ns,key_cpu_p95_ns,key_cpu_p99_ns\n");
  CinemaPalette palette=ReflectionHelpers.getField(t.home,"palette");
  for(int stage:new int[]{3}){
   CinemaProbe.stage=stage;t.home.options.prefs.edit().putInt("renderer_stage",stage).apply();t.home.options.mode("Cinema");t.home.options.set("focus",stage>0);t.home.options.set("ambient",stage>=2);t.home.options.set("reflection",stage>=4);t.home.resume();t.layout(1920,1080);t.go(0);Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(400));
   CinemaProbe.enabled=true;CinemaProbe.reset();int initialSamples=palette.samples;long[] times=new long[84];
   for(int i=0;i<84;i++){
    int key=(i/21)%2==0?22:21;long start=System.nanoTime();assertTrue(t.key(t.root.findFocus(),key));times[i]=System.nanoTime()-start;
    Shadows.shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(16));t.root.draw(canvas);
   }
   java.util.Arrays.sort(times);csv.append((char)('A'+stage)+",84,"+CinemaProbe.cards+","+CinemaProbe.sizes+","+CinemaProbe.animators+","+CinemaProbe.rowLayouts+","+CinemaProbe.reflections+","+CinemaProbe.surfaces+","+CinemaProbe.draws+","+(palette.samples-initialSamples)+","+times[42]+","+times[79]+","+times[83]+"\n");CinemaProbe.enabled=false;
  }
  output.recycle();File dir=new File("build/cinema-native");dir.mkdirs();java.nio.file.Files.write(new File(dir,"local-perf-profile.csv").toPath(),csv.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }finally{CinemaProbe.enabled=false;t.close();}
 }
}
