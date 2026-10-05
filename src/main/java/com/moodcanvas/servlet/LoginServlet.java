package com.moodcanvas.servlet;

import com.moodcanvas.dao.UserDAO;
import com.moodcanvas.model.User;
import com.moodcanvas.util.PasswordUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.sql.SQLException;

public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String email = request.getParameter("email");
        String password = request.getParameter("password");
        try {
            User user = new UserDAO().getUserByEmail(email == null ? "" : email.trim());
            if (user != null && user.getPasswordHash().equals(PasswordUtil.hash(password == null ? "" : password))) {
                HttpSession session = request.getSession(true);
                session.setAttribute("user", user);
                session.setAttribute("role", user.getRole());
                String theme = user.getTheme();
                if (request.getCookies() != null) {
                    for (Cookie cookie : request.getCookies()) {
                        if (cookie.getName().equals("moodcanvas_theme")) theme = cookie.getValue();
                    }
                }
                session.setAttribute("theme", theme);
                response.sendRedirect(request.getContextPath() + ("admin".equals(user.getRole()) ? "/admin" : "/pages/dashboard.jsp"));
                return;
            }
            response.sendRedirect(request.getContextPath() + "/pages/signin.html?error=invalid");
        } catch (SQLException e) {
            getServletContext().log("MoodCanvas login could not reach its database", e);
            response.sendRedirect(request.getContextPath() + "/pages/signin.html?error=database");
        }
    }
}
