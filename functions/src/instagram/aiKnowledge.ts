import { RELEASE_KNOWLEDGE } from "./aiRelease.generated";

/**
 * What the DM agent knows. Source: the public site (public/index.html, FAQ, landing pages) and docs/MONETIZATION.md.
 * The owner adds answers in the sheet's Knowledge tab (mirrored into app_config/instagram_knowledge); those win over this text.
 * Prices and offers are deliberately absent: they come live from Google Play (aiOffers.ts).
 */
export const BASE_KNOWLEDGE = `ABOUT PROHOST
ProHost is a mobile app (Android, on Google Play) that connects space owners ("hosts") with professionals who need a room. Hosts list clinics, therapy rooms, offices, studios, classrooms, meeting rooms and coworking desks. Professionals (therapists, doctors, trainers, coaches, consultants, freelancers) find and book them.
Website: https://pro-host.tech . Instagram: @prohost.app . Download link: https://pro-host.tech/ig
Android only today. The iPhone app is coming soon; people can leave their email on the website to be told when it is ready.

FOR PROFESSIONALS (renters)
- Free: search spaces on a live map, book by the hour, shift, day or month, see real-time availability, message hosts on WhatsApp, track rentals and payment reminders.
- Before booking, a professional adds a profile photo and a WhatsApp number. The host sees each request and accepts or declines it.
- Booking is free; ProHost takes no booking fee and does not process payments between host and professional. They agree and settle payment directly.

FOR HOSTS (space owners)
- Publishing and managing listings needs ProHost Premium, a simple subscription with a monthly and a yearly plan, no lock-in, cancel any time in Google Play (Payments & subscriptions).
- Premium offers for NEW subscribers change over time: use only the LIVE PREMIUM OFFERS block provided with each conversation (exact, from Google Play). Google Play shows the exact price and the date billing starts before the person confirms; the regular price applies afterwards. If no live block is provided, do not mention any price or discount.
- Five ways to rent, room by room: hourly, shift, daily, monthly, per attendee. Each room has its own prices. Changing prices applies now or from the next term and tenants are notified automatically.
- One request inbox: accept or decline in a tap; availability updates by itself so there are no double bookings. Earnings and occupancy dashboard. WhatsApp built in; the host's number is never shown publicly.
- ProHost does not take a commission on bookings; Premium is a flat subscription.

FEATURES AND THE VALUE THEY GIVE (use to connect the product to the person's need; explain HOW it works, then WHY it helps)
- Live map search (professionals): see rooms near you with real-time availability, so you find a space in minutes instead of phoning around.
- Five ways to rent, room by room (hosts): hourly, shift, daily, monthly or per attendee. Value: the same room earns from a therapist by the hour, a doctor by the shift and a trainer per seat for a workshop.
- Live availability and automatic locking: when a request is accepted the slot locks itself. Value: no double bookings and no calendar to maintain by hand.
- One request inbox: every request lands in one place with all details; accept or decline in a tap. Value: less back-and-forth, nothing lost in chats.
- WhatsApp built in: professionals and hosts talk directly; the host's number is never shown publicly. Value: fast answers without giving out private numbers.
- Change prices any time: choose to apply now or from the next term; affected tenants are notified automatically. Value: you adjust prices without chasing people one by one.
- Earnings and occupancy dashboard (hosts): bookings, occupancy and revenue per room. Value: see which rooms earn and which sit empty.
- Payment reminders (professionals): add a monthly reminder to your own phone calendar so rent is never forgotten.
- Saved spaces and My Rentals: track what you saved and what you booked in one place.
- Arabic and English: the app follows the phone language, or choose it in Profile > Language (new in 1.0.43).

CONTACT
- WhatsApp (admin team): +961 76 701 865 (https://wa.me/96176701865)
- Email: admin@pro-host.tech
- Strategic partners: https://pro-host.tech/partner
- Practitioners who own an office (psychologists, life coaches, therapists…) can rent it out by the hour on ProHost and get their first month of Premium free when they register through the office campaign: they comment "office" under our posts or reply OFFICE, and we register them right here in the DM (name, email, WhatsApp number, profession, city, after they agree), or they fill the 30-second form https://pro-host.tech/list-your-office (the "office" button). The ProHost team then contacts them on WhatsApp to set up the listing and the free month.
- Website contact form: https://pro-host.tech/#contact

NOT KNOWN / DO NOT ANSWER
Anything about specific listings, availability, someone's account, payments, refunds, legal terms beyond the website, or other companies. Say the ProHost team can help and offer WhatsApp or email.`;

export interface KnowledgeItem { q: string; a: string }

export function buildKnowledge(extra: KnowledgeItem[]): string {
  const base = `${BASE_KNOWLEDGE}\n\n${RELEASE_KNOWLEDGE}`;
  const owner = extra.filter((i) => i.q.trim() && i.a.trim()).slice(0, 80)
    .map((i) => `Q: ${i.q.trim().slice(0, 300)}\nA: ${i.a.trim().slice(0, 900)}`).join("\n\n");
  return owner ? `${base}\n\nOWNER-APPROVED ANSWERS (these override anything above if they conflict)\n${owner}` : base;
}
