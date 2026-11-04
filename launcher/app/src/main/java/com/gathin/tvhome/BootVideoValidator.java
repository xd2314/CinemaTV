package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.media.*;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.BooleanSupplier;

/** Strict preview-only format gate; validation never implies the vendor boot player was tested. */
final class BootVideoValidator {
    static final String SPEC="MP4／H.264 High L3.1、1280×720、24fps、5秒、120幀、無音軌、8-bit 4:2:0、faststart";
    static String validate(File file,File poster,BooleanSupplier cancelled)throws Exception{
        container(file);identityMatrices(file);MediaExtractor extractor=new MediaExtractor();
        Bitmap decodedPoster=null;
        try{
            extractor.setDataSource(file.getPath());if(extractor.getTrackCount()!=1)throw new IOException("影片必須只有一條視訊軌，不含音訊或其他軌道");
            MediaFormat format=extractor.getTrackFormat(0);validateFormat(format);extractor.selectTrack(0);
            List<Long> times=new ArrayList<>();ByteBuffer sample=ByteBuffer.allocate(2*1024*1024);
            while(extractor.getSampleTime()>=0){BootAssetStore.checkCancelled(cancelled);if(times.size()>=120)throw new IOException("影片超過 120 幀");
                if((extractor.getSampleFlags()&MediaExtractor.SAMPLE_FLAG_ENCRYPTED)!=0)throw new IOException("不接受加密影片");
                sample.clear();int count=extractor.readSampleData(sample,0);if(count<=0||count>sample.capacity())throw new IOException("影片資料無法完整讀取");
                times.add(extractor.getSampleTime());extractor.advance();
            }
            cadence(times);extractor.seekTo(0,MediaExtractor.SEEK_TO_CLOSEST_SYNC);decodedPoster=BootVideoDecoder.decode(extractor,format,cancelled);
        }finally{extractor.release();}
        try{BootAssetStore.checkCancelled(cancelled);BootAssetStore.savePng(decodedPoster,poster);}
        finally{if(decodedPoster!=null)decodedPoster.recycle();}
        return "1280 × 720 · H.264 High L3.1\n24fps · 5秒 · 120幀 · 無聲\n完整解碼檢查通過；開機播放器相容性仍待實機驗收";
    }
    static void validateFormat(MediaFormat f)throws IOException{
        try{
            if(!"video/avc".equals(f.getString(MediaFormat.KEY_MIME))||f.getInteger(MediaFormat.KEY_WIDTH)!=1280||f.getInteger(MediaFormat.KEY_HEIGHT)!=720)throw new IOException("需要 H.264／1280×720 影片");
            if(!f.containsKey(MediaFormat.KEY_DURATION)||Math.abs(f.getLong(MediaFormat.KEY_DURATION)-5_000_000L)>1000)throw new IOException("影片長度必須為 5 秒");
            if(f.containsKey(MediaFormat.KEY_ROTATION)&&f.getInteger(MediaFormat.KEY_ROTATION)!=0)throw new IOException("影片不可包含旋轉設定");
            if(f.containsKey(MediaFormat.KEY_COLOR_TRANSFER)&&(f.getInteger(MediaFormat.KEY_COLOR_TRANSFER)==MediaFormat.COLOR_TRANSFER_ST2084||f.getInteger(MediaFormat.KEY_COLOR_TRANSFER)==MediaFormat.COLOR_TRANSFER_HLG))throw new IOException("不接受 HDR 影片");
            ByteBuffer csd=f.getByteBuffer("csd-0");if(csd==null||csd.remaining()>65536)throw new IOException("無法確認 H.264 編碼規格");byte[] bytes=new byte[csd.remaining()];csd.duplicate().get(bytes);sps(bytes);
        }catch(IllegalArgumentException|NullPointerException|ClassCastException malformed){throw new IOException("影片格式資料不完整",malformed);}
    }
    static void cadence(List<Long> timestamps)throws IOException{
        if(timestamps.size()!=120)throw new IOException("影片必須為 120 幀");List<Long> sorted=new ArrayList<>(timestamps);Collections.sort(sorted);
        for(int i=0;i<120;i++)if(Math.abs(sorted.get(i)-Math.round(i*1_000_000d/24))>1000)throw new IOException("影片必須為固定 24fps，不能使用可變幀率");
    }
    static void container(File file)throws IOException{
        if(file.length()<=0||file.length()>BootAssetStore.MAX_BYTES)throw new IOException("影片檔案須介於 1 byte 與 20 MiB");
        try(RandomAccessFile in=new RandomAccessFile(file,"r")){
            long position=0,moov=-1,mdat=-1;boolean ftyp=false;int boxes=0;
            while(position<in.length()){
                if(++boxes>4096||in.length()-position<8)throw new IOException("MP4 容器不完整");in.seek(position);long size=in.readInt()&0xffffffffL;byte[] type=new byte[4];in.readFully(type);String kind=new String(type,StandardCharsets.US_ASCII);int header=8;
                if(size==1){if(in.length()-position<16)throw new IOException("MP4 大小欄位無效");size=in.readLong();header=16;}
                if(size==0)size=in.length()-position;if(size<header||size>in.length()-position)throw new IOException("MP4 區塊超出檔案範圍");
                if(kind.equals("ftyp")){
                    if(ftyp||size<header+8||size>1024||(size-header)%4!=0)throw new IOException("MP4 標頭無效");
                    boolean compatible=false;for(int i=0;i<(size-header)/4;i++){byte[] brand=new byte[4];in.readFully(brand);if(i!=1&&Arrays.asList("isom","iso2","iso4","iso5","iso6","mp41","mp42","avc1").contains(new String(brand,StandardCharsets.US_ASCII)))compatible=true;}
                    if(!compatible)throw new IOException("需要 MP4 相容容器，不接受 QuickTime 或未知格式");ftyp=true;
                }
                if(kind.equals("moov")){if(moov>=0)throw new IOException("重複 MP4 索引");moov=position;}
                if(kind.equals("mdat")){if(mdat<0)mdat=position;}
                if(kind.equals("moof"))throw new IOException("不接受分段 MP4");position+=size;
            }
            if(!ftyp||moov<0||mdat<0||moov>mdat)throw new IOException("需要 MP4 faststart（索引在影片資料前）");
        }
    }
    static void sps(byte[] bytes)throws IOException{
        // Find Annex-B SPS, then strip emulation-prevention bytes before parsing bounded Exp-Golomb.
        int start=-1,end=bytes.length;
        for(int i=0;i+4<bytes.length;i++){int prefix=bytes[i]==0&&bytes[i+1]==0?(bytes[i+2]==1?3:bytes[i+2]==0&&bytes[i+3]==1?4:0):0;
            if(prefix!=0&&(bytes[i+prefix]&31)==7){start=i+prefix+1;break;}}
        if(start<0)throw new IOException("缺少 H.264 SPS");for(int i=start;i+3<bytes.length;i++)if(bytes[i]==0&&bytes[i+1]==0&&(bytes[i+2]==1||bytes[i+2]==0&&bytes[i+3]==1)){end=i;break;}
        ByteArrayOutputStream rbsp=new ByteArrayOutputStream();int zeros=0;
        for(int i=start;i<end;i++){int value=bytes[i]&255;if(zeros>=2&&value==3){zeros=0;continue;}rbsp.write(value);zeros=value==0?zeros+1:0;}
        Bits b=new Bits(rbsp.toByteArray());if(b.read(8)!=100)throw new IOException("只接受 H.264 High Profile");b.read(8);if(b.read(8)!=31)throw new IOException("只接受 H.264 Level 3.1");b.ue();
        if(b.ue()!=1||b.ue()!=0||b.ue()!=0)throw new IOException("只接受 8-bit YUV 4:2:0");b.read(1);
        if(b.read(1)!=0)for(int i=0;i<8;i++)if(b.read(1)!=0){int last=8,next=8;for(int j=0;j<(i<6?16:64);j++){if(next!=0)next=(last+b.se()+256)%256;last=next==0?last:next;}}
        b.ue();int order=b.ue();if(order==0)b.ue();else if(order==1){b.read(1);b.se();b.se();int count=b.ue();if(count>256)throw new IOException("SPS 過大");for(int i=0;i<count;i++)b.se();}else if(order!=2)throw new IOException("SPS 無效");
        b.ue();b.read(1);int width=b.ue(),height=b.ue();if(width>255||height>255||b.read(1)!=1)throw new IOException("不接受交錯掃描或異常尺寸");b.read(1);
        int left=0,right=0,top=0,bottom=0;if(b.read(1)!=0){left=b.ue();right=b.ue();top=b.ue();bottom=b.ue();}
        if((width+1)*16-2L*(left+right)!=1280||(height+1)*16-2L*(top+bottom)!=720)throw new IOException("SPS 尺寸不是 1280×720");
        if(b.read(1)!=0){
            if(b.read(1)!=0){int aspect=b.read(8);if(aspect==255){int sarWidth=b.read(16),sarHeight=b.read(16);if(sarWidth!=sarHeight||sarWidth==0)throw new IOException("影片像素比例必須為 1:1");}else if(aspect!=1)throw new IOException("影片像素比例必須為 1:1");}
            if(b.read(1)!=0)b.read(1);
            if(b.read(1)!=0){b.read(3);b.read(1);if(b.read(1)!=0){b.read(8);int transfer=b.read(8);b.read(8);if(transfer==16||transfer==18)throw new IOException("不接受 HDR 影片");}}
        }
    }
    static void identityMatrices(File file)throws IOException{
        try(RandomAccessFile input=new RandomAccessFile(file,"r")){int[] counts=new int[2];scanMatrices(input,0,input.length(),0,counts);if(counts[0]!=1||counts[1]!=1)throw new IOException("影片必須有單一視訊軌與完整顯示資訊");}
    }
    private static void scanMatrices(RandomAccessFile in,long begin,long end,int depth,int[] counts)throws IOException{
        if(depth>2)throw new IOException("影片結構異常");int boxes=0;
        for(long at=begin;at<end;){
            if(++boxes>4096||end-at<8)throw new IOException("影片結構截斷");in.seek(at);long size=in.readInt()&0xffffffffL;byte[] name=new byte[4];in.readFully(name);String type=new String(name,StandardCharsets.US_ASCII);int header=8;
            if(size==1){if(end-at<16)throw new IOException("影片結構截斷");size=in.readLong();header=16;}if(size==0)size=end-at;if(size<header||size>end-at)throw new IOException("影片結構越界");
            if(type.equals("moov")||type.equals("trak"))scanMatrices(in,at+header,at+size,depth+1,counts);
            else if(type.equals("mvhd")||type.equals("tkhd")){
                in.seek(at+header);int version=in.readUnsignedByte();if(version>1)throw new IOException("未知影片標頭版本");boolean track=type.equals("tkhd");int offset=(track?40:36)+(version==1?12:0);
                if(size<header+offset+36+(track?8:0))throw new IOException("影片顯示矩陣截斷");in.seek(at+header+offset);
                for(int expected:new int[]{0x10000,0,0,0,0x10000,0,0,0,0x40000000})if(in.readInt()!=expected)throw new IOException("不接受旋轉、鏡射或縮放矩陣，請先轉成正向影片");
                if(track&&(in.readInt()!=(1280<<16)||in.readInt()!=(720<<16)))throw new IOException("影片顯示尺寸必須為 1280×720");counts[track?1:0]++;
            }
            at+=size;
        }
    }
    private static final class Bits {
        final byte[] data;int bit;Bits(byte[] data){this.data=data;}
        int read(int n)throws IOException{if(n<0||n>31||bit+n>data.length*8)throw new IOException("SPS 截斷");int value=0;for(int i=0;i<n;i++,bit++)value=(value<<1)|((data[bit/8]>>(7-bit%8))&1);return value;}
        int ue()throws IOException{int zeros=0;while(read(1)==0)if(++zeros>24)throw new IOException("SPS 數值過大");return ((1<<zeros)-1)+read(zeros);}
        int se()throws IOException{int value=ue();return (value&1)==0?-(value/2):(value+1)/2;}
    }
}
