package com.gathin.tvhome;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.zip.*;
import org.json.JSONObject;

/** Local export package, preserving both original image streams and their receipt. No API setters. */
final class BootWallpaperBackup {
    static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    static final class Result{final long bytes;final String sha;Result(long bytes,String sha){this.bytes=bytes;this.sha=sha;}}
    static final class Count extends FilterOutputStream{long total;Count(OutputStream out){super(out);}@Override public void write(int b)throws IOException{out.write(b);total++;if(total>48L*1024*1024)throw new IOException("Backup export too large");}@Override public void write(byte[] b,int off,int len)throws IOException{out.write(b,off,len);total+=len;if(total>48L*1024*1024)throw new IOException("Backup export too large");}@Override public void close()throws IOException{flush();}}
    static Result write(OutputStream output,File[] files,String json)throws Exception{
        if(files.length!=2)throw new IOException();JSONObject receipt=new JSONObject(json);String[] names={"system-wallpaper.image","lock-wallpaper.image"},hashes={receipt.getString("beforeSha"),receipt.optString("beforeLockSha",receipt.getString("lockSha"))};
        byte[] metadata=json.getBytes(StandardCharsets.UTF_8);if(metadata.length>32768)throw new IOException("Receipt too large");Count count=new Count(output);MessageDigest all=MessageDigest.getInstance("SHA-256");
        try(ZipOutputStream zip=new ZipOutputStream(new DigestOutputStream(count,all))){
            for(int i=0;i<2;i++){
                if(!BootVideoRoot.sha(hashes[i])||!BootAssetStore.hash(files[i]).equals(hashes[i]))throw new IOException("Original backup hash mismatch");
                ZipEntry entry=new ZipEntry(names[i]);entry.setTime(0);zip.putNextEntry(entry);MessageDigest content=MessageDigest.getInstance("SHA-256");long bytes=0;
                try(InputStream in=new FileInputStream(files[i])){byte[] buffer=new byte[16384];int n;while((n=in.read(buffer))!=-1){bytes+=n;if(bytes>BootAssetStore.MAX_BYTES)throw new IOException("Original image too large");content.update(buffer,0,n);zip.write(buffer,0,n);}}
                if(bytes==0||!hex(content.digest()).equals(hashes[i]))throw new IOException("Original backup changed during export");zip.closeEntry();
            }
            ZipEntry entry=new ZipEntry("receipt.json");entry.setTime(0);zip.putNextEntry(entry);zip.write(metadata);zip.closeEntry();
        }
        return new Result(count.total,hex(all.digest()));
    }
}
