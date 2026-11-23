"""ProHost strategic business plan: budget-driven forecast model + 3-page PDF (plus a 1-page appendix).

    python3 scripts/business/investment_report.py   -> docs/business/ProHost_Business_Plan.pdf + forecast_model.csv

Operating model (owner, 23 Nov 2026): a small contract tech team keeps the apps running; marketing is outsourced to an agency,
starting at $1,000/month and growing only with revenue (a fixed share of last month's revenue). Users are therefore NOT fixed
targets: paid growth = agency budget / acquisition cost, organic growth = SEO + the Instagram/WhatsApp AI agent per live market.
Pricing: 1 listing $4.49 / $44.99 (unchanged); Unlimited doubled to $17.99 / $159.99 (offers kept: Unlimited monthly 1 month free,
yearly 50% off year one). Every chart, table and sentence with a number is computed from the ASSUMPTIONS below.
"""
import csv
import os

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402
from matplotlib.patches import Circle  # noqa: E402
from reportlab.lib import colors  # noqa: E402
from reportlab.lib.pagesizes import A4  # noqa: E402
from reportlab.lib.styles import ParagraphStyle  # noqa: E402
from reportlab.lib.units import mm  # noqa: E402
from reportlab.platypus import Image, PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle  # noqa: E402

ROOT = __file__.rsplit("/", 3)[0]
OUT = os.path.join(ROOT, "docs/business")
TMP = os.path.join(OUT, "charts")
os.makedirs(TMP, exist_ok=True)
NAVY, ORANGE, STEEL, GREY, GREEN, RED = "#001f4e", "#fd631d", "#2B5A8C", "#8a94a6", "#2e8b57", "#c0392b"

# ----------------------------------------------------------------------------------------------------------------------
# ASSUMPTIONS
# ----------------------------------------------------------------------------------------------------------------------
PRICES = {"single_m": 4.49, "single_y": 44.99, "unl_m": 17.99, "unl_y": 159.99}
SINGLE_SHARE, YEARLY_SHARE = 0.65, 0.30      # with Unlimited at 2x, most small owners pick 1 listing; 30% pay yearly
FREE_MONTH_DRAG = 0.05                        # Unlimited monthly: first month free (new subscribers)
STORE_FEE = 0.15                              # Apple / Google small-business rate
_single = (1 - YEARLY_SHARE) * PRICES["single_m"] + YEARLY_SHARE * PRICES["single_y"] / 12
_unl = ((1 - YEARLY_SHARE) * PRICES["unl_m"] + YEARLY_SHARE * PRICES["unl_y"] / 12) * (1 - FREE_MONTH_DRAG)
GROSS_ARPU = SINGLE_SHARE * _single + (1 - SINGLE_SHARE) * _unl
NET_ARPU = GROSS_ARPU * (1 - STORE_FEE)

# Staged expansion: (start month, stage, markets, why)
STAGES = [
    (1, "1 · Prove", "Lebanon (Beirut, Mount Lebanon)",
     "Home market and founder network; dense private clinics and therapy practices that sublet rooms; Arabic + English; "
     "proves the playbook cheaply."),
    (7, "2 · Monetise", "UAE (Dubai, Abu Dhabi) + Saudi Arabia (Riyadh, Jeddah)",
     "Highest purchasing power in the region, very high clinic and office rents (owners want to fill idle hours), "
     "large licensed-practitioner base, strong app-store spending."),
    (19, "3 · Scale volume", "Egypt, Jordan, Iraq (Kurdistan), Kuwait, Qatar",
     "Large practitioner populations and fast-growing private health/coaching sectors; 1-listing tier fits price "
     "sensitivity; same language and product."),
    (31, "4 · Extend", "Oman, Bahrain, Morocco, Tunisia + Arabic diaspora cities (London, Paris, Montreal)",
     "Completes MENA; North Africa needs French (one localisation); diaspora professionals already search in Arabic."),
]
SCEN = {
    #            share of last month's revenue to the agency, host CAC, specialist CAC, organic specialists / market / month,
    #            team multiplier, host churn reduction
    "Conservative": {"mkt_share": 0.25, "cac_h": 60, "cac_s": 2.5, "org": 90, "org_h": 4, "team": 0.85, "launch": 1500},
    "Expected": {"mkt_share": 0.30, "cac_h": 45, "cac_s": 2.0, "org": 160, "org_h": 8, "team": 1.00, "launch": 2500},
    "Aggressive": {"mkt_share": 0.40, "cac_h": 35, "cac_s": 1.5, "org": 260, "org_h": 14, "team": 1.25, "launch": 4000},
}
MKT_FLOOR = 1_000                             # agency budget floor, month 1 onward
PAID_HOST_SPLIT = 0.6                         # of the agency budget spent on owners (rest on specialists)
MARKETS_PER_STAGE = [1, 2, 5, 7]              # countries live after each stage
ORGANIC_RAMP = 0.04                           # organic intake grows 4%/month of market age (SEO, reviews), capped at 3x
HOST_FROM_DEMAND = 0.006                      # organic owners per new specialist (demand attracts supply)
HOST_REFERRAL = 0.01                          # monthly referrals per paying host
HOST_CHURN = [0.065, 0.055, 0.045, 0.040, 0.040]
MAU_SHARE = 0.35
START_SPECIALISTS, START_HOSTS = 90, 2         # prodcheck 22 Nov 2026 (92 sign-ups, 2 live listings)
TEAM = [2_500, 3_000, 3_500, 4_000, 4_500]     # minimum contract tech team (dev + part-time QA/support), by year
TEAM_SHARE = 0.25                               # the team grows only as a share of revenue above that minimum
FIXED = [400, 500, 700, 900, 1_100]            # legal, accounting, developer accounts, tools
INFRA_BASE, INFRA_PER_MAU, AI_PER_USER = 150, 0.03, 0.003
SUPPORT_PER_HOST = 0.5                          # mostly covered by the team; variable part per paying host
FEATURED = {"start": 13, "adoption": 0.06, "price": 9.99}       # featured listing upgrade (stage 2+)
ENTERPRISE = {"start": 25, "per_hosts": 300, "price": 149.0}     # clinic chains / coworking groups (stage 3+)
BOOKING_UPSIDE = {"take": 0.04, "in_app": 0.2, "gmv_per_host": 650}  # shown as upside only, not in the base case


