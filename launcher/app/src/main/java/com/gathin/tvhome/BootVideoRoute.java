package com.gathin.tvhome;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Read-only route discovery. No Root, property writes, or automatic fallback after dispatch. */
final class BootVideoRoute {
    static final String AUTO="AUTO",LOCAL="LOCAL_FILE_V1",PROPERTY="PROPERTY_MP4_V1";
    static final String KEY="persist.sys.bootanim.video_file";
    static final String STOCK="/system/media/bootanimation.mp4",ACTIVE="/data/local/bootanim/bootanimation.mp4";
    private BootVideoRoute(){}
    static boolean known(String route){return LOCAL.equals(route)||PROPERTY.equals(route);}
    static boolean propertyPath(String path){return STOCK.equals(path)||ACTIVE.equals(path);}
    static String select(String property,boolean localFile)throws IOException{
        if(propertyPath(property))return PROPERTY;
        if(property.isEmpty()&&localFile)return LOCAL;
        throw new IOException("No supported existing boot-video route");
    }
    static String inspect()throws Exception{
        String property=readProperty(),route=select(property,new File("/data/local/boot.mp4").isFile());
        File current=new File(PROPERTY.equals(route)?property:"/data/local/boot.mp4");
        if(!current.isFile()||!current.getCanonicalPath().equals(current.getPath())||current.length()<=0||current.length()>BootAssetStore.MAX_BYTES)throw new IOException("目前影片無法以普通身分讀取，需人工核對。");
        if(PROPERTY.equals(route)&&BootVideoReaderProfile.protectedTarget(current)&&BootVideoReaderProfile.supported())
            return "已偵測系統媒體服務專用的影片路徑：\n"+property+"\nApp普通身分無法讀取此系統候選；內容SHA以Root交易讀回紀錄為準。這次未要求Root或套用。";
        BootAssetStore.hash(current);
        return (PROPERTY.equals(route)?"已讀取並檢查韌體影片路徑：\n"+property:"已讀取並檢查既有 /data/local/boot.mp4。")+"\n本次僅唯讀，尚未要求 Root 或套用。";
    }
    static String readProperty()throws Exception{
        java.lang.Process process=new ProcessBuilder("/system/bin/getprop",KEY).redirectErrorStream(true).start();
        try{
            if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Property probe timed out; no Root sent");}
            if(process.exitValue()!=0)throw new IOException("Property probe failed; no Root sent");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[256];int count;
            try(java.io.InputStream input=process.getInputStream()){while((count=input.read(bytes))!=-1){if(out.size()+count>4096)throw new IOException("Property result too large");out.write(bytes,0,count);}}
            return out.toString(StandardCharsets.UTF_8.name()).trim();
        }finally{process.destroy();}
    }
}
