<%@ page contentType="text/html;charset=UTF-8" pageEncoding="UTF-8" %>
<%@ taglib prefix="c" uri="jakarta.tags.core" %>
<%
  if (session.getAttribute("user") == null) {
      response.sendRedirect(request.getContextPath() + "/pages/signin.html");
      return;
  }
  String ctx = request.getContextPath();
  String qUploaded = request.getParameter("uploaded");
  String qRemoved  = request.getParameter("removed");
  String qError    = request.getParameter("error");
  Object attrError = request.getAttribute("uploadError");
  String notice = "";
  String noticeType = "ok"; // "ok" or "err"
  if ("true".equals(qUploaded)) {
      notice = "Your artwork was uploaded and will appear in your mood matches.";
  } else if ("true".equals(qRemoved)) {
      notice = "The artwork was removed from your private gallery.";
  } else if (qError != null || attrError != null) {
      noticeType = "err";
      String e = qError != null ? qError : String.valueOf(attrError);
      switch (e) {
          case "invalid"  -> notice = "Please check the title and mood fields — both are required.";
          case "size"     -> notice = "The image file must be between 1 byte and 5 MB.";
          case "type"     -> notice = "Unsupported file format. Please upload a JPEG, PNG, GIF, or WebP image.";
          case "database" -> notice = "The upload could not be saved. Please try again or check the database connection.";
          default         -> notice = "Something went wrong. Please try again.";
      }
  }
%>
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width,initial-scale=1">
  <title>My uploaded art — MoodCanvas</title>
  <link href="https://fonts.googleapis.com/css2?family=DM+Sans:wght@400;500;600;700&family=Fraunces:opsz,wght@9..144,500;9..144,600;9..144,700&display=swap" rel="stylesheet">
  <link rel="stylesheet" href="<%=ctx%>/css/style.css">
  <style>
    .notice-ok  { background:#e8f5e2; color:#3a6b2a; border:1px solid #b6d9a4; }
    .notice-err { background:#fce8e8; color:#852424; border:1px solid #f0b8b8; }
  </style>
</head>
<body>
  <%@ include file="header.jspf" %>
  <main class="app-main wrap">

    <section class="welcome-row">
      <div>
        <span class="eyebrow">YOUR OWN LITTLE GALLERY</span>
        <h1>Share your <em>art.</em></h1>
        <p>Upload a piece that matters to you. It stays private to your account and can appear in mood matches.</p>
      </div>
      <span class="welcome-flower">✿</span>
    </section>

    <% if (!notice.isEmpty()) { %>
    <div class="notice notice-<%=noticeType%>" role="alert" style="margin-bottom:18px;">
      <%=notice%>
    </div>
    <% } %>

    <section class="upload-panel">
      <span class="step-label">ADD A PIECE TO YOUR GALLERY</span>
      <h2>A place for what you've <em>made.</em></h2>
      <form
        action="<%=ctx%>/my-artworks"
        method="post"
        enctype="multipart/form-data"
        class="upload-form"
        id="uploadForm"
      >
        <label>
          Artwork image
          <input
            type="file"
            name="image"
            accept="image/jpeg,image/png,image/gif,image/webp"
            required
            id="imageInput"
          >
          <small>JPEG, PNG, GIF, or WebP — maximum 5 MB.</small>
        </label>

        <label>
          Title <span style="color:var(--accent)">*</span>
          <input name="title" maxlength="180" required placeholder="Give your piece a name">
        </label>

        <label>
          Artist name
          <input name="artist" maxlength="180" placeholder="Leave blank to use your account name">
        </label>

        <label>
          What is it about?
          <textarea name="description" maxlength="1000" rows="3"
            placeholder="A little context helps match this piece with a mood."></textarea>
        </label>

        <label>
          Show it for this feeling <span style="color:var(--accent)">*</span>
          <select name="moodTag" required>
            <option value="all">Any mood</option>
            <option value="melancholic">Melancholic</option>
            <option value="dreamy">Dreamy</option>
            <option value="joyful">Joyful</option>
            <option value="calm">Calm</option>
            <option value="romantic">Romantic</option>
            <option value="reflective">Reflective</option>
          </select>
        </label>

        <div id="uploadPreview" style="display:none;margin-top:4px;">
          <img id="previewImg" alt="Preview" style="max-height:140px;border-radius:8px;border:1px solid var(--border);">
        </div>

        <button class="button" type="submit" id="uploadBtn">
          Upload my art <span>↗</span>
        </button>
      </form>
    </section>

    <section class="results-section">
      <div class="results-heading">
        <div>
          <span class="step-label">ONLY VISIBLE TO YOU</span>
          <h2>Your uploaded <em>pieces.</em></h2>
        </div>
      </div>
      <div class="journal-grid upload-grid">
        <c:choose>
          <c:when test="${empty userArtworks}">
            <div class="journal-empty">
              <span>✿</span>
              <h2>No uploads yet.</h2>
              <p>Share your first image above and it can join your personal mood matches.</p>
            </div>
          </c:when>
          <c:otherwise>
            <c:forEach var="artwork" items="${userArtworks}">
              <article class="journal-card">
                <div class="journal-image">
                  <img
                    src="<%=ctx%>/my-artwork-image?id=<c:out value='${artwork.artworkID}'/>"
                    alt="<c:out value='${artwork.title}'/>"
                    loading="lazy"
                  >
                  <span class="museum-badge"><c:out value="${artwork.moodTag}"/></span>
                </div>
                <div class="journal-info">
                  <span class="journal-date">Your private gallery</span>
                  <h2><c:out value="${artwork.title}"/></h2>
                  <p><c:out value="${artwork.artist}"/></p>
                  <c:if test="${not empty artwork.description}">
                    <p class="upload-description"><c:out value="${artwork.description}"/></p>
                  </c:if>
                  <form
                    action="<%=ctx%>/my-artworks"
                    method="post"
                    onsubmit="return confirm('Remove this upload from your private gallery?')"
                  >
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="artworkID" value="<c:out value='${artwork.artworkID}'/>">
                    <button class="remove-button" type="submit">Remove <span>&times;</span></button>
                  </form>
                </div>
              </article>
            </c:forEach>
          </c:otherwise>
        </c:choose>
      </div>
    </section>

    <footer class="app-footer">
      <span>Your uploads stay private to your account</span>
      <span>Made for the way you feel <b>♡</b></span>
    </footer>
  </main>

  <script>
    // Live image preview before upload
    var imageInput = document.getElementById('imageInput');
    var previewDiv  = document.getElementById('uploadPreview');
    var previewImg  = document.getElementById('previewImg');
    if (imageInput) {
      imageInput.addEventListener('change', function () {
        var file = imageInput.files && imageInput.files[0];
        if (!file) { previewDiv.style.display = 'none'; return; }

        // Client-side size guard (5 MB)
        if (file.size > 5 * 1024 * 1024) {
          alert('That file is larger than 5 MB. Please choose a smaller image.');
          imageInput.value = '';
          previewDiv.style.display = 'none';
          return;
        }

        var reader = new FileReader();
        reader.onload = function (e) {
          previewImg.src = e.target.result;
          previewDiv.style.display = 'block';
        };
        reader.readAsDataURL(file);
      });
    }

    // Disable submit button while uploading to prevent double-submit
    var uploadForm = document.getElementById('uploadForm');
    var uploadBtn  = document.getElementById('uploadBtn');
    if (uploadForm && uploadBtn) {
      uploadForm.addEventListener('submit', function () {
        uploadBtn.disabled = true;
        uploadBtn.textContent = 'Uploading...';
      });
    }
  </script>
</body>
</html>
