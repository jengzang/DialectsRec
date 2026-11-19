package com.dialectrec.audio;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public final class PcmResampler {
    private PcmResampler() {}
    public static byte[] resample(byte[] input, int sourceRate, int targetRate) {
        if (sourceRate == targetRate) return input.clone();
        int inFrames=input.length/2, outFrames=(int)Math.round(inFrames*(targetRate/(double)sourceRate));
        ByteBuffer out=ByteBuffer.allocate(outFrames*2).order(ByteOrder.LITTLE_ENDIAN); ByteBuffer in=ByteBuffer.wrap(input).order(ByteOrder.LITTLE_ENDIAN);
        for(int i=0;i<outFrames;i++){double pos=i*sourceRate/(double)targetRate;int left=(int)Math.floor(pos);double frac=pos-left;short a=in.getShort(Math.min(left,Math.max(0,inFrames-1))*2);short b=in.getShort(Math.min(left+1,Math.max(0,inFrames-1))*2);int value=(int)Math.round(a+(b-a)*frac);out.putShort((short)Math.max(Short.MIN_VALUE,Math.min(Short.MAX_VALUE,value)));}
        return out.array();
    }
}
