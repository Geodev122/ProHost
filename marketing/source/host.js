// Host (space owner) post template. window.P = { kicker, role, city, badgeIcon, badgeBg:[c1,c2], quote:[lines],
//   listing:{title,type,place,price,unit,icon}, rentTitle, chips:[{label,on}], request:{who,lines:[..],amount},
//   accept:t, earn:{total, note}, outcome }
const p = window.P;
Object.assign(ICONS, {
  hotel: "M7 13c1.66 0 3-1.34 3-3S8.66 7 7 7s-3 1.34-3 3 1.34 3 3 3zm12-6h-8v7H3V5H1v15h2v-3h18v3h2v-9c0-2.21-1.79-4-4-4z",
  hospital: "M19 3H5c-1.1 0-1.99.9-1.99 2L3 19c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-1 11h-4v4h-4v-4H6v-4h4V6h4v4h4v4z",
  school: "M5 13.18v4L12 21l7-3.82v-4L12 17l-7-3.82zM12 3L1 9l11 6 9-4.91V17h2V9L12 3z",
  yoga: "M12 2c1.1 0 2 .9 2 2s-.9 2-2 2-2-.9-2-2 .9-2 2-2zm9 14v-2c-2.24 0-4.16-.96-5.6-2.68l-1.34-1.6c-.38-.46-.94-.72-1.53-.72h-1.05c-.59 0-1.15.26-1.53.72l-1.34 1.6C7.16 13.04 5.24 14 3 14v2c2.77 0 5.19-1.17 7-3.25V15l-3.88 1.55c-.67.27-1.12.93-1.12 1.66C5 19.2 5.8 20 6.79 20H9v-.5c0-1.38 1.12-2.5 2.5-2.5h3c.28 0 .5.22.5.5s-.22.5-.5.5h-3c-.83 0-1.5.67-1.5 1.5v.5h7.21c.99 0 1.79-.8 1.79-1.79 0-.73-.45-1.39-1.12-1.66L14 15v-2.25c1.81 2.08 4.23 3.25 7 3.25z",
  bell: "M12 22c1.1 0 2-.9 2-2h-4c0 1.1.89 2 2 2zm6-6v-5c0-3.07-1.64-5.64-4.5-6.32V4c0-.83-.67-1.5-1.5-1.5s-1.5.67-1.5 1.5v.68C7.63 5.36 6 7.92 6 11v5l-2 2v1h16v-1l-2-2z",
  trend: "M16 6l2.29 2.29-4.88 4.88-4-4L2 16.59 3.41 18l6-6 4 4 6.3-6.29L22 12V6z",
});

