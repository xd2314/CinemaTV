package com.gathin.tvhome;

import java.io.IOException;
import java.io.File;
import java.io.RandomAccessFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.util.Arrays;

/** Offline FAT16/BMP inspection. All ranges come from the supplied full backup; no device I/O. */
final class BootLogoLayout {
    static final int PHYSICAL_BYTES=32*1024*1024,WIDTH=1280,HEIGHT=720,RGB_BYTES=WIDTH*HEIGHT*3;
    final int entry,fileStart,fileBytes,pixelStart,pixelEnd,windowStart,windowEnd,firstCluster,lastCluster;
    final long declaredVolumeBytes;
    private BootLogoLayout(int entry,int fileStart,int fileBytes,int first,int last,long volume){
        this.entry=entry;this.fileStart=fileStart;this.fileBytes=fileBytes;this.firstCluster=first;this.lastCluster=last;declaredVolumeBytes=volume;
        pixelStart=fileStart+54;pixelEnd=pixelStart+RGB_BYTES;windowStart=pixelStart/512*512;windowEnd=(pixelEnd+511)/512*512;
    }
    static int u16(ByteBuffer b,int at){return Short.toUnsignedInt(b.getShort(at));}
    static long u32(ByteBuffer b,int at){return Integer.toUnsignedLong(b.getInt(at));}
    private interface Bytes { int length(); byte get(int at)throws IOException; }
    private static int u16(Bytes b,int at)throws IOException{return (b.get(at)&255)|((b.get(at+1)&255)<<8);}
    private static long u32(Bytes b,int at)throws IOException{return Integer.toUnsignedLong(u16(b,at)|(u16(b,at+2)<<16));}
    /** Four 4KiB pages; no full-partition byte array or mapped 32MiB region. */
    private static final class FileBytes implements Bytes,AutoCloseable {
        final RandomAccessFile file;
        final Map<Integer,byte[]> pages=new LinkedHashMap<Integer,byte[]>(4,.75f,true){
            @Override protected boolean removeEldestEntry(Map.Entry<Integer,byte[]> e){return size()>4;}
        };
        FileBytes(File source)throws IOException{file=new RandomAccessFile(source,"r");if(file.length()!=PHYSICAL_BYTES){file.close();throw new IOException("Invalid full resource backup");}}
        public int length(){return PHYSICAL_BYTES;}
        public byte get(int at)throws IOException{
            if(at<0||at>=PHYSICAL_BYTES)throw new IOException("Resource offset outside backup");
            int page=at/4096;byte[] data=pages.get(page);
            if(data==null){data=new byte[4096];file.seek((long)page*4096);file.readFully(data);pages.put(page,data);}
            return data[at%4096];
        }
        public void close()throws IOException{file.close();pages.clear();}
    }
    static BootLogoLayout parse(byte[] bytes)throws IOException{
        return parse(new Bytes(){public int length(){return bytes.length;}public byte get(int at){return bytes[at];}});
    }
    static BootLogoLayout parse(File source)throws IOException{try(FileBytes bytes=new FileBytes(source)){return parse(bytes);}}
    private static BootLogoLayout parse(Bytes bytes)throws IOException {
        if(bytes.length()!=PHYSICAL_BYTES||bytes.get(510)!=(byte)0x55||bytes.get(511)!=(byte)0xaa)throw new IOException("Invalid full resource backup");
        int sector=u16(bytes,11),spc=Byte.toUnsignedInt(bytes.get(13)),reserved=u16(bytes,14),fats=Byte.toUnsignedInt(bytes.get(16)),entries=u16(bytes,17),fatSectors=u16(bytes,22);
        long total=u16(bytes,19);if(total==0)total=u32(bytes,32);
        if(sector!=512||spc==0||spc>64||(spc&(spc-1))!=0||reserved==0||fats!=2||entries==0||fatSectors==0||total*sector!=128L*1024*1024)throw new IOException("Unreviewed FAT16 geometry");
        long fatStartLong=(long)reserved*sector,fatBytesLong=(long)fatSectors*sector,rootLong=fatStartLong+2*fatBytesLong;
        long dataLong=rootLong+((entries*32L+sector-1)/sector)*sector;
        if(fatBytesLong<4||fatBytesLong>131072||rootLong+entries*32L>bytes.length()||dataLong>=bytes.length())throw new IOException("FAT/root outside physical backup");
        int fatStart=(int)fatStartLong,fatBytes=(int)fatBytesLong,root=(int)rootLong,data=(int)dataLong,clusterBytes=sector*spc;
        for(int i=0;i<fatBytes;i++)if(bytes.get(fatStart+i)!=bytes.get(fatStart+fatBytes+i))throw new IOException("FAT copies differ");
        int entry=-1;byte[] name={'B','O','O','T','L','O','G','O','B','M','P'};
        for(int at=root;at<root+entries*32;at+=32){
            if(bytes.get(at)==0)break;if(bytes.get(at)==(byte)0xe5||bytes.get(at+11)==0x0f)continue;
            boolean match=true;for(int i=0;i<11;i++)if(bytes.get(at+i)!=name[i])match=false;
            if(match){if(entry!=-1||(bytes.get(at+11)&0x18)!=0)throw new IOException("Ambiguous or non-file BOOTLOGO.BMP");entry=at;}
        }
        if(entry<0)throw new IOException("No unique BOOTLOGO.BMP");
        long sizeLong=u32(bytes,entry+28);if(sizeLong<54L+RGB_BYTES||sizeLong>3L*1024*1024)throw new IOException("Unexpected BMP allocation size");
        int size=(int)sizeLong,first=u16(bytes,entry+26),cluster=first,count=0,start=-1,last=-1,previous=-1;
        boolean[] seen=new boolean[fatBytes/2];
        while(cluster>=2&&cluster<0xfff8){
            if(cluster>=seen.length||seen[cluster]||cluster>=0xfff0)throw new IOException("Invalid or cyclic FAT chain");seen[cluster]=true;
            long offset=(long)data+(cluster-2L)*clusterBytes;
            if(offset<0||offset+clusterBytes>bytes.length()||(previous>=0&&offset!=(long)previous+clusterBytes))throw new IOException("Non-contiguous or out-of-bounds resource chain");
            if(start<0)start=(int)offset;previous=(int)offset;last=cluster;count++;
            if(count>(size+clusterBytes-1)/clusterBytes)throw new IOException("Oversized FAT chain");cluster=u16(bytes,fatStart+2*cluster);
        }
        if(cluster<0xfff8||count!=(size+clusterBytes-1)/clusterBytes||start<0||start+(long)size>bytes.length())throw new IOException("Incomplete FAT chain");
        if(bytes.get(start)!='B'||bytes.get(start+1)!='M'||u32(bytes,start+14)!=40||u32(bytes,start+10)!=54||(int)u32(bytes,start+18)!=WIDTH||(int)u32(bytes,start+22)!=HEIGHT
            ||u16(bytes,start+26)!=1||u16(bytes,start+28)!=24||u32(bytes,start+30)!=0)throw new IOException("Unreviewed BMP header");
        long reported=u32(bytes,start+2),pixels=u32(bytes,start+34);
        // This unit's vendor BMP counts its two trailing bytes in biSizeImage.
        // Preserve those bytes and the header exactly; they are not pixel data.
        boolean vendorTrailer=pixels==RGB_BYTES+2L&&size==54+RGB_BYTES+2;
        if(reported<54L+RGB_BYTES||reported>size||(pixels!=0&&pixels!=RGB_BYTES&&!vendorTrailer))throw new IOException("BMP size fields inconsistent");
        BootLogoLayout layout=new BootLogoLayout(entry,start,size,first,last,total*sector);
        if(layout.windowEnd>bytes.length()||layout.windowStart<0||layout.windowEnd-layout.windowStart>3*1024*1024)throw new IOException("Unbounded write window");
        return layout;
        }
    static String sha(byte[] bytes)throws Exception{byte[] digest=MessageDigest.getInstance("SHA-256").digest(bytes);StringBuilder out=new StringBuilder();for(byte b:digest)out.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return out.toString();}
    static final class Candidate {
        final BootLogoLayout layout;final byte[] originalWindow,candidateWindow;final String beforeSha,afterSha,originalWindowSha,candidateWindowSha;
        Candidate(BootLogoLayout layout,byte[] originalWindow,byte[] candidateWindow,String before,String after)throws Exception{
            this.layout=layout;this.originalWindow=originalWindow;this.candidateWindow=candidateWindow;beforeSha=before;afterSha=after;
            originalWindowSha=sha(originalWindow);candidateWindowSha=sha(candidateWindow);
        }
        Candidate(BootLogoLayout layout,byte[] original,byte[] candidate)throws Exception{
            this.layout=layout;beforeSha=sha(original);afterSha=sha(candidate);
            originalWindow=Arrays.copyOfRange(original,layout.windowStart,layout.windowEnd);candidateWindow=Arrays.copyOfRange(candidate,layout.windowStart,layout.windowEnd);
            originalWindowSha=sha(originalWindow);candidateWindowSha=sha(candidateWindow);
        }
    }
    static Candidate build(byte[] original,byte[] topDownRgb)throws Exception{
        BootLogoLayout layout=parse(original);if(topDownRgb.length!=RGB_BYTES)throw new IOException("Requires normalized opaque 1280x720 RGB");
        byte[] candidate=original.clone();
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int from=(y*WIDTH+x)*3,to=layout.pixelStart+((HEIGHT-1-y)*WIDTH+x)*3;
            candidate[to]=topDownRgb[from+2];candidate[to+1]=topDownRgb[from+1];candidate[to+2]=topDownRgb[from];
        }
        parse(candidate);return new Candidate(layout,original,candidate);
    }
    /** Same FAT/BMP guards and exact hashes as build(byte[]), with only the derived window in heap. */
    static Candidate build(File original,byte[] topDownRgb)throws Exception{
        if(topDownRgb.length!=RGB_BYTES)throw new IOException("Requires normalized opaque 1280x720 RGB");
        final BootLogoLayout layout=parse(original);
        Runtime runtime=Runtime.getRuntime();if(runtime.maxMemory()<2L*(layout.windowEnd-layout.windowStart)+2L*1024*1024)throw new IOException("Candidate allocation exceeds maximum App heap");
        final byte[] beforeWindow=new byte[layout.windowEnd-layout.windowStart];
        try(RandomAccessFile input=new RandomAccessFile(original,"r")){input.seek(layout.windowStart);input.readFully(beforeWindow);}
        final byte[] next=beforeWindow.clone();
        for(int y=0;y<HEIGHT;y++)for(int x=0;x<WIDTH;x++){
            int from=(y*WIDTH+x)*3,to=layout.pixelStart-layout.windowStart+((HEIGHT-1-y)*WIDTH+x)*3;
            next[to]=topDownRgb[from+2];next[to+1]=topDownRgb[from+1];next[to+2]=topDownRgb[from];
        }
        try(FileBytes source=new FileBytes(original)){
            parse(new Bytes(){public int length(){return PHYSICAL_BYTES;}public byte get(int at)throws IOException{
                return at>=layout.windowStart&&at<layout.windowEnd?next[at-layout.windowStart]:source.get(at);
            }});
        }
        MessageDigest before=MessageDigest.getInstance("SHA-256"),after=MessageDigest.getInstance("SHA-256");
        try(RandomAccessFile input=new RandomAccessFile(original,"r")){
            if(input.length()!=PHYSICAL_BYTES)throw new IOException("Resource size changed");
            byte[] block=new byte[65536];int offset=0;
            while(offset<PHYSICAL_BYTES){int n=Math.min(block.length,PHYSICAL_BYTES-offset);input.readFully(block,0,n);before.update(block,0,n);
                int start=Math.max(offset,layout.windowStart),end=Math.min(offset+n,layout.windowEnd);
                for(int at=start;at<end;at++){
                    if(block[at-offset]!=beforeWindow[at-layout.windowStart])throw new IOException("Resource window changed during parse");
                    block[at-offset]=next[at-layout.windowStart];
                }
                after.update(block,0,n);offset+=n;
            }
            if(input.length()!=PHYSICAL_BYTES)throw new IOException("Resource size changed");
        }
        return new Candidate(layout,beforeWindow,next,hex(before.digest()),hex(after.digest()));
    }
    private static String hex(byte[] digest){StringBuilder s=new StringBuilder();for(byte b:digest)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}

}
