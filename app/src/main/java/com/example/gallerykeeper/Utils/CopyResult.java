package com.example.gallerykeeper.Utils;

import android.net.Uri;

public class CopyResult {
    private final boolean success;
    private final Uri targetUri;

    public CopyResult(boolean success, Uri targetUri) {
        this.success = success;
        this.targetUri = targetUri;
    }

    public boolean isSuccess() {
        return success;
    }

    public Uri getTargetUri() {
        return targetUri;
    }
}