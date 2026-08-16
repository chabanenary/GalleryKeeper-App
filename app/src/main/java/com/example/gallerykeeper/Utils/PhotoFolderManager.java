package com.example.gallerykeeper.Utils;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class PhotoFolderManager {


    private static final String TAG = "PhotoFolderManager";


    public static void createPhotoFolderWithPlaceholder(Context context, String folderName, String placeholderFileName) {
        if (context == null || folderName == null || placeholderFileName == null) {
            Log.e("PhotoFolderManager", "Paramètres invalides pour la création du dossier.");
            return;
        }

        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        Uri imageUri = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android Q et versions ultérieures
            String relativePath = Environment.DIRECTORY_PICTURES + File.separator + folderName;
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, placeholderFileName);
            values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg");
            values.put(MediaStore.MediaColumns.IS_PENDING, 1);

            try {
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (imageUri == null) {
                    Log.e("PhotoFolderManager", "Échec de l'insertion de l'image dans MediaStore.");
                    return;
                }

                // Écrire une image placeholder
                try (OutputStream outputStream = resolver.openOutputStream(imageUri)) {
                    if (outputStream != null) {
                        Bitmap placeholderBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                        placeholderBitmap.eraseColor(android.graphics.Color.LTGRAY);
                        placeholderBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                        placeholderBitmap.recycle();
                    }
                }

                // Marquer l'image comme non en attente
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                resolver.update(imageUri, values, null, null);

                Log.d("PhotoFolderManager", "Dossier créé avec succès : " + relativePath);
            } catch (IOException e) {
                Log.e("PhotoFolderManager", "Erreur lors de l'écriture de l'image placeholder.", e);
                if (imageUri != null) {
                    resolver.delete(imageUri, null, null);
                }
            }
        } else {
            // Versions antérieures à Android Q
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName);
            if (!directory.exists() && !directory.mkdirs()) {
                Log.e("PhotoFolderManager", "Échec de la création du dossier : " + directory.getAbsolutePath());
                return;
            }

            File placeholderFile = new File(directory, placeholderFileName);
            try (OutputStream outputStream = new FileOutputStream(placeholderFile)) {
                Bitmap placeholderBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                placeholderBitmap.eraseColor(android.graphics.Color.LTGRAY);
                placeholderBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                placeholderBitmap.recycle();

                Log.d("PhotoFolderManager", "Dossier créé avec succès : " + directory.getAbsolutePath());
            } catch (IOException e) {
                Log.e("PhotoFolderManager", "Erreur lors de l'écriture de l'image placeholder.", e);
            }
        }
    }



    public static void updateMediaStorePathsForMovedFolder(Context context, String oldFolderPathHint, String newFolderPath) {
        if (context == null || newFolderPath == null) {
            return;
        }

        Log.d(TAG, "Tentative de mise à jour MediaStore pour le dossier : " + newFolderPath);

        ContentResolver resolver = context.getContentResolver();
        Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;

        // Construire le chemin relatif pour la recherche
        String relativePath = Environment.DIRECTORY_PICTURES + File.separator + new File(newFolderPath).getName() + File.separator;

        // Requête pour récupérer les fichiers dans le nouveau dossier
        String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DATA};
        String selection = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
        String[] selectionArgs = {relativePath};

        try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                List<String> pathsToScan = new ArrayList<>();
                int dataColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);

                do {
                    String filePath = cursor.getString(dataColumnIndex);
                    pathsToScan.add(filePath);
                } while (cursor.moveToNext());

                // Scanner les fichiers récupérés
                MediaScannerConnection.scanFile(context.getApplicationContext(),
                        pathsToScan.toArray(new String[0]),
                        null,
                        (path, uri) -> Log.d(TAG, "Fichier scanné : " + path + ", URI : " + uri));
            } else {
                Log.d(TAG, "Aucun fichier trouvé dans MediaStore pour le chemin relatif : " + relativePath);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la mise à jour MediaStore pour le dossier : " + newFolderPath, e);
        }
    }
    /**
     * Tente de mettre à jour le RELATIVE_PATH pour une seule URI de média.
     * Lève une SecurityException (potentiellement RecoverableSecurityException) si la permission est refusée.
     *
     * @param mediaUri        L'URI du fichier multimédia à mettre à jour.
     * @param newRelativePath Le nouveau chemin relatif (ex: "Pictures/MonNouveauDossier/").
     * @return true si la mise à jour a réussi, false sinon (sans lever d'exception de sécurité).
     * @throws SecurityException si la permission est refusée.
     */
    public static boolean updateSingleFileRelativePath(Context context, Uri mediaUri, String newRelativePath) throws SecurityException {
        if (context == null) return false;
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();

        Log.d("UPDATE_DEBUG", "updateSingleFileRelativePath: Tentative pour URI " + mediaUri + " vers RELATIVE_PATH: [" + newRelativePath + "]");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, newRelativePath);
            // Optionnel: si vous voulez aussi changer le nom d'affichage en même temps
            // values.put(MediaStore.MediaColumns.DISPLAY_NAME, "nouveau_nom_fichier.jpg");
        } else {
            // Pour les versions < Q, vous mettriez à jour MediaStore.MediaColumns.DATA
            // et potentiellement renommeriez le fichier physique. Non couvert ici.
            return false;
        }

        try {
            int updatedRows = resolver.update(mediaUri, values, null, null);
            if (updatedRows > 0) {
                Log.d(TAG, "Chemin relatif mis à jour pour " + mediaUri + " vers " + newRelativePath);
                Log.i("UPDATE_DEBUG", "updateSingleFileRelativePath: SUCCÈS! " + updatedRows + " ligne(s) mise(s) à jour pour URI " + mediaUri + ". Nouveau RELATIVE_PATH devrait être [" + newRelativePath + "]");
                return true;
            } else {
                Log.w("UPDATE_DEBUG", "updateSingleFileRelativePath: ÉCHEC. 0 ligne mise à jour pour URI " + mediaUri + ". Le RELATIVE_PATH n'a PAS été changé dans MediaStore par cet appel.");
                return false;
            }
        } catch (SecurityException se) {
            Log.e("UPDATE_DEBUG", "updateSingleFileRelativePath: SecurityException pour URI " + mediaUri, se);
            throw se;
        } catch (Exception e) {
            // Autres exceptions (ex: IllegalArgumentException si l'URI est mauvaise)
            Log.e("UPDATE_DEBUG", "updateSingleFileRelativePath: Exception Générale pour URI " + mediaUri, e);
            return false;
        }
    }

        /**
         * Renomme un dossier photo dans le répertoire Pictures pour Android Q (API 29) et plus.
         * Cette méthode met à jour le MediaStore pour refléter le changement de nom du dossier.
         *
         * @param context      Le contexte de l'application.
         * @param oldFolderName Le nom actuel du dossier.
         * @param newFolderName Le nouveau nom souhaité pour le dossier.
         * @return true si la mise à jour du MediaStore a été initiée pour au moins un fichier, false sinon.
         */
        @RequiresApi(api = Build.VERSION_CODES.Q)
        public static boolean renamePhotoFolderLegacy(Context context, String oldFolderName, String newFolderName) {
            if (oldFolderName == null || newFolderName == null || oldFolderName.isEmpty() || newFolderName.isEmpty() || oldFolderName.equals(newFolderName)) {
                Log.e(TAG, "Noms de dossier invalides ou identiques.");
                return false;
            }

            ContentResolver resolver = context.getContentResolver();
            String oldRelativePathPictures = Environment.DIRECTORY_PICTURES + File.separator + oldFolderName + File.separator;
            String newRelativePathPictures = Environment.DIRECTORY_PICTURES + File.separator + newFolderName + File.separator;

            Log.d(TAG, "Tentative de renommage du dossier via MediaStore de '" + oldRelativePathPictures + "' vers '" + newRelativePathPictures + "'");

            Uri[] mediaCollections = {
                    MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
                    MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            };

            String[] projection = {
                    MediaStore.MediaColumns._ID,
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.DATA // Chemin physique (utile pour vérification et scan)
            };

            String selection = MediaStore.MediaColumns.RELATIVE_PATH + " = ?";
            String[] selectionArgs = {oldRelativePathPictures};

            List<Uri> urisToUpdate = new ArrayList<>();
            List<String> oldFilePaths = new ArrayList<>(); // Pour scanner après

            for (Uri collection : mediaCollections) {
                try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
                    if (cursor != null) {
                        Log.d(TAG, "Fichiers trouvés dans '" + oldRelativePathPictures + "' (collection: " + collection + "): " + cursor.getCount());
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID));
                            Uri itemUri = ContentUris.withAppendedId(collection, id);
                            urisToUpdate.add(itemUri);

                            String path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA));
                            if (path != null) {
                                oldFilePaths.add(path);
                            }
                            String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME));
                            Log.d(TAG, "Fichier trouvé: " + displayName + " (" + path + ") avec URI: " + itemUri);
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erreur lors de la requête des fichiers dans " + oldRelativePathPictures + " pour la collection " + collection, e);
                }
            }

            if (urisToUpdate.isEmpty()) {
                Log.w(TAG, "Aucun fichier multimédia indexé trouvé dans le dossier MediaStore: " + oldRelativePathPictures);
                // Si aucun fichier n'est indexé, essayons un renommage physique direct
                // (peut être un dossier vide ou un dossier non scanné par MediaStore)
                File oldPhysicalDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
                File newPhysicalDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), newFolderName);
                if (oldPhysicalDir.exists() && oldPhysicalDir.isDirectory()) {
                    if (oldPhysicalDir.renameTo(newPhysicalDir)) {
                        Log.i(TAG, "Dossier physique (possiblement vide ou non indexé) renommé de " + oldFolderName + " à " + newFolderName);
                        // Scanner le nouveau et l'ancien chemin parent pour forcer la mise à jour
                        scanDirectory(context, Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath());
                        return true;
                    } else {
                        Log.e(TAG, "Échec du renommage du dossier physique: " + oldFolderName);
                        return false;
                    }
                }
                Log.w(TAG, "Le dossier physique " + oldFolderName + " n'existe pas ou n'est pas un répertoire.");
                return false; // Ni fichiers indexés, ni dossier physique trouvé.
            }

            int updatedCount = 0;
            ArrayList<ContentProviderOperation> ops = new ArrayList<>();

            for (Uri itemUri : urisToUpdate) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.RELATIVE_PATH, newRelativePathPictures);
                // Si vous souhaitez également renommer les fichiers, vous pouvez ajouter ici DISPLAY_NAME
                // values.put(MediaStore.MediaColumns.DISPLAY_NAME, "nouveau_nom_fichier.jpg");

                // Crée une opération de mise à jour.
                // Utiliser des opérations en batch peut être plus performant
                // mais pour la clarté, une mise à jour directe est montrée ici.
                // Pour un grand nombre de fichiers, envisagez ContentProviderOperation.
                try {
                    int rows = resolver.update(itemUri, values, null, null);
                    if (rows > 0) {
                        updatedCount++;
                        Log.d(TAG, "Mise à jour de RELATIVE_PATH pour " + itemUri + " vers " + newRelativePathPictures);
                    } else {
                        Log.w(TAG, "Échec de la mise à jour de RELATIVE_PATH pour " + itemUri + " (0 ligne affectée).");
                    }
                } catch (Exception e) {
                    // Si une SecurityException est levée ici, c'est que l'application n'a pas les droits
                    // pour modifier cet URI spécifique. Cela peut arriver si l'application n'a pas créé le fichier.
                    // Pour Android Q+, il faudrait utiliser MediaStore.createWriteRequest() pour obtenir la permission.
                    Log.e(TAG, "Erreur lors de la mise à jour de l'URI " + itemUri, e);
                    // Si une erreur se produit, il vaut mieux arrêter ou gérer spécifiquement.
                    // Pour l'instant, on continue pour tenter de mettre à jour les autres.
                }
            }


            if (updatedCount > 0) {
                Log.i(TAG, updatedCount + " fichiers mis à jour dans MediaStore vers le nouveau chemin: " + newRelativePathPictures);

                // Étape cruciale : Scanner l'ancien et le nouveau chemin du dossier parent pour forcer la synchronisation.
                // On scanne le répertoire "Pictures" en entier pour s'assurer que les changements sont vus.
                String picturesDirectoryPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath();
                Log.d(TAG, "Scan du répertoire Pictures : " + picturesDirectoryPath);
                scanDirectory(context, picturesDirectoryPath);


                // Tentative de suppression de l'ancien dossier PHYSIQUE seulement s'il est vide.
                // MediaStore devrait avoir déplacé les fichiers.
                // Il faut être prudent ici, car MediaStore peut prendre un peu de temps.
                // Un léger délai ou une vérification que le dossier est vide est une bonne idée.
                final File oldPhysicalFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
                if (oldPhysicalFolder.exists() && oldPhysicalFolder.isDirectory()) {
                    // Attendre un court instant pour laisser le temps au système de déplacer les fichiers
                    try {
                        Thread.sleep(1000); // 1 seconde, à ajuster si nécessaire
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }

                    String[] filesInOldFolder = oldPhysicalFolder.list();
                    if (filesInOldFolder == null || filesInOldFolder.length == 0) {
                        if (oldPhysicalFolder.delete()) {
                            Log.i(TAG, "Ancien dossier physique vide '" + oldFolderName + "' supprimé avec succès.");
                            // Scanner à nouveau le répertoire parent après la suppression du dossier
                            scanDirectory(context, picturesDirectoryPath);
                        } else {
                            Log.w(TAG, "Échec de la suppression de l'ancien dossier physique vide: " + oldFolderName);
                        }
                    } else {
                        Log.w(TAG, "L'ancien dossier physique '" + oldFolderName + "' n'est pas vide après la mise à jour de MediaStore. Contenu : " + java.util.Arrays.toString(filesInOldFolder));
                        // Cela peut indiquer que MediaStore n'a pas déplacé tous les fichiers
                        // ou qu'il y avait des fichiers non-média dans le dossier.
                    }
                }

                return true;
            } else {
                Log.w(TAG, "Aucun fichier n'a pu être mis à jour dans MediaStore. Le renommage a échoué ou les permissions sont manquantes.");
                return false;
            }
        }

    /**
     * Scanne tous les fichiers et sous-dossiers d'un répertoire donné
     * pour mettre à jour l'index MediaStore.
     */
    private static void scanDirectory(Context context, String directoryPath) {
        Log.d(TAG, "Début du scan MediaStore pour le répertoire : " + directoryPath);
        File dir = new File(directoryPath);
        if (!dir.exists() || !dir.isDirectory()) {
            Log.w(TAG, "Le répertoire à scanner n'existe pas ou n'est pas un répertoire: " + directoryPath);
            return;
        }

        List<String> pathsToScan = new ArrayList<>();
        // Ajouter le répertoire lui-même
        pathsToScan.add(directoryPath);

        // Lister les fichiers et sous-dossiers pour un scan plus complet
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                pathsToScan.add(file.getAbsolutePath());
            }
        }

        if (pathsToScan.isEmpty()) {
            Log.d(TAG, "Aucun chemin à scanner dans : " + directoryPath);
            return;
        }

        MediaScannerConnection.scanFile(
                context.getApplicationContext(),
                pathsToScan.toArray(new String[0]),
                null, // MIME types (null pour laisser le scanner déduire)
                (path, uri) -> Log.d(TAG, "Scan MediaStore complété pour : " + path + (uri != null ? " -> " + uri : " (pas d'URI retourné)"))
        );
    }


