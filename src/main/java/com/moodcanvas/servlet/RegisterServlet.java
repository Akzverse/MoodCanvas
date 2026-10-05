package com.moodcanvas.servlet;

import com.moodcanvas.dao.UserDAO;
import com.moodcanvas.util.PasswordUtil;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Locale;

public class RegisterServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String name = clean(request.getParameter("name"));
        String email = clean(request.getParameter("email")).toLowerCase(Locale.ROOT);
        String password = request.getParameter("password");
        if (name.isBlank() || name.length() > 100 || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")
                || password == null || password.length() < 8 || password.length() > 200) {
            response.sendRedirect(request.getContextPath() + "/pages/register.html?error=invalid");
            return;
        }
        try {
            UserDAO users = new UserDAO();
            if (users.emailExists(email)) {
                response.sendRedirect(request.getContextPath() + "/pages/register.html?error=exists");
                return;
            }
            users.createUser(name, email, PasswordUtil.hash(password));
            response.sendRedirect(request.getContextPath() + "/pages/signin.html?registered=true");
        } catch (SQLException e) {
            getServletContext().log("MoodCanvas registration could not reach its database", e);
            response.sendRedirect(request.getContextPath() + "/pages/register.html?error=database");
        }
    }

    private String clean(String value) {
        return value == null ? "" : value.trim();
    }
}
