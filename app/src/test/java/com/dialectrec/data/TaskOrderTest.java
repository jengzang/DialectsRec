package com.dialectrec.data;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class TaskOrderTest {
    private List<Sentence> rows(int count) {
        List<Sentence> rows = new ArrayList<>();
        for (int i = 1; i <= count; i++)
            rows.add(new Sentence(String.format(Locale.ROOT, "S%06d", i), "句子" + i, "家庭", "short"));
        return rows;
    }

    @Test public void shufflesAllFiveThousandWithoutLossDuplicatesOrMutatingBrowseOrder() {
        List<Sentence> ordered = rows(5000);
        List<Sentence> shuffled = TaskOrder.shuffled(ordered, new Random(123));
        assertEquals(5000, shuffled.size());
        assertEquals(new HashSet<>(ordered), new HashSet<>(shuffled));
        assertNotEquals(ordered, shuffled);
        assertEquals(rowsIds(ordered), rowsIds(rows(5000)));
    }

    @Test public void independentTasksGetDifferentOrders() {
        List<Sentence> ordered = rows(100);
        assertNotEquals(TaskOrder.shuffled(ordered, new Random(1)),
                TaskOrder.shuffled(ordered, new Random(2)));
    }

    @Test public void handlesEmptySingleAndFilteredTasks() {
        assertTrue(TaskOrder.shuffled(Collections.emptyList(), new Random(1)).isEmpty());
        List<Sentence> one = rows(1);
        assertEquals(one, TaskOrder.shuffled(one, new Random(1)));
        List<Sentence> range = rows(100).subList(20, 40);
        assertEquals(new HashSet<>(range), new HashSet<>(TaskOrder.shuffled(range, new Random(1))));
    }

    @Test public void snapshotRestoresExactQueueAndCurrentSentenceFromFreshRows() {
        List<Sentence> original = TaskOrder.shuffled(rows(100), new Random(1));
        List<Sentence> restored = TaskOrder.restore(rows(100), TaskOrder.encode(original));
        assertEquals(rowsIds(original), rowsIds(restored));
        assertEquals(original.get(37).id, restored.get(37).id);
    }

    @Test public void rejectsTruncatedDuplicateUnknownOrMissingSnapshot() {
        List<Sentence> ordered = rows(3);
        for (String snapshot : Arrays.asList(null, "", "S000001,S000002", "S000001,S000001,S000003",
                "S000001,S000002,S999999", "S000001,S000002,S000003,")) {
            assertThrows(IllegalArgumentException.class, () -> TaskOrder.restore(ordered, snapshot));
        }
    }

    @Test public void migratesLegacyTaskWithoutMovingVisitedPrefixOrCurrentSentence() {
        List<Sentence> ordered = rows(100);
        List<Sentence> migrated = TaskOrder.migrateLegacy(ordered, 12, new Random(1));
        assertEquals(ordered.subList(0, 13), migrated.subList(0, 13));
        assertNotEquals(ordered.subList(13, 100), migrated.subList(13, 100));
        assertEquals(new HashSet<>(ordered), new HashSet<>(migrated));
        assertEquals(rowsIds(rows(100)), rowsIds(ordered));
        assertEquals(rowsIds(migrated), rowsIds(TaskOrder.restore(rows(100), TaskOrder.encode(migrated))));
    }

    @Test public void legacyMigrationHandlesLastSentenceAndSingleSentence() {
        List<Sentence> ordered = rows(3);
        assertEquals(ordered, TaskOrder.migrateLegacy(ordered, 2, new Random(1)));
        List<Sentence> one = rows(1);
        assertEquals(one, TaskOrder.migrateLegacy(one, 0, new Random(1)));
    }

    private List<String> rowsIds(List<Sentence> rows) {
        List<String> ids = new ArrayList<>();
        for (Sentence row : rows) ids.add(row.id);
        return ids;
    }
}
