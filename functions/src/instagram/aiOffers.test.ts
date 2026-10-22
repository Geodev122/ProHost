import { test } from "node:test";
import assert from "node:assert/strict";
import { formatOffers, offersContext, OFFERS_UNKNOWN } from "./aiOffers";
import { LiveBasePlan } from "../billing/playCatalogLive";

const offer = (o: Partial<LiveBasePlan["offers"][number]>) => ({ offerId: "o", state: "ACTIVE", eligibility: "new customers: never had this subscription", tags: [], phases: [], availableIn: { LB: true, US: true }, regionCount: 2, otherRegions: false, ...o });
const plan = (p: Partial<LiveBasePlan>): LiveBasePlan => ({ basePlanId: "b", state: "ACTIVE", period: "P1M", prices: { LB: "4.99 USD", US: "4.99 USD" }, availableIn: { LB: true, US: true }, regionCount: 2, offers: [], ...p });

test("live offers are described from Play data: free months, percent off, regular price", () => {
  const text = formatOffers([
    plan({ offers: [offer({ phases: [{ duration: "P1M", recurrenceCount: 4, pricing: { LB: "free", US: "free" } }] })] }),
    plan({ basePlanId: "y", period: "P1Y", prices: { LB: "49.99 USD", US: "49.99 USD" }, offers: [offer({ phases: [{ duration: "P1Y", recurrenceCount: 1, pricing: { LB: "-50%", US: "-50%" } }] })] }),
  ]);
  assert.match(text, /Monthly plan offer: 4 months free, then regular price 4.99 USD per month/);
  assert.match(text, /Yearly plan offer: 50% off for 1 year, then regular price 49.99 USD per year/);
});

test("promo, inactive, upgrade-only offers and inactive plans never reach the customer", () => {
  const text = formatOffers([
    plan({ offers: [offer({ tags: ["promo"], phases: [{ duration: "P1M", recurrenceCount: 1, pricing: { LB: "free", US: "free" } }] }), offer({ state: "DRAFT", phases: [{ duration: "P1M", recurrenceCount: 2, pricing: { LB: "free", US: "free" } }] }), offer({ eligibility: "upgrade: current subscribers", phases: [{ duration: "P1M", recurrenceCount: 3, pricing: { LB: "free", US: "free" } }] })] }),
    plan({ state: "INACTIVE", period: "P1Y" }),
  ]);
  assert.equal(text, "- Monthly plan: no special offer right now, regular price 4.99 USD per month.");
});

test("no offers data means the agent is told to quote nothing", () => {
  assert.equal(offersContext(""), OFFERS_UNKNOWN);
  assert.match(offersContext("- x"), /describe ONLY these/);
});
