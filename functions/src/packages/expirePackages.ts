import { onSchedule } from "firebase-functions/v2/scheduler";
import { getAuth } from "firebase-admin/auth";
import { getFirestore } from "firebase-admin/firestore";
import { logger } from "firebase-functions/v2";
import { recordAuditLog } from "../lib/auditLog";
import { sendPushToUser } from "../lib/push";
import { setClaimsThenFirestore } from "../lib/roles";
import { sendEmail } from "../lib/email";
import { subscriptionExpiringTemplate, subscriptionExpiredTemplate, UserContext } from "../lib/emailTemplates";
import { planLabel } from "../billing/playCatalog";
import { queryPlaySubscription } from "../billing/billingHelpers";
import { syncSubscription } from "../billing/subscriptionService";
import { classifyPlayError } from "../billing/playSubscription";
import { PlayRecheck, expiryDecision } from "./expiryLogic";
import { notifyAdminsOfSubscriptionChange } from "../billing/adminBillingAlerts";
import "../lib/admin";
import { sendGa4Event } from "../lib/ga4";

/**
 * Real, enforced package expiry. ownerPackageExpiryMillis was previously
 * written on every OWNER_PACKAGE grant but never read/checked anywhere, so no
 * package (PAYG or tiered) ever actually lapsed. Runs hourly; queries
 * user_profiles for any doc whose ownerPackageExpiryMillis has passed
 * (Firestore's `<=` filter naturally excludes docs where the field is null,
 * so only a genuinely-active-then-lapsed package ever matches).
 *
 * On expiry with no renewal: demotes PRO_HOST -> SPECIALIST (setClaimsThenFirestore,
 * the same fail-safe claim+Firestore pattern revokeProHostRole.ts uses) and mirrors
 * isOwnerPackageLapsed: true onto every listing the host owns — hides them from a
 * fresh Discovery browse (DiscoveryViewModel's isLiveListing filter) WITHOUT
 * unpublishing (status/isActiveSubscription untouched) and WITHOUT locking out a
 * specialist who already has an ACCEPTED booking there (SpaceDetailsScreen shows a
 * "host is in verification process" note for them instead, reached via My Bookings,
 * not Discovery). Both are reversed automatically the moment the host's package
 * renews — see entitlements.ts's restoreListingsAfterRenewal. firestore.rules'
 * hasActivePackage() also checks the live expiry timestamp itself, so a package
 * that's lapsed but not yet swept by this function already blocks publishing.
 */
