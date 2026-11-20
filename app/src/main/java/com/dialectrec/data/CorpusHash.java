package com.dialectrec.data;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

public final class CorpusHash {
    private CorpusHash() {}
    public static String sha256(List<Sentence> sentences) {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for(Sentence s:sentences){String value=s.id+"\u001f"+s.text+"\u001f"+s.category+"\u001f"+s.difficulty+"\u001f"+s.lengthBucket+"\u001f"+s.notes+"\n";digest.update(value.getBytes(StandardCharsets.UTF_8));}
            StringBuilder out=new StringBuilder();for(byte b:digest.digest())out.append(String.format(Locale.ROOT,"%02x",b));return out.toString();
        } catch(Exception e){throw new IllegalStateException(e);}
    }
}
