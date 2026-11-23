"""ProHost investment analysis: forecast model + 3-page PDF (plus a 1-page appendix with the monthly table).

    python3 scripts/business/investment_report.py      -> docs/business/ProHost_Investment_Analysis.pdf (+ model CSV)

Every chart and table comes from the ASSUMPTIONS below (nothing hard-coded in the charts). Prices are the live store prices
(Google Play = App Store, 22 Nov 2026); traction numbers are from Maintenance `prodcheck` (22 Nov 2026).
"""
import csv
import math
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import Circle  # noqa: E402
from reportlab.lib import colors  # noqa: E402
from reportlab.lib.enums import TA_LEFT  # noqa: E402
from reportlab.lib.pagesizes import A4  # noqa: E402
from reportlab.lib.styles import ParagraphStyle  # noqa: E402
from reportlab.lib.units import mm  # noqa: E402
from reportlab.platypus import (Image, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle, KeepInFrame)  # noqa: E402

ROOT = __file__.rsplit("/", 3)[0]
OUT = os.path.join(ROOT, "docs/business")
TMP = os.path.join(OUT, "charts")
os.makedirs(TMP, exist_ok=True)

NAVY, ORANGE, STEEL, GREY, GREEN, RED = "#001f4e", "#fd631d", "#2B5A8C", "#8a94a6", "#2e8b57", "#c0392b"

# ----------------------------------------------------------------------------------------------------------------------
# ASSUMPTIONS (all monthly unless stated)
# ----------------------------------------------------------------------------------------------------------------------
PRICES = {"single_m": 4.49, "single_y": 44.99, "unl_m": 8.99, "unl_y": 79.99}
MIX = {"single": 0.55, "yearly_share": 0.30}          # 55% of hosts on 1 listing; 30% pay yearly
FREE_MONTH_DRAG = 0.05                                  # first month free on Unlimited monthly
STORE_FEE = 0.15                                        # Apple / Google small-business rate
gross_arpu = (MIX["single"] * ((1 - MIX["yearly_share"]) * PRICES["single_m"] + MIX["yearly_share"] * PRICES["single_y"] / 12)
              + (1 - MIX["single"]) * ((1 - MIX["yearly_share"]) * PRICES["unl_m"] + MIX["yearly_share"] * PRICES["unl_y"] / 12))
gross_arpu *= (1 - FREE_MONTH_DRAG)
NET_ARPU = gross_arpu * (1 - STORE_FEE)

SCENARIOS = {
    # registered users at the end of years 1..5, paying-host share of users, team cost multiplier, host CAC
    "Conservative": {"users": [2_000, 6_000, 15_000, 30_000, 55_000], "host_share": (0.040, 0.050), "team": 0.6, "cac": 45,
                     "upsell": False},
    "Expected": {"users": [5_000, 18_000, 50_000, 120_000, 250_000], "host_share": (0.045, 0.060), "team": 1.0, "cac": 38,
                 "upsell": True},
    "Aggressive": {"users": [10_000, 45_000, 150_000, 400_000, 900_000], "host_share": (0.050, 0.065), "team": 1.6, "cac": 32,
                   "upsell": True},
}
START_USERS = 92                                        # Auth users today (prodcheck 22 Nov 2026)
MAU_SHARE = 0.35
HOST_CHURN = [0.065, 0.055, 0.045, 0.040, 0.040]        # monthly, by year
SPEC_CHURN = [0.08, 0.07, 0.06, 0.055, 0.05]            # monthly inactive-churn of specialists
SPEC_CAC = 1.2                                          # blended (SEO, Instagram agent, referrals)
TEAM = [6_000, 15_000, 35_000, 60_000, 90_000]          # monthly payroll + contractors (Expected), by year
FIXED_OTHER = [800, 1_500, 2_500, 3_000, 4_000]         # legal, compliance, accounting, tools
INFRA_BASE, INFRA_PER_MAU, AI_PER_USER = 200, 0.035, 0.004
SUPPORT_PER_MAU, SUPPORT_PER_HOST = 0.04, 1.0
# Upsell streams (Expected / Aggressive): featured listings from month 13, enterprise packages and booking fee from month 25
FEATURED = {"start": 13, "adoption": 0.06, "price": 12.0}
ENTERPRISE = {"start": 25, "per_hosts": 400, "price": 199.0}
BOOKING = {"start": 25, "gmv_per_host": 650.0, "in_app_share": (0.05, 0.30), "take": 0.05, "processing": 0.029}