document.body.innerHTML = `
<div class="glow" id="g1" style="width:640px;height:640px;background:#cfe0f3;left:-240px;top:120px;opacity:.9"></div>
<div class="glow" id="g2" style="width:560px;height:560px;background:#ffd9d2;right:-220px;top:1250px;opacity:.85"></div>

<div id="stage"><div class="scene" id="S1">
  <div class="small abs" id="k" style="left:0;right:0;top:150px;text-align:center;color:var(--steel);letter-spacing:4px">${p.kicker}</div>
  <div class="abs" id="bd" style="left:400px;top:225px;width:280px;height:280px;border-radius:80px;display:grid;place-items:center;color:#fff;
    background:linear-gradient(135deg,${p.badgeBg[0]},${p.badgeBg[1]});box-shadow:0 30px 60px rgba(16,32,60,.28)">${icon(p.badgeIcon, 150)}</div>
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
  <div class="h2 abs" id="w" style="left:90px;top:110px;font-size:60px">Listed on <span class="steel">ProHost</span></div>
  <div class="card abs" id="lc" style="left:90px;right:90px;top:225px;padding:26px;display:flex;gap:26px;align-items:center">
    <div style="width:150px;height:150px;border-radius:24px;flex:none;background:linear-gradient(135deg,#dfe8f3,#b9cde3);display:grid;place-items:center;color:var(--steel)">${icon(p.listing.icon, 76)}</div>
    <div style="flex:1;min-width:0">
      <div style="display:flex;justify-content:space-between;align-items:center"><span style="display:flex;gap:10px"><span class="tag">${p.listing.type}</span><span class="tag" style="background:var(--green)">Live</span></span>
        <span style="font-weight:800;font-size:44px;color:var(--steel)">${p.listing.price}<span style="font-size:26px;color:var(--muted);font-weight:600">${p.listing.unit}</span></span></div>
      <div style="font-weight:800;font-size:38px;margin-top:12px;letter-spacing:-.5px">${p.listing.title}</div>
      <div style="font-weight:600;font-size:26px;color:var(--muted);margin-top:6px;display:flex;align-items:center;gap:6px">${icon("pin", 28)} ${p.listing.place}</div>
    </div>
  </div>
  <div class="card abs" id="oc" style="left:90px;right:90px;top:455px;padding:30px 40px">
    <div class="small" style="color:var(--muted);letter-spacing:2px;font-size:24px">${p.rentTitle}</div>
    <div style="display:flex;flex-wrap:wrap;gap:16px;margin-top:18px">${p.chips.map((c) => `<span class="chip ch">${c.label}</span>`).join("")}</div>
  </div>
  <div class="card abs" id="rq" style="left:90px;right:90px;top:690px;padding:30px 40px;border:3px solid var(--orange-c)">
    <div style="display:flex;align-items:center;gap:16px">
      <span style="width:62px;height:62px;border-radius:18px;background:var(--orange-c);color:var(--orange);display:grid;place-items:center">${icon("bell", 38)}</span>
      <div style="flex:1"><div style="font-weight:800;font-size:34px">New request</div><div style="font-weight:600;font-size:26px;color:var(--muted)">${p.request.who}</div></div>
      <div style="font-weight:800;font-size:46px;color:var(--ink)">${p.request.amount}</div>
    </div>
    <div style="font-weight:600;font-size:28px;color:var(--ink);margin:16px 0 22px;line-height:1.4">${p.request.lines.join("<br>")}</div>
    <div style="display:flex;gap:18px">
      <div style="flex:1;height:88px;border-radius:24px;background:#eef2f7;color:var(--muted);display:grid;place-items:center;font-weight:800;font-size:32px">Decline</div>
      <div id="acc" style="flex:1.4;height:88px;border-radius:24px;background:var(--steel);color:#fff;display:flex;align-items:center;justify-content:center;gap:12px;font-weight:800;font-size:32px">
        <span id="a1">Accept</span><span id="a2" style="display:none;align-items:center;gap:10px">${icon("check", 40)} Accepted</span></div>
    </div>
  </div>
  <div class="card abs" id="er" style="left:90px;right:90px;top:1080px;padding:28px 40px;display:flex;align-items:center;gap:24px">
    <span style="width:84px;height:84px;border-radius:24px;background:var(--green-c);color:#2f8f57;display:grid;place-items:center">${icon("trend", 52)}</span>
    <div style="flex:1"><div style="font-weight:700;font-size:28px;color:var(--muted)">This month</div><div style="font-weight:600;font-size:24px;color:var(--muted)">${p.earn.note}</div></div>
    <div id="ev" style="font-weight:800;font-size:64px;color:#2f8f57">$0</div>
  </div>
  <div class="abs" id="tap" style="width:84px;height:84px;margin:-42px 0 0 -42px;border-radius:50%;background:rgba(43,90,140,.25);border:4px solid rgba(43,90,140,.65)"></div>
</div>

<div class="scene" id="S3">
  <div class="h2 abs" id="oT" style="left:60px;right:60px;top:40px;text-align:center;font-size:72px">${p.outcome}</div>
  <div class="small abs" id="ok" style="left:0;right:0;top:215px;text-align:center;color:var(--steel);letter-spacing:4px">LAUNCH OFFER</div>
  <div class="abs" id="ob" style="left:375px;top:270px;width:330px;height:330px;border-radius:50%;display:grid;place-items:center;text-align:center;color:#fff;
    background:radial-gradient(circle at 35% 30%,#ff8a75,var(--orange) 55%,var(--orange-d));box-shadow:0 30px 70px rgba(242,95,76,.45)">
    <div><div style="font-weight:800;font-size:112px;line-height:.9;letter-spacing:-4px">50%</div><div style="font-weight:800;font-size:42px;letter-spacing:2px">OFF</div></div></div>
  <div class="h3 abs" id="oS" style="left:0;right:0;top:650px;text-align:center;font-size:48px">your first <span class="orange">ProHost Premium</span><br>subscription</div>
  <div class="brandbar" id="bb" style="top:830px"></div>
  <div class="abs" id="gp" style="left:0;right:0;top:965px;text-align:center"><span class="gp"></span></div>
  <div class="abs small" id="ff" style="left:0;right:0;top:1125px;text-align:center;color:var(--muted);font-weight:500;font-size:23px;line-height:1.5">
    New subscribers · monthly or yearly · billed securely by Google Play<br>Prices and earnings shown are examples</div>
</div></div>`;
document.body.className = "light dots";
fillParts();

