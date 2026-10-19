/**
 * Maintenance task `playOfferFix` (workflow task `playofferfix`): repairs two Play Console slips found by
 * `playcatalog` on 2026-10-19, through the Play Developer API with the functions' own service account.
 *
 *  1. The PROHOST2026 promo offer sits on the INACTIVE base plan `pro-monthly`. Play Console can't
 *     activate an offer whose base plan is inactive ("An unexpected error has occurred" when resuming),
 *     so the offer is copied — phases, tags, regions and eligibility — onto the live monthly plan and
 *     activated there. The app finds it by its `promo` tag on any base plan (PlayPromo).
 *  2. The monthly launch offer's 4-month phase was set to 99% off (a few cents) instead of free, while the
 *     website, emails and media promise "4 months free": the phase is made free (same duration, regions).
 *  3. The monthly launch offer is limited to accounts that "never had any subscription in this app", so
 *     anyone who ever subscribed never sees it. It is widened to "never had this subscription", like the
 *     yearly offer.
 *
 * Dry run unless `apply` is true: it then only reports what it would change. Never touches prices.
 */
import type { androidpublisher_v3 } from "googleapis";
import { PACKAGE_NAME, getPlayPublisher } from "./billingHelpers";
import { PLAY_PRODUCT_ID } from "./playCatalog";
import { WEBSITE_OFFER_CLAIMS } from "./offerClaims";

type Offer = androidpublisher_v3.Schema$SubscriptionOffer;
type Phase = androidpublisher_v3.Schema$SubscriptionOfferPhase;
type BasePlan = androidpublisher_v3.Schema$BasePlan;

const PROMO_TAG = "promo";
/** Region versions to try, newest first (the API rejects writes without one it knows). */
const REGIONS_VERSIONS = ["2025/03", "2022/02"];

export interface OfferFixStep {
  step: string;
  action: string;
  result: string;
}

const isPromo = (o: Offer) => (o.offerTags ?? []).some((t) => (t.tag ?? "").toLowerCase() === PROMO_TAG);
const months = (iso?: string | null) => {
  const m = /^P(\d+)([DWMY])$/.exec(iso ?? "");
  if (!m) return 0;
  const n = Number(m[1]);
  return m[2] === "Y" ? n * 12 : m[2] === "M" ? n : m[2] === "W" ? n / 4.345 : n / 30.4;
};

/** Pure: the ACTIVE base plan billed every month (the live monthly plan), if exactly one. */
export function liveMonthlyPlan(basePlans: BasePlan[]): BasePlan | null {
  const monthly = basePlans.filter((bp) => bp.state === "ACTIVE" && months(bp.autoRenewingBasePlanType?.billingPeriodDuration) === 1);
  return monthly.length === 1 ? monthly[0] : null;
}

/** Pure: a copy of [source] for [basePlanId] with only the writable fields (no state, same everything else). */
export function copyOfferFor(source: Offer, basePlanId: string, offerId: string): Offer {
  return {
    packageName: source.packageName,
    productId: source.productId,
    basePlanId,
    offerId,
    phases: source.phases,
    targeting: source.targeting,
    regionalConfigs: source.regionalConfigs,
    otherRegionsConfig: source.otherRegionsConfig,
    offerTags: source.offerTags,
  };
}

/** Pure: the smallest relative discount (0–1) a phase gives across its regions; 1 when every region is free. */
export function phaseMinDiscount(p: Phase): number {
  const cfgs = p.regionalConfigs ?? [];
  if (!cfgs.length) return 0;
  return Math.min(...cfgs.map((c) => (c.free ? 1 : typeof c.relativeDiscount === "number" ? c.relativeDiscount : 0)));
}

/** Pure: true for a claimed monthly phase that is "nearly free" (≥ 90 % off, not free) for at least the claimed months. */
export function isNearlyFreeClaimPhase(p: Phase | undefined): boolean {
  if (!p) return false;
  const d = phaseMinDiscount(p);
  return d >= 0.9 && d < 1 && months(p.duration) * (p.recurrenceCount ?? 1) >= WEBSITE_OFFER_CLAIMS.monthlyFreeMonths;
}

