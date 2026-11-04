package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;
import java.time.Duration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.*;
import org.robolectric.util.ReflectionHelpers;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@org.robolectric.annotation.Config(sdk={29,31},qualifiers="zh-rTW-w1920dp-h1080dp-land-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class CinemaVisualComfortTest {
 CinemaViewTest t;
 @Before public void setup(){t=new CinemaViewTest();t.create();t.idle();}
 @After public void close(){t.close();}
 BitmapDrawable image(int color){Bitmap b=Bitmap.createBitmap(64,64,Bitmap.Config.ARGB_8888);b.eraseColor(color);return new BitmapDrawable(t.activity.getResources(),b);}
 Bitmap draw(android.graphics.drawable.Drawable d,int w,int h){Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);d.setBounds(0,0,w,h);d.draw(new Canvas(b));return b;}
 @Test public void whiteBackingReadsSmallerThanColourAndTransparentMark(){
  CinemaIconFit white=(CinemaIconFit)CinemaIconFit.wrap(image(Color.WHITE)),colour=(CinemaIconFit)CinemaIconFit.wrap(image(Color.BLUE));
  BitmapDrawable transparent=image(Color.TRANSPARENT);Canvas c=new Canvas(transparent.getBitmap());Paint p=new Paint();p.setColor(Color.RED);c.drawCircle(32,32,20,p);CinemaIconFit mark=(CinemaIconFit)CinemaIconFit.wrap(transparent);
  assertEquals(.86f,white.scale,.001f);assertEquals(.94f,colour.scale,.001f);assertEquals(1f,mark.scale,.001f);assertNull(CinemaIconFit.wrap(null));
 }
 @Test public void opticalInsetPreservesFullLogoPixelsAspectAndOriginalBounds(){
  BitmapDrawable original=image(Color.WHITE);Bitmap bitmap=original.getBitmap();Canvas c=new Canvas(bitmap);Paint p=new Paint();p.setColor(Color.RED);c.drawRect(0,0,8,8,p);original.setBounds(3,4,67,68);Rect before=new Rect(original.getBounds());CinemaIconFit fit=(CinemaIconFit)CinemaIconFit.wrap(original);
  Bitmap output=draw(fit,200,200);assertEquals(Color.RED,output.getPixel(15,15));assertEquals(0,Color.alpha(output.getPixel(1,1)));assertEquals(before,original.getBounds());assertEquals(Color.RED,bitmap.getPixel(0,0));assertFalse(bitmap.isRecycled());assertEquals(original.getIntrinsicWidth(),fit.getIntrinsicWidth());output.recycle();
 }
 @Test public void readabilityIsLocalAndAdaptsOnlyWhenWallpaperIsDelivered(){
  CinemaReadabilityShade d=new CinemaReadabilityShade();BitmapDrawable dark=image(Color.BLACK),bright=image(Color.WHITE);d.wallpaper(dark);int weak=d.strength();d.wallpaper(bright);assertTrue(d.strength()>weak);Shader shader=ReflectionHelpers.getField(d,"band");
  Bitmap b=draw(d,600,338);assertEquals(0,Color.alpha(b.getPixel(300,30)));assertTrue(Color.alpha(b.getPixel(300,238))>100);assertEquals(0,Color.alpha(b.getPixel(300,320)));Shader after=ReflectionHelpers.getField(d,"band");for(int i=0;i<40;i++)d.draw(new Canvas(b));assertSame(after,ReflectionHelpers.getField(d,"band"));assertFalse(bright.getBitmap().isRecycled());b.recycle();
 }
 @Test public void recentPhotographKeepsClearUpperAreaAndDarkCaptionBand(){
  CinemaRecentShade shade=new CinemaRecentShade();Bitmap b=draw(shade,300,144);int first=b.getPixel(150,104);Bitmap again=draw(shade,300,144);assertEquals(first,again.getPixel(150,104));again.recycle();assertTrue(Color.alpha(b.getPixel(150,12))<12);assertTrue(Color.alpha(b.getPixel(150,104))>=200);assertTrue(Color.alpha(b.getPixel(150,134))>=220);b.recycle();
 }
 boolean ring(View v){return (Boolean)ReflectionHelpers.getField(v.getBackground(),"selected");}
 @Test public void checkedModeDoesNotHaveFocusRingUntilItActuallyHasFocus(){
  t.home.showSettings();t.layout(1920,1080);t.idle();TextView cinema=t.root.findViewWithTag("mode:Cinema"),lite=t.root.findViewWithTag("mode:Lite");View personal=t.root.findViewWithTag("personalization");
  assertTrue(cinema.isSelected());assertTrue(cinema.getText().toString().startsWith("✓"));assertFalse(ring(cinema));assertTrue(ring(personal));lite.requestFocus();assertTrue(ring(lite));assertFalse(lite.isSelected());assertTrue(cinema.isSelected());assertFalse(ring(cinema));assertEquals("Cinema",t.home.options.mode());
  lite.performClick();assertTrue(lite.isSelected());assertFalse(cinema.isSelected());personal.requestFocus();assertFalse(ring(lite));assertTrue(lite.getText().toString().startsWith("✓"));
 }
 @Test public void libraryFilterUsesCheckmarkAndOnlyCurrentFocusHasRing(){
  CinemaAppPanel p=t.panel(false);TextView all=(TextView)p.tabs.getChildAt(0),recent=(TextView)p.tabs.getChildAt(2);assertTrue(all.isSelected());assertTrue(all.getText().toString().startsWith("✓"));assertFalse(ring(all));recent.requestFocus();assertTrue(ring(recent));assertFalse(recent.isSelected());recent.performClick();assertTrue(recent.isSelected());assertFalse(all.isSelected());p.query.requestFocus();assertFalse(ring(recent));assertTrue(recent.getText().toString().startsWith("✓"));
 }
 @Test public void personalChoicesExposeSelectedStateWithoutStealingFocus(){
  t.home.showPersonalization();t.layout(1920,1080);t.idle();CinemaPersonalizationPanel p=t.home.personalization;View original=p.findViewWithTag("display:AUTO"),sunset=p.findViewWithTag("display:SUNSET"),theme=p.findViewWithTag("theme:mountain");assertTrue(original.isSelected());sunset.requestFocus();sunset.performClick();assertTrue(sunset.isSelected());assertFalse(original.isSelected());assertSame(sunset,t.root.findFocus());assertTrue(((TextView)sunset).getText().toString().startsWith("✓"));
 }
 @Test public void oneRecentItemHasConsistentInsetAndDoesNotShowPlaceholders(){
  t.activity.getSharedPreferences("last_opened",0).edit().clear().commit();LastOpened.record(t.activity,CinemaViewTest.PKGS[0]);t.home.resume();t.layout(1920,1080);assertEquals(1,t.home.recent.getChildCount());View card=t.home.recent.getChildAt(0);ImageView icon=ReflectionHelpers.getField(card,"icon");TextView name=ReflectionHelpers.getField(card,"name");assertEquals(44,icon.getWidth());assertEquals(78,name.getLeft());assertEquals(1,name.getMaxLines());assertEquals(android.text.TextUtils.TruncateAt.END,name.getEllipsize());assertTrue(name.getCurrentTextColor()!=Color.TRANSPARENT);t.layout(1280,720);assertEquals(Math.round(44*2f/3f),icon.getWidth());assertTrue(name.getRight()<=Math.round(300*2f/3f));
 }
 @Test public void repeatedFocusKeepsOpticalAssetAndReadabilityShader(){
  t.home.wallpapers.pause();t.home.options.mode("Static");t.home.resume();t.home.wallpapers.pause();t.layout(1920,1080);t.go(0);View first=t.root.findFocus();ImageView icon=ReflectionHelpers.getField(first,"icon");Object drawable=icon.getDrawable(),shader=ReflectionHelpers.getField(t.home.readability,"band");
  for(int i=0;i<20;i++){t.go(3);t.go(0);}assertSame(drawable,icon.getDrawable());assertSame(shader,ReflectionHelpers.getField(t.home.readability,"band"));
 }
 @Test public void allFiveVisualChangesHaveNativePreviews()throws Exception{
  t.seed(7);t.layout(1920,1080);t.go(3);Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(400));t.capture(1920,1080,"v70-home-1920.png");t.capture(1280,720,"v70-home-1280.png");
  t.home.showSettings();t.layout(1920,1080);t.idle();t.capture(1920,1080,"v70-settings-focus-1920.png");t.home.showPersonalization();t.layout(1920,1080);t.idle();t.capture(1280,720,"v70-personalization-1280.png");
 }
}