def year_of(m):
    return min((m - 1) // 12, 4)


def interp_users(targets, m):
    """Geometric interpolation between year-end targets (month 0 = START_USERS)."""
    points = [(0, START_USERS)] + [(12 * (i + 1), u) for i, u in enumerate(targets)]
    for (m0, u0), (m1, u1) in zip(points, points[1:]):
        if m <= m1:
            f = (m - m0) / (m1 - m0)
            return u0 * (u1 / u0) ** f
    return targets[-1]


def run(name):
    sc = SCENARIOS[name]
    rows, prev_hosts, prev_users, cash = [], 0.0, START_USERS, 0.0
    for m in range(1, 61):
        y = year_of(m)
        users = interp_users(sc["users"], m)
        share = sc["host_share"][0] + (sc["host_share"][1] - sc["host_share"][0]) * (m - 1) / 59
        hosts = users * share
        specs = users - hosts
        mau = users * MAU_SHARE
        churned_hosts = prev_hosts * HOST_CHURN[y]
        new_hosts = max(hosts - prev_hosts + churned_hosts, 0)
        new_users = max(users - prev_users, 0)
        new_specs = max(new_users - new_hosts, 0)
        subs = hosts * gross_arpu
        featured = hosts * FEATURED["adoption"] * FEATURED["price"] if sc["upsell"] and m >= FEATURED["start"] else 0
        enterprise = (hosts / ENTERPRISE["per_hosts"]) * ENTERPRISE["price"] if sc["upsell"] and m >= ENTERPRISE["start"] else 0
        if sc["upsell"] and m >= BOOKING["start"]:
            f = (m - BOOKING["start"]) / (60 - BOOKING["start"])
            in_app = BOOKING["in_app_share"][0] + (BOOKING["in_app_share"][1] - BOOKING["in_app_share"][0]) * f
            gmv = hosts * BOOKING["gmv_per_host"] * in_app
        else:
            gmv = 0
        booking = gmv * BOOKING["take"]
        revenue = subs + featured + enterprise + booking
        store_fees = (subs + featured) * STORE_FEE
        processing = gmv * BOOKING["processing"]
        infra = INFRA_BASE + mau * INFRA_PER_MAU + users * AI_PER_USER
        support = mau * SUPPORT_PER_MAU + hosts * SUPPORT_PER_HOST
        marketing = new_hosts * sc["cac"] + new_specs * SPEC_CAC
        team = TEAM[y] * sc["team"]
        other = FIXED_OTHER[y]
        cogs = store_fees + processing + infra + support
        cost = cogs + marketing + team + other
        ebitda = revenue - cost
        cash += ebitda
        rows.append(dict(month=m, year=y + 1, users=users, specialists=specs, hosts=hosts, mau=mau, new_hosts=new_hosts,
                         churned_hosts=churned_hosts, subs=subs, featured=featured, enterprise=enterprise, booking=booking,
                         revenue=revenue, store_fees=store_fees, processing=processing, infra=infra, support=support,
                         marketing=marketing, team=team, other=other, cogs=cogs, cost=cost, ebitda=ebitda, cash=cash))
        prev_hosts, prev_users = hosts, users
    return rows


MODEL = {n: run(n) for n in SCENARIOS}
EXP = MODEL["Expected"]


def yearly(rows, key, how="sum"):
    out = []
    for y in range(1, 6):
        vals = [r[key] for r in rows if r["year"] == y]
        out.append(sum(vals) if how == "sum" else vals[-1])
    return out


def money(v, dec=0):
    s = "-" if v < 0 else ""
    v = abs(v)
    if v >= 1e6:
        return f"{s}${v / 1e6:.{max(dec, 1)}f}M"
    if v >= 1e3:
        return f"{s}${v / 1e3:.{dec}f}k"
    return f"{s}${v:.0f}"


# ---- unit economics (Expected, year 3 steady state)
churn3 = HOST_CHURN[2]
gm_host = 1 - STORE_FEE - (SUPPORT_PER_HOST / gross_arpu)
LTV = gross_arpu * gm_host / churn3
CAC = SCENARIOS["Expected"]["cac"]
PAYBACK = CAC / (gross_arpu * gm_host)
y_rev, y_cogs = yearly(EXP, "revenue"), yearly(EXP, "cogs")
GROSS_MARGIN = [(r - c) / r if r else 0 for r, c in zip(y_rev, y_cogs)]
contrib = [(r - c - mk) / r if r else 0 for r, c, mk in zip(y_rev, y_cogs, yearly(EXP, "marketing"))]
def be_of(rows):
    return next((r["month"] for r in rows if r["ebitda"] > 0 and all(x["ebitda"] > 0 for x in rows if x["month"] >= r["month"])), None)


be_month = next((r["month"] for r in EXP if r["ebitda"] > 0 and all(x["ebitda"] > 0 for x in EXP if x["month"] >= r["month"])), None)
min_cash = min(r["cash"] for r in EXP)

# ---- cost/revenue as a function of users (run-rate, Expected ratios at a steady state)
USER_POINTS = [1_000, 10_000, 50_000, 100_000, 250_000, 500_000, 1_000_000]


def run_rate(users):
    hosts = users * 0.06
    mau = users * MAU_SHARE
    team = 6_000 if users < 5_000 else 15_000 if users < 25_000 else 35_000 if users < 80_000 else 60_000 if users < 200_000 else 90_000 if users < 400_000 else 160_000 if users < 750_000 else 240_000
    other = 800 if users < 5_000 else 2_500 if users < 80_000 else 4_000 if users < 400_000 else 8_000
    growth_new_hosts = hosts * 0.06
    gmv = hosts * BOOKING["gmv_per_host"] * 0.2
    revenue = hosts * gross_arpu + hosts * FEATURED["adoption"] * FEATURED["price"] + hosts / ENTERPRISE["per_hosts"] * ENTERPRISE["price"] + gmv * BOOKING["take"]
    cost = (hosts * gross_arpu * STORE_FEE + gmv * BOOKING["processing"] + INFRA_BASE + mau * INFRA_PER_MAU + users * AI_PER_USER
            + mau * SUPPORT_PER_MAU + hosts * SUPPORT_PER_HOST + growth_new_hosts * CAC + users * 0.01 * SPEC_CAC + team + other)
    return revenue, cost, dict(team=team, other=other, infra=INFRA_BASE + mau * INFRA_PER_MAU + users * AI_PER_USER,
                               support=mau * SUPPORT_PER_MAU + hosts * SUPPORT_PER_HOST, marketing=growth_new_hosts * CAC + users * 0.01 * SPEC_CAC,
                               fees=hosts * gross_arpu * STORE_FEE + gmv * BOOKING["processing"])


grid = [int(10 ** (3 + 3 * i / 120)) for i in range(121)]
rr = [run_rate(u) for u in grid]
BE_USERS = next((u for u, (rv, c, _) in zip(grid, rr) if rv >= c and all(r2[0] >= r2[1] for r2 in rr[grid.index(u):])), None)

# ----------------------------------------------------------------------------------------------------------------------
# CHARTS
# ----------------------------------------------------------------------------------------------------------------------
plt.rcParams.update({"font.size": 8, "axes.titlesize": 9, "axes.titleweight": "bold", "axes.spines.top": False,
                     "axes.spines.right": False, "axes.edgecolor": "#9aa5b5", "axes.labelcolor": "#33415c"})
SC_COL = {"Conservative": GREY, "Expected": STEEL, "Aggressive": ORANGE}
yrs = ["Y1", "Y2", "Y3", "Y4", "Y5"]


def save(fig, name):
    p = os.path.join(TMP, name)
    fig.tight_layout()
    fig.savefig(p, dpi=200)
    plt.close(fig)
    return p


# 1 revenue growth (3 scenarios)
fig, ax = plt.subplots(figsize=(3.6, 2.3))
for n, rows in MODEL.items():
    ax.plot(yrs, [v / 1e6 for v in yearly(rows, "revenue")], marker="o", color=SC_COL[n], label=n, lw=1.8)
ax.set_title("1. Revenue growth by scenario ($M / year)")
ax.legend(frameon=False, fontsize=7)
ax.grid(axis="y", alpha=0.25)
C1 = save(fig, "c1_revenue.png")

# 2 cost growth (Expected, stacked by category)
fig, ax = plt.subplots(figsize=(3.6, 2.3))
cats = [("team", "Team", NAVY), ("other", "Legal/fixed", "#5b6b85"), ("marketing", "Acquisition", ORANGE),
        ("store_fees", "Store/processing fees", "#f2a17a"), ("infra", "Infrastructure + AI", STEEL), ("support", "Support", "#9fb8d6")]
bottom = [0] * 5
for key, label, col in cats:
    vals = yearly(EXP, key)
    if key == "store_fees":
        vals = [a + b for a, b in zip(vals, yearly(EXP, "processing"))]
    ax.bar(yrs, [v / 1e6 for v in vals], bottom=[b / 1e6 for b in bottom], color=col, label=label, width=0.6)
    bottom = [b + v for b, v in zip(bottom, vals)]
ax.set_title("2. Cost growth, Expected ($M / year)")
ax.legend(frameon=False, fontsize=6, ncol=2)
ax.grid(axis="y", alpha=0.25)
C2 = save(fig, "c2_costs.png")

# 3 revenue vs cost (months) + vs users
fig, (a1, a2) = plt.subplots(1, 2, figsize=(7.4, 2.4))
ms = [r["month"] for r in EXP]
a1.plot(ms, [r["revenue"] / 1e3 for r in EXP], color=GREEN, lw=1.8, label="Revenue")
a1.plot(ms, [r["cost"] / 1e3 for r in EXP], color=RED, lw=1.8, label="Cost")
a1.fill_between(ms, [r["ebitda"] / 1e3 for r in EXP], 0, color=STEEL, alpha=0.15, label="EBITDA")
if be_month:
    a1.axvline(be_month, color=NAVY, ls="--", lw=1)
    a1.text(be_month + 1, a1.get_ylim()[1] * 0.85, f"break-even\nmonth {be_month}", fontsize=7, color=NAVY)
a1.set_title("3a. Revenue vs cost, Expected, months 1-60 ($k / month)")
a1.legend(frameon=False, fontsize=7)
a1.grid(alpha=0.25)
a2.plot(grid, [r[0] / 1e3 for r in rr], color=GREEN, lw=1.8, label="Revenue run-rate")
a2.plot(grid, [r[1] / 1e3 for r in rr], color=RED, lw=1.8, label="Cost run-rate")
a2.set_xscale("log")
if BE_USERS:
    a2.axvline(BE_USERS, color=NAVY, ls="--", lw=1)
    a2.text(BE_USERS * 1.1, a2.get_ylim()[1] * 0.8, f"break-even\n~{BE_USERS / 1e3:.0f}k users", fontsize=7, color=NAVY)
a2.set_title("3b. Revenue vs cost by registered users ($k / month)")
a2.legend(frameon=False, fontsize=7)
a2.grid(alpha=0.25)
C3 = save(fig, "c3_rev_vs_cost.png")

# 4 user growth (3 scenarios) + hosts
fig, ax = plt.subplots(figsize=(3.6, 2.3))
for n, rows in MODEL.items():
    ax.plot(yrs, [v / 1e3 for v in yearly(rows, "users", "last")], marker="o", color=SC_COL[n], lw=1.8, label=f"{n} users")
    ax.plot(yrs, [v / 1e3 for v in yearly(rows, "hosts", "last")], ls="--", color=SC_COL[n], lw=1, label=f"{n} paying hosts")
ax.set_yscale("log")
ax.set_title("4. User growth forecast (thousands, log)")
ax.legend(frameon=False, fontsize=5.5, ncol=2)
ax.grid(alpha=0.25)
C4 = save(fig, "c4_users.png")

# 5 revenue mix pie (Expected, year 5)
mix_vals = [yearly(EXP, k)[4] for k in ("subs", "featured", "booking", "enterprise")]
fig, ax = plt.subplots(figsize=(3.0, 2.3))
ax.pie(mix_vals, labels=["Subscriptions", "Featured listings", "Booking fee", "Enterprise"], autopct="%1.0f%%",
       colors=[STEEL, ORANGE, NAVY, "#9fb8d6"], textprops={"fontsize": 7}, startangle=90,
       wedgeprops={"linewidth": 1, "edgecolor": "white"})
ax.set_title("5. Revenue mix, Expected Y5")
C5 = save(fig, "c5_mix.png")

# 6 TAM SAM SOM
TAM, SAM, SOM = 50e9, 2.1e9, 25e6
fig, ax = plt.subplots(figsize=(3.0, 2.3))
for r, col, lab, val, yoff in [(1.0, "#dbe5f2", "TAM", TAM, 0.72), (0.58, "#9fb8d6", "SAM", SAM, 0.30), (0.22, ORANGE, "SOM", SOM, -0.02)]:
    ax.add_patch(Circle((0, r - 1), r, color=col))
    ax.text(0, yoff - 0.25 if lab == "TAM" else yoff - 0.45 if lab == "SAM" else -0.78, f"{lab}\n{money(val)}", ha="center", va="center",
            fontsize=7, color="white" if lab == "SOM" else NAVY, fontweight="bold")
ax.set_xlim(-1.05, 1.05)
ax.set_ylim(-2.05, 0.05)
ax.set_aspect("equal")
ax.axis("off")
ax.set_title("6. TAM / SAM / SOM (bookings value)")
C6 = save(fig, "c6_tam.png")

# 7 competitor positioning
COMP = [("Peerspace", 8.0, 2.5, "20% host fee"), ("LiquidSpace", 7.0, 4.5, "commission"), ("Regus / IWG", 9.0, 5.0, "owns space"),
        ("WeWork On Demand", 7.5, 3.5, "per booking"), ("Tagvenue", 6.0, 2.0, "commission"), ("Deskpass", 6.5, 4.0, "member subs"),
        ("FB / WhatsApp groups", 1.0, 5.5, "free, no tools"), ("ProHost", 2.0, 8.5, "flat $4.49-8.99, 0%")]
fig, ax = plt.subplots(figsize=(3.6, 2.6))
for name, x, y, note in COMP:
    col = ORANGE if name == "ProHost" else STEEL
    ax.scatter(x, y, s=120 if name == "ProHost" else 50, color=col, zorder=3)
    ax.annotate(name, (x, y), textcoords="offset points", xytext=(5, 4), fontsize=6.5, color=NAVY if name != "ProHost" else ORANGE,
                fontweight="bold" if name == "ProHost" else "normal")
ax.set_xlim(0, 10.5)
ax.set_ylim(0, 10)
ax.axhline(5, color="#c8d0dc", lw=0.8)
ax.axvline(5, color="#c8d0dc", lw=0.8)
ax.set_xlabel("Cost to host (take rate / price) →", fontsize=7)
ax.set_ylabel("Focus on professional rooms →", fontsize=7)
ax.set_xticks([])
ax.set_yticks([])
ax.set_title("7. Competitive positioning")
C7 = save(fig, "c7_matrix.png")

# ----------------------------------------------------------------------------------------------------------------------
# PDF
# ----------------------------------------------------------------------------------------------------------------------
H1 = ParagraphStyle("h1", fontName="Helvetica-Bold", fontSize=15, textColor=colors.HexColor(NAVY), spaceAfter=2, leading=18)
H2 = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=9.5, textColor=colors.HexColor(ORANGE), spaceBefore=4, spaceAfter=1.5, leading=11.5)
B = ParagraphStyle("b", fontName="Helvetica", fontSize=7.4, leading=9.3, textColor=colors.HexColor("#1c2738"), alignment=TA_LEFT)
S = ParagraphStyle("s", parent=B, fontSize=6.4, leading=7.8, textColor=colors.HexColor("#4a5568"))


