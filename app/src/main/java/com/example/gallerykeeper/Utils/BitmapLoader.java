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

    /**
     * Comme decode(), mais l'image est réduite dès le décodage (facteur puissance de 2) en gardant
     * son grand côté au moins égal à targetLongSide. Ex. : une photo de 12 MP (4000x3000, ~48 Mo)
     * devient 2000x1500 (~12 Mo) pour targetLongSide = 1280. Les petites images ne sont pas réduites.
     * Une OutOfMemoryError éventuelle n'est pas rattrapée ici : l'appelant décide quoi faire.
     */
    @Nullable
    public static Bitmap decodeSampled(@Nullable ContentResolver resolver, @Nullable Uri uri,
                                       int targetLongSide) throws IOException {
        if (resolver == null || uri == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                    decoder.setTargetSampleSize(computeSampleSize(
                            info.getSize().getWidth(), info.getSize().getHeight(), targetLongSide));
                });
            } catch (RuntimeException | IOException e) {
                Log.w(TAG, "ImageDecoder failed, falling back to BitmapFactory", e);
            }
        }

        // 1er passage : dimensions seulement (aucune mémoire allouée pour les pixels)
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            BitmapFactory.decodeStream(in, null, bounds);
        }

        // 2e passage : décodage réduit
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, targetLongSide);
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            return BitmapFactory.decodeStream(in, null, options);
        }
    }

    /**
     * Logique JVM pure (testable) : plus grand facteur de réduction (1, 2, 4, 8...) qui garde le grand
     * côté de l'image au moins égal à targetLongSide. 1 = pas de réduction.
     */
    public static int computeSampleSize(int width, int height, int targetLongSide) {
        if (width <= 0 || height <= 0 || targetLongSide <= 0) {
            return 1;
        }
        int longSide = Math.max(width, height);
        int sampleSize = 1;
        while (longSide / (sampleSize * 2) >= targetLongSide) {
            sampleSize *= 2;
        }
        return sampleSize;
    }
}
