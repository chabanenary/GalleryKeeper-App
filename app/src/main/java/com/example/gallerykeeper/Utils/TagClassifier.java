package com.example.gallerykeeper.Utils;

import java.util.List;

/**
 * Logique métier (testable en JVM) : détermine le nom du tag à partir des détections.
 *
 * Règles utilisées par PhotoPredictor.predict() (donc par le scan et par le monitoring) :
 * - si une boîte "nude" a cnf > 0.7 => "Nude", prioritaire sur tout le reste
 *   (quel que soit l'ordre des boîtes et la confiance des autres classes)
 * - une boîte "nude" à 0.7 ou moins est ignorée
 * - sinon si on voit "child" avec cnf > 0.6 => "Children" (prioritaire)
 * - sinon on prend la box de plus forte confiance avec cnf > 0.6
 * - mapping: creditcard -> "Credit Card", id -> "Identity Document"
 */
public final class TagClassifier {

    // Seuil de nudité : au-delà, la photo est classée "Nude"
    public static final float NUDE_THRESHOLD = 0.7f;
    // Seuil enfant : au-delà, la photo est classée "Children" (prioritaire sur les autres classes)
    public static final float CHILD_THRESHOLD = 0.6f;
    // Seuil des autres classes (carte bancaire, pièce d'identité) : la boîte la plus sûre au-delà l'emporte
    public static final float OTHER_THRESHOLD = 0.6f;

    private TagClassifier() {
    }

    public static String decideTagName(List<BoundingBox> boundingBoxes) {
        if (boundingBoxes == null || boundingBoxes.isEmpty()) {
            return null;
        }

        // 1) Nude prioritaire : on regarde toutes les boîtes, l'ordre ne compte pas
        for (BoundingBox box : boundingBoxes) {
            if (box != null && "nude".equals(box.clsName) && box.cnf > NUDE_THRESHOLD) {
                return "Nude";
            }
        }

        // 2) Sinon, règles des autres classes ; les boîtes "nude" sous le seuil sont ignorées
        BoundingBox highestConfidenceBox = null;
        float highestConfidence = -1.0f;

        for (BoundingBox box : boundingBoxes) {
            if (box == null || box.clsName == null || "nude".equals(box.clsName)) {
                continue;
            }

            if ("child".equals(box.clsName) && box.cnf > CHILD_THRESHOLD) {
                highestConfidenceBox = box;
                break;
            } else {
                if (box.cnf > highestConfidence && box.cnf > OTHER_THRESHOLD) {
                    highestConfidence = box.cnf;
                    highestConfidenceBox = box;
                }
            }
        }

        if (highestConfidenceBox == null || highestConfidenceBox.clsName == null) {
            return null;
        }

        switch (highestConfidenceBox.clsName) {
            case "nude":
                return "Nude";
            case "child":
                return "Children";
            case "creditcard":
                return "Credit Card";
            case "id":
                return "Identity Document";
            default:
                return null;
        }
    }
}
