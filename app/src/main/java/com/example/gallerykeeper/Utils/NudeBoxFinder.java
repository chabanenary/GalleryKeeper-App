package com.example.gallerykeeper.Utils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Logique JVM pure (testable) : Nude est prioritaire sur toutes les autres classes.
 *
 * Le Detector ne garde qu'une classe par ancre (celle de plus fort score), puis applique un NMS
 * qui ignore les classes. Un score "nude" élevé peut donc disparaître derrière une boîte "child"
 * (ou autre) plus forte au même endroit, avant même d'arriver à TagClassifier.
 * Cette classe retrouve la meilleure boîte "nude" de toute la sortie du modèle et l'ajoute aux
 * boîtes retenues. Les boîtes des autres classes ne sont pas modifiées.
 */
public final class NudeBoxFinder {

    private NudeBoxFinder() {
    }

    /**
     * @param output         sortie du modèle [1, numChannel, numElements] aplatie (même format que Detector)
     * @param nudeClassIndex index de "nude" dans les labels (-1 si absent)
     * @param minConfidence  score minimal, comme dans Detector (strictement supérieur)
     * @return la boîte "nude" de plus fort score, ou null
     */
    public static BoundingBox findBestNudeBox(float[] output, int numChannel, int numElements,
                                              int nudeClassIndex, float minConfidence) {
        if (output == null || nudeClassIndex < 0 || numElements <= 0) {
            return null;
        }
        int channel = 4 + nudeClassIndex;
        if (channel >= numChannel || output.length < numChannel * numElements) {
            return null;
        }

        BoundingBox best = null;
        for (int c = 0; c < numElements; c++) {
            float score = output[c + numElements * channel];
            if (score <= minConfidence || (best != null && score <= best.cnf)) {
                continue;
            }

            float cx = output[c];
            float cy = output[c + numElements];
            float w = output[c + numElements * 2];
            float h = output[c + numElements * 3];
            float x1 = cx - (w / 2F);
            float y1 = cy - (h / 2F);
            float x2 = cx + (w / 2F);
            float y2 = cy + (h / 2F);
            // Mêmes limites que Detector.bestBox()
            if (x1 < 0F || x1 > 1F) continue;
            if (y1 < 0F || y1 > 1F) continue;
            if (x2 < 0F || x2 > 1F) continue;
            if (y2 < 0F || y2 > 1F) continue;

            best = new BoundingBox(x1, y1, x2, y2, cx, cy, w, h, score, nudeClassIndex, "nude");
        }
        return best;
    }

    /**
     * Ajoute bestNude aux boîtes retenues, sauf si une boîte "nude" au moins aussi forte y est déjà.
     *
     * @return la liste d'origine si rien n'est ajouté, sinon une nouvelle liste triée par confiance décroissante
     */
    public static List<BoundingBox> addIfMissing(List<BoundingBox> selected, BoundingBox bestNude) {
        if (bestNude == null) {
            return selected;
        }
        List<BoundingBox> result = new ArrayList<>();
        if (selected != null) {
            for (BoundingBox box : selected) {
                if (box != null && "nude".equals(box.clsName) && box.cnf >= bestNude.cnf) {
                    return selected;
                }
            }
            result.addAll(selected);
        }
        result.add(bestNude);
        Collections.sort(result, Comparator.comparingDouble(box -> -box.cnf));
        return result;
    }
}
