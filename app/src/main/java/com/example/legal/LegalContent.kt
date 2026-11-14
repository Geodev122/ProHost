package com.example.legal

/**
 * Structured legal-document content - rendered both as the in-app HTML popup (LegalDocumentDialog.kt) and the
 * downloadable A5 PDF (LegalPdfGenerator.kt).
 *
 * The Privacy Policy and Terms of Use blocks between the GENERATED markers are produced from the website pages
 * public/privacy.html and public/terms.html by scripts/legal/sync_legal_content.py, so the app, the website and
 * the store listings always show the same text. Edit the HTML, run the script, never edit the generated blocks.
 * The Revocation Policy below is written by hand. All three are drafts that a licensed lawyer should review.
 */
data class LegalSection(
    val heading: String,
    val paragraphs: List<String>
)

data class LegalDocument(
    val id: String,
    val title: String,
    val shortDescription: String,
    val effectiveDate: String,
    val sections: List<LegalSection>
)

// SYNC: must match CURRENT_CONSENT_VERSION in functions/src/roles/assignInitialRole.ts
private const val EFFECTIVE_DATE = "November 8, 2026"

object LegalContent {

    val privacyPolicy = LegalDocument(
        id = "privacy_policy",
        title = "Privacy Policy",
        shortDescription = "How ProHost collects, uses, stores, and protects your personal data.",
        effectiveDate = EFFECTIVE_DATE,
        sections = listOf(
            // BEGIN GENERATED from public/privacy.html (scripts/legal/sync_legal_content.py)
            LegalSection(
                "Summary and What Changed",
                listOf(
                    "Summary: ProHost is a marketplace that connects specialists with host spaces. We collect " +
                    "only what we need to run it, we do not sell your personal data, we do not track you " +
                    "across other companies' apps or websites, and we do not use advertising identifiers. You " +
                    "can access, correct, export or delete your account and its data at any time, in the app " +
                    "or by email, wherever you live.",
                    "What changed on 8 November 2026: we corrected how sign-in works (email and password, " +
                    "Google, and Sign in with Apple; no SMS codes), added the ProHost app for iOS and Apple " +
                    "App Store subscriptions, named the service providers we use (including Google Maps, " +
                    "Apple, our AI assistant provider and our website host), described our automated " +
                    "assistant on Instagram and WhatsApp, and added backup, deletion and EU Digital Services " +
                    "Act details. These changes apply to new accounts, including iOS accounts, from today and " +
                    "to existing users from 22 November 2026 (14 days' notice, see Section 13)."
                )
            ),
            LegalSection(
                "1. Who We Are",
                listOf(
                    "ProHost (\"we\", \"us\", \"our\") operates a mobile and web platform (the Android app, " +
                    "the iOS app and pro-host.tech) that connects independent professionals (\"Specialists\") " +
                    "with property owners or operators (\"Pro Hosts\") who list rentable clinic, office and " +
                    "workspace subdivisions. We act as a platform intermediary and are not a party to any " +
                    "rental agreement between users. ProHost is the data controller (or, for California and " +
                    "similar U.S. state laws, the \"business\") responsible for the personal data described " +
                    "in this Policy.",
                    "ProHost is operated by Georges El Najjar, Maalaka North, Zahle, Central Bekaa, Lebanon " +
                    "(المعلقة الشمالية، زحلة، البقاع الأوسط، لبنان).",
                    "Privacy contact and single point of contact for users and authorities (including under " +
                    "the EU Digital Services Act): admin@pro-host.tech. We write to you in English; you may " +
                    "write to us in English or Arabic."
                )
            ),
            LegalSection(
                "2. Data We Collect",
                listOf(
                    "2.1 Account Data",
                    "• Email address and sign-in method — you sign in with email and password, with your " +
                    "Google account, or (on iOS) with Sign in with Apple. If you use Apple's \"Hide My " +
                    "Email\", we receive and use Apple's private-relay address as your email. We never see or " +
                    "store your plain password: Firebase Authentication keeps only a salted hash. Email is " +
                    "also used for service emails (verification, password reset, booking and subscription " +
                    "notices).",
                    "• Display name and specialty/profession — provided during registration; shown to the " +
                    "other party in a booking transaction.",
                    "• WhatsApp number — entered by you (it is self-declared and not verified by SMS) so that " +
                    "hosts and specialists can reach you about bookings. On Android you may choose to fill it " +
                    "from the system number picker; we do not read your number without that choice.",
                    "• Country and city — needed to publish a listing and to show relevant spaces.",
                    "• Profile photo — optional; stored in Google Cloud Storage if uploaded.",
                    "• Listing verification documents — only if, as a Pro Host, you ask for the optional " +
                    "\"Listing Verified\" badge and upload a proof of ownership or of your right to rent out " +
                    "that space; never requested at registration and never required of a Specialist. " +
                    "Documents are stored privately and reviewed by a ProHost administrator.",
                    "• Consent record — the date and version of the Terms and Privacy Policy you accepted.",
                    "2.2 Listing and Booking Data",
                    "• Space address, map position (the pin a host places), photos, pricing and availability " +
                    "configured by Pro Hosts.",
                    "• Booking requests, confirmation status and rental period.",
                    "• The WhatsApp number of the other party is shown when a booking request is made or " +
                    "confirmed, so you can arrange the rental directly.",
                    "• Saved listings (favourites), so you can find them again.",
                    "2.3 Usage, Device and Location Data",
                    "• Approximate location — used only while the app is open, to show nearby spaces and to " +
                    "detect your country at sign-up.",
                    "• Precise location — requested only when you tap \"Locate me\" to place or find a " +
                    "workspace on the map; it is used once for that action and is not stored continuously. " +
                    "You may decline, or grant approximate location only, and still use the app (you can " +
                    "place the pin by hand). On iOS and Android the system permission prompt applies and you " +
                    "can change it at any time in system settings.",
                    "• Maps — maps are drawn by the Google Maps SDK. Google receives the information needed " +
                    "to show a map (for example your IP address, device and app details, and the area shown) " +
                    "under Google's Privacy Policy (https://policies.google.com/privacy) and the Google " +
                    "Maps/Google Earth Additional Terms (https://maps.google.com/help/terms_maps/).",
                    "• Photos and camera — the app does not request access to your photo library or camera in " +
                    "advance. Photos you add come from the system photo picker (only the photos you select " +
                    "are shared with the app); the camera is used only if you choose \"Take photo\".",
                    "• Push notifications — if you allow them, we store a push token (Firebase Cloud " +
                    "Messaging on Android; Apple Push Notification service through Firebase on iOS) and keep " +
                    "a short in-app notification history (48 hours).",
                    "• Security and device integrity — to protect accounts and our servers from abuse we use " +
                    "Firebase App Check (Play Integrity on Android, Apple App Attest on iOS). These checks " +
                    "confirm that requests come from a genuine copy of our app; they do not identify you or " +
                    "read your files.",
                    "• Crash and diagnostic logs via Google Firebase Crashlytics — anonymised and never " +
                    "linked to your account.",
                    "• Usage analytics (only if you choose \"Allow\") via Google Analytics for Firebase: " +
                    "screens opened, searches and filters used, listings and rooms viewed, booking and " +
                    "subscription steps, and errors. These events are linked to your pseudonymous ProHost " +
                    "code (U-…), your role, country, plan status and account-age range — never to your name, " +
                    "email, phone number or an advertising identifier. Some subscription and booking events " +
                    "(for example renewals) are sent from our servers on your behalf, only while your choice " +
                    "is \"Allow\". Nothing is collected until you choose, and you can change your choice any " +
                    "time in Profile › Privacy. Analytics data is never used for advertising.",
                    "• No tracking. We do not use the advertising identifier (the Android advertising ID or " +
                    "Apple's IDFA), we do not combine your data with other companies' data to track you " +
                    "across apps and websites, and therefore we do not show Apple's App Tracking Transparency " +
                    "prompt.",
                    "2.4 Subscription and Purchase Data",
                    "If you subscribe to ProHost Premium, the store you bought it from processes the payment: " +
                    "Google Play on Android, the Apple App Store on iOS. We never receive your card or bank " +
                    "details. We receive and store the purchase identifiers (on Google Play the purchase " +
                    "token and order ID; on the App Store the transaction identifiers), the plan (monthly or " +
                    "yearly), the subscription status and the renewal/expiry dates, linked to your account, " +
                    "to verify the purchase, grant and remove Pro Host access and prevent fraud. Both stores " +
                    "send us notifications when a subscription renews, is cancelled or refunded.",
                    "2.5 Communications and Our Automated Assistant",
                    "• Between users — ProHost does not host or store chat messages between users. Direct " +
                    "communication happens on WhatsApp, which is governed by Meta's own privacy policy. We " +
                    "only store the event record (booking request/acceptance) within our platform.",
                    "• Our assistant on Instagram and WhatsApp — when you message or comment on ProHost's " +
                    "Instagram account (@prohost.app) or its WhatsApp number, an automated AI assistant may " +
                    "reply. It says that it is an AI assistant, and you can write STOP at any time or ask for " +
                    "a person. We process the messages you send, your public profile name on that platform, " +
                    "and a short summary needed to keep the conversation going, using Anthropic's API as our " +
                    "AI provider (Anthropic does not use this content to train its models under its " +
                    "commercial terms). The assistant only looks up your ProHost account after you give it " +
                    "your registration email and agree, and it never reads back account details. If you " +
                    "choose to leave your name, email, WhatsApp number, profession and city as an " +
                    "office-owner or partner enquiry, we store them as a lead after you consent. " +
                    "Conversations are kept so that a chat can continue and are deleted when you ask us to " +
                    "(write STOP or email us); Meta processes the messages on its platforms under its own " +
                    "policies.",
                    "• Reports and support — if you report content or contact us, we keep the message, the " +
                    "content concerned and our decision.",
                    "2.6 Website Contact Form and Newsletter",
                    "When you use the contact form on pro-host.tech we store your name, email and, if you " +
                    "give them, your phone number, organization, role and message, and we email your request " +
                    "to our team at admin@pro-host.tech so we can answer you.",
                    "If you tick \"Send me ProHost news and offers\" or subscribe to the newsletter, we keep " +
                    "your email (and the details you gave) in our mailing list and send you occasional " +
                    "ProHost news and offers from noreply@pro-host.tech. Every email has an Unsubscribe link " +
                    "that removes you immediately; you can also write to admin@pro-host.tech.",
                    "This data is stored in Google Firebase, is accessible only to ProHost administrators, " +
                    "and is never sold. We keep contact requests for as long as needed to handle them and our " +
                    "relationship with you, and newsletter data until you unsubscribe (after which we keep " +
                    "only a record that you opted out, so we don't email you again)."
                )
            ),
            LegalSection(
                "3. How We Use Your Data",
                listOf(
                    "• To create and secure your account, sign you in and prevent fraud and abuse (including " +
                    "attestation checks and, where you request it, listing verification).",
                    "• To display your listing and profile details to users searching in the app.",
                    "• To facilitate booking requests, confirmations and price-change notices.",
                    "• To sell, verify and manage ProHost Premium through Google Play and the Apple App " +
                    "Store.",
                    "• To send service messages and push notifications you need (bookings, subscription " +
                    "changes, security).",
                    "• To provide customer support, handle reports and investigate disputes.",
                    "• To comply with legal obligations, including data-protection, consumer-protection, and " +
                    "tax/accounting laws applicable in the jurisdictions where we or our users operate.",
                    "• To improve the platform through aggregated analytics (only with your consent) and " +
                    "crash diagnostics — never sold to third parties.",
                    "• To answer website contact requests and AI-assistant conversations and, only with your " +
                    "consent, to send ProHost news and offers (see 2.5 and 2.6)."
                )
            ),
            LegalSection(
                "4. Legal Bases for Processing",
                listOf(
                    "Where the EU General Data Protection Regulation (Regulation (EU) 2016/679, \"GDPR\"), " +
                    "the UK GDPR, or an equivalent law applies to our processing of your personal data, we " +
                    "rely on the following legal bases:",
                    "• Contract performance (GDPR Art. 6(1)(b)) — processing necessary to register your " +
                    "account, provide Premium and fulfil booking transactions you initiate.",
                    "• Legitimate interest (GDPR Art. 6(1)(f)) — fraud prevention, device-integrity checks, " +
                    "listing verification, platform security, content moderation and diagnostics, balanced " +
                    "against your rights and never overriding them.",
                    "• Consent (GDPR Art. 6(1)(a)) — optional analytics, precise location when you tap " +
                    "\"Locate me\", push notifications, AI-assistant account look-ups and lead capture, and " +
                    "marketing communications; you may withdraw consent at any time without affecting the " +
                    "lawfulness of processing before withdrawal.",
                    "• Legal obligation (GDPR Art. 6(1)(c)) — compliance with applicable law, including " +
                    "responding to a lawful request from a competent authority."
                )
            ),
            LegalSection(
                "5. Data Sharing and Service Providers",
                listOf(
                    "We do not sell your personal data, and we do not \"share\" it for cross-context " +
                    "behavioural advertising as that term is used in the California Consumer Privacy Act as " +
                    "amended by the California Privacy Rights Act (\"CCPA/CPRA\"). We share data only as " +
                    "follows:",
                    "• Between users — a Specialist's name, specialty and WhatsApp number are shared with a " +
                    "Pro Host when a booking request is made or confirmed, and vice versa.",
                    "• Google (Google LLC / Google Ireland Limited) — Firebase Authentication, Firestore, " +
                    "Cloud Storage, Cloud Functions, App Check, Cloud Messaging, Crashlytics and Analytics; " +
                    "the Google Maps SDK; and sign-in with Google. Google acts as our service provider / " +
                    "processor under its data processing terms, and for Google sign-in and Google Maps under " +
                    "its own privacy policy. See Firebase Privacy " +
                    "(https://firebase.google.com/support/privacy).",
                    "• Google Play — subscription purchases on Android are processed by Google under the " +
                    "Google Play Terms of Service; we exchange the purchase token with Google's Play " +
                    "Developer API to verify and manage your subscription.",
                    "• Apple (Apple Inc. and Apple Distribution International) — Sign in with Apple, App " +
                    "Store subscription processing and server notifications, Apple Push Notification service, " +
                    "App Attest and TestFlight (for testers). Apple's own Privacy Policy " +
                    "(https://www.apple.com/legal/privacy/) applies to what Apple collects.",
                    "• Anthropic (Anthropic PBC) — AI model provider for our automated assistant (see 2.5). " +
                    "We send only the conversation needed to answer.",
                    "• Meta (Meta Platforms Ireland Limited / Meta Platforms, Inc.) — Instagram and WhatsApp, " +
                    "through which our assistant and WhatsApp conversations run; Meta's own policies apply.",
                    "• Netlify (Netlify, Inc.) — hosts the pro-host.tech website and serves its pages; it " +
                    "processes technical request data such as IP address and requested page for delivery and " +
                    "security.",
                    "• Email delivery and domain services — providers that send our service emails and host " +
                    "our domain and mailboxes, acting only on our instructions.",
                    "• Legal process — we may disclose data if required by a valid court order or legal " +
                    "obligation, or to protect the rights and safety of users.",
                    "• Business transfer — in the event of a merger, acquisition or asset sale, data may " +
                    "transfer subject to the same privacy commitments described here, and you will be " +
                    "notified of any change in controller."
                )
            ),
            LegalSection(
                "6. Data Retention",
                listOf(
                    "• Active account data is retained while your account remains open.",
                    "• Booking records are retained for five (5) years for legal and dispute-resolution " +
                    "purposes, or longer where a longer statutory limitation period applies in the relevant " +
                    "jurisdiction.",
                    "• Subscription and purchase records are kept while the subscription is active and for up " +
                    "to five (5) years after it ends, for tax, accounting and chargeback/fraud purposes.",
                    "• Upon account deletion, personal data is removed from our live systems within thirty " +
                    "(30) calendar days except where retention is required by law. Encrypted system backups " +
                    "(daily backups kept 7 days, weekly backups kept 8 weeks) roll off automatically, so " +
                    "deleted data can remain in backups for up to 8 weeks and is not restored except for " +
                    "disaster recovery.",
                    "• Reports and moderation records are kept for as long as needed to handle the report and " +
                    "protect users, and longer only where a dispute or legal claim requires it.",
                    "• Assistant conversations and leads are kept until you ask us to delete them or, for " +
                    "leads, until the enquiry is completed; STOP or an email to admin@pro-host.tech deletes " +
                    "them.",
                    "• Pseudonymous analytics events are kept for 14 months in Google Analytics and for up to " +
                    "26 months in our private BigQuery export; aggregated, non-identifiable reports may be " +
                    "retained indefinitely. Withdrawing consent stops collection and resets the analytics " +
                    "identifier on your device."
                )
            ),
            LegalSection(
                "7. Your Privacy Rights",
                listOf(
                    "Your specific rights depend on where you live. We honour the applicable rights below for " +
                    "every user, regardless of location, as a matter of policy — not only where strictly " +
                    "required by law.",
                    "7.1 If You Are in the EEA, UK, or a Similar GDPR-Aligned Jurisdiction",
                    "• Access (Art. 15) a copy of personal data we hold about you.",
                    "• Rectification (Art. 16) of inaccurate or incomplete data — most details can be edited " +
                    "in Profile.",
                    "• Erasure (Art. 17) of your account and associated personal data — available directly in " +
                    "the app (Profile → Delete Account, on Android and iOS) or by written request to " +
                    "admin@pro-host.tech or via our web request page. Processed within 30 days. On iOS, " +
                    "deleting your account also revokes the Sign in with Apple connection.",
                    "• Restriction of processing (Art. 18) and objection (Art. 21) in certain circumstances.",
                    "• Data portability (Art. 20) — a machine-readable export of data you provided.",
                    "• Withdraw consent (Art. 7(3)) for optional analytics, location, notifications, " +
                    "assistant look-ups or communications at any time.",
                    "• Lodge a complaint with your local data protection supervisory authority — see Section " +
                    "12.",
                    "7.2 If You Are a California or Other U.S. State Resident",
                    "Under the CCPA/CPRA and similar state laws (for example Virginia's VCDPA, Colorado's " +
                    "CPA, and Connecticut's CTDPA), you may have the right to:",
                    "• Know / Access the categories and specific pieces of personal information we have " +
                    "collected about you and the purposes for collection, as described in Section 2 and " +
                    "Section 3.",
                    "• Delete personal information we have collected from you, subject to certain exceptions " +
                    "(e.g. completing a transaction, legal compliance).",
                    "• Correct inaccurate personal information.",
                    "• Opt out of \"sale\" or \"sharing\" — we do not sell personal information or share it " +
                    "for cross-context behavioural advertising, so there is nothing to opt out of; we do not " +
                    "use dark patterns to obscure this fact.",
                    "• Limit use of sensitive personal information — we do not use verification documents or " +
                    "precise geolocation for any purpose beyond verification, safety, and providing the core " +
                    "booking/discovery features you request.",
                    "• Non-discrimination — we will not deny you goods or services, or provide a different " +
                    "level of service, because you exercised a privacy right.",
                    "• Appeal a denied request, and designate an authorised agent to submit a request on your " +
                    "behalf (subject to identity verification).",
                    "To exercise any right in Section 7.1 or 7.2, contact us at admin@pro-host.tech or use " +
                    "the in-app options. We will verify your identity before acting on a request and will " +
                    "respond within the time required by applicable law (generally thirty (30) to forty-five " +
                    "(45) days)."
                )
            ),
            LegalSection(
                "8. Security",
                listOf(
                    "We employ industry-standard security measures: HTTPS-only data transmission, Firebase " +
                    "Security Rules limiting data access to authenticated owners, server-side-only " +
                    "enforcement of sensitive fields (role, verification status, payment entitlements), " +
                    "password hashing by Firebase Authentication, app-integrity checks (Firebase App Check), " +
                    "private storage of verification documents, and no storage of payment card data. No " +
                    "system is perfectly secure; we cannot guarantee absolute security. If we become aware of " +
                    "a data breach creating a real risk to your rights, we will notify affected users and, " +
                    "where legally required, the competent supervisory authority, without undue delay and in " +
                    "any event within the timeframe required by applicable law (for example 72 hours under " +
                    "GDPR Art. 33 where feasible)."
                )
            ),
            LegalSection(
                "9. Cookies and Tracking on This Website",
                listOf(
                    "The ProHost marketing website (pro-host.tech) uses no third-party advertising or " +
                    "behavioural-tracking cookies. Google Fonts are loaded from Google's servers; see " +
                    "Google's Privacy Policy (https://policies.google.com/privacy) for how font requests are " +
                    "handled. The site is served by Netlify, which processes technical request data (see " +
                    "Section 5). We honour the Global Privacy Control (GPC) signal and equivalent \"Do Not " +
                    "Track\" browser signals as a valid opt-out request under CCPA/CPRA where applicable, " +
                    "though as noted in Section 5 we do not sell or share personal information in any event."
                )
            ),
            LegalSection(
                "10. Children's Privacy",
                listOf(
                    "ProHost is not directed at persons under 18 years of age (or the age of majority in your " +
                    "jurisdiction, if higher). We do not knowingly collect personal data from minors. If we " +
                    "become aware that a minor has registered, we will delete the account and associated data " +
                    "promptly."
                )
            ),
            LegalSection(
                "11. Limitation of Liability and Disclaimer",
                listOf(
                    "Platform disclaimer. ProHost is a marketplace facilitator only. We are not a party to " +
                    "any rental agreement, employment arrangement, or professional services contract entered " +
                    "into between Specialists and Pro Hosts through the platform. ProHost makes no warranty " +
                    "as to the accuracy of listings, the fitness of a space for a particular professional " +
                    "use, or the conduct of any user.",
                    "To the maximum extent permitted by applicable law, ProHost's aggregate liability arising " +
                    "out of or in connection with the collection or use of personal data shall not exceed the " +
                    "total fees paid by you (if any) to ProHost in the twelve (12) months preceding the " +
                    "claim. See the Terms of Use for our full limitation of liability and your " +
                    "indemnification obligations, and the Terms' section on governing law and your local " +
                    "rights for the non-waivable rights preserved for users in the EU/UK, Australia, " +
                    "California, and similar jurisdictions.",
                    "We are not liable for: (a) any consequential, indirect, incidental or punitive damages; " +
                    "(b) any loss of data resulting from the actions of third-party service providers such as " +
                    "Google Firebase; or (c) any breach resulting from a force majeure event as described in " +
                    "the Terms of Use. Nothing in this Policy limits liability that cannot be limited by law."
                )
            ),
            LegalSection(
                "12. International Data Transfers & Regional Compliance",
                listOf(
                    "ProHost's infrastructure runs on Google Firebase / Google Cloud (primarily in the " +
                    "European Union, europe-west1), and our providers (Apple, Anthropic, Meta, Netlify and " +
                    "others in Section 5) may process data in the United States, the European Union, or other " +
                    "countries where they operate. Where we transfer personal data protected by the GDPR or " +
                    "UK GDPR outside the EEA or UK to a country not deemed adequate by the European " +
                    "Commission or UK authorities, we rely on an appropriate safeguard recognised under " +
                    "Chapter V GDPR, in particular the European Commission's Standard Contractual Clauses (or " +
                    "the UK International Data Transfer Addendum) and, where a provider is certified, the " +
                    "EU-U.S. Data Privacy Framework, together with the providers' supplementary security " +
                    "measures.",
                    "EU/UK/EEA users: you may lodge a complaint with the supervisory authority in your " +
                    "country of habitual residence or place of alleged infringement (for example, the Irish " +
                    "Data Protection Commission for the EU, or the UK Information Commissioner's Office for " +
                    "the UK) at any time, in addition to contacting us directly.",
                    "California and other U.S. states: the rights and disclosures in Section 7.2 apply to you " +
                    "regardless of where our servers are physically located.",
                    "Lebanon: we operate in compliance with Lebanese Law No. 81/2018 (Electronic Transactions " +
                    "and Personal Data) and applicable consumer-protection obligations. Users located in " +
                    "Lebanon have all rights described in Section 7.1, applied by ProHost as a matter of " +
                    "policy even though Lebanese law does not currently mandate an identical standalone " +
                    "regime.",
                    "We do not transfer personal data to any jurisdiction without an appropriate safeguard of " +
                    "the kind described above, or another legally recognised transfer mechanism, in place."
                )
            ),
            LegalSection(
                "13. Changes to This Policy",
                listOf(
                    "We may update this policy at any time. We will notify registered users of material " +
                    "changes via in-app notification or email at least fourteen (14) days before they take " +
                    "effect (or a longer period where required by mandatory law in your jurisdiction). " +
                    "Continued use of the platform after that date constitutes acceptance of the revised " +
                    "policy, without prejudice to any right you have to object to a change under Section 7 or " +
                    "to delete your account beforehand. The changes dated 8 November 2026 take effect for " +
                    "existing users on 22 November 2026."
                )
            ),
            LegalSection(
                "Contact",
                listOf(
                    "For any privacy-related request, question or complaint:",
                    "• Email: admin@pro-host.tech",
                    "• Account deletion: pro-host.tech/delete-account",
                    "• Terms of Use: pro-host.tech/terms",
                    "We aim to respond within 30 calendar days (or sooner where required by applicable law)."
                )
            )
            // END GENERATED
        )
    )