/** Pure: the same phase made free in every region it covers (duration and recurrence unchanged). */
export function freePhaseFrom(p: Phase): Phase {
  return {
    duration: p.duration,
    recurrenceCount: p.recurrenceCount,
    regionalConfigs: (p.regionalConfigs ?? []).map((c) => ({ regionCode: c.regionCode, free: {} })),
    ...(p.otherRegionsConfig ? { otherRegionsConfig: { free: {} } } : {}),
  };
}

/** Pure: true when an offer is limited to accounts that never had any subscription in the app. */
export function isAnyAppScope(o: Offer): boolean {
  return !!o.targeting?.acquisitionRule?.scope?.anySubscriptionInApp;
}

async function withRegionsVersion<T>(call: (version: string) => Promise<T>): Promise<T> {
  let last: unknown;
  for (const v of REGIONS_VERSIONS) {
    try {
      return await call(v);
    } catch (e) {
      last = e;
      const msg = String((e as { message?: string })?.message ?? e);
      if (!/region/i.test(msg)) throw e;
    }
  }
  throw last;
}

const reason = (e: unknown) => {
  const err = e as { code?: number; status?: number; message?: string };
  const status = err?.code ?? err?.status;
  const msg = String(err?.message ?? e).slice(0, 220);
  return status === 401 || status === 403
    ? `HTTP ${status}: the functions service account can't edit subscriptions in Play Console (${msg})`
    : `${status ? `HTTP ${status}: ` : ""}${msg}`;
};

