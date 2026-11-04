package com.gathin.tvhome;

import android.graphics.*;
import android.graphics.drawable.Drawable;
import java.util.LinkedHashMap;
import java.util.Map;

/** Weighted dominant hue, not RGB average. Samples only on icon identity changes/bind. */
final class CinemaPalette {
    static final int NEUTRAL=0xFF91A2B5;
    private static final class CachedColor {final java.lang.ref.WeakReference<Object> identity;final int color;CachedColor(Object i,int c){identity=new java.lang.ref.WeakReference<>(i);color=c;}}
    private final Map<String,CachedColor> cache=new LinkedHashMap<String,CachedColor>(64,.75f,true){
        @Override protected boolean removeEldestEntry(Map.Entry<String,CachedColor> e){return size()>128;}
    };
    int samples;
    int color(String pkg,Drawable icon){
        Object identity=icon==null?null:icon.getConstantState()!=null?icon.getConstantState():icon;
        CachedColor old=cache.get(pkg);if(old!=null&&old.identity.get()==identity)return old.color;
        int result=icon==null?fallback(pkg):sample(icon);cache.put(pkg,new CachedColor(identity,result));return result;
    }
    private int fallback(String pkg){
        // Existing local mappings are a last resort for missing icons, never override icon colors.
        if(pkg.equals("com.google.android.youtube.tv"))return normalize(0xFFE83B44);
        if(pkg.equals("com.ubdata.ublive"))return normalize(0xFFEF8E4F);
        if(pkg.equals("com.ubdata.hdtv"))return normalize(0xFF42BDB6);
        if(pkg.equals("com.ubdata.uvod"))return normalize(0xFF7E8BEB);
        return NEUTRAL;
    }
    static int normalize(int color){float[] hsv=new float[3];Color.colorToHSV(color,hsv);hsv[1]=Math.min(.80f,hsv[1]);hsv[2]=Math.max(.48f,Math.min(.86f,hsv[2]));return Color.HSVToColor(hsv);}
    private int sample(Drawable icon){
        samples++;Bitmap bitmap=Bitmap.createBitmap(32,32,Bitmap.Config.ARGB_8888);
        Rect saved=new Rect(icon.getBounds());
        try{icon.setBounds(0,0,32,32);icon.draw(new Canvas(bitmap));}catch(RuntimeException ignored){bitmap.recycle();return NEUTRAL;}
        finally{icon.setBounds(saved);}
        try{
            float[] weight=new float[36],sat=new float[36],value=new float[36],hsv=new float[3];int[] count=new int[36];int opaque=0;
            for(int y=0;y<32;y++)for(int x=0;x<32;x++){
                int c=bitmap.getPixel(x,y);if(Color.alpha(c)<80)continue;opaque++;
                Color.colorToHSV(c,hsv);if(hsv[2]<.17f||hsv[1]<.23f)continue;
                int bin=Math.min(35,(int)(hsv[0]/10));float w=(Color.alpha(c)/255f)*(.5f+.3f*hsv[1]+.2f*hsv[2]);
                count[bin]++;weight[bin]+=w;sat[bin]+=hsv[1]*w;value[bin]+=hsv[2]*w;
            }
            int winner=-1;float best=0;
            for(int i=0;i<36;i++){
                int l=(i+35)%36,r=(i+1)%36;int population=count[l]+count[i]+count[r];
                float score=weight[i]+.65f*(weight[l]+weight[r]);
                if(population>=Math.max(6,opaque*.025f)&&score>best){winner=i;best=score;}
            }
            if(winner<0)return NEUTRAL;
            // Circular mean within the winning neighboring bins preserves reds around 0/360.
            float sin=0,cos=0,s=0,v=0,w=0;
            for(int d=-1;d<=1;d++){int b=(winner+d+36)%36;double angle=Math.toRadians(b*10+5);sin+=Math.sin(angle)*weight[b];cos+=Math.cos(angle)*weight[b];s+=sat[b];v+=value[b];w+=weight[b];}
            hsv[0]=(float)((Math.toDegrees(Math.atan2(sin,cos))+360)%360);hsv[1]=Math.min(.80f,s/w);hsv[2]=Math.max(.48f,Math.min(.86f,v/w));return Color.HSVToColor(hsv);
        }finally{bitmap.recycle();}
    }
    void clear(){cache.clear();}
}
