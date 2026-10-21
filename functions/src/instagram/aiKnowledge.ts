/**
 * What the DM agent knows. Source: the public site (public/index.html, FAQ, landing pages) and docs/MONETIZATION.md.
 * The owner adds answers in the sheet's Knowledge tab (mirrored into app_config/instagram_knowledge); those win over this text.
 * Prices are deliberately absent: Google Play shows the exact price in the buyer's currency.
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
- Launch offer for NEW Premium subscribers: first 4 months free on the monthly plan, or 50% off the first year on the yearly plan. Google Play shows the exact price and the date billing starts before the person confirms; the regular price applies afterwards. Never quote a price: say Google Play shows it.
- Five ways to rent, room by room: hourly, shift, daily, monthly, per attendee. Each room has its own prices. Changing prices applies now or from the next term and tenants are notified automatically.
- One request inbox: accept or decline in a tap; availability updates by itself so there are no double bookings. Earnings and occupancy dashboard. WhatsApp built in; the host's number is never shown publicly.
- ProHost does not take a commission on bookings; Premium is a flat subscription.

CONTACT
- WhatsApp (admin team): +961 76 701 865 (https://wa.me/96176701865)
- Email: admin@pro-host.tech
- Strategic partners: https://pro-host.tech/partner
- Website contact form: https://pro-host.tech/#contact

NOT KNOWN / DO NOT ANSWER
Anything about specific listings, availability, someone's account, payments, refunds, legal terms beyond the website, or other companies. Say a team member will help and offer WhatsApp or email.`;

export interface KnowledgeItem { q: string; a: string }

export function buildKnowledge(extra: KnowledgeItem[]): string {
  const owner = extra.filter((i) => i.q.trim() && i.a.trim()).slice(0, 80)
    .map((i) => `Q: ${i.q.trim().slice(0, 300)}\nA: ${i.a.trim().slice(0, 900)}`).join("\n\n");
  return owner ? `${BASE_KNOWLEDGE}\n\nOWNER-APPROVED ANSWERS (these override anything above if they conflict)\n${owner}` : BASE_KNOWLEDGE;
}
