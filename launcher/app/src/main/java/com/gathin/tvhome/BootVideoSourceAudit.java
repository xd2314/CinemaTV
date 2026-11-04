package com.gathin.tvhome;

import android.system.Os;
import android.system.OsConstants;
import android.system.StructStat;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Ordinary App UID only: inspect the exact source predicates without Root or writes. */
final class BootVideoSourceAudit {
    private BootVideoSourceAudit(){}
    static JSONObject reviewNoWrite(BootAssetStore.Asset asset)throws Exception{
        JSONObject source=inspect(asset);int uid=android.os.Process.myUid();
        if(!source.optBoolean("regular")||source.optInt("uid",-1)!=uid||source.optInt("gid",-1)!=uid||!source.optString("mode").equals("600")
            ||!source.optBoolean("canonicalMatches")||source.optInt("readlinkExit",-1)!=0||!source.optString("actualSha").equals(asset.sha))throw new IOException("Source audit changed");
        String property=BootVideoRoute.readProperty();if(!property.equals(BootVideoRoute.STOCK))throw new IOException("Video property changed");
        for(String path:new String[]{"/data/local/cinema-lite-boot-video-property-v1","/data/local/bootanim"}){
            try{Os.lstat(path);throw new IOException("Root journal or data target already exists");}
            catch(android.system.ErrnoException absent){if(absent.errno!=OsConstants.ENOENT)throw absent;}
        }
        File stock=new File(BootVideoRoute.STOCK);StructStat stat=Os.lstat(stock.getPath());
        if(!OsConstants.S_ISREG(stat.st_mode)||stat.st_uid!=0||stat.st_gid!=0||(stat.st_mode&07777)!=0644||stat.st_size<=0||stat.st_size>BootAssetStore.MAX_BYTES||!stock.getCanonicalPath().equals(stock.getPath()))throw new IOException("Stock metadata changed");
        return new JSONObject().put("readOnly",true).put("source",source).put("property",property).put("stockSha",BootAssetStore.hash(stock))
            .put("newRootJournalAbsent",true).put("dataTargetDirectoryAbsent",true).put("at",System.currentTimeMillis());
    }
    static JSONObject inspect(BootAssetStore.Asset asset)throws Exception{
        JSONObject result=new JSONObject().put("appUid",android.os.Process.myUid()).put("expectedSha",asset.sha);
        File file=asset.output;
        try{
            String source=file.getCanonicalPath();StructStat stat=Os.lstat(source);
            result.put("path",file.getPath()).put("javaCanonical",source)
                .put("uid",stat.st_uid).put("gid",stat.st_gid).put("mode",Integer.toOctalString(stat.st_mode&07777))
                .put("regular",OsConstants.S_ISREG(stat.st_mode)).put("length",stat.st_size).put("actualSha",BootAssetStore.hash(file));
            java.lang.Process process=new ProcessBuilder("/system/bin/readlink","-f",source).redirectErrorStream(true).start();
            try{
                if(!process.waitFor(2,TimeUnit.SECONDS)){process.destroyForcibly();throw new IOException("Read-only canonical probe timeout");}
                byte[] bytes=new byte[4096];int count;
                try(java.io.InputStream in=process.getInputStream()){count=in.read(bytes);}
                String value=count<0?"":new String(bytes,0,count,StandardCharsets.UTF_8).trim();
                result.put("readlinkExit",process.exitValue()).put("readlinkCanonical",value).put("canonicalMatches",source.equals(value));
            }finally{process.destroy();}
        }catch(Exception error){result.put("error",error.getClass().getSimpleName());}
        return result;
    }
}