def P(t, st=B):
    return Paragraph(t, st)


def tbl(data, widths, font=6.6, head=True, zebra=True):
    t = Table(data, colWidths=widths)
    style = [("FONT", (0, 0), (-1, -1), "Helvetica", font), ("VALIGN", (0, 0), (-1, -1), "TOP"),
             ("TOPPADDING", (0, 0), (-1, -1), 1.2), ("BOTTOMPADDING", (0, 0), (-1, -1), 1.2),
             ("LEFTPADDING", (0, 0), (-1, -1), 2.5), ("RIGHTPADDING", (0, 0), (-1, -1), 2.5),
             ("LINEBELOW", (0, 0), (-1, 0), 0.6, colors.HexColor(NAVY))]
    if head:
        style += [("FONT", (0, 0), (-1, 0), "Helvetica-Bold", font), ("TEXTCOLOR", (0, 0), (-1, 0), colors.HexColor(NAVY))]
    if zebra:
        style += [("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f2f5fa")])]
    t.setStyle(TableStyle(style))
    return t


W = A4[0] - 24 * mm
img = lambda p, w, h: Image(p, width=w, height=h)  # noqa: E731
story = []

y5 = {n: yearly(MODEL[n], "revenue")[4] for n in MODEL}
y5e = yearly(EXP, "ebitda")
users5 = yearly(EXP, "users", "last")
hosts5 = yearly(EXP, "hosts", "last")