    val termsOfUse = LegalDocument(
        id = "terms_of_use",
        title = "Terms of Use",
        shortDescription = "The rules governing your use of the ProHost marketplace.",
        effectiveDate = EFFECTIVE_DATE,
        sections = listOf(
            // BEGIN GENERATED from public/terms.html (scripts/legal/sync_legal_content.py)
            LegalSection(
                "Summary and What Changed",
                listOf(
                    "Please read carefully, including Sections 11, 12, 13 and 17. By creating an account or " +
                    "using any feature of ProHost, you agree to these Terms. If you do not agree, do not use " +
                    "the platform. These Terms apply to users worldwide; Section 17 explains how they " +
                    "interact with the mandatory consumer-protection laws of your own country of residence.",
                    "What changed on 8 November 2026: sign-in now uses email and password, Google or (on iOS) " +
                    "Sign in with Apple, instead of SMS codes; Premium can be bought on Google Play and on " +
                    "the Apple App Store (Section 7); we added content rules, reporting and moderation " +
                    "(Section 9), information required for EU users (Section 9.4), our automated assistant " +
                    "(Section 1.2), Google Maps and Apple terms (Section 15) and clearer account and " +
                    "termination rules. These changes apply to new accounts, including iOS accounts, from " +
                    "today and to existing users from 22 November 2026 (14 days' notice, Section 18). Section " +
                    "17 (governing law and dispute resolution) is unchanged."
                )
            ),
            LegalSection(
                "1. About ProHost",
                listOf(
                    "ProHost is an online marketplace platform that enables independent professionals " +
                    "(\"Specialists\") to discover and request workspace subdivisions listed by property " +
                    "owners and operators (\"Pro Hosts\"). ProHost provides the technology connecting both " +
                    "sides of the market, through our Android app, our iOS app and the website pro-host.tech.",
                    "ProHost is a platform intermediary, not a party to any rental agreement. Any rental, " +
                    "licence or services arrangement is entered into solely between the Specialist and the " +
                    "Pro Host. ProHost does not own, manage, operate or control any listed space, and is not " +
                    "responsible for acts or omissions of any user.",
                    "1.1 Who provides the service",
                    "The ProHost service is provided by Georges El Najjar, Maalaka North, Zahle, Central " +
                    "Bekaa, Lebanon (المعلقة الشمالية، زحلة، البقاع الأوسط، لبنان), referred to in these " +
                    "Terms as \"ProHost\", \"we\" or \"us\".",
                    "Contact for questions, notices and complaints: admin@pro-host.tech.",
                    "1.2 Our automated assistant",
                    "ProHost uses an automated AI assistant to answer questions on ProHost's Instagram " +
                    "account and WhatsApp number and in comments on our posts. The assistant tells you that " +
                    "it is an AI assistant, can make mistakes, does not give legal, medical, tax or financial " +
                    "advice, and does not make binding offers: prices, offers and availability shown in the " +
                    "app or by the app stores are the ones that apply. You can type STOP at any time to stop " +
                    "messages, or ask for a person. Please do not send sensitive personal data in these " +
                    "chats. How we process these conversations is described in the Privacy Policy."
                )
            ),
            LegalSection(
                "2. Eligibility",
                listOf(
                    "• You must be at least 18 years of age, or the age of legal majority in your country of " +
                    "residence if higher, to use ProHost.",
                    "• You must have the legal capacity to enter into binding contracts under applicable law.",
                    "• You must give accurate profile details and a WhatsApp number that you own and control, " +
                    "and keep them up to date.",
                    "• Entities (companies, clinics, practices) may register through an authorised " +
                    "representative who accepts these Terms on behalf of the entity and confirms they have " +
                    "authority to do so.",
                    "• You must not be located in, or a resident of, any country or region subject to a " +
                    "comprehensive trade embargo by the United Nations, the United States, the European Union " +
                    "or Lebanon, and must not be listed on any applicable restricted-party or sanctions list " +
                    "(see Section 14)."
                )
            ),
            LegalSection(
                "3. Account Registration",
                listOf(
                    "You can create an account with your email address and a password, with your Google " +
                    "account or, on iOS, with Sign in with Apple. We do not send or require SMS codes. Your " +
                    "WhatsApp number is entered by you and is not verified by SMS. Some actions need more of " +
                    "your profile: requesting a booking needs a profile photo and a WhatsApp number; " +
                    "publishing a listing also needs your country and city.",
                    "You are responsible for all activity that occurs under your account. Choose a strong " +
                    "password, keep it confidential, and do not share your account credentials, let others " +
                    "use your account, or create accounts on behalf of others without authorisation. Tell us " +
                    "promptly at admin@pro-host.tech if you think someone else has access to your account.",
                    "You can delete your account at any time in the app (Profile › Delete Account) or on " +
                    "pro-host.tech/delete-account. Deleting your account does not cancel a subscription you " +
                    "bought from Google Play or the App Store (see Section 7.1).",
                    "ProHost reserves the right to suspend or terminate accounts that violate these Terms, " +
                    "that are reported for misconduct, or that show signs of fraud, at our sole discretion."
                )
            ),
            LegalSection(
                "4. Pro Host Obligations",
                listOf(
                    "• All listing information (address, photos, amenities, pricing, availability) must be " +
                    "accurate and kept up to date.",
                    "• Pro Hosts must hold all necessary permits, licences and insurance required under the " +
                    "law of the space's location to rent the listed premises for the advertised professional " +
                    "uses.",
                    "• Pro Hosts are solely responsible for ensuring the workspace complies with applicable " +
                    "health, safety, building, zoning, and accessibility regulations in the jurisdiction " +
                    "where the space is located.",
                    "• Pro Hosts must honour confirmed bookings or notify the Specialist promptly with " +
                    "adequate notice if cancellation is unavoidable.",
                    "• Pro Hosts must not discriminate against Specialists on the basis of any characteristic " +
                    "protected under applicable law in the jurisdiction where the space is located."
                )
            ),
            LegalSection(
                "5. Specialist Obligations",
                listOf(
                    "• Specialists must use rented spaces only for lawful professional purposes and in " +
                    "accordance with any rules communicated by the Pro Host.",
                    "• Specialists are solely responsible for holding all professional licences, permits, " +
                    "insurance and regulatory approvals required to practise their profession at the rented " +
                    "location, under the law of that location.",
                    "• Specialists must vacate the premises at the agreed end time, leave them in the " +
                    "condition received, and report damage promptly.",
                    "• Any professional malpractice, negligence or liability arising from a Specialist's " +
                    "professional activities is the Specialist's sole responsibility."
                )
            ),
            LegalSection(
                "6. Rental Agreements",
                listOf(
                    "A booking request on ProHost does not create a legally binding rental agreement until " +
                    "the Pro Host expressly confirms the booking within the platform. The terms, duration, " +
                    "price, rules and conditions of the rental are determined solely by the parties, under " +
                    "the law applicable to a rental of that kind at that location. ProHost is not responsible " +
                    "for enforcing, interpreting or adjudicating any rental agreement or dispute arising from " +
                    "one.",
                    "Cancelling. A booking request that has not yet been accepted may be withdrawn by the " +
                    "Specialist at any time before the Pro Host responds. Either party may cancel a booking " +
                    "that has already been accepted in the app by choosing a reason (for example schedule " +
                    "conflict, an alternative space found, relocation or closure, a property condition issue, " +
                    "or mutual agreement) and, optionally, adding a note; the other party is notified " +
                    "immediately. Cancelling in the app ends the booking's status on the platform. It does " +
                    "not by itself decide any refund, penalty or notice period between you, which depend on " +
                    "what you agreed with the other party and the law that applies to that arrangement; " +
                    "ProHost takes no position on, and has no role in deciding, who owes what to whom after a " +
                    "cancellation."
                )
            ),
            LegalSection(
                "7. Fees and Payments",
                listOf(
                    "ProHost currently does not charge transaction fees or process payments between users. " +
                    "Any financial arrangements (rent, deposits, damages) are negotiated and settled directly " +
                    "between the Specialist and the Pro Host outside the platform. ProHost makes no " +
                    "representation about pricing, and any price shown is set unilaterally by the Pro Host. " +
                    "Where ProHost charges a Pro Host a subscription or listing fee for use of the platform " +
                    "itself (through Google Play or the Apple App Store), that fee is separate from, and " +
                    "unrelated to, any rent paid between users, and is subject to the store's own terms and " +
                    "any refund policy stated at the time of purchase, together with any non-waivable " +
                    "consumer refund right under the law of your country of residence.",
                    "7.1 ProHost Premium subscription",
                    "Browsing, saving and requesting workspaces is free. Publishing workspace listings as a " +
                    "Pro Host requires ProHost Premium, an auto-renewing subscription with a monthly and a " +
                    "yearly plan, sold only through Google Play (Android) and the Apple App Store (iOS). The " +
                    "store you buy from is your seller of record for the payment.",
                    "• Price and billing. The price, billing period and any free trial or introductory offer " +
                    "are shown on the store's purchase screen and on the ProHost Premium screen before you " +
                    "confirm. Payment is charged to your Google Play account or your Apple ID account at " +
                    "confirmation of purchase.",
                    "• Automatic renewal. The subscription renews automatically at the end of each period at " +
                    "the then-current price unless you cancel at least 24 hours before the end of the current " +
                    "period; the store charges the renewal within 24 hours before the period ends. A free " +
                    "trial or promo-code period converts to a paid subscription at its end unless you cancel " +
                    "before it ends, and any unused part of a free trial is forfeited when you buy a " +
                    "subscription. The stores notify you before any price increase as they require.",
                    "• Switching plans. The store applies its own switching rules. On Google Play, moving " +
                    "from monthly to yearly takes effect immediately with credit for unused time and moving " +
                    "from yearly to monthly takes effect at your next renewal date; on the App Store the " +
                    "upgrade and downgrade rules of the subscription group apply.",
                    "• Cancelling. Cancel at any time in the store where you bought it: Google Play › " +
                    "Payments & subscriptions › Subscriptions, or on iPhone/iPad Settings › your name › " +
                    "Subscriptions (or in the App Store app › your account › Subscriptions). The \"Manage " +
                    "subscription\" link in the app opens the right page. You keep Premium until the end of " +
                    "the period you have paid for; we do not provide partial refunds for unused time except " +
                    "where required by law. Deleting your ProHost account does not cancel the subscription.",
                    "• Refunds. Refunds are handled by the store you bought from under its refund policy " +
                    "(Google Play, or Apple at reportaproblem.apple.com), together with any non-waivable " +
                    "consumer refund right under the law of your country of residence. ProHost cannot refund " +
                    "store purchases itself.",
                    "• One subscription, one store. A subscription is managed only in the store where you " +
                    "bought it. Premium status belongs to your ProHost account and applies on any device " +
                    "where you sign in; please do not buy Premium twice.",
                    "• When Premium ends. If the subscription expires, is cancelled and runs out, is " +
                    "refunded, or is on hold or paused because of a payment problem, your account returns to " +
                    "a free Specialist account and your listings are hidden from search until Premium is " +
                    "active again. Your listings are not deleted.",
                    "• Plans no longer offered. Only the current ProHost Premium subscription grants Pro Host " +
                    "access. A purchase of a plan we have retired is reviewed by our team, which either " +
                    "activates it or lets the store refund it."
                )
            ),
            LegalSection(
                "8. Prohibited Conduct",
                listOf(
                    "You must not:",
                    "• Post false, misleading, or fraudulent listings or profiles.",
                    "• Use the platform for any unlawful purpose or to facilitate unlawful activity, " +
                    "including any activity prohibited by the sanctions and export-control laws described in " +
                    "Section 14.",
                    "• Harass, threaten or abuse other users.",
                    "• Attempt to circumvent the platform (e.g. taking bookings off-platform after initial " +
                    "contact to avoid future fees).",
                    "• Scrape, reverse-engineer, decompile, or attack the platform's systems, or attempt to " +
                    "gain unauthorised access to any account or data.",
                    "• Impersonate any person or entity, or misrepresent your affiliation with any person or " +
                    "entity.",
                    "• Upload content that infringes a third party's intellectual property, privacy, or other " +
                    "legal rights.",
                    "• Post or send content that is unlawful, hateful, discriminatory, sexually explicit, " +
                    "gratuitously violent, defamatory or that exposes another person's private information " +
                    "(see Section 9.2).",
                    "• Misuse the automated assistant, for example to extract other people's information, to " +
                    "send spam, or to attack the service."
                )
            ),
            LegalSection(
                "9. Content, Intellectual Property, and Infringement Notices",
                listOf(
                    "You retain ownership of content (photos, descriptions) you upload. By uploading content, " +
                    "you grant ProHost a worldwide, royalty-free, non-exclusive licence to store, display and " +
                    "distribute that content within the platform for operational purposes. You represent that " +
                    "you own or have permission to use all content you upload.",
                    "ProHost, its logo and branding are the intellectual property of ProHost and may not be " +
                    "used without written permission.",
                    "9.1 Reporting Infringing Content",
                    "If you believe content on ProHost infringes your copyright, trademark, or other " +
                    "intellectual property right, send a written notice to admin@pro-host.tech identifying: " +
                    "(a) the specific content and its location in the app; (b) your contact information; (c) " +
                    "a statement that you have a good-faith belief the use is unauthorised; and (d) a " +
                    "statement, under penalty of perjury, that the notice is accurate and that you are the " +
                    "rights holder or authorised to act on their behalf. We will review valid notices and may " +
                    "remove or disable access to the reported content, and may terminate the account of a " +
                    "user we determine to be a repeat infringer. A user whose content is removed may submit a " +
                    "counter-notice to the same address; we will forward it to the original complainant and " +
                    "may reinstate the content if no legal action is filed within a reasonable time.",
                    "9.2 Content Standards",
                    "Everything you publish or send through ProHost (listings, photos, descriptions, names, " +
                    "profile pictures and messages to us or our assistant) must be lawful, truthful and " +
                    "respectful. It must not be false or misleading, unlawful, hateful or discriminatory, " +
                    "harassing or threatening, sexually explicit, gratuitously violent, defamatory, " +
                    "fraudulent, spam, or contain another person's private information without permission, " +
                    "and it must not infringe anyone's rights. Listings must show the real space, real prices " +
                    "and your real right to rent it out. We check listings and reports using a mix of " +
                    "automated checks and human review.",
                    "9.3 Reporting, Blocking and Moderation",
                    "• Report. Tell us about any listing, profile, booking behaviour or content that breaks " +
                    "these Terms or the law by emailing admin@pro-host.tech with the ProHost code (for " +
                    "example L-… or U-…) or a link, and what is wrong; you can also use the report option in " +
                    "the app where it is offered. Reports can be made without an account.",
                    "• Our response. We acknowledge reports within 24 hours and act without undue delay: we " +
                    "may ask for more information, warn the user, remove or hide content, limit features, or " +
                    "suspend or terminate an account. For urgent risks to safety we act first. Repeat or " +
                    "serious violations lead to termination.",
                    "• Blocking. If a user is bothering you, use the Block option where the app offers it, or " +
                    "ask us by email and we will prevent that user from contacting you.",
                    "• Reasons and appeals. When we remove content or restrict an account because of a report " +
                    "or our own review, we tell the affected user what we decided and why, and how to contest " +
                    "it. You may contest a decision within six (6) months by replying to our message or " +
                    "writing to admin@pro-host.tech; a person who was not involved in the first decision " +
                    "reviews it.",
                    "9.4 Information for Users in the European Union (Digital Services Act)",
                    "To the extent Regulation (EU) 2022/2065 (the Digital Services Act) applies to ProHost: " +
                    "(a) our single point of contact for users and for authorities of EU Member States and " +
                    "the European Commission is admin@pro-host.tech, and you may write to us in English or " +
                    "Arabic; (b) you can notify us of content you consider illegal by email with a " +
                    "sufficiently substantiated explanation of why it is illegal, the exact location of the " +
                    "content (its code or link), your name and email address, and a statement that you " +
                    "believe the notice is accurate and complete; (c) we send the notifier a confirmation and " +
                    "our decision, and we tell affected users the reasons for any restriction; (d) you may " +
                    "use any certified out-of-court dispute settlement body available in your Member State, " +
                    "in addition to Section 9.3 and your right to go to court; and (e) Pro Hosts who offer " +
                    "spaces to consumers in the EU as traders must give us their name, address, contact " +
                    "details and, where they have one, their trade register number, so that we can show them " +
                    "to consumers where the law requires."
                )
            ),
            LegalSection(
                "10. Disclaimer of Warranties",
                listOf(
                    "THE PROHOST PLATFORM IS PROVIDED \"AS IS\" AND \"AS AVAILABLE\" WITHOUT WARRANTIES OF " +
                    "ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO WARRANTIES OF " +
                    "MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, NON-INFRINGEMENT, ACCURACY, " +
                    "RELIABILITY OR CONTINUOUS AVAILABILITY. PROHOST DOES NOT WARRANT THAT THE PLATFORM WILL " +
                    "BE ERROR-FREE, SECURE OR UNINTERRUPTED, OR THAT ANY LISTING IS ACCURATE, SAFE OR " +
                    "SUITABLE FOR ANY PROFESSIONAL USE.",
                    "Nothing in this Section excludes or limits any warranty, guarantee, or right that cannot " +
                    "lawfully be excluded or limited under the law of your country of residence, including " +
                    "any statutory guarantee of acceptable quality or fitness for purpose available to " +
                    "consumers in that jurisdiction."
                )
            ),
            LegalSection(
                "11. Limitation of Liability",
                listOf(
                    "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE LAW, PROHOST, ITS OFFICERS, EMPLOYEES AND " +
                    "AFFILIATES SHALL NOT BE LIABLE FOR ANY INDIRECT, INCIDENTAL, SPECIAL, CONSEQUENTIAL, " +
                    "PUNITIVE OR EXEMPLARY DAMAGES ARISING OUT OF OR IN CONNECTION WITH YOUR USE OF THE " +
                    "PLATFORM, INCLUDING WITHOUT LIMITATION: LOSS OF REVENUE OR PROFIT, LOSS OF DATA, " +
                    "PERSONAL INJURY, PROPERTY DAMAGE, PROFESSIONAL MALPRACTICE CLAIMS, DAMAGE ARISING FROM A " +
                    "RENTAL AGREEMENT OR DISPUTE, OR BUSINESS INTERRUPTION, EVEN IF ADVISED OF THE " +
                    "POSSIBILITY OF SUCH DAMAGE.",
                    "In jurisdictions that do not permit the total exclusion of liability, ProHost's " +
                    "aggregate liability to you for all claims arising out of or in connection with the " +
                    "platform shall not exceed the greater of: (a) the total amount of fees, if any, paid by " +
                    "you to ProHost in the twelve (12) months preceding the event giving rise to the claim; " +
                    "or (b) USD 100.",
                    "Nothing in these Terms limits liability for: (i) death or personal injury caused by " +
                    "ProHost's gross negligence; (ii) fraud or fraudulent misrepresentation; (iii) any " +
                    "liability that cannot be excluded by applicable law; or (iv) any right you have as a " +
                    "consumer under the mandatory law of your country of residence that cannot be waived by " +
                    "contract (see Section 17.3)."
                )
            ),
            LegalSection(
                "12. Indemnification",
                listOf(
                    "You agree to indemnify, defend and hold harmless ProHost, its officers, directors, " +
                    "employees, agents and affiliates (the \"ProHost Parties\") from and against any claims, " +
                    "actions, demands, liabilities, damages, losses, costs and expenses (including reasonable " +
                    "legal fees and costs of investigation) arising out of or related to:",
                    "• Your use of or access to the platform;",
                    "• Any content you upload or transmit;",
                    "• Your breach of these Terms;",
                    "• Any rental agreement or transaction between you and another user;",
                    "• Any professional activities conducted at a booked space, including malpractice, " +
                    "regulatory violations or injuries;",
                    "• Your violation of any applicable law or regulation, including the sanctions and " +
                    "export-control obligations in Section 14;",
                    "• Any claim that your content, or your use of the platform, infringes or misappropriates " +
                    "the rights of a third party.",
                    "Procedure. ProHost will give you prompt written notice of any claim subject to this " +
                    "indemnity, and reasonable cooperation and information you provide, at your expense, to " +
                    "assist in its defence. ProHost may, at its own expense, elect to assume control of the " +
                    "defence and settlement of any such claim with counsel of its own choosing, and you agree " +
                    "to cooperate with that defence; you may not settle any such claim in a manner that " +
                    "admits fault by, or imposes any obligation on, a ProHost Party without ProHost's prior " +
                    "written consent. This indemnification obligation survives termination of your account " +
                    "and these Terms, and applies regardless of where you or the claim are located, subject " +
                    "only to any limitation that cannot lawfully be excluded under the mandatory law of your " +
                    "country of residence."
                )
            ),
            LegalSection(
                "13. Force Majeure",
                listOf(
                    "ProHost is not liable for any failure or delay in performance resulting from causes " +
                    "beyond its reasonable control, including acts of God, natural disaster, war, armed " +
                    "conflict, civil unrest, terrorism, epidemic or pandemic, governmental order or " +
                    "sanctions, currency or banking-system disruption, power or telecommunications or " +
                    "internet outages, failure of a third-party infrastructure provider (including Google " +
                    "Firebase, Google Play, the Apple App Store, Netlify, or Meta/WhatsApp), or labour " +
                    "disputes. This Section does not excuse your own payment obligations to another user or " +
                    "to ProHost that were already due before the event occurred."
                )
            ),
            LegalSection(
                "14. Export Controls and Sanctions Compliance",
                listOf(
                    "You represent that you are not (a) located in a country or region subject to a " +
                    "comprehensive embargo under the trade-control or sanctions laws of the United Nations, " +
                    "the United States, the European Union, the United Kingdom, or Lebanon, or (b) listed on " +
                    "any denied-party, restricted-party, or sanctioned-person list maintained by those " +
                    "authorities. You agree not to use the platform in violation of any applicable " +
                    "export-control or sanctions law. ProHost may suspend or terminate your account without " +
                    "notice if we reasonably believe your use of the platform violates this Section."
                )
            ),
            LegalSection(
                "15. Third-Party Services",
                listOf(
                    "15.1 Infrastructure, communication and payment services",
                    "ProHost uses Google Firebase infrastructure (Authentication, Firestore, Storage, Cloud " +
                    "Functions, Cloud Messaging) to operate the platform, and a hosting provider for the " +
                    "website. Your use of the platform is also governed by Google Firebase Terms of Service " +
                    "(https://firebase.google.com/terms) and Google Terms of Service " +
                    "(https://policies.google.com/terms). ProHost is not liable for any outage, data loss or " +
                    "security incident attributable to those providers' infrastructure.",
                    "Communications via WhatsApp and Instagram are governed by Meta's own Terms and Privacy " +
                    "Policy. ProHost has no access to, and no control over, the content of your WhatsApp " +
                    "conversations with other users. Purchases made through Google Play or the Apple App " +
                    "Store are governed by that store's own terms and refund policy.",
                    "15.2 Maps",
                    "Maps in the app are provided by Google Maps. By using them you also agree to the Google " +
                    "Maps/Google Earth Additional Terms of Service " +
                    "(https://maps.google.com/help/terms_maps/), and Google's Privacy Policy " +
                    "(https://policies.google.com/privacy) applies to the data Google receives. Locations and " +
                    "distances shown are indicative only.",
                    "15.3 Apple App Store users (iOS)",
                    "If you download the ProHost iOS app, these Terms are the end-user licence agreement for " +
                    "the app, and the following apply in addition to the rest of these Terms. These terms are " +
                    "between you and ProHost only, not Apple:",
                    "• Acknowledgement. You and ProHost acknowledge that this agreement is concluded between " +
                    "you and ProHost only, and not with Apple Inc. (\"Apple\"); ProHost, not Apple, is solely " +
                    "responsible for the app and its content.",
                    "• Scope of licence. ProHost grants you a limited, non-transferable licence to use the " +
                    "app on Apple-branded products that you own or control and as permitted by the Usage " +
                    "Rules in the Apple Media Services Terms and Conditions, except that the app may also be " +
                    "accessed and used by other accounts associated with you through Family Sharing or volume " +
                    "purchasing.",
                    "• Maintenance and support. ProHost, not Apple, is solely responsible for providing " +
                    "maintenance and support for the app (contact admin@pro-host.tech). Apple has no " +
                    "obligation to furnish any maintenance or support services for the app.",
                    "• Warranty. ProHost is solely responsible for any product warranties, whether express or " +
                    "implied by law, to the extent not effectively disclaimed. If the app fails to conform to " +
                    "an applicable warranty, you may notify Apple, and Apple will refund the purchase price " +
                    "of the app (if any) to you; to the maximum extent permitted by law, Apple has no other " +
                    "warranty obligation with respect to the app, and any other claims, losses, liabilities, " +
                    "damages, costs or expenses attributable to a failure to conform to any warranty are " +
                    "ProHost's sole responsibility.",
                    "• Product claims. ProHost, not Apple, is responsible for addressing any claims by you or " +
                    "any third party relating to the app or your possession and/or use of it, including " +
                    "product liability claims, any claim that the app fails to conform to a legal or " +
                    "regulatory requirement, and claims under consumer protection, privacy or similar laws.",
                    "• Intellectual property claims. If a third party claims that the app or your possession " +
                    "and use of it infringes that party's intellectual property rights, ProHost, not Apple, " +
                    "is solely responsible for the investigation, defence, settlement and discharge of the " +
                    "claim.",
                    "• Legal compliance. You represent and warrant that you are not located in a country that " +
                    "is subject to a U.S. Government embargo or that has been designated by the U.S. " +
                    "Government as a \"terrorist supporting\" country, and that you are not listed on any " +
                    "U.S. Government list of prohibited or restricted parties.",
                    "• Developer contact. Questions, complaints or claims about the app can be sent to " +
                    "ProHost at admin@pro-host.tech, or to Georges El Najjar, Maalaka North, Zahle, Central " +
                    "Bekaa, Lebanon, telephone +961 78 885 080.",
                    "• Third-party terms. You must comply with applicable third-party terms of agreement when " +
                    "using the app, for example your wireless data service agreement and the Apple Media " +
                    "Services Terms.",
                    "• Third-party beneficiary. Apple and its subsidiaries are third-party beneficiaries of " +
                    "this agreement, and when you accept it Apple will have the right (and will be deemed to " +
                    "have accepted the right) to enforce it against you as a third-party beneficiary.",
                    "Where Apple's licensed-application terms and these Terms conflict about the matters " +
                    "above, the Apple terms prevail for those matters."
                )
            ),
            LegalSection(
                "16. Termination",
                listOf(
                    "You may delete your account at any time via the app (Profile › Delete Account) or by " +
                    "contacting us. ProHost may suspend or permanently terminate your account at any time, " +
                    "with or without notice, for violation of these Terms, for inactivity, or for any reason " +
                    "at our discretion, subject to any notice period required by mandatory law in your " +
                    "jurisdiction. Upon termination, your access to the platform ceases immediately. " +
                    "Cancelling a subscription is done in the store where you bought it (Section 7.1). " +
                    "Sections 9.1 (as to content already uploaded), 10, 11, 12, 14, and 17 survive " +
                    "termination."
                )
            ),
            LegalSection(
                "17. Governing Law, Your Local Rights, and Dispute Resolution",
                listOf(
                    "17.1 Governing Law",
                    "These Terms, and any dispute or claim arising out of or in connection with them or their " +
                    "subject matter, are governed by the laws of the Republic of Lebanon, without regard to " +
                    "its conflict-of-laws principles.",
                    "17.2 Informal Resolution and Arbitration",
                    "Before filing a claim, you agree to first contact us at admin@pro-host.tech and attempt " +
                    "in good faith to resolve the dispute informally for at least thirty (30) days. If not " +
                    "resolved, any dispute shall be finally and exclusively resolved by binding arbitration " +
                    "on an individual basis, rather than in court, except that either party may bring an " +
                    "individual claim in a small-claims court of competent jurisdiction, and either party may " +
                    "seek urgent injunctive or other equitable relief from a court of competent jurisdiction " +
                    "to prevent actual or threatened infringement, misappropriation, or violation of a " +
                    "party's intellectual-property or confidentiality rights. Unless you and ProHost agree " +
                    "otherwise, the arbitration will be conducted in Beirut, Lebanon, in the English " +
                    "language, before a single arbitrator, under a recognised international arbitration " +
                    "body's rules then in effect. Each party bears its own costs of arbitration, except as " +
                    "the arbitrator may otherwise award. YOU AND PROHOST EACH WAIVE ANY RIGHT TO A JURY TRIAL " +
                    "AND TO PARTICIPATE IN A CLASS, CONSOLIDATED, OR REPRESENTATIVE ACTION.",
                    "17.3 Your Local Mandatory Rights Are Not Affected",
                    "ProHost operates worldwide, and Sections 17.1 and 17.2 are not intended to, and do not, " +
                    "deprive you of any protection you cannot lawfully waive under the mandatory law of your " +
                    "country or state of habitual residence — including, without limitation, your right as a " +
                    "consumer in the European Union or United Kingdom to rely on the mandatory " +
                    "consumer-protection provisions of your own country of residence and to bring proceedings " +
                    "before the courts of that country under Regulation (EU) No 1215/2012 or the equivalent " +
                    "UK provisions; your rights as a consumer in Australia under the Australian Consumer Law; " +
                    "and your rights as a consumer in California or another U.S. state under applicable state " +
                    "consumer-protection or arbitration law. Where mandatory law renders Section 17.2's " +
                    "arbitration or class-action waiver unenforceable as to you, that provision does not " +
                    "apply to you and the dispute will instead proceed before the competent courts identified " +
                    "by that mandatory law, or, failing that, the courts of Beirut, Lebanon under Section " +
                    "17.1.",
                    "17.4 Injunctive Relief",
                    "Nothing in this Section prevents either party from seeking urgent injunctive or other " +
                    "equitable relief from a court of competent jurisdiction."
                )
            ),
            LegalSection(
                "18. Changes to These Terms",
                listOf(
                    "We may amend these Terms at any time. We will give you at least fourteen (14) days' " +
                    "notice of material changes via in-app notification or email (or a longer period where " +
                    "required by mandatory law in your jurisdiction). Continued use of the platform after the " +
                    "effective date of amended Terms constitutes your acceptance of those Terms; if you do " +
                    "not agree to a material change, your remedy is to stop using the platform and delete " +
                    "your account before it takes effect. The changes dated 8 November 2026 take effect for " +
                    "existing users on 22 November 2026."
                )
            ),
            LegalSection(
                "19. General",
                listOf(
                    "• Entire agreement: These Terms, together with the Privacy Policy, constitute the entire " +
                    "agreement between you and ProHost regarding the platform.",
                    "• Severability: If any provision is found unenforceable in your jurisdiction, it is " +
                    "severed as to that jurisdiction only, to the minimum extent necessary, and the remaining " +
                    "provisions continue in full force.",
                    "• Waiver: Failure by ProHost to enforce any provision is not a waiver of future " +
                    "enforcement.",
                    "• No assignment: You may not assign your rights or obligations under these Terms without " +
                    "ProHost's written consent; ProHost may assign these Terms in connection with a merger, " +
                    "acquisition, or sale of assets, or by operation of law, without restricting any right " +
                    "you have under mandatory law as a result.",
                    "• Language: These Terms are provided in English. Any translation is for convenience " +
                    "only; the English version governs in the event of conflict, except where mandatory local " +
                    "law requires the local-language version to control."
                )
            ),
            LegalSection(
                "Contact",
                listOf(
                    "For questions about these Terms:",
                    "• Email: admin@pro-host.tech",
                    "• Website: pro-host.tech"
                )
            )
            // END GENERATED
        )
    )

