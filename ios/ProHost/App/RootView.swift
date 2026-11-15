import SwiftUI
import ProHostShared

/// Hosts ProHost's shared Compose UI (`:shared`, `MainViewController` in `shared/src/iosMain`). Every screen is drawn by the
/// same Kotlin code as the Android app; Swift here only provides the window and, later, the platform services.
struct RootView: View {
    var body: some View {
        ComposeRoot().ignoresSafeArea()
    }
}

private struct ComposeRoot: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
