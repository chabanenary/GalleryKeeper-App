package com.example.gallerykeeper.Utils;

/**
 * Logique JVM pure (testable) : construit une boîte à partir de la sortie du modèle (centre + taille,
 * coordonnées normalisées 0..1).
 *
 * Quand un document ou une personne remplit la photo, le modèle place souvent un bord juste hors de
 * l'image (ex. x1 = -0.01). Rejeter ces boîtes faisait perdre des cartes et pièces détectées à plus de
 * 90 % : leurs bords sont maintenant ramenés dans l'image, comme le fait Ultralytics.
 */
public final class BoxGeometry {

    private BoxGeometry() {
    }

    /**
     * @return la boîte ramenée dans l'image, ou null si elle est vide une fois recadrée
     *         (entièrement hors de l'image, taille nulle ou valeurs NaN)
     */
    public static BoundingBox clampedBox(float cx, float cy, float w, float h, float cnf, int cls, String clsName) {
        float x1 = clamp(cx - (w / 2F));
        float y1 = clamp(cy - (h / 2F));
        float x2 = clamp(cx + (w / 2F));
        float y2 = clamp(cy + (h / 2F));
        // Écrit ainsi pour rejeter aussi les NaN (toute comparaison avec NaN est fausse)
        if (!(x2 > x1) || !(y2 > y1)) {
            return null;
        }
        float clampedW = x2 - x1;
        float clampedH = y2 - y1;
        return new BoundingBox(x1, y1, x2, y2, x1 + clampedW / 2F, y1 + clampedH / 2F,
                clampedW, clampedH, cnf, cls, clsName);
    }

    private static float clamp(float value) {
        return Math.max(0F, Math.min(1F, value));
    }
}
