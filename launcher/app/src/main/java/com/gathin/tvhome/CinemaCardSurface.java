package com.gathin.tvhome;
import android.graphics.*;
import android.graphics.drawable.Drawable;
/** Four cached glass layers. No live backdrop blur and no shaders allocated while drawing. */
final class CinemaCardSurface extends Drawable {
 private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
 private final RectF face=new RectF(),innerRect=new RectF();
 private final int accent;private Shader body,specular,edge,inner;private float unit,radius,focus;private boolean lighting=true;
 CinemaCardSurface(int color){accent=color;if(CinemaProbe.enabled)CinemaProbe.surfaces++;}
 void lighting(boolean enabled){if(lighting!=enabled){lighting=enabled;invalidateSelf();}}
 void focus(float level){if(Math.abs(level-focus)<.001f)return;focus=level;invalidateSelf();}
 private int tint(int base,float amount,int alpha){return Color.argb(alpha,Math.round(Color.red(base)*(1-amount)+Color.red(accent)*amount),Math.round(Color.green(base)*(1-amount)+Color.green(accent)*amount),Math.round(Color.blue(base)*(1-amount)+Color.blue(accent)*amount));}
 @Override protected void onBoundsChange(Rect b){
  unit=b.width()/CinemaCarousel.CARD_WIDTH;radius=22*unit;face.set(b.left+unit,b.top+unit,b.right-unit,b.bottom-unit);innerRect.set(face);innerRect.inset(1.5f*unit,1.5f*unit);
  body=new LinearGradient(0,0,b.width()*.40f,b.height(),new int[]{tint(0xFF4B4C55,.09f,229),tint(0xFF161923,.08f,239),tint(0xFF080F17,.10f,240),tint(0xFF16222A,.34f,225)},new float[]{0,.32f,.69f,1},Shader.TileMode.CLAMP);
  // A curved reflection fades inside the rounded face; never a white rectangular overlay.
  specular=new RadialGradient(b.width()*.09f,b.height()*.01f,b.width()*.92f,new int[]{0xB8FFFFFF,0x45F6E9DE,0x0BF0F4FF,0},new float[]{0,.25f,.63f,1},Shader.TileMode.CLAMP);
  int rgb=accent&0xFFFFFF;
  edge=new LinearGradient(0,0,0,b.height(),new int[]{0xB8FFF4E5,rgb|0x68000000,rgb|0xA0000000,rgb|0xFF000000},new float[]{0,.37f,.78f,1},Shader.TileMode.CLAMP);
  inner=new LinearGradient(0,0,b.width()*.32f,b.height(),new int[]{0xE8FFFFFF,0x38FFFFFF,0x08FFFFFF,tint(0xFFFFFFFF,.45f,160)},new float[]{0,.32f,.72f,1},Shader.TileMode.CLAMP);
 }
 @Override public void draw(Canvas c){
  if(body==null)return;paint.setColor(Color.WHITE);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);paint.setShader(body);c.drawRoundRect(face,radius,radius,paint);
  paint.setShader(specular);paint.setAlpha(Math.round(135+70*focus));c.drawRoundRect(face,radius,radius,paint);
  // Main tinted perimeter plus a fine inner rim establish material thickness.
  paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(unit*(.9f+1.1f*focus));paint.setShader(edge);paint.setAlpha(lighting?Math.round(125+130*focus*CinemaLighting.FOCUS_BORDER_STRENGTH):100);c.drawRoundRect(face,radius,radius,paint);
  paint.setShader(inner);paint.setAlpha(Math.round(95+100*focus));paint.setStrokeWidth(.7f*unit);c.drawRoundRect(innerRect,radius-unit,radius-unit,paint);
  paint.setShader(null);paint.setAlpha(255);paint.setStyle(Paint.Style.FILL);
 }
 @Override public void setAlpha(int a){} @Override public void setColorFilter(ColorFilter f){} @Override public int getOpacity(){return PixelFormat.TRANSLUCENT;}
}