# ---------------- PAGE 1
story += [P("ProHost — Investment &amp; Business Analysis", H1),
          P(f"Rooms by the hour for professionals · Android + iOS + web · analysis date 23 Nov 2026 · all figures USD · model: "
            f"<font face='Courier'>scripts/business/investment_report.py</font>", S), Spacer(1, 3)]
story += [P("Executive summary", H2), P(
    "<b>Product.</b> ProHost is a two-sided marketplace app where <b>space owners (Pro Hosts)</b> list clinics, therapy and meeting rooms, "
    "studios and offices by the hour, shift, day or month, and <b>professionals (Specialists)</b> discover them on a map, check dated "
    "availability and send booking requests; payment and contact happen directly between the two (WhatsApp hand-off, no commission). "
    "<b>Value proposition:</b> owners fill idle hours with zero commission for a flat $4.49–$8.99/month; professionals find verified, "
    "price-transparent rooms without brokers. <b>Differentiators found in the code:</b> per-attendee and shift pricing, conflict-guarded "
    "dated booking, verification + report/block, Arabic/RTL first-class, an AI Instagram/WhatsApp agent that converts comments into leads, "
    "SEO landing pages, and one Kotlin Multiplatform codebase (Android, iOS, web admin). <b>Journey:</b> discover → request → host accepts → "
    "pay host directly → re-book; hosts: sign up → Premium → publish → manage requests/calendar/price changes. "
    "<b>Key assumptions:</b> owners will pay a flat fee for demand + tools; enough specialists search in-app instead of social groups; "
    "MENA-first launch, open globally. <b>Traction (22 Nov 2026):</b> 92 sign-ups, 58 profiles, 2 live listings, 11 bookings, 1 legacy "
    "paid subscription — <b>pre-product-market-fit</b>.")]

