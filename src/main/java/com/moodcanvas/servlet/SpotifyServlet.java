package com.moodcanvas.servlet;

import com.moodcanvas.model.User;
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
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional Spotify OAuth connection. Track details and tokens remain in the signed-in session only. */
public class SpotifyServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String SPOTIFY_SCOPES = "user-read-recently-played playlist-read-private";

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
        String[] reference = parseSpotifyReference(link);
        if (reference == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Paste a Spotify song or playlist link.");
            return;
        }
        if (blank((String) session.getAttribute("spotifyAccessToken"))) {
            response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED, "Connect Spotify first, then import this link.");
            return;
        }
        try {
            String endpoint = "track".equals(reference[0])
                    ? "https://api.spotify.com/v1/tracks/" + reference[1]
                    : "https://api.spotify.com/v1/playlists/" + reference[1] + "/items?limit=30";
            JSONObject payload = spotifyGet(session, endpoint);
            List<String> tracks = new ArrayList<>();
            if ("track".equals(reference[0])) {
                addTrack(payload, tracks);
            } else {
                JSONArray items = payload.optJSONArray("items");
                if (items != null) for (int i = 0; i < items.length() && tracks.size() < 30; i++) {
                    JSONObject row = items.optJSONObject(i);
                    if (row != null) addTrack(row.optJSONObject("track"), tracks);
                }
            }
            if (tracks.isEmpty()) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "No playable tracks were found in that Spotify link.");
                return;
            }
            String summary = ("playlist".equals(reference[0]) ? "Songs from my Spotify playlist: " : "A song I chose: ") + String.join("; ", tracks);
            if (summary.length() > 950) summary = summary.substring(0, 947) + "…";
            JSONObject result = new JSONObject().put("text", summary).put("count", tracks.size()).put("kind", reference[0]);
            response.getWriter().write(result.toString());
        } catch (Exception e) {
            getServletContext().log("Could not import Spotify link", e);
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Spotify could not read that link. For playlists, connect the account that owns or collaborates on it.");
        }
    }

    private String[] parseSpotifyReference(String link) {
        if (blank(link)) return null;
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(?:https?://open\\.spotify\\.com/|spotify:)(track|playlist)[/:]([A-Za-z0-9]{22})").matcher(link.strip());
        return matcher.find() ? new String[] { matcher.group(1), matcher.group(2) } : null;
    }

    private void addTrack(JSONObject track, List<String> tracks) {
        if (track == null || track.optBoolean("is_local", false)) return;
        String title = track.optString("name", "").strip();
        JSONArray artists = track.optJSONArray("artists");
        StringBuilder names = new StringBuilder();
        if (artists != null) for (int i = 0; i < artists.length(); i++) {
            JSONObject artist = artists.optJSONObject(i);
            String name = artist == null ? "" : artist.optString("name", "").strip();
            if (!name.isEmpty()) { if (!names.isEmpty()) names.append(", "); names.append(name); }
        }
        if (!title.isEmpty()) tracks.add(names.isEmpty() ? title : title + " — " + names);
    }

    private JSONObject spotifyGet(HttpSession session, String endpoint) throws Exception {
        String token = (String) session.getAttribute("spotifyAccessToken");
        Object expires = session.getAttribute("spotifyExpiresAt");
        if (blank(token) || !(expires instanceof Long time) || time < System.currentTimeMillis() + 30000) {
            refreshToken(session);
            token = (String) session.getAttribute("spotifyAccessToken");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(7000); connection.setReadTimeout(10000);
        connection.setRequestProperty("Authorization", "Bearer " + token);
        try (var stream = connection.getInputStream()) {
            return new JSONObject(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } finally { connection.disconnect(); }
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
            // Spotify accepts 127.0.0.1 but not "localhost" as a loopback redirect URI.
            // Canonicalizing before creating OAuth state keeps the session cookie on one host.
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