/**  fonction de renommage de dossier photo */

    /**
     * Renomme un dossier photo dans la galerie en gérant les versions Android des plus anciennes aux plus récentes.
     *
     * @param context Le contexte de l'application
     * @param oldFolderName Le nom actuel du dossier
     * @param newFolderName Le nouveau nom du dossier
     * @return Liste des URIs nécessitant des permissions supplémentaires (Android 10+)
     */

    // PhotoFolderManager.java

    public static boolean renamePhotoFolder(Context context, String oldFolderName, String newFolderName) {
        File oldFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
        File newFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), newFolderName);

        Log.d(TAG, "Début du renommage : " + oldFolder.getAbsolutePath() + " -> " + newFolder.getAbsolutePath());

        boolean physicalRenameSuccess = false;
        if (oldFolder.exists()) {
            physicalRenameSuccess = oldFolder.renameTo(newFolder);
            Log.d(TAG, "Renommage physique du dossier : " + physicalRenameSuccess);
        } else {
            Log.e(TAG, "Le dossier source n'existe pas : " + oldFolder.getAbsolutePath());
            return false;
        }
        if (physicalRenameSuccess) {
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q) {
                String oldRelativePath = Environment.DIRECTORY_PICTURES + File.separator + oldFolderName + File.separator;
                String newRelativePath = Environment.DIRECTORY_PICTURES + File.separator + newFolderName + File.separator;

                ContentResolver resolver = context.getContentResolver();
                Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                String[] projection = {MediaStore.Images.Media._ID};
                String selection = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
                String[] selectionArgs = {oldRelativePath};

                try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
                    if (cursor != null) {
                        int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                        while (cursor.moveToNext()) {
                            long id = cursor.getLong(idColumn);
                            Uri fileUri = ContentUris.withAppendedId(collection, id);

                            ContentValues values = new ContentValues();
                            values.put(MediaStore.Images.Media.RELATIVE_PATH, newRelativePath);

                            try {
                                resolver.update(fileUri, values, null, null);
                            } catch (Exception e) {
                                Log.e(TAG, "Erreur lors de la mise à jour du RELATIVE_PATH pour " + fileUri, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Erreur lors de la requête MediaStore", e);
                }
            }
            // Scanner le nouveau dossier pour que MediaStore indexe correctement les fichiers
            MediaScannerConnection.scanFile(context, new String[]{newFolder.getAbsolutePath()}, null, null);
            return true;
        } else {
            Log.e(TAG, "Échec du renommage physique, MediaStore non mis à jour.");
            return false;
        }

    }

    public static List<ImageData> loadImagesFromGallery(Context context) {
        List<ImageData> images = new ArrayList<>();

        Uri externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media._ID}; // Utilisez l'ID pour accéder aux images

        try (Cursor cursor = context.getContentResolver().query(externalUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                do {
                    long id = cursor.getLong(idColumn);
                    Uri imageUri = ContentUris.withAppendedId(externalUri, id);

                    try {
                        // Chargez le bitmap à partir de l'URI (sans API dépréciée)
                        Bitmap bitmap = BitmapLoader.decode(context.getContentResolver(), imageUri);
                        if (bitmap != null) {
                            images.add(new ImageData(bitmap, imageUri));
                        }
                    } catch (IOException e) {
                        Log.e("ImageLoader", "Erreur lors du chargement du bitmap pour l'URI : " + imageUri, e);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("ImageLoader", "Erreur lors de la requête MediaStore : " + e.getMessage());
        }

        return images;
    }

    public static String getFileNameFromUri(Context context, Uri uri) {
        if (context == null || uri == null) {
            return null;
        }

        String fileName = null;
        Cursor cursor = context.getContentResolver().query(uri, new String[]{MediaStore.Images.Media.DISPLAY_NAME}, null, null, null);
        if (cursor != null && cursor.moveToFirst()) {
            try {
                fileName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME));
            } finally {
                cursor.close();
            }
        }
        return fileName;
    }

    public static CopyResult copyImageToFolder(Context context, Uri sourceImageUri, String targetFolderName, String targetFileName) {
        if (context == null || sourceImageUri == null || targetFolderName == null || targetFileName == null) {
            Log.e("PhotoFolderManager", "Paramètres invalides pour la copie de l'image.");
            return new CopyResult(false, null);
        }

        ContentResolver resolver = context.getContentResolver();
        Uri targetUri = null;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android Q et versions ultérieures
            String relativePath = Environment.DIRECTORY_PICTURES + File.separator + targetFolderName;
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName);

            try {
                targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (targetUri == null) {
                    Log.e("PhotoFolderManager", "Échec de l'insertion de l'image dans MediaStore.");
                    return new CopyResult(false, null);
                }

                // Copier les données de l'image source vers la destination
                try (InputStream inputStream = resolver.openInputStream(sourceImageUri);
                     OutputStream outputStream = resolver.openOutputStream(targetUri)) {
                    if (inputStream == null || outputStream == null) {
                        Log.e("PhotoFolderManager", "Erreur lors de l'ouverture des flux.");
                        return new CopyResult(false, null);
                    }

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }

                Log.d("PhotoFolderManager", "Image copiée avec succès vers : " + relativePath);
                return new CopyResult(true, targetUri);
            } catch (IOException e) {
                Log.e("PhotoFolderManager", "Erreur lors de la copie de l'image.", e);
                return new CopyResult(false, null);
            }
        } else {
            // Versions antérieures à Android Q
            File targetFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetFolderName);
            if (!targetFolder.exists() && !targetFolder.mkdirs()) {
                Log.e("PhotoFolderManager", "Échec de la création du dossier cible : " + targetFolder.getAbsolutePath());
                return new CopyResult(false, null);
            }

            File targetFile = new File(targetFolder, targetFileName);
            targetUri = Uri.fromFile(targetFile);

            try {
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = resolver.query(sourceImageUri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String sourceFilePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    cursor.close();

                    File sourceFile = new File(sourceFilePath);
                    if (sourceFile.exists() && sourceFile.renameTo(targetFile)) {
                        Log.d("PhotoFolderManager", "Image copiée avec succès vers : " + targetFile.getAbsolutePath());
                        return new CopyResult(true, targetUri);
                    }
                }
            } catch (Exception e) {
                Log.e("PhotoFolderManager", "Erreur lors de la copie de l'image.", e);
            }
            return new CopyResult(false, null);
        }
    }

    public static boolean deleteImageFromSource(Context context, Uri sourceImageUri) {
        if (context == null || sourceImageUri == null) {
            Log.e("PhotoFolderManager", "Paramètres invalides pour la suppression de l'image.");
            return false;
        }

        ContentResolver resolver = context.getContentResolver();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // resolver.delete(sourceImageUri, null, null);
                int rowsDeleted = resolver.delete(sourceImageUri, null, null);
                if (rowsDeleted > 0) {
                    Log.d(TAG, "Image supprimée avec succès (Android R+): " + sourceImageUri);
                    return true;
                } else {
                    Log.w(TAG, "Aucune image supprimée ou l'image n'existait pas (Android R+): " + sourceImageUri);
                    // Cela peut aussi signifier que la permission est nécessaire via createDeleteRequest
                    return false;
                }
            } else {
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = resolver.query(sourceImageUri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String sourceFilePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    cursor.close();

                    File sourceFile = new File(sourceFilePath);
                    if (sourceFile.exists() && sourceFile.delete()) {
                        Log.d("PhotoFolderManager", "Image supprimée avec succès : " + sourceFilePath);
                        return true;
                    }
                }
            }
            Log.d("PhotoFolderManager", "Image supprimée avec succès : " + sourceImageUri);
            return true;
        } catch (Exception e) {
            Log.e("PhotoFolderManager", "Erreur lors de la suppression de l'image.", e);
            return false;
        }
    }


