import SwiftUI
import UIKit

struct AuthScreen: View {
  @Environment(AppStore.self) private var store
  @State private var signup = false
  @State private var name = ""
  @State private var email = ""
  @State private var password = ""
  @State private var busy = false
  @State private var error: String?

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(alignment: .leading, spacing: 22) {
          Label("wayfare", systemImage: "airplane.departure").typeStyle(.headlineSmall).padding(.top, 30)
          Text(store.recovery ? "A fresh start." : "Go places.\nKeep count.")
            .typeStyle(.displaySmall).padding(.top, 30)
          Text(
            store.recovery
              ? "Choose a new password for your account."
              : "A little ledger for your next big chapter."
          )
          .foregroundStyle(Palette.soft)
          if !store.configured {
            Notice(
              text:
                "Add your public Supabase configuration to ios/Config/Local.xcconfig, then rebuild the app."
            )
          }
          if let error { Notice(text: error) }
          if let notice = store.notice { Notice(text: notice) }
          VStack(alignment: .leading, spacing: 16) {
            if signup && !store.recovery { field("Your name", text: $name, content: .name) }
            if !store.recovery { field("Email address", text: $email, content: .emailAddress) }
            VStack(alignment: .leading, spacing: 6) {
              Text("Password").font(.subheadline.weight(.medium))
              SecureField("At least 6 characters", text: $password)
                .textContentType(signup || store.recovery ? .newPassword : .password)
                .padding(14).background(Palette.card, in: RoundedRectangle(cornerRadius: 12))
            }
          }
          PrimaryButton(
            title: store.recovery ? "Update password" : signup ? "Create account" : "Sign in",
            busy: busy
          ) {
            run {
              guard password.count >= 6 else {
                throw FormError("Use at least six characters for your password.")
              }
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
            Button("Continue with Google", systemImage: "person.badge.key") {
              run { try await store.signInWithGoogle() }
            }.buttonStyle(.bordered).frame(maxWidth: .infinity)
            Button(signup ? "Already have an account? Sign in" : "New here? Create an account") {
              signup.toggle()
              error = nil
              store.notice = nil
            }
            Button("Forgot your password?") {
              run {
                try validateEmail()
                try await store.resetPassword(email: email)
              }
            }
            Button("Resend confirmation email") {
              run {
                try validateEmail()
                try await store.resendConfirmation(email: email)
              }
            }.typeStyle(.bodyMedium)
          } else {
            Button("Back to sign in") { run { try await store.signOut() } }
          }
        }
        .disabled(busy || !store.configured).padding(24).frame(maxWidth: 480)
        .frame(maxWidth: .infinity)
      }.paperScreen()
    }
  }

  private func field(_ label: String, text: Binding<String>, content: UITextContentType)
    -> some View
  {
    VStack(alignment: .leading, spacing: 6) {
      Text(label).font(.subheadline.weight(.medium))
      TextField(label, text: text).textContentType(content)
        .textInputAutocapitalization(content == .name ? .words : .never)
        .keyboardType(content == .emailAddress ? .emailAddress : .default).autocorrectionDisabled()
        .padding(14).background(Palette.card, in: RoundedRectangle(cornerRadius: 12))
    }
  }

  private func validateEmail() throws {
    guard email.contains("@") else { throw FormError("Enter your email address first.") }
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

struct FormError: LocalizedError {
  let message: String
  init(_ message: String) { self.message = message }
  var errorDescription: String? { message }
}
