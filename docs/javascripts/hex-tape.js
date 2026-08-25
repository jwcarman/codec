/*
 * Renders each .jw-tape element as the bytes of its data-word: one cell per
 * character showing the hex value with the character beneath. On load a
 * write head sweeps the tape once, left to right, settling each cell from a
 * placeholder to its value; the head is the only cell lit while it passes,
 * and nothing stays lit afterwards. Under prefers-reduced-motion the tape
 * renders settled with no sweep.
 */
(function () {
  function render(tape) {
    var word = tape.dataset.word || "";
    if (!word) {
      return;
    }
    var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
    var step = 120;
    tape.textContent = "";
    Array.from(word).forEach(function (ch, i) {
      var hex = ch.codePointAt(0).toString(16).toUpperCase().padStart(2, "0");
      var cell = document.createElement("span");
      cell.className = "jw-tape__cell";
      var value = document.createElement("b");
      var label = document.createElement("i");
      label.textContent = ch;
      cell.appendChild(value);
      cell.appendChild(label);
      tape.appendChild(cell);
      var settle = function () {
        value.textContent = hex;
        cell.classList.add("is-set");
      };
      if (reduce) {
        settle();
      } else {
        value.textContent = "··";
        setTimeout(function () {
          cell.classList.add("is-head");
          settle();
        }, 300 + i * step);
        setTimeout(function () {
          cell.classList.remove("is-head");
        }, 300 + (i + 1) * step);
      }
    });
  }

  function init() {
    document.querySelectorAll(".jw-tape").forEach(render);
  }

  if (typeof document$ !== "undefined") {
    document$.subscribe(init);
  } else {
    document.addEventListener("DOMContentLoaded", init);
  }
})();
