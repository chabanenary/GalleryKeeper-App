package com.example.gallerykeeper.Utils;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.IOException;
import java.io.InputStream;

/**
 * Chargement de Bitmap depuis une URI MediaStore sans utiliser MediaStore.Images.Media.getBitmap (déprécié).
 */
public final class BitmapLoader {

    private static final String TAG = "BitmapLoader";

    private BitmapLoader() {
    }

    @Nullable
    public static Bitmap decode(@Nullable ContentResolver resolver, @Nullable Uri uri) throws IOException {
        if (resolver == null || uri == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } catch (RuntimeException | IOException e) {
                Log.w(TAG, "ImageDecoder failed, falling back to BitmapFactory", e);
            }
        }

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            try {
                return BitmapFactory.decodeStream(in);
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OutOfMemory decoding bitmap from: " + uri, oom);
                return null;
            }
        }
    }
}