personas = [["Role", "Purpose / benefits", "Audience & geography", "Money", "Main challenges"],
            [P("<b>Specialist</b> (renter)"), P("Find and book rooms by hour/shift/day/month; saved rooms, My Rentals, reminders; free."),
             P("Therapists, psychologists, doctors, trainers, coaches, tutors, freelancers 25–45; urban MENA first (Lebanon, Gulf, Levant), "
               "Arabic + English."), P("No direct revenue today; drives host value (demand). Low CAC via SEO/Instagram."),
             P("Acquisition: habit of Facebook/WhatsApp groups. Retention: episodic need, off-platform deals after first contact.")],
            [P("<b>Pro Host</b> (owner)"), P("Publish listings, divisions, dated availability, change price, analytics, request inbox; "
                                            "Premium required to publish."),
             P("Clinic/center owners, coworking operators, therapists subletting own room, studios; SMB, price-sensitive, mobile-first."),
             P(f"100% of revenue today: net ARPU {money(NET_ARPU, 2)}/mo after store fee. Upsell: featured, multi-listing, enterprise, payments."),
             P("Needs proof of bookings before paying; churn if idle hours stay idle. Scales per city via density.")],
            [P("<b>Admin</b>"), P("Web console: verification, billing rescue, reports queue, schema, analytics."), P("Internal team."),
             P("Cost centre (support, trust &amp; safety)."), P("Moderation load grows with listings.")]]
story += [P("User personas", H2), tbl(personas, [W * 0.11, W * 0.22, W * 0.25, W * 0.21, W * 0.21], font=6.4)]

story += [P("Market opportunity", H2)]
mkt = P(f"<b>TAM</b> — global flexible workspace ≈ <b>{money(TAM)}</b> (2025; published estimates $45–55B, CAGR 9–16%). "
        f"<b>SAM</b> — hourly professional rooms (clinic, therapy, meeting, training, studio) in MENA + Arabic-speaking and emerging "
        f"English markets ≈ <b>{money(SAM)}</b> of bookings value. <b>SOM</b> (5 years) — bookings value routed through ProHost ≈ "
        f"<b>{money(SOM)}</b>/yr, i.e. ~1.2% of SAM. Growth drivers: hybrid work, rising independent practitioners (mental-health and "
        f"allied-health demand), clinic owners monetising idle chairs/rooms. <b>Competitors:</b> Peerspace (≈20% host fee, events/creative, "
        f"US), LiquidSpace (commission, corporate), Regus/IWG and WeWork On Demand (own inventory, premium price), Tagvenue/Spacebase "
        f"(venue commissions, Europe), Deskpass (member subscriptions); <b>substitutes</b>: Facebook/WhatsApp groups, classifieds, word of "
        f"mouth (free, unverified, no calendar). None targets MENA professional rooms with Arabic UX and a flat 0%-commission fee: a real "
        f"niche, but small and fragmented. <b>PMF potential: plausible in dense cities</b>; must be proven with repeat bookings per host.", B)
