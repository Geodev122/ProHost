// Persona post template. window.P = { kicker, name, role, city, quote, look:{skin,hair,hairStyle,top,bg},
//   listing:{title, type, place, price, unit}, optTitle, chips:[{label,on}], stepper:{label,from,to,t0,t1}|null,
//   summary:{label, total, t}, sent:t, outcome, outcome2 }
const p = window.P;

function avatarSVG(l) {
  const hair = {
    long: `<path d="M50 120c0-48 26-78 70-78s70 30 70 78v80c-14 6-24-4-26-20 0 0-4-60-44-62-40 2-44 62-44 62-2 16-12 26-26 20z" fill="${l.hair}"/>`,
    bun: `<circle cx="120" cy="38" r="22" fill="${l.hair}"/><path d="M62 112c0-42 24-66 58-66s58 24 58 66c-8-26-30-40-58-40s-50 14-58 40z" fill="${l.hair}"/>`,
    short: `<path d="M64 108c0-40 24-62 56-62s56 22 56 62c-6-18-22-34-56-34s-50 16-56 34z" fill="${l.hair}"/>`,
    wavy: `<path d="M56 118c-4-46 22-74 64-74s70 28 64 74c-8 30-10 52-22 66-2-30-4-58-10-74-10-14-58-14-70 4-6 18-6 44-8 70-14-16-16-40-18-66z" fill="${l.hair}"/>`,
  }[l.hairStyle];
  const beard = l.beard ? `<path d="M86 132c4 30 18 40 34 40s30-10 34-40c-8 10-20 14-34 14s-26-4-34-14z" fill="${l.hair}" opacity=".9"/>` : "";
  return `<svg viewBox="0 0 240 240" width="100%" height="100%">
    <defs><linearGradient id="bg" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="${l.bg[0]}"/><stop offset="1" stop-color="${l.bg[1]}"/></linearGradient>
    <clipPath id="cc"><circle cx="120" cy="120" r="120"/></clipPath></defs>
    <circle cx="120" cy="120" r="120" fill="url(#bg)"/>
    <g clip-path="url(#cc)">
      ${l.hairStyle === "long" || l.hairStyle === "wavy" ? hair : ""}
      <path d="M30 250c4-50 40-76 90-76s86 26 90 76z" fill="${l.top}"/>
      <path d="M102 150h36v30c-6 8-30 8-36 0z" fill="${l.skin}"/>
      <ellipse cx="120" cy="112" rx="44" ry="52" fill="${l.skin}"/>
      ${l.hairStyle === "long" || l.hairStyle === "wavy" ? "" : hair}
      ${beard}
      <circle cx="104" cy="114" r="4.5" fill="#2a2320"/><circle cx="136" cy="114" r="4.5" fill="#2a2320"/>
      <path d="M108 138c7 6 17 6 24 0" stroke="#7a3b2e" stroke-width="4" fill="none" stroke-linecap="round"/>
      ${l.glasses ? `<g fill="none" stroke="#2a2320" stroke-width="3.5"><circle cx="104" cy="114" r="13"/><circle cx="136" cy="114" r="13"/><path d="M117 114h6"/></g>` : ""}
    </g></svg>`;
}

