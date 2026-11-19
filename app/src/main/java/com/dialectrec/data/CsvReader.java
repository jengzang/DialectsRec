package com.dialectrec.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

public final class CsvReader {
    private CsvReader() {}
    public static List<String[]> read(InputStream input) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int count; while ((count = input.read(buffer)) >= 0) { if (count > 0) bytes.write(buffer, 0, count); } String s = new String(bytes.toByteArray(), StandardCharsets.UTF_8); if (s.startsWith("\ufeff")) s=s.substring(1);
        List<String[]> rows = new ArrayList<>(); List<String> row = new ArrayList<>(); StringBuilder cell = new StringBuilder(); boolean quoted=false;
        for (int i=0;i<s.length();i++) { char c=s.charAt(i);
            if (quoted) { if (c=='"' && i+1<s.length() && s.charAt(i+1)=='"') { cell.append('"'); i++; } else if (c=='"') quoted=false; else cell.append(c); }
            else if (c=='"' && cell.length()==0) quoted=true;
            else if (c==',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c=='\n' || c=='\r') { if (c=='\r' && i+1<s.length() && s.charAt(i+1)=='\n') i++; row.add(cell.toString()); cell.setLength(0); if (!(row.size()==1 && row.get(0).isEmpty())) rows.add(row.toArray(new String[0])); row=new ArrayList<>(); }
            else cell.append(c);
        }
        if (cell.length()>0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row.toArray(new String[0])); }
        return rows;
    }
}
