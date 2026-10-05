package com.moodcanvas.servlet;

import com.moodcanvas.dao.ArtworkUploadDAO;
import com.moodcanvas.model.ArtworkUpload;
import com.moodcanvas.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public class MyArtworkServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private static final int MAX_IMAGE_BYTES = 5 * 1024 * 1024;
    private static final Set<String> MOODS = Set.of("all", "melancholic", "dreamy", "joyful", "calm", "romantic", "reflective");

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        User user = currentUser(request);
        if (user == null) {
            response.sendRedirect(request.getContextPath() + "/pages/signin.html");
            return;
        }
        try {
            request.setAttribute("userArtworks", new ArtworkUploadDAO().listForUser(user.getUserID()));
            request.getRequestDispatcher("/pages/my-artwork.jsp").forward(request, response);
        } catch (Exception e) {
            getServletContext().log("Could not load the user's uploaded artwork", e);
            request.setAttribute("userArtworks", java.util.List.of());
            request.setAttribute("uploadError", "database");
            request.getRequestDispatcher("/pages/my-artwork.jsp").forward(request, response);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        User user = currentUser(request);
        if (user == null) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        request.setCharacterEncoding("UTF-8");
        ArtworkUploadDAO uploads = new ArtworkUploadDAO();
        if ("delete".equals(request.getParameter("action"))) {
            String id = request.getParameter("artworkID");
            if (id == null || !id.matches("[0-9a-fA-F-]{36}")) {
                response.sendRedirect(request.getContextPath() + "/my-artworks?error=invalid");
                return;
            }
            try {
                response.sendRedirect(request.getContextPath() + "/my-artworks?removed=" + uploads.remove(id, user.getUserID()));
            } catch (Exception e) {
                getServletContext().log("Could not remove uploaded artwork", e);
                response.sendRedirect(request.getContextPath() + "/my-artworks?error=database");
            }
            return;
        }

        try {
            String title = clean(request.getParameter("title"));
            String artist = clean(request.getParameter("artist"));
            String description = clean(request.getParameter("description"));
            String moodTag = clean(request.getParameter("moodTag")).toLowerCase(Locale.ROOT);
            Part image = request.getPart("image");
            if (title.isBlank() || title.length() > 180 || artist.length() > 180 || description.length() > 1000
                    || !MOODS.contains(moodTag)) {
                redirectError(request, response, "invalid");
                return;
            }
            if (image == null || image.getSize() < 1 || image.getSize() > MAX_IMAGE_BYTES) {
                redirectError(request, response, "size");
                return;
            }
            byte[] bytes;
            try (InputStream input = image.getInputStream()) {
                bytes = input.readNBytes(MAX_IMAGE_BYTES + 1);
            }
            if (bytes.length == 0 || bytes.length > MAX_IMAGE_BYTES) {
                redirectError(request, response, "size");
                return;
            }
            String contentType = sniffImageType(bytes);
            if (contentType == null) {
                redirectError(request, response, "type");
                return;
            }
            if (artist.isBlank()) artist = user.getName();
            ArtworkUpload artwork = new ArtworkUpload(UUID.randomUUID().toString(), title, artist, description, moodTag,
                    contentType, bytes, null);
            uploads.create(artwork, user.getUserID());
            response.sendRedirect(request.getContextPath() + "/my-artworks?uploaded=true");
        } catch (IllegalStateException e) {
            redirectError(request, response, "size");
        } catch (Exception e) {
            getServletContext().log("Could not save uploaded artwork", e);
            redirectError(request, response, "database");
        }
    }

    private User currentUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        return session != null && session.getAttribute("user") instanceof User user ? user : null;
    }

    private void redirectError(HttpServletRequest request, HttpServletResponse response, String error) throws IOException {
        response.sendRedirect(request.getContextPath() + "/my-artworks?error=" + error);
    }

    private String clean(String value) {
        return value == null ? "" : value.strip();
    }

    private String sniffImageType(byte[] bytes) {
        if (bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4e && bytes[3] == 0x47
                && bytes[4] == 0x0d && bytes[5] == 0x0a && bytes[6] == 0x1a && bytes[7] == 0x0a) return "image/png";
        if (bytes.length >= 3 && (bytes[0] & 0xff) == 0xff && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff) return "image/jpeg";
        if (bytes.length >= 6 && bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F'
                && bytes[3] == '8' && (bytes[4] == '7' || bytes[4] == '9') && bytes[5] == 'a') return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return "image/webp";
        return null;
    }
}