def stage_at(m):
    return max(i for i, s in enumerate(STAGES) if m >= s[0])


def run(name):
    sc = SCEN[name]
    specs, hosts, prev_rev, cash = START_SPECIALISTS, START_HOSTS, 0.0, 0.0
    rows = []
    for m in range(1, 61):
        y = min((m - 1) // 12, 4)
        st = stage_at(m)
        launch = sc["launch"] if any(m == s[0] for s in STAGES[1:]) else 0
        budget = max(MKT_FLOOR, sc["mkt_share"] * prev_rev) + launch
        # organic intake per live market, each market ramping with its own age
        organic, organic_h = 0.0, 0.0
        for i, s in enumerate(STAGES[: st + 1]):
            n_new = MARKETS_PER_STAGE[i] - (MARKETS_PER_STAGE[i - 1] if i else 0)
            ramp = min(1 + ORGANIC_RAMP * (m - s[0]), 3.0)
            organic += n_new * sc["org"] * ramp
            organic_h += n_new * sc["org_h"] * ramp
        new_specs = organic + budget * (1 - PAID_HOST_SPLIT) / sc["cac_s"]
        new_hosts = budget * PAID_HOST_SPLIT / sc["cac_h"] + organic_h + HOST_FROM_DEMAND * new_specs + HOST_REFERRAL * hosts
        churned = hosts * HOST_CHURN[y]
        hosts = hosts + new_hosts - churned
        specs += new_specs
        users = specs + hosts
        mau = users * MAU_SHARE
        subs = hosts * GROSS_ARPU
        featured = hosts * FEATURED["adoption"] * FEATURED["price"] if m >= FEATURED["start"] else 0
        enterprise = hosts / ENTERPRISE["per_hosts"] * ENTERPRISE["price"] if m >= ENTERPRISE["start"] else 0
        revenue = subs + featured + enterprise
        fees = (subs + featured) * STORE_FEE
        infra = INFRA_BASE + mau * INFRA_PER_MAU + users * AI_PER_USER
        support = hosts * SUPPORT_PER_HOST
        team = max(TEAM[y] * sc["team"], TEAM_SHARE * prev_rev)
        fixed = FIXED[y]
        cogs = fees + infra + support
        cost = cogs + budget + team + fixed
        ebitda = revenue - cost
        cash += ebitda
        upside = hosts * BOOKING_UPSIDE["gmv_per_host"] * BOOKING_UPSIDE["in_app"] * BOOKING_UPSIDE["take"] if m >= 25 else 0
        rows.append(dict(month=m, year=y + 1, stage=st + 1, users=users, specialists=specs, hosts=hosts, mau=mau,
                         new_specialists=new_specs, new_hosts=new_hosts, churned_hosts=churned, subs=subs, featured=featured,
                         enterprise=enterprise, revenue=revenue, store_fees=fees, infra=infra, support=support, marketing=budget,
                         team=team, fixed=fixed, cogs=cogs, cost=cost, ebitda=ebitda, cash=cash, booking_upside=upside))
        prev_rev = revenue
    return rows


MODEL = {n: run(n) for n in SCEN}
EXP = MODEL["Expected"]


def yearly(rows, key, how="sum"):
    out = []
    for y in range(1, 6):
        v = [r[key] for r in rows if r["year"] == y]
        out.append(sum(v) if how == "sum" else v[-1])
    return out


def be_of(rows):
    return next((r["month"] for r in rows if r["ebitda"] > 0 and all(x["ebitda"] > 0 for x in rows if x["month"] >= r["month"])), None)


def money(v, dec=0):
    s = "-" if v < 0 else ""
    v = abs(v)
    if v >= 1e6:
        return f"{s}${v / 1e6:.{max(dec, 1)}f}M"
    if v >= 1e3:
        return f"{s}${v / 1e3:.{dec}f}k"
    return f"{s}${v:.0f}"


BE = {n: be_of(r) for n, r in MODEL.items()}
MINCASH = {n: min(r["cash"] for r in rows) for n, rows in MODEL.items()}
REV = {n: yearly(r, "revenue") for n, r in MODEL.items()}
EBITDA = {n: yearly(r, "ebitda") for n, r in MODEL.items()}
GM = [(a - b) / a if a else 0 for a, b in zip(yearly(EXP, "revenue"), yearly(EXP, "cogs"))]
CM = [(a - b - c) / a if a else 0 for a, b, c in zip(yearly(EXP, "revenue"), yearly(EXP, "cogs"), yearly(EXP, "marketing"))]
host_margin = GROSS_ARPU * (1 - STORE_FEE) - SUPPORT_PER_HOST
LTV = host_margin / HOST_CHURN[2]
CAC = SCEN["Expected"]["cac_h"]
PAYBACK = CAC / host_margin

# Run-rate cost / revenue by registered users (Expected ratios: paying hosts = Y5 share of users)
HOST_SHARE = EXP[-1]["hosts"] / EXP[-1]["users"]


def run_rate(users):
    hosts = users * HOST_SHARE
    mau = users * MAU_SHARE
    rev = hosts * GROSS_ARPU + hosts * FEATURED["adoption"] * FEATURED["price"] + hosts / ENTERPRISE["per_hosts"] * ENTERPRISE["price"]
    team = max(TEAM[-1], TEAM_SHARE * rev)
    fixed = FIXED[0] if users < 10_000 else FIXED[2] if users < 120_000 else FIXED[4] if users < 700_000 else 2_000
    infra = INFRA_BASE + mau * INFRA_PER_MAU + users * AI_PER_USER
    mkt = max(MKT_FLOOR, SCEN["Expected"]["mkt_share"] * rev)
    fees = (hosts * GROSS_ARPU + hosts * FEATURED["adoption"] * FEATURED["price"]) * STORE_FEE
    support = hosts * SUPPORT_PER_HOST
    cost = team + fixed + infra + mkt + fees + support
    return rev, cost, dict(team=team, fixed=fixed, infra=infra, support=support, marketing=mkt, fees=fees)


GRID = [int(10 ** (3 + 3 * i / 120)) for i in range(121)]
RR = [run_rate(u) for u in GRID]
BE_USERS = next((u for i, u in enumerate(GRID) if all(r[0] >= r[1] for r in RR[i:])), None)
USER_POINTS = [1_000, 10_000, 50_000, 100_000, 250_000, 500_000, 1_000_000]

# ----------------------------------------------------------------------------------------------------------------------
# CHARTS
# ----------------------------------------------------------------------------------------------------------------------
plt.rcParams.update({"font.size": 8, "axes.titlesize": 9, "axes.titleweight": "bold", "axes.spines.top": False,
                     "axes.spines.right": False, "axes.edgecolor": "#9aa5b5", "axes.labelcolor": "#33415c"})
COL = {"Conservative": GREY, "Expected": STEEL, "Aggressive": ORANGE}
YRS = ["Y1", "Y2", "Y3", "Y4", "Y5"]


def save(fig, name):
    p = os.path.join(TMP, name)
    fig.tight_layout()
    fig.savefig(p, dpi=200)
    plt.close(fig)
    return p


fig, ax = plt.subplots(figsize=(3.6, 2.3))
for n in MODEL:
    ax.plot(YRS, [v / 1e3 for v in REV[n]], marker="o", color=COL[n], lw=1.8, label=n)
ax.set_title("1. Revenue by scenario ($k / year)")
ax.legend(frameon=False, fontsize=7)
ax.grid(axis="y", alpha=0.25)
C1 = save(fig, "c1.png")

fig, ax = plt.subplots(figsize=(3.6, 2.3))
bottom = [0] * 5
for key, label, col in [("team", "Tech team (contract)", NAVY), ("fixed", "Legal / fixed", "#5b6b85"),
                        ("marketing", "Marketing agency", ORANGE), ("store_fees", "Store fees", "#f2a17a"),
                        ("infra", "Cloud + AI agent", STEEL), ("support", "Support", "#9fb8d6")]:
    v = yearly(EXP, key)
    ax.bar(YRS, [x / 1e3 for x in v], bottom=[b / 1e3 for b in bottom], color=col, label=label, width=0.6)
    bottom = [b + x for b, x in zip(bottom, v)]
ax.set_title("2. Cost growth, Expected ($k / year)")
ax.legend(frameon=False, fontsize=6, ncol=2)
ax.grid(axis="y", alpha=0.25)
C2 = save(fig, "c2.png")

fig, (a1, a2) = plt.subplots(1, 2, figsize=(7.4, 2.4))
ms = [r["month"] for r in EXP]
a1.plot(ms, [r["revenue"] / 1e3 for r in EXP], color=GREEN, lw=1.8, label="Revenue")
a1.plot(ms, [r["cost"] / 1e3 for r in EXP], color=RED, lw=1.8, label="Cost")
a1.fill_between(ms, [r["ebitda"] / 1e3 for r in EXP], 0, color=STEEL, alpha=0.15, label="EBITDA")
for s in STAGES[1:]:
    a1.axvline(s[0], color="#c8d0dc", lw=0.8)
    a1.text(s[0] + 0.5, a1.get_ylim()[1] * 0.92, s[1].split(" ")[0], fontsize=6, color=GREY)
if BE["Expected"]:
    a1.axvline(BE["Expected"], color=NAVY, ls="--", lw=1)
    a1.text(BE["Expected"] + 1, a1.get_ylim()[1] * 0.6, f"break-even\nmonth {BE['Expected']}", fontsize=7, color=NAVY)
a1.set_title("3a. Revenue vs cost, Expected ($k / month)")
a1.legend(frameon=False, fontsize=7, loc="upper left")
a1.grid(alpha=0.25)
a2.plot(GRID, [r[0] / 1e3 for r in RR], color=GREEN, lw=1.8, label="Revenue run-rate")
a2.plot(GRID, [r[1] / 1e3 for r in RR], color=RED, lw=1.8, label="Cost run-rate")
a2.set_xscale("log")
if BE_USERS:
    a2.axvline(BE_USERS, color=NAVY, ls="--", lw=1)
    a2.text(BE_USERS * 1.15, a2.get_ylim()[1] * 0.75, f"break-even\n~{BE_USERS / 1e3:.0f}k users", fontsize=7, color=NAVY)
a2.set_title("3b. Revenue vs cost by registered users ($k / month)")
a2.legend(frameon=False, fontsize=7)
a2.grid(alpha=0.25)
C3 = save(fig, "c3.png")

fig, ax = plt.subplots(figsize=(3.6, 2.3))
for n, rows in MODEL.items():
    ax.plot(YRS, [v / 1e3 for v in yearly(rows, "users", "last")], marker="o", color=COL[n], lw=1.8, label=f"{n} users")
    ax.plot(YRS, [v / 1e3 for v in yearly(rows, "hosts", "last")], ls="--", color=COL[n], lw=1, label=f"{n} paying hosts")
ax.set_yscale("log")
ax.set_title("4. User growth (thousands, log)")
ax.legend(frameon=False, fontsize=5.5, ncol=2)
ax.grid(alpha=0.25)
C4 = save(fig, "c4.png")

y5_subs_single = yearly(EXP, "subs")[4] * SINGLE_SHARE * _single / GROSS_ARPU
y5_subs_unl = yearly(EXP, "subs")[4] - y5_subs_single
MIX5 = [y5_subs_single, y5_subs_unl, yearly(EXP, "featured")[4], yearly(EXP, "enterprise")[4]]
fig, ax = plt.subplots(figsize=(3.0, 2.3))
ax.pie(MIX5, labels=["1 listing", "Unlimited", "Featured", "Enterprise"], autopct="%1.0f%%", startangle=90,
       colors=[STEEL, NAVY, ORANGE, "#9fb8d6"], textprops={"fontsize": 7}, wedgeprops={"linewidth": 1, "edgecolor": "white"})
ax.set_title("5. Revenue mix, Expected Y5")
C5 = save(fig, "c5.png")

TAM, SAM, SOM = 50e9, 2.1e9, 25e6
fig, ax = plt.subplots(figsize=(3.0, 2.3))
for r, col, lab, val, ty in [(1.0, "#dbe5f2", "TAM", TAM, 0.62), (0.58, "#9fb8d6", "SAM", SAM, -0.12), (0.22, ORANGE, "SOM", SOM, -0.78)]:
    ax.add_patch(Circle((0, r - 1), r, color=col))
    ax.text(0, ty, f"{lab}\n{money(val)}", ha="center", va="center", fontsize=7,
            color="white" if lab == "SOM" else NAVY, fontweight="bold")
ax.set_xlim(-1.05, 1.05)
ax.set_ylim(-1.05, 1.05)
ax.set_aspect("equal")
ax.axis("off")
ax.set_title("6. TAM / SAM / SOM (bookings value)")
C6 = save(fig, "c6.png")

COMP = [("Peerspace", 8.0, 2.5), ("LiquidSpace", 7.0, 4.5), ("Regus / IWG", 9.0, 5.0), ("WeWork On Demand", 7.5, 3.5),
        ("Tagvenue", 6.0, 2.0), ("Deskpass", 6.5, 4.0), ("FB / WhatsApp groups", 1.0, 5.5), ("ProHost", 2.5, 8.5)]
fig, ax = plt.subplots(figsize=(3.6, 2.6))
for name, x, y in COMP:
    me = name == "ProHost"
    ax.scatter(x, y, s=120 if me else 50, color=ORANGE if me else STEEL, zorder=3)
    ax.annotate(name, (x, y), textcoords="offset points", xytext=(5, 4), fontsize=6.5, color=ORANGE if me else NAVY,
                fontweight="bold" if me else "normal")
ax.set_xlim(0, 10.5)
ax.set_ylim(0, 10)
ax.axhline(5, color="#c8d0dc", lw=0.8)
ax.axvline(5, color="#c8d0dc", lw=0.8)
ax.set_xlabel("Cost to host (take rate / price) →", fontsize=7)
ax.set_ylabel("Focus on professional rooms →", fontsize=7)
ax.set_xticks([])
ax.set_yticks([])
ax.set_title("7. Competitive positioning")
C7 = save(fig, "c7.png")

# ----------------------------------------------------------------------------------------------------------------------
# PDF
# ----------------------------------------------------------------------------------------------------------------------
H1 = ParagraphStyle("h1", fontName="Helvetica-Bold", fontSize=15, textColor=colors.HexColor(NAVY), leading=18, spaceAfter=1)
H2 = ParagraphStyle("h2", fontName="Helvetica-Bold", fontSize=9.5, textColor=colors.HexColor(ORANGE), spaceBefore=4, spaceAfter=1.5, leading=11.5)
B = ParagraphStyle("b", fontName="Helvetica", fontSize=7.3, leading=9.2, textColor=colors.HexColor("#1c2738"))
S = ParagraphStyle("s", parent=B, fontSize=6.3, leading=7.7, textColor=colors.HexColor("#4a5568"))
P = lambda t, st=B: Paragraph(t, st)  # noqa: E731
W = A4[0] - 24 * mm


def tbl(data, widths, font=6.5):
    t = Table([[P(str(c), ParagraphStyle("c", parent=S, fontSize=font, leading=font + 1.6,
                                         fontName="Helvetica-Bold" if i == 0 else "Helvetica",
                                         textColor=colors.HexColor(NAVY if i == 0 else "#1c2738")))
                for c in row] for i, row in enumerate(data)], colWidths=widths)
    t.setStyle(TableStyle([("VALIGN", (0, 0), (-1, -1), "TOP"), ("TOPPADDING", (0, 0), (-1, -1), 1.1),
                           ("BOTTOMPADDING", (0, 0), (-1, -1), 1.1), ("LEFTPADDING", (0, 0), (-1, -1), 2.4),
                           ("RIGHTPADDING", (0, 0), (-1, -1), 2.4), ("LINEBELOW", (0, 0), (-1, 0), 0.6, colors.HexColor(NAVY)),
                           ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f2f5fa")])]))
    return t


def row(cells, widths):
    return Table([cells], colWidths=widths, style=[("VALIGN", (0, 0), (-1, -1), "TOP"), ("LEFTPADDING", (0, 0), (-1, -1), 0),
                                                   ("RIGHTPADDING", (0, 0), (-1, -1), 3)])


img = lambda p, w, h: Image(p, width=w, height=h)  # noqa: E731
e5 = EXP[-1]
story = [P("ProHost — Strategic Business Plan &amp; Investment Case", H1),
         P("Rooms by the hour for professionals · Android, iOS, web · 23 Nov 2026 · USD · model: "
           "<font face='Courier'>scripts/business/investment_report.py</font>", S), Spacer(1, 2)]

# ---------------- PAGE 1: summary, strategy, expansion, market
story += [P("Executive summary", H2), P(
    "<b>What ProHost is.</b> A marketplace app where <b>space owners (Pro Hosts)</b> rent clinic, therapy, meeting, training and studio "
    "rooms by the hour, shift, day or month, and <b>professionals (Specialists)</b> find them on a map, check dated availability and send "
    "requests. Payment and contact stay direct between the two (WhatsApp), so ProHost takes <b>0% commission</b>; owners pay a flat "
    f"Premium fee to publish: <b>1 listing ${PRICES['single_m']}/mo or ${PRICES['single_y']}/yr</b>, <b>Unlimited "
    f"${PRICES['unl_m']}/mo or ${PRICES['unl_y']}/yr</b> (planned 2x price; first month free / 50% off year one). Specialists use it free. "
    "<b>Why it can win:</b> owners of under-used rooms get demand and booking tools for less than one hour of rent per month; "
    "practitioners get verified, price-transparent rooms instead of Facebook groups. Already built: Android + iOS from one codebase, "
    "a web admin console, store billing on both platforms, verification + report/block, Arabic/RTL, SEO pages and an AI Instagram/"
    "WhatsApp agent that turns comments into leads. <b>Today:</b> 92 sign-ups, 2 live listings, 11 bookings (pre-traction).")]
story += [P("Strategy and operating model", H2), P(
    f"<b>Lean by design.</b> No hiring plan beyond a <b>small contract tech team</b> (one developer + part-time QA/support) that keeps "
    f"the apps, store billing and backend running: ${TEAM[0]:,}/month minimum in year 1 (${TEAM[4]:,} by year 5), growing above that "
    f"only as {TEAM_SHARE:.0%} of revenue. The founder runs partnerships, owner sales and quality. <b>Marketing is outsourced to an agency</b> with a budget of <b>${MKT_FLOOR:,}/month at launch</b> that grows only "
    f"with revenue (<b>{SCEN['Expected']['mkt_share']:.0%} of last month's revenue</b>, never below ${MKT_FLOOR:,}), plus a one-off "
    f"${SCEN['Expected']['launch']:,} launch push when a new stage opens. Growth is therefore self-funded and paced by paying "
    f"owners: every user number in this plan follows from that budget (paid) and from SEO + the AI agent per live market (organic).")]
stg = [["Stage", "When", "Where", "Why there", "Exit target before the next stage"]]
targets = ["≥80 paying owners, ≥30% with a booking each month",
           "≥400 paying owners, 3-month owner retention ≥45%",
           "≥2,000 paying owners, featured listings ≥5%",
           "≥4,000 paying owners, enterprise packages live"]
for (start, name, where, why), tgt in zip(STAGES, targets):
    stg.append([name, f"month {start}", where, why, tgt])
story += [P("Staged expansion — where and why", H2), tbl(stg, [W * 0.1, W * 0.08, W * 0.2, W * 0.4, W * 0.22], font=6.2)]
story += [P("Users and market", H2), row([P(
    "<b>Pro Host</b> — clinic and centre owners, therapists subletting their own room, coworking and training centres, studios; small "
    "businesses, mobile-first, price-sensitive; motivated to earn from idle hours. <b>100% of revenue.</b> "
    "<b>Specialist</b> — therapists, psychologists, doctors, dietitians, trainers, coaches, tutors (25–45, independent, urban); free; "
    "they are the demand that makes owners pay. Challenges: owners pay only after seeing requests; specialists move to WhatsApp after "
    "the first contact (fine for a 0%-commission model). "
    f"<b>Market:</b> flexible workspace ≈ {money(TAM)} (2025, CAGR 9–16%); hourly professional rooms in MENA + diaspora ≈ "
    f"{money(SAM)} of bookings (SAM); obtainable in 5 years ≈ {money(SOM)} (SOM). Competitors charge 15–20% commissions (Peerspace, "
    "LiquidSpace, Tagvenue) or own premium inventory (Regus, WeWork); the real rival is free social groups. Nobody serves MENA "
    "professional rooms in Arabic with a flat, commission-free fee: a defensible niche if density is reached city by city.", B),
    img(C6, 48 * mm, 37 * mm), img(C7, 58 * mm, 42 * mm)], [W - 108 * mm, 50 * mm, 58 * mm]), PageBreak()]

# ---------------- PAGE 2: revenue model, pricing, forecasts
story += [P("Revenue model and pricing", H2)]
price_tbl = [["Plan (both stores)", "Monthly", "Yearly", "Offer", "Expected mix", "Net to ProHost / mo*"],
             ["1 listing", f"${PRICES['single_m']}", f"${PRICES['single_y']}", "—", f"{SINGLE_SHARE:.0%}", money(_single * 0.85, 2)],
             ["Unlimited (2x)", f"${PRICES['unl_m']}", f"${PRICES['unl_y']}", "1st month free / 50% off year 1", f"{1 - SINGLE_SHARE:.0%}",
              money(_unl * 0.85, 2)],
             ["Blended", "", "", f"{YEARLY_SHARE:.0%} pay yearly", "", f"{money(NET_ARPU, 2)} (gross {money(GROSS_ARPU, 2)})"]]
streams = [["Stream", "Starts", "Y5 share", "Margin", "Note"],
           ["Premium subscriptions", "now", f"{(MIX5[0] + MIX5[1]) / sum(MIX5):.0%}", "≈80%", "live on Play + App Store"],
           ["Featured listings", f"month {FEATURED['start']}", f"{MIX5[2] / sum(MIX5):.0%}", "≈80%", f"${FEATURED['price']}/mo, {FEATURED['adoption']:.0%} of owners"],
           ["Enterprise packages", f"month {ENTERPRISE['start']}", f"{MIX5[3] / sum(MIX5):.0%}", "≈95%", f"${ENTERPRISE['price']:.0f}/mo clinic chains, coworking"],
           ["Booking fee (upside)", "optional", "not in base", "≈30%", "in-app payment later"]]
story += [row([tbl(price_tbl, [W * 0.1, W * 0.06, W * 0.06, W * 0.13, W * 0.07, W * 0.12], font=6.1),
               tbl(streams, [W * 0.11, W * 0.065, W * 0.06, W * 0.05, W * 0.135], font=6.1)], [W * 0.555, W * 0.445]),
          P("*after the 15% store fee. Revenue funnel: SEO / Instagram agent / agency ads → sign-up → specialist requests → owner sees "
            "demand → Premium at publish → featured upgrade → enterprise. Commission stays 0% by design (no incentive to bypass).", S)]

story += [P("Go-to-market", H2), P(
    f"<b>Most cost-efficient channel:</b> SEO landing pages + the AI Instagram/WhatsApp agent (already built, near-zero marginal cost) "
    f"for specialists; the agency's ${MKT_FLOOR:,}/month goes {PAID_HOST_SPLIT:.0%} to owner acquisition (Meta/Instagram lead ads to "
    f"clinic and centre owners, Google search \"rent clinic room\"), {1 - PAID_HOST_SPLIT:.0%} to specialists. Partnerships: "
    "psychology and physiotherapy associations, training academies, coworking groups. Influencers: practitioners with audiences, "
    "paid per paying owner referred. Referral: owners invite owners (1 free month). Content: \"how to fill empty clinic hours\" guides "
    "in Arabic and English. Each new country opens only after the previous stage hits its exit target.")]

story += [P("Growth and financial forecast", H2)]
ug = [["Scenario", "Y1 users", "Y3 users", "Y5 users", "Y5 paying owners", "Y5 MAU", "Y5 revenue", "Y5 EBITDA", "Break-even", "Max cash need"]]
for n, rows in MODEL.items():
    u = yearly(rows, "users", "last")
    ug.append([n, f"{u[0]:,.0f}", f"{u[2]:,.0f}", f"{u[4]:,.0f}", f"{yearly(rows, 'hosts', 'last')[4]:,.0f}",
               f"{yearly(rows, 'mau', 'last')[4]:,.0f}", money(REV[n][4], 1), money(EBITDA[n][4], 1),
               f"month {BE[n]}" if BE[n] else "after Y5", money(-MINCASH[n]) if MINCASH[n] < 0 else "none"])
story += [row([img(C4, 62 * mm, 40 * mm), img(C1, 62 * mm, 40 * mm), img(C5, 52 * mm, 40 * mm)], [64 * mm, 64 * mm, W - 128 * mm]),
          tbl(ug, [W * 0.11] + [W * 0.0988] * 9, font=6.2)]
fin = [["Expected (USD / year)"] + YRS]
for label, key in [("Revenue", "revenue"), ("  Subscriptions", "subs"), ("  Featured", "featured"), ("  Enterprise", "enterprise"),
                   ("Store fees + cloud + support", "cogs"), ("Marketing agency", "marketing"), ("Tech team", "team"),
                   ("Legal / fixed", "fixed"), ("Total cost", "cost"), ("EBITDA", "ebitda")]:
    fin.append([label] + [money(v) for v in yearly(EXP, key)])
fin.append(["Gross margin"] + [f"{g:.0%}" for g in GM])
fin.append(["Contribution margin"] + [f"{c:.0%}" for c in CM])
fin.append(["Cumulative cash"] + [money(v) for v in yearly(EXP, "cash", "last")])
fin.append(["Booking-fee upside (not in base)"] + [money(v) for v in yearly(EXP, "booking_upside")])
unit = [["Unit economics (paying owner)", "Value"], ["Gross ARPU / month", money(GROSS_ARPU, 2)], ["Net after store fee", money(NET_ARPU, 2)],
        ["Contribution / owner / month", money(host_margin, 2)], ["Monthly churn (Y3)", f"{HOST_CHURN[2]:.1%}"],
        ["LTV", money(LTV)], ["Paid CAC (agency)", money(CAC)], ["LTV / CAC", f"{LTV / CAC:.1f}x"], ["CAC payback", f"{PAYBACK:.1f} months"],
        ["Break-even (Expected)", f"month {BE['Expected']}" if BE["Expected"] else "after Y5"],
        ["Max cash needed (Expected)", money(-MINCASH["Expected"]) if MINCASH["Expected"] < 0 else "none"]]
story += [row([tbl(fin, [W * 0.2] + [W * 0.078] * 5, font=6.1), tbl(unit, [W * 0.2, W * 0.1], font=6.1)], [W * 0.6, W * 0.4]), PageBreak()]

# ---------------- PAGE 3: revenue vs cost, risks, investor view, verdict
story += [P("Revenue vs cost", H2), img(C3, W, W * 2.4 / 7.4)]
be_txt = (f"<b>Break-even:</b> Expected case is EBITDA-positive from <b>month {BE['Expected']}</b>; " if BE["Expected"]
          else "<b>Break-even:</b> Expected case does not break even within 5 years; ")
story += [row([img(C2, 62 * mm, 40 * mm), P(
    be_txt + f"Conservative {'month ' + str(BE['Conservative']) if BE['Conservative'] else 'after year 5'}, Aggressive "
    f"{'month ' + str(BE['Aggressive']) if BE['Aggressive'] else 'after year 5'}. Run-rate break-even ≈ "
    f"<b>{BE_USERS / 1e3:.0f}k registered users</b> (≈{BE_USERS * HOST_SHARE:,.0f} paying owners). Maximum cash needed before "
    f"break-even (Expected): <b>{money(-MINCASH['Expected']) if MINCASH['Expected'] < 0 else 'none'}</b> — angel / founder scale, no "
    f"venture round required. Because marketing is a share of revenue and the team stays small, costs can never run far ahead of "
    f"income; the trade-off is slower growth in the first 18 months. Monthly cost at 1k / 10k / 100k / 1M users: "
    + ", ".join(money(run_rate(u)[1]) for u in (1_000, 10_000, 100_000, 1_000_000)) + ". Year-5 revenue (Expected) "
    f"<b>{money(REV['Expected'][4], 1)}</b> with EBITDA <b>{money(EBITDA['Expected'][4], 1)}</b>.", B)], [64 * mm, W - 64 * mm])]

risks = [["Risk", "Type", "Prob.", "Severity", "Mitigation"],
         ["Cold start in a new city (no requests → owners cancel)", "Market", "High", "High", "Stage gates; seed listings; founder sales; first month free"],
         ["Owners reject Unlimited at 2x price", "Market", "Medium", "Medium", "1-listing tier stays cheap; yearly 50% off; watch mix monthly"],
         ["Slow growth because marketing follows revenue", "Strategy", "Medium", "Medium", "Organic engines (SEO, AI agent); add budget only when CAC < $45"],
         ["Deals move to WhatsApp after first contact", "Product", "High", "Low", "0% commission: the fee pays for demand + tools, not per booking"],
         ["Store fee / policy changes", "Regulatory", "Low", "Medium", "Web checkout where allowed; keep price parity across stores"],
         ["Medical licensing / liability for clinic rooms", "Regulatory", "Medium", "Medium", "Owner verification, terms, report/block"],
         ["Small team = key-person and delivery risk", "Technical", "Medium", "High", "One shared codebase, CI gates, documented runbooks"],
         ["AI agent misstatement or platform policy breach", "AI", "Medium", "Medium", "Disclosure, live-offer-only answers, kill switch, human handoff"],
         ["Regional instability (Lebanon, payments)", "Market", "High", "Medium", "Early move to UAE/KSA; USD pricing through the stores"]]
story += [P("Risk matrix", H2), tbl(risks, [W * 0.34, W * 0.09, W * 0.07, W * 0.08, W * 0.42], font=6.1)]
scores = [("Market opportunity", 6), ("Product differentiation", 7), ("Scalability", 7), ("Revenue potential", 5),
          ("Defensibility", 4), ("Profitability", 7), ("Founder-market fit", 7)]
vps = sum(s for _, s in scores) / len(scores)
story += [P("Investor assessment", H2), row([tbl([["Criterion", "Score"]] + [[a, f"{b}/10"] for a, b in scores] + [["Venture potential", f"{vps:.1f}/10"]],
                                                 [W * 0.22, W * 0.07], font=6.3),
    P(f"<b>Would a VC invest today?</b> Not yet, and the plan does not need it. The product is complete and cheap to run; the lean "
      f"model reaches profitability with about {money(-MINCASH['Expected']) if MINCASH['Expected'] < 0 else 'no'} of outside cash, which "
      f"suits founders, angels and regional accelerators (and keeps ownership). A VC would want proof of liquidity (owners getting "
      f"monthly bookings), a path above ~{money(REV['Aggressive'][4], 1)} revenue (Aggressive) and stronger lock-in (in-app payments, "
      f"reviews, calendar sync, B2B accounts). <b>To improve:</b> hit the stage-1 exit target, publish owner retention, launch featured "
      f"listings on time, and test the booking-fee upside in one market.", B)], [W * 0.31, W * 0.69])]
story += [P("Should this business be built and scaled?", H2), P(
    f"<b>Yes — built lean and scaled by stages.</b> The model pays for itself: the tech team starts at "
    f"${TEAM[0]:,}/month and grows only with revenue, marketing starts at ${MKT_FLOOR:,} and grows only from revenue, every paying owner earns "
    f"{money(host_margin, 2)}/month after fees and support, and LTV/CAC is {LTV / CAC:.1f}x. Expected case: "
    f"{money(REV['Expected'][2], 1)} revenue in year 3 and {money(REV['Expected'][4], 1)} in year 5, break-even "
    f"{'in month ' + str(BE['Expected']) if BE['Expected'] else 'after year 5'}, with Lebanon proving the model, the Gulf funding it and "
    f"Egypt/Levant giving volume. Scale each stage only when its exit target is met; consider outside capital later, once liquidity "
    f"data supports a faster Aggressive path.")]

# ---------------- APPENDIX
story += [PageBreak(), P("Appendix — Expected case month by month (months 1–24), cost growth by users, assumptions", H2)]
mt = [["M", "Stage", "Users", "Specialists", "Paying owners", "MAU", "Subs", "Other", "Revenue", "Agency", "Team+fixed", "Fees+cloud", "EBITDA", "Cash"]]
for r in EXP[:24]:
    mt.append([r["month"], r["stage"], f"{r['users']:,.0f}", f"{r['specialists']:,.0f}", f"{r['hosts']:,.0f}", f"{r['mau']:,.0f}",
               money(r["subs"]), money(r["featured"] + r["enterprise"]), money(r["revenue"]), money(r["marketing"]),
               money(r["team"] + r["fixed"]), money(r["cogs"]), money(r["ebitda"]), money(r["cash"])])
story += [tbl(mt, [W * 0.035, W * 0.045] + [W * 0.0767] * 12, font=5.9)]
cg = [["Registered users", "Tech team", "Legal/fixed", "Cloud + AI", "Support", "Agency", "Store fees", "Total cost / mo", "Revenue / mo"]]
for u in USER_POINTS:
    rv, c, d = run_rate(u)
    cg.append([f"{u:,}", money(d["team"]), money(d["fixed"]), money(d["infra"]), money(d["support"]), money(d["marketing"]),
               money(d["fees"]), money(c), money(rv)])
story += [P("Cost growth model (monthly run-rate, Expected ratios)", H2), tbl(cg, [W * 0.12] + [W * 0.11] * 8, font=6.2)]
story += [P("Assumptions", H2), P(
    f"Prices: 1 listing ${PRICES['single_m']}/${PRICES['single_y']}; Unlimited ${PRICES['unl_m']}/${PRICES['unl_y']} (2x today's "
    f"$8.99/$79.99), same on Google Play and the App Store; offers kept. Mix {SINGLE_SHARE:.0%} 1 listing, {YEARLY_SHARE:.0%} yearly, "
    f"free-month drag {FREE_MONTH_DRAG:.0%}, store fee {STORE_FEE:.0%}. Agency budget = max(${MKT_FLOOR:,}, share of last month's "
    f"revenue: {', '.join(f'{n} {s['mkt_share']:.0%}' for n, s in SCEN.items())}) + launch push per new stage; "
    f"{PAID_HOST_SPLIT:.0%} to owners at CAC {', '.join(f'${s['cac_h']}' for s in SCEN.values())}, rest to specialists at "
    f"{', '.join(f'${s['cac_s']}' for s in SCEN.values())}. Organic specialists per live country/month "
    f"{', '.join(str(s['org']) for s in SCEN.values())}, growing {ORGANIC_RAMP:.0%}/month of market age (cap 3x); organic owners "
    f"{HOST_FROM_DEMAND:.1%} of new specialists + {HOST_REFERRAL:.0%} referrals/month. Owner churn {HOST_CHURN[0]:.1%}→"
    f"{HOST_CHURN[4]:.1%}/month. Countries live: {', '.join(str(x) for x in MARKETS_PER_STAGE)} by stage. Tech team "
    f"minimum ${TEAM[0]:,}→${TEAM[4]:,}/mo × {', '.join(str(s['team']) for s in SCEN.values())} or {TEAM_SHARE:.0%} of revenue if higher; "
    f"organic owners per live country/month {', '.join(str(s['org_h']) for s in SCEN.values())}; fixed ${FIXED[0]}→${FIXED[4]:,}; cloud "
    f"${INFRA_BASE} + ${INFRA_PER_MAU}/MAU + AI ${AI_PER_USER}/user; support ${SUPPORT_PER_HOST}/owner. Featured "
    f"{FEATURED['adoption']:.0%} × ${FEATURED['price']} from month {FEATURED['start']}; enterprise 1 × ${ENTERPRISE['price']:.0f} per "
    f"{ENTERPRISE['per_hosts']} owners from month {ENTERPRISE['start']}. Market sizes: published flexible-workspace estimates "
    f"(Grand View, Fortune Business Insights, Reports and Data, 2025); SAM/SOM are estimates. Traction: production check 22 Nov 2026. "
    f"A model, not a promise.", S)]


def footer(c, d):
    c.saveState()
    c.setFont("Helvetica", 6.5)
    c.setFillColor(colors.HexColor(GREY))
    c.drawString(12 * mm, 7 * mm, "ProHost — strategic business plan · confidential")
    c.drawRightString(A4[0] - 12 * mm, 7 * mm, f"page {d.page}")
    c.setFillColor(colors.HexColor(ORANGE))
    c.rect(12 * mm, A4[1] - 8 * mm, A4[0] - 24 * mm, 1.2, fill=1, stroke=0)
    c.restoreState()


PDF = os.path.join(OUT, "ProHost_Business_Plan.pdf")
SimpleDocTemplate(PDF, pagesize=A4, leftMargin=12 * mm, rightMargin=12 * mm, topMargin=11 * mm, bottomMargin=11 * mm,
                  title="ProHost — Strategic Business Plan", author="ProHost").build(story, onFirstPage=footer, onLaterPages=footer)
with open(os.path.join(OUT, "forecast_model.csv"), "w", newline="") as f:
    w = csv.writer(f)
    keys = list(EXP[0].keys())
    w.writerow(["scenario"] + keys)
    for n, rows in MODEL.items():
        for r in rows:
            w.writerow([n] + [round(r[k], 2) if isinstance(r[k], float) else r[k] for k in keys])

print("PDF", PDF)
print(f"ARPU gross {GROSS_ARPU:.2f} net {NET_ARPU:.2f} LTV {LTV:.0f} CAC {CAC} LTV/CAC {LTV / CAC:.1f} payback {PAYBACK:.1f}")
for n in MODEL:
    print(n, "users", [round(v) for v in yearly(MODEL[n], "users", "last")], "hosts", [round(v) for v in yearly(MODEL[n], "hosts", "last")],
          "rev", [round(v) for v in REV[n]], "ebitda", [round(v) for v in EBITDA[n]], "BE", BE[n], "mincash", round(MINCASH[n]))
print("BE users", BE_USERS, "host share", round(HOST_SHARE, 4))
