package com.dialectrec.data;

public final class Sentence {
    public final String id, text, category, difficulty, lengthBucket, notes;
    public Sentence(String id, String text, String category, String difficulty) {
        this(id,text,category,difficulty,"","");
    }
    public Sentence(String id, String text, String category, String difficulty, String lengthBucket, String notes) {
        this.id=id; this.text=text; this.category=category; this.difficulty=difficulty;
        this.lengthBucket=lengthBucket==null||lengthBucket.isEmpty()?("narrative".equals(difficulty)?"long":"situational".equals(difficulty)?"medium":"short"):lengthBucket;
        this.notes=notes==null?"":notes;
    }
}