export const expirePackages = onSchedule({ schedule: "0 * * * *", timeoutSeconds: 540 }, async () => {
  const db = getFirestore();
  const now = Date.now();

  // 1. Proactive warning for packages expiring in <= 3 days (notified once)
  const warningWindowEnd = now + 3 * 24 * 60 * 60 * 1000;
  let warningLastDoc: FirebaseFirestore.QueryDocumentSnapshot | null = null;
  while (true) {
    let warningQuery = db
      .collection("user_profiles")
      .where("ownerPackageExpiryMillis", ">", now)
      .where("ownerPackageExpiryMillis", "<=", warningWindowEnd)
      .limit(500);
    if (warningLastDoc) warningQuery = warningQuery.startAfter(warningLastDoc);
    const warningSnap = await warningQuery.get();
    if (warningSnap.empty) break;
    for (const doc of warningSnap.docs) {
      const data = doc.data();
      if (data.expiryWarningSent === true) continue;
      // An auto-renewing Play subscription isn't "expiring" — Google renews it and emails
      // about payment problems itself. Warn only when it won't renew (canceled) or isn't Play.
      if (data.entitlementSource === "google_play" && data.billingStatus !== "CANCELED") continue;
      const userId = doc.id;
      const expiry = data.ownerPackageExpiryMillis as number;
      const daysLeft = Math.max(1, Math.ceil((expiry - now) / (24 * 60 * 60 * 1000)));
      await sendPushToUser(
        userId,
        "ProHost Premium ends soon",
        `Your Pro Host access ends in ${daysLeft} day(s) and your listings will be hidden. Resubscribe from ProHost Premium to keep them live.`,
        {
          category: "PAYMENT_REMINDER",
          targetTab: "owner_subscriptions",
        }
      );
      try {
        if (data.email) {
          const ctx: UserContext = {
            fullName: data.fullName ?? "Member",
            email: data.email as string,
            role: "PRO_HOST",
            activeListingCount: (data.activeListingCount ?? 0) as number,
          };
          const expiryDate = new Date(expiry).toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
          const tpl = subscriptionExpiringTemplate(ctx, planLabel(data.ownerPackageId as string | undefined), daysLeft, expiryDate);
          await sendEmail({ to: data.email as string, ...tpl });
        }
      } catch (_) { /* email is best-effort */ }
      await doc.ref.update({ expiryWarningSent: true }).catch(() => undefined);
    }
    if (warningSnap.docs.length < 500) break;
    warningLastDoc = warningSnap.docs[warningSnap.docs.length - 1];
  }

  const auth = getAuth();
  let clearedCount = 0;
  let demotedCount = 0;
  let listingsHiddenCount = 0;
  let totalSwept = 0;

  let skippedCount = 0;
  // Cursor-paginated (a doc that is skipped or fails keeps matching the query, so
  // re-querying from the start would loop on it), with a hard page cap.
  let expiredCursor: FirebaseFirestore.QueryDocumentSnapshot | null = null;
  for (let page = 0; page < 20; page++) {
    let expiredQuery = db
      .collection("user_profiles")
      .where("ownerPackageExpiryMillis", "<=", now)
      .orderBy("ownerPackageExpiryMillis")
      .limit(200);
    if (expiredCursor) expiredQuery = expiredQuery.startAfter(expiredCursor);
    const expiredSnap = await expiredQuery.get();
    if (expiredSnap.empty) break;
    expiredCursor = expiredSnap.docs[expiredSnap.docs.length - 1];

    for (const doc of expiredSnap.docs) {
      const uid = doc.id;
      try {
        // Google Play is the authority: before demoting a Play subscriber, re-read the
        // subscription — a renewal whose notification was missed extends it here, and an
        // ended one is removed through the same EntitlementManager path as RTDN.
        const token = doc.data()?.lastPurchaseToken as string | undefined;
        let recheck: PlayRecheck = { kind: "not_play" };
        if (doc.data()?.entitlementSource === "google_play" && token) {
          try {
            const sub = await queryPlaySubscription(token);
            const result = await syncSubscription(uid, token, sub, { source: "expire_sweep" });
            recheck = result.hasAccess ? { kind: "has_access" } : { kind: "no_access" };
          } catch (e) {
            const err = classifyPlayError(e);
            logger.warn(`expirePackages: Play re-check failed [${err.kind}]: ${err.message}`);
            recheck = { kind: "error", errorKind: err.kind };
          }
        }
        const decision = expiryDecision(recheck);
        if (decision === "keep") { clearedCount++; continue; }
        if (decision === "skip") { skippedCount++; continue; }
        // syncSubscription already removed Pro Host when Play said it ended.
        if (recheck.kind === "no_access") { clearedCount++; continue; }

        let authUser;
        try {
          authUser = await auth.getUser(uid);
        } catch (e) {
          if ((e as { code?: string }).code === "auth/user-not-found") {
            // Account deleted outside the app: just stop it matching this sweep.
            await doc.ref.update({ ownerPackageId: null, ownerPackageExpiryMillis: null }).catch(() => undefined);
            clearedCount++;
            continue;
          }
          throw e;
        }
        const isProHost = authUser.customClaims?.role === "PRO_HOST";

        if (isProHost) {
          await setClaimsThenFirestore(
            auth,
            uid,
            authUser.customClaims,
            { ...authUser.customClaims, role: "SPECIALIST" },
            async () => {
              await doc.ref.update(
                { role: "SPECIALIST", ownerPackageId: null, ownerPackageExpiryMillis: null, billingStatus: "EXPIRED", updatedAt: now }
              );
            }
          );
          try {
            await auth.revokeRefreshTokens(uid);
          } catch (e) {
            logger.warn(`expirePackages: revokeRefreshTokens failed: ${(e as Error).message}`);
          }
          demotedCount++;
          await notifyAdminsOfSubscriptionChange(uid, "EXPIRED", {
            planId: (doc.data()?.ownerPackageId as string | undefined) ?? null,
            note: "demoted to Specialist by the expiry sweep",
          });
          await sendGa4Event(uid, "subscription_expired", { plan_id: (doc.data()?.ownerPackageId as string | undefined) ?? undefined });

          const ownedListings = await db.collection("workspace_listings").where("ownerId", "==", uid).get();
          if (!ownedListings.empty) {
            const bulkWriter = db.bulkWriter();
            ownedListings.docs.forEach((listingDoc) => {
              bulkWriter.update(listingDoc.ref, { isOwnerPackageLapsed: true });
            });
            await bulkWriter.close();
            listingsHiddenCount += ownedListings.size;
          }

          await sendPushToUser(
            uid,
            "ProHost Premium expired",
            "Your Pro Host access has ended and your listings are hidden. Subscribe to ProHost Premium to restore them.",
            { category: "PACKAGE_EXPIRED", targetTab: "owner_subscriptions" }
          );
          try {
            const expiredData = doc.data();
            if (expiredData?.email) {
              const ctx: UserContext = {
                fullName: expiredData.fullName ?? "Member",
                email: expiredData.email as string,
                role: "SPECIALIST",
              };
              const tpl = subscriptionExpiredTemplate(ctx);
              await sendEmail({ to: expiredData.email as string, ...tpl });
            }
          } catch (_) { /* email is best-effort */ }
        } else {
          // Not currently PRO_HOST (e.g. already SPECIALIST with a stray expiry
          // value on file) — just clear the baseline, nothing to demote or hide.
          await doc.ref.update({ ownerPackageId: null, ownerPackageExpiryMillis: null });
        }
        clearedCount++;
      } catch (e) {
        logger.error(`expirePackages: failed to process a profile: ${(e as Error).message}`);
      }
    }

    totalSwept += expiredSnap.size;
    if (expiredSnap.size < 200) break;
  }

  logger.info(`expirePackages: swept ${totalSwept} packages`);

  await recordAuditLog({
    actionType: "PACKAGES_EXPIRED_BATCH",
    details: `Scheduled sweep processed ${clearedCount} expired owner package(s): ${demotedCount} Pro Host(s) demoted to Specialist, ${listingsHiddenCount} listing(s) hidden pending renewal` +
      (skippedCount > 0 ? `; ${skippedCount} Play subscriber(s) skipped because Google Play couldn't be read (retried next hour).` : "."),
    actorEmail: "system@prohost.app",
    severity: demotedCount > 0 ? "WARN" : "INFO",
  });
});
