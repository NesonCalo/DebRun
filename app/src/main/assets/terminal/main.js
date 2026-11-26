(function () {
  try {
    if (typeof Terminal !== 'function') {
      throw new Error('Terminal global missing (xterm.js did not load)');
    }
    if (!window.FitAddon || !window.FitAddon.FitAddon) {
      throw new Error('FitAddon global missing (addon-fit.js did not load)');
    }

    var term = new Terminal({
      fontFamily: 'monospace, "Cascadia Mono", "JetBrains Mono"',
      fontSize: 13,
      lineHeight: 1.15,
      cursorBlink: true,
      scrollback: 8000,
      drawBoldTextInBrightColors: false,
      theme: {
        background: '#0d1014',
        foreground: '#e6e9ee',
        cursor: '#7bd88f',
        selectionBackground: '#2a3444',
        black: '#1b2028',
        red: '#ff7b72',
        green: '#7bd88f',
        yellow: '#d2a8ff',
        blue: '#79c0ff',
        magenta: '#ff9bce',
        cyan: '#56d4dd',
        white: '#e6e9ee'
      }
    });

    var fit = new FitAddon.FitAddon();
    term.loadAddon(fit);
    term.open(document.getElementById('t'));

    // Percentage heights (html/body { height: 100% }) resolve to 0 in some
    // WebView/Chromium combinations, which leaves xterm stuck at one row, so the
    // container is sized in explicit pixels from the real viewport height.
    function applyLayout() {
      var h = Math.max(window.innerHeight || 0, 120);
      document.documentElement.style.height = h + 'px';
      document.body.style.height = h + 'px';
      document.getElementById('t').style.height = h + 'px';
    }

    function fitNow() {
      try {
        applyLayout();
        fit.fit();
      } catch (e) { }
    }
    fitNow();

    function emit(data) {
      if (window.DrBridge && DrBridge.sendData) DrBridge.sendData(data);
    }

    term.onData(emit);
    term.onResize(function (size) {
      if (window.DrBridge && DrBridge.resize) DrBridge.resize(size.cols, size.rows);
    });

    window.dr = {
      write: function (text) { term.write(text); },
      clear: function () { term.clear(); },
      inject: function (data) { emit(data); },
      focus: function () { term.focus(); },
      fit: function () {
        // Try twice: the first pass may run before the viewport settles.
        fitNow();
        setTimeout(fitNow, 50);
      },
      fontSize: function (size) {
        term.options.fontSize = size;
        fitNow();
      }
    };

    window.addEventListener('resize', fitNow);
    window.addEventListener('orientationchange', function () { setTimeout(fitNow, 300); });
    if (window.ResizeObserver) {
      try {
        new ResizeObserver(function () { fitNow(); }).observe(document.body);
      } catch (e) { }
    }

    // Keep keyboard focus on the hidden textarea so tapping the screen re-opens the IME.
    document.addEventListener('pointerdown', function () { term.focus(); }, true);
    document.addEventListener('touchstart', function () { term.focus(); }, true);

    term.focus();
    // The WebView often reports a zero-height viewport before its first layout pass,
    // so retry a few times instead of trusting a single fit().
    [0, 60, 200, 500, 1200].forEach(function (delay) {
      setTimeout(fitNow, delay);
    });
    setTimeout(function () {
      fitNow();
      if (window.DrBridge && DrBridge.ready) DrBridge.ready();
      console.log('xterm ready ' + term.cols + 'x' + term.rows);
    }, 60);
  } catch (e) {
    if (window.showErr) window.showErr('main.js: ' + (e && e.message ? e.message : e));
  }
})();
