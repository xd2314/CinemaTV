package com.gathin.tvhome;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import java.util.HashMap;
/** One bounded floor renderer. Seven reused geometry slots; no new objects in frame updates. */
final class CinemaFocusLight extends View {
 private static final class Ink {
  final Paint pool=new Paint(Paint.ANTI_ALIAS_FLAG),spread=new Paint(Paint.ANTI_ALIAS_FLAG),contact=new Paint(Paint.ANTI_ALIAS_FLAG),halo=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  final Bitmap rim,column;private final Paint reflectionPaint=new Paint(Paint.ANTI_ALIAS_FLAG|Paint.FILTER_BITMAP_FLAG);
  Ink(int color){int rgb=color&0xFFFFFF;
   pool.setShader(new RadialGradient(0,0,1,new int[]{rgb|0xFA000000,rgb|0xD0000000,rgb|0x78000000,rgb|0x14000000,rgb},new float[]{0,.18f,.48f,.80f,1},Shader.TileMode.CLAMP));
   spread.setShader(new RadialGradient(0,0,1,new int[]{rgb|0xF0000000,rgb|0x90000000,rgb},new float[]{0,.30f,1},Shader.TileMode.CLAMP));
   int white=Color.rgb((Color.red(color)+4*255)/5,(Color.green(color)+4*255)/5,(Color.blue(color)+4*255)/5);
   contact.setShader(new RadialGradient(0,0,1,new int[]{white,white,rgb|0xCC000000,rgb},new float[]{0,.30f,.65f,1},Shader.TileMode.CLAMP));
   // Precomputed signed-distance halo, not six animated strokes or a live blur.
   int w=104,h=80;int[] pixels=new int[w*h];
   for(int y=0;y<h;y++)for(int x=0;x<w;x++){
    float qx=Math.abs(x+.5f-w/2f)-(48-9),qy=Math.abs(y+.5f-h/2f)-(36-9);
    float distance=(float)Math.hypot(Math.max(qx,0),Math.max(qy,0))+Math.min(Math.max(qx,qy),0)-9;
    float strength=distance<0?0:Math.max(0,1-distance/4f);strength*=strength;
    pixels[y*w+x]=rgb|(Math.round(125*strength)<<24);
   }
   rim=Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888);
   int cw=48,ch=96;int[] rays=new int[cw*ch];
   for(int y=0;y<ch;y++)for(int x=0;x<cw;x++){float fy=y/(float)(ch-1),fx=(x+.5f)/cw*2-1;
    float drift=(float)(Math.sin(fy*13)*.07+Math.sin(fy*29)*.025);float width=.34f+.30f*fy;float side=Math.max(0,1-Math.abs(fx-drift)/width);
    float fall=(float)Math.pow(1-fy,1.7);float texture=.87f+.13f*(float)Math.cos(fy*62+fx*5);float whiteMix=.42f*(1-fy)*(1-fy);
    int tint=Color.rgb(Math.round(Color.red(color)*(1-whiteMix)+255*whiteMix),Math.round(Color.green(color)*(1-whiteMix)+255*whiteMix),Math.round(Color.blue(color)*(1-whiteMix)+255*whiteMix))&0xFFFFFF;
    rays[y*cw+x]=tint|(Math.round(230*side*side*fall*texture)<<24);}
   column=Bitmap.createBitmap(rays,cw,ch,Bitmap.Config.ARGB_8888);
  }
 }
 private final java.util.LinkedHashMap<Integer,Ink> inks=new java.util.LinkedHashMap<Integer,Ink>(16,.75f,true){
  @Override protected boolean removeEldestEntry(java.util.Map.Entry<Integer,Ink> e){return size()>CinemaMemoryPolicy.MAX_LIGHT_INKS;}
 };private Ink ink;
 private final RectF card=new RectF(),haloRect=new RectF(),columnRect=new RectF();private float originX;
 void origin(float x){originX=x;}
 private final RectF[] neighbors=new RectF[CinemaCarousel.MAX_VISIBLE_ITEMS];
 private final Ink[] neighborInk=new Ink[CinemaCarousel.MAX_VISIBLE_ITEMS];
 private final float[] neighborStrength=new float[CinemaCarousel.MAX_VISIBLE_ITEMS];private int count;
 private float poolX,poolY,startX,startY,unit=1,progress=1;private boolean poolEnabled;
 private CinemaReflection reflection;
 private float zone=1,poolGain=1,contactGain=1,reflectionGain=1;
 // Both animation paths call position() after zone(); invalidate only once per frame.
 void zone(float value){zone=value;}
 void intensity(int value){poolGain=value/85f;contactGain=value==0?0:(float)Math.sqrt(value/85f);reflectionGain=(float)Math.pow(value/85f,1.2);invalidate();}
 private int alpha(float strength,float gain){return Math.round(255*Math.min(1,Math.max(0,strength*gain*zone)));}
 private boolean streaks=true;void streaks(boolean enabled){streaks=enabled;invalidate();}
 CinemaFocusLight(Context context){super(context);for(int i=0;i<neighbors.length;i++)neighbors[i]=new RectF();setFocusable(false);setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);}
 void prepare(int accent){if(!inks.containsKey(accent))inks.put(accent,new Ink(accent));}
 void select(int accent){prepare(accent);ink=inks.get(accent);startX=poolX;startY=poolY;reflection=null;}
 void reflection(CinemaReflection image){reflection=image;invalidate();}
 void clear(){reflection=null;inks.clear();ink=null;java.util.Arrays.fill(neighborInk,null);count=0;invalidate();}
 int cachedInkCount(){return inks.size();}
 long cachedBitmapBytes(){long bytes=0;for(Ink value:inks.values())bytes+=value.rim.getAllocationByteCount()+value.column.getAllocationByteCount();return bytes;}
 void beginNeighbors(){java.util.Arrays.fill(neighborInk,null);count=0;}
 void neighbor(RectF bounds,int accent,float focus){neighbor(bounds,accent,focus,1);}
 void neighbor(RectF bounds,int accent,float focus,int distance){if(count==neighbors.length)return;neighbors[count].set(bounds);neighbors[count].offset(originX,0);prepare(accent);neighborInk[count]=inks.get(accent);neighborStrength[count]=Math.max(0,1-focus)*(distance<=1?1:.5f);count++;}
 void position(RectF bounds,float fraction,float u,boolean enabled){
  card.set(bounds);card.offset(originX,0);unit=u;progress=fraction;poolEnabled=enabled;
  poolX=startX+(card.centerX()-startX)*fraction;poolY=startY+(card.bottom-startY)*fraction;
  // The bounded dock optical layer is invalidated; no full-screen overlay or perpetual timer.
  invalidate();
 }
 private void ellipse(Canvas c,Paint paint,float x,float y,float width,float height){if(width<=0||height<=0)return;int saved=c.save();c.translate(x,y);c.scale(width/2,height/2);c.drawCircle(0,0,1,paint);c.restoreToCount(saved);}
 @Override protected void onDraw(Canvas c){
  if(ink==null||card.isEmpty())return;
  if(poolEnabled){
   // Cached, low-strength colored footprints. Only geometry follows the existing card animation.
   for(int i=0;i<count;i++){Ink n=neighborInk[i];if(n==null)continue;RectF r=neighbors[i];n.pool.setAlpha(alpha(CinemaLighting.NONFOCUS_GLOW_STRENGTH*neighborStrength[i],poolGain));ellipse(c,n.pool,r.centerX(),r.bottom,r.width()*1.12f,r.height()*.64f);}
   ink.pool.setAlpha(alpha(CinemaLighting.SPILL_STRENGTH,poolGain));ellipse(c,ink.pool,poolX,poolY,card.width()*1.95f,card.height()*1.9f);
   ink.pool.setAlpha(alpha(CinemaLighting.FOCUS_GLOW_STRENGTH*(.75f+.25f*progress),poolGain));
   ellipse(c,ink.pool,poolX,poolY,card.width()*CinemaLighting.GLOW_WIDTH_RATIO,card.height()*CinemaLighting.GLOW_HEIGHT_RATIO*2);
   // Four-pixel source padding maps exactly outside the 96x72 inner boundary.
   haloRect.set(card);haloRect.inset(-card.width()/24f,-card.height()/18f);ink.halo.setAlpha(alpha(CinemaLighting.FOCUS_BORDER_STRENGTH,contactGain));c.drawBitmap(ink.rim,null,haloRect,ink.halo);
  }
  if(poolEnabled&&streaks){columnRect.set(card.centerX()-card.width()*.38f,card.bottom,card.centerX()+card.width()*.38f,card.bottom+card.height()*CinemaLighting.VERTICAL_LIGHT_HEIGHT);ink.reflectionPaint.setAlpha(alpha(CinemaLighting.VERTICAL_LIGHT_STRENGTH,reflectionGain));c.drawBitmap(ink.column,null,columnRect,ink.reflectionPaint);}
  if(reflection!=null){reflection.setAlpha(alpha(CinemaLighting.REFLECTION_ALPHA,reflectionGain));reflection.setBounds((int)(card.centerX()-card.width()*.28f),(int)card.bottom,(int)(card.centerX()+card.width()*.28f),(int)(card.bottom+card.height()*CinemaLighting.REFLECTION_FADE_RATIO));reflection.draw(c);}
  if(poolEnabled){
   ink.spread.setAlpha(alpha(CinemaLighting.CONTACT_SPREAD_STRENGTH,contactGain));ellipse(c,ink.spread,card.centerX(),card.bottom+2*unit,card.width()*CinemaLighting.CONTACT_SPREAD_WIDTH_RATIO,24*unit);
   ink.contact.setAlpha(alpha(CinemaLighting.FOCUS_CONTACT_LIGHT_STRENGTH,contactGain));ellipse(c,ink.contact,card.centerX(),card.bottom+unit,card.width()*CinemaLighting.CONTACT_LIGHT_WIDTH_RATIO,CinemaLighting.CONTACT_LIGHT_HEIGHT*unit);
  }
 }
}
