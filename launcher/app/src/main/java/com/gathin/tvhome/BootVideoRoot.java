package com.gathin.tvhome;

import android.content.Context;
import android.os.Process;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Fixed script parameters, one existing su 0, no shell/UID dialect retry. */
final class BootVideoRoot implements BootVideoTransactions.Runner {
    private final Context context;
    BootVideoRoot(Context context){this.context=context.getApplicationContext();}
    static void auditBeforeAcknowledge(Context context)throws Exception{
        if(!BootVideoInstallActivity.reviewedUnit(android.os.Build.HARDWARE,android.os.Build.FINGERPRINT,
            android.os.Build.VERSION.SDK_INT,Process.myUid(),context.getPackageName()))throw new IOException("Unsupported identity");
        android.system.StructStat parent=android.system.Os.lstat("/data/local");
        android.system.StructStat target=android.system.Os.lstat("/data/local/boot.mp4");
        if(!android.system.OsConstants.S_ISDIR(parent.st_mode)||parent.st_uid!=0||parent.st_gid!=0||(parent.st_mode&0777)!=0751
            ||!android.system.OsConstants.S_ISREG(target.st_mode)||target.st_uid!=0||target.st_gid!=0||(target.st_mode&0777)!=0644)
            throw new IOException("Unexpected metadata");
        try{android.system.Os.lstat("/data/local/cinema-lite-boot-video-v1");throw new IOException("Root transaction directory exists");}
        catch(android.system.ErrnoException absent){if(absent.errno!=android.system.OsConstants.ENOENT)throw absent;}
        if(!BootAssetStore.hash(new File("/data/local/boot.mp4")).equals("a95c90c4f55760bbf83ff1ffe76ce5b90ebed9a1fdd296f57da24c1886c6ded7")
            ||!BootAssetStore.hash(new File("/system/media/boot.mp4")).equals("4aa476a6a1c834c5202a3be48ee8452315348bc208026e7bcd610b3a303cb26e"))
            throw new IOException("Baseline changed");
    }
    static boolean uuid(String s){return s!=null&&s.matches("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}");}
    static boolean sha(String s){return s!=null&&s.matches("[a-f0-9]{64}");}
    static String script(String template,BootVideoTransactions.Request request)throws IOException {
        if(!uuid(request.id)||!sha(request.sha)||!BootDeviceSupport.appUid(request.clientUid))throw new IOException("Invalid fixed adapter parameters");
        String source="-";
        if(request.apply){
            source=request.source;
            if(source==null||!source.matches("/data/(user/0|data)/com\\.gathin\\.tvhome\\.cinemalite\\.dev/files/boot_asset_preparation/[a-f0-9-]{36}/prepared\\.mp4"))throw new IOException("Untrusted source path");
            String id=source.substring(source.indexOf("boot_asset_preparation/")+23,source.lastIndexOf('/'));
            if(!uuid(id))throw new IOException("Invalid asset UUID");
        }
        return template.replace("@TX@",request.id).replace("@OP@",request.apply?"APPLY":"RESTORE")
            .replace("@SOURCE@",source).replace("@SHA@",request.sha).replace("@UID@",Integer.toString(request.clientUid));
    }
    static String propertyScript(String template,BootVideoTransactions.Request request,String property)throws IOException{
        if(!BootVideoRoute.propertyPath(property)||!BootVideoRoute.PROPERTY.equals(request.route))throw new IOException("Invalid property route");
        if(!request.apply&&(!BootVideoRoute.ACTIVE.equals(property)||!BootVideoRoot.sha(request.restoreBefore)||!BootVideoRoute.propertyPath(request.restoreProperty)||!BootVideoRoot.sha(request.restoreStock)))throw new IOException("Invalid saved property restore binding");
        return script(template,request).replace("@EXPECTED_PROPERTY@",property)
            .replace("@RESTORE_BEFORE@",request.apply?"-":request.restoreBefore)
            .replace("@RESTORE_PROPERTY@",request.apply?"-":request.restoreProperty)
            .replace("@RESTORE_STOCK@",request.apply?"-":request.restoreStock);
    }
    @Override public BootVideoTransactions.Result run(BootVideoTransactions.Request request)throws Exception {
        if(request.clientUid!=Process.myUid()||!BootDeviceSupport.rootMaterials(android.os.Build.VERSION.SDK_INT,Process.myUid(),context.getPackageName()))throw new IOException("Unsupported Launcher identity");
        if(request.apply){
            File file=new File(request.source);
            if(!file.getCanonicalPath().equals(request.source)||file.length()<=0||file.length()>BootAssetStore.MAX_BYTES||!BootAssetStore.hash(file).equals(request.sha))throw new IOException("Prepared video changed");
            File check=File.createTempFile("boot-video-recheck-",".png",context.getCacheDir());
            try{BootVideoValidator.validate(file,check,()->false);}finally{check.delete();}
        }
        String route=request.route,property="";
        if(BootVideoRoute.AUTO.equals(route)){
            property=BootVideoRoute.readProperty();route=BootVideoRoute.select(property,new File("/data/local/boot.mp4").isFile());
        }else if(BootVideoRoute.PROPERTY.equals(route))property=BootVideoRoute.readProperty();
        else if(request.apply&&BootVideoRoute.LOCAL.equals(route)&&!BootVideoRoute.readProperty().isEmpty())throw new IOException("Existing video route changed; no Root sent");
        if(!BootVideoRoute.known(route))throw new IOException("Unsupported video route");
        if(request.apply&&BootVideoRoute.PROPERTY.equals(route)&&!BootVideoReaderProfile.supported())throw new IOException("Unverified firmware reader contract; no Root sent");
        BootVideoTransactions.Request resolved=new BootVideoTransactions.Request(request.apply,request.id,request.sha,request.source,request.clientUid,route,request.restoreBefore,request.restoreProperty,request.restoreStock);
        String template;
        try(java.io.InputStream in=context.getAssets().open(BootVideoRoute.PROPERTY.equals(route)?"cinema-boot-video-property.sh":"cinema-boot-video.sh")){
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] bytes=new byte[4096];int count;
            while((count=in.read(bytes))!=-1)out.write(bytes,0,count);
            template=new String(out.toByteArray(),StandardCharsets.UTF_8);
        }
        String payload=BootVideoRoute.PROPERTY.equals(route)?propertyScript(template,resolved,property):script(template,resolved);
        String su=CinemaAdbProcess.findSu();
        if(su==null)throw new IOException("No existing su; no Root command sent");
        BootVideoTransactions.Result result=execute(su,payload);
        if(request.apply&&BootVideoRoute.PROPERTY.equals(route)&&result.exit==0&&!result.timeout&&!BootVideoReaderProfile.proof(result))
            return new BootVideoTransactions.Result(-1,false,result.output+"\nCLBV_READER_PROOF_INVALID\n");
        return result;
    }
    static BootVideoTransactions.Result execute(String su,String payload)throws Exception{
        return execute(su,payload,45000);
    }
    static BootVideoTransactions.Result execute(String su,String payload,long timeoutMs)throws Exception{
        java.lang.Process process=new ProcessBuilder(su,"0").redirectErrorStream(true).start();
        StringBuilder output=new StringBuilder();
        Thread reader=new Thread(()->{try(java.io.InputStream in=process.getInputStream()){
            byte[] bytes=new byte[512];int count;while((count=in.read(bytes))!=-1)synchronized(output){if(output.length()<8000)output.append(new String(bytes,0,Math.min(count,8000-output.length()),StandardCharsets.UTF_8));}
        }catch(IOException ignored){}},"boot-video-root-output");reader.setDaemon(true);reader.start();
        Thread writer=new Thread(()->{try(java.io.OutputStream out=process.getOutputStream()){out.write(payload.getBytes(StandardCharsets.UTF_8));}catch(IOException ignored){}},"boot-video-root-input");writer.setDaemon(true);writer.start();
        try{
            boolean finished=process.waitFor(timeoutMs,java.util.concurrent.TimeUnit.MILLISECONDS);
            reader.join(300);
            synchronized(output){return new BootVideoTransactions.Result(finished?process.exitValue():-1,!finished,output.toString());}
        }finally{
            // A setuid child may outlive cancellation. Never block the result on pipe cleanup.
            Thread cleanup=new Thread(()->{
                try{if(process.isAlive())process.destroyForcibly();}catch(RuntimeException ignored){}
                try{process.getOutputStream().close();}catch(IOException ignored){}
                try{process.getInputStream().close();}catch(IOException ignored){}
            },"boot-video-root-cleanup");cleanup.setDaemon(true);cleanup.start();
        }
    }
}
