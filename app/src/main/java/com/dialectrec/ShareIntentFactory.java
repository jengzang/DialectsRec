package com.dialectrec;

import android.content.Intent;
import android.net.Uri;

public final class ShareIntentFactory {
    private ShareIntentFactory() {}
    public static Intent create(Uri uri) {
        Intent send = new Intent(ShareIntentSpec.ACTION);
        send.setType(ShareIntentSpec.MIME);
        send.putExtra(Intent.EXTRA_STREAM, uri);
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return send;
    }
}
