package com.gathin.tvhome;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** One existing su 0 per explicit operation. Full read is binary and bounded, never logged. */
final class BootLogoRoot implements BootLogoTransactions.Backend {
    final Context context;
    BootLogoRoot(Context context){this.context=context.getApplicationContext();}
    private void profile()throws IOException{
        if(!BootDeviceSupport.rootMaterials(android.os.Build.VERSION.SDK_INT,android.os.Process.myUid(),context.getPackageName()))throw new IOException("Unreviewed Launcher identity");
    }
    private String template(String name)throws Exception{try(InputStream in=context.getAssets().open(name)){ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return new String(out.toByteArray(),StandardCharsets.UTF_8);}}
    static boolean nodeMeta(String value){return value!=null&&value.matches("[0-9a-f]+:[0-9a-f]+:[0-9]+:[0-9]+:[0-7]{3,4}");}
    static String writeScript(String template,BootLogoTransactions.WriteRequest r)throws Exception{
        if(!BootDeviceSupport.appUid(r.clientUid)||!BootDeviceSupport.blockPath(r.device)||!BootVideoRoot.uuid(r.id)||!BootVideoRoot.sha(r.before)||!BootVideoRoot.sha(r.after)||!BootVideoRoot.sha(r.oldWindow)||!BootVideoRoot.sha(r.newWindow)||!nodeMeta(r.meta)
            ||r.seek<=0||r.count<=0||r.count>6144||(long)r.seek+r.count>65536)throw new IOException("Invalid bounded logo parameters");
        String source="-";
        if(r.apply){source=r.source.getCanonicalPath();String expected="/data/(user/0|data)/com\\.gathin\\.tvhome\\.cinemalite\\.dev/files/boot_logo_transactions/"+r.id+"/candidate-window\\.bin";
            if(!source.matches(expected))throw new IOException("Untrusted logo staging path");}
        return template.replace("@TX@",r.id).replace("@OP@",r.apply?"APPLY":"RESTORE").replace("@SOURCE@",source).replace("@UID@",Integer.toString(r.clientUid))
            .replace("@BEFORE@",r.before).replace("@AFTER@",r.after).replace("@OLD_WINDOW@",r.oldWindow).replace("@NEW_WINDOW@",r.newWindow)
            .replace("@SEEK@",Integer.toString(r.seek)).replace("@COUNT@",Integer.toString(r.count)).replace("@META@",r.meta).replace("@DEVICE@",r.device);
    }
    @Override public BootVideoTransactions.Result write(BootLogoTransactions.WriteRequest request)throws Exception{
        profile();if(request.clientUid!=android.os.Process.myUid())throw new IOException("Wrong caller UID");String script=writeScript(template("cinema-boot-logo-write.sh"),request);String su=CinemaAdbProcess.findSu();if(su==null)throw new IOException("No existing su");
        return BootVideoRoot.execute(su,script);
    }
    @Override public BootLogoTransactions.ReadResult read(File destination)throws Exception{
        profile();String script=template("cinema-boot-logo-read.sh").replace("@UID@",Integer.toString(android.os.Process.myUid()));String su=CinemaAdbProcess.findSu();if(su==null)throw new IOException("No existing su");
        return executeRead(su,script,destination,45000,android.os.Process.myUid());
    }
    static String line(InputStream in)throws IOException{
        ByteArrayOutputStream b=new ByteArrayOutputStream();int c;
        while((c=in.read())!=-1){if(c=='\n')return new String(b.toByteArray(),StandardCharsets.US_ASCII);if(b.size()>=512)throw new IOException("Unbounded Root metadata");b.write(c);}
        throw new EOFException("Incomplete Root read protocol");
    }
    static final class ReadState{Map<String,String> fields=new LinkedHashMap<>();Throwable error;String sha;final CountDownLatch done=new CountDownLatch(1);}
    static void parseRead(InputStream in,File destination,ReadState state){parseRead(in,destination,state,10106);}
    static void parseRead(InputStream in,File destination,ReadState state,int uid){
        try{
            for(int i=0;;i++){String s=line(in);if(s.equals("CLBL_DATA_BEGIN"))break;if(i>=8||!s.contains("="))throw new IOException("Unexpected Root header");
                String[] pair=s.split("=",2);if(!pair[0].matches("CLBL_UID|CLBL_CLIENT_UID|CLBL_DEVICE|CLBL_META|CLBL_BYTES|CLBL_SHA_BEFORE")||state.fields.put(pair[0],pair[1])!=null)throw new IOException("Duplicate or unexpected Root header");}
            if(!"0".equals(state.fields.get("CLBL_UID"))||!BootDeviceSupport.appUid(uid)||!Integer.toString(uid).equals(state.fields.get("CLBL_CLIENT_UID"))||!BootDeviceSupport.blockPath(state.fields.get("CLBL_DEVICE"))
                ||!"33554432".equals(state.fields.get("CLBL_BYTES"))||!nodeMeta(state.fields.get("CLBL_META"))||!BootVideoRoot.sha(state.fields.get("CLBL_SHA_BEFORE")))throw new IOException("Root identity/size proof missing");
            MessageDigest hash=MessageDigest.getInstance("SHA-256");int remaining=BootLogoLayout.PHYSICAL_BYTES;
            try(FileOutputStream out=new FileOutputStream(destination)){byte[] buffer=new byte[65536];while(remaining>0){int n=in.read(buffer,0,Math.min(buffer.length,remaining));if(n<0)throw new EOFException("Short resource read");out.write(buffer,0,n);hash.update(buffer,0,n);remaining-=n;}out.getFD().sync();}
            if(!line(in).isEmpty()||!line(in).equals("CLBL_DATA_END"))throw new IOException("Unbounded resource read");
            String after=line(in),result=line(in);if(!after.startsWith("CLBL_SHA_AFTER=")||!result.equals("CLBL_RESULT=READ_COMPLETE"))throw new IOException("Missing Root read completion");
            state.fields.put("CLBL_SHA_AFTER",after.substring("CLBL_SHA_AFTER=".length()));
            StringBuilder hex=new StringBuilder();for(byte b:hash.digest())hex.append(String.format(java.util.Locale.ROOT,"%02x",b&255));state.sha=hex.toString();
            if(!state.sha.equals(state.fields.get("CLBL_SHA_BEFORE"))||!state.sha.equals(state.fields.get("CLBL_SHA_AFTER")))throw new IOException("Before/after/host SHA mismatch");
            if(in.read()!=-1)throw new IOException("Trailing unexpected Root output");
        }catch(Throwable error){state.error=error;}finally{state.done.countDown();}
    }
    static BootLogoTransactions.ReadResult executeRead(String su,String script,File destination,long timeoutMs)throws Exception{return executeRead(su,script,destination,timeoutMs,10106);}
    static BootLogoTransactions.ReadResult executeRead(String su,String script,File destination,long timeoutMs,int uid)throws Exception{
        if(!destination.createNewFile())throw new IOException("Backup destination already exists");
        java.lang.Process p=new ProcessBuilder(su,"0").redirectErrorStream(true).start();long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeoutMs);ReadState state=new ReadState();
        Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){parseRead(in,destination,state,uid);}catch(Throwable error){state.error=error;state.done.countDown();}},"boot-logo-root-read");reader.setDaemon(true);reader.start();
        Thread writer=new Thread(()->{try(OutputStream out=p.getOutputStream()){out.write(script.getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}},"boot-logo-root-input");writer.setDaemon(true);writer.start();
        try{
            boolean ended=p.waitFor(timeoutMs,TimeUnit.MILLISECONDS);long left=Math.max(0,deadline-System.nanoTime());boolean read=state.done.await(left,TimeUnit.NANOSECONDS);
            if(!ended||!read)throw new IOException("Root read timeout; do not resend");
            if(p.exitValue()!=0||state.error!=null||state.sha==null)throw new IOException("Root read incomplete; do not resend",state.error);
            String meta=state.fields.get("CLBL_META");return new BootLogoTransactions.ReadResult(state.sha,meta,"CLBL_UID=0\nCLBL_CLIENT_UID="+uid+"\nCLBL_BYTES=33554432\nCLBL_SHA_BEFORE="+state.sha+"\nCLBL_SHA_AFTER="+state.sha+"\nCLBL_RESULT=READ_COMPLETE\n",uid,state.fields.get("CLBL_DEVICE"));
        }finally{
            Thread cleanup=new Thread(()->{try{if(p.isAlive())p.destroyForcibly();}catch(RuntimeException ignored){}try{p.getOutputStream().close();}catch(IOException ignored){}try{p.getInputStream().close();}catch(IOException ignored){}},"boot-logo-root-cleanup");cleanup.setDaemon(true);cleanup.start();
        }
    }
}
