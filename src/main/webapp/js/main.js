(function () {
  'use strict';
  var form = document.querySelector('#moodForm');
  if (!form) return;

  var grid = document.querySelector('#artworkGrid');
  var loader = document.querySelector('#loader');
  var area = document.querySelector('#moodText');
  var source = document.querySelector('#moodSource');
  var toast = document.querySelector('#toast');
  var toastTimer;
  var importForm = document.querySelector('#spotifyImportForm');

  if (importForm) {
    importForm.addEventListener('submit', function (event) {
      event.preventDefault();
      var input = document.querySelector('#spotifyLink');
      var button = importForm.querySelector('button');
      var originalButtonHtml = button.innerHTML;
      var notice = document.querySelector('#spotifyNotice');

      if (notice) notice.hidden = true;
      button.disabled = true;
      button.textContent = 'Importing...';

      fetch('../spotify/import', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ spotifyLink: input.value.trim() })
      }).then(function (response) {
        if (!response.ok) {
          return response.text().then(function (message) {
            var cleanMsg = message.replace(/<[^>]*>/g, '').trim();
            if (response.status === 412) throw new Error('Connect Spotify first or paste a public Spotify link.');
            if (response.status === 502) throw new Error('Spotify could not read that link. Check the link or privacy settings.');
            throw new Error(cleanMsg.slice(0, 180) || 'Could not read that Spotify link. Make sure it is a valid track, album, or playlist.');
          });
        }
        return response.json();
      }).then(function (result) {
        area.value = result.text;
        if (source) source.value = 'spotify';
        if (result.mood) {
          Array.prototype.forEach.call(document.querySelectorAll('[data-mood]'), function (pill) {
            pill.classList.toggle('active', pill.getAttribute('data-mood') === result.mood);
          });
        }
        input.value = '';
        showToast('Music imported! Finding art for your vibe...');
        submitMoodForm();
      }).catch(function (error) {
        if (notice) {
          notice.hidden = false;
          notice.textContent = error.message;
        }
        showToast(error.message);
      }).then(function () {
        button.disabled = false;
        button.innerHTML = originalButtonHtml;
      });
    });
  }

  function showToast(message) {
    if (!toast) return;
    toast.textContent = message;
    toast.classList.add('show');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { toast.classList.remove('show'); }, 3000);
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, function (ch) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch];
    });
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-mood]'), function (pill) {
    pill.addEventListener('click', function () {
      area.value = 'I feel ' + pill.getAttribute('data-mood') + ' today';
      if (source) source.value = '';
      Array.prototype.forEach.call(document.querySelectorAll('[data-mood]'), function (other) {
        other.classList.toggle('active', other === pill);
      });
      area.focus();
    });
  });

  function submitMoodForm() {
    if (!area.value.trim()) return;
    loader.hidden = false;
    grid.hidden = true;

    fetch('../mood', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ moodText: area.value.trim(), source: source ? source.value : '' })
    }).then(function (response) {
      if (!response.ok) {
        return response.text().then(function (body) {
          var msg = body.replace(/<[^>]*>/g, '').trim().slice(0, 200) || 'Could not load artworks. Please try again.';
          throw new Error(msg);
        });
      }
      return response.text();
    }).then(function (xmlText) {
      var parser = new DOMParser();
      var xml = parser.parseFromString(xmlText, 'application/xml');
      var parseError = xml.querySelector('parsererror');
      if (parseError) {
        throw new Error('Received unexpected data from the server. Please try again.');
      }
      var artworks = xml.querySelectorAll('artwork');
      renderArtworks(artworks);
      var moodEl = xml.querySelector('mood');
      var resultMood = document.querySelector('#resultMood');
      if (resultMood) resultMood.textContent = moodEl ? 'A little ' + moodEl.textContent : '';
    }).catch(function (error) {
      grid.innerHTML = '<div class="empty-discovery"><span>\u2601</span><h3>Something went wrong</h3><p>' + escapeHtml(error.message) + '</p></div>';
    }).then(function () {
      loader.hidden = true;
      grid.hidden = false;
    });
  }

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    submitMoodForm();
  });

  function renderArtworks(artworks) {
    grid.innerHTML = '';
    if (!artworks || artworks.length === 0) {
      grid.innerHTML = '<div class="empty-discovery"><span>\u2601</span><h3>No artworks found</h3><p>The museum collections did not return any images for this search. Try a different mood or description.</p></div>';
      return;
    }
    var loadedCount = 0;
    Array.prototype.forEach.call(artworks, function (artwork) {
      var titleNode = artwork.querySelector('title');
      var artistNode = artwork.querySelector('artist');
      var imageNode = artwork.querySelector('imageURL');
      var museumNode = artwork.querySelector('museum');
      var data = {
        artworkID: artwork.getAttribute('id') || '',
        title: titleNode ? titleNode.textContent : 'Untitled',
        artist: artistNode ? artistNode.textContent : 'Unknown artist',
        imageURL: imageNode ? imageNode.textContent : '',
        museum: museumNode ? museumNode.textContent : 'Museum'
      };
      if (!data.imageURL) return; // skip if no image URL

      var card = document.createElement('article');
      card.className = 'artwork-card';
      card.innerHTML =
        '<div class="artwork-image">' +
          '<img src="' + escapeHtml(data.imageURL) + '" alt="' + escapeHtml(data.title) + '" loading="lazy">' +
          '<span class="museum-badge">' + escapeHtml(data.museum) + '</span>' +
        '</div>' +
        '<div class="artwork-info">' +
          '<span class="artwork-meta">A MATCH FOR YOUR MOOD</span>' +
          '<h3>' + escapeHtml(data.title) + '</h3>' +
          '<p>' + escapeHtml(data.artist) + '</p>' +
          '<button class="save-button" type="button">\u2661 Save to journal</button>' +
        '</div>';

      var img = card.querySelector('img');
      var saveButton = card.querySelector('button');

      img.addEventListener('load', function () { loadedCount++; });
      img.addEventListener('error', function () {
        card.remove();
        if (grid.children.length === 0) {
          grid.innerHTML = '<div class="empty-discovery"><span>\u2601</span><h3>Images failed to load</h3><p>The museum servers could not deliver images right now. Please try again in a moment.</p></div>';
        }
      });

      saveButton.addEventListener('click', function () { saveArtwork(data, saveButton); });
      grid.appendChild(card);
    });

    if (grid.children.length === 0) {
      grid.innerHTML = '<div class="empty-discovery"><span>\u2661</span><h3>No images returned</h3><p>The museums did not return images for this mood. Try a different mood or description.</p></div>';
    }
  }

  function saveArtwork(data, button) {
    button.disabled = true;
    fetch('../journal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(data)
    }).then(function (response) {
      if (!response.ok) throw new Error('Save failed');
      button.textContent = '\u2713 Saved to journal';
      button.classList.add('saved');
      showToast('Added to your journal');
    }).catch(function () {
      button.disabled = false;
      showToast('Could not save this artwork. Try again.');
    });
  }

  Array.prototype.forEach.call(document.querySelectorAll('[data-theme]'), function (button) {
    button.addEventListener('click', function () {
      var theme = button.getAttribute('data-theme');
      fetch('../setTheme?theme=' + encodeURIComponent(theme)).then(function (response) {
        if (!response.ok) throw new Error('Theme update failed');
        document.body.className = 'theme-' + theme;
        Array.prototype.forEach.call(document.querySelectorAll('[data-theme]'), function (other) {
          other.classList.toggle('active', other === button);
        });
      }).catch(function () { showToast('Could not save your theme right now'); });
    });
  });

  // Auto-search when Spotify OAuth returns with tracks already in the textarea
  if (new URLSearchParams(location.search).get('spotify') === 'ready' && area && area.value.trim()) {
    window.setTimeout(function () { submitMoodForm(); }, 500);
  }
}());
