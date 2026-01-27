package com.example.gallerykeeper.data.database.dao;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;
import com.example.gallerykeeper.data.database.entities.Tag; // Importe ton entité Tag

@Dao // Indique à Room que c'est un DAO
public interface TagDao { // Déclare-le comme une interface

    // Méthode pour insérer un ou plusieurs tags
    @Insert(onConflict = OnConflictStrategy.IGNORE) // Ignore si un tag avec le même ID existe déjà
    void insertTag(Tag tag);

    // Méthode pour insérer plusieurs tags
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertAllTags(List<Tag> tags);

    // Méthode pour mettre à jour un ou plusieurs tags
    @Update
    void updateTag(Tag tag);

    // Méthode pour supprimer un ou plusieurs tags
    @Delete
    void deleteTag(Tag tag);

    // Méthode pour supprimer tous les tags
    @Query("DELETE FROM tags") // Utilise une requête SQL pour supprimer toutes les lignes
    void deleteAllTags();

    // Méthode pour obtenir tous les tags
    @Query("SELECT * FROM tags") // Utilise une requête SQL pour sélectionner toutes les colonnes de la table "tags"
    List<Tag> getAllTags();

    // Méthode pour obtenir un tag par son ID
    @Query("SELECT * FROM tags WHERE id = :tagId") // Utilise un paramètre dans la requête SQL
    Tag getTagById(int tagId);

    // Méthode pour obtenir un tag par son nom
    @Query("SELECT * FROM tags WHERE tag_name = :tagName")
    Tag getTagByName(String tagName);

    // Supprimer un élément par son nom
    @Query("DELETE FROM tags WHERE tag_name = :tagName")
    void deleteTagByName(String tagName);

    // Méthode pour obtenir tous les tags surveillés
  //  @Query("SELECT * FROM tags WHERE is_monitored = 1") // 1 pour true en SQLite
 //   List<Tag> getMonitoredTags();

    // Méthode pour obtenir tous les tags scannés
  //  @Query("SELECT * FROM tags WHERE is_scanned = 1") // 1 pour true en SQLite
   // List<Tag> getScannedTags();

    // Méthode pour compter le nombre de tags
    @Query("SELECT COUNT(*) FROM tags")
    int countTags();
}