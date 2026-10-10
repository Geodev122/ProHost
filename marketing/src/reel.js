// Second-person Reel template ("You're a … / problem / Now you can … / Download ProHost, 50% off").
// window.P = { mode:"renter"|"host", bg, kicker, role, problem:[lines], lead, badgeIcon,
//   listing:{title,type,place,price,unit,icon},
//   renter: optTitle, chips:[{label,on}], stepper|null, summary:{label,total,t}, sent, after
//   host:   rentTitle, chips:[{label,on}], request:{who,lines,amount}, accept, earn:{total,note} }
const p = window.P;
Object.assign(ICONS, {
  hotel: "M7 13c1.66 0 3-1.34 3-3S8.66 7 7 7s-3 1.34-3 3 1.34 3 3 3zm12-6h-8v7H3V5H1v15h2v-3h18v3h2v-9c0-2.21-1.79-4-4-4z",
  hospital: "M19 3H5c-1.1 0-1.99.9-1.99 2L3 19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-1 11h-4v4h-4v-4H6v-4h4V6h4v4h4v4z",
  school: "M5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82zM12 3L1 9l11 6 9-4.91V17h2V9L12 3z",
  yoga: "M12 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm9 14v-2c-2.24 0-4.16-.96-5.6-2.68l-1.34-1.6c-.38-.46-.94-.72-1.53-.72h-1.05c-.59 0-1.15.26-1.53.72l-1.34 1.6C7.16 13.04 5.24 14 3 14v2c2.77 0 5.19-1.17 7-3.25V15l-3.88 1.55c-.67.27-1.12.93-1.12 1.66C5 19.2 5.8 20 6.79 20H9v-.5c0-1.38 1.12-2.5 2.5-2.5h3c.28 0 .5.22.5.5s-.22.5-.5.5h-3c-.83 0-1.5.67-1.5 1.5v.5h7.21c.99 0 1.79-.8 1.79-1.79 0-.73-.45-1.39-1.12-1.66L14 15v-2.25c1.81 2.08 4.23 3.25 7 3.25z",
  bell: "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
  trend: "M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z",
});
const host = p.mode === "host";

const listingCard = (top) => `
  <div class="card abs" id="lc" style="left:90px;right:90px;top:${top}px;padding:26px;display:flex;gap:26px;align-items:center">
    <div style="width:150px;height:150px;border-radius:24px;flex:none;background:url('${p.bg}') center/cover,linear-gradient(135deg,#dfe8f3,#b9cde3)"></div>
    <div style="flex:1;min-width:0">
      <div style="display:flex;justify-content:space-between;align-items:center"><span style="display:flex;gap:10px"><span class="tag">${p.listing.type}</span>${host ? `<span class="tag" style="background:var(--green)">Live</span>` : ""}</span>
        <span style="font-weight:800;font-size:44px;color:var(--steel)">${p.listing.price}<span style="font-size:26px;color:var(--muted);font-weight:600">${p.listing.unit}</span></span></div>
      <div style="font-weight:800;font-size:38px;margin-top:12px;letter-spacing:-.5px">${p.listing.title}</div>
      <div style="font-weight:600;font-size:26px;color:var(--muted);margin-top:6px;display:flex;align-items:center;gap:6px">${icon("pin", 28)} ${p.listing.place}
        ${host ? "" : `<span style="margin-left:14px;color:var(--green);display:flex;align-items:center;gap:6px">${icon("verified", 26)} Verified host</span>`}</div>
    </div>
  </div>`;

const renterS2 = () => `
  ${listingCard(255)}
  <div class="card abs" id="oc" style="left:90px;right:90px;top:500px;padding:34px 40px">
    <div class="small" style="color:var(--muted);letter-spacing:2px;font-size:24px">${p.optTitle}</div>
    <div style="display:flex;flex-wrap:wrap;gap:16px;margin-top:20px">${p.chips.map((c) => `<span class="chip ch">${c.label}</span>`).join("")}</div>
    ${p.stepper ? `<div style="display:flex;align-items:center;justify-content:space-between;margin-top:26px;padding:20px 26px;border-radius:24px;background:var(--steel-c)">
      <div style="font-weight:700;font-size:32px;color:var(--steel);display:flex;align-items:center;gap:12px">${icon("groups", 40)} ${p.stepper.label}</div>
      <div style="display:flex;align-items:center;gap:22px"><span class="stb">−</span><span id="stv" style="font-weight:800;font-size:48px;min-width:70px;text-align:center">${p.stepper.from}</span><span class="stb" id="plus">+</span></div></div>` : ""}
    <div style="height:2px;background:#e8edf3;margin:28px 0 22px"></div>
    <div style="display:flex;justify-content:space-between;align-items:center">
      <div style="font-weight:600;font-size:30px;color:var(--muted)">${p.summary.label}</div>
      <div style="font-weight:800;font-size:56px" id="tot">$0</div>
    </div>
  </div>
  <div class="abs" id="btn" style="left:90px;right:90px;top:${p.stepper ? 1050 : 950}px;height:116px;border-radius:30px;background:var(--orange);color:#fff;display:flex;align-items:center;justify-content:center;gap:18px;font-weight:800;font-size:40px;box-shadow:0 20px 40px rgba(242,95,76,.35)">
    <span id="bt1">Send request</span><span id="bt2" style="display:none;align-items:center;gap:14px">${icon("check", 48)} Request sent</span></div>
  <div class="abs" id="wa" style="left:0;right:0;top:${p.stepper ? 1195 : 1095}px;text-align:center;font-weight:700;font-size:32px;color:#fff;display:flex;align-items:center;justify-content:center;gap:12px">
    <span style="color:#5fd38d;display:flex">${icon("chat", 38)}</span> ${p.after}</div>`;

const hostS2 = () => `
  ${listingCard(215)}
  <div class="card abs" id="oc" style="left:90px;right:90px;top:445px;padding:30px 40px">
    <div class="small" style="color:var(--muted);letter-spacing:2px;font-size:24px">${p.rentTitle}</div>
    <div style="display:flex;flex-wrap:wrap;gap:16px;margin-top:18px">${p.chips.map((c) => `<span class="chip ch">${c.label}</span>`).join("")}</div>
  </div>
  <div class="card abs" id="rq" style="left:90px;right:90px;top:680px;padding:30px 40px;border:3px solid var(--orange-c)">
    <div style="display:flex;align-items:center;gap:16px">
      <span style="width:62px;height:62px;border-radius:18px;background:var(--orange-c);color:var(--orange);display:grid;place-items:center">${icon("bell", 38)}</span>
      <div style="flex:1"><div style="font-weight:800;font-size:34px">New request</div><div style="font-weight:600;font-size:26px;color:var(--muted)">${p.request.who}</div></div>
      <div style="font-weight:800;font-size:46px">${p.request.amount}</div>
    </div>
    <div style="font-weight:600;font-size:28px;margin:16px 0 22px;line-height:1.4">${p.request.lines.join("<br>")}</div>
    <div style="display:flex;gap:18px">
      <div style="flex:1;height:88px;border-radius:24px;background:#eef2f7;color:var(--muted);display:grid;place-items:center;font-weight:800;font-size:32px">Decline</div>
      <div id="acc" style="flex:1.4;height:88px;border-radius:24px;background:var(--steel);color:#fff;display:flex;align-items:center;justify-content:center;gap:12px;font-weight:800;font-size:32px">
        <span id="a1">Accept</span><span id="a2" style="display:none;align-items:center;gap:10px">${icon("check", 40)} Accepted</span></div>
    </div>
  </div>
  <div class="card abs" id="er" style="left:90px;right:90px;top:1070px;padding:28px 40px;display:flex;align-items:center;gap:24px">
    <span style="width:84px;height:84px;border-radius:24px;background:var(--green-c);color:#2f8f57;display:grid;place-items:center">${icon("trend", 52)}</span>
    <div style="flex:1"><div style="font-weight:700;font-size:28px;color:var(--muted)">This month</div><div style="font-weight:600;font-size:24px;color:var(--muted)">${p.earn.note}</div></div>
    <div id="ev" style="font-weight:800;font-size:64px;color:#2f8f57">$0</div>
  </div>`;

document.body.innerHTML = `
<div class="abs" id="bgw" style="inset:0;overflow:hidden"><div id="bgi" class="abs" style="inset:-40px;background:url('${p.bg}') center/cover,linear-gradient(160deg,#33608f,#1c2a40)"></div></div>
<div class="abs" id="sh1" style="inset:0;background:linear-gradient(180deg,rgba(14,22,36,.35) 0%,rgba(14,22,36,.25) 30%,rgba(14,22,36,.82) 62%,rgba(14,22,36,.92) 100%)"></div>
<div class="abs" id="sh2" style="inset:0;background:rgba(14,22,36,.62)"></div>

<div id="stage"><div class="scene" id="S1">
  <div class="abs" id="bd" style="left:90px;top:520px;width:120px;height:120px;border-radius:34px;display:grid;place-items:center;color:#fff;background:var(--orange);box-shadow:0 20px 40px rgba(242,95,76,.4)">${icon(p.badgeIcon, 70)}</div>
  <div class="small abs" id="k" style="left:94px;top:690px;color:#ffd2ca;letter-spacing:5px;font-size:28px">${p.kicker}</div>
  <div class="h1 abs" id="rl" style="left:90px;right:60px;top:735px;color:#fff;font-size:${p.roleSize || 104}px">${p.role}</div>
  <div class="abs" id="pb" style="left:90px;right:90px;top:${p.problemTop || 880}px;color:#fff;font-weight:700;font-size:56px;line-height:1.16;letter-spacing:-.8px">
    ${p.problem.map((l) => `<span class="pl" style="display:block">${l}</span>`).join("")}</div>
</div>

<div class="scene" id="S2">
  <div class="h2 abs" id="w" style="left:90px;right:60px;top:${host ? 95 : 120}px;font-size:58px;color:#fff">${p.lead}</div>
  ${host ? hostS2() : renterS2()}
  <div class="abs" id="tap" style="width:84px;height:84px;margin:-42px 0 0 -42px;border-radius:50%;background:rgba(255,255,255,.28);border:4px solid rgba(255,255,255,.85)"></div>
</div>

<div class="scene" id="S3">
  <div class="h1 abs" id="dl" style="left:0;right:0;top:40px;text-align:center;color:#fff;font-size:92px">Download <span class="orange">ProHost</span></div>
  <div class="abs" id="ob" style="left:390px;top:200px;width:300px;height:300px;border-radius:50%;display:grid;place-items:center;text-align:center;color:#fff;
    background:radial-gradient(circle at 35% 30%,#ff8a75,var(--orange) 55%,var(--orange-d));box-shadow:0 30px 70px rgba(242,95,76,.5)">
    <div><div style="font-weight:800;font-size:104px;line-height:.9;letter-spacing:-4px">50%</div><div style="font-weight:800;font-size:40px;letter-spacing:2px">OFF</div></div></div>
  <div class="h3 abs" id="oS" style="left:0;right:0;top:545px;text-align:center;font-size:48px;color:#fff">your first <span class="orange">ProHost Premium</span><br>subscription</div>
  <div class="abs" id="my" style="left:0;right:0;top:690px;text-align:center"><span class="pill" style="font-size:30px;padding:14px 30px">Monthly or yearly</span></div>
  <div class="brandbar" id="bb" style="top:820px"></div>
  <div class="abs" id="gp" style="left:0;right:0;top:955px;text-align:center"><span class="gp"></span></div>
  <div class="abs small" id="ff" style="left:0;right:0;top:1110px;text-align:center;color:rgba(255,255,255,.75);font-weight:500;font-size:23px;line-height:1.5">
    ${host ? "New subscribers · billed securely by Google Play<br>Prices and earnings shown are examples"
           : "Booking is free · Premium is for listing your space<br>Prices shown are examples"}</div>
</div></div>`;
document.body.className = "";
document.body.style.background = "#1c2a40";
const st = document.createElement("style");
st.textContent = `.stb{display:grid;place-items:center;width:64px;height:64px;border-radius:50%;background:#fff;color:var(--steel);font-weight:800;font-size:40px;box-shadow:0 6px 14px rgba(16,32,60,.15)}
#S1 .h1, #pb { text-shadow: 0 6px 30px rgba(0,0,0,.35) }`;
document.head.appendChild(st);
fillParts();

// ---- timeline ----
A("#sh2", 0, 0.01, () => ({ opacity: 0 }));
A("#sh2", 4.7, 0.6, (e) => ({ opacity: e }));
A("#sh1", 4.7, 0.6, (e) => ({ opacity: 1 - e }));
A("#bd", 0.2, 0.7, (e) => ({ opacity: Math.min(1, e * 2), transform: `scale(${lerp(.4, 1, e)}) rotate(${lerp(-14, 0, e)}deg)` }), "back");
fadeIn("#k", 0.45, 0.5);
fadeUp("#rl", 0.55, 0.7, 50);
A(".pl", 1.4, 0.6, (e) => ({ opacity: e, transform: `translateY(${lerp(30, 0, e)}px)` }), "out", 0.4);
out("#S1", 4.6, 0.45, -60);

fadeIn("#S2", 5.0, 0.01);
fadeUp("#w", 5.05, 0.6);
A("#lc", 5.3, 0.7, (e) => ({ opacity: e, transform: `translateX(${lerp(160, 0, e)}px)` }));
fadeUp("#oc", 5.7, 0.7, 60);
pop(".ch", 5.95, 0.45, 0.08);
if (host) {
  A("#rq", 7.7, 0.7, (e) => ({ opacity: Math.min(1, e * 1.6), transform: `translateY(${lerp(-90, 0, e)}px) scale(${lerp(.92, 1, e)})` }), "back");
  fadeUp("#er", p.accept + 0.45, 0.6, 50);
} else {
  fadeUp("#btn", 6.3, 0.6, 60);
  A("#wa", 0, 0.01, () => ({ opacity: 0 }));
  fadeUp("#wa", p.sent + 0.4, 0.5, 20);
}
out("#S2", 11.2, 0.45, -60);

fadeIn("#S3", 11.6, 0.01);
fadeUp("#dl", 11.65, 0.6);
A("#ob", 11.9, 0.8, (e) => ({ opacity: Math.min(1, e * 2), transform: `scale(${lerp(.3, 1, e)}) rotate(${lerp(-25, 0, e)}deg)` }), "back");
fadeUp("#oS", 12.3, 0.6);
pop("#my", 12.6, 0.5);
A("#bb", 12.8, 0.6, (e) => ({ opacity: e, transform: `translateX(-50%) translateY(${lerp(40, 0, e)}px)` }));
fadeUp("#gp", 13.05, 0.6);
fadeIn("#ff", 13.4, 0.5);

// tap targets (positions in unscaled stage coordinates)
const stage = document.getElementById("stage");
const sr = stage.getBoundingClientRect(), sc = sr.width / 1080;
const center = (el) => { const r = el.getBoundingClientRect(); return [(r.left - sr.left + r.width / 2) / sc, (r.top - sr.top + r.height / 2) / sc]; };
const chipEls = [...document.querySelectorAll(".ch")];
const taps = [];
p.chips.forEach((c, i) => { if (c.on != null) taps.push({ t: c.on, at: center(chipEls[i]) }); });
if (!host && p.stepper) taps.push({ t: p.stepper.t0, at: center(document.getElementById("plus")), hold: p.stepper.t1 - p.stepper.t0 });
const finalT = host ? p.accept : p.sent;
taps.push({ t: finalT, at: center(document.getElementById(host ? "acc" : "btn")) });
taps.sort((a, b) => a.t - b.t);
const money = (v, plus) => (plus ? "+$" : "$") + Math.round(v).toLocaleString("en-US");

