package com.example.gallerykeeper.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class PhotoPredictor {
    private static final String TAG = "PhotoPredictor";
    private static final String MODEL_FILE = "model_KIP.tflite";
    private static final String LABELS_FILE = "labels_KIP.txt";
    private Detector detector;
    private final Context context;

    public PhotoPredictor(Context context) {
        this.context = context.getApplicationContext();
        copyAssetsIfNeeded();
        detector = new Detector(this.context, getModelPath(), getLabelsPath());
    }

    private void copyAssetsIfNeeded() {
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

    private void copyAssetToFile(String assetName, File outFile) {
        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
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
        if (boundingBoxes == null || boundingBoxes.isEmpty()) {
            Log.d(TAG, "No object detected in the image");
            return null;
        }

        String tagName = TagClassifier.decideTagName(boundingBoxes);
        Log.d(TAG, "Detected Tag Name: " + tagName);
        return tagName;
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
