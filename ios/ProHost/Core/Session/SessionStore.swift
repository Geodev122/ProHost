import FirebaseAuth
import FirebaseFirestore
import Foundation
import Observation

/// Owns the signed-in state for the whole app: Auth listener, server-assigned role, profile load, and the resulting `SessionPhase`.
/// The client never sets a role; `assignInitialRole` (through `AuthService.finishSession`) is only called when the token has no role claim yet.
@MainActor
@Observable
final class SessionStore {
    private(set) var phase: SessionPhase = .launching
    @ObservationIgnored private let auth = AuthService()
    @ObservationIgnored private var listener: AuthStateDidChangeListenerHandle?
    @ObservationIgnored private var loadTask: Task<Void, Never>?

    init() {
        listener = Auth.auth().addStateDidChangeListener { [weak self] _, user in
            Task { @MainActor in self?.authChanged(signedIn: user != nil) }
        }
    }

    deinit {
        if let listener { Auth.auth().removeStateDidChangeListener(listener) }
    }

    private func authChanged(signedIn: Bool) {
        if signedIn {
            reload()
        } else {
            loadTask?.cancel()
            phase = .signedOut
        }
    }

    /// Re-reads role and profile (after registration, on retry, and on resume).
    func reload() {
        loadTask?.cancel()
        guard let user = Auth.auth().currentUser else {
            phase = .signedOut
            return
        }
        if case .ready = phase {} else { phase = .launching }
        loadTask = Task { await load(user) }
    }

    private func load(_ user: User) async {
        do {
            let token = try await user.getIDTokenResult()
            var role = UserRole.fromClaim(token.claims["role"])
            if role == nil { role = try await auth.finishSession() }
            let snapshot = try await Firestore.firestore().collection("user_profiles").document(user.uid).getDocument()
            if Task.isCancelled { return }
            phase = SessionResolver.resolve(
                uid: user.uid, email: user.email ?? "", role: role ?? .specialist,
                profile: snapshot.exists ? snapshot.data() : nil
            )
        } catch let suspended as AccountSuspendedError {
            phase = .suspended(suspended.message)
        } catch {
            if Task.isCancelled { return }
            phase = .failed(FunctionsClient.userMessage(for: error))
        }
    }

    func signOut() {
        try? auth.signOut()
    }
}