document.body.innerHTML = `
<div class="glow" id="g1" style="width:640px;height:640px;background:#cfe0f3;left:-240px;top:120px;opacity:.9"></div>
<div class="glow" id="g2" style="width:560px;height:560px;background:#ffd9d2;right:-220px;top:1250px;opacity:.85"></div>

<div id="stage"><div class="scene" id="S1">
  <div class="small abs" id="k" style="left:0;right:0;top:150px;text-align:center;color:var(--steel);letter-spacing:4px">${p.kicker}</div>
  <div class="abs" id="av" style="left:390px;top:225px;width:300px;height:300px;filter:drop-shadow(0 24px 40px rgba(16,32,60,.25))">${avatarSVG(p.look)}</div>
  <div class="abs" id="nm" style="left:0;right:0;top:560px;text-align:center">
    <div class="h3" style="font-size:56px">${p.role}</div>
    <div class="body" style="color:var(--muted);font-size:32px;margin-top:6px">in ${p.city}</div>
  </div>
  <div class="card abs" id="q" style="left:90px;right:90px;top:790px;padding:56px 60px 60px;border-radius:44px">
    <div style="position:absolute;left:44px;top:-46px;font-size:170px;line-height:1;color:var(--orange);font-weight:800">“</div>
    <div class="h2" style="font-size:58px;line-height:1.14">${p.quote.map((l) => `<span class="ql" style="display:block">${l}</span>`).join("")}</div>
  </div>
</div>

<div class="scene" id="S2">
  <div class="h2 abs" id="w" style="left:90px;top:160px;font-size:60px">With <span class="steel">ProHost</span>${p.withLine ? `, ${p.withLine}` : ":"}</div>
  <div class="card abs" id="lc" style="left:90px;right:90px;top:285px;padding:28px;display:flex;gap:28px;align-items:center">
    <div style="width:170px;height:170px;border-radius:24px;flex:none;background:linear-gradient(135deg,#dfe8f3,#b9cde3);display:grid;place-items:center;color:var(--steel)">${icon(p.listing.icon || "map", 84)}</div>
    <div style="flex:1;min-width:0">
      <div style="display:flex;justify-content:space-between;align-items:center"><span class="tag">${p.listing.type}</span>
        <span style="font-weight:800;font-size:46px;color:var(--steel)">${p.listing.price}<span style="font-size:26px;color:var(--muted);font-weight:600">${p.listing.unit}</span></span></div>
      <div style="font-weight:800;font-size:40px;margin-top:12px;letter-spacing:-.5px">${p.listing.title}</div>
      <div style="font-weight:600;font-size:26px;color:var(--muted);margin-top:6px;display:flex;align-items:center;gap:6px">${icon("pin", 28)} ${p.listing.place} <span style="margin-left:14px;color:var(--green);display:flex;align-items:center;gap:6px">${icon("verified", 26)} Verified host</span></div>
    </div>
  </div>
  <div class="card abs" id="oc" style="left:90px;right:90px;top:560px;padding:36px 40px">
    <div class="small" style="color:var(--muted);letter-spacing:2px;font-size:24px">${p.optTitle}</div>
    <div id="chips" style="display:flex;flex-wrap:wrap;gap:16px;margin-top:20px">
      ${p.chips.map((c) => `<span class="chip ch">${c.label}</span>`).join("")}
    </div>
    ${p.stepper ? `<div style="display:flex;align-items:center;justify-content:space-between;margin-top:28px;padding:22px 26px;border-radius:24px;background:var(--steel-c)">
      <div style="font-weight:700;font-size:32px;color:var(--steel);display:flex;align-items:center;gap:12px">${icon("groups", 40)} ${p.stepper.label}</div>
      <div style="display:flex;align-items:center;gap:22px"><span class="stb">−</span><span id="stv" style="font-weight:800;font-size:48px;min-width:70px;text-align:center">${p.stepper.from}</span><span class="stb" id="plus">+</span></div></div>` : ""}
    <div style="height:2px;background:#e8edf3;margin:30px 0 24px"></div>
    <div style="display:flex;justify-content:space-between;align-items:center">
      <div style="font-weight:600;font-size:30px;color:var(--muted)" id="sl">${p.summary.label}</div>
      <div style="font-weight:800;font-size:56px" id="tot">$0</div>
    </div>
  </div>
  <div class="abs" id="btn" style="left:90px;right:90px;top:${p.stepper ? 1115 : 1020}px;height:116px;border-radius:30px;background:var(--orange);color:#fff;display:flex;align-items:center;justify-content:center;gap:18px;font-weight:800;font-size:40px;box-shadow:0 20px 40px rgba(242,95,76,.35)">
    <span id="bt1">Send request</span><span id="bt2" style="display:none;align-items:center;gap:14px">${icon("check", 48)} Request sent</span></div>
  <div class="abs" id="wa" style="left:0;right:0;top:${p.stepper ? 1255 : 1165}px;text-align:center;font-weight:700;font-size:30px;color:#1f9d55;display:flex;align-items:center;justify-content:center;gap:12px">${icon("chat", 36)} The host replies on WhatsApp</div>
  <div class="abs" id="tap" style="width:84px;height:84px;margin:-42px 0 0 -42px;border-radius:50%;background:rgba(43,90,140,.25);border:4px solid rgba(43,90,140,.65)"></div>
</div>

<div class="scene" id="S3">
  <div class="abs" id="av2" style="left:430px;top:170px;width:220px;height:220px">${avatarSVG(p.look)}</div>
  <div class="h1 abs" id="oT" style="left:80px;right:80px;top:440px;text-align:center;font-size:84px">${p.outcome}</div>
  <div class="body abs" id="oS" style="left:120px;right:120px;top:${p.outcomeTop || 720}px;text-align:center;color:var(--muted)">${p.outcome2}</div>
  <div class="brandbar" id="bb" style="top:930px"></div>
  <div class="abs" id="gp" style="left:0;right:0;top:1070px;text-align:center"><span class="gp"></span></div>
  <div class="abs small" id="ff" style="left:0;right:0;top:1235px;text-align:center;color:var(--muted);font-weight:500;font-size:24px">Free for professionals · Prices shown are examples</div>
</div></div>`;
document.body.className = "light dots";
const st = document.createElement("style");
st.textContent = `.stb{display:grid;place-items:center;width:64px;height:64px;border-radius:50%;background:#fff;color:var(--steel);font-weight:800;font-size:40px;box-shadow:0 6px 14px rgba(16,32,60,.15)}`;
document.head.appendChild(st);
fillParts();

