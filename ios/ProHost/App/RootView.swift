import SwiftUI

/// Routes by `SessionPhase`. The welcome, sign-in and registration screens arrive in the next slice (S3) in the signed-out and
/// needs-registration slots; until then those two phases show the brand screen.
struct RootView: View {
    @State private var session = SessionStore()

    var body: some View {
        Group {
            switch session.phase {
            case .launching, .signedOut, .needsRegistration:
                SplashView()
            case .ready(let user):
                MainShell(user: user, onSignOut: { session.signOut() })
            case .suspended(let message):
                SessionNotice(title: L("account_suspended"), message: message, actionTitle: L("sign_out")) { session.signOut() }
            case .failed(let message):
                SessionNotice(title: AppInfo.name, message: message, actionTitle: L("retry")) { session.reload() }
            }
        }
        .animation(.easeInOut(duration: 0.2), value: session.phase)
    }
}

private struct SessionNotice: View {
    let title: String
    let message: String
    let actionTitle: String
    let action: () -> Void

    var body: some View {
        ZStack {
            PH.background.ignoresSafeArea()
            ProEmptyState(title: title, description: message, icon: .filledErrorOutline, actionTitle: actionTitle, action: action)
        }
    }
}
