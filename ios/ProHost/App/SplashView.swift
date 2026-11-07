import SwiftUI

/// M0 placeholder: proves the build, signing and TestFlight pipeline. Replaced by the real shell in M2.
struct SplashView: View {
    var body: some View {
        ZStack {
            Color("AccentColor").ignoresSafeArea()
            VStack(spacing: 16) {
                Image("prohost_checkmark_logo")
                    .resizable()
                    .scaledToFit()
                    .frame(width: 160)
                    .accessibilityHidden(true)
                Text(AppInfo.name)
                    .font(.largeTitle.weight(.heavy))
                    .foregroundStyle(.white)
                Text(AppInfo.versionLabel)
                    .font(.footnote)
                    .foregroundStyle(.white.opacity(0.8))
            }
        }
    }
}

#Preview {
    SplashView()
}
