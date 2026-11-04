package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.media.Image;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.os.Build;
import android.os.SystemClock;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Software-only preparation. Never retries a failed codec or selects a vendor decoder. */
final class BootVideoDecoder {
    static MediaCodec create(MediaFormat format)throws IOException {
        MediaCodecInfo[] infos=new MediaCodecList(MediaCodecList.REGULAR_CODECS).getCodecInfos();
        for(String name:new String[]{"c2.android.avc.decoder","OMX.google.h264.decoder"}) {
            for(MediaCodecInfo info:infos) {
                if(!name.equals(info.getName())||info.isEncoder())continue;
                if(Build.VERSION.SDK_INT>=29&&(!info.isSoftwareOnly()||info.isVendor()))continue;
                boolean avc=false;for(String type:info.getSupportedTypes())if("video/avc".equals(type))avc=true;
                if(!avc||!info.getCapabilitiesForType("video/avc").isFormatSupported(format))continue;
                // An instantiation failure propagates; no automatic codec retry.
                return MediaCodec.createByCodecName(name);
            }
        }
        throw new IOException("沒有支援此格式的平台軟體解碼器；未嘗試廠商解碼器");
    }
    static Bitmap decode(MediaExtractor extractor,MediaFormat format,BooleanSupplier cancelled)throws Exception {
        MediaCodec decoder=create(format);boolean started=false,success=false;
        Bitmap poster=null;int posterScore=-1;List<Long> times=new ArrayList<>();
        long deadline=SystemClock.elapsedRealtime()+25000;
        try {
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible);
            decoder.configure(format,null,null,0);decoder.start();started=true;
            boolean inputEnd=false,outputEnd=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
            while(!outputEnd) {
                BootAssetStore.checkCancelled(cancelled);
                if(SystemClock.elapsedRealtime()>deadline)throw new IOException("完整影片軟體解碼逾時；不接受此素材");
                if(!inputEnd) {
                    int index=decoder.dequeueInputBuffer(10000);
                    if(index>=0) {
                        ByteBuffer data=decoder.getInputBuffer(index);if(data==null)throw new IOException("軟體解碼輸入不可用");
                        data.clear();int n=extractor.readSampleData(data,0);
                        if(n<0){decoder.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inputEnd=true;}
                        else{decoder.queueInputBuffer(index,0,n,extractor.getSampleTime(),0);extractor.advance();}
                    }
                }
                int output=decoder.dequeueOutputBuffer(info,10000);
                if(output>=0) {
                    try {
                        if(info.size>0&&(info.flags&MediaCodec.BUFFER_FLAG_CODEC_CONFIG)==0) {
                            times.add(info.presentationTimeUs);
                            if(times.size()>120)throw new IOException("解碼幀數超過 120");
                            if(poster==null||posterScore<12&&CinemaPosterPolicy.sample(times.size())) {
                                final int outputIndex=output;
                                Bitmap candidate=CinemaPosterPolicy.readCandidate(poster==null,()->{
                                    try(Image image=decoder.getOutputImage(outputIndex)){
                                        return image==null?null:poster(image,decoder.getOutputFormat());
                                    }
                                });
                                if(candidate!=null){int score=CinemaPosterPolicy.score(candidate);
                                    if(poster==null||score>posterScore){if(poster!=null)poster.recycle();poster=candidate;posterScore=score;}else candidate.recycle();
                                }
                            }
                        }
                        outputEnd=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                    }finally{decoder.releaseOutputBuffer(output,false);}
                }
            }
            BootVideoValidator.cadence(times);
            if(poster==null)throw new IOException("影片沒有可用的預覽幀");
            success=true;return poster;
        } catch(MediaCodec.CodecException failure) {
            throw new IOException("平台軟體解碼失敗，未改試廠商解碼器："+failure.getDiagnosticInfo(),failure);
        } finally {
            if(started)try{decoder.stop();}catch(RuntimeException ignored){}
            decoder.release();if(!success&&poster!=null)poster.recycle();
        }
    }
    private static Bitmap poster(Image image,MediaFormat format)throws IOException {
        Rect crop=image.getCropRect();Image.Plane[] planes=image.getPlanes();
        if(image.getFormat()!=ImageFormat.YUV_420_888||planes.length!=3||crop.width()!=1280||crop.height()!=720)
            throw new IOException("不支援的軟體解碼縮圖格式");
        boolean bt709=format.containsKey(MediaFormat.KEY_COLOR_STANDARD)&&format.getInteger(MediaFormat.KEY_COLOR_STANDARD)==MediaFormat.COLOR_STANDARD_BT709;
        boolean full=format.containsKey(MediaFormat.KEY_COLOR_RANGE)&&format.getInteger(MediaFormat.KEY_COLOR_RANGE)==MediaFormat.COLOR_RANGE_FULL;
        int[] pixels=new int[640*360];
        for(int y=0;y<360;y++)for(int x=0;x<640;x++) {
            int sx=crop.left+x*2,sy=crop.top+y*2;
            int yy=sample(planes[0].getBuffer(),planes[0].getRowStride(),planes[0].getPixelStride(),sx,sy);
            int u=sample(planes[1].getBuffer(),planes[1].getRowStride(),planes[1].getPixelStride(),sx/2,sy/2);
            int v=sample(planes[2].getBuffer(),planes[2].getRowStride(),planes[2].getPixelStride(),sx/2,sy/2);
            pixels[y*640+x]=argb(yy,u,v,bt709,full);
        }
        return Bitmap.createBitmap(pixels,640,360,Bitmap.Config.ARGB_8888);
    }
    static int sample(ByteBuffer data,int rowStride,int pixelStride,int x,int y)throws IOException {
        if(rowStride<=0||pixelStride<=0||x<0||y<0)throw new IOException("無效的解碼像素排列");
        long at=(long)data.position()+(long)y*rowStride+(long)x*pixelStride;
        if(at<data.position()||at>=data.limit())throw new IOException("解碼像素超出範圍");
        return data.get((int)at)&255;
    }
    static int argb(int y,int u,int v,boolean bt709,boolean full) {
        int d=u-128,e=v-128,c=full?256*y:298*(y-16);
        int red=(c+(bt709?(full?403:459):(full?359:409))*e+128)>>8;
        int green=(c-(bt709?(full?48:55):(full?88:100))*d-(bt709?(full?120:136):(full?183:208))*e+128)>>8;
        int blue=(c+(bt709?(full?475:541):(full?454:516))*d+128)>>8;
        return 0xff000000|clamp(red)<<16|clamp(green)<<8|clamp(blue);
    }
    private static int clamp(int x){return Math.max(0,Math.min(255,x));}
}
