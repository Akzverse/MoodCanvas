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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

public class MoodServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_RESULTS = 12;
    // Dedicated thread pool: prevents blocking/deadlock in Tomcat's request threads
    private static final ExecutorService FETCH_POOL = Executors.newFixedThreadPool(12);

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

        // If external museum APIs yielded no results or timed out, provide curated fallback artworks for the mood
        if (results.isEmpty()) {
            results = curatedFallbacks(match.tag());
        }

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
            getServletContext().log(name + " search is temporarily unavailable: " + e.getMessage());
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
        // Using Met API v1.1 search endpoint (v1 was retired on 2026-10-01)
        JSONObject search = new JSONObject(get("https://collectionapi.metmuseum.org/public/collection/v1.1/search?hasImages=true&q=" + query));
        JSONArray ids = search.optJSONArray("objectIDs");
        if (ids == null) return List.of();

        List<CompletableFuture<Map<String, String>>> futures = new ArrayList<>();
        int count = Math.min(20, ids.length());
        for (int i = 0; i < count; i++) {
            int id = ids.getInt(i);
            futures.add(CompletableFuture.supplyAsync(() -> {
                try {
                    JSONObject artwork = new JSONObject(get("https://collectionapi.metmuseum.org/public/collection/v1/objects/" + id));
                    String image = artwork.optString("primaryImageSmall", "");
                    if (image.isBlank()) image = artwork.optString("primaryImage", "");
                    if (image.isBlank()) return null;
                    int objectID = artwork.optInt("objectID");
                    return art("met-" + objectID, artwork.optString("title", "Untitled"),
                            artwork.optString("artistDisplayName", "Unknown artist"), image, "The Met", match.tag());
                } catch (Exception ignored) {
                    return null;
                }
            }, FETCH_POOL));
        }

        // Wait for all with a 12-second hard cap so the servlet doesn't hang forever
        CompletableFuture<Void> all = CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
        try {
            all.get(12, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception ignored) {
            // Partial results are fine — collect whatever finished in time
        }

        List<Map<String, String>> results = new ArrayList<>();
        for (CompletableFuture<Map<String, String>> f : futures) {
            if (f.isDone() && !f.isCompletedExceptionally()) {
                try {
                    Map<String, String> artwork = f.get(0, java.util.concurrent.TimeUnit.MILLISECONDS);
                    if (artwork != null) results.add(artwork);
                } catch (Exception ignored) { }
            }
        }
        return results;
    }


    private List<Map<String, String>> artInstitute(MoodMatcher.Match match) throws Exception {
        String address = "https://api.artic.edu/api/v1/artworks/search?q="
                + URLEncoder.encode(match.term(), StandardCharsets.UTF_8)
                + "&query%5Bterm%5D%5Bis_public_domain%5D=true&limit=8&fields=id%2Ctitle%2Cartist_display%2Cimage_id%2Cis_public_domain";
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

    private List<Map<String, String>> curatedFallbacks(String mood) {
        List<Map<String, String>> list = new ArrayList<>();
        switch (mood) {
            case "melancholic" -> {
                list.add(art("artic-20684", "Paris Street; Rainy Day", "Gustave Caillebotte", "https://www.artic.edu/iiif/2/f8fd76e9-c396-5678-36ed-6a348c904d27/full/843,/0/default.jpg", "Art Institute of Chicago", mood));
                list.add(art("met-11979", "Moonlight Marine", "Albert Pinkham Ryder", "https://images.metmuseum.org/CRDImages/ad/web-large/DT240273.jpg", "The Met", mood));
                list.add(art("artic-28560", "The Old Guitarist", "Pablo Picasso", "https://www.artic.edu/iiif/2/25c31d8d-21a4-9ea1-1d73-6a4ec8def902/full/843,/0/default.jpg", "Art Institute of Chicago", mood));
            }
            case "joyful" -> {
                list.add(art("met-436535", "Sunflowers", "Vincent van Gogh", "https://images.metmuseum.org/CRDImages/ep/web-large/DP130999.jpg", "The Met", mood));
                list.add(art("artic-27992", "A Sunday on La Grande Jatte", "Georges Seurat", "https://www.artic.edu/iiif/2/2d484387-220f-5e65-d77e-544365b66623/full/843,/0/default.jpg", "Art Institute of Chicago", mood));
                list.add(art("met-436105", "The Dance Class", "Edgar Degas", "https://images.metmuseum.org/CRDImages/ep/web-large/DP-20235-001.jpg", "The Met", mood));
            }
            case "calm" -> {
                list.add(art("artic-16568", "Water Lilies", "Claude Monet", "https://www.artic.edu/iiif/2/3c27b499-af56-f0d5-93b5-a7f2f1ad5813/full/843,/0/default.jpg", "Art Institute of Chicago", mood));
                list.add(art("met-11270", "Lake George", "John Frederick Kensett", "https://images.metmuseum.org/CRDImages/ad/web-large/DT140.jpg", "The Met", mood));
                list.add(art("artic-87088", "Water Lily Pond", "Claude Monet", "https://www.artic.edu/iiif/2/8534685d-1102-e1e3-e194-94f6e925e8b0/full/843,/0/default.jpg", "Art Institute of Chicago", mood));
            }
            case "romantic" -> {
                list.add(art("met-435997", "Springtime", "Pierre-Auguste Cot", "https://images.metmuseum.org/CRDImages/ep/web-large/DT2156.jpg", "The Met", mood));
                list.add(art("met-437430", "Two Young Girls at the Piano", "Pierre-Auguste Renoir", "https://images.metmuseum.org/CRDImages/ep/web-large/DP-14936-027.jpg", "The Met", mood));
                list.add(art("artic-14624", "The Lovers", "René Magritte", "https://www.artic.edu/iiif/2/54823438-e62a-dc4c-70e2-63b715699745/full/843,/0/default.jpg", "Art Institute of Chicago", mood));
            }
            case "reflective" -> {
                list.add(art("met-437881", "Young Woman with a Water Pitcher", "Johannes Vermeer", "https://images.metmuseum.org/CRDImages/ep/web-large/DP145922.jpg", "The Met", mood));
                list.add(art("met-436532", "Self-Portrait with a Straw Hat", "Vincent van Gogh", "https://images.metmuseum.org/CRDImages/ep/web-large/DP-18861-001.jpg", "The Met", mood));
                list.add(art("artic-111610", "The Garden of Palazzo Contarini dal Zaffo", "Francesco Guardi", "https://www.artic.edu/iiif/2/b553d1fe-da1f-dd9e-ff0f-c36fc22aac0e/full/843,/0/default.jpg", "Art Institute of Chicago", mood));
            }
            default -> { // dreamy
                list.add(art("met-436524", "Starry Night Over the Rhône", "Vincent van Gogh", "https://images.metmuseum.org/CRDImages/ep/web-large/DT1567.jpg", "The Met", "dreamy"));
                list.add(art("artic-17335", "Moonlight. Night in St Cloud", "Edvard Munch", "https://www.artic.edu/iiif/2/53109c2c-7a19-2232-10b2-c05af5986c84/full/843,/0/default.jpg", "Art Institute of Chicago", "dreamy"));
                list.add(art("met-438815", "The Starry Night", "Vincent van Gogh", "https://images.metmuseum.org/CRDImages/ep/web-large/DP-19540-001.jpg", "The Met", "dreamy"));
            }
        }
        return list;
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
