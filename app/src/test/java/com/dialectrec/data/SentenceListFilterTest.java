package com.dialectrec.data;

import org.junit.Test;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
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

    @Test public void progressAndCategoryBrowseKeepCorpusOrderAfterTaskCreation() {
        Sentence first = new Sentence("S1", "第一句。", "家庭", "short");
        Sentence second = new Sentence("S2", "第二句。", "出行", "short");
        Sentence third = new Sentence("S3", "第三句。", "家庭", "short");
        List<Sentence> rows = Arrays.asList(first, second, third);
        TaskOrder.shuffled(rows, new Random(1));
        assertEquals(Arrays.asList(first, second, third), SentenceListFilter.filter(rows, "全部类别"));
        assertEquals(Arrays.asList(first, third), SentenceListFilter.filter(rows, "家庭"));
    }
}
