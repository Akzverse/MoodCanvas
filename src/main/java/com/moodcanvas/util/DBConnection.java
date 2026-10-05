package com.moodcanvas.util;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/** Provides a persistent, zero-configuration local database, or the configured MySQL database. */
public final class DBConnection {
    private static final String DATABASE_URL = configuredUrl();
    private static volatile boolean schemaReady;

    private DBConnection() { }

    private static String configuredUrl() {
        String configured = System.getenv("MOODCANVAS_DB_URL");
        if (configured != null && !configured.isBlank()) return configured;
        String home = System.getProperty("user.home");
        Path data = Path.of(home, ".moodcanvas");
        try {
            Files.createDirectories(data);
        } catch (java.io.IOException e) {
            throw new ExceptionInInitializerError("Cannot create MoodCanvas data folder: " + e.getMessage());
        }
        return "jdbc:h2:file:" + data.resolve("moodcanvas").toAbsolutePath().toString().replace('\\', '/')
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH";
    }

    public static boolean usesEmbeddedDatabase() {
        return DATABASE_URL.startsWith("jdbc:h2:");
    }

    public static Connection getConnection() throws SQLException {
        String user = usesEmbeddedDatabase() ? "sa" : System.getenv().getOrDefault("MOODCANVAS_DB_USER", "root");
        String password = usesEmbeddedDatabase() ? "" : System.getenv().getOrDefault("MOODCANVAS_DB_PASSWORD", "");
        try {
            Class.forName(usesEmbeddedDatabase() ? "org.h2.Driver" : "com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            String database = usesEmbeddedDatabase() ? "H2" : "MySQL";
            throw new SQLException(database + " driver is unavailable. Update Maven dependencies and redeploy.", e);
        }
        Connection connection = DriverManager.getConnection(DATABASE_URL, user, password);
        if (usesEmbeddedDatabase() && !schemaReady) initializeEmbeddedSchema(connection);
        return connection;
    }

    private static synchronized void initializeEmbeddedSchema(Connection connection) throws SQLException {
        if (schemaReady) return;
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS users (userID INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(150) NOT NULL UNIQUE, passwordHash VARCHAR(255) NOT NULL, role VARCHAR(20) NOT NULL DEFAULT 'user', theme VARCHAR(50) NOT NULL DEFAULT 'warm', createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS mood_entries (moodID INT AUTO_INCREMENT PRIMARY KEY, userID INT NOT NULL, moodText CLOB NOT NULL, moodTag VARCHAR(50), submittedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (userID) REFERENCES users(userID) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS journal_entries (journalID INT AUTO_INCREMENT PRIMARY KEY, userID INT NOT NULL, artworkID VARCHAR(100) NOT NULL, title VARCHAR(255), artist VARCHAR(255), imageURL CLOB, museum VARCHAR(100), savedAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, CONSTRAINT unique_user_artwork UNIQUE (userID, artworkID), FOREIGN KEY (userID) REFERENCES users(userID) ON DELETE CASCADE)");
            statement.executeUpdate("CREATE TABLE IF NOT EXISTS user_artworks (artworkID VARCHAR(36) PRIMARY KEY, userID INT NOT NULL, title VARCHAR(180) NOT NULL, artist VARCHAR(180) NOT NULL, description VARCHAR(1000), moodTag VARCHAR(50) NOT NULL, contentType VARCHAR(30) NOT NULL, imageData BLOB NOT NULL, createdAt TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP, FOREIGN KEY (userID) REFERENCES users(userID) ON DELETE CASCADE)");
            schemaReady = true;
        } catch (SQLException e) {
            connection.close();
            throw new SQLException("Could not initialize MoodCanvas's local database. Check the Tomcat console for details.", e);
        }
    }
}
