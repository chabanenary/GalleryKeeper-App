package com.example.gallerykeeper.ui.monitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageDecoder;
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
import com.example.gallerykeeper.MainActivity;
import com.example.gallerykeeper.R;
import com.example.gallerykeeper.Utils.PhotoPredictor;
import com.example.gallerykeeper.Utils.PhotoTaskUtils;
import com.example.gallerykeeper.Utils.UserPrefs;
import com.example.gallerykeeper.Utils.MediaStoreUriValidator;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class MonitorForegroundService extends Service {
    private static final String TAG = "MonitorService";
    private static final String CHANNEL_ID = "monitor_channel";
    public static final String ACTION_MONITORING_STATE = "com.example.gallerykeeper.MONITORING_STATE";
    public static final String EXTRA_ACTIVE = "active";
    private static final String PREFS_NAME = "monitor_prefs";
    private static final String PREF_KEY_ACTIVE = "active";
    // true quand Android a arrêté la surveillance : l'app la relance à sa prochaine ouverture
    private static final String PREF_KEY_RESTART_ON_OPEN = "restart_on_open";
    private static final String ALERT_CHANNEL_ID = "monitor_alert_channel";
    private static final int ALERT_NOTIFICATION_ID = 2;

    private ContentObserver galleryObserver;
    private PhotoPredictor predictor;
    private Handler mainHandler;
    private final AtomicBoolean isDestroyed = new AtomicBoolean(false);
    // true si Android a arrêté ou refusé la surveillance (onTimeout, startForeground refusé)
    private boolean pausedByAndroid = false;

    private final Queue<Uri> lastProcessedPhotoQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Uri> lastTargetProcessedPhotoQueue = new ConcurrentLinkedQueue<>();
    private final Queue<Uri> newPhotoQueue = new ConcurrentLinkedQueue<>();

    private final Set<Uri> urisBeingProcessed = ConcurrentHashMap.newKeySet();
    private final Map<Uri, Integer> retryCount = new ConcurrentHashMap<>();

    private static final long PROCESS_DELAY_MS = 800L;
    private static final int MAX_DECODE_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000L;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        try {
            startForeground(1, getNotification());
        } catch (IllegalStateException e) {
            // ForegroundServiceStartNotAllowedException (Android 12+) : démarrage refusé en arrière-plan
            // (ex. relance START_STICKY), ou limite de 6 h déjà atteinte (Android 15+).
            // Plutôt que de planter : on s'arrête et on relancera à la prochaine ouverture de l'app.
            Log.e(TAG, "startForeground refusé par Android", e);
            pauseUntilNextAppOpen();
            return;
        }
        // Démarrage réussi : plus de relance en attente, on retire l'éventuelle alerte "en pause"
        setRestartOnOpen(this, false);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        if (notificationManager != null) {
            notificationManager.cancel(ALERT_NOTIFICATION_ID);
        }
        publishMonitoringState(true);

        try {
            predictor = new PhotoPredictor(this);
            mainHandler = new Handler(Looper.getMainLooper());
            registerGalleryObserver();
        } catch (Exception e) {
            Log.e(TAG, "Failed to initialize service", e);
            publishMonitoringState(false);
            stopSelf();
        }
    }

    private void publishMonitoringState(boolean active) {
        try {
            SharedPreferences sp = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            sp.edit().putBoolean(PREF_KEY_ACTIVE, active).apply();
        } catch (Exception e) {
            Log.w(TAG, "Prefs monitoring state fail", e);
        }
        Intent i = new Intent(ACTION_MONITORING_STATE);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_ACTIVE, active);
        sendBroadcast(i);
    }

    private void registerGalleryObserver() {
        galleryObserver = new ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                super.onChange(selfChange, uri);
                if (isDestroyed.get()) return;

                if (uri == null) {
                    Log.w(TAG, "URI de la nouvelle photo est null dans onChange.");
                    return;
                }

                if (isInvalidMediaUri(uri)) {
                    Log.d(TAG, "URI générique ignorée: " + uri);
                    return;
                }
                Log.d(TAG, "ContentObserver onChange: URI détectée: " + uri);

                if (lastProcessedPhotoQueue.contains(uri) ||
                        lastTargetProcessedPhotoQueue.contains(uri)) {
                    Log.d(TAG, "URI " + uri + " récemment traitée, ignorée.");
                    return;
                }

                // Atomic add: only proceed if URI was not already being processed
                if (urisBeingProcessed.add(uri)) {
                    PhotoTaskUtils.addUriToQueue(newPhotoQueue, uri);
                    Log.d(TAG, "Nouvelle photo ajoutée à newPhotoQueue: " + uri);
                    processNextInQueue();
                } else {
                    Log.d(TAG, "URI " + uri + " déjà en cours de traitement, ignorée.");
                }
            }
        };
        getContentResolver().registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                galleryObserver
        );
        Log.d(TAG, "GalleryObserver enregistré.");
    }

    private void processNextInQueue() {
        if (isDestroyed.get()) return;

        Uri uriToProcess = newPhotoQueue.poll();
        if (uriToProcess == null) {
            Log.d(TAG, "newPhotoQueue est vide.");
            return;
        }

        if (isInvalidMediaUri(uriToProcess)) {
            Log.w(TAG, "URI invalide ignorée lors du poll: " + uriToProcess);
            urisBeingProcessed.remove(uriToProcess);
            // Use handler post instead of recursion to avoid stack overflow
            mainHandler.post(this::processNextInQueue);
            return;
        }

        if (lastProcessedPhotoQueue.contains(uriToProcess) || lastTargetProcessedPhotoQueue.contains(uriToProcess)) {
            Log.d(TAG, "URI " + uriToProcess + " déjà traitée, ignorée.");
            urisBeingProcessed.remove(uriToProcess);
            mainHandler.post(this::processNextInQueue);
            return;
        }

        Log.d(TAG, "Préparation du traitement pour: " + uriToProcess);
        processNewPhotoWithDelay(uriToProcess);
    }

    private void processNewPhotoWithDelay(final Uri uri) {
        mainHandler.postDelayed(() -> {
            if (isDestroyed.get()) {
                urisBeingProcessed.remove(uri);
                return;
            }

            Log.d(TAG, "Exécution de processNewPhoto pour (après délai): " + uri);

            if (isInvalidMediaUri(uri)) {
                Log.w(TAG, "URI invalide détectée pendant le traitement: " + uri);
                urisBeingProcessed.remove(uri);
                processNextInQueue();
                return;
            }

            if (lastProcessedPhotoQueue.contains(uri) || lastTargetProcessedPhotoQueue.contains(uri)) {
                Log.d(TAG, "URI " + uri + " déjà traitée, ignorée.");
                urisBeingProcessed.remove(uri);
                processNextInQueue();
                return;
            }

            Bitmap bitmap = null;
            try {
                bitmap = decodeBitmapFromUri(getContentResolver(), uri);
                if (bitmap == null) {
                    int attempts = retryCount.getOrDefault(uri, 0);
                    if (attempts < MAX_DECODE_RETRIES) {
                        retryCount.put(uri, attempts + 1);
                        Log.w(TAG, "Bitmap null pour " + uri + ", retry " + (attempts + 1) + "/" + MAX_DECODE_RETRIES);
                        mainHandler.postDelayed(() -> processNewPhotoWithDelay(uri), RETRY_DELAY_MS);
                        return;
                    }
                    Log.e(TAG, "Bitmap toujours null après " + MAX_DECODE_RETRIES + " tentatives pour: " + uri);
                    retryCount.remove(uri);
                    urisBeingProcessed.remove(uri);
                    processNextInQueue();
                    return;
                }
                retryCount.remove(uri);

                String tag = predictor.predict(bitmap);
                if (tag != null) {
                    String userName = UserPrefs.getUserEmail(this);
                    if (userName == null) {
                        Log.w(TAG, "Aucun userEmail trouvé, impossible de traiter.");
                        urisBeingProcessed.remove(uri);
                        processNextInQueue();
                        return;
                    }
                    Log.d(TAG, "Tag détecté pour " + uri + ": " + tag);
                    PhotoTaskUtils.moveUriToTagFolder(this, userName, uri, tag, result -> {
                        if (isDestroyed.get()) {
                            urisBeingProcessed.remove(uri);
                            return;
                        }
                        if (result.isSuccess()) {
                            Log.d(TAG, "Photo déplacée avec succès: " + uri + " vers dossier " + tag);
                            PhotoTaskUtils.addUriToQueue(lastProcessedPhotoQueue, uri);
                            PhotoTaskUtils.addUriToQueue(lastTargetProcessedPhotoQueue, result.getTargetUri());
                            PendingDeletionRepository.enqueue(getApplicationContext(), uri);
                            PendingDeletionRepository.broadcastUpdate(MonitorForegroundService.this);
                        } else {
                            Log.w(TAG, "Échec du déplacement pour " + uri);
                        }
                        urisBeingProcessed.remove(uri);
                        processNextInQueue();
                    });
                } else {
                    Log.d(TAG, "Aucun tag détecté pour " + uri);
                    urisBeingProcessed.remove(uri);
                    processNextInQueue();
                }
            } catch (OutOfMemoryError oom) {
                Log.e(TAG, "OutOfMemory lors du décodage de " + uri, oom);
                urisBeingProcessed.remove(uri);
                processNextInQueue();
            } catch (IOException e) {
                Log.e(TAG, "IOException lors du traitement de " + uri, e);
                urisBeingProcessed.remove(uri);
                processNextInQueue();
            } catch (Exception e) {
                Log.e(TAG, "Exception inattendue lors du traitement de " + uri, e);
                urisBeingProcessed.remove(uri);
                processNextInQueue();
            } finally {
                if (bitmap != null) {
                    bitmap.recycle();
                }
            }
        }, PROCESS_DELAY_MS);
    }

    @Nullable
    private static Bitmap decodeBitmapFromUri(ContentResolver resolver, Uri uri) throws IOException {
        if (resolver == null || uri == null) {
            return null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                ImageDecoder.Source source = ImageDecoder.createSource(resolver, uri);
                return ImageDecoder.decodeBitmap(source, (decoder, info, src) -> {
                    decoder.setAllocator(ImageDecoder.ALLOCATOR_SOFTWARE);
                });
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "ImageDecoder a échoué, fallback BitmapFactory pour: " + uri, e);
            }
        }

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) {
                return null;
            }
            return BitmapFactory.decodeStream(in);
        }
    }

    private Notification getNotification() {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Surveillance de la galerie")
                .setContentText("GalleryKeeper surveille vos nouvelles photos.")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void createNotificationChannel() {
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

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (pausedByAndroid) {
            // Démarrage refusé dans onCreate : ne pas rester actif ni être relancé par le système
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    // Android 15+ : le type mediaProcessing est limité à 6 h par 24 h. Le système appelle onTimeout
    // et l'app plante si le service ne s'arrête pas dans les secondes qui suivent.
    // Une relance immédiate serait refusée tant que l'app n'est pas revenue au premier plan :
    // on s'arrête, on prévient l'utilisateur, et l'app relance la surveillance à sa prochaine ouverture.
    @Override
    public void onTimeout(int startId, int fgsType) {
        Log.w(TAG, "onTimeout : limite Android atteinte (type " + fgsType + "), surveillance mise en pause.");
        pauseUntilNextAppOpen();
    }

    private void pauseUntilNextAppOpen() {
        pausedByAndroid = true;
        setRestartOnOpen(this, true);
        showPausedNotification();
        stopSelf();
    }

    private void showPausedNotification() {
        try {
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager == null) return;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                        ALERT_CHANNEL_ID,
                        getString(R.string.monitor_paused_channel_name),
                        NotificationManager.IMPORTANCE_DEFAULT
                );
                manager.createNotificationChannel(channel);
            }
            // Ouvre l'app comme depuis l'icône (ramène la tâche existante sans la recréer)
            Intent openApp = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (openApp == null) {
                openApp = new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            }
            PendingIntent contentIntent = PendingIntent.getActivity(
                    this, 0, openApp, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            String text = getString(R.string.monitor_paused_text);
            Notification notification = new NotificationCompat.Builder(this, ALERT_CHANNEL_ID)
                    .setContentTitle(getString(R.string.monitor_paused_title))
                    .setContentText(text)
                    .setStyle(new NotificationCompat.BigTextStyle().bigText(text))
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentIntent(contentIntent)
                    .setAutoCancel(true)
                    .build();
            manager.notify(ALERT_NOTIFICATION_ID, notification);
        } catch (Exception e) {
            // Ex. notifications refusées par l'utilisateur : la relance à l'ouverture fonctionne quand même
            Log.w(TAG, "Notification de pause non affichée", e);
        }
    }

    /** true si Android a arrêté la surveillance et qu'elle doit être relancée à la prochaine ouverture de l'app. */
    public static boolean shouldRestartOnOpen(Context context) {
        return context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE).getBoolean(PREF_KEY_RESTART_ON_OPEN, false);
    }

    private static void setRestartOnOpen(Context context, boolean restart) {
        // commit() et non apply() : le processus peut être tué juste après l'arrêt du service
        context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putBoolean(PREF_KEY_RESTART_ON_OPEN, restart)
                .commit();
    }

    @Override
    public void onDestroy() {
        isDestroyed.set(true);
        // Cancel all pending handler callbacks to prevent post-destroy crashes
        if (mainHandler != null) {
            mainHandler.removeCallbacksAndMessages(null);
        }
        if (galleryObserver != null) {
            getContentResolver().unregisterContentObserver(galleryObserver);
        }
        if (predictor != null) {
            predictor.close();
        }
        publishMonitoringState(false);
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isInvalidMediaUri(@Nullable Uri uri) {
        return uri == null || !MediaStoreUriValidator.isValidMediaStoreItemUri(uri.toString());
    }
}
