package com.example.gallerykeeper.data.database.entities;


import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.HashMap;
import java.util.Map;

@Entity(tableName = "tags")
public class Tag {


    @PrimaryKey(autoGenerate = true)
    private int id;

    @ColumnInfo(name = "tag_name")
    private String tagName;

    @ColumnInfo(name = "folder_name")
    private String folderName;

    @ColumnInfo(name = "monitored")
    private boolean monitored;

    @ColumnInfo(name = "scanned")
    private boolean scanned;

    // Getters et setters
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getFolderName() {
        return folderName;
    }

    public void setFolderName(String folderName) {
        this.folderName = folderName;
    }

    public boolean isMonitored() {
        return monitored;
    }

    public void setMonitored(boolean monitored) {
        this.monitored = monitored;
    }

    public boolean isScanned() {
        return scanned;
    }

    public void setScanned(boolean scanned) {
        this.scanned = scanned;
    }


    // API pour définir tous les champs
    public void setAll(Map<String, Object> data) {
//
        if (data.containsKey("tagName")) this.tagName = (String) data.get("tagName");
        if (data.containsKey("folderName")) this.folderName = (String) data.get("folderName");
        if (data.containsKey("monitored")) this.monitored = (boolean) data.get("monitored");
        if (data.containsKey("scanned")) this.scanned = (boolean) data.get("scanned");
    }


    // API pour récupérer tous les champs
    public Map<String, Object> getAll() {
        Map<String, Object> data = new HashMap<>();
        data.put("id", this.id);
        data.put("tagName", this.tagName);
        data.put("folderName", this.folderName);
        data.put("monitored", this.monitored);
        data.put("scanned", this.scanned);
        return data;
    }
}
