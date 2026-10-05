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
  if (importForm) importForm.addEventListener('submit', function (event) {
    event.preventDefault();
    var input = document.querySelector('#spotifyLink');
    var button = importForm.querySelector('button');
    button.disabled = true;
    fetch('../spotify/import', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ spotifyLink: input.value.trim() })
    }).then(function (response) {
      if (!response.ok) return response.text().then(function (message) {
        if (response.status === 412) throw new Error('Connect Spotify first, then import your song or playlist.');
        if (response.status === 502) throw new Error('Spotify could not read that playlist. Try a playlist owned by or shared with the connected account.');
        throw new Error(message.replace(/<[^>]*>/g, '').slice(0, 220) || 'Could not import that Spotify link.');
      });
      return response.json();
    }).then(function (result) {
      area.value = result.text;
      if (source) source.value = 'spotify';
      Array.prototype.forEach.call(document.querySelectorAll('[data-mood]'), function (pill) { pill.classList.remove('active'); });
      area.focus();
      showToast('Imported ' + result.count + (result.count === 1 ? ' song' : ' songs') + '. Choose a mood or search.');
    }).catch(function (error) {
      var notice = document.querySelector('#spotifyNotice');
      notice.hidden = false;
      notice.textContent = error.message;
    }).then(function () { button.disabled = false; });
  });

  function showToast(message) {
    toast.textContent = message;
    toast.classList.add('show');
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { toast.classList.remove('show'); }, 2500);
  }

  function escapeHtml(value) {
    return String(value || '').replace(/[&<>"']/g, function (character) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[character];
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

  form.addEventListener('submit', function (event) {
    event.preventDefault();
    if (!area.value.trim()) return;
    loader.hidden = false;
    grid.hidden = true;
    fetch('../mood', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams({ moodText: area.value.trim(), source: source ? source.value : '' })
    }).then(function (response) {
      if (!response.ok) throw new Error('We couldn’t find your art just now. Please try again.');
      return response.text();
    }).then(function (source) {
      var xml = new DOMParser().parseFromString(source, 'application/xml');
      if (xml.querySelector('parsererror')) throw new Error('We had trouble reading the collection. Please try again.');
      renderArtworks(xml.querySelectorAll('artwork'));
      var mood = xml.querySelector('mood');
      document.querySelector('#resultMood').textContent = mood ? 'A little ' + mood.textContent : '';
    }).catch(function (error) {
      grid.innerHTML = '<div class="empty-discovery"><span>\u2601</span><h3>We lost our place for a moment</h3><p>' + escapeHtml(error.message) + '</p></div>';
    }).then(function () {
      loader.hidden = true;
      grid.hidden = false;
    });
  });

  function renderArtworks(artworks) {
    grid.innerHTML = '';
    if (!artworks.length) {
      grid.innerHTML = '<div class="empty-discovery"><span>✿</span><h3>No matching artworks came back</h3><p>The museum collections did not return images for this search. Try another mood or a more specific description.</p></div>';
      return;
    }
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
      var card = document.createElement('article');
      card.className = 'artwork-card';
      card.innerHTML = '<div class="artwork-image"><img src="' + escapeHtml(data.imageURL) + '" alt="' + escapeHtml(data.title) + '" loading="lazy"><span class="museum-badge">' + escapeHtml(data.museum) + '</span></div>' +
        '<div class="artwork-info"><span class="artwork-meta">A MATCH FOR YOUR MOOD</span><h3>' + escapeHtml(data.title) + '</h3><p>' + escapeHtml(data.artist) + '</p><button class="save-button" type="button">♡ Save to journal</button></div>';
      var saveButton = card.querySelector('button');
      card.querySelector('img').addEventListener('error', function () { card.remove(); });
      saveButton.addEventListener('click', function () { saveArtwork(data, saveButton); });
      grid.appendChild(card);
    });
  }

  function saveArtwork(data, button) {
    button.disabled = true;
    fetch('../journal', {
      method: 'POST',
      headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
      body: new URLSearchParams(data)
    }).then(function (response) {
      if (!response.ok) throw new Error('Save failed');
      button.textContent = '✓ Saved to journal';
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
}());