// ---- timeline ----
fadeIn("#k", 0.15, 0.5);
A("#av", 0.25, 0.9, (e) => ({ opacity: Math.min(1, e * 2), transform: `scale(${lerp(.4, 1, e)})` }), "back");
fadeUp("#nm", 0.8, 0.6, 40);
fadeUp("#q", 1.4, 0.7, 80);
A(".ql", 1.7, 0.6, (e) => ({ opacity: e, transform: `translateY(${lerp(24, 0, e)}px)` }), "out", 0.45);
out("#S1", 4.6, 0.45, -60);

fadeIn("#S2", 5.0, 0.01);
fadeUp("#w", 5.05, 0.6);
A("#lc", 5.3, 0.7, (e) => ({ opacity: e, transform: `translateX(${lerp(160, 0, e)}px)` }));
fadeUp("#oc", 5.7, 0.7, 70);
pop(".ch", 6.0, 0.45, 0.08);
fadeUp("#btn", 6.3, 0.6, 60);
A("#wa", 0, 0.01, () => ({ opacity: 0 }));
fadeUp("#wa", p.sent + 0.4, 0.5, 20);
out("#S2", 11.2, 0.45, -60);

fadeIn("#S3", 11.6, 0.01);
A("#av2", 11.65, 0.7, (e) => ({ opacity: Math.min(1, e * 2), transform: `scale(${lerp(.5, 1, e)})` }), "back");
fadeUp("#oT", 11.85, 0.7);
fadeUp("#oS", 12.25, 0.6);
A("#bb", 12.6, 0.6, (e) => ({ opacity: e, transform: `translateX(-50%) translateY(${lerp(40, 0, e)}px)` }));
fadeUp("#gp", 12.9, 0.6);
fadeIn("#ff", 13.3, 0.5);

// tap targets: chips that switch on, stepper "+", then the button
const chipEls = [...document.querySelectorAll(".ch")];
const stageTop = document.getElementById("stage").getBoundingClientRect().top;
const center = (el) => { const r = el.getBoundingClientRect(); return [r.left + r.width / 2, r.top - stageTop + r.height / 2]; };
const taps = [];
p.chips.forEach((c, i) => { if (c.on != null) taps.push({ t: c.on, at: center(chipEls[i]), el: chipEls[i] }); });
if (p.stepper) taps.push({ t: p.stepper.t0, at: center(document.getElementById("plus")), hold: p.stepper.t1 - p.stepper.t0 });
taps.push({ t: p.sent, at: center(document.getElementById("btn")) });
taps.sort((a, b) => a.t - b.t);

const money = (v) => "$" + Math.round(v).toLocaleString("en-US");
L((t) => {
  document.getElementById("g1").style.transform = `translate(${Math.sin(t * .5) * 50}px, ${Math.cos(t * .4) * 40}px)`;
  document.getElementById("g2").style.transform = `translate(${Math.cos(t * .45) * 50}px, ${Math.sin(t * .6) * 40}px)`;
  // chips on
  p.chips.forEach((c, i) => chipEls[i].classList.toggle("on", c.on != null && t >= c.on + 0.12));
  // stepper count
  if (p.stepper) {
    const s = p.stepper, e = Math.min(1, Math.max(0, (t - s.t0) / (s.t1 - s.t0)));
    document.getElementById("stv").textContent = Math.round(lerp(s.from, s.to, E.inOut(e)));
  }
  // total count-up
  const te = Math.min(1, Math.max(0, (t - p.summary.t) / 0.9));
  document.getElementById("tot").textContent = money(p.summary.total * E.out(te));
  // button state
  const sent = t >= p.sent + 0.15;
  document.getElementById("bt1").style.display = sent ? "none" : "inline";
  document.getElementById("bt2").style.display = sent ? "inline-flex" : "none";
  const b = document.getElementById("btn");
  b.style.background = sent ? "var(--green)" : "var(--orange)";
  if (t > p.sent && t < p.sent + 0.3) b.style.transform = `scale(${1 - Math.sin((t - p.sent) / 0.3 * Math.PI) * .05})`;
  // tap cursor: glide between targets, pulse on each tap
  const tap = document.getElementById("tap");
  const active = t > 6.8 && t < p.sent + 0.6;
  tap.style.opacity = active ? 1 : 0;
  if (active) {
    let prev = taps[0], next = taps[0];
    for (const k of taps) { if (k.t <= t) prev = k; }
    next = taps.find((k) => k.t > t) || prev;
    let x = prev.at[0], y = prev.at[1];
    if (next !== prev) {
      const e = E.inOut(Math.min(1, Math.max(0, (t - (next.t - 0.45)) / 0.45)));
      x = lerp(prev.at[0], next.at[0], e); y = lerp(prev.at[1], next.at[1], e);
    } else if (t < taps[0].t) { x = taps[0].at[0]; y = taps[0].at[1] + 160 * (1 - Math.min(1, (t - 6.8) / 0.4)); }
    const since = t - prev.t;
    const pulse = since >= 0 && since < 0.3 ? Math.sin(since / 0.3 * Math.PI) : (prev.hold && since < prev.hold ? 0.6 : 0);
    tap.style.left = x + "px"; tap.style.top = y + "px";
    tap.style.transform = `scale(${1 - pulse * .3})`;
  }
});
