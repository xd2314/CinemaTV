package com.gathin.tvhome;

import android.app.ActivityManager;
import android.content.Context;
/** Conservative UBOX9/Android10 image budgets; actual App heap is read, never inferred from total RAM. */
final class CinemaMemoryPolicy {
    static final int MAX_CARD_ASSETS=32,MAX_LIGHT_INKS=32;
    static final long THUMBNAIL_CACHE_BYTES=4L*1024*1024;
    static int[] background(int width,int height,int heapMiB,boolean lowRam){
        int capW=lowRam||heapMiB<=128?1280:1920,capH=lowRam||heapMiB<=128?720:1080;
        float scale=Math.min(1,Math.min(capW/(float)Math.max(1,width),capH/(float)Math.max(1,height)));
        return new int[]{Math.max(1,Math.round(width*scale)),Math.max(1,Math.round(height*scale))};
    }
    static int[] background(Context c,int width,int height){ActivityManager m=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
        return background(width,height,m==null?256:m.getMemoryClass(),m!=null&&m.isLowRamDevice());}
    static void requireAllocationBudget(long bytes)throws java.io.IOException{Runtime r=Runtime.getRuntime();long available=r.maxMemory();if(bytes<0||available<bytes+4L*1024*1024)throw new java.io.IOException("此素材的單次記憶體需求超過 App 上限；原素材保留，請改用較小圖片。");}
    private CinemaMemoryPolicy(){}
}
