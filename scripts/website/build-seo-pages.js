#!/usr/bin/env node
// Builds the pro-host.tech search landing pages (English + Arabic) from seo-pages.js into public/.
// Run after editing copy:  node scripts/website/build-seo-pages.js
const fs = require("fs");
const path = require("path");
const { PAGES, AR, LEGACY_SLUGS } = require("./seo-pages");

const SITE = "https://pro-host.tech";
const PUB = path.join(__dirname, "..", "..", "public");
const PLAY = "https://play.google.com/store/apps/details?id=app.geonajjar.prohost";
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
const strip = (s) => s.replace(/<[^>]+>/g, "");
const bySlug = Object.fromEntries(PAGES.map((p) => [p.slug, p]));
const NAV_LABEL = {
  "clinic-room-rental": "Clinic rooms", "therapy-room-rental": "Therapy rooms", "meeting-room-rental": "Meeting rooms",
  "training-room-rental": "Training rooms", "studio-rental": "Studios", "office-space-by-the-hour": "Offices & desks",
  "list-your-space": "List your space",
};
const getUrl = (medium, slug) => `/get?utm_source=website&amp;utm_medium=${medium}&amp;utm_campaign=seo_${slug.replace(/-/g, "_")}`;
const PLAY_SVG = `<svg width="26" height="28" viewBox="0 0 26 28" aria-hidden="true"><path d="M1.2.5 14.6 14 1.2 27.5c-.5-.3-.8-.9-.8-1.6V2.1c0-.7.3-1.3.8-1.6z" fill="#00D7FE"/><path d="M19.1 9.5 14.6 14 1.2.5c.2-.1.4-.2.7-.2.3 0 .6.1.9.2l16.3 9z" fill="#00F076"/><path d="M19.1 18.5 2.8 27.7c-.3.2-.6.2-.9.2-.3 0-.5-.1-.7-.2L14.6 14l4.5 4.5z" fill="#FF3A44"/><path d="M24.6 14c0 .6-.3 1.2-.9 1.5l-4.6 3L14.6 14l4.5-4.5 4.6 3c.6.3.9.9.9 1.5z" fill="#FFD500"/></svg>`;

const ORG = { "@type": "Organization", "@id": `${SITE}/#org`, name: "ProHost", url: `${SITE}/`, logo: `${SITE}/logo.png`, email: "admin@pro-host.tech",
  sameAs: [PLAY] };

const WEBSITE = { "@type": "WebSite", "@id": `${SITE}/#site`, url: `${SITE}/`, name: "ProHost", inLanguage: ["en", "ar"], publisher: { "@id": `${SITE}/#org` } };

function head({ lang, dir, title, description, keywords, canonical, image, imageAlt, ld, alternates, robots }) {
  return `<!DOCTYPE html>
<html lang="${lang}"${dir ? ` dir="${dir}"` : ""}>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, viewport-fit=cover">
<title>${esc(title)}</title>
<meta name="description" content="${esc(description)}">
<meta name="keywords" content="${esc(keywords)}">
<meta name="robots" content="${robots || "index, follow, max-image-preview:large"}">
<link rel="canonical" href="${canonical}">
${(alternates || []).map(([hl, href]) => `<link rel="alternate" hreflang="${hl}" href="${href}">`).join("\n")}
<meta property="og:type" content="website">
<meta property="og:site_name" content="ProHost">
<meta property="og:locale" content="${lang === "ar" ? "ar_AR" : "en_US"}">
<meta property="og:locale:alternate" content="${lang === "ar" ? "en_US" : "ar_AR"}">
<meta property="og:title" content="${esc(title)}">
<meta property="og:description" content="${esc(description)}">
<meta property="og:url" content="${canonical}">
<meta property="og:image" content="${image}">
<meta property="og:image:type" content="image/jpeg">
<meta property="og:image:width" content="1200">
<meta property="og:image:height" content="630">
<meta property="og:image:alt" content="${esc(imageAlt || title)}">
<meta name="twitter:card" content="summary_large_image">
<meta name="twitter:title" content="${esc(title)}">
<meta name="twitter:description" content="${esc(description)}">
<meta name="twitter:image" content="${image}">
<meta name="twitter:image:alt" content="${esc(imageAlt || title)}">
<meta name="theme-color" content="#1C2A40">
<link rel="icon" type="image/png" href="/logo.png">
<link rel="preconnect" href="https://fonts.googleapis.com"><link rel="preconnect" href="https://fonts.gstatic.com" crossorigin>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@600;700;800&family=Inter:wght@400;500;600${lang === "ar" ? "&family=Cairo:wght@400;600;700;800" : ""}&display=swap" rel="stylesheet">
<link rel="stylesheet" href="/landing.css">
<script type="application/ld+json">${JSON.stringify({ "@context": "https://schema.org", "@graph": ld })}</script>
</head>`;
}