    val revocationPolicy = LegalDocument(
        id = "revocation_policy",
        title = "Revocation Policy",
        shortDescription = "When and how ProHost may suspend, downgrade, or terminate an account.",
        effectiveDate = EFFECTIVE_DATE,
        sections = listOf(
            LegalSection(
                "1. Purpose of This Policy",
                listOf(
                    "This Revocation Policy explains the account-governance actions ProHost Administrators may take against an account — " +
                    "suspension, reactivation, downgrading a Pro Host back to Specialist, and deletion — the grounds for each, and what each " +
                    "action does and does not do. It forms part of, and should be read together with, the Terms of Use."
                )
            ),
            LegalSection(
                "2. Account Suspension",
                listOf(
                    "ProHost may suspend an account when there are reasonable grounds to believe it has violated the Terms of Use, engaged in " +
                    "suspected fraud (including uploading a false or fraudulent ownership document), received a credible complaint from " +
                    "another user, or is the subject of a legal request from a competent authority.",
                    "Effect of suspension: a suspended account is signed out of every active session immediately, cannot sign back in, cannot " +
                    "create new Listings or submit new booking requests, and — for a suspended Pro Host — every published Listing is marked as " +
                    "belonging to a suspended account and is hidden from public search results in Discovery (though it remains visible to the " +
                    "Pro Host themselves and to Administrators, clearly marked as suspended, so nothing is silently deleted). A suspension does " +
                    "not delete your account, your booking history, or your data.",
                    "Suspension is not necessarily permanent. An Administrator may reactivate a suspended account once the underlying concern is " +
                    "resolved, at ProHost's discretion. ProHost is not obligated to provide advance notice before a suspension takes effect " +
                    "where doing so would undermine the purpose of the action (for example, in a suspected-fraud case), but will provide the " +
                    "reason for a suspension upon request through the in-app support channel."
                )
            ),
            LegalSection(
                "3. Revocation of the Pro Host Role",
                listOf(
                    "Separately from suspension, ProHost Administrators may revoke a Pro Host's role and downgrade the account back to " +
                    "Specialist — for example, for a serious or repeated Terms violation specific to that account's Listings, a well-founded " +
                    "dispute pattern with multiple Specialists, or a determination that the account's proof-of-right-to-rent documentation was " +
                    "materially false.",
                    "Effect of revocation: the account immediately loses Pro Host privileges and reverts to the default Specialist role " +
                    "(retaining full ability to browse and book workspaces as a Specialist, since every Pro Host account is, and remains, a " +
                    "Specialist underneath). Every Listing the account had published is marked inactive/expired — shown as such in Discovery, " +
                    "and excluded by a Specialist's \"active subscription only\" search filter, but not deleted, so the historical record and " +
                    "any completed bookings against those Listings are preserved. Regaining the Pro Host role " +
                    "requires an active ProHost Premium subscription again, exactly as it did the first time.",
                    "Revocation does not, by itself, cancel or affect a booking that was already ACCEPTED before the revocation — that booking " +
                    "continues to its natural conclusion or is cancelled through the ordinary cancellation flow described in the Terms of Use, " +
                    "Section 6, like any other accepted booking."
                )
            ),
            LegalSection(
                "4. Account Deletion",
                listOf(
                    "You may delete your own account at any time (Profile → Delete Account). ProHost Administrators may also delete an account, " +
                    "typically reserved for severe or repeated violations, a valid legal deletion request, or at your own explicit request " +
                    "submitted through the in-app support channel.",
                    "Effect of deletion: unlike suspension or revocation, deletion is intended to be permanent and removes your profile and " +
                    "published Listings from the Platform, subject to the retention exceptions described in the Privacy Policy (Section 5) — " +
                    "principally, records already embedded in another user's booking or audit history, and data ProHost must retain to satisfy a " +
                    "legal, accounting, or dispute-resolution obligation."
                )
            ),
            LegalSection(
                "5. No Automated, Unreviewable Decision",
                listOf(
                    "Every suspension, revocation, and deletion action described in this Policy is a deliberate action taken by a human ProHost " +
                    "Administrator through the Platform's governance tools — none of these actions happen automatically based solely on an " +
                    "algorithmic score. Every such action is recorded in ProHost's immutable audit-security log, identifying the acting " +
                    "Administrator, the timestamp, and (where applicable) the reason, so it can be reviewed after the fact."
                )
            ),
            LegalSection(
                "6. Requesting Review of a Governance Decision",
                listOf(
                    "If your account has been suspended, your Pro Host role revoked, or your account deleted, and you believe this was done in " +
                    "error, you may request a review through the in-app support channel or by email to admin@pro-host.tech. " +
                    "Include the email you signed up with so we can locate your account record. ProHost will review the " +
                    "request and respond within a reasonable time.",
                    "Legal disclaimer: This document is a genuine, substantive draft prepared specifically for ProHost's actual governance " +
                    "features and Lebanon's applicable legal framework. It does not constitute legal advice, and ProHost recommends this " +
                    "document be reviewed by a licensed Lebanese attorney before being relied upon as final."
                )
            )
        )
    )

