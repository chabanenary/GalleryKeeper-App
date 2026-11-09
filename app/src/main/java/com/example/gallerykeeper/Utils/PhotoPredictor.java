package com.example.gallerykeeper.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

public class PhotoPredictor {
    private static final String TAG = "PhotoPredictor";
    // Logs de diagnostic des scores (même tag que Detector) : filtre Logcat "tag:DiagScores"
    private static final String DIAG_TAG = "DiagScores";
    private static final String MODEL_FILE = "model_KIP.tflite";
    private static final String LABELS_FILE = "labels_KIP.txt";
    // Une seule copie à la fois : le scan et le service peuvent créer un PhotoPredictor en même temps
    private static final Object ASSET_COPY_LOCK = new Object();
    private Detector detector;
    private final Context context;

    public PhotoPredictor(Context context) {
        this.context = context.getApplicationContext();
        copyAssetsIfNeeded();
        try {
            detector = new Detector(this.context, getModelPath(), getLabelsPath());
        } catch (RuntimeException e) {
            // Modèle copié illisible (ex. copie interrompue par une ancienne version de l'app) :
            // on supprime les copies, on recopie depuis les assets et on réessaie une seule fois.
            // Si ça échoue encore, l'exception remonte comme avant cette correction.
            Log.e(TAG, "Chargement du modèle impossible, nouvelle copie depuis les assets", e);
            recopyAssets();
            detector = new Detector(this.context, getModelPath(), getLabelsPath());
        }
        Log.d(DIAG_TAG, String.format(Locale.US, "Seuils de classement : nude > %.2f, child > %.2f, autres > %.2f",
                TagClassifier.NUDE_THRESHOLD, TagClassifier.CHILD_THRESHOLD, TagClassifier.OTHER_THRESHOLD));
    }

    private void copyAssetsIfNeeded() {
        synchronized (ASSET_COPY_LOCK) {
            File modelFile = new File(context.getFilesDir(), MODEL_FILE);
            File labelsFile = new File(context.getFilesDir(), LABELS_FILE);

            if (!modelFile.exists()) {
                Log.d(TAG, "Copying model to " + modelFile.getAbsolutePath());
                copyAssetToFile(MODEL_FILE, modelFile);
            }
            if (!labelsFile.exists()) {
                Log.d(TAG, "Copying labels to " + labelsFile.getAbsolutePath());
                copyAssetToFile(LABELS_FILE, labelsFile);
            }
        }
    }

    // Supprime les copies (modèle + labels) puis les recopie depuis les assets
    private void recopyAssets() {
        synchronized (ASSET_COPY_LOCK) {
            File modelFile = new File(context.getFilesDir(), MODEL_FILE);
            File labelsFile = new File(context.getFilesDir(), LABELS_FILE);
            if (modelFile.exists() && !modelFile.delete()) {
                Log.w(TAG, "Suppression impossible de " + modelFile.getAbsolutePath());
            }
            if (labelsFile.exists() && !labelsFile.delete()) {
                Log.w(TAG, "Suppression impossible de " + labelsFile.getAbsolutePath());
            }
            copyAssetsIfNeeded();
        }
    }

    private void copyAssetToFile(String assetName, File outFile) {
        // Copie atomique : jamais de fichier final tronqué, même si l'app est tuée pendant la copie
        try (InputStream in = context.getAssets().open(assetName)) {
            AtomicFileCopy.copy(in, outFile);
        } catch (IOException e) {
            Log.e(TAG, "Erreur lors de la copie de l'asset " + assetName, e);
        }
    }

    private String getModelPath() {
        return new File(context.getFilesDir(), MODEL_FILE).getAbsolutePath();
    }

    private String getLabelsPath() {
        return new File(context.getFilesDir(), LABELS_FILE).getAbsolutePath();
    }

    public String predict(Bitmap bitmap) {
        if (bitmap == null || detector == null) {
            Log.w(TAG, "predict: bitmap or detector is null");
            return null;
        }

        List<BoundingBox> boundingBoxes = detector.detect(bitmap);
        logDetections(boundingBoxes);
        if (boundingBoxes == null || boundingBoxes.isEmpty()) {
            Log.d(TAG, "No object detected in the image");
            return null;
        }

        String tagName = TagClassifier.decideTagName(boundingBoxes);
        Log.d(TAG, "Detected Tag Name: " + tagName);
        return tagName;
    }

    // Diagnostic : boîtes retenues après NMS (celles qu'examine TagClassifier), avec leur score
    private static void logDetections(List<BoundingBox> boxes) {
        if (boxes == null || boxes.isEmpty()) {
            Log.d(DIAG_TAG, "Boîtes retenues : aucune");
            return;
        }
        StringBuilder sb = new StringBuilder("Boîtes retenues :");
        for (BoundingBox box : boxes) {
            sb.append(' ').append(box.clsName).append('=').append(String.format(Locale.US, "%.2f", box.cnf));
        }
        Log.d(DIAG_TAG, sb.toString());
    }

    public void close() {
        if (detector != null) {
            detector.close();
            detector = null;
        }
    }

    public static void showNotification(Context context, String title, String message) {
        String channelId = "photo_action_channel";
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager == null) return;

        android.app.NotificationChannel channel = new android.app.NotificationChannel(
                channelId, "Actions photos", android.app.NotificationManager.IMPORTANCE_DEFAULT);
        notificationManager.createNotificationChannel(channel);

        android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setAutoCancel(true)
                .build();
        notificationManager.notify((int) System.currentTimeMillis(), notification);
    }
}
