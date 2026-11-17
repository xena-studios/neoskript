// The landing page's two consoles (index.astro): both servers load and reload
// the benchmark's scripts at the speed each one measured, each clock stopping
// at its own time. The page is drawn finished; the replay runs once when the
// consoles come into view, and again from the Replay button. With reduced
// motion it never runs and the button stays hidden. The numbers' bars draw out
// when they come into view.
const reduce = matchMedia("(prefers-reduced-motion: reduce)");
const figure = document.querySelector<HTMLElement>("[data-replay]");

if (figure && !reduce.matches) {
  const consoles = [...figure.querySelectorAll<HTMLElement>(".console")].map((el) => ({
    end: Number(el.dataset.end),
    clock: el.querySelector<HTMLElement>("[data-clock]")!,
    lines: [...el.querySelectorAll<HTMLElement>("[data-at]")].map((line) => ({ el: line, at: Number(line.dataset.at) })),
  }));
  const last = Math.max(...consoles.map((c) => c.end));
  const button = figure.querySelector<HTMLButtonElement>("[data-replay-button]")!;
  let frame = 0;

  const show = (s: number) => `${s.toFixed(2)} s`;
  const play = () => {
    cancelAnimationFrame(frame);
    for (const c of consoles) {
      c.clock.textContent = show(0);
      for (const line of c.lines) {
        line.el.classList.remove("arrived");
        line.el.classList.add("pending");
      }
    }
    const start = performance.now();
    const tick = (now: number) => {
      const t = (now - start) / 1000;
      for (const c of consoles) {
        c.clock.textContent = show(Math.min(t, c.end));
        for (const line of c.lines) {
          if (line.at <= t && line.el.classList.contains("pending")) {
            line.el.classList.remove("pending");
            line.el.classList.add("arrived");
          }
        }
      }
      if (t < last) frame = requestAnimationFrame(tick);
    };
    frame = requestAnimationFrame(tick);
  };

  button.hidden = false;
  button.addEventListener("click", play);
  const seen = new IntersectionObserver((entries) => {
    if (entries.some((e) => e.isIntersecting)) {
      seen.disconnect();
      play();
    }
  }, { threshold: 0.4 });
  seen.observe(figure);
}

const numbers = document.querySelector<HTMLElement>(".numbers");
if (numbers && !reduce.matches) {
  const seen = new IntersectionObserver((entries) => {
    if (entries.some((e) => e.isIntersecting)) {
      seen.disconnect();
      numbers.classList.add("drawing");
    }
  }, { threshold: 0.25 });
  seen.observe(numbers);
}