    val all: List<LegalDocument> = listOf(privacyPolicy, termsOfUse, revocationPolicy)

    // Deliberately NOT in [all] — this isn't a general policy shown in the Legal
    // menu, it's a specific template a Pro Host downloads from "Get Listing
    // Verified" (see OwnerHubScreen's listing card), gets signed by hand by the
    // real property owner, and re-uploads as verificationDocUrl to earn the
    // Listing Verified badge (RERENTAL_AUTHORIZATION path — see
    // ListingVerificationDocType). The four identity lines are genuine blank
    // placeholders, not pre-filled from either account, even though the Pro
    // Host's own name is technically known — they're meant to be filled by hand
    // before the document is printed and physically signed.
    val rerentalAuthorizationTemplate = LegalDocument(
        id = "rerental_authorization_template",
        title = "Space Re-Rental Authorization Statement",
        shortDescription = "A template the real property owner signs to authorize a Pro Host to re-rent their space through ProHost.",
        effectiveDate = EFFECTIVE_DATE,
        sections = listOf(
            LegalSection(
                "Parties",
                listOf(
                    "Property Owner Full Name: ______________________________",
                    "Property Owner ID Number: ______________________________",
                    "Pro Host (Authorized Renter) Full Name: ______________________________",
                    "Pro Host (Authorized Renter) ID Number: ______________________________",
                    "ProHost Listing Reference: ______________________________"
                )
            ),
            LegalSection(
                "1. Grant of Authorization",
                listOf(
                    "I, the Property Owner named above, am the lawful owner of, or otherwise hold the legal right to let, the property " +
                    "identified by the ProHost Listing Reference above (\"the Space\"). I authorize the Pro Host named above to list, offer, and " +
                    "re-rent the Space to third parties (\"Specialists\") through the ProHost platform, on an hourly, shift, daily, and/or " +
                    "monthly basis, at the Pro Host's own discretion as to pricing, scheduling, and choice of Specialist, for as long as this " +
                    "authorization remains in effect.",
                    "This authorization may be withdrawn by the Property Owner at any time by written notice to the Pro Host; it does not, " +
                    "however, retroactively affect any booking already accepted by a Specialist before such notice, which remains the Pro Host's " +
                    "responsibility to honor or resolve directly with that Specialist."
                )
            ),
            LegalSection(
                "2. Release and Indemnification",
                listOf(
                    "The Property Owner agrees that ProHost (the platform operator) and the Pro Host named above are each held harmless from, " +
                    "and released from any liability arising out of, the Property Owner later objecting to, interfering with, or otherwise " +
                    "questioning the Pro Host's re-renting of the Space in a manner consistent with this authorization. ProHost is not a party " +
                    "to, and assumes no responsibility for, the arrangement between the Property Owner and the Pro Host — this statement exists " +
                    "solely so ProHost can confirm the Pro Host has the Property Owner's permission to list the Space, and is kept on file for " +
                    "that purpose only.",
                    "Nothing in this statement transfers ownership of the Space, and it does not itself constitute a lease between the Property " +
                    "Owner and any Specialist — the Pro Host remains solely responsible for their own arrangements with, and obligations to, " +
                    "both the Property Owner and every Specialist they rent to."
                )
            ),
            LegalSection(
                "3. Signatures",
                listOf(
                    "Property Owner Signature: ______________________________     Date: ____________",
                    "This is a template provided for convenience — it is not legal advice, and the parties are encouraged to have it reviewed by " +
                    "a licensed attorney before signing, and to keep their own copy of the signed original in addition to the one uploaded to " +
                    "ProHost."
                )
            )
        )
    )
}

