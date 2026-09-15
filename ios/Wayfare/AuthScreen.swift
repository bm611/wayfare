import SwiftUI
import UIKit

private let loginBackgroundImage: UIImage = {
  guard let path = Bundle.main.path(forResource: "login-background", ofType: "png"),
    let image = UIImage(contentsOfFile: path)
  else {
    assertionFailure("Missing login-background.png from the app bundle")
    return UIImage()
  }
  return image
}()

struct AuthScreen: View {
  @Environment(AppStore.self) private var store
  @State private var signup = false
  @State private var name = ""
  @State private var email = ""
  @State private var password = ""
  @State private var busy = false
  @State private var error: String?
  @State private var fieldErrors: [String: String] = [:]

  var body: some View {
    NavigationStack {
      ZStack {
        GeometryReader { geometry in
          Image(uiImage: loginBackgroundImage)
            .resizable()
            .scaledToFill()
            .frame(width: geometry.size.width, height: geometry.size.height)
            .clipped()
        }
        .ignoresSafeArea()
        LinearGradient(
          stops: [
            .init(color: Palette.canvas.opacity(0.04), location: 0),
            .init(color: Palette.canvas.opacity(0.5), location: 0.28),
            .init(color: Palette.canvas.opacity(0.82), location: 0.48),
            .init(color: Palette.canvas.opacity(0.9), location: 1),
          ],
          startPoint: .top, endPoint: .bottom
        ).ignoresSafeArea()
        ScrollView {
          VStack(alignment: .leading, spacing: 24) {
            HStack(spacing: 8) {
              Image(systemName: "airplane.departure").font(.system(size: 20, weight: .medium))
              Text("wayfare").typeStyle(.headlineSmall)
            }.foregroundStyle(Palette.accentInk).padding(.top, 12)
            VStack(alignment: .leading, spacing: 14) {
              Text(store.recovery ? "A fresh start." : "Go places.\nKeep count.")
                .typeStyle(.displaySmall)
              Text(
                store.recovery
                  ? "Choose a new password for your account."
                  : "A little ledger for your next big chapter."
              ).typeStyle(.bodyMedium).foregroundStyle(Palette.ink.opacity(0.76))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.vertical, 12)
            if !store.configured {
              Notice(
                text:
                  "Add your public Supabase configuration to ios/Config/Local.xcconfig, then rebuild the app."
              )
            }
            if let error { Notice(text: error, isError: true) }
            if let notice = store.notice { Notice(text: notice) }
            VStack(alignment: .leading, spacing: 16) {
              if signup && !store.recovery { field("Your name", text: $name, content: .name) }
              if !store.recovery { field("Email address", text: $email, content: .emailAddress) }
              LabeledField(label: "Password", error: fieldErrors["password"]) {
                SecureField("At least 6 characters", text: $password)
                  .textContentType(signup || store.recovery ? .newPassword : .password)
                  .typeStyle(.bodyLarge).accessibilityLabel("Password")
              }
            }
            PrimaryButton(
              title: store.recovery ? "Update password" : signup ? "Create account" : "Sign in",
              busy: busy
            ) {
              fieldErrors = [:]
              if !store.recovery && !email.contains("@") {
                fieldErrors["email"] = "Enter a valid email address."
                return
              }
              if signup && !store.recovery && name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                fieldErrors["name"] = "Enter the name your travel companions will see."
                return
              }
              guard password.count >= 6 else {
                fieldErrors["password"] = "Use at least six characters for your password."
                return
              }
              run {
                if store.recovery {
                  try await store.updatePassword(password)
                } else if signup {
                  try await store.signUp(name: name, email: email, password: password)
                } else {
                  try await store.signIn(email: email, password: password)
                }
              }
            }.disabled(!store.configured)
            if !store.recovery {
              SecondaryButton(title: "Continue with Google", icon: "person.badge.key") {
                run { try await store.signInWithGoogle() }
              }
              VStack(spacing: 4) {
                Button(signup ? "Already have an account? Sign in" : "New here? Create an account") {
                  signup.toggle()
                  fieldErrors = [:]
                  error = nil
                  store.notice = nil
                }.typeStyle(.labelMedium).foregroundStyle(Palette.ink)
                Button("Forgot your password?") {
                  run {
                    guard validateEmail() else { return }
                    try await store.resetPassword(email: email)
                  }
                }.typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
                Button("Resend confirmation email") {
                  run {
                    guard validateEmail() else { return }
                    try await store.resendConfirmation(email: email)
                  }
                }.typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
              }.frame(maxWidth: .infinity)
            } else {
              Button("Back to sign in") { run { try await store.signOut() } }
                .typeStyle(.labelMedium).foregroundStyle(Palette.ink)
                .frame(maxWidth: .infinity)
            }
          }
          .disabled(busy || !store.configured).padding(24).frame(maxWidth: 480)
          .frame(maxWidth: .infinity)
        }
        .scrollContentBackground(.hidden)
        .foregroundStyle(Palette.ink)
      }
    }
  }

  private func field(_ label: String, text: Binding<String>, content: UITextContentType)
    -> some View
  {
    WayfareTextField(label: label, text: text, error: fieldErrors[content == .name ? "name" : "email"])
      .textContentType(content)
      .textInputAutocapitalization(content == .name ? .words : .never)
      .keyboardType(content == .emailAddress ? .emailAddress : .default).autocorrectionDisabled()
  }

  private func validateEmail() -> Bool {
    guard email.contains("@") else {
      fieldErrors["email"] = "Enter your email address first."
      return false
    }
    return true
  }

  private func run(_ action: @escaping () async throws -> Void) {
    guard !busy else { return }
    busy = true
    error = nil
    store.notice = nil
    Task {
      defer { busy = false }
      do { try await action() } catch { self.error = error.localizedDescription }
    }
  }
}
