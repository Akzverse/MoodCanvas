(function () {
  'use strict';
  var timer;
  var toast = document.querySelector('#toast');

  Array.prototype.forEach.call(document.querySelectorAll('.remove-button'), function (button) {
    button.addEventListener('click', function () {
      button.disabled = true;
      fetch('../journal', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: new URLSearchParams({ action: 'remove', journalID: button.getAttribute('data-id') })
      }).then(function (response) {
        if (!response.ok) throw new Error('Remove failed');
        var card = button.closest('[data-journal-id]');
        card.remove();
        if (!document.querySelector('.journal-card')) {
          document.querySelector('#journalGrid').innerHTML = '<div class="journal-empty"><span>✿</span><h2>Your art journal is empty.</h2><p>Start exploring to save pieces that move you.</p><a class="button" href="dashboard.jsp">Find your art <span>↗</span></a></div>';
        }
        toast.textContent = 'Removed from your journal';
        toast.classList.add('show');
        window.clearTimeout(timer);
        timer = window.setTimeout(function () { toast.classList.remove('show'); }, 2200);
      }).catch(function () {
        button.disabled = false;
        toast.textContent = 'Could not remove this artwork. Try again.';
        toast.classList.add('show');
      });
    });
  });

  Array.prototype.forEach.call(document.querySelectorAll('[data-theme]'), function (button) {
    button.addEventListener('click', function () {
      var theme = button.getAttribute('data-theme');
      fetch('../setTheme?theme=' + encodeURIComponent(theme)).then(function (response) {
        if (!response.ok) throw new Error('Theme update failed');
        document.body.className = 'theme-' + theme;
      }).catch(function () {});
    });
  });
}());
