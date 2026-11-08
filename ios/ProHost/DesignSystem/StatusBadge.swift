import SwiftUI

enum StatusBadgeType {
    case available, occupied, verified, pending, maintenance, cancelled, admin, gold, info
}

enum ProBadgeType {
    case verifiedMember, active30d, expired, pending, acceptedLocked, declined, unavailable, superAdmin
    case tierA, tierB, tierC, customInfo, customSuccess, customWarning, customError

    var status: StatusBadgeType {
        switch self {
        case .verifiedMember: return .verified
        case .active30d, .customSuccess: return .available
        case .expired, .declined, .customError: return .cancelled
        case .pending, .customWarning: return .pending
        case .acceptedLocked: return .occupied
        case .unavailable: return .maintenance
        case .superAdmin: return .admin
        case .tierA: return .gold
        case .tierB, .tierC, .customInfo: return .info
        }
    }
}

/// Small colored status chip (`StatusBadge` on Android): 8 pt radius, 13 pt icon, labelSmall bold.
struct StatusBadge: View {
    let status: StatusBadgeType
    var label: String?
    var customIcon: PHIcon?
    var cornerRadius: CGFloat = 8

    private var spec: (bg: Color, fg: Color, icon: PHIcon, text: String) {
        switch status {
        case .available: return (PH.success, PH.onSuccess, .filledCheckCircle, L("available"))
        case .occupied: return (PH.locked, PH.onLocked, .filledLock, L("occupied_booked"))
        case .verified: return (PH.success, PH.onSuccess, .filledVerified, L("license_id_verified"))
        case .pending: return (PH.warning, PH.onWarning, .filledHourglassTop, L("pending_approval"))
        case .maintenance: return (PH.onSurfaceVariant, PH.surface, .filledBuild, L("maintenance_slot"))
        case .cancelled: return (PH.error, PH.onError, .filledCancel, L("cancelled_unavailable"))
        case .admin: return (PH.primary, PH.onPrimary, .filledShield, L("super_admin"))
        case .gold: return (PH.warning, PH.onWarning, .filledWorkspacePremium, L("premium_tier"))
        case .info: return (PH.info, PH.onInfo, .filledInfo, L("information"))
        }
    }

    var body: some View {
        let s = spec
        HStack(spacing: Spacing.xs) {
            PHIconView(icon: customIcon ?? s.icon, size: 13, tint: s.fg)
            Text(label ?? s.text)
                .phText(PHType.labelSmall.weighted(.bold), color: s.fg)
                .lineLimit(1)
                .fixedSize()
        }
        .padding(.horizontal, Spacing.sm)
        .padding(.vertical, Spacing.xs)
        .background(s.bg, in: RoundedRectangle(cornerRadius: cornerRadius, style: .continuous))
    }
}

struct ProStatusBadge: View {
    let type: ProBadgeType
    var customText: String?

    var body: some View {
        StatusBadge(status: type.status, label: customText)
    }
}
