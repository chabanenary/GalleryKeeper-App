package com.example.gallerykeeper.Utils;

import java.util.List;

/**
 * Pipeline JVM pur pour tester la logique scan/monitoring sans Android:
 * - entrée: détections (BoundingBox)
 * - sortie: décision (déplacer ? supprimer l'original ?)
 *
 * IMPORTANT: ne remplace pas ton code Android, c'est un support de tests.
 */
public final class ScanPipeline {

    private ScanPipeline() {
    }

    public static ScanDecision decideActions(List<BoundingBox> detections) {
        String tagName = TagClassifier.decideTagName(detections);
        if (tagName == null) {
            return ScanDecision.noOp();
        }
        // Dans l'app: si tag != null -> moveUriToTagFolder(...) puis on enfile l'URI originale pour suppression.
        return ScanDecision.moveAndDeleteOriginal(tagName);
    }
}
