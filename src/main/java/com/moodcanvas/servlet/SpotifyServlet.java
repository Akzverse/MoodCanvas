package com.moodcanvas.servlet;

import com.moodcanvas.model.User;
import com.moodcanvas.util.MoodMatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional Spotify OAuth and link import integration. */
public class SpotifyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SPOTIFY_SCOPES = "user-read-recently-played playlist-read-private";

    private static volatile String cachedAppToken = null;
    private static volatile long appTokenExpiresAt = 0;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String path = request.getServletPath();
        if ("/spotify/connect".equals(path)) {
            connect(request, response);
        } else if ("/spotify/callback".equals(path)) {
            callback(request, response);
        } else {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("user") instanceof User)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Sign in to import Spotify music.");
            return;
        }

        String link = request.getParameter("spotifyLink");
        if (blank(link)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Please paste a Spotify song, album, or playlist link.");
            return;
        }

        String[] reference = parseSpotifyReference(link);
        if (reference == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Could not recognize that link. Paste a Spotify track, album, or playlist link.");
            return;
        }

        String kind = reference[0];
        String id = reference[1];
        List<String> tracks = new ArrayList<>();

        // 1. Try with user's OAuth access token if available
        String userToken = (String) session.getAttribute("spotifyAccessToken");
        if (!blank(userToken)) {
            try {
                fetchTracksWithToken(session, userToken, kind, id, tracks);
            } catch (Exception e) {
                getServletContext().log("User token Spotify fetch failed, falling back", e);
            }
        }

        // 2. Try with app-level Client Credentials token if tracks still empty
        if (tracks.isEmpty()) {
            try {
                String appToken = getAppToken();
                if (!blank(appToken)) {
                    fetchTracksWithToken(null, appToken, kind, id, tracks);
                }
            } catch (Exception e) {
                getServletContext().log("Client credentials Spotify fetch failed, falling back to oEmbed", e);
            }
        }

        // 3. Fallback to Spotify open oEmbed endpoint (no keys or authorization needed)
        if (tracks.isEmpty()) {
            try {
                fetchViaOEmbed(link.strip(), tracks);
            } catch (Exception e) {
                getServletContext().log("Spotify oEmbed fetch failed", e);
            }
        }

        if (tracks.isEmpty()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No playable tracks or details could be found for that Spotify link.");
            return;
        }

        String prefix = "playlist".equals(kind) ? "Songs from my Spotify playlist: "
                : ("album".equals(kind) ? "Songs from Spotify album: " : "A song I chose: ");
        String summary = prefix + String.join("; ", tracks);
        if (summary.length() > 950) summary = summary.substring(0, 947) + "…";

        String inferredMood = MoodMatcher.match(summary).tag();

        JSONObject result = new JSONObject()
                .put("text", summary)
                .put("count", tracks.size())
                .put("kind", kind)
                .put("mood", inferredMood);

        response.getWriter().write(result.toString());
    }

    private void fetchTracksWithToken(HttpSession session, String token, String kind, String id, List<String> tracks) throws Exception {
        String endpoint;
        if ("track".equals(kind)) {
            endpoint = "https://api.spotify.com/v1/tracks/" + id;
        } else if ("album".equals(kind)) {
            endpoint = "https://api.spotify.com/v1/albums/" + id + "/tracks?limit=30";
        } else {
            endpoint = "https://api.spotify.com/v1/playlists/" + id + "/tracks?limit=30";
        }

        JSONObject payload = spotifyGet(token, endpoint);
        if ("track".equals(kind)) {
            addTrack(payload, tracks);
        } else {
            JSONArray items = payload.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length() && tracks.size() < 30; i++) {
                    JSONObject row = items.optJSONObject(i);
                    if (row != null) {
                        JSONObject trackObj = row.has("track") ? row.optJSONObject("track") : row;
                        addTrack(trackObj, tracks);
                    }
                }
            }
        }
    }

    private void fetchViaOEmbed(String link, List<String> tracks) {
        try {
            String url = "https://open.spotify.com/oembed?url=" + URLEncoder.encode(link, StandardCharsets.UTF_8);
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(6000);
            conn.setReadTimeout(8000);
            conn.setRequestProperty("User-Agent", "MoodCanvas/1.0");
            if (conn.getResponseCode() == 200) {
                try (var in = conn.getInputStream()) {
                    JSONObject obj = new JSONObject(new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    String title = obj.optString("title", "").strip();
                    if (!title.isEmpty()) {
                        tracks.add(title);
                    }
                }
            }
            conn.disconnect();
        } catch (Exception ignored) { }
    }

    private synchronized String getAppToken() {
        if (!blank(cachedAppToken) && System.currentTimeMillis() < appTokenExpiresAt - 30000) {
            return cachedAppToken;
        }
        String clientID = System.getenv("MOODCANVAS_SPOTIFY_CLIENT_ID");
        String clientSecret = System.getenv("MOODCANVAS_SPOTIFY_CLIENT_SECRET");
        if (blank(clientID) || blank(clientSecret)) return null;

        try {
            JSONObject token = postToken("grant_type=client_credentials");
            cachedAppToken = token.optString("access_token", null);
            appTokenExpiresAt = System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000L;
            return cachedAppToken;
        } catch (Exception e) {
            return null;
        }
    }

    private String[] parseSpotifyReference(String link) {
        if (blank(link)) return null;
        // Matches (track|playlist|album) followed by / or : and 22 base62 characters anywhere in the string
        Matcher matcher = Pattern.compile("(track|playlist|album)[/:]([A-Za-z0-9]{22})").matcher(link.strip());
        return matcher.find() ? new String[] { matcher.group(1), matcher.group(2) } : null;
    }

    private void addTrack(JSONObject track, List<String> tracks) {
        if (track == null || track.optBoolean("is_local", false)) return;
        String title = track.optString("name", "").strip();
        JSONArray artists = track.optJSONArray("artists");
        StringBuilder names = new StringBuilder();
        if (artists != null) {
            for (int i = 0; i < artists.length(); i++) {
                JSONObject artist = artists.optJSONObject(i);
                String name = artist == null ? "" : artist.optString("name", "").strip();
                if (!name.isEmpty()) {
                    if (!names.isEmpty()) names.append(", ");
                    names.append(name);
                }
            }
        }
        if (!title.isEmpty()) tracks.add(names.isEmpty() ? title : title + " — " + names);
    }

    private JSONObject spotifyGet(String token, String endpoint) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        connection.setRequestProperty("User-Agent", "MoodCanvas/1.0");
        try (var stream = connection.getInputStream()) {
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } finally {
            connection.disconnect();
        }
    }

    private void connect(HttpServletRequest request, HttpServletResponse response) throws IOException {
        if (!isSignedIn(request)) {
            response.sendRedirect(request.getContextPath() + "/pages/signin.html");
            return;
        }
        String clientID = System.getenv("MOODCANVAS_SPOTIFY_CLIENT_ID");
        String clientSecret = System.getenv("MOODCANVAS_SPOTIFY_CLIENT_SECRET");
        if (blank(clientID) || blank(clientSecret)) {
            redirectStatus(request, response, "setup-required");
            return;
        }

        String redirectURI = redirectURI(request);
        URI callback = URI.create(redirectURI);
        if (!callback.getHost().equalsIgnoreCase(request.getServerName()) || callback.getPort() != request.getServerPort()) {
            String canonical = callback.getScheme() + "://" + callback.getRawAuthority() + request.getContextPath() + "/spotify/connect";
            response.sendRedirect(canonical);
            return;
        }

        HttpSession session = request.getSession(false);
        byte[] stateBytes = new byte[32];
        RANDOM.nextBytes(stateBytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(stateBytes);
        session.setAttribute("spotifyOauthState", state);
        session.setAttribute("spotifyOauthStateAt", System.currentTimeMillis());

        String authorize = "https://accounts.spotify.com/authorize?response_type=code&client_id="
                + encode(clientID) + "&redirect_uri=" + encode(redirectURI)
                + "&scope=" + encode(SPOTIFY_SCOPES) + "&state=" + encode(state);
        response.sendRedirect(authorize);
    }

    private void callback(HttpServletRequest request, HttpServletResponse response) throws IOException {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("user") instanceof User)) {
            response.sendRedirect(request.getContextPath() + "/pages/signin.html");
            return;
        }
        String expectedState = (String) session.getAttribute("spotifyOauthState");
        Object stateTime = session.getAttribute("spotifyOauthStateAt");
        session.removeAttribute("spotifyOauthState");
        session.removeAttribute("spotifyOauthStateAt");
        String returnedState = request.getParameter("state");
        boolean fresh = stateTime instanceof Long time && System.currentTimeMillis() - time < 10 * 60 * 1000L;
        if (blank(expectedState) || blank(returnedState) || !fresh
                || !MessageDigest.isEqual(expectedState.getBytes(StandardCharsets.UTF_8), returnedState.getBytes(StandardCharsets.UTF_8))) {
            redirectStatus(request, response, "session-expired");
            return;
        }
        if (!blank(request.getParameter("error"))) {
            redirectStatus(request, response, "cancelled");
            return;
        }
        String code = request.getParameter("code");
        if (blank(code)) {
            redirectStatus(request, response, "unavailable");
            return;
        }
        try {
            storeTokens(session, exchangeCode(request, code));
            JSONObject history = recentTracks(request, session, true);
            JSONArray items = history.optJSONArray("items");
            List<String> tracks = new ArrayList<>();
            if (items != null) {
                for (int i = 0; i < items.length() && tracks.size() < 20; i++) {
                    JSONObject track = items.optJSONObject(i) == null ? null : items.getJSONObject(i).optJSONObject("track");
                    if (track == null || track.optBoolean("is_local", false)) continue;
                    String title = track.optString("name", "").strip();
                    JSONArray artists = track.optJSONArray("artists");
                    StringBuilder artistNames = new StringBuilder();
                    if (artists != null) {
                        for (int j = 0; j < artists.length(); j++) {
                            String artist = artists.optJSONObject(j) == null ? "" : artists.getJSONObject(j).optString("name", "").strip();
                            if (!artist.isEmpty()) {
                                if (!artistNames.isEmpty()) artistNames.append(", ");
                                artistNames.append(artist);
                            }
                        }
                    }
                    if (!title.isEmpty()) tracks.add(artistNames.isEmpty() ? title : title + " — " + artistNames);
                }
            }
            if (tracks.isEmpty()) {
                redirectStatus(request, response, "no-tracks");
                return;
            }
            String summary = "Music I have recently listened to: " + String.join("; ", tracks);
            if (summary.length() > 950) summary = summary.substring(0, 947) + "…";
            session.setAttribute("spotifyMoodInput", summary);
            session.setAttribute("spotifyTrackCount", tracks.size());
            response.sendRedirect(request.getContextPath() + "/pages/dashboard.jsp?spotify=ready");
        } catch (Exception e) {
            getServletContext().log("Could not read the signed-in user's recent Spotify tracks", e);
            redirectStatus(request, response, "unavailable");
        }
    }

    private JSONObject exchangeCode(HttpServletRequest request, String code) throws Exception {
        String body = "grant_type=authorization_code&code=" + encode(code) + "&redirect_uri=" + encode(redirectURI(request));
        return postToken(body);
    }

    private JSONObject refreshToken(HttpSession session) throws Exception {
        String refresh = (String) session.getAttribute("spotifyRefreshToken");
        if (blank(refresh)) throw new IOException("Spotify session expired; reconnect the account.");
        JSONObject token = postToken("grant_type=refresh_token&refresh_token=" + encode(refresh));
        storeTokens(session, token);
        return token;
    }

    private JSONObject postToken(String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("https://accounts.spotify.com/api/token").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        String credentials = System.getenv("MOODCANVAS_SPOTIFY_CLIENT_ID") + ":" + System.getenv("MOODCANVAS_SPOTIFY_CLIENT_SECRET");
        connection.setRequestProperty("Authorization", "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)));
        try {
            try (var output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            int status = connection.getResponseCode();
            try (var stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream()) {
                String responseBody = stream == null ? "{}" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                if (status < 200 || status >= 300) throw new IOException("Spotify token request returned HTTP " + status);
                return new JSONObject(responseBody);
            }
        } finally {
            connection.disconnect();
        }
    }

    private JSONObject recentTracks(HttpServletRequest request, HttpSession session, boolean mayRefresh) throws Exception {
        String accessToken = (String) session.getAttribute("spotifyAccessToken");
        Object expiresAt = session.getAttribute("spotifyExpiresAt");
        if (blank(accessToken) || !(expiresAt instanceof Long time) || time < System.currentTimeMillis() + 30000) {
            if (blank((String) session.getAttribute("spotifyRefreshToken"))) throw new IOException("Spotify session needs to be reconnected.");
            refreshToken(session);
            accessToken = (String) session.getAttribute("spotifyAccessToken");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL("https://api.spotify.com/v1/me/player/recently-played?limit=20").openConnection();
        connection.setConnectTimeout(7000);
        connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer " + accessToken);
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_UNAUTHORIZED) {
                connection.disconnect();
                if (!mayRefresh) throw new IOException("Spotify rejected the refreshed access token.");
                refreshToken(session);
                return recentTracks(request, session, false);
            }
            try (var stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream()) {
                String responseBody = stream == null ? "{}" : new String(stream.readAllBytes(), StandardCharsets.UTF_8);
                if (status < 200 || status >= 300) throw new IOException("Spotify recently played request returned HTTP " + status);
                return new JSONObject(responseBody);
            }
        } finally {
            connection.disconnect();
        }
    }

    private void storeTokens(HttpSession session, JSONObject token) {
        session.setAttribute("spotifyAccessToken", token.getString("access_token"));
        if (token.has("refresh_token")) session.setAttribute("spotifyRefreshToken", token.getString("refresh_token"));
        session.setAttribute("spotifyExpiresAt", System.currentTimeMillis() + token.optLong("expires_in", 3600) * 1000L);
    }

    private String redirectURI(HttpServletRequest request) {
        String configured = System.getenv("MOODCANVAS_SPOTIFY_REDIRECT_URI");
        if (!blank(configured)) return configured.strip();
        return "http://127.0.0.1:" + request.getServerPort() + request.getContextPath() + "/spotify/callback";
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isSignedIn(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("user") instanceof User;
    }

    private void redirectStatus(HttpServletRequest request, HttpServletResponse response, String status) throws IOException {
        response.sendRedirect(request.getContextPath() + "/pages/dashboard.jsp?spotify=" + encode(status));
    }
}
