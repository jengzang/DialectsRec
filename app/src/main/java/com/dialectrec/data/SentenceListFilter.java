package com.dialectrec.data;

import java.util.ArrayList;
import java.util.List;

public final class SentenceListFilter {
    private SentenceListFilter() {}
    public static List<Sentence> filter(List<Sentence> rows, String category) {
        if (category == null || category.isEmpty() || "全部类别".equals(category)) return new ArrayList<>(rows);
        List<Sentence> out = new ArrayList<>();
        for (Sentence row : rows) if (category.equals(row.category)) out.add(row);
        return out;
    }
}
