package com.dialectrec.data;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.assertEquals;

public class SentenceListFilterTest {
    @Test public void returnsAllRowsOrOnlySelectedCategory() {
        List<Sentence> rows = Arrays.asList(
            new Sentence("S1", "甲乙丙。", "家庭", "short"),
            new Sentence("S2", "丁戊己。", "出行", "short"),
            new Sentence("S3", "庚辛壬。", "家庭", "short"));
        assertEquals(3, SentenceListFilter.filter(rows, "全部类别").size());
        assertEquals(2, SentenceListFilter.filter(rows, "家庭").size());
    }
}
