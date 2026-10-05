<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %><%@ taglib prefix="c" uri="jakarta.tags.core" %><% if(session.getAttribute("user")==null){response.sendRedirect(request.getContextPath()+"/pages/signin.html");return;} %>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
  <title>My uploaded art — MoodCanvas</title>
  <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Fraunces:opsz,wght@9..144,500;9..144,600;9..144,700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%=request.getContextPath()%>/css/style.css">
</head>
<body>
  <%@ include file="header.jspf" %>
  <main class="app-main wrap">
    <section class="welcome-row">
      <div><span class="eyebrow">YOUR OWN LITTLE GALLERY</span><h1>Share your <em>art.</em></h1><p>Upload a piece that matters to you. It stays private to your account and can appear in mood matches for you.</p></div>
      <span class="welcome-flower">✿</span>
    </section>
    <div id="uploadNotice" class="notice" role="status" data-error="<c:out value='${uploadError}'/>" hidden="hidden"></div>
    <section class="upload-panel">
      <span class="step-label">ADD A PIECE TO YOUR GALLERY</span>
      <h2>A place for what you’ve <em>made.</em></h2>
      <form action="<%=request.getContextPath()%>/my-artworks" method="post" enctype="multipart/form-data" class="upload-form">
        <label>Artwork image <input type="file" name="image" accept="image/jpeg,image/png,image/gif,image/webp" required><small>JPEG, PNG, GIF, or WebP. Maximum 5 MB.</small></label>
        <label>Title <input name="title" maxlength="180" required placeholder="Give your piece a name"></label>
        <label>Artist name <input name="artist" maxlength="180" placeholder="Leave blank to use your account name"></label>
        <label>What is it about? <textarea name="description" maxlength="1000" rows="3" placeholder="A little context helps match this piece with a mood."></textarea></label>
        <label>Show it for this feeling
          <select name="moodTag" required>
            <option value="all">Any mood</option><option value="melancholic">Melancholic</option><option value="dreamy">Dreamy</option>
            <option value="joyful">Joyful</option><option value="calm">Calm</option><option value="romantic">Romantic</option><option value="reflective">Reflective</option>
          </select>
        </label>
        <button class="button" type="submit">Upload my art <span>↗</span></button>
      </form>
    </section>
    <section class="results-section">
      <div class="results-heading"><div><span class="step-label">ONLY VISIBLE TO YOU</span><h2>Your uploaded <em>pieces.</em></h2></div></div>
      <div class="journal-grid upload-grid">
        <c:choose>
          <c:when test="${empty userArtworks}"><div class="journal-empty"><span>✿</span><h2>No uploads yet.</h2><p>Share your first image above and it can join your personal mood matches.</p></div></c:when>
          <c:otherwise>
            <c:forEach var="artwork" items="${userArtworks}">
              <article class="journal-card">
                <div class="journal-image"><img src="<%=request.getContextPath()%>/my-artwork-image?id=<c:out value='${artwork.artworkID}'/>" alt="<c:out value='${artwork.title}'/>"><span class="museum-badge"><c:out value="${artwork.moodTag}"/></span></div>
                <div class="journal-info"><span class="journal-date">Your private gallery</span><h2><c:out value="${artwork.title}"/></h2><p><c:out value="${artwork.artist}"/></p><c:if test="${not empty artwork.description}"><p class="upload-description"><c:out value="${artwork.description}"/></p></c:if>
                  <form action="<%=request.getContextPath()%>/my-artworks" method="post" onsubmit="return confirm('Remove this upload from your private gallery?')"><input type="hidden" name="action" value="delete"><input type="hidden" name="artworkID" value="<c:out value='${artwork.artworkID}'/>"><button class="remove-button" type="submit">Remove <span>×</span></button></form>
                </div>
              </article>
            </c:forEach>
          </c:otherwise>
        </c:choose>
      </div>
    </section>
    <footer class="app-footer"><span>Your uploads stay private to your account</span><span>Made for the way you feel <b>♡</b></span></footer>
  </main>
  <script>var uploadQuery=new URLSearchParams(location.search);var uploadMessages={uploaded:'Your artwork was uploaded and is ready for your mood matches.',removed:'The artwork was removed from your private gallery.',invalid:'Check the title, artist and mood fields and try again.',size:'Choose an image file no larger than 5 MB.',type:'This file is not a supported image. Please use JPEG, PNG, GIF or WebP.',database:'The upload could not be saved. Check the database connection and try again.'};var uploadMessage=uploadQuery.has('uploaded')?'uploaded':uploadQuery.get('removed')==='true'?'removed':uploadQuery.get('error')||document.querySelector('#uploadNotice').dataset.error;if(uploadMessages[uploadMessage]){var notice=document.querySelector('#uploadNotice');notice.hidden=false;notice.textContent=uploadMessages[uploadMessage]}</script>
</body>
</html>
