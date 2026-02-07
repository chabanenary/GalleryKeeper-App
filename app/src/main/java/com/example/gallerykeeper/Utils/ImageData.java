package com.example.gallerykeeper.Utils;


import android.graphics.Bitmap;
import android.net.Uri;

public class ImageData {
    private final Bitmap bitmap;
    private final Uri uri;

    public ImageData(Bitmap bitmap, Uri uri) {
        this.bitmap = bitmap;
        this.uri = uri;
    }

    public Bitmap getBitmap() {
        return bitmap;
    }

    public Uri getUri() {
        return uri;
    }
}