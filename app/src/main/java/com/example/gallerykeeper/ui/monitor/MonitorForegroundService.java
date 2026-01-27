package com.example.gallerykeeper.ui.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.database.ContentObserver;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import com.example.gallerykeeper.R;
import com.example.gallerykeeper.Utils.PhotoPredictor;
import com.example.gallerykeeper.Utils.PhotoTaskUtils;
import com.example.gallerykeeper.Utils.UserPrefs;

import java.io.IOException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MonitorForegroundService extends Service {
    private static final String TAG = "MonitorService";
    private static final String CHANNEL_ID = "monitor_channel";
    public static final String ACTION_MONITORING_STATE = "com.example.gallerykeeper.MONITORING_STATE";
    public static final String EXTRA_ACTIVE = "active";
    private static final String PREFS_NAME = "monitor_prefs";
    private static final String PREF_KEY_ACTIVE = "active";

    private ContentObserver galleryObserver;
    private PhotoPredictor predictor;
    private Handler mainHandler;

    private Uri lastProcessedUri;
    private Uri lastTargetProcessedUri;
    private Uri newPhotoQueueUri;

    private final Queue<Uri> lastProcessedPhotoQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Uri> lastTargetProcessedPhotoQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Uri> newPhotoQueue = new ConcurrentLinkedQueue<>();

    // Petit délai pour laisser l'appareil photo finaliser l'écriture

    private final Set<Uri> urisBeingProcessed = new ConcurrentHashMap<Uri, Boolean>().keySet(true); // Pour marquer les URIs en cours de traitement

    private static final long PROCESS_DELAY_MS = 300L;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(1, getNotification());
        publishMonitoringState(true);

        lastProcessedUri = null; // Initialiser l'URI traité
        lastTargetProcessedUri = null; // Initialiser l'URI cible traité
        newPhotoQueueUri = null; // Initialiser l'URI de la nouvelle photo

        predictor = new PhotoPredictor(this);
        mainHandler = new Handler(Looper.getMainLooper());
        registerGalleryObserver();
    }

    private void publishMonitoringState(boolean active) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            sp.edit().putBoolean(PREF_KEY_ACTIVE, active).apply();
        } catch (Exception e) {
            Log.w(TAG, "Prefs monitoring state fail", e);
        }
        Intent i = new Intent(ACTION_MONITORING_STATE);
        i.putExtra(EXTRA_ACTIVE, active);
        sendBroadcast(i);
    }



    private void registerGalleryObserver() {
        galleryObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                if (uri != null) {
                    if (!isSpecificMediaItemUri(uri)) {
                        Log.d(TAG, "URI générique ignorée: " + uri);
                        return;
                    }
                    Log.d(TAG, "ContentObserver onChange: URI détectée: " + uri);

                    //  Vérification PRIMAIRE pour éviter de traiter une URI déjà en cours ou très récemment traitée
                    //  Cette vérification est cruciale pour éviter de multiples postDelayed pour la même URI.
                    if (urisBeingProcessed.contains(uri) ||
                            lastProcessedPhotoQueue.contains(uri) ||
                            lastTargetProcessedPhotoQueue.contains(uri)) {
                        Log.d(TAG, "URI " + uri + " est déjà en cours de traitement ou récemment traitée, ignorée initialement.");
                        return;
                    }

                    // Si l'URI n'est pas déjà marquée, l'ajouter à la file pour traitement.
                    // Et la marquer comme "en cours de traitement" pour éviter que des onChange rapprochés
                    // ne la repostent immédiatement.
                    // Il faudra la retirer de urisBeingProcessed une fois le traitement terminé ou échoué.
                    PhotoTaskUtils.addUriToQueue(newPhotoQueue, uri);
                    urisBeingProcessed.add(uri); // Marquer comme en cours de traitement
                    Log.d(TAG, "Monitoring service: Nouvelle photo ajoutée à newPhotoQueue: " + uri);
                    processNextInQueue(); // Traiter la file

                } else {
                    Log.w(TAG, "URI de la nouvelle photo est null dans onChange.");
                }
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true, // true pour notifier aussi les descendants, ce qui peut causer des notifications multiples
                galleryObserver
        );
        Log.d(TAG, "GalleryObserver enregistré.");
    }


    private void processNextInQueue() {
        Uri uriToProcess = newPhotoQueue.poll();
        if (uriToProcess != null) {
            // La vérification des files lastProcessedPhotoQueue/lastTargetProcessedPhotoQueue
            // est redondante ici si urisBeingProcessed est bien géré, mais ne fait pas de mal.
            if (lastProcessedPhotoQueue.contains(uriToProcess) || lastTargetProcessedPhotoQueue.contains(uriToProcess)) {
                Log.d(TAG, "URI " + uriToProcess + " trouvée dans lastProcessed/Target queues avant postDelayed, ignorée.");
                urisBeingProcessed.remove(uriToProcess); // Nettoyer le marquage
                processNextInQueue(); // Vérifier s'il y a autre chose
                return;
            }
            Log.d(TAG, "Préparation du traitement pour: " + uriToProcess);
            processNewPhotoWithDelay(uriToProcess);
        } else {
            Log.d(TAG, "newPhotoQueue est vide.");
        }
    }


    private void processNewPhotoWithDelay(final Uri uri) { // Renommée pour clarifier
        mainHandler.postDelayed(() -> {
            Log.d(TAG, "Exécution de processNewPhoto pour (après délai): " + uri);

            if (!isSpecificMediaItemUri(uri)) {
                Log.w(TAG, "URI invalide détectée pendant le traitement, abandonnée: " + uri);
                urisBeingProcessed.remove(uri);
                processNextInQueue();
                return;
            }

            // Vérification finale avant traitement lourd, au cas où elle aurait été traitée
            // par un autre chemin pendant le délai (peu probable avec la synchro en amont mais sécuritaire)
            if (lastProcessedPhotoQueue.contains(uri) || lastTargetProcessedPhotoQueue.contains(uri)) {
                Log.d(TAG, "URI " + uri + " trouvée dans lastProcessed/Target queues DANS postDelayed, ignorée.");
                urisBeingProcessed.remove(uri); // Nettoyer
                processNextInQueue(); // Vérifier s'il y a autre chose
                return;
            }

            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                if (bitmap == null) {
                    Log.e(TAG, "Bitmap est null pour URI: " + uri);
                    urisBeingProcessed.remove(uri); // Nettoyer
                    processNextInQueue(); // Suivant
                    return;
                }

                String tag = predictor.predict(bitmap);
                if (tag != null) {

                    String userName = UserPrefs.getUserEmail(this);
                    if (userName == null) {
                        Log.w(TAG, "Aucun userEmail trouvé dans UserPrefs, impossible de déterminer la base.");
                        urisBeingProcessed.remove(uri);
                        processNextInQueue();
                        return;
                    }
                    Log.d(TAG,"userName pour le déplacement: " + userName);
                    Log.d(TAG, "Tag détecté pour " + uri + ": " + tag);
                    PhotoTaskUtils.moveUriToTagFolder(this, userName,  uri, tag, result -> {
                        if (result.isSuccess()) {
                            Log.d(TAG, "Photo déplacée avec succès: " + uri + " vers dossier " + tag);
                            PhotoTaskUtils.addUriToQueue(lastProcessedPhotoQueue, uri);
                            PhotoTaskUtils.addUriToQueue(lastTargetProcessedPhotoQueue, result.getTargetUri());
                            PendingDeletionRepository.enqueue(getApplicationContext(), uri);
                            PendingDeletionRepository.broadcastUpdate(MonitorForegroundService.this);
                            Log.d(TAG, "Ajout de " + uri + " au dépôt des suppressions en attente.");
                            PhotoPredictor.showNotification(this, "Photo déplacée", "...");
                        } else {
                            Log.w(TAG, "Échec du déplacement pour " + uri + " vers dossier " + tag);
                            PhotoPredictor.showNotification(this, "Déplacement échoué", "...");
                        }
                        urisBeingProcessed.remove(uri); // Traitement terminé (succès ou échec du déplacement)
                        processNextInQueue(); // Traiter la prochaine photo dans la file
                    });
                } else {
                    Log.d(TAG, "Aucun tag détecté pour " + uri);
                    // Même si aucun tag, l'URI a été "traitée" (tentative de prédiction faite).
                    // On pourrait l'ajouter à une file "non classifiée" ou simplement la retirer de beingProcessed.
                    urisBeingProcessed.remove(uri);
                    processNextInQueue();
                }
            } catch (IOException e) {
                Log.e(TAG, "IOException lors du traitement de " + uri, e);
                PhotoPredictor.showNotification(this, "Erreur", "when processing " + uri);
                urisBeingProcessed.remove(uri);
                processNextInQueue();
            } catch (Exception e) {
                Log.e(TAG, "Exception inattendue lors du traitement de " + uri, e);
                // Gérer autres exceptions
                urisBeingProcessed.remove(uri);
                processNextInQueue();
            }
        }, PROCESS_DELAY_MS);
    }


    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Surveillance de la galerie")
                .setContentText("GalleryKeeper surveille vos nouvelles photos.")
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Correction de l'icône
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Surveillance Galerie",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (galleryObserver != null) {
            getContentResolver().unregisterContentObserver(galleryObserver);
        }
        publishMonitoringState(false);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isSpecificMediaItemUri(Uri uri) {
        if (uri == null) {
            return false;
        }
        if (!"content".equals(uri.getScheme())) {
            return false;
        }
        try {
            ContentUris.parseId(uri);
            return true;
        } catch (NumberFormatException | UnsupportedOperationException e) {
            return false;
        }
    }
}
