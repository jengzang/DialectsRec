package com.dialectrec.data;

import java.util.*;

/** Recording queues are copies; the corpus and progress browser keep their original order. */
public final class TaskOrder {
    private TaskOrder() {}

    public static List<Sentence> shuffled(List<Sentence> ordered, Random random) {
        List<Sentence> out = new ArrayList<>(ordered);
        Collections.shuffle(out, random);
        return out;
    }

    public static List<Sentence> migrateLegacy(List<Sentence> ordered, int current, Random random) {
        List<Sentence> out = new ArrayList<>(ordered);
        int from = Math.min(out.size(), Math.max(0, current + 1));
        Collections.shuffle(out.subList(from, out.size()), random);
        return out;
    }

    public static String encode(List<Sentence> task) {
        List<String> ids = new ArrayList<>();
        for (Sentence row : task) ids.add(row.id);
        return String.join(",", ids);
    }

    /** Fail closed on stale or damaged snapshots rather than resuming the wrong sentence. */
    public static List<Sentence> restore(List<Sentence> ordered, String snapshot) {
        if (snapshot == null) throw new IllegalArgumentException("Missing task order");
        if (ordered.isEmpty() && snapshot.isEmpty()) return new ArrayList<>();
        String[] ids = snapshot.split(",", -1);
        if (ids.length != ordered.size()) throw new IllegalArgumentException("Task size changed");
        Map<String, Sentence> available = new HashMap<>();
        for (Sentence row : ordered) available.put(row.id, row);
        List<Sentence> out = new ArrayList<>();
        for (String id : ids) {
            Sentence row = available.remove(id);
            if (row == null) throw new IllegalArgumentException("Unknown or duplicate task sentence");
            out.add(row);
        }
        if (!available.isEmpty()) throw new IllegalArgumentException("Incomplete task order");
        return out;
    }
}
