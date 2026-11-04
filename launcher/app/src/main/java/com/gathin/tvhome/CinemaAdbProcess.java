package com.gathin.tvhome;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Fixed commands, bounded output/time, no user-supplied shell text and no su dialect retry. */
final class CinemaAdbProcess implements CinemaAdbAccess.Runner {
    static final String ENABLE_SCRIPT=
        "uid=$(/system/bin/id -u) || exit 10\n"+
        "[ \"$uid\" = 0 ] || exit 11\n"+
        "/system/bin/setprop service.adb.tcp.port 5555 || exit 20\n"+
        "echo CYT_PORT_OK\n"+
        "/system/bin/stop adbd || exit 21\n"+
        "echo CYT_STOP_OK\n"+
        "/system/bin/start adbd || exit 22\n"+
        "echo CYT_START_OK\nexit 0\n";
    static String findSu(){
        for(String path:new String[]{"/system/xbin/su","/system/bin/su","/sbin/su","/su/bin/su"})if(new File(path).canExecute())return path;
        return null;
    }
    @Override public CinemaAdbAccess.Result run(CinemaAdbAccess.Action action)throws Exception{
        String su=findSu();
        if(su==null)return new CinemaAdbAccess.Result(11,false,"找不到可執行的既有 su。未安裝任何 Root 工具。");
        return execute(new String[]{su,"0"},ENABLE_SCRIPT,45000);
    }
    static CinemaAdbAccess.Result execute(String[] command,String script,long timeoutMs)throws Exception{
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        BoundedOutput output=new BoundedOutput();
        Thread reader=new Thread(()->{try(InputStream in=process.getInputStream()){byte[] bytes=new byte[1024];int count;while((count=in.read(bytes))!=-1)output.add(bytes,count);}catch(IOException ignored){}} ,"cinema-adb-output");
        reader.setDaemon(true);reader.start();
        try {
            try(OutputStream input=process.getOutputStream()){input.write(script.getBytes(StandardCharsets.UTF_8));}
            if(!process.waitFor(timeoutMs,TimeUnit.MILLISECONDS)){
                process.destroyForcibly();reader.join(300);
                return new CinemaAdbAccess.Result(-1,true,output.text());
            }
            reader.join(300);return new CinemaAdbAccess.Result(process.exitValue(),false,output.text());
        }finally{
            if(process.isAlive())process.destroyForcibly();
            try{process.getInputStream().close();}catch(IOException ignored){}
        }
    }
    private static final class BoundedOutput {
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        synchronized void add(byte[] data,int count){int remaining=6000-bytes.size();if(remaining>0)bytes.write(data,0,Math.min(count,remaining));}
        synchronized String text(){return new String(bytes.toByteArray(),StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}&&[^\\n\\t]]","");}
    }
}
