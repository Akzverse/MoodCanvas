package com.moodcanvas.servlet;
import jakarta.servlet.http.*;import java.io.*;
public class LogoutServlet extends HttpServlet {private static final long serialVersionUID=1L;protected void doGet(HttpServletRequest q,HttpServletResponse s)throws IOException{HttpSession x=q.getSession(false);if(x!=null)x.invalidate();s.sendRedirect(q.getContextPath()+"/pages/index.html");}}
