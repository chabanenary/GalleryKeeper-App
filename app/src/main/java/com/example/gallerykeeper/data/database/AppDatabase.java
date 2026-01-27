package com.example.gallerykeeper.data.database;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.gallerykeeper.data.database.dao.TagDao; // Importe ton DAO
import com.example.gallerykeeper.data.database.entities.Tag; // Importe ton entité

import java.util.HashMap;
import java.util.Map;

@Database(entities = {Tag.class}, version = 3, exportSchema = false) // Bump version après changement de schéma
public abstract class AppDatabase extends RoomDatabase {

    private static final Map<String, AppDatabase> INSTANCES = new HashMap<>();

    public static synchronized AppDatabase getInstance(Context context, String userEmail) {
        if (userEmail == null) {
            throw new IllegalArgumentException("userEmail ne doit pas être null");
        }

        AppDatabase db = INSTANCES.get(userEmail);
        if (db == null) {
            // Nettoyer l’email pour l’utiliser comme nom de fichier
            String safeEmail = userEmail.replace("@", "_at_").replace(".", "_dot_");
            String dbName = "gallerykeeper_" + safeEmail + ".db";

            db = Room.databaseBuilder(
                    context.getApplicationContext(),
                    AppDatabase.class,
                    dbName
            ).build();
            INSTANCES.put(userEmail, db);
        }
        return db;
    }

    public static synchronized void clearAllInstances() {
        for (AppDatabase db : INSTANCES.values()) {
            db.close();
        }
        INSTANCES.clear();
    }

    public abstract TagDao tagDao();
}