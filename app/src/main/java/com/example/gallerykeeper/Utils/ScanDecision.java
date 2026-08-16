package com.example.gallerykeeper.Utils;

/**
 * Résultat JVM pur d'une décision de scan/monitoring.
 *
 * Cette classe ne touche pas Android. Elle aide à tester l'enchaînement:
 * - classifier (tagName)
 * - décider des actions: déplacer puis demander suppression de l'original si tag != null
 */
public final class ScanDecision {

    public final String tagName;
    public final boolean shouldMove;
    public final boolean shouldEnqueueDeletion;

    private ScanDecision(String tagName, boolean shouldMove, boolean shouldEnqueueDeletion) {
        this.tagName = tagName;
        this.shouldMove = shouldMove;
        this.shouldEnqueueDeletion = shouldEnqueueDeletion;
    }

    public static ScanDecision noOp() {
        return new ScanDecision(null, false, false);
    }

    public static ScanDecision moveAndDeleteOriginal(String tagName) {
        return new ScanDecision(tagName, true, true);
    }
}
