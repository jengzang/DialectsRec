package com.dialectrec;

public enum ExportChoice {
    SHARE_WECHAT("分享到微信"),
    LOCAL_ONLY("仅保存本地");
    public final String label;
    ExportChoice(String label) { this.label = label; }
}
