package com.example.gallerykeeper.Utils;

import android.app.Activity;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.IntentSender;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import com.example.gallerykeeper.Utils.ImageData;
import com.example.gallerykeeper.R;


public class PhotoFolderManager {


    private static final String TAG = "PhotoFolderManager";


    /**
     * Crée un "dossier" dans la galerie en y ajoutant une image placeholder.
     * Sur Android Q+, cela utilise MediaStore et IS_PENDING.
     *
     * @param folderName        Le nom du dossier à créer dans Pictures (ex: "MonDossier").
     * @param placeholderFileName Le nom du fichier image placeholder (ex: "cover.jpg").
     */
    public static void createPhotoFolderWithPlaceholder_old(Context context, Activity activity, String folderName, String placeholderFileName) {
        if (context == null || activity == null || folderName == null || placeholderFileName == null){
            Log.e(TAG, "Contexte ou Activity  ou folderName ou placeholderFileName nul, impossible de créer le dossier photo.");
            return;
        }

        ContentResolver resolver = activity.getContentResolver();
        ContentValues values = new ContentValues();
        Uri imageUri = null;

        // Définir le chemin relatif pour Android Q et plus
        String relativePathForCreation = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            relativePathForCreation = Environment.DIRECTORY_PICTURES + File.separator + folderName + File.separator;
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePathForCreation);
            values.put(MediaStore.MediaColumns.IS_PENDING, 1); //
            Log.d(TAG, "CREATE_PLACEHOLDER: RELATIVE_PATH en cours de définition pour MediaStore: [" + relativePathForCreation + "]");// Mettre en attente
        } else {
            // Pour les versions < Q, la gestion des fichiers est différente.
            // On crée le dossier directement et on utilisera MediaStore.MediaColumns.DATA.
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName);
            Log.d(TAG, "Nanou création du répertoire (pré-Q): " + directory.getAbsolutePath());
            if (!directory.exists()) {
                if (!directory.mkdirs()) {
                    Log.e(TAG, "Échec de la création du répertoire (pré-Q): " + directory.getAbsolutePath());
                    return;
                }
            }
            // Le chemin DATA sera construit plus tard pour les versions < Q
        }

        values.put(MediaStore.MediaColumns.DISPLAY_NAME, placeholderFileName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg"); // Ou image/png si vous utilisez un PNG

        try {
            imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

            if (imageUri == null) {
                Log.e(TAG, "Échec de l'insertion de l'image dans MediaStore pour le dossier: " + folderName);
                return;
            }

            // Écrire les données de l'image placeholder
            try (OutputStream outputStream = resolver.openOutputStream(imageUri)) {
                if (outputStream == null) {
                    Log.e(TAG, "Impossible d'obtenir OutputStream pour l'URI: " + imageUri);
                    // Si on ne peut pas écrire, il faut supprimer l'entrée en attente (Q+) ou le fichier (pré-Q)
                    resolver.delete(imageUri, null, null); // Tentative de nettoyage
                    return;
                }


                // Charger le bitmap depuis les ressources drawable
                // Remplacez R.drawable.image_principale par l'ID réel de votre ressource
                // Si votre image s'appelle image_principale.jpg dans res/drawable,
                // son ID sera R.drawable.image_principale
                //Bitmap placeholderBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.image_principale);

                // Créer un bitmap placeholder simple (ex: un carré de 100x100px d'une couleur)
                // Pour une vraie application, vous pourriez avoir une petite image dans vos ressources
                // ou générer quelque chose de plus significatif.
                Bitmap placeholderBitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
                placeholderBitmap.eraseColor(android.graphics.Color.LTGRAY); // Fond gris clair

                // Compresser et écrire le bitmap dans l'outputStream
                placeholderBitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream);
                placeholderBitmap.recycle(); // Libérer la mémoire du bitmap

                Log.d(TAG, "Image placeholder écrite avec succès pour: " + imageUri);
            }

            // Rendre l'image non en attente pour Android Q+
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear();
                values.put(MediaStore.MediaColumns.IS_PENDING, 0);
                int updatedRows = resolver.update(imageUri, values, null, null);
                if (updatedRows == 0) {
                    Log.w(TAG, "Échec de la mise à jour de IS_PENDING à 0 pour: " + imageUri);
                    // L'image pourrait rester en attente, ce qui n'est pas idéal.
                }
            }
            // Pour les versions < Q, MediaScannerConnection serait utilisé pour informer la galerie du nouveau fichier.
            // Mais l'insertion via MediaStore.Images.Media.insert() devrait déjà le faire.

            Log.d(TAG, "Dossier '" + folderName + "' (via placeholder '" + placeholderFileName + "') créé avec succès: " + imageUri);

        } catch (IOException e) {
            Log.e(TAG, "IOException lors de l'écriture de l'image placeholder pour le dossier: " + folderName, e);
            if (imageUri != null) {
                resolver.delete(imageUri, null, null); // Nettoyage en cas d'erreur
            }

        } catch (Exception e) {
            Log.e(TAG, "Exception lors de la création du dossier photo: " + folderName, e);
            if (imageUri != null) {
                resolver.delete(imageUri, null, null); // Nettoyage en cas d'erreur
            }

        }
    }
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
    public static void updateMediaStorePathsForMovedFolder_old(Context context, String oldFolderPathHint, String newFolderPath) {
        if (context == null || newFolderPath == null) {
            return;
        }
        Log.d(TAG, "Tentative de scan MediaStore pour le dossier: " + newFolderPath);

        // Lister les fichiers dans le nouveau dossier physique
        File newFolder = new File(newFolderPath);
        if (!newFolder.exists() || !newFolder.isDirectory()) {
            Log.w(TAG, "Le nouveau dossier n'existe pas ou n'est pas un répertoire: " + newFolderPath);
            return;
        }

        File[] filesInNewFolder = newFolder.listFiles();
        if (filesInNewFolder == null || filesInNewFolder.length == 0) {
            Log.d(TAG, "Aucun fichier à scanner dans: " + newFolderPath);
            return;
        }

        String[] pathsToScan = new String[filesInNewFolder.length];
        for (int i = 0; i < filesInNewFolder.length; i++) {
            pathsToScan[i] = filesInNewFolder[i].getAbsolutePath();
        }

        MediaScannerConnection.scanFile(context.getApplicationContext(),
                pathsToScan,
                null, // mimetypes, null pour laisser le scanner deviner
                (path, uri) -> {
                    Log.d(TAG, "Fichier scanné: " + path + ", URI: " + uri);
                    // Ici, l'URI est l'URI MediaStore du fichier scanné.
                    // Vous pourriez vouloir vérifier si l'ancien chemin (oldFolderPathHint)
                    // a été correctement mis à jour, mais le scan devrait s'en charger.
                });
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

    public static void updateSingleMediaStorePath(Context context, String oldFilePath, String newFilePath) {
        if (context == null || oldFilePath == null || newFilePath == null) {
            return;
        }
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DATA, newFilePath); // Mettre à jour le chemin du fichier

        // Construire la clause WHERE pour trouver l'ancien fichier
        // Il est important d'échapper les apostrophes si les chemins de fichiers peuvent en contenir.
        String selection = MediaStore.MediaColumns.DATA + " = ?";
        String[] selectionArgs = new String[]{oldFilePath};

        try {
            // Essayer de mettre à jour pour les images, puis les vidéos, etc.
            // Vous pouvez rendre cela plus générique en utilisant MediaStore.Files.getContentUri("external")
            // mais cela nécessite de gérer différents types de médias.

            int updatedRowsImages = resolver.update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, selection, selectionArgs);
            if (updatedRowsImages > 0) {
                Log.d(TAG, "MediaStore (Images) mis à jour pour: " + oldFilePath + " -> " + newFilePath);
                return; // Trouvé et mis à jour dans les images
            }

            int updatedRowsVideos = resolver.update(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values, selection, selectionArgs);
            if (updatedRowsVideos > 0) {
                Log.d(TAG, "MediaStore (Videos) mis à jour pour: " + oldFilePath + " -> " + newFilePath);
                return; // Trouvé et mis à jour dans les vidéos
            }

            // Ajoutez d'autres types de médias si nécessaire (Audio, etc.)

            Log.w(TAG, "Aucune entrée MediaStore trouvée ou mise à jour pour l'ancien chemin: " + oldFilePath);

        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la mise à jour de MediaStore pour " + oldFilePath, e);
        }
    }
    private static List<File> listMediaFiles(File directory) {
        if (directory == null || !directory.isDirectory()) {
            return new ArrayList<>(); // Retourne une liste vide si le répertoire est invalide
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return new ArrayList<>(); // Erreur lors du listage des fichiers
        }
        // Pour l'instant, retourne tous les fichiers. Vous pourriez filtrer ici.
        // Par exemple, pour ne garder que les .jpg, .png, .mp4, etc.
        return new ArrayList<>(Arrays.asList(files));
    }


    private static boolean moveFile(File source, File destination) {
        if (!source.exists()) {
            Log.e(TAG, "Le fichier source n'existe pas : " + source.getAbsolutePath());
            return false;
        }

        if (destination.exists()) {
            Log.w(TAG, "Le fichier destination existe déjà : " + destination.getAbsolutePath());
            return false;
        }

        boolean moved = source.renameTo(destination);
        if (!moved) {
            Log.e(TAG, "Échec du déplacement du fichier : " + source.getName());
        }
        return moved;
    }

    public static List<Uri> renameGalleryFolder_withPhysicalMove(Context context, String oldFolderName, String newFolderName) {
        if (context == null || oldFolderName == null || newFolderName == null || oldFolderName.equals(newFolderName)) {
            return new ArrayList<>();
        }

        List<Uri> urisNeedingPermission = new ArrayList<>();
        File oldFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
        File newFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), newFolderName);

        if (!oldFolder.exists()) {
            Log.e(TAG, "Le dossier source n'existe pas : " + oldFolder.getAbsolutePath());
            return urisNeedingPermission;
        }

        if (!newFolder.exists()) {
            newFolder.mkdirs();
        }

        File[] files = oldFolder.listFiles();
        if (files != null) {
            for (File file : files) {
                File newFile = new File(newFolder, file.getName());
                if (moveFile(file, newFile)) {
                    // Mise à jour du MediaStore
                    ContentValues values = new ContentValues();
                    values.put(MediaStore.MediaColumns.DATA, newFile.getAbsolutePath());
                    String selection = MediaStore.MediaColumns.DATA + " = ?";
                    String[] selectionArgs = {file.getAbsolutePath()};
                    context.getContentResolver().update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, selection, selectionArgs);
                }
            }
        }

        // Supprime l'ancien dossier s'il est vide
        if (oldFolder.listFiles() == null || oldFolder.listFiles().length == 0) {
            oldFolder.delete();
        }

        MediaScannerConnection.scanFile(context,
                new String[]{newFolder.getAbsolutePath()},
                null,
                (path, uri) -> Log.d(TAG, "Dossier scanné : " + path));

        return urisNeedingPermission;
    }
    public static List<Uri> renameGalleryFolder(Context context, String oldFolderName, String newFolderName) {
        if (context == null || oldFolderName.equals(newFolderName)) {
            return new ArrayList<>();
        }

        ContentResolver resolver = context.getContentResolver();
        List<Uri> urisNeedingPermission = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // --- Android Q (API 29) and above ---
            List<Uri> filesToProcessForQ = new ArrayList<>();
            String currentRelativePath = Environment.DIRECTORY_PICTURES + File.separator + oldFolderName + File.separator;
            String targetRelativePath = Environment.DIRECTORY_PICTURES + File.separator + newFolderName + File.separator;

            Log.d(TAG, "RENAME_FOLDER (Q+): RELATIVE_PATH utilisé pour la requête MediaStore: [" + currentRelativePath + "]");
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME};
            String selection = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
            String[] selectionArgs = {currentRelativePath};

            try (Cursor c = resolver.query(collection, projection, selection, selectionArgs, null)) {
                if (c != null && c.moveToFirst()) {
                    int idCol = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                    do {
                        long id = c.getLong(idCol);
                        Uri foundUri = ContentUris.withAppendedId(collection, id);
                        filesToProcessForQ.add(foundUri);
                    } while (c.moveToNext());
                }
            } catch (Exception e) {
                Log.e(TAG, "RENAME_FOLDER (Q+): Erreur lors de la requête MediaStore ou du traitement du curseur: " + oldFolderName, e);
                return urisNeedingPermission;
            }

            for (Uri fileUri : filesToProcessForQ) {
                try {
                    if (!updateSingleFileRelativePath(context, fileUri, targetRelativePath)) {
                        urisNeedingPermission.add(fileUri);
                    }

                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                        String oldPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + File.separator + oldFolderName;
                        String newPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + File.separator + newFolderName;
                        Log.d(TAG, "ICIII 6  MediaStore mis à jour pour le chemin : " + oldPath + " -> " + newPath);
                        updateMediaStorePathsForMovedFolder(context, oldPath, newPath);

                    }

                } catch (SecurityException secEx) {
                    urisNeedingPermission.add(fileUri);
                } catch (Exception e) {
                    Log.e(TAG, "UpdateErr Q+: " + fileUri, e);
                }
            }

            if (!urisNeedingPermission.isEmpty()) {
                String oldPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + File.separator + oldFolderName;
                String newPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath() + File.separator + newFolderName;
                MediaScannerConnection.scanFile(context, new String[]{oldPath, newPath}, null, (path, uri) -> {
                    Log.d(TAG, "ICIII 5 MediaStore mis à jour pour le chemin : " + path);
                });
            }

        } else {
            // --- Android versions older than Q (API 28 and below) ---
            File oldPFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
            File newPFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), newFolderName);

            if (oldPFolder.renameTo(newPFolder)) {
                Log.d(TAG, "ICII MediaStore mis à jour pour le chemin : " + oldPFolder.getAbsolutePath() + " -> " + newPFolder.getAbsolutePath());
                updateMediaStorePathsForMovedFolder(context, oldPFolder.getAbsolutePath(), newPFolder.getAbsolutePath());
            } else {
                if (!newPFolder.exists()) {
                    Log.d(TAG, "ICII 2 Création du nouveau dossier : " + newPFolder.getAbsolutePath());
                    newPFolder.mkdirs();
                }
                Log.d(TAG, "ICII 3 Déplacement des fichiers de l'ancien dossier vers le nouveau : " + oldPFolder.getAbsolutePath() + " -> " + newPFolder.getAbsolutePath());
                List<File> filesInOld = listMediaFiles(oldPFolder);
                for (File fMove : filesInOld) {
                    File newLoc = new File(newPFolder, fMove.getName());
                    if (fMove.renameTo(newLoc)) {
                        updateSingleMediaStorePath(context, fMove.getAbsolutePath(), newLoc.getAbsolutePath());
                    }
                }
            }
        }

        return urisNeedingPermission;
    }


        public static void moveMediaFolder(Context context, String oldFolderName, String newFolderName) {

        // --- Android version Q and Older (API 29 and below) ---
        File oldPFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
        File newPFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), newFolderName);

        if (oldPFolder.renameTo(newPFolder)) {
            Log.d(TAG, "ICII MediaStore mis à jour pour le chemin : " + oldPFolder.getAbsolutePath() + " -> " + newPFolder.getAbsolutePath());
            updateMediaStorePathsForMovedFolder(context, oldPFolder.getAbsolutePath(), newPFolder.getAbsolutePath());
        } else {
            if (!newPFolder.exists()) {
                Log.d(TAG, "ICII 2 Création du nouveau dossier : " + newPFolder.getAbsolutePath());
                newPFolder.mkdirs();
            }
            Log.d(TAG, "ICII 3 Déplacement des fichiers de l'ancien dossier vers le nouveau : " + oldPFolder.getAbsolutePath() + " -> " + newPFolder.getAbsolutePath());
            List<File> filesInOld = listMediaFiles(oldPFolder);
            for (File fMove : filesInOld) {
                File newLoc = new File(newPFolder, fMove.getName());
                if (fMove.renameTo(newLoc)) {
                    updateSingleMediaStorePath(context, fMove.getAbsolutePath(), newLoc.getAbsolutePath());
                }
            }
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

    public static boolean renamePhotoFolder_versioOld(Context context, String oldFolderName, String newFolderName) {
        File oldFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
        File newFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), newFolderName);

        Log.d(TAG, "Début du renommage : " + oldFolder.getAbsolutePath() + " -> " + newFolder.getAbsolutePath());

        boolean physicalRenameSuccess = false;
        if (oldFolder.exists()) {
            physicalRenameSuccess = oldFolder.renameTo(newFolder);
            Log.d(TAG, "Renommage physique du dossier : " + physicalRenameSuccess);
        } else {
            Log.e(TAG, "Le dossier source n'existe pas : " + oldFolder.getAbsolutePath());
        }

        // Mise à jour MediaStore si le renommage physique a réussi
        if (physicalRenameSuccess) {
            String oldRelativePath = "Pictures/" + oldFolderName + "/";
            String newRelativePath = "Pictures/" + newFolderName + "/";

            ContentValues values = new ContentValues();
            values.put(MediaStore.Images.Media.RELATIVE_PATH, newRelativePath);

            String selection = MediaStore.Images.Media.RELATIVE_PATH + "=?";
            String[] selectionArgs = new String[]{oldRelativePath};

            ContentResolver resolver = context.getContentResolver();
            int updated = resolver.update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, selection, selectionArgs);

            Log.d(TAG, "Mise à jour MediaStore : " + updated + " fichiers mis à jour.");
            // Scanner le nouveau dossier
            MediaScannerConnection.scanFile(context, new String[]{newFolder.getAbsolutePath()}, null, null);
            return true;
        } else {
            Log.e(TAG, "Échec du renommage physique, MediaStore non mis à jour.");
            return false;
        }
    }

    public static List<Uri> renamePhotoFolder_old(Context context, String oldFolderName, String newFolderName) {
        if (context == null || oldFolderName == null || newFolderName == null || oldFolderName.equals(newFolderName)) {
            return new ArrayList<>();
        }

        List<Uri> urisNeedingPermission = new ArrayList<>();
        ContentResolver resolver = context.getContentResolver();

        // --- Pour Android Q (API 29) et versions ultérieures ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            String oldRelativePath = Environment.DIRECTORY_PICTURES + File.separator + oldFolderName + File.separator;
            String newRelativePath = Environment.DIRECTORY_PICTURES + File.separator + newFolderName + File.separator;

            Log.d(TAG, "Renommage du dossier : recherche des fichiers dans " + oldRelativePath);

            // Recherche les fichiers dans l'ancien dossier
            Uri collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
            String[] projection = {MediaStore.Images.Media._ID};
            String selection = MediaStore.Images.Media.RELATIVE_PATH + " = ?";
            String[] selectionArgs = {oldRelativePath};

            try (Cursor cursor = resolver.query(collection, projection, selection, selectionArgs, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                    do {
                        long id = cursor.getLong(idColumn);
                        Uri fileUri = ContentUris.withAppendedId(collection, id);

                        try {
                            // Tente de mettre à jour le chemin relatif
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.Images.Media.RELATIVE_PATH, newRelativePath);

                            int updatedRows = resolver.update(fileUri, values, null, null);
                            if (updatedRows > 0) {
                                Log.d(TAG, "Chemin relatif mis à jour pour : " + fileUri);
                            } else {
                                Log.w(TAG, "Échec de la mise à jour pour : " + fileUri);
                                urisNeedingPermission.add(fileUri);
                            }
                        } catch (SecurityException e) {
                            Log.e(TAG, "Erreur de permission pour : " + fileUri, e);
                            urisNeedingPermission.add(fileUri);
                        }
                    } while (cursor.moveToNext());
                } else {
                    Log.w(TAG, "Aucun fichier trouvé dans : " + oldRelativePath);
                }
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de la requête MediaStore", e);
            }

            // Force le scan du nouveau dossier pour mettre à jour le MediaStore
            String physicalPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath()
                    + File.separator + newFolderName;
            File newFolder = new File(physicalPath);
            if (!newFolder.exists()) {
                newFolder.mkdirs();
            }

            MediaScannerConnection.scanFile(context,
                    new String[]{physicalPath},
                    null,
                    (path, uri) -> Log.d(TAG, "Dossier scanné : " + path));
        }
        // --- Pour les versions antérieures à Android Q (API 28 et moins) ---
        else {
            File oldFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), oldFolderName);
            File newFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), newFolderName);

            if (!oldFolder.exists()) {
                Log.e(TAG, "Le dossier source n'existe pas : " + oldFolder.getAbsolutePath());
                return urisNeedingPermission;
            }

            // Tente de renommer directement le dossier
            boolean renameSuccessful = oldFolder.renameTo(newFolder);

            if (renameSuccessful) {
                Log.d(TAG, "Dossier renommé avec succès : " + oldFolder.getAbsolutePath() + " → " + newFolder.getAbsolutePath());
            } else {
                // Si le renommage direct échoue, copie les fichiers un par un
                Log.d(TAG, "Renommage direct échoué, essai de copie fichier par fichier");

                if (!newFolder.exists()) {
                    newFolder.mkdirs();
                }

                File[] files = oldFolder.listFiles();
                if (files != null) {
                    for (File file : files) {
                        File newFile = new File(newFolder, file.getName());
                        boolean moved = file.renameTo(newFile);

                        if (moved) {
                            // Mise à jour du MediaStore pour chaque fichier
                            ContentValues values = new ContentValues();
                            values.put(MediaStore.MediaColumns.DATA, newFile.getAbsolutePath());

                            // Recherche de l'entrée correspondante dans le MediaStore
                            String selection = MediaStore.MediaColumns.DATA + " = ?";
                            String[] selectionArgs = {file.getAbsolutePath()};

                            resolver.update(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values, selection, selectionArgs);
                        } else {
                            Log.w(TAG, "Échec du déplacement de " + file.getName());
                        }
                    }

                    // Supprime l'ancien dossier s'il est vide
                    if (oldFolder.listFiles() == null || oldFolder.listFiles().length == 0) {
                        oldFolder.delete();
                    }
                }
            }

            // Force le scan du nouveau dossier pour mettre à jour le MediaStore
            MediaScannerConnection.scanFile(context,
                    new String[]{newFolder.getAbsolutePath()},
                    null,
                    (path, uri) -> Log.d(TAG, "Dossier scanné : " + path));
        }

        return urisNeedingPermission;
    }


    /**
     * Crée un dossier photo dans la galerie sans nécessairement ajouter d'image placeholder.
     *
     * @param context Le contexte de l'application
     * @param folderName Le nom du dossier à créer
     * @return true si la création a réussi, false sinon
     */
    public static boolean createPhotoFolder(Context context, String folderName) {
        if (context == null || folderName == null || folderName.isEmpty()) {
            Log.e(TAG, "Paramètres invalides pour la création du dossier");
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Pour Android Q et plus, créer le chemin relatif
            String relativePath = Environment.DIRECTORY_PICTURES + File.separator + folderName + File.separator;
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName);

            if (directory.exists()) {
                Log.d(TAG, "Le dossier existe déjà: " + directory.getAbsolutePath());
                return true;
            }

            if (!directory.mkdirs()) {
                Log.e(TAG, "Échec de la création physique du dossier: " + directory.getAbsolutePath());
            }

            // Notifier MediaStore du nouveau dossier
            MediaScannerConnection.scanFile(context,
                    new String[]{directory.getAbsolutePath()},
                    null,
                    (path, uri) -> Log.d(TAG, "Dossier scanné: " + path));

            return true;
        } else {
            // Pour Android < Q, créer le dossier physiquement
            File directory = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), folderName);

            if (directory.exists()) {
                Log.d(TAG, "Le dossier existe déjà: " + directory.getAbsolutePath());
                return true;
            }

            boolean result = directory.mkdirs();
            if (result) {
                Log.d(TAG, "Dossier créé avec succès: " + directory.getAbsolutePath());

                // Notifier MediaStore du nouveau dossier
                MediaScannerConnection.scanFile(context,
                        new String[]{directory.getAbsolutePath()},
                        null,
                        (path, uri) -> Log.d(TAG, "Dossier scanné: " + path));
            } else {
                Log.e(TAG, "Échec de la création du dossier: " + directory.getAbsolutePath());
            }

            return result;
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
                        // Chargez le bitmap à partir de l'URI
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
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

    public static List<Bitmap> loadBitmapFromGallery(Context context) {
        List<Bitmap> bitmaps = new ArrayList<>();

        Uri externalUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
        String[] projection = {MediaStore.Images.Media._ID}; // Utilisez l'ID pour accéder aux images

        try (Cursor cursor = context.getContentResolver().query(externalUri, projection, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);

                do {
                    long id = cursor.getLong(idColumn);
                    Uri imageUri = ContentUris.withAppendedId(externalUri, id);

                    try {
                        // Chargez le bitmap à partir de l'URI
                        Bitmap bitmap = MediaStore.Images.Media.getBitmap(context.getContentResolver(), imageUri);
                        if (bitmap != null) {
                            bitmaps.add(bitmap);
                        }
                    } catch (IOException e) {
                        Log.e("ImageLoader", "Erreur lors du chargement du bitmap pour l'URI : " + imageUri, e);
                    }
                } while (cursor.moveToNext());
            }
        } catch (Exception e) {
            Log.e("ImageLoader", "Erreur lors de la requête MediaStore : " + e.getMessage());
        }

        return bitmaps;
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

    /**
     * Déplace une image vers un dossier spécifique dans la galerie.
     *
     * @param context Le contexte de l'application
     * @param sourceImageUri L'URI de l'image source
     * @param targetFolderName Le nom du dossier cible
     * @param targetFileName Le nom du fichier cible
     * @return true si le déplacement a réussi, false sinon
     */


    public static boolean moveImageToFolder(Context context, Uri sourceImageUri, String targetFolderName, String targetFileName) {
        if (context == null || sourceImageUri == null || targetFolderName == null || targetFileName == null) {
            Log.e("PhotoFolderManager", "Paramètres invalides pour le déplacement de l'image.");
            return false;
        }

        ContentResolver resolver = context.getContentResolver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android Q et versions ultérieures
            String relativePath = Environment.DIRECTORY_PICTURES + File.separator + targetFolderName;
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName);

            try {
                // Insérer dans le nouvel emplacement
                Uri targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (targetUri == null) {
                    Log.e("PhotoFolderManager", "Échec de l'insertion de l'image dans MediaStore.");
                    return false;
                }

                // Copier les données de l'image source vers la destination
                try (InputStream inputStream = resolver.openInputStream(sourceImageUri);
                     OutputStream outputStream = resolver.openOutputStream(targetUri)) {
                    if (inputStream == null || outputStream == null) {
                        Log.e("PhotoFolderManager", "Erreur lors de l'ouverture des flux.");
                        return false;
                    }

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }

                // Supprimer l'ancien fichier
                resolver.delete(sourceImageUri, null, null);
                Log.d("PhotoFolderManager", "Image déplacée avec succès vers : " + relativePath);
                return true;
            } catch (IOException e) {
                Log.e("PhotoFolderManager", "Erreur lors du déplacement de l'image.", e);
                return false;
            }
        } else {
            // Versions antérieures à Android Q
            File targetFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetFolderName);
            if (!targetFolder.exists() && !targetFolder.mkdirs()) {
                Log.e("PhotoFolderManager", "Échec de la création du dossier cible : " + targetFolder.getAbsolutePath());
                return false;
            }

            File targetFile = new File(targetFolder, targetFileName);
            try {
                // Obtenir le chemin du fichier source
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = resolver.query(sourceImageUri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    int dataIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    String sourceFilePath = cursor.getString(dataIndex);
                    cursor.close();

                    File sourceFile = new File(sourceFilePath);
                    if (sourceFile.renameTo(targetFile)) {
                        // Scanner le fichier pour qu'il apparaisse dans la galerie
                        MediaScannerConnection.scanFile(context,
                                new String[]{targetFile.getAbsolutePath()},
                                null,
                                (path, uri) -> Log.d("PhotoFolderManager", "Fichier scanné : " + path));
                        Log.d("PhotoFolderManager", "Image déplacée avec succès vers : " + targetFile.getAbsolutePath());
                        return true;
                    } else {
                        Log.e("PhotoFolderManager", "Échec du déplacement du fichier.");
                    }
                }
            } catch (Exception e) {
                Log.e("PhotoFolderManager", "Erreur lors du déplacement de l'image.", e);
            }
        }
        return false;
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

    public static boolean copyImageToFolder_ori(Context context, Uri sourceImageUri, String targetFolderName, String targetFileName) {
        if (context == null || sourceImageUri == null || targetFolderName == null || targetFileName == null) {
            Log.e("PhotoFolderManager", "Paramètres invalides pour la copie de l'image.");
            return false;
        }

        ContentResolver resolver = context.getContentResolver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android Q et versions ultérieures
            String relativePath = Environment.DIRECTORY_PICTURES + File.separator + targetFolderName;
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, targetFileName);

            try {
                Uri targetUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (targetUri == null) {
                    Log.e("PhotoFolderManager", "Échec de l'insertion de l'image dans MediaStore.");
                    return false;
                }

                // Copier les données de l'image source vers la destination
                try (InputStream inputStream = resolver.openInputStream(sourceImageUri);
                     OutputStream outputStream = resolver.openOutputStream(targetUri)) {
                    if (inputStream == null || outputStream == null) {
                        Log.e("PhotoFolderManager", "Erreur lors de l'ouverture des flux.");
                        return false;
                    }

                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                }

                Log.d("PhotoFolderManager", "Image copiée avec succès vers : " + relativePath);
                return true;
            } catch (IOException e) {
                Log.e("PhotoFolderManager", "Erreur lors de la copie de l'image.", e);
                return false;
            }
        } else {
            // Versions antérieures à Android Q
            File targetFolder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), targetFolderName);
            if (!targetFolder.exists() && !targetFolder.mkdirs()) {
                Log.e("PhotoFolderManager", "Échec de la création du dossier cible : " + targetFolder.getAbsolutePath());
                return false;
            }

            File targetFile = new File(targetFolder, targetFileName);
            try {
                String[] projection = {MediaStore.Images.Media.DATA};
                Cursor cursor = resolver.query(sourceImageUri, projection, null, null, null);
                if (cursor != null && cursor.moveToFirst()) {
                    String sourceFilePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
                    cursor.close();

                    File sourceFile = new File(sourceFilePath);
                    if (sourceFile.exists() && sourceFile.renameTo(targetFile)) {
                        Log.d("PhotoFolderManager", "Image copiée avec succès vers : " + targetFile.getAbsolutePath());
                        return true;
                    }
                }
            } catch (Exception e) {
                Log.e("PhotoFolderManager", "Erreur lors de la copie de l'image.", e);
            }
            return false;
        }
    }

    public static boolean deleteImageFromSourceWithMediaStoreDeleteRequest(Context context, Uri sourceImageUri) {
        if (context == null || sourceImageUri == null) {
            Log.e("PhotoFolderManager", "Paramètres invalides pour la suppression de l'image.");
            return false;
        }

        ContentResolver resolver = context.getContentResolver();

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Utilisation de MediaStore.createDeleteRequest pour Android 11+
                List<Uri> urisToDelete = new ArrayList<>();
                urisToDelete.add(sourceImageUri);

                IntentSender intentSender = MediaStore.createDeleteRequest(resolver, urisToDelete).getIntentSender();
                if (context instanceof Activity) {
                    Activity activity = (Activity) context;
                    activity.startIntentSenderForResult(intentSender, 1001, null, 0, 0, 0);
                    Log.d("PhotoFolderManager", "Demande de suppression envoyée pour : " + sourceImageUri);
                } else {
                    Log.e("PhotoFolderManager", "Le contexte n'est pas une instance d'Activity.");
                    return false;
                }
            } else {
                // Suppression directe pour les versions inférieures à Android 11
                int rowsDeleted = resolver.delete(sourceImageUri, null, null);
                if (rowsDeleted > 0) {
                    Log.d("PhotoFolderManager", "Image supprimée avec succès : " + sourceImageUri);
                    return true;
                } else {
                    Log.w("PhotoFolderManager", "Aucune image supprimée pour l'URI : " + sourceImageUri);
                    return false;
                }
            }
        } catch (Exception e) {
            Log.e("PhotoFolderManager", "Erreur lors de la suppression de l'image.", e);
            return false;
        }

        return true;
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


    /**
     * Déplace une photo dans le dossier cible (récupéré depuis la base si besoin), puis supprime l'original.
     * Utilise la même logique que la tâche de scan.
     * @param context Contexte
     * @param sourceImageUri Uri de la photo à déplacer
     * @param tag Tag de prédiction (pour retrouver le dossier cible)
     * @param targetFileName Nom du fichier cible
     * @return true si déplacement et suppression réussis
     */
    public static boolean movePhotoWithDb(Context context, Uri sourceImageUri, String tag, String targetFileName) {
        // Récupérer le nom du dossier cible depuis la base de données (méthode à adapter selon ton projet)
        String targetFolderName = getTargetFolderFromDb(context, tag); // À implémenter selon ta logique
        if (targetFolderName == null) {
            Log.e(TAG, "Dossier cible introuvable pour le tag: " + tag);
            return false;
        }
        CopyResult copyResult = copyImageToFolder(context, sourceImageUri, targetFolderName, targetFileName);
        if (copyResult.isSuccess()) {
            boolean deleted = deleteImageFromSource(context, sourceImageUri);
            return deleted;
        }
        return false;
    }

    // Stub à adapter selon ta logique de base de données
    public static String getTargetFolderFromDb(Context context, String tag) {
        // TODO: Récupérer le nom du dossier cible depuis la base de données en fonction du tag
        // Exemple: return DatabaseHelper.getFolderNameForTag(context, tag);
        // Pour l'instant, retourne le tag + "_photos" comme fallback
        return tag + "_photos";
    }


}
