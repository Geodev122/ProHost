// Deterministic timeline: render(t) sets every animated style for time t (seconds),
// so each video frame is reproducible (no real-time CSS animations).
const E = {
  lin: (t) => t,
  out: (t) => 1 - Math.pow(1 - t, 3),
  out5: (t) => 1 - Math.pow(1 - t, 5),
  inOut: (t) => (t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2),
  back: (t) => { const c1 = 1.6, c3 = c1 + 1; return 1 + c3 * Math.pow(t - 1, 3) + c1 * Math.pow(t - 1, 2); },
  in: (t) => t * t * t,
};
const tracks = [];
const loops = [];
// A(selector, start, dur, fn(e, i, p), ease, stagger): fn returns a style object for eased progress e.
function A(sel, start, dur, fn, ease = "out", stagger = 0) {
  document.querySelectorAll(sel).forEach((el, i) => tracks.push({ el, start: start + i * stagger, dur, fn, ease, i }));
}
function L(fn) { loops.push(fn); }
const lerp = (a, b, e) => a + (b - a) * e;
// Common entrances / exits
const fadeUp = (sel, s, d = 0.7, dist = 60, st = 0, ease = "out") =>
  A(sel, s, d, (e) => ({ opacity: e, transform: `translateY(${lerp(dist, 0, e)}px)` }), ease, st);
const fadeIn = (sel, s, d = 0.6, st = 0) => A(sel, s, d, (e) => ({ opacity: e }), "out", st);
const pop = (sel, s, d = 0.6, st = 0) =>
  A(sel, s, d, (e) => ({ opacity: Math.min(1, e * 2), transform: `scale(${lerp(0.6, 1, e)})` }), "back", st);
const slideIn = (sel, s, d = 0.7, dx = -120, st = 0) =>
  A(sel, s, d, (e) => ({ opacity: e, transform: `translateX(${lerp(dx, 0, e)}px)` }), "out", st);
const out = (sel, s, d = 0.45, dy = -40, st = 0) =>
  A(sel, s, d, (e) => ({ opacity: 1 - e, transform: `translateY(${lerp(0, dy, e)}px)` }), "in", st);
const fadeOut = (sel, s, d = 0.45) => A(sel, s, d, (e) => ({ opacity: 1 - e }), "in");

function render(t) {
  // First track of each element always applies (its pre-start state); later ones only once started.
  const seen = new Set();
  const sorted = tracks.slice().sort((a, b) => a.start - b.start);
  for (const tr of sorted) {
    const first = !seen.has(tr.el);
    seen.add(tr.el);
    if (!first && t < tr.start) continue;
    const p = Math.min(1, Math.max(0, (t - tr.start) / tr.dur));
    Object.assign(tr.el.style, tr.fn(E[tr.ease](p), tr.i, p));
  }
  for (const f of loops) f(t);
}
window.render = render;
