package com.example.gallerykeeper.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.util.Log;

import org.tensorflow.lite.Interpreter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

public class PhotoPredictor {
    private static final String TAG = "PhotoPredictor";
    private static final String MODEL_FILE = "model_KIP.tflite";
    private static final String LABELS_FILE = "labels_KIP.txt";
    private Interpreter interpreter;
    private Detector detector;
    private Context context;

    public PhotoPredictor(Context context) {
        this.context = context;
        copyAssetsIfNeeded();
        detector = new Detector(context, getModelPath(), getLabelsPath());
    }

    private void copyAssetsIfNeeded() {
        File modelFile = new File(context.getFilesDir(), MODEL_FILE);
        File labelsFile = new File(context.getFilesDir(), LABELS_FILE);

        Log.d(TAG, "copy Assets "+ modelFile.getAbsolutePath() + " - " + labelsFile.getAbsolutePath());

        if (!modelFile.exists()) {
            Log.d(TAG, "copy model "+ modelFile.getAbsolutePath());
            copyAssetToFile(MODEL_FILE, modelFile);
        }
        if (!labelsFile.exists()) {
            Log.d(TAG, "copy label "+  labelsFile.getAbsolutePath());
            copyAssetToFile(LABELS_FILE, labelsFile);
        }
    }

    private void copyAssetToFile(String assetName, File outFile) {
        try (InputStream in = context.getAssets().open(assetName);
             FileOutputStream out = new FileOutputStream(outFile)) {
            byte[] buffer = new byte[1024];
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



    public String predict(Bitmap bitmap){

        // Preprocess the image
        // Run inference
        // Postprocess the result
        String tagName = null; // Initialiser le tagName à null

        // Bitmap mutableBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap mutableBitmap = bitmap;
        if (mutableBitmap == null) {
            Log.d("mutableBitmap is null", "mutableBitmap is null");
        } else {
            Log.d("mutableBitmap is not null", "mutableBitmap is not null");

            List<BoundingBox> boundingBoxes = detector.detect(mutableBitmap);
            if (boundingBoxes == null) {
                Log.d("Prediction in Task", "No object detected in the image");
            } else {
                //trouver le meilleur boundingbox
                BoundingBox highestConfidenceBox = null;
                float highestConfidence = -1.0f;


                for (BoundingBox box : boundingBoxes) {

                    Log.d("monitoring or scan", "Detection: Class: " + box.clsName + ", Confidence: " + box.cnf);

                    if ("nude".equals(box.clsName) && box.cnf > 0.4) {

                        highestConfidenceBox = box;
                        Log.d("Scan or monitor ", "Detection important: Class: " + box.clsName + ", Confidence: " + box.cnf);
                        break;
                    } else if ("child".equals(box.clsName) && box.cnf > 0.4) {
                        highestConfidenceBox = box;
                        Log.d("Scan or monitor", "Detection second important: Class: " + box.clsName + ", Confidence: " + box.cnf);
                        break;
                    } else {
                        if (box.cnf > highestConfidence && box.cnf > 0.5) {
                            highestConfidence = box.cnf;
                            highestConfidenceBox = box;
                        }
                        Log.d("Scan or monitor", "Highest Confidence Detection: Class: " + box.clsName + ", Confidence: " + box.cnf);
                    }

                }
                if (highestConfidenceBox == null) {
                    Log.d("Scan Task", "No high confidence bounding boxes detected.");
                } else {
                    //trouver le tagName correspondant à la boundingBox
                    switch (highestConfidenceBox.clsName) {
                        case "nude":
                            tagName = "Nude";
                            break;
                        case "child":
                            tagName = "Children";
                            break;
                        case "creditcard":
                            tagName = "Credit Card";
                            break;
                        case "id":
                            tagName = "Identity Document";
                            break;
                    }
                    Log.d("Scan Task", "Detected Tag Name in the pict: " + tagName);

                }
            }
        }
        return tagName;
    }
    public String predict_old(Bitmap bitmap) {
        Log.d(TAG, "prediction: ");
        if (detector == null)
        {
            Log.d(TAG, "prediction: detector null");
            return null;
        }
        Log.d(TAG, "prediction with detector:");

        List<BoundingBox> boxes = detector.detect(bitmap);
        if (boxes == null || boxes.isEmpty()) return null;
        BoundingBox best = null;
        float bestCnf = -1f;
        for (BoundingBox box : boxes) {
            if (box.cnf > bestCnf) {
                bestCnf = box.cnf;
                best = box;
            }
        }
        if (best == null) return null;
        switch (best.clsName) {
            case "nude": return "Nude";
            case "child": return "Children";
            case "creditcard": return "Credit Card";
            case "id": return "Identity Document";
            default: return null;
        }
    }

    // Utilitaire pour afficher une notification système
    public static void showNotification(Context context, String title, String message) {
        String channelId = "photo_action_channel";
        android.app.NotificationManager notificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.NotificationChannel channel = new android.app.NotificationChannel(channelId, "Actions photos", android.app.NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
        }
        android.app.Notification notification = new androidx.core.app.NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_gallery)
                .setAutoCancel(true)
                .build();
        notificationManager.notify((int) System.currentTimeMillis(), notification);
    }
}
