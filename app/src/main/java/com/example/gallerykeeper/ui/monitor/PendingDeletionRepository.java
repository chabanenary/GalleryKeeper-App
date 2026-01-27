package com.example.gallerykeeper.ui.monitor;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stocke les URIs à supprimer lorsque le service tourne sans UI.
 * File persistante accessible par le service et le fragment.
 */
public final class PendingDeletionRepository {

    public static final String ACTION_PENDING_DELETIONS_UPDATED = "com.example.gallerykeeper.ACTION_PENDING_DELETIONS_UPDATED";
    public static final String EXTRA_PENDING_COUNT = "extra_pending_count";

    private static final String PREFS_NAME = "pending_deletions_prefs";
    private static final String KEY_QUEUE = "pending_queue";
    private static final String TAG = "PendingDeletionRepo";

    private PendingDeletionRepository() {
        // Utilitaire
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public static synchronized void enqueue(Context context, Uri uri) {
        if (uri == null) return;
        List<Uri> queue = getAllInternal(context);
        queue.add(uri);
        save(context, queue);
    }

    public static synchronized void enqueueAll(Context context, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        List<Uri> queue = getAllInternal(context);
        queue.addAll(uris);
        save(context, queue);
    }

    /**
     * Réinsère des éléments en tête de file (avant ceux stockés par le service).
     */
    public static synchronized void prependAll(Context context, List<Uri> uris) {
        if (uris == null || uris.isEmpty()) return;
        List<Uri> queue = getAllInternal(context);
        List<Uri> merged = new ArrayList<>(uris.size() + queue.size());
        merged.addAll(uris);
        merged.addAll(queue);
        save(context, dedupe(merged));
    }

    public static synchronized List<Uri> drain(Context context) {
        List<Uri> queue = new ArrayList<>(getAllInternal(context));
        save(context, new ArrayList<>());
        return queue;
    }

    public static synchronized int count(Context context) {
        return getAllInternal(context).size();
    }

    public static void broadcastUpdate(Context context) {
        Context appContext = context.getApplicationContext();
        Intent intent = new Intent(ACTION_PENDING_DELETIONS_UPDATED);
        intent.setPackage(appContext.getPackageName());
        intent.putExtra(EXTRA_PENDING_COUNT, count(appContext));
        appContext.sendBroadcast(intent);
    }

    private static List<Uri> getAllInternal(Context context) {
        String json = prefs(context).getString(KEY_QUEUE, "[]");
        ArrayList<Uri> uris = new ArrayList<>();
        if (json == null || json.isEmpty()) {
            return uris;
        }
        try {
            JSONArray array = new JSONArray(json);
            for (int i = 0; i < array.length(); i++) {
                String value = array.optString(i, null);
                if (value != null && !value.isEmpty()) {
                    uris.add(Uri.parse(value));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Echec lecture file pending", e);
        }
        return uris;
    }

    private static void save(Context context, List<Uri> queue) {
        JSONArray array = new JSONArray();
        if (queue != null) {
            for (Uri uri : queue) {
                if (uri != null) {
                    array.put(uri.toString());
                }
            }
        }
        prefs(context).edit().putString(KEY_QUEUE, array.toString()).apply();
    }

    private static List<Uri> dedupe(List<Uri> source) {
        ArrayList<Uri> deduped = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        if (source == null) {
            return deduped;
        }
        for (Uri uri : source) {
            if (uri == null) continue;
            String key = uri.toString();
            if (seen.add(key)) {
                deduped.add(uri);
            }
        }
        return deduped;
    }
}

