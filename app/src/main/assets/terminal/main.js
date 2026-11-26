(function () {
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

  function fitNow() {
    try { fit.fit(); } catch (e) { }
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
    fontSize: function (size) {
      term.options.fontSize = size;
      fitNow();
    }
  };

  window.addEventListener('resize', fitNow);
  window.addEventListener('orientationchange', function () { setTimeout(fitNow, 300); });

  term.focus();
  setTimeout(function () {
    fitNow();
    if (window.DrBridge && DrBridge.ready) DrBridge.ready();
  }, 60);
})();
