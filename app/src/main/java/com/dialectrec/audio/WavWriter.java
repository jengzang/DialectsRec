package com.dialectrec.audio;

import java.io.*;

public final class WavWriter {
    private WavWriter() {}
    public static void write(File target, byte[] pcm, int sampleRate, int channels, int bitsPerSample) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            writeHeader(out, pcm.length, sampleRate, channels, bitsPerSample); out.write(pcm); out.flush();
        }
        promote(tmp, target);
    }
    public static void writeFromPcm(File target, File pcmFile, int sampleRate, int channels, int bitsPerSample) throws IOException {
        File tmp = new File(target.getPath() + ".tmp");
        try (FileInputStream in = new FileInputStream(pcmFile); FileOutputStream out = new FileOutputStream(tmp)) {
            writeHeader(out, (int) pcmFile.length(), sampleRate, channels, bitsPerSample);
            byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) >= 0) { if (n > 0) out.write(buf, 0, n); }
            out.flush();
        }
        promote(tmp, target);
    }
    private static void writeHeader(OutputStream out, int length, int sampleRate, int channels, int bitsPerSample) throws IOException {
        writeAscii(out, "RIFF"); writeLeInt(out, 36 + length); writeAscii(out, "WAVE"); writeAscii(out, "fmt "); writeLeInt(out, 16); writeLeShort(out, (short) 1); writeLeShort(out, (short) channels); writeLeInt(out, sampleRate);
        int blockAlign = channels * bitsPerSample / 8; writeLeInt(out, sampleRate * blockAlign); writeLeShort(out, (short) blockAlign); writeLeShort(out, (short) bitsPerSample); writeAscii(out, "data"); writeLeInt(out, length);
    }
    private static void promote(File tmp, File target) throws IOException { if (target.exists() && !target.delete()) throw new IOException("cannot replace WAV"); if (!tmp.renameTo(target)) throw new IOException("cannot atomically promote WAV"); }
    private static void writeAscii(OutputStream o, String s) throws IOException { o.write(s.getBytes("US-ASCII")); }
    private static void writeLeInt(OutputStream o, int v) throws IOException { o.write(v & 255); o.write((v >> 8) & 255); o.write((v >> 16) & 255); o.write((v >> 24) & 255); }
    private static void writeLeShort(OutputStream o, short v) throws IOException { o.write(v & 255); o.write((v >> 8) & 255); }
}
