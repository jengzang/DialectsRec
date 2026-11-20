package com.dialectrec.data;

/** Stable identities: keep the AI corpus unchanged so existing recordings remain linked. */
public final class CorpusCatalog {
    private CorpusCatalog() {}
    public static final String DEFAULT_ID="jnu_chaozhou";
    public static final class Entry {
        public final String directory,title;
        Entry(String directory,String title){this.directory=directory;this.title=title;}
    }
    public static final Entry[] BUNDLED={
        new Entry("corpus_jnu_chaozhou","暨大·潮州"),
        new Entry("corpus_jnu_suzhou","暨大·苏州"),
        new Entry("corpus_v1","AI·通用补充"),
        new Entry("corpus_jnu_local","暨大·地方特色")
    };
}
