package com.moodcanvas.dao;

import com.moodcanvas.model.ArtworkUpload;
import com.moodcanvas.util.DBConnection;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ArtworkUploadDAO {
    public void create(ArtworkUpload artwork, int userID) throws SQLException {
        String sql = "INSERT INTO user_artworks(artworkID,userID,title,artist,description,moodTag,contentType,imageData) VALUES(?,?,?,?,?,?,?,?)";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, artwork.getArtworkID());
            statement.setInt(2, userID);
            statement.setString(3, artwork.getTitle());
            statement.setString(4, artwork.getArtist());
            statement.setString(5, artwork.getDescription());
            statement.setString(6, artwork.getMoodTag());
            statement.setString(7, artwork.getContentType());
            statement.setBytes(8, artwork.getImageData());
            statement.executeUpdate();
        }
    }

    public List<ArtworkUpload> listForUser(int userID) throws SQLException {
        List<ArtworkUpload> artworks = new ArrayList<>();
        String sql = "SELECT artworkID,title,artist,description,moodTag,contentType,createdAt FROM user_artworks WHERE userID=? ORDER BY createdAt DESC";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userID);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) artworks.add(fromRow(rows, null));
            }
        }
        return artworks;
    }

    public List<ArtworkUpload> findForMood(int userID, String moodTag, int limit) throws SQLException {
        List<ArtworkUpload> artworks = new ArrayList<>();
        String sql = "SELECT artworkID,title,artist,description,moodTag,contentType,createdAt FROM user_artworks WHERE userID=? AND (moodTag=? OR moodTag='all') ORDER BY createdAt DESC LIMIT ?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, userID);
            statement.setString(2, moodTag);
            statement.setInt(3, Math.max(1, Math.min(6, limit)));
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) artworks.add(fromRow(rows, null));
            }
        }
        return artworks;
    }

    public ArtworkUpload findImageForUser(String artworkID, int userID) throws SQLException {
        String sql = "SELECT artworkID,title,artist,description,moodTag,contentType,imageData,createdAt FROM user_artworks WHERE artworkID=? AND userID=?";
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, artworkID);
            statement.setInt(2, userID);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? fromRow(row, row.getBytes("imageData")) : null;
            }
        }
    }

    public boolean remove(String artworkID, int userID) throws SQLException {
        try (Connection connection = DBConnection.getConnection(); PreparedStatement statement = connection.prepareStatement("DELETE FROM user_artworks WHERE artworkID=? AND userID=?")) {
            statement.setString(1, artworkID);
            statement.setInt(2, userID);
            return statement.executeUpdate() == 1;
        }
    }

    private ArtworkUpload fromRow(ResultSet row, byte[] imageData) throws SQLException {
        return new ArtworkUpload(row.getString("artworkID"), row.getString("title"), row.getString("artist"),
                row.getString("description"), row.getString("moodTag"), row.getString("contentType"), imageData,
                row.getTimestamp("createdAt"));
    }
}