/**
 * Renders this document as a self-contained HTML string — the "HTML popup" view
 * shown in [com.example.ui.components.LegalDocumentDialog] via a WebView. Kept as
 * a plain function here (not a template file) so it stays in lockstep with
 * [LegalSection]/[LegalDocument] without a separate asset to keep in sync.
 */
fun LegalDocument.toHtml(): String {
    val sectionsHtml = sections.joinToString("\n") { section ->
        val paragraphsHtml = section.paragraphs.joinToString("\n") { p -> "<p>${escapeHtml(p)}</p>" }
        "<h2>${escapeHtml(section.heading)}</h2>\n$paragraphsHtml"
    }
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="utf-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <style>
                body { font-family: sans-serif; padding: 16px; line-height: 1.5; color: #1a1a1a; background: #ffffff; }
                h1 { font-size: 20px; margin-bottom: 4px; }
                .meta { color: #666; font-size: 12px; margin-bottom: 20px; }
                h2 { font-size: 15px; margin-top: 22px; margin-bottom: 6px; color: #0b3d91; }
                p { font-size: 13px; margin: 6px 0; text-align: justify; }
                @media (prefers-color-scheme: dark) {
                    body { color: #e8e8e8; background: #121212; }
                    .meta { color: #a0a0a0; }
                    h2 { color: #7fb0ff; }
                }
            </style>
        </head>
        <body>
            <h1>${escapeHtml(title)}</h1>
            <div class="meta">ProHost &mdash; Effective ${escapeHtml(effectiveDate)}</div>
            $sectionsHtml
        </body>
        </html>
    """.trimIndent()
}

private fun escapeHtml(text: String): String {
    return text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
