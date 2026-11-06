import Foundation

/// Settings shared between the host app and the keyboard extension through an App Group.
/// Change `appGroup` to an App Group you have registered in your Apple developer account.
final class SharedSettings {
    static let appGroup = "group.com.wingmate.shared"
    static let shared = SharedSettings()

    private let defaults: UserDefaults

    init(defaults: UserDefaults? = UserDefaults(suiteName: SharedSettings.appGroup)) {
        self.defaults = defaults ?? .standard
    }

    var consentGiven: Bool {
        get { defaults.bool(forKey: "consent") }
        set { defaults.set(newValue, forKey: "consent") }
    }

    var tone: Tone {
        get { Tone(rawValue: defaults.string(forKey: "tone") ?? "") ?? .any }
        set { defaults.set(newValue.rawValue, forKey: "tone") }
    }

    var aboutMe: String {
        get { defaults.string(forKey: "about_me") ?? "" }
        set { defaults.set(String(newValue.prefix(500)), forKey: "about_me") }
    }

    var backendURL: String {
        get {
            defaults.string(forKey: "backend_url")
                ?? (Bundle.main.object(forInfoDictionaryKey: "WingmateBackendURL") as? String)
                ?? "http://localhost:8787"
        }
        set { defaults.set(newValue.trimmingCharacters(in: .whitespaces), forKey: "backend_url") }
    }

    var appToken: String {
        get { defaults.string(forKey: "app_token") ?? "" }
        set { defaults.set(newValue.trimmingCharacters(in: .whitespaces), forKey: "app_token") }
    }
}
