package com.example.legal

/**
 * Structured legal-document content — the single source of truth rendered both as
 * the in-app HTML popup (LegalDocumentDialog.kt) and the downloadable A5 PDF
 * (LegalPdfGenerator.kt), so the two can never drift out of sync with each other.
 *
 * These three documents are drafted specifically for ProHost's actual features in
 * this app (phone-OTP registration, self-attested ID/ownership documents with no
 * admin review, workspace listings with real GPS location, booking requests, an
 * admin suspend/revoke governance model, WhatsApp hand-off for direct contact, and
 * settlement that happens entirely outside the app) and reference the Lebanese
 * legal framework most relevant to a Lebanon-only marketplace app — principally
 * Law No. 81/2018 (Electronic Transactions and Personal Data), the Lebanese Penal
 * Code's provisions on privacy and breach of trust, Law No. 659/2005 (Consumer
 * Protection), and the Lebanese Code of Obligations and Contracts for the
 * contractual terms. This is a genuine, substantive draft — not placeholder
 * lorem-ipsum — but it is still a draft: it should be reviewed by a licensed
 * Lebanese attorney before being relied on as the app's final legal terms,
 * exactly as the in-app disclaimer on each document states.
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

private const val EFFECTIVE_DATE = "September 6, 2026"
private const val OPERATOR_LINE = "ProHost (\"the Platform\", \"we\", \"us\") is a Lebanon-focused marketplace connecting independent healthcare and professional-services specialists (\"Specialists\") with workspace hosts (\"Pro Hosts\") who list clinics, offices, and shared practice spaces (\"Listings\") for rent."

object LegalContent {

    val privacyPolicy = LegalDocument(
        id = "privacy_policy",
        title = "Privacy Policy",
        shortDescription = "How ProHost collects, uses, stores, and protects your personal data.",
        effectiveDate = EFFECTIVE_DATE,
        sections = listOf(
            LegalSection(
                "1. Who We Are and Scope of This Policy",
                listOf(
                    OPERATOR_LINE,
                    "This Privacy Policy explains what personal data ProHost collects from Specialists, Pro Hosts, and anyone browsing the app; why we collect it; how it is stored, secured, and shared; and the rights you have over it. It applies to the ProHost Android application and any associated backend services, and is written to reflect Lebanese law — principally Law No. 81/2018 on Electronic Transactions and Personal Data, and, to the extent applicable, general principles recognized under the Lebanese Penal Code's provisions protecting private life and correspondence (Articles 579–580) and the Code of Obligations and Contracts governing contractual good faith. Where our practices also track internationally recognized data-protection norms (such as the EU General Data Protection Regulation) more closely than Lebanese law strictly requires, we do so voluntarily, as a matter of good practice for our users, not because Lebanese law itself currently imposes an equivalent standalone comprehensive data-protection regime."
                )
            ),
            LegalSection(
                "2. Data We Collect",
                listOf(
                    "Account & identity data: full name, email address, phone number (verified by SMS one-time code through Firebase Phone Authentication), country/governorate/city, and the professional specialty you declare.",
                    "Verification documents: a government-issued ID document you upload at registration, and — for Pro Hosts — a proof-of-ownership or right-to-rent document uploaded when publishing a Listing. Both are stored as private files in Firebase Cloud Storage, accessible only to you and to ProHost Administrators; ProHost does not operate an automated or manual review/approval process for either document — they are kept on file, not verified for authenticity, and you remain solely responsible for the accuracy and lawfulness of what you upload.",
                    "Location data: the precise GPS coordinates you place a pin at when publishing a Listing (used to plot it on the discovery map), and, with your device permission, your approximate location while browsing the map to show nearby Listings. We do not track your location in the background or outside active use of the map features.",
                    "Listing and booking data: everything you enter to publish or book a workspace — title, address, photos, pricing, availability, and the details of any booking request you send or receive, including the other party's name, phone number, and contact details as necessary to complete the booking.",
                    "Communications metadata: when you use an in-app \"WhatsApp\" button to contact another user, ProHost does not see or store the content of that WhatsApp conversation (it happens entirely within WhatsApp, a third-party service you must have installed separately), but we do record that the hand-off occurred — timestamp, initiating account, and target — in our internal audit log, for accountability and dispute-resolution purposes only.",
                    "Device and diagnostic data: your Firebase Cloud Messaging push-notification token (to deliver booking alerts to your device), and standard technical logs (crash reports, IP address at time of request, app version) generated by Firebase's infrastructure.",
                    "We do not collect payment-card or bank-account data of any kind. ProHost has no in-app payment processing for booking rent — Specialists and Pro Hosts settle rent between themselves, outside the app, by whatever means they agree (commonly cash or bank transfer); subscription/package fees for Pro Host listing tiers, where applicable, are processed through Whish Money, a third-party Lebanese payment provider, and ProHost's servers only ever receive a payment confirmation from Whish, never your card or wallet credentials."
                )
            ),
            LegalSection(
                "3. Why We Process Your Data (Legal Basis and Purpose)",
                listOf(
                    "To create and secure your account, verify your phone number, and authenticate you on each sign-in.",
                    "To operate the core marketplace function: displaying Listings, matching Specialists with Pro Hosts, and processing booking requests through to acceptance or decline.",
                    "To enable direct contact between a Specialist and a Pro Host (the WhatsApp hand-off), which you initiate voluntarily on each occasion.",
                    "To send you push notifications about your own bookings, listings, and account status (for example, a booking accepted, a payment reminder, or an account suspension).",
                    "To maintain a security and governance audit trail — every listing action, booking action, WhatsApp hand-off, and Administrator action is logged with a timestamp and actor identity, so that misuse, disputes, or account-suspension decisions can be investigated and, where appropriate, explained to you.",
                    "To comply with a legal obligation, respond to a lawful request from a competent Lebanese authority, or establish, exercise, or defend a legal claim.",
                    "Where we process data for a purpose not listed here, we will seek your consent first."
                )
            ),
            LegalSection(
                "4. Who We Share Data With",
                listOf(
                    "Firebase (Google Ireland Limited / Google LLC): our sole backend infrastructure provider — authentication, database (Cloud Firestore), file storage (Cloud Storage), push notifications (Cloud Messaging), and serverless backend logic (Cloud Functions) all run on Firebase. Data may accordingly be processed and stored on Google's servers outside Lebanon (see Section 6, International Transfers).",
                    "Whish Money: receives only the minimum information needed to process a subscription/package payment you initiate (your name, phone, and the amount) — it never receives, and ProHost never stores, your card or wallet credentials.",
                    "The other party to a booking or Listing: a Specialist's name, specialty, and contact details are shared with a Pro Host when the Specialist submits a booking request to that Pro Host's Listing (and vice versa) — this is inherent to how the marketplace functions, and cannot be turned off without disabling the booking feature itself for your account.",
                    "Lebanese authorities: only where required by a valid legal process (a court order, a request from the Public Prosecution, or an equivalent binding legal demand) or to protect the rights, property, or safety of ProHost, our users, or the public.",
                    "We do not sell your personal data to advertisers or third-party data brokers, and we do not run third-party advertising on the ProHost app."
                )
            ),
            LegalSection(
                "5. Data Retention",
                listOf(
                    "We retain your account and profile data for as long as your account remains active, and for a reasonable period afterward (currently up to 24 months) to resolve any outstanding disputes, satisfy legal or accounting retention obligations, and prevent fraudulent re-registration.",
                    "Audit-security logs (the record of listing, booking, WhatsApp hand-off, and Administrator actions described in Section 3) are retained indefinitely as an immutable governance record, since their evidentiary value does not diminish over time — however, they are never publicly visible and are accessible only to ProHost Administrators.",
                    "If you delete your account (Section 8), we delete your profile, uploaded documents, and Listings within a reasonable operational period, except where a copy must be retained for the legal or dispute-resolution reasons above, or where the data has already been embedded in another user's booking/audit record (for example, a completed booking's historical record is not erased from the other party's booking history merely because you delete your own account)."
                )
            ),
            LegalSection(
                "6. International Data Transfers",
                listOf(
                    "Because ProHost is built entirely on Firebase, your data may be stored and processed on Google's servers located outside Lebanon, potentially including the European Union, the United States, or other jurisdictions where Google operates data centers. By using ProHost, you acknowledge and accept that this cross-border transfer is a necessary and unavoidable feature of the app's infrastructure. Google maintains its own data-protection and security certifications for Firebase; we encourage you to review Google's Firebase-specific privacy and security documentation for further detail on their safeguards.",
                    "We reserve the right to migrate our backend infrastructure — including to a different cloud provider, a different region, or a data store we operate ourselves — at any time, for reasons including but not limited to cost, performance, regulatory compliance, or the development of a successor or related application. Your data will be migrated under the same protections described in this Policy, and we will update this Policy and, where the change is material, notify you in-app in advance of the migration taking effect."
                )
            ),
            LegalSection(
                "7. Data Security",
                listOf(
                    "Access to your account requires phone-number verification via a one-time SMS code. Administrator access to the platform's governance tools is itself role-gated by server-verified credentials, not a client-side toggle.",
                    "Sensitive fields — your account role, verification status, suspension status, and payment/entitlement records — can only ever be written by our server-side Cloud Functions, never directly by any app (including a modified or reverse-engineered one), which materially reduces the risk of client-side tampering.",
                    "Uploaded documents (ID, proof of ownership) are stored in access-controlled Cloud Storage locations readable only by you and by Administrators — they are never publicly listed or indexed.",
                    "No system is perfectly secure. If we become aware of a data breach that creates a real risk to your rights or freedoms, we will notify affected users through the app and, where legally required, notify the competent Lebanese authority, without undue delay."
                )
            ),
            LegalSection(
                "8. Your Rights and How to Exercise Them",
                listOf(
                    "You may access, correct, or update most of your profile information directly in the app's Profile screen at any time.",
                    "You may request a copy of the personal data we hold about you, request its deletion, or object to a specific processing activity, by contacting us using the details in Section 11. We will respond within a reasonable time, and in any case no later than 30 days, except where a longer period is justified by the complexity of the request.",
                    "Deleting your account is available in-app (Profile → Account Settings) and takes effect subject to the retention exceptions described in Section 5.",
                    "If you believe your data has been mishandled, you may also raise a complaint with the competent Lebanese authority responsible for electronic transactions and data matters under Law No. 81/2018, in addition to contacting us directly."
                )
            ),
            LegalSection(
                "9. Children's Privacy",
                listOf(
                    "ProHost is intended for licensed and practicing professionals and property hosts, and is not directed at or intended for use by anyone under the age of 18. We do not knowingly collect personal data from a minor. If we learn that we have inadvertently collected data from a minor, we will delete it promptly."
                )
            ),
            LegalSection(
                "10. Changes to This Policy",
                listOf(
                    "We may update this Privacy Policy from time to time to reflect changes in our practices, our infrastructure, or the law. We will post the updated version in-app with a new effective date, and for material changes we will provide reasonably prominent notice (such as an in-app banner) before the change takes effect. Continued use of ProHost after an update constitutes acceptance of the revised Policy."
                )
            ),
            LegalSection(
                "11. Contact Us",
                listOf(
                    "For any privacy question, access/deletion request, or complaint, contact ProHost's data-protection point of contact through the in-app support channel listed in your Profile screen, or by the email address published on ProHost's Google Play Store listing.",
                    "Legal disclaimer: This document is a genuine, substantive draft prepared specifically for ProHost's actual features and Lebanon's applicable legal framework. It is provided for transparency to users and to support Google Play Store data-safety submission requirements, but it does not constitute legal advice, and ProHost recommends this Policy be reviewed by a licensed Lebanese attorney before being relied upon as final."
                )
            )
        )
    )

    val termsOfUse = LegalDocument(
        id = "terms_of_use",
        title = "Terms of Use",
        shortDescription = "The rules governing your use of the ProHost marketplace.",
        effectiveDate = EFFECTIVE_DATE,
        sections = listOf(
            LegalSection(
                "1. Acceptance of These Terms",
                listOf(
                    OPERATOR_LINE,
                    "By creating an account or otherwise using ProHost, you agree to be bound by these Terms of Use, our Privacy Policy, and our Revocation Policy, together forming the entire agreement between you and ProHost. If you do not agree, do not use the app. These Terms are governed by, and interpreted in accordance with, the laws of the Republic of Lebanon, including the Lebanese Code of Obligations and Contracts, Law No. 81/2018 on Electronic Transactions and Personal Data (which recognizes the validity of electronic contracts and consent given through an app), and Law No. 659/2005 on Consumer Protection where applicable to your use of the Platform."
                )
            ),
            LegalSection(
                "2. Eligibility and Account Registration",
                listOf(
                    "You must be at least 18 years old and legally capable of entering into a binding contract under Lebanese law to register. You must provide accurate registration information and a real, working phone number, which you verify by SMS one-time code — this is currently the only sign-in method the Platform offers, alongside optional Google Sign-In linked to the same verified phone number.",
                    "Every new account is registered with the standard \"Specialist\" role. The \"Pro Host\" role — which allows publishing workspace Listings — is granted automatically and exclusively upon your successful payment of a Pro Host subscription/package fee through Whish Money; there is no other way to obtain it, and ProHost Administrators do not grant it manually or for free except in the ordinary operation of that payment flow.",
                    "You are responsible for maintaining the confidentiality of your account and for all activity that occurs under it. Notify us immediately if you suspect unauthorized access."
                )
            ),
            LegalSection(
                "3. Self-Attested Documents — No Verification by ProHost",
                listOf(
                    "At registration, you upload a government-issued ID document. If you become a Pro Host, you additionally upload a document evidencing your ownership of, or right to rent out, each Listing you publish.",
                    "IMPORTANT: ProHost does not review, verify, or approve either document. They are kept on file for accountability purposes only. You represent and warrant that every document you upload is genuine, current, and accurately represents your identity and, for a proof-of-ownership document, your actual legal right to lease the specific space listed. You are solely and fully responsible for the truthfulness of these representations, and for any consequence — civil, criminal, or otherwise — of uploading a false or fraudulent document, including potential liability under the Lebanese Penal Code's provisions on forgery (Articles 453 et seq.) and fraud (Articles 655 et seq.)."
                )
            ),
            LegalSection(
                "4. Listings and Bookings",
                listOf(
                    "A Pro Host is solely responsible for the accuracy of every Listing they publish — description, photos, pricing, location pin, availability, and facilities — and for having the legal right to offer that space for rent.",
                    "A booking request creates a direct arrangement between the requesting Specialist and the Pro Host once the Pro Host accepts it and uploads a signed leasing agreement between the two of you (outside the app). ProHost is a technology platform that facilitates this introduction and record-keeping — ProHost is not a party to, and assumes no liability under, the leasing agreement itself, nor does ProHost broker, negotiate, or guarantee the terms either party agrees to.",
                    "Package-tier listing limits (the number of active Listings a Pro Host may publish under their current subscription tier) are enforced by the Platform and may change if ProHost adjusts its pricing tiers; you will be notified in-app of your current tier and limit."
                )
            ),
            LegalSection(
                "5. Payments and Settlement",
                listOf(
                    "Rent for a booked workspace is settled entirely outside the app, directly between the Specialist and the Pro Host, on whatever lawful terms they agree between themselves. ProHost has no in-app payment flow for booking rent, does not collect, hold, or disburse rental payments, and has no visibility into whether or how rent was actually paid beyond what either party chooses to record via the in-app \"Payment Due Reminder\" feature (which is a courtesy notification only, not proof of payment or non-payment).",
                    "Where a Pro Host subscription/package fee applies, it is processed through Whish Money at the price displayed in-app at the time of purchase. Fees, once successfully charged, are non-refundable except where required by Lebanese consumer-protection law or expressly stated otherwise by ProHost.",
                    "ProHost is not responsible for, and disclaims all liability arising from, any dispute between a Specialist and a Pro Host over rent, damages, deposits, or any other term of their leasing arrangement — you are strongly encouraged to document your agreement clearly and to consider your own legal recourse under ordinary Lebanese contract and tenancy principles for any such dispute."
                )
            ),
            LegalSection(
                "6. Conduct and Prohibited Uses",
                listOf(
                    "You agree not to: (a) upload false, misleading, or fraudulent information or documents; (b) use the Platform to harass, defraud, or discriminate against another user; (c) attempt to bypass, tamper with, or reverse-engineer the Platform's role, verification, or payment systems; (d) list or attempt to book a space you do not have the legal right to offer or occupy; (e) use the Platform for any purpose that violates Lebanese law, including but not limited to money laundering, tax evasion, or the operation of an unlicensed medical or professional practice where a license is legally required.",
                    "ProHost reserves the right to investigate suspected violations using the audit-log and governance tools described in the Privacy Policy, and to take action as described in the Revocation Policy."
                )
            ),
            LegalSection(
                "7. Cancellation of an Accepted Booking",
                listOf(
                    "Either the Specialist or the Pro Host may cancel a booking that has already been accepted (an \"early termination\"), directly in the app, by selecting a reason code (for example: schedule conflict, an alternative space found, practice relocation or closure, a property condition issue, or mutual agreement) and, optionally, adding a note. The other party is notified immediately.",
                    "This in-app cancellation action ends the booking's status on the Platform; it does not, by itself, determine any refund, penalty, notice period, or other consequence between the two of you under your outside-the-app leasing agreement — those consequences are governed entirely by whatever you agreed with the other party and, failing express agreement, by ordinary Lebanese contract-law principles applicable to your arrangement. ProHost takes no position on, and has no role in adjudicating, who owes what to whom following a cancellation.",
                    "A booking request that has not yet been accepted may be withdrawn by the requesting Specialist at any time before the Pro Host responds, with no reason required."
                )
            ),
            LegalSection(
                "8. Suspension, Revocation, and Termination",
                listOf(
                    "ProHost may suspend, restrict, or terminate your account for a violation of these Terms, suspected fraud, a legal requirement, or any of the reasons described in our Revocation Policy, which forms part of these Terms by reference.",
                    "You may delete your own account at any time from the Profile screen, subject to the data-retention exceptions in the Privacy Policy."
                )
            ),
            LegalSection(
                "9. Disclaimers and Limitation of Liability",
                listOf(
                    "The Platform is provided \"as is\" and \"as available.\" ProHost does not guarantee that any Listing is accurate, that any Specialist or Pro Host is who they claim to be beyond the self-attested documents described in Section 3, or that the Platform will be uninterrupted or error-free.",
                    "To the maximum extent permitted by Lebanese law, ProHost's aggregate liability arising out of or relating to your use of the Platform shall not exceed the total fees you have paid to ProHost in the twelve (12) months preceding the claim, except for liability that cannot be limited or excluded under mandatory Lebanese law (including liability for fraud or willful misconduct)."
                )
            ),
            LegalSection(
                "10. Right to Migrate the Platform",
                listOf(
                    "ProHost reserves the right, at its sole discretion, to modify, discontinue, or migrate any part of the Platform's technical infrastructure, including migrating your account and data to a successor application, a different backend provider, or a related ProHost product, should ProHost develop one. Where such a migration is material, we will provide reasonable advance in-app notice and update this Terms of Use and the Privacy Policy accordingly."
                )
            ),
            LegalSection(
                "11. Governing Law and Disputes",
                listOf(
                    "These Terms are governed by the laws of the Republic of Lebanon. Any dispute arising out of or relating to these Terms or your use of the Platform shall be subject to the exclusive jurisdiction of the competent courts of Beirut, Lebanon, without regard to conflict-of-law principles, except where mandatory consumer-protection law provides otherwise.",
                    "Legal disclaimer: This document is a genuine, substantive draft prepared specifically for ProHost's actual features and Lebanon's applicable legal framework. It does not constitute legal advice, and ProHost recommends this document be reviewed by a licensed Lebanese attorney before being relied upon as final."
                )
            )
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
                    "This Revocation Policy explains the account-governance actions ProHost Administrators may take against an account — suspension, reactivation, downgrading a Pro Host back to Specialist, and deletion — the grounds for each, and what each action does and does not do. It forms part of, and should be read together with, the Terms of Use."
                )
            ),
            LegalSection(
                "2. Account Suspension",
                listOf(
                    "ProHost may suspend an account when there are reasonable grounds to believe it has violated the Terms of Use, engaged in suspected fraud (including uploading a false or fraudulent ID or ownership document), received a credible complaint from another user, or is the subject of a legal request from a competent Lebanese authority.",
                    "Effect of suspension: a suspended account is signed out of every active session immediately, cannot sign back in, cannot create new Listings or submit new booking requests, and — for a suspended Pro Host — every published Listing is marked as belonging to a suspended account and is hidden from public search results in Discovery (though it remains visible to the Pro Host themselves and to Administrators, clearly marked as suspended, so nothing is silently deleted). A suspension does not delete your account, your booking history, or your data.",
                    "Suspension is not necessarily permanent. An Administrator may reactivate a suspended account once the underlying concern is resolved, at ProHost's discretion. ProHost is not obligated to provide advance notice before a suspension takes effect where doing so would undermine the purpose of the action (for example, in a suspected-fraud case), but will provide the reason for a suspension upon request through the in-app support channel."
                )
            ),
            LegalSection(
                "3. Revocation of the Pro Host Role",
                listOf(
                    "Separately from suspension, ProHost Administrators may revoke a Pro Host's role and downgrade the account back to Specialist — for example, for a serious or repeated Terms violation specific to that account's Listings, a well-founded dispute pattern with multiple Specialists, or a determination that the account's proof-of-right-to-rent documentation was materially false.",
                    "Effect of revocation: the account immediately loses Pro Host privileges and reverts to the default Specialist role (retaining full ability to browse and book workspaces as a Specialist, since every Pro Host account is, and remains, a Specialist underneath). Every Listing the account had published is marked inactive/expired — shown as such in Discovery, and excluded by a Specialist's \"active subscription only\" search filter, but not deleted, so the historical record and any completed bookings against those Listings are preserved. A revoked account's Pro Host subscription tier resets to the entry (Pay-As-You-Go) tier; any renewed access to the Pro Host role again requires a new, successful subscription/package payment, exactly as it did the first time.",
                    "Revocation does not, by itself, cancel or affect a booking that was already ACCEPTED before the revocation — that booking continues to its natural conclusion or is cancelled through the ordinary cancellation flow described in the Terms of Use, Section 7, like any other accepted booking."
                )
            ),
            LegalSection(
                "4. Account Deletion",
                listOf(
                    "You may delete your own account at any time (Profile → Account Settings). ProHost Administrators may also delete an account, typically reserved for severe or repeated violations, a valid legal deletion request, or at your own explicit request submitted through the in-app support channel.",
                    "Effect of deletion: unlike suspension or revocation, deletion is intended to be permanent and removes your profile and published Listings from the Platform, subject to the retention exceptions described in the Privacy Policy (Section 5) — principally, records already embedded in another user's booking or audit history, and data ProHost must retain to satisfy a legal, accounting, or dispute-resolution obligation."
                )
            ),
            LegalSection(
                "5. No Automated, Unreviewable Decision",
                listOf(
                    "Every suspension, revocation, and deletion action described in this Policy is a deliberate action taken by a human ProHost Administrator through the Platform's governance tools — none of these actions happen automatically based solely on an algorithmic score. Every such action is recorded in ProHost's immutable audit-security log, identifying the acting Administrator, the timestamp, and (where applicable) the reason, so it can be reviewed after the fact."
                )
            ),
            LegalSection(
                "6. Requesting Review of a Governance Decision",
                listOf(
                    "If your account has been suspended, your Pro Host role revoked, or your account deleted, and you believe this was done in error, you may request a review through the in-app support channel or the contact email published on ProHost's Google Play Store listing. Include your registered phone number or email so we can locate your account record. ProHost will review the request and respond within a reasonable time.",
                    "Legal disclaimer: This document is a genuine, substantive draft prepared specifically for ProHost's actual governance features and Lebanon's applicable legal framework. It does not constitute legal advice, and ProHost recommends this document be reviewed by a licensed Lebanese attorney before being relied upon as final."
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
                    "I, the Property Owner named above, am the lawful owner of, or otherwise hold the legal right to let, the property identified by the ProHost Listing Reference above (\"the Space\"). I authorize the Pro Host named above to list, offer, and re-rent the Space to third parties (\"Specialists\") through the ProHost platform, on an hourly, shift, daily, and/or monthly basis, at the Pro Host's own discretion as to pricing, scheduling, and choice of Specialist, for as long as this authorization remains in effect.",
                    "This authorization may be withdrawn by the Property Owner at any time by written notice to the Pro Host; it does not, however, retroactively affect any booking already accepted by a Specialist before such notice, which remains the Pro Host's responsibility to honor or resolve directly with that Specialist."
                )
            ),
            LegalSection(
                "2. Release and Indemnification",
                listOf(
                    "The Property Owner agrees that ProHost (the platform operator) and the Pro Host named above are each held harmless from, and released from any liability arising out of, the Property Owner later objecting to, interfering with, or otherwise questioning the Pro Host's re-renting of the Space in a manner consistent with this authorization. ProHost is not a party to, and assumes no responsibility for, the arrangement between the Property Owner and the Pro Host — this statement exists solely so ProHost can confirm the Pro Host has the Property Owner's permission to list the Space, and is kept on file for that purpose only.",
                    "Nothing in this statement transfers ownership of the Space, and it does not itself constitute a lease between the Property Owner and any Specialist — the Pro Host remains solely responsible for their own arrangements with, and obligations to, both the Property Owner and every Specialist they rent to."
                )
            ),
            LegalSection(
                "3. Signatures",
                listOf(
                    "Property Owner Signature: ______________________________     Date: ____________",
                    "This is a template provided for convenience — it is not legal advice, and the parties are encouraged to have it reviewed by a licensed attorney before signing, and to keep their own copy of the signed original in addition to the one uploaded to ProHost."
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
