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
        VStack(alignment: .leading, spacing: 24) {
          HStack(spacing: 8) {
            Image(systemName: "airplane.departure").font(.system(size: 20, weight: .medium))
            Text("wayfare").typeStyle(.headlineSmall)
          }.foregroundStyle(Palette.rausch).padding(.top, 12)
          VStack(alignment: .leading, spacing: 14) {
            Text(store.recovery ? "A fresh start." : "Go places.\nKeep count.")
              .typeStyle(.displaySmall)
            Text(
              store.recovery
                ? "Choose a new password for your account."
                : "A little ledger for your next big chapter."
            ).typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
          }
          .frame(maxWidth: .infinity, alignment: .leading).padding(24)
          .background(Palette.softCloud, in: RoundedRectangle(cornerRadius: Radius.card))
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
            VStack(alignment: .leading, spacing: 6) {
              Text("Password").typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
              SecureField("At least 6 characters", text: $password)
                .textContentType(signup || store.recovery ? .newPassword : .password)
                .typeStyle(.bodyLarge).authFieldChrome()
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
            SecondaryButton(title: "Continue with Google", icon: "person.badge.key") {
              run { try await store.signInWithGoogle() }
            }
            VStack(spacing: 4) {
              Button(signup ? "Already have an account? Sign in" : "New here? Create an account") {
                signup.toggle()
                error = nil
                store.notice = nil
              }.typeStyle(.labelMedium).foregroundStyle(Palette.ink)
              Button("Forgot your password?") {
                run {
                  try validateEmail()
                  try await store.resetPassword(email: email)
                }
              }.typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
              Button("Resend confirmation email") {
                run {
                  try validateEmail()
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
      }.canvasScreen()
    }
  }

  private func field(_ label: String, text: Binding<String>, content: UITextContentType)
    -> some View
  {
    VStack(alignment: .leading, spacing: 6) {
      Text(label).typeStyle(.bodyMedium).foregroundStyle(Palette.ash)
      TextField(label, text: text).textContentType(content)
        .textInputAutocapitalization(content == .name ? .words : .never)
        .keyboardType(content == .emailAddress ? .emailAddress : .default).autocorrectionDisabled()
        .typeStyle(.bodyLarge).authFieldChrome()
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

extension View {
  /// A text input: white behind a hairline border at the control radius. The
  /// system never tints a field with the accent.
  fileprivate func authFieldChrome() -> some View {
    padding(.horizontal, 16).frame(height: 48)
      .background(Palette.canvas, in: RoundedRectangle(cornerRadius: Radius.control))
      .overlay(
        RoundedRectangle(cornerRadius: Radius.control).stroke(Palette.hairline, lineWidth: 1))
  }
}

struct FormError: LocalizedError {
  let message: String
  init(_ message: String) { self.message = message }
  var errorDescription: String? { message }
}
