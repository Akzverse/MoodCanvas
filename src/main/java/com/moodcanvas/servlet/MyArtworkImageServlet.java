package com.moodcanvas.servlet;

import com.moodcanvas.dao.ArtworkUploadDAO;
import com.moodcanvas.model.ArtworkUpload;
import com.moodcanvas.model.User;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;

public class MyArtworkImageServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute("user") instanceof User user)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        String id = request.getParameter("id");
        if (id == null || !id.matches("[0-9a-fA-F-]{36}")) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        try {
            ArtworkUpload artwork = new ArtworkUploadDAO().findImageForUser(id, user.getUserID());
            if (artwork == null || artwork.getImageData() == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND);
                return;
            }
            response.setContentType(artwork.getContentType());
            response.setContentLength(artwork.getImageData().length);
            response.setHeader("Cache-Control", "private, no-store");
            response.setHeader("X-Content-Type-Options", "nosniff");
            response.getOutputStream().write(artwork.getImageData());
        } catch (Exception e) {
            getServletContext().log("Could not read uploaded artwork image", e);
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
    }
}
