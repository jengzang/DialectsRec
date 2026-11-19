package com.dialectrec.audio;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class WavInspector {
    public static final class Info {
        public final boolean valid; public final int sampleRate, channels, bitsPerSample, frames; public final String spokenTranscript;
        Info(boolean valid, int sampleRate, int channels, int bitsPerSample, int frames) { this.valid = valid; this.sampleRate = sampleRate; this.channels = channels; this.bitsPerSample = bitsPerSample; this.frames = frames; this.spokenTranscript = null; }
    }
    private WavInspector() {}
    public static Info inspect(File file) throws IOException {
        try (RandomAccessFile r = new RandomAccessFile(file, "r")) {
            if (r.length() < 44 || !readString(r, 4).equals("RIFF")) throw new IOException("not RIFF");
            r.skipBytes(4); if (!readString(r, 4).equals("WAVE")) throw new IOException("not WAVE");
            int rate = 0, channels = 0, bits = 0, data = -1;
            while (r.getFilePointer() + 8 <= r.length()) {
                String id = readString(r, 4); int size = readLeInt(r); long next = r.getFilePointer() + size;
                if (id.equals("fmt ")) { int format = readLeShort(r); channels = readLeShort(r); rate = readLeInt(r); r.skipBytes(6); bits = readLeShort(r); if (format != 1) throw new IOException("compressed WAV"); }
                else if (id.equals("data")) { data = size; break; }
                r.seek(Math.min(next, r.length()));
            }
            if (data < 0 || rate <= 0 || channels <= 0 || bits <= 0) throw new IOException("incomplete WAV");
            return new Info(rate == 16000 && channels == 1 && bits == 16 && data > 0, rate, channels, bits, data / (channels * bits / 8));
        }
    }
    private static String readString(RandomAccessFile r, int n) throws IOException { byte[] b = new byte[n]; r.readFully(b); return new String(b, StandardCharsets.US_ASCII); }
    private static int readLeInt(RandomAccessFile r) throws IOException { return (r.read() & 255) | ((r.read() & 255) << 8) | ((r.read() & 255) << 16) | ((r.read() & 255) << 24); }
    private static int readLeShort(RandomAccessFile r) throws IOException { return (r.read() & 255) | ((r.read() & 255) << 8); }
}