const header = (ar) => `<header class="top"><div class="wrap nav">
  <a class="brand" href="${ar ? "/ar" : "/"}" aria-label="ProHost"><img class="mark" src="/logo.png" alt="ProHost logo" width="34" height="34"><span>Pro<i>Host</i></span></a>
  <nav aria-label="${ar ? "القائمة" : "Main"}">${ar
    ? `<a href="#rooms">المساحات</a><a href="#how">كيف يعمل</a><a href="#hosts">لأصحاب المساحات</a><a href="#faq">أسئلة</a><a href="/" hreflang="en" lang="en">English</a>`
    : `<a href="/clinic-room-rental">Clinics</a><a href="/therapy-room-rental">Therapy rooms</a><a href="/meeting-room-rental">Meeting rooms</a><a href="/list-your-space">List your space</a><a href="/ar" hreflang="ar" lang="ar">العربية</a>`}</nav>
  <a class="btn btn-orange" href="/get?utm_source=website&amp;utm_medium=header&amp;utm_campaign=seo">${ar ? "حمّل التطبيق" : "Get the app"}</a>
</div></header>`;

function footer(ar) {
  const links = PAGES.map((p) => `<a href="/${p.slug}">${NAV_LABEL[p.slug]}</a>`).join("");
  return `<footer class="site"><div class="wrap">
  <div class="foot">
    <div style="max-width:340px"><a class="brand" href="/"><img class="mark" src="/logo.png" alt="" width="34" height="34"><span>Pro<i>Host</i></span></a>
      <p style="margin-top:.8rem">${ar ? "احجز وأجّر العيادات والمكاتب والاستوديوهات وقاعات التدريب والاجتماعات، بالساعة أو بالدوام أو باليوم أو بالشهر أو حسب عدد المشاركين." : "Rent and book clinics, therapy rooms, offices, studios, classrooms and meeting rooms, by the hour, shift, day, month or per attendee."}</p></div>
    <nav aria-label="Popular searches" class="fcol"><b>${ar ? "مساحات" : "Find a space"}</b>${links}<a href="/ar" lang="ar">العربية</a></nav>
    <nav aria-label="Company" class="fcol"><b>ProHost</b><a href="/">Home</a><a href="/#premium">Premium</a><a href="/#contact">Contact</a><a href="/privacy">Privacy Policy</a><a href="/terms">Terms of Use</a><a href="/delete-account">Delete account</a></nav>
  </div>
  <div class="copy"><span>© 2026 ProHost · admin@pro-host.tech</span></div>
</div></footer>`;
}

