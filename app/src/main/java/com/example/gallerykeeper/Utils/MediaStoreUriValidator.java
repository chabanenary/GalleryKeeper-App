package com.example.gallerykeeper.Utils;

import java.util.regex.Pattern;

/**
 * Validation d'URI MediaStore sous forme de String.
 *
 * Objectif: empcher les URI "g
 *    - content://media/
 *    - content://media/external/images/media
 * qui correspondent  des collections et non  des items.
 *
 * Pur Java (pas d
 */
public final class MediaStoreUriValidator {

    private MediaStoreUriValidator() {
    }

    // Accepte:
    // - content://media/external/images/media/36576
    // - content://media/external/video/media/123
    // - content://media/external/file/123
    // On rejette volontairement les URL "collection" sans ID.
    private static final Pattern MEDIASTORE_ITEM_URI = Pattern.compile(
            "^content://media/" +
                    "(?:external|internal)/" +
                    "(?:images/media|video/media|file)/" +
                    "\\d+$"
    );

    public static boolean isValidMediaStoreItemUri(String uriString) {
        if (uriString == null) {
            return false;
        }
        String trimmed = uriString.trim();
        if (trimmed.isEmpty()) {
            return false;
        }
        // Normaliser le trailing slash pour 
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return MEDIASTORE_ITEM_URI.matcher(trimmed).matches();
    }
}
