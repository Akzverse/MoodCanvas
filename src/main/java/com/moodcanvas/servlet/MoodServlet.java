package com.moodcanvas.servlet;

import com.moodcanvas.dao.ArtworkUploadDAO;
import com.moodcanvas.dao.MoodDAO;
import com.moodcanvas.model.ArtworkUpload;
import com.moodcanvas.model.User;
import com.moodcanvas.util.MoodMatcher;
import com.moodcanvas.util.XMLParser;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.json.JSONArray;
import org.json.JSONObject;

public class MoodServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_RESULTS = 12;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/xml;charset=UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("user") instanceof User user)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String text = request.getParameter("moodText");
        if (text == null || (text = text.strip()).isEmpty() || text.length() > 1000) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please enter a mood (up to 1,000 characters).");
            return;
        }

        MoodMatcher.Match match = MoodMatcher.match(text);
        if (!"spotify".equals(request.getParameter("source"))) {
            try {
                new MoodDAO().saveMood(user.getUserID(), text, match.tag());
            } catch (Exception e) {
                getServletContext().log("Could not save mood entry", e);
            }
        }

        List<Map<String, String>> personalArt = personalArt(request, user, match);
        List<Map<String, String>> metArt = safeCollection("The Met", () -> met(match));
        List<Map<String, String>> chicagoArt = safeCollection("Art Institute of Chicago", () -> artInstitute(match));
        List<Map<String, String>> rijksArt = safeCollection("Rijksmuseum", () -> rijks(match));

        List<Map<String, String>> results = interleave(personalArt, metArt, chicagoArt, rijksArt);
        response.getWriter().write(XMLParser.buildMoodResponseXML(results, match.tag(), match.term()));
    }

    private List<Map<String, String>> personalArt(HttpServletRequest request, User user, MoodMatcher.Match match) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            for (ArtworkUpload upload : new ArtworkUploadDAO().findForMood(user.getUserID(), match.tag(), 3)) {
                results.add(art("upload-" + upload.getArtworkID(), upload.getTitle(), upload.getArtist(),
                        request.getContextPath() + "/my-artwork-image?id=" + upload.getArtworkID(), "Your uploads", match.tag()));
            }
        } catch (Exception e) {
            getServletContext().log("Could not include matching private uploads", e);
        }
        return results;
    }

    private List<Map<String, String>> safeCollection(String name, CollectionSearch search) {
        try {
            return search.find();
        } catch (Exception e) {
            getServletContext().log(name + " search is temporarily unavailable", e);
            return List.of();
        }
    }

    private List<Map<String, String>> interleave(List<Map<String, String>> personal, List<Map<String, String>> met,
            List<Map<String, String>> chicago, List<Map<String, String>> rijks) {
        List<Map<String, String>> results = new ArrayList<>(personal);
        List<List<Map<String, String>>> collections = List.of(met, chicago, rijks);
        for (int i = 0; results.size() < MAX_RESULTS; i++) {
            boolean found = false;
            for (List<Map<String, String>> collection : collections) {
                if (i < collection.size() && results.size() < MAX_RESULTS) {
                    results.add(collection.get(i));
                    found = true;
                }
            }
            if (!found) break;
        }
        return results;
    }

    private String get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(5000);
        connection.setReadTimeout(7000);
        connection.setRequestProperty("User-Agent", "MoodCanvas/1.0");
        try (InputStream stream = connection.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private List<Map<String, String>> met(MoodMatcher.Match match) throws Exception {
        String query = URLEncoder.encode(match.term(), StandardCharsets.UTF_8);
        JSONObject search = new JSONObject(get("https://collectionapi.metmuseum.org/public/collection/v1/search?hasImages=true&q=" + query));
        JSONArray ids = search.optJSONArray("objectIDs");
        if (ids == null) return List.of();

        List<CompletableFuture<Map<String, String>>> requests = new ArrayList<>();
        for (int i = 0; i < Math.min(18, ids.length()); i++) {
            int id = ids.getInt(i);
            requests.add(CompletableFuture.supplyAsync(() -> {
                try {
                    JSONObject artwork = new JSONObject(get("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + id));
                    String image = artwork.optString("primaryImageSmall", "");
                    if (image.isBlank()) return null;
                    int objectID = artwork.optInt("objectID");
                    return art("met-" + objectID, artwork.optString("title", "Untitled"),
                            artwork.optString("artistDisplayName", "Unknown artist"), image, "The Met", match.tag());
                } catch (Exception ignored) {
                    return null;
                }
            }));
        }

        List<Map<String, String>> results = new ArrayList<>();
        for (CompletableFuture<Map<String, String>> request : requests) {
            Map<String, String> artwork = request.join();
            if (artwork != null) results.add(artwork);
        }
        return results;
    }

    private List<Map<String, String>> artInstitute(MoodMatcher.Match match) throws Exception {
        String address = "https://api.artic.edu/api/v1/artworks/search?q="
                + URLEncoder.encode(match.term(), StandardCharsets.UTF_8)
                + "&query%5Bterm%5D%5Bis_public_domain%5D=true&limit=6&fields=id%2Ctitle%2Cartist_display%2Cimage_id%2Cis_public_domain";
        JSONArray rows = new JSONObject(get(address)).optJSONArray("data");
        if (rows == null) return List.of();

        List<Map<String, String>> results = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject artwork = rows.optJSONObject(i);
            if (artwork == null || !artwork.optBoolean("is_public_domain", false)) continue;
            String imageID = artwork.optString("image_id", "");
            if (imageID.isBlank()) continue;
            String image = "https://www.artic.edu/iiif/2/" + URLEncoder.encode(imageID, StandardCharsets.UTF_8) + "/full/843,/0/default.jpg";
            results.add(art("artic-" + artwork.optInt("id"), artwork.optString("title", "Untitled"),
                    artwork.optString("artist_display", "Unknown artist"), image, "Art Institute of Chicago", match.tag()));
        }
        return results;
    }

    private List<Map<String, String>> rijks(MoodMatcher.Match match) throws Exception {
        String key = System.getenv("RIJKSMUSEUM_API_KEY");
        if (key == null || key.isBlank()) return List.of();
        String address = "https://www.rijksmuseum.nl/api/en/collection?key=" + URLEncoder.encode(key, StandardCharsets.UTF_8)
                + "&q=" + URLEncoder.encode(match.term(), StandardCharsets.UTF_8) + "&imgonly=True&ps=6";
        JSONArray rows = new JSONObject(get(address)).optJSONArray("artObjects");
        if (rows == null) return List.of();

        List<Map<String, String>> results = new ArrayList<>();
        for (int i = 0; i < rows.length(); i++) {
            JSONObject artwork = rows.getJSONObject(i);
            JSONObject webImage = artwork.optJSONObject("webImage");
            String image = webImage == null ? "" : webImage.optString("url", "");
            if (image.isBlank()) continue;
            results.add(art("rijks-" + artwork.optString("objectNumber"), artwork.optString("title", "Untitled"),
                    artwork.optString("principalOrFirstMaker", "Unknown artist"), image, "Rijksmuseum", match.tag()));
        }
        return results;
    }

    private Map<String, String> art(String id, String title, String artist, String image, String museum, String mood) {
        Map<String, String> artwork = new LinkedHashMap<>();
        artwork.put("id", id);
        artwork.put("title", title);
        artwork.put("artist", artist);
        artwork.put("imageURL", image);
        artwork.put("museum", museum);
        artwork.put("mood", mood);
        return artwork;
    }

    @FunctionalInterface
    private interface CollectionSearch {
        List<Map<String, String>> find() throws Exception;
    }
}