export async function playOfferFix(apply: boolean): Promise<{ apply: boolean; steps: OfferFixStep[] }> {
  const publisher = await getPlayPublisher();
  const sub = (await publisher.monetization.subscriptions.get({ packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID })).data;
  const offers: Offer[] = [];
  let pageToken: string | undefined;
  do {
    const res = await publisher.monetization.subscriptions.basePlans.offers.list({
      packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: "-", pageSize: 100, pageToken,
    });
    offers.push(...(res.data.subscriptionOffers ?? []));
    pageToken = res.data.nextPageToken ?? undefined;
  } while (pageToken);

  const steps: OfferFixStep[] = [];
  const basePlans = sub.basePlans ?? [];
  const monthly = liveMonthlyPlan(basePlans);
  if (!monthly?.basePlanId) {
    steps.push({ step: "monthlyPlan", action: "none", result: "no single ACTIVE monthly base plan — nothing changed" });
    return { apply, steps };
  }
  const target = monthly.basePlanId;
  const activePlanIds = new Set(basePlans.filter((bp) => bp.state === "ACTIVE").map((bp) => bp.basePlanId));

  // 1. Promo offer stranded on an inactive base plan → copy onto the live monthly plan and activate.
  const livePromo = offers.find((o) => isPromo(o) && activePlanIds.has(o.basePlanId ?? ""));
  const stranded = offers.find((o) => isPromo(o) && !activePlanIds.has(o.basePlanId ?? ""));
  if (livePromo) {
    steps.push({ step: "promo", action: "none", result: `promo already on an ACTIVE plan: ${livePromo.basePlanId}/${livePromo.offerId} (${livePromo.state})` });
  } else if (!stranded) {
    steps.push({ step: "promo", action: "none", result: "no promo-tagged offer found" });
  } else {
    const taken = new Set(offers.filter((o) => o.basePlanId === target).map((o) => o.offerId));
    const offerId = !taken.has(stranded.offerId) ? stranded.offerId ?? "prohost-promo" : `${stranded.offerId}-m1`;
    const action = `copy ${stranded.basePlanId}/${stranded.offerId} → ${target}/${offerId} and activate`;
    if (!apply) steps.push({ step: "promo", action, result: "dry run" });
    else {
      try {
        await withRegionsVersion((version) => publisher.monetization.subscriptions.basePlans.offers.create({
          packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: target, offerId,
          "regionsVersion.version": version, requestBody: copyOfferFor(stranded, target, offerId),
        }));
        await publisher.monetization.subscriptions.basePlans.offers.activate({
          packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: target, offerId,
          requestBody: { packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: target, offerId },
        });
        steps.push({ step: "promo", action, result: "created and ACTIVE" });
      } catch (e) {
        steps.push({ step: "promo", action, result: `failed — ${reason(e)}. Play Console: Monetize › Subscriptions › ProHost Premium › ${target} › Add offer (copy phases + tags of ${stranded.offerId}), then Activate.` });
      }
    }
  }

  // 2. Claimed monthly offer charged a near-zero price instead of free → make the phase free.
  for (const o of offers.filter((x) => x.basePlanId === target && !isPromo(x) && x.state === "ACTIVE" && isNearlyFreeClaimPhase(x.phases?.[0]))) {
    const first = (o.phases ?? [])[0];
    const pct = Math.round(phaseMinDiscount(first) * 100);
    const phases = [freePhaseFrom(first), ...(o.phases ?? []).slice(1)];
    const action = `${target}/${o.offerId}: phase 1 ${first.recurrenceCount ?? 1}x${first.duration} ${pct}% off → free`;
    if (!apply) { steps.push({ step: "freePhase", action, result: "dry run" }); continue; }
    try {
      await withRegionsVersion((version) => publisher.monetization.subscriptions.basePlans.offers.patch({
        packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: target, offerId: o.offerId ?? "",
        updateMask: "phases", "regionsVersion.version": version,
        requestBody: { ...copyOfferFor(o, target, o.offerId ?? ""), phases },
      }));
      steps.push({ step: "freePhase", action, result: "updated in place" });
    } catch (patchErr) {
      // Play may refuse to change an active offer's phases: publish a free twin, then retire the old one.
      const twin = `${o.offerId}-trial`;
      try {
        await withRegionsVersion((version) => publisher.monetization.subscriptions.basePlans.offers.create({
          packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: target, offerId: twin,
          "regionsVersion.version": version, requestBody: { ...copyOfferFor(o, target, twin), phases },
        }));
        const ids = { packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: target };
        await publisher.monetization.subscriptions.basePlans.offers.activate({ ...ids, offerId: twin, requestBody: { ...ids, offerId: twin } });
        await publisher.monetization.subscriptions.basePlans.offers.deactivate({ ...ids, offerId: o.offerId ?? "", requestBody: { ...ids, offerId: o.offerId ?? "" } });
        steps.push({ step: "freePhase", action, result: `patch refused (${reason(patchErr)}); created ${twin} (free, ACTIVE) and deactivated ${o.offerId}` });
      } catch (e) {
        steps.push({ step: "freePhase", action, result: `failed — ${reason(e)}. Play Console: open ${o.offerId} › phase 1 › Free trial, ${first.duration} › Save.` });
      }
    }
  }
  if (!steps.some((s) => s.step === "freePhase")) steps.push({ step: "freePhase", action: "none", result: `no ${target} offer charging a near-zero price instead of free` });

  // 3. Claimed (non-promo) offers on the live monthly plan limited to "never had any subscription in this app".
  for (const o of offers.filter((x) => x.basePlanId === target && !isPromo(x) && x.state === "ACTIVE" && isAnyAppScope(x))) {
    const action = `${target}/${o.offerId}: eligibility "never had any subscription in this app" → "never had this subscription"`;
    if (!apply) { steps.push({ step: "eligibility", action, result: "dry run" }); continue; }
    try {
      await withRegionsVersion((version) => publisher.monetization.subscriptions.basePlans.offers.patch({
        packageName: PACKAGE_NAME, productId: PLAY_PRODUCT_ID, basePlanId: target, offerId: o.offerId ?? "",
        updateMask: "targeting", "regionsVersion.version": version,
        requestBody: { ...copyOfferFor(o, target, o.offerId ?? ""), targeting: { acquisitionRule: { scope: { thisSubscription: {} } } } },
      }));
      steps.push({ step: "eligibility", action, result: "updated" });
    } catch (e) {
      steps.push({ step: "eligibility", action, result: `failed — ${reason(e)}. Play Console: open the offer › Eligibility › "Never had this subscription" › Save.` });
    }
  }
  if (!steps.some((s) => s.step === "eligibility")) steps.push({ step: "eligibility", action: "none", result: `no ${target} offer limited to "any subscription in this app"` });
  return { apply, steps };
}