story += [Table([[mkt, img(C6, 52 * mm, 40 * mm), img(C7, 62 * mm, 45 * mm)]], colWidths=[W - 118 * mm, 54 * mm, 64 * mm],
                style=[("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0)])]
comp = [["Competitor", "Model", "Strength", "Weakness", "Pricing", "Share*"],
        ["Peerspace", "Marketplace", "Brand, events demand", "US-centric, high fee", "~20% host fee", "Leader, US"],
        ["LiquidSpace", "Marketplace", "Enterprise B2B", "Corporate focus", "Commission", "Mid"],
        ["Regus / IWG", "Operator", "Global inventory", "Expensive, rigid", "Own price list", "Largest operator"],
        ["Tagvenue / Spacebase", "Marketplace", "Venues, EU", "Events focus", "Commission", "Regional"],
        ["Social groups", "Substitute", "Free, habitual", "No trust/tools", "Free", "Dominant in MENA"],
        ["ProHost", "Flat SaaS marketplace", "0% fee, Arabic, pro rooms", "No demand yet", "$4.49–$8.99/mo", "<0.1%"]]
story += [tbl(comp, [W * 0.17, W * 0.16, W * 0.19, W * 0.17, W * 0.15, W * 0.16], font=6.2),
          P("*Qualitative estimates; no audited share data exists for this niche.", S), PageBreak()]

# ---------------- PAGE 2
story += [P("Revenue model", H2)]
rev_streams = [["Stream", "In code today", "Y5 share", "Scalability", "Investor appeal", "Gross margin"],
               ["Subscriptions (Premium: 1 listing / Unlimited)", "Yes (Play + App Store)", f"{mix_vals[0] / sum(mix_vals):.0%}", "7/10", "Medium", "≈82%"],
               ["Featured / sponsored listings", "No (proposed Y2)", f"{mix_vals[1] / sum(mix_vals):.0%}", "6/10", "Medium", "≈85%"],
               ["Booking / transaction fee (in-app pay)", "No (proposed Y3)", f"{mix_vals[2] / sum(mix_vals):.0%}", "9/10", "High", "≈40% of fee"],
               ["Enterprise packages (clinic chains, coworking)", "No (proposed Y3)", f"{mix_vals[3] / sum(mix_vals):.0%}", "6/10", "High", "≈90%"],
               ["Commission / marketplace fee", "Deliberately 0%", "0%", "—", "—", "—"],
               ["Advertising, partnerships, API", "No", "0% (upside)", "4/10", "Low", "high"]]
story += [tbl(rev_streams, [W * 0.34, W * 0.17, W * 0.09, W * 0.11, W * 0.13, W * 0.16], font=6.4),
          P(f"<b>Funnel:</b> visitor (SEO, Instagram agent, referral) → sign-up → specialist browsing → booking request → host accepts "
            f"→ repeat booking; supply side: owner lead → listing draft → <b>Premium paywall at publish</b> (gross ARPU "
            f"{money(gross_arpu, 2)}/mo, net {money(NET_ARPU, 2)} after 15% store fee) → featured upgrade → enterprise. The paywall at "
            f"publish is the single monetisation point today; marketplace liquidity, not price, is the constraint.", B)]

story += [P("Go-to-market", H2), P(
    "<b>Phase 1 — Launch (0–6 m):</b> one city cluster (Beirut + 2 Gulf cities); founder-led sales to 100 clinic/coworking owners; "
    "free first month; Instagram AI agent + comment-to-DM funnels; 8 SEO landing pages live. <b>Phase 2 — Validation (6–18 m):</b> "
    "target ≥30% of hosts with a booking/month and ≥40% 3-month host retention; referral credit (host invites host); therapist and "
    "medical associations as partners. <b>Phase 3 — Expansion (18–36 m):</b> Gulf + Levant + Egypt, featured listings, in-app payments, "
    "clinic-chain packages. <b>Phase 4 — Scale (36 m+):</b> English emerging markets, API/partner integrations, paid acquisition only "
    "where LTV/CAC &gt; 3. <b>Most cost-efficient channel:</b> SEO landing pages + the existing Instagram/WhatsApp AI agent (near-zero "
    "marginal cost, already built); paid ads only for hosts in dense cities. Influencers: practitioners with audiences (psychologists, "
    "trainers), paid per booked host.")]

story += [P("Growth &amp; financial forecasts", H2)]
ug = [["Scenario", "Y1 users", "Y3 users", "Y5 users", "Y5 paying hosts", "Y5 MAU", "Y5 revenue", "Y5 EBITDA"]]
for n, rows in MODEL.items():
    u = yearly(rows, "users", "last")
    ug.append([n, f"{u[0]:,.0f}", f"{u[2]:,.0f}", f"{u[4]:,.0f}", f"{yearly(rows, 'hosts', 'last')[4]:,.0f}",
               f"{yearly(rows, 'mau', 'last')[4]:,.0f}", money(yearly(rows, 'revenue')[4], 1), money(yearly(rows, 'ebitda')[4], 1)])
story += [Table([[img(C4, 62 * mm, 40 * mm), img(C1, 62 * mm, 40 * mm), img(C5, 52 * mm, 40 * mm)]],
                colWidths=[64 * mm, 64 * mm, W - 128 * mm], style=[("LEFTPADDING", (0, 0), (-1, -1), 0)]),
          tbl(ug, [W * 0.14] + [W * 0.123] * 7, font=6.4),
          P(f"Retention: host monthly churn {HOST_CHURN[0]:.1%} (Y1) → {HOST_CHURN[2]:.1%} (Y3+); specialist inactivity churn "
            f"{SPEC_CHURN[0]:.0%} → {SPEC_CHURN[4]:.0%}/month; MAU = {MAU_SHARE:.0%} of registered users.", S)]

fin = [["Expected (USD)"] + yrs]
for label, key in [("Revenue", "revenue"), ("  Subscriptions", "subs"), ("  Featured", "featured"), ("  Booking fee", "booking"),
                   ("  Enterprise", "enterprise"), ("Cost of revenue", "cogs"), ("Acquisition", "marketing"), ("Team", "team"),
                   ("Fixed other", "other"), ("Total cost", "cost"), ("EBITDA", "ebitda")]:
    fin.append([label] + [money(v, 0) for v in yearly(EXP, key)])
fin.append(["Gross margin"] + [f"{g:.0%}" for g in GROSS_MARGIN])
fin.append(["Contribution margin"] + [f"{c:.0%}" for c in contrib])
fin.append(["Cash position (cum.)"] + [money(v) for v in yearly(EXP, "cash", "last")])
unit = [["Unit economics (paying host)", "Value"],
        ["Gross ARPU / month", money(gross_arpu, 2)], ["Net ARPU after store fee", money(NET_ARPU, 2)],
        ["Host gross margin", f"{gm_host:.0%}"], ["Monthly churn (Y3)", f"{churn3:.1%}"],
        ["LTV (margin / churn)", money(LTV)], ["CAC (host)", money(CAC)], ["LTV / CAC", f"{LTV / CAC:.1f}x"],
        ["CAC payback", f"{PAYBACK:.1f} months"], ["Break-even (Expected)", f"month {be_month}" if be_month else "not within 5 years"],
        ["Peak funding need", money(-min_cash)]]
story += [Table([[tbl(fin, [W * 0.17] + [W * 0.085] * 5, font=6.2), tbl(unit, [W * 0.2, W * 0.12], font=6.2)]],
                colWidths=[W * 0.62, W * 0.36], style=[("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0)]),
          PageBreak()]

# ---------------- PAGE 3
story += [P("Revenue vs cost", H2), img(C3, W, W * 2.4 / 7.4),
          Table([[img(C2, 62 * mm, 40 * mm), P(
              (f"<b>Break-even:</b> the Expected case does <b>not</b> reach EBITDA break-even within 5 years (Y5 EBITDA "
               f"{money(y5e[4], 1)} on {money(y5['Expected'], 1)} revenue); " if not be_month else
               f"<b>Break-even:</b> Expected case turns EBITDA-positive for good in <b>month {be_month}</b>; ")
              + f"on a run-rate basis revenue overtakes cost at about <b>{BE_USERS / 1e3:.0f}k registered users</b> "
              f"(≈{BE_USERS * 0.06 / 1e3:.0f}k paying hosts). Aggressive case: "
              + (f"break-even in month {be_of(MODEL['Aggressive'])}. " if be_of(MODEL['Aggressive']) else "no break-even in 5 years. ")
              + f"Peak cumulative burn (Expected) <b>{money(-min_cash)}</b>. <b>Milestones:</b> each paying host is contribution-positive "
              f"from month one (store fee + support &lt; ARPU), but acquisition (CAC {money(CAC)}, payback {PAYBACK:.0f} months) and a growing "
              f"team outpace a {money(NET_ARPU, 2)} net ARPU: subscriptions alone cannot carry the company; the booking fee and "
              f"enterprise packages are what close the gap. "
              f"Conservative stays loss-making at Y5 ({money(yearly(MODEL['Conservative'], 'ebitda')[4], 1)} EBITDA) — subscriptions alone "
              f"cannot carry a team. Cost per 1k / 10k / 100k / 1M users (monthly run-rate): "
              + ", ".join(f"{money(run_rate(u)[1])}" for u in (1_000, 10_000, 100_000, 1_000_000)) + ".", B)]],
                colWidths=[64 * mm, W - 64 * mm], style=[("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0)])]

risks = [["Risk", "Type", "Prob.", "Severity", "Mitigation"],
         ["Cold start: too few listings/requests per city", "Market", "High", "High", "One city at a time; founder sales; free first month; seed listings"],
         ["Disintermediation via WhatsApp after first contact", "Product", "High", "Medium", "Flat fee (no commission to avoid); value in calendar, reminders, reviews"],
         ["Low willingness to pay of MENA SMB owners", "Market", "Medium", "High", "1-listing tier $4.49; prove bookings before paywall; annual discounts"],
         ["Store policy / fee changes (15–30%)", "Regulatory", "Low", "Medium", "Web checkout where allowed; keep pricing parity"],
         ["Medical-space licensing / liability", "Regulatory", "Medium", "Medium", "Verification docs, terms, host responsibility clauses"],
         ["Big player enters MENA (Peerspace, Regus app)", "Competitive", "Low", "High", "Arabic UX, local trust, price"],
         ["KMP migration / two-platform complexity", "Technical", "Medium", "Medium", "Parity gates, shared code, CI already in place"],
         ["AI agent misstatements / platform policy", "AI", "Medium", "Medium", "Disclosure, live-offer-only quoting, kill switch, human handoff"],
         ["Lebanon macro / payments instability", "Market", "High", "Medium", "Multi-country launch, USD pricing via stores"]]
story += [P("Risk matrix", H2), tbl(risks, [W * 0.33, W * 0.1, W * 0.07, W * 0.08, W * 0.42], font=6.2)]

scores = [("Market opportunity", 6), ("Product differentiation", 6), ("Scalability", 6), ("Revenue potential", 4),
          ("Defensibility", 3), ("Profitability", 5), ("Founder-market fit (from product)", 7)]
overall = sum(s for _, s in scores) / len(scores)
story += [P("Investor assessment", H2),
          Table([[tbl([["Criterion", "Score /10"]] + [[a, str(b)] for a, b in scores] + [["Venture potential", f"{overall:.1f}"]],
                      [W * 0.27, W * 0.08], font=6.4),
                  P(f"<b>Would a VC invest today? Not yet.</b> The product is unusually complete for its stage (two native-quality apps, web "
                    f"admin, billing on both stores, AI acquisition agent, SEO, trust &amp; safety), and founder execution is evident. But "
                    f"traction is pre-PMF (2 listings), the revenue model is a low-ticket SaaS fee (net {money(NET_ARPU, 2)}/host/month) on a "
                    f"fragmented SMB base, and defensibility is limited (no network lock-in while payments happen off-platform). Expected Y5 "
                    f"revenue {money(y5['Expected'], 1)} is angel/seed scale, not venture scale. <b>To become investable:</b> (1) prove liquidity "
                    f"in one city: ≥150 paying hosts, ≥30% with monthly bookings, &gt;40% 3-month retention; (2) add in-app payment with a "
                    f"small fee or host-paid guarantee to capture GMV and data; (3) B2B packages for clinic chains/coworking groups "
                    f"(higher ARPU, lower churn); (4) reviews + calendar sync as switching costs; (5) a repeatable acquisition cost below "
                    f"$40 per paying host.", B)]],
                colWidths=[W * 0.37, W * 0.63], style=[("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0)])]
story += [P("Should this business be built and scaled?", H2), P(
    f"<b>Build — yes, lean; scale with venture money — not yet.</b> The product is built, infrastructure is serverless and cheap, "
    f"growth tools (SEO, AI Instagram agent) are already paid for, and each paying host is contribution-positive from month one. "
    f"But on today's model (flat {money(gross_arpu, 2)}/month, no transaction capture) the Expected case needs about "
    f"{money(-min_cash)} of funding and does not break even within 5 years; break-even needs roughly {BE_USERS / 1e3:.0f}k users. "
    f"Keep the team minimal until one city proves liquidity. The opportunity becomes "
    f"venture-scale only if ProHost proves city-level liquidity and moves from a flat listing fee to capturing transactions and B2B "
    f"accounts (Aggressive case: {money(y5['Aggressive'], 1)} revenue in Y5). Recommendation: run a 6-month single-city validation "
    f"against the metrics above, then raise a seed round on evidence rather than on the product alone.", B)]

# ---------------- APPENDIX: monthly table + assumptions
story += [PageBreak(), P("Appendix — Expected case, months 1–24, and model assumptions", H2)]
mt = [["M", "Users", "Spec.", "Paying hosts", "MAU", "Subs", "Other rev.", "Revenue", "COGS", "Acquisition", "Team+fixed", "Total cost", "EBITDA", "Cash"]]
for r in EXP[:24]:
    mt.append([r["month"], f"{r['users']:,.0f}", f"{r['specialists']:,.0f}", f"{r['hosts']:,.0f}", f"{r['mau']:,.0f}", money(r["subs"]),
               money(r["featured"] + r["booking"] + r["enterprise"]), money(r["revenue"]), money(r["cogs"]), money(r["marketing"]),
               money(r["team"] + r["other"]), money(r["cost"]), money(r["ebitda"]), money(r["cash"])])
story += [tbl(mt, [W * 0.04] + [W * 0.0738] * 13, font=5.9)]
cg = [["Users", "Team", "Legal/fixed", "Infra + AI", "Support", "Acquisition", "Fees", "Total / month", "Revenue / month"]]
for u in USER_POINTS:
    rv, c, d = run_rate(u)
    cg.append([f"{u:,}", money(d["team"]), money(d["other"]), money(d["infra"]), money(d["support"]), money(d["marketing"]), money(d["fees"]),
               money(c), money(rv)])
story += [P("Cost growth model (monthly run-rate by registered users, Expected ratios)", H2), tbl(cg, [W * 0.1] + [W * 0.1125] * 8, font=6.2)]
assump = (f"Prices = live store prices (1 listing $4.49/mo or $44.99/yr; Unlimited $8.99/mo or $79.99/yr; Unlimited monthly has 1 "
          f"month free, yearly 50% off year one). Mix: {MIX['single']:.0%} of hosts on 1 listing, {MIX['yearly_share']:.0%} yearly; "
          f"free-month drag {FREE_MONTH_DRAG:.0%}; store fee {STORE_FEE:.0%}. Paying-host share of users "
          f"{SCENARIOS['Expected']['host_share'][0]:.1%}→{SCENARIOS['Expected']['host_share'][1]:.1%} (Expected). Featured listings from "
          f"month {FEATURED['start']}: {FEATURED['adoption']:.0%} of hosts × ${FEATURED['price']:.0f}. Booking fee from month "
          f"{BOOKING['start']}: {BOOKING['take']:.0%} of in-app GMV (in-app share {BOOKING['in_app_share'][0]:.0%}→"
          f"{BOOKING['in_app_share'][1]:.0%} of ${BOOKING['gmv_per_host']:.0f}/host/month), processing {BOOKING['processing']:.1%}. "
          f"Enterprise: 1 package (${ENTERPRISE['price']:.0f}/mo) per {ENTERPRISE['per_hosts']} hosts from month {ENTERPRISE['start']}. "
          f"Costs: team ${TEAM[0]:,}/mo (Y1) → ${TEAM[4]:,}/mo (Y5) × scenario multiplier; other fixed ${FIXED_OTHER[0]:,}→"
          f"${FIXED_OTHER[4]:,}; infra ${INFRA_BASE} + ${INFRA_PER_MAU}/MAU + AI ${AI_PER_USER}/user; support ${SUPPORT_PER_MAU}/MAU + "
          f"${SUPPORT_PER_HOST:.0f}/host; CAC ${CAC}/paying host (Expected), ${SPEC_CAC}/specialist. Market sizes: published flexible-"
          f"workspace estimates (Grand View, Fortune Business Insights, Reports and Data, 2025); SAM/SOM are analyst estimates. "
          f"Traction: Maintenance prodcheck, 22 Nov 2026. This is a model, not a guarantee.")
story += [P("Assumptions", H2), P(assump, S)]


def footer(c, d):
    c.saveState()
    c.setFont("Helvetica", 6.5)
    c.setFillColor(colors.HexColor(GREY))
    c.drawString(12 * mm, 7 * mm, "ProHost — investment & business analysis · confidential · generated from scripts/business/investment_report.py")
    c.drawRightString(A4[0] - 12 * mm, 7 * mm, f"page {d.page}")
    c.setFillColor(colors.HexColor(ORANGE))
    c.rect(12 * mm, A4[1] - 8 * mm, A4[0] - 24 * mm, 1.2, fill=1, stroke=0)
    c.restoreState()


pdf = os.path.join(OUT, "ProHost_Investment_Analysis.pdf")
SimpleDocTemplate(pdf, pagesize=A4, leftMargin=12 * mm, rightMargin=12 * mm, topMargin=11 * mm, bottomMargin=11 * mm,
                  title="ProHost — Investment & Business Analysis", author="ProHost").build(story, onFirstPage=footer, onLaterPages=footer)

with open(os.path.join(OUT, "forecast_model.csv"), "w", newline="") as f:
    w = csv.writer(f)
    keys = list(EXP[0].keys())
    w.writerow(["scenario"] + keys)
    for n, rows in MODEL.items():
        for r in rows:
            w.writerow([n] + [round(r[k], 2) if isinstance(r[k], float) else r[k] for k in keys])

print(f"PDF {pdf}")
print(f"gross ARPU {gross_arpu:.2f} net {NET_ARPU:.2f} LTV {LTV:.0f} CAC {CAC} LTV/CAC {LTV / CAC:.2f} payback {PAYBACK:.1f}m")
for n, rows in MODEL.items():
    print(n, "rev", [round(v) for v in yearly(rows, "revenue")], "ebitda", [round(v) for v in yearly(rows, "ebitda")],
          "cash min", round(min(r["cash"] for r in rows)))
print("break-even month", be_month, "users", BE_USERS, "GM", [f"{g:.2f}" for g in GROSS_MARGIN])
