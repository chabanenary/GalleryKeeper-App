package com.example.gallerykeeper.Utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import com.example.gallerykeeper.data.database.AppDatabase;
import com.example.gallerykeeper.data.database.entities.Tag;
import java.util.Queue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class PhotoTaskUtils {
    private static final String TAG = "PhotoTaskUtils";

    private static final ExecutorService databaseExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "PhotoTaskUtils-DB");
        t.setDaemon(true);
        return t;
    });

    private static final int MAX_QUEUE_SIZE = 100;

    public interface CopyResultCallback {
        void onCopyResult(CopyResult result);
    }

    public static void addUriToQueue(Queue<Uri> queue, Uri uri) {
        while (queue.size() >= MAX_QUEUE_SIZE) {
            queue.poll();
        }
        queue.add(uri);
    }

    public static void moveUriToTagFolder(Context context, String userName, Uri imageUri, String tagName, CopyResultCallback callback) {
        if (callback == null) {
            Log.w(TAG, "moveUriToTagFolder: callback is null");
            return;
        }
        if (imageUri == null) {
            callback.onCopyResult(new CopyResult(false, null));
            return;
        }
        getFolderNameByTagName(context, userName, tagName, folderName -> {
            if (folderName != null) {
                String fileName = PhotoFolderManager.getFileNameFromUri(context, imageUri);
                if (fileName == null) {
                    callback.onCopyResult(new CopyResult(false, null));
                    return;
                }
                CopyResult result = PhotoFolderManager.copyImageToFolder(context, imageUri, folderName, fileName);
                callback.onCopyResult(result);
            } else {
                callback.onCopyResult(new CopyResult(false, null));
            }
        });
    }

    private static void getFolderNameByTagName(Context context, String userName, String tagName, Consumer<String> onComplete) {
        if (context == null) {
            if (onComplete != null) onComplete.accept(null);
            return;
        }
        if (tagName == null || tagName.isEmpty()) {
            if (onComplete != null) onComplete.accept(null);
            return;
        }
        databaseExecutor.execute(() -> {
            try {
                AppDatabase db = AppDatabase.getInstance(context, userName);
                Tag tag = db.tagDao().getTagByName(tagName);
                String folderName = null;
                if (tag != null) {
                    folderName = tag.getFolderName();
                    Log.d(TAG, "getFolderNameByTagName " + tagName + " -> " + folderName);
                } else {
                    Log.d(TAG, "Tag non trouvé pour: " + tagName);
                }
                if (onComplete != null) {
                    onComplete.accept(folderName);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error getting folder name for tag: " + tagName, e);
                if (onComplete != null) {
                    onComplete.accept(null);
                }
            }
        });
    }
}
