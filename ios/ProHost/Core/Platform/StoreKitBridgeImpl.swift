import Foundation
import ProHostShared
import StoreKit
import UIKit

/// StoreKit 2 for the shared Premium page (`IosAppBilling`). Prices, periods and introductory offers are read from StoreKit
/// only; each verified transaction goes to the server (`verifyAppleTransaction`), which decides access. Transactions are
/// finished once StoreKit verified them; the server re-checks the same signed data and `currentEntitlements` keeps
/// returning an active subscription, so a failed server call is retried on the next resume.
final class StoreKitBridgeImpl: NSObject, StoreKitBridge {
    private var updatesTask: Task<Void, Never>?

    deinit { updatesTask?.cancel() }

    func loadStoreProducts(ids: [String], completion: @escaping ([StoreProductReply], String) -> Void) {
        Task {
            do {
                let products = try await Product.products(for: ids)
                var out: [StoreProductReply] = []
                for product in products where product.type == .autoRenewable {
                    out.append(await Self.productReply(product))
                }
                await MainActor.run { completion(out, "") }
            } catch {
                await MainActor.run { completion([], error.localizedDescription) }
            }
        }
    }

    func purchaseStoreProduct(productId: String, appAccountToken: String, completion: @escaping (StorePurchaseReply) -> Void) {
        Task {
            let reply: StorePurchaseReply
            do {
                guard let product = try await Product.products(for: [productId]).first,
                      let token = UUID(uuidString: appAccountToken) else {
                    reply = StorePurchaseReply(outcome: 3, transaction: nil, error: "product unavailable")
                    await MainActor.run { completion(reply) }
                    return
                }
                switch try await product.purchase(options: [.appAccountToken(token)]) {
                case .success(let verification):
                    switch verification {
                    case .verified(let transaction):
                        let tx = await Self.transactionReply(transaction, jws: verification.jwsRepresentation)
                        await transaction.finish()
                        reply = StorePurchaseReply(outcome: 0, transaction: tx, error: "")
                    case .unverified(_, let error):
                        reply = StorePurchaseReply(outcome: 3, transaction: nil, error: "unverified: \(error.localizedDescription)")
                    }
                case .userCancelled:
                    reply = StorePurchaseReply(outcome: 1, transaction: nil, error: "")
                case .pending:
                    reply = StorePurchaseReply(outcome: 2, transaction: nil, error: "")
                @unknown default:
                    reply = StorePurchaseReply(outcome: 3, transaction: nil, error: "unknown purchase result")
                }
            } catch {
                reply = StorePurchaseReply(outcome: 3, transaction: nil, error: error.localizedDescription)
            }
            await MainActor.run { completion(reply) }
        }
    }

    func currentStoreEntitlements(completion: @escaping ([StoreTransactionReply]) -> Void) {
        Task {
            let out = await Self.entitlements()
            await MainActor.run { completion(out) }
        }
    }

    func syncStorePurchases(completion: @escaping ([StoreTransactionReply], String) -> Void) {
        Task {
            do {
                try await AppStore.sync()
            } catch StoreKitError.userCancelled {
                await MainActor.run { completion([], "cancelled") }
                return
            } catch {
                await MainActor.run { completion([], error.localizedDescription) }
                return
            }
            let out = await Self.entitlements()
            await MainActor.run { completion(out, "") }
        }
    }

    func presentManageSubscriptions() {
        Task { @MainActor in
            guard let scene = Self.activeScene() else { return }
            try? await AppStore.showManageSubscriptions(in: scene)
        }
    }

    func presentOfferCodeRedemption() {
        Task { @MainActor in
            guard let scene = Self.activeScene() else { return }
            try? await AppStore.presentOfferCodeRedeemSheet(in: scene)
        }
    }

    func observeStoreTransactions(listener: @escaping (StoreTransactionReply) -> Void) {
        updatesTask?.cancel()
        updatesTask = Task.detached {
            for await result in Transaction.updates {
                guard case .verified(let transaction) = result else { continue }
                let tx = await StoreKitBridgeImpl.transactionReply(transaction, jws: result.jwsRepresentation)
                await transaction.finish()
                await MainActor.run { listener(tx) }
            }
        }
    }

    // MARK: - Mapping

    private static func entitlements() async -> [StoreTransactionReply] {
        var out: [StoreTransactionReply] = []
        for await result in Transaction.currentEntitlements {
            guard case .verified(let transaction) = result,
                  transaction.productType == .autoRenewable,
                  transaction.revocationDate == nil else { continue }
            out.append(await transactionReply(transaction, jws: result.jwsRepresentation))
        }
        return out
    }

    /// The transaction's JWS plus the renewal info of its subscription group (auto-renew state, billing retry).
    private static func transactionReply(_ transaction: Transaction, jws: String) async -> StoreTransactionReply {
        var renewalJws = ""
        var autoRenews = true
        if let group = transaction.subscriptionGroupID,
           let statuses = try? await Product.SubscriptionInfo.status(for: group) {
            for status in statuses {
                guard case .verified(let current) = status.transaction,
                      current.originalID == transaction.originalID else { continue }
                renewalJws = status.renewalInfo.jwsRepresentation
                if case .verified(let info) = status.renewalInfo { autoRenews = info.willAutoRenew }
            }
        }
        return StoreTransactionReply(
            productId: transaction.productID,
            signedTransaction: jws,
            signedRenewalInfo: renewalJws,
            autoRenews: autoRenews
        )
    }

    private static func productReply(_ product: Product) async -> StoreProductReply {
        let subscription = product.subscription
        let period = subscription.map { iso($0.subscriptionPeriod, times: 1) } ?? ""
        var introMicros: Int64 = 0
        var introPrice = ""
        var introPeriod = ""
        var introCycles: Int32 = 0
        if let subscription, let intro = subscription.introductoryOffer, await subscription.isEligibleForIntroOffer {
            introMicros = micros(intro.price)
            introPrice = intro.displayPrice
            if intro.paymentMode == .payUpFront {
                // One payment for the whole span, like a Play phase of one cycle.
                introPeriod = iso(intro.period, times: intro.periodCount)
                introCycles = 1
            } else {
                introPeriod = iso(intro.period, times: 1)
                introCycles = Int32(max(intro.periodCount, 1))
            }
        }
        return StoreProductReply(
            productId: product.id,
            productDescription: product.description,
            priceMicros: micros(product.price),
            currency: product.priceFormatStyle.currencyCode,
            formattedPrice: product.displayPrice,
            period: period,
            introPriceMicros: introMicros,
            introFormattedPrice: introPrice,
            introPeriod: introPeriod,
            introCycles: introCycles
        )
    }

    private static func iso(_ period: Product.SubscriptionPeriod, times: Int) -> String {
        let value = period.value * max(times, 1)
        switch period.unit {
        case .day: return "P\(value)D"
        case .week: return "P\(value)W"
        case .month: return "P\(value)M"
        case .year: return "P\(value)Y"
        @unknown default: return "P\(value)M"
        }
    }

    private static func micros(_ price: Decimal) -> Int64 {
        NSDecimalNumber(decimal: price * 1_000_000).int64Value
    }

    @MainActor
    private static func activeScene() -> UIWindowScene? {
        let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
        return scenes.first { $0.activationState == .foregroundActive } ?? scenes.first
    }
}
