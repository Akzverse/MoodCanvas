<%@ page import="com.moodcanvas.model.User" contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %><%
User current = (User) session.getAttribute("user");
if (current == null) {
    response.sendRedirect(request.getContextPath() + "/pages/signin.html");
    return;
}
String theme = "warm";
if (request.getCookies() != null) {
    for (jakarta.servlet.http.Cookie c : request.getCookies()) {
        if ("moodcanvas_theme".equals(c.getName())) theme = c.getValue();
    }
}
if (!java.util.Set.of("warm", "cool", "dark").contains(theme)) theme = "warm";
String spotifyMoodInput = (String) session.getAttribute("spotifyMoodInput");
Integer spotifyTrackCount = (Integer) session.getAttribute("spotifyTrackCount");
session.removeAttribute("spotifyMoodInput");
session.removeAttribute("spotifyTrackCount");
if (spotifyMoodInput != null) pageContext.setAttribute("spotifyMoodInput", spotifyMoodInput);
if (spotifyTrackCount != null) pageContext.setAttribute("spotifyTrackCount", spotifyTrackCount);
%>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>Your mood, your art — MoodCanvas</title>
  <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Fraunces:opsz,wght@9..144,500;9..144,600;9..144,700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="../css/style.css">
</head>
<body class="theme-<%=theme%>">
  <%@ include file="header.jspf" %>
  <main class="app-main wrap">
    <section class="welcome-row">
      <div>
        <span class="eyebrow"><span class="eyebrow-dot"></span> A MOMENT FOR YOU</span>
        <h1>Welcome back, <em><c:out value="${sessionScope.user.name}"/>.</em></h1>
        <p>Let’s find a little art for the way you feel.</p>
      </div>
      <span class="welcome-flower">✿</span>
    </section>

    <section class="mood-panel">
      <div class="mood-panel-head">
        <div>
          <span class="step-label">01 <i>—</i> CHECK IN WITH YOURSELF</span>
          <h2>What’s on your <em>mind?</em></h2>
        </div>
        <span class="panel-sparkle">✳</span>
      </div>
      <form id="moodForm">
        <label class="sr-only" for="moodText">Describe how you feel, or enter music you have been listening to</label>
        <textarea id="moodText" name="moodText" maxlength="1000" placeholder="Describe how you feel, or paste a few songs or artists that have been on your mind…" required><c:out value="${spotifyMoodInput}"/></textarea>
        <input type="hidden" id="moodSource" value="<%=spotifyMoodInput == null ? "" : "spotify"%>">
        <div class="mood-panel-bottom">
          <div class="mood-pills">
            <button type="button" class="pill pill-lilac" data-mood="melancholic">☾ Melancholic</button>
            <button type="button" class="pill pill-blue" data-mood="dreamy">✧ Dreamy</button>
            <button type="button" class="pill pill-yellow" data-mood="joyful">☼ Joyful</button>
            <button type="button" class="pill pill-green" data-mood="calm">◌ Calm</button>
            <button type="button" class="pill pill-pink" data-mood="romantic">♡ Romantic</button>
            <button type="button" class="pill pill-peach" data-mood="reflective">✳ Reflective</button>
          </div>
          <button class="button" type="submit">Find my art <span>↗</span></button>
        </div>
      </form>
    </section>

    <aside class="spotify-panel">
      <div class="spotify-copy">
        <span class="step-label">MUSIC-INSPIRED SEARCH</span>
        <h2>Bring a song, album, or playlist into your canvas.</h2>
        <p>Paste any Spotify link (song, album, or playlist) and choose <strong>Import music</strong> to immediately find art inspired by its vibe. Or click <strong>Connect Spotify</strong> to sync your recently played tracks.</p>
        <form id="spotifyImportForm" class="spotify-import-form">
          <label class="sr-only" for="spotifyLink">Spotify track, album, or playlist link</label>
          <input id="spotifyLink" type="text" maxlength="500" placeholder="Paste link (e.g. https://open.spotify.com/track/... or /playlist/...)" required autocomplete="off">
          <button class="button button-light" type="submit">Import music <span>↗</span></button>
        </form>
      </div>
      <a class="button button-light spotify-connect" href="../spotify/connect">Connect Spotify <span>↗</span></a>
    </aside>

    <div id="spotifyNotice" class="notice" role="status" hidden="hidden"></div>

    <section class="results-section">
      <div class="results-heading">
        <div>
          <span class="step-label">02 <i>—</i> A COLLECTION FOR THIS FEELING</span>
          <h2>Your mood, <em>in art.</em></h2>
        </div>
        <span id="resultMood" class="result-mood"></span>
      </div>
      <div id="loader" class="loader" hidden="hidden">
        <span class="spinner"></span>
        <span>Finding art that feels like you…</span>
      </div>
      <div id="artworkGrid" class="artwork-grid">
        <div class="empty-discovery">
          <span>✿</span>
          <h3>Your collection is waiting</h3>
          <p>Tell us how you feel or import music above, and we’ll find a few works of art to meet you there.</p>
        </div>
      </div>
    </section>

    <footer class="app-footer">
      <span>Art from The Met, Rijksmuseum, and the Art Institute of Chicago</span>
      <span>Made for the way you feel <b>♡</b></span>
    </footer>
  </main>

  <div id="toast" class="toast" role="status"></div>

  <script>
    var spotifyStatus = new URLSearchParams(location.search).get('spotify');
    var spotifyMessages = {
      'ready': 'Your recent tracks are ready! Searching art for your musical vibe…',
      'setup-required': 'Spotify account connection requires Spotify API keys in your server environment. You can still paste any Spotify song or playlist link directly to import music!',
      'session-expired': 'The Spotify connection expired. Please connect again or paste a link directly.',
      'cancelled': 'Spotify connection was cancelled. You can still import links or describe your mood.',
      'no-tracks': 'Spotify did not return recent tracks. Try again later or paste a link.',
      'unavailable': 'Spotify could not provide listening history. You can still paste any song or playlist link directly!'
    };
    if (spotifyMessages[spotifyStatus]) {
      var spotifyNotice = document.querySelector('#spotifyNotice');
      spotifyNotice.hidden = false;
      spotifyNotice.textContent = spotifyMessages[spotifyStatus];
    }
  </script>
  <script src="../js/main.js" defer></script>
</body>
</html>