function page(p) {
  const url = `${SITE}/${p.slug}`;
  const image = `${SITE}/img/og/${p.slug}.jpg`;
  const hosts = p.audience === "hosts";
  const ld = [
    ORG, WEBSITE,
    { "@type": "WebPage", "@id": `${url}#page`, url, name: p.title, description: p.description, inLanguage: "en", isPartOf: { "@id": `${SITE}/#site` },
      about: { "@type": "Service", serviceType: strip(p.h1), provider: { "@id": `${SITE}/#org` } },
      primaryImageOfPage: { "@type": "ImageObject", url: image, width: 1200, height: 630 }, breadcrumb: { "@id": `${url}#bc` } },
    { "@type": "BreadcrumbList", "@id": `${url}#bc`, itemListElement: [
      { "@type": "ListItem", position: 1, name: "ProHost", item: `${SITE}/` },
      { "@type": "ListItem", position: 2, name: NAV_LABEL[p.slug], item: url }] },
    { "@type": "FAQPage", mainEntity: p.faq.map(([q, a]) => ({ "@type": "Question", name: q, acceptedAnswer: { "@type": "Answer", text: a } })) },
  ];
  const html = `${head({ lang: "en", title: p.title, description: p.description, keywords: p.keywords, canonical: url, image, imageAlt: p.imgAlt, ld,
    alternates: [["en", url], ["ar", `${SITE}/ar`], ["x-default", url]] })}
<body>
${header(false)}
<main>
<nav class="crumbs wrap" aria-label="Breadcrumb"><a href="/">ProHost</a> <span aria-hidden="true">›</span> <span>${NAV_LABEL[p.slug]}</span></nav>
<section class="hero"><div class="wrap hero-grid">
  <div>
    <span class="eyebrow">${p.eyebrow}</span>
    <h1>${p.h1}</h1>
    <p class="lead">${p.lead}</p>
    <div class="ctas">
      <a class="play" href="${getUrl("hero", p.slug)}" aria-label="Get ProHost on Google Play">${PLAY_SVG}<span><small>GET IT ON</small><b>Google Play</b></span></a>
      <a class="btn btn-outline" href="${hosts ? "/#contact" : "#how"}">${hosts ? "Talk to our team" : "How it works"}</a>
    </div>
    <ul class="ticks">${(hosts ? ["50% off your first Premium subscription", "No booking commission", "Cancel anytime in Google Play"] : ["Free for professionals", "Phone-verified community", "Live availability"]).map((t) => `<li>${t}</li>`).join("")}</ul>
  </div>
  <img class="hero-img" src="/img/${p.img}.jpg" alt="${esc(p.imgAlt)}" width="640" height="800" fetchpriority="high">
</div></section>

<section><div class="wrap">
  <div class="sec-head"><span class="eyebrow">Why ProHost</span><h2>${hosts ? "Fill the hours your rooms sit empty" : "Book exactly the time you need"}</h2></div>
  <div class="grid4">${p.why.map(([t, d]) => `<article class="feat"><h3>${t}</h3><p>${d}</p></article>`).join("")}</div>
</div></section>

<section id="how" class="white"><div class="wrap">
  <div class="sec-head"><span class="eyebrow">How it works</span><h2>${hosts ? "From empty room to accepted booking in three steps" : "From search to confirmed booking in three steps"}</h2></div>
  <ol class="steps">${(hosts
    ? [["List your space", "Add photos, rooms, amenities and opening hours. Your listing appears on the ProHost map for professionals nearby."],
       ["Set how each room is rented", `Choose ${p.modes.join(", ").toLowerCase()} pricing, room by room, and change prices whenever you need.`],
       ["Accept requests", "Requests land in one inbox with every detail. Accept in a tap, talk on WhatsApp, and your availability updates itself."]]
    : [["Search the map", "Open ProHost, filter by space type, room and pricing formula, and see what is available in your area."],
       ["Pick your slots", "Open a room, check its live availability and choose the days and hours you need. The total is shown before you send."],
       ["Get confirmed", "The host accepts your request in the app. Track it in My Rentals, message the host on WhatsApp and add payment reminders."]])
    .map(([t, d]) => `<li><h3>${t}</h3><p>${d}</p></li>`).join("")}</ol>
  <div class="modes"><b>Ways to rent:</b> ${p.modes.map((m) => `<span class="chip">${m}</span>`).join("")}</div>
</div></section>

<section><div class="wrap two">
  <div><span class="eyebrow">${hosts ? "Who lists on ProHost" : "Who books on ProHost"}</span><h2 class="h2s">${hosts ? "Built for every kind of space" : "Made for independent professionals"}</h2>
    <ul class="list">${p.who.map((w) => `<li>${w}</li>`).join("")}</ul></div>
  <div><span class="eyebrow">Where</span><h2 class="h2s">${hosts ? "Found by professionals near your space" : "Spaces near you"}</h2>
    <p class="muted">${hosts ? "Every listing appears on a live map, so professionals searching in your area see your rooms and their live availability." : "Every listing appears on a live map. Search your area, filter by space type and pricing, and see which rooms are free right now."}</p>
    <p class="fine" style="margin-top:.8rem">Availability changes daily. Open the map in the app to see the spaces listed near you right now.</p></div>
</div></section>

<section id="faq" class="white"><div class="wrap">
  <div class="sec-head center"><span class="eyebrow">FAQ</span><h2>Frequently asked questions</h2></div>
  <div class="faq">${p.faq.map(([q, a], i) => `<details${i ? "" : " open"}><summary>${q}</summary><p>${a}</p></details>`).join("")}</div>
</div></section>

<section><div class="wrap">
  <div class="final"><div><h2>${hosts ? "Your first Premium subscription is half price." : "Find your next room on ProHost."}</h2>
    <p>${hosts ? "List your space today, monthly or yearly. Offer for new Premium subscribers; Google Play shows the exact price before you confirm." : "Free to download and free to book. Search the map, check live availability and send your request in minutes."}</p></div>
    <a class="btn btn-white" href="${getUrl("final", p.slug)}">Get ProHost on Google Play</a></div>
  <div class="related"><b>Related:</b> ${p.related.map((s) => `<a href="/${s}">${esc(strip(bySlug[s].h1))}</a>`).join("")}</div>
</div></section>
</main>
${footer(false)}
</body>
</html>
`;
  fs.writeFileSync(path.join(PUB, `${p.slug}.html`), html);
}