L((t) => {
  // slow Ken Burns on the photo; blurred behind the UI scenes
  const z = 1.12 - 0.08 * Math.min(1, t / 15);
  const bl = t < 4.7 ? 0 : Math.min(16, (t - 4.7) / 0.6 * 16);
  const bgi = document.getElementById("bgi");
  bgi.style.transform = `scale(${z}) translate(${Math.sin(t * .2) * 12}px, ${-t * 2}px)`;
  bgi.style.filter = `blur(${bl}px) saturate(${t < 4.7 ? 1.05 : 0.9})`;
  p.chips.forEach((c, i) => chipEls[i].classList.toggle("on", c.on != null && t >= c.on + 0.12));
  if (host) {
    const ok = t >= p.accept + 0.15;
    document.getElementById("a1").style.display = ok ? "none" : "inline";
    document.getElementById("a2").style.display = ok ? "inline-flex" : "none";
    const b = document.getElementById("acc");
    b.style.background = ok ? "var(--green)" : "var(--steel)";
    b.style.transform = t > p.accept && t < p.accept + 0.3 ? `scale(${1 - Math.sin((t - p.accept) / 0.3 * Math.PI) * .05})` : "";
    const ee = Math.min(1, Math.max(0, (t - (p.accept + 0.7)) / 1.1));
    document.getElementById("ev").textContent = money(p.earn.total * E.out(ee), true);
  } else {
    if (p.stepper) {
      const s = p.stepper, e = Math.min(1, Math.max(0, (t - s.t0) / (s.t1 - s.t0)));
      document.getElementById("stv").textContent = Math.round(lerp(s.from, s.to, E.inOut(e)));
    }
    const te = Math.min(1, Math.max(0, (t - p.summary.t) / 0.9));
    document.getElementById("tot").textContent = money(p.summary.total * E.out(te));
    const sent = t >= p.sent + 0.15;
    document.getElementById("bt1").style.display = sent ? "none" : "inline";
    document.getElementById("bt2").style.display = sent ? "inline-flex" : "none";
    const b = document.getElementById("btn");
    b.style.background = sent ? "var(--green)" : "var(--orange)";
    b.style.transform = t > p.sent && t < p.sent + 0.3 ? `scale(${1 - Math.sin((t - p.sent) / 0.3 * Math.PI) * .05})` : "";
  }
  if (t > 12.8) document.getElementById("ob").style.transform = `scale(${1 + Math.sin((t - 12.8) * 3) * .025})`;
  const tap = document.getElementById("tap");
  const active = t > 6.2 && t < finalT + 0.6;
  tap.style.opacity = active ? 1 : 0;
  if (active) {
    let prev = taps[0];
    for (const k of taps) { if (k.t <= t) prev = k; }
    const next = taps.find((k) => k.t > t) || prev;
    let x = prev.at[0], y = prev.at[1];
    if (next !== prev) {
      const e = E.inOut(Math.min(1, Math.max(0, (t - (next.t - 0.45)) / 0.45)));
      x = lerp(prev.at[0], next.at[0], e); y = lerp(prev.at[1], next.at[1], e);
    } else if (t < taps[0].t) { x = taps[0].at[0]; y = taps[0].at[1] + 160 * (1 - Math.min(1, (t - 6.2) / 0.4)); }
    const since = t - prev.t;
    const pulse = since >= 0 && since < 0.3 ? Math.sin(since / 0.3 * Math.PI) : (prev.hold && since < prev.hold ? 0.6 : 0);
    tap.style.left = x + "px"; tap.style.top = y + "px";
    tap.style.transform = `scale(${1 - pulse * .3})`;
  }
});