// ---- timeline ----
fadeIn("#k", 0.15, 0.5);
A("#bd", 0.25, 0.9, (e) => ({ opacity: Math.min(1, e * 2), transform: `scale(${lerp(.4, 1, e)}) rotate(${lerp(-12, 0, e)}deg)` }), "back");
fadeUp("#nm", 0.8, 0.6, 40);
fadeUp("#q", 1.4, 0.7, 80);
A(".ql", 1.7, 0.6, (e) => ({ opacity: e, transform: `translateY(${lerp(24, 0, e)}px)` }), "out", 0.45);
out("#S1", 4.6, 0.45, -60);

fadeIn("#S2", 5.0, 0.01);
fadeUp("#w", 5.05, 0.6);
A("#lc", 5.3, 0.7, (e) => ({ opacity: e, transform: `translateX(${lerp(160, 0, e)}px)` }));
fadeUp("#oc", 5.7, 0.6, 60);
pop(".ch", 5.9, 0.45, 0.08);
A("#rq", 7.7, 0.7, (e) => ({ opacity: Math.min(1, e * 1.6), transform: `translateY(${lerp(-90, 0, e)}px) scale(${lerp(.92, 1, e)})` }), "back");
fadeUp("#er", p.accept + 0.45, 0.6, 50);
out("#S2", 11.2, 0.45, -60);

fadeIn("#S3", 11.6, 0.01);
fadeUp("#oT", 11.65, 0.6);
fadeIn("#ok", 11.9, 0.4);
A("#ob", 12.0, 0.8, (e) => ({ opacity: Math.min(1, e * 2), transform: `scale(${lerp(.3, 1, e)}) rotate(${lerp(-25, 0, e)}deg)` }), "back");
fadeUp("#oS", 12.45, 0.6);
A("#bb", 12.75, 0.6, (e) => ({ opacity: e, transform: `translateX(-50%) translateY(${lerp(40, 0, e)}px)` }));
fadeUp("#gp", 13.0, 0.6);
fadeIn("#ff", 13.35, 0.5);

const chipEls = [...document.querySelectorAll(".ch")];
const stageTop = document.getElementById("stage").getBoundingClientRect().top;
const center = (el) => { const r = el.getBoundingClientRect(); return [r.left + r.width / 2, r.top - stageTop + r.height / 2]; };
const taps = [];
p.chips.forEach((c, i) => { if (c.on != null) taps.push({ t: c.on, at: center(chipEls[i]) }); });
taps.push({ t: p.accept, at: center(document.getElementById("acc")) });
taps.sort((a, b) => a.t - b.t);
const money = (v) => "+$" + Math.round(v).toLocaleString("en-US");

L((t) => {
  document.getElementById("g1").style.transform = `translate(${Math.sin(t * .5) * 50}px, ${Math.cos(t * .4) * 40}px)`;
  document.getElementById("g2").style.transform = `translate(${Math.cos(t * .45) * 50}px, ${Math.sin(t * .6) * 40}px)`;
  p.chips.forEach((c, i) => chipEls[i].classList.toggle("on", c.on != null && t >= c.on + 0.12));
  const ok = t >= p.accept + 0.15;
  document.getElementById("a1").style.display = ok ? "none" : "inline";
  document.getElementById("a2").style.display = ok ? "inline-flex" : "none";
  const b = document.getElementById("acc");
  b.style.background = ok ? "var(--green)" : "var(--steel)";
  b.style.transform = t > p.accept && t < p.accept + 0.3 ? `scale(${1 - Math.sin((t - p.accept) / 0.3 * Math.PI) * .05})` : "";
  const ee = Math.min(1, Math.max(0, (t - (p.accept + 0.7)) / 1.1));
  document.getElementById("ev").textContent = money(p.earn.total * E.out(ee));
  if (t > 12.8) document.getElementById("ob").style.transform = `scale(${1 + Math.sin((t - 12.8) * 3) * .025})`;
  const tap = document.getElementById("tap");
  const active = t > 6.0 && t < p.accept + 0.6;
  tap.style.opacity = active ? 1 : 0;
  if (active) {
    let prev = taps[0];
    for (const k of taps) { if (k.t <= t) prev = k; }
    const next = taps.find((k) => k.t > t) || prev;
    let x = prev.at[0], y = prev.at[1];
    if (next !== prev) {
      const e = E.inOut(Math.min(1, Math.max(0, (t - (next.t - 0.45)) / 0.45)));
      x = lerp(prev.at[0], next.at[0], e); y = lerp(prev.at[1], next.at[1], e);
    } else if (t < taps[0].t) { x = taps[0].at[0]; y = taps[0].at[1] + 160 * (1 - Math.min(1, (t - 6.0) / 0.4)); }
    const since = t - prev.t;
    const pulse = since >= 0 && since < 0.3 ? Math.sin(since / 0.3 * Math.PI) : 0;
    tap.style.left = x + "px"; tap.style.top = y + "px";
    tap.style.transform = `scale(${1 - pulse * .3})`;
  }
});