/**
     * Supprime un dossier photo de la galerie, en gérant les versions Android des plus anciennes aux plus récentes.
     *
     * @param context Le contexte de l'application
     * @param folderName Le nom du dossier à supprimer
     * @return true si la suppression a réussi, false sinon
     */
    public static boolean deletePhotoFolder(Context context, String folderName) {
        if (context == null || folderName == null) return false;

        File picturesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        File folder = new File(picturesDir, folderName);

        boolean allDeleted = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String relativePath = Environment.DIRECTORY_PICTURES + File.separator + folderName + File.separator;
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Images.Media._ID};
            String selection = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
            String[] selectionArgs = {relativePath};

            ContentResolver resolver = context.getContentResolver();
            try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
                if (cursor != null) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idColumn);
                        Uri fileUri = ContentUris.withAppendedId(collection, id);
                        int deleted = resolver.delete(fileUri, null, null);
                        if (deleted == 0) allDeleted = false;
                    }
                }
            } catch (Exception e) {
                Log.e("PhotoFolderManager", "Erreur suppression MediaStore Q+: " + e.getMessage());
                allDeleted = false;
            }
            // Supprime le dossier physique si vide
            if (folder.exists() && folder.isDirectory() && folder.listFiles() != null && folder.listFiles().length == 0) {
                allDeleted &= folder.delete();
            }
        } else {
            // Versions < Q
            if (folder.exists() && folder.isDirectory()) {
                File[] files = folder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        if (!file.delete()) allDeleted = false;
                    }
                }
                allDeleted &= folder.delete();
                // Scan pour mettre à jour la galerie
                MediaScannerConnection.scanFile(context, new String[]{folder.getAbsolutePath()}, null, null);
            } else {
                allDeleted = false;
            }
        }
        return allDeleted;
    }


}
