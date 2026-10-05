package com.moodcanvas.model;

import java.sql.Timestamp;

public class ArtworkUpload {
    private final String artworkID;
    private final String title;
    private final String artist;
    private final String description;
    private final String moodTag;
    private final String contentType;
    private final byte[] imageData;
    private final Timestamp createdAt;

    public ArtworkUpload(String artworkID, String title, String artist, String description, String moodTag,
            String contentType, byte[] imageData, Timestamp createdAt) {
        this.artworkID = artworkID;
        this.title = title;
        this.artist = artist;
        this.description = description;
        this.moodTag = moodTag;
        this.contentType = contentType;
        this.imageData = imageData;
        this.createdAt = createdAt;
    }

    public String getArtworkID() { return artworkID; }
    public String getTitle() { return title; }
    public String getArtist() { return artist; }
    public String getDescription() { return description; }
    public String getMoodTag() { return moodTag; }
    public String getContentType() { return contentType; }
    public byte[] getImageData() { return imageData; }
    public Timestamp getCreatedAt() { return createdAt; }
}
