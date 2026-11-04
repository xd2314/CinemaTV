package com.gathin.tvhome;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import java.io.*;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.UUID;
import org.json.JSONObject;

/** Copies an already verified read-only backup. No Root, transaction setters or partition access. */
final class BootLogoProbeExport {
    interface Sink { OutputStream open()throws Exception; InputStream read()throws Exception; JSONObject publish()throws Exception; }
    private BootLogoProbeExport(){}
    static boolean eligible(String receipt,String probe,int uid){
        try{
            if(!BootLogoReadProbe.eligible(receipt,uid))return false;
            JSONObject p=new JSONObject(probe),r=p.getJSONObject("result"),original=p.getJSONObject("originalReceipt");
            BootLogoTransactions.ReadResult proof=new BootLogoTransactions.ReadResult(r.getString("sha"),r.getString("metadata"),r.getString("diagnostic"),uid,r.getString("device"));
            return p.getString("state").equals("FINISHED")&&p.getBoolean("readOnly")&&p.getString("transaction").equals(original.getString("transaction"))
                &&original.toString().equals(new JSONObject(receipt).toString())&&r.getLong("bytes")==BootLogoLayout.PHYSICAL_BYTES&&r.getBoolean("layoutSupported")&&proof.valid();
        }catch(Exception invalid){return false;}
    }
    static String validate(File file,String receipt,String probe,int uid)throws Exception{
        if(!eligible(receipt,probe,uid))throw new IOException("Read-only proof does not match original receipt");
        String sha=new JSONObject(probe).getJSONObject("result").getString("sha");
        if(!file.isFile()||Files.isSymbolicLink(file.toPath())||file.length()!=BootLogoLayout.PHYSICAL_BYTES||!BootAssetStore.hash(file).equals(sha))throw new IOException("Verified resource backup changed");
        BootLogoLayout.parse(file);return sha;
    }
    static String digest(InputStream input)throws Exception{
        try(InputStream in=input){if(in==null)throw new IOException("No export readback stream");MessageDigest h=MessageDigest.getInstance("SHA-256");long size=0;byte[] b=new byte[65536];int n;
            while((n=in.read(b))!=-1){size+=n;if(size>BootLogoLayout.PHYSICAL_BYTES)throw new IOException("Export exceeds verified resource size");h.update(b,0,n);}
            if(size!=BootLogoLayout.PHYSICAL_BYTES)throw new IOException("Short exported resource");return BootWallpaperBackup.hex(h.digest());}
    }
    static JSONObject copy(File file,String receipt,String probe,int uid,Sink sink)throws Exception{
        String sha=validate(file,receipt,probe,uid);long size=0;MessageDigest h=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file);OutputStream out=sink.open()){
            if(out==null)throw new IOException("No export output stream");byte[] b=new byte[65536];int n;
            while((n=in.read(b))!=-1){size+=n;if(size>BootLogoLayout.PHYSICAL_BYTES)throw new IOException("Resource grew during export");h.update(b,0,n);out.write(b,0,n);}out.flush();
        }
        if(size!=BootLogoLayout.PHYSICAL_BYTES||!sha.equals(BootWallpaperBackup.hex(h.digest()))||!sha.equals(digest(sink.read())))throw new IOException("Export readback mismatch");
        JSONObject result=sink.publish();
        if(!sha.equals(digest(sink.read())))throw new IOException("Published resource changed");
        return result.put("bytes",size).put("sha",sha).put("readOnlyBackup",true);
    }
    static JSONObject run(Context context,String receipt,String probe)throws Exception{
        if(android.os.Build.VERSION.SDK_INT<29||android.os.Build.VERSION.SDK_INT>32)throw new IOException("Downloads export requires Android10-12");
        String id=new JSONObject(receipt).getString("transaction");
        File file=new File(new File(new File(context.getFilesDir(),"boot_logo_read_probe_v1"),id),"before.img");
        return copy(file,receipt,probe,android.os.Process.myUid(),new Sink(){
            Uri uri;final String name="cinema-bootlogo-read-"+id+"-"+UUID.randomUUID()+".img";
            public OutputStream open()throws Exception{
                if(android.os.Build.VERSION.SDK_INT<29)throw new IOException("Downloads API unavailable");
                ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.DISPLAY_NAME,name);v.put(MediaStore.MediaColumns.MIME_TYPE,"application/octet-stream");
                v.put(MediaStore.MediaColumns.RELATIVE_PATH,Environment.DIRECTORY_DOWNLOADS+"/CinemaTV-backups/");v.put(MediaStore.MediaColumns.IS_PENDING,1);
                uri=context.getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI,v);if(uri==null)throw new IOException("No Downloads destination");
                return context.getContentResolver().openOutputStream(uri,"w");
            }
            public InputStream read()throws Exception{return context.getContentResolver().openInputStream(uri);}
            public JSONObject publish()throws Exception{
                ContentValues v=new ContentValues();v.put(MediaStore.MediaColumns.IS_PENDING,0);
                if(context.getContentResolver().update(uri,v,null,null)!=1)throw new IOException("Cannot publish verified backup");
                return new JSONObject().put("uri",uri.toString()).put("filename",name).put("directory",Environment.DIRECTORY_DOWNLOADS+"/CinemaTV-backups/");
            }
        });
    }
}