function arabic() {
  const url = `${SITE}/ar`;
  const faq = [
    ["هل يمكنني استئجار عيادة لبضع ساعات فقط في الأسبوع؟", "نعم. كثير من العيادات على ProHost معروضة بالدوام أو بالساعة، فتحجز مثلاً صباح الاثنين والخميس فقط، ويظهر لك المجموع قبل إرسال الطلب."],
    ["هل الحجز مجاني للمهنيين؟", "نعم. البحث والحجز على ProHost مجانيان للمهنيين، وتدفع ثمن الغرفة مباشرة لصاحب المساحة."],
    ["كيف أتواصل مع صاحب المساحة؟", "من صفحة المساحة اضغط على زر واتساب لتسأل عن التجهيزات أو الموقف قبل الحجز، من دون أن يُنشر رقم صاحب المساحة."],
    ["كم يكلّف عرض مساحتي على ProHost؟", "نشر المساحات وإدارتها جزء من اشتراك ProHost Premium الشهري أو السنوي عبر Google Play، مع خصم 50% على أول اشتراك للمشتركين الجدد. يظهر السعر الدقيق في Google Play قبل التأكيد."],
    ["هل يأخذ ProHost عمولة على الحجوزات؟", "لا. ProHost لا يأخذ عمولة ولا يعالج المدفوعات بينك وبين المهني، وتتفقان على الدفع مباشرة."],
  ];
  const ld = [ORG, WEBSITE,
    { "@type": "WebPage", "@id": `${url}#page`, url, name: AR.title, description: AR.description, inLanguage: "ar", isPartOf: { "@id": `${SITE}/#site` } },
    { "@type": "FAQPage", mainEntity: faq.map(([q, a]) => ({ "@type": "Question", name: q, acceptedAnswer: { "@type": "Answer", text: a } })) }];
  const rooms = [
    ["medical", "عيادات للايجار بالساعة أو بالدوام", "للأطباء وأطباء الأسنان وأخصائيي التغذية والعلاج الفيزيائي: غرف عيادات داخل مراكز طبية وعيادات خاصة."],
    ["therapy", "غرف علاج نفسي واستشارات", "للمعالجين النفسيين والمستشارين والمدرّبين: غرف هادئة وخاصة تحجزها لساعات جلساتك فقط."],
    ["meeting", "قاعات اجتماعات ومؤتمرات", "قاعات في فنادق ومراكز أعمال ومساحات عمل مشتركة، بالساعة أو باليوم أو حسب عدد المشاركين."],
    ["classroom", "قاعات تدريب وصفوف", "للمدرّبين ومراكز الدورات: صفوف متاحة في المساء وعطلة نهاية الأسبوع، مع تسعير حسب عدد المتدرّبين."],
    ["yoga", "استوديوهات يوغا ورقص ورياضة", "لمدرّبي اليوغا والبيلاتس والرقص: احجز ساعات صفّك أسبوعياً في استوديو قريب من طلابك."],
    ["office", "مكاتب خاصة ومكاتب مشتركة", "للمستقلين والاستشاريين والفرق الصغيرة: مكتب باليوم أو مقعد بالشهر من دون عقد طويل."],
  ];
  const html = `${head({ lang: "ar", dir: "rtl", title: AR.title, description: AR.description, keywords: AR.keywords, canonical: url, image: `${SITE}/img/og-hero.jpg`, imageAlt: "ProHost: تأجير العيادات والمكاتب والقاعات بالساعة", ld,
    alternates: [["ar", url], ["en", `${SITE}/`], ["x-default", `${SITE}/`]] })}
<body class="rtl">
${header(true)}
<main>
<section class="hero"><div class="wrap hero-grid">
  <div>
    <span class="eyebrow">للمهنيين ولأصحاب المساحات</span>
    <h1>عيادة أو مكتب أو قاعة… <em>بالساعة</em> أو بالدوام أو بالشهر</h1>
    <p class="lead">ProHost تطبيق لحجز وتأجير مساحات العمل: عيادات، غرف علاج نفسي، مكاتب، قاعات اجتماعات وتدريب واستوديوهات. احجز الوقت الذي تحتاجه فقط، أو اعرض غرفك الفارغة واربح منها.</p>
    <div class="ctas">
      <a class="play" href="/get?utm_source=website&amp;utm_medium=hero&amp;utm_campaign=seo_ar" aria-label="حمّل ProHost من Google Play">${PLAY_SVG}<span><small>GET IT ON</small><b>Google Play</b></span></a>
      <a class="btn btn-outline" href="#hosts">لأصحاب المساحات</a>
    </div>
    <ul class="ticks"><li>مجاني للمهنيين</li><li>أرقام هاتف موثّقة</li><li>توفّر مباشر من دون حجز مزدوج</li></ul>
  </div>
  <img class="hero-img" src="/img/clinic.jpg" alt="غرفة عيادة للايجار بالساعة عبر ProHost" width="640" height="800" fetchpriority="high">
</div></section>

<section id="rooms"><div class="wrap">
  <div class="sec-head"><span class="eyebrow">ماذا تجد على ProHost</span><h2>مساحات عمل لكل مهنة</h2></div>
  <div class="cards3">${rooms.map(([img, t, d]) => `<article class="rcard"><img src="/img/${img}.jpg" alt="${esc(t)}" width="640" height="800" loading="lazy"><div><h3>${t}</h3><p>${d}</p></div></article>`).join("")}</div>
</div></section>

<section id="how" class="white"><div class="wrap">
  <div class="sec-head"><span class="eyebrow">كيف يعمل</span><h2>من البحث إلى الحجز المؤكد بثلاث خطوات</h2></div>
  <ol class="steps"><li><h3>ابحث على الخريطة</h3><p>افتح ProHost وفلتر حسب نوع المساحة والغرفة وطريقة التسعير، وشاهد ما هو متاح في منطقتك.</p></li>
    <li><h3>اختر أوقاتك</h3><p>افتح الغرفة، تحقق من التوفّر المباشر، واختر الأيام والساعات. يظهر المجموع قبل الإرسال.</p></li>
    <li><h3>احصل على التأكيد</h3><p>يقبل صاحب المساحة طلبك في التطبيق. تابعه في «إيجاراتي» وتواصل عبر واتساب وأضف تذكيرات الدفع.</p></li></ol>
  <div class="modes"><b>طرق الإيجار:</b> <span class="chip">بالساعة</span><span class="chip">بالدوام</span><span class="chip">باليوم</span><span class="chip">بالشهر</span><span class="chip">حسب عدد المشاركين</span></div>
</div></section>

<section id="hosts"><div class="wrap two">
  <div><span class="eyebrow">لأصحاب المساحات</span><h2 class="h2s">غرفك فارغة جزءاً من الأسبوع؟ أجّرها.</h2>
    <p class="muted">فنادق ومراكز طبية ومراكز تدريب واستوديوهات ومكاتب تعرض غرفها على ProHost لتملأ الساعات الفارغة مع مهنيين موثّقين. خمس طرق للتأجير لكل غرفة، صندوق طلبات واحد، توفّر مباشر يمنع الحجز المزدوج، وتغيير الأسعار في أي وقت مع إشعار المستأجرين تلقائياً.</p>
    <a class="btn btn-orange" style="margin-top:1rem" href="/get?utm_source=website&amp;utm_medium=hosts&amp;utm_campaign=seo_ar">خصم 50% على أول اشتراك Premium</a></div>
  <div><span class="eyebrow">الموقع</span><h2 class="h2s">مساحات قريبة منك</h2>
    <p class="muted">تظهر كل مساحة على خريطة مباشرة، فتبحث في منطقتك وتفلتر حسب نوع المساحة وطريقة التسعير، وترى الغرف المتاحة الآن.</p>
    <p class="fine" style="margin-top:.8rem">يتغيّر التوفّر يومياً. افتح الخريطة في التطبيق لترى المساحات المعروضة قربك الآن.</p></div>
</div></section>

<section id="faq" class="white"><div class="wrap">
  <div class="sec-head center"><span class="eyebrow">أسئلة شائعة</span><h2>أسئلة وأجوبة</h2></div>
  <div class="faq">${faq.map(([q, a], i) => `<details${i ? "" : " open"}><summary>${q}</summary><p>${a}</p></details>`).join("")}</div>
</div></section>

<section><div class="wrap"><div class="final"><div><h2>حمّل ProHost اليوم</h2><p>مجاني للتنزيل ومجاني للحجز. ولأصحاب المساحات: خصم 50% على أول اشتراك Premium، شهري أو سنوي.</p></div>
  <a class="btn btn-white" href="/get?utm_source=website&amp;utm_medium=final&amp;utm_campaign=seo_ar">حمّل من Google Play</a></div></div></section>
</main>
${footer(true)}
</body>
</html>
`;
  fs.writeFileSync(path.join(PUB, "ar.html"), html);
}

/** /partner: the strategic-partner offer linked from the introduction email. Not in the sitemap and noindex: it is shared by link. */
function partner() {
  const url = `${SITE}/partner`, title = "Become ProHost's Strategic Partner | ProHost";
  const description = "ProHost is opening one strategic partner position: a share of Google Play revenue, free listings for up to 100 accounts and your logo in the app and on our marketing.";
  const mail = "mailto:admin@pro-host.tech?subject=" + encodeURIComponent("ProHost strategic partnership");
  const perks = [
    ["13% of app revenue", "13% of all revenue that Google Play billing distributes to ProHost for ProHost Premium subscriptions, in every country."],
    ["Unlimited free listings", "Create up to 100 accounts and publish unlimited listings on all of them, with no Premium subscription to pay."],
    ["Your logo in the app", "Your logo on the ProHost splash screen and on the login screen that every user sees when opening the app."],
    ["Your logo on our marketing", "Your logo on all ProHost marketing materials in every expansion country on our list, flagged \"In partnership with\"."],
  ];
  const faq = [
    ["What does the partner invest?", "USD 24,000. In return the partner receives everything listed on this page."],
    ["How is the 13% calculated?", "On the revenue Google Play distributes to ProHost from ProHost Premium subscriptions. The written agreement sets out the calculation, the reporting and the payment dates."],
    ["Which countries are covered?", "Every expansion country on ProHost's list. We share the list with the partner and the logo appears in the marketing of each country as it launches."],
    ["How many partners are there?", "One. This is a single strategic partner position."],
    ["How do we start?", "Write to admin@pro-host.tech or use the button below. We will set up a call and send the written agreement."],
  ];
  const ld = [ORG, WEBSITE, { "@type": "WebPage", "@id": `${url}#page`, url, name: title, description, inLanguage: "en", isPartOf: { "@id": `${SITE}/#site` } }];
  const html = `${head({ lang: "en", title, description, keywords: "ProHost strategic partner, ProHost sponsor, ProHost partnership", canonical: url, image: `${SITE}/img/og/partner.jpg`, imageAlt: "ProHost strategic partner offer", ld,
    alternates: [["en", url], ["x-default", url]], robots: "noindex, follow" })}
<body>
${header(false)}
<main>
<section class="hero"><div class="wrap hero-grid">
  <div>
    <span class="eyebrow">Strategic partnership</span>
    <h1>Become ProHost's <em>strategic partner</em></h1>
    <p class="lead">We are opening one strategic partner position for the launch and expansion of ProHost. Your brand goes into the app and onto every market we enter, and you share in the revenue the app earns on Google Play.</p>
    <div class="ctas"><a class="btn btn-orange" href="${mail}">Talk to us about the partnership</a><a class="btn btn-outline" href="#package">See the package</a></div>
    <ul class="ticks"><li>One partner only</li><li>Investment: USD 24,000</li><li>Terms in a written agreement</li></ul>
  </div>
  <img class="hero-img" src="/img/meeting.jpg" alt="A meeting room listed on ProHost" width="640" height="800" fetchpriority="high">
</div></section>

<section id="package"><div class="wrap">
  <div class="sec-head"><span class="eyebrow">The partner package</span><h2>What the partner receives for USD 24,000</h2></div>
  <div class="grid4">${perks.map(([t, d]) => `<article class="feat"><h3>${t}</h3><p>${d}</p></article>`).join("")}</div>
</div></section>

<section class="white"><div class="wrap">
  <div class="sec-head"><span class="eyebrow">How it works</span><h2>From first call to your logo in the app</h2></div>
  <ol class="steps">
    <li><h3>Talk to us</h3><p>A short call about your goals and the countries you care about.</p></li>
    <li><h3>Sign the agreement</h3><p>The written agreement sets the revenue calculation, reporting, payment dates and brand rules.</p></li>
    <li><h3>Go live as partner</h3><p>Your logo appears in the app and in our marketing, and your accounts get free listings.</p></li>
  </ol>
</div></section>

<section id="faq"><div class="wrap">
  <div class="sec-head center"><span class="eyebrow">FAQ</span><h2>Partner questions</h2></div>
  <div class="faq">${faq.map(([q, a], i) => `<details${i ? "" : " open"}><summary>${q}</summary><p>${a}</p></details>`).join("")}</div>
</div></section>

<section class="white"><div class="wrap"><div class="final"><div><h2>Interested in being our partner?</h2><p>Write to us and we will set up a call. Terms are confirmed in a written agreement.</p></div>
  <a class="btn btn-white" href="${mail}">Email admin@pro-host.tech</a></div></div></section>
</main>
${footer(false)}
</body>
</html>
`;
  fs.writeFileSync(path.join(PUB, "partner.html"), html);
}

PAGES.forEach(page);
arabic();
partner();
// A retired slug must not linger as a static file: firebase.json 301-redirects it to the new page.
Object.keys(LEGACY_SLUGS).forEach((old) => { const f = path.join(PUB, `${old}.html`); if (fs.existsSync(f)) { fs.unlinkSync(f); console.log(`removed retired page ${old}.html`); } });
console.log(`built ${PAGES.length} English pages + /ar + /partner`);
module.exports = { PAGES, LEGACY_SLUGS };
