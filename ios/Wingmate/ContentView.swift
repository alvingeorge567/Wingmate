import SwiftUI

struct ContentView: View {
    private let settings = SharedSettings.shared
    @State private var consent = SharedSettings.shared.consentGiven
    @State private var tone = SharedSettings.shared.tone
    @State private var aboutMe = SharedSettings.shared.aboutMe
    @State private var backendURL = SharedSettings.shared.backendURL
    @State private var appToken = SharedSettings.shared.appToken
    @State private var testText = ""

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("A keyboard that suggests natural replies for your dating chats.")
                }
                if !consent {
                    consentSection
                } else {
                    setupSection
                    styleSection
                    serverSection
                    trySection
                }
            }
            .navigationTitle("Wingmate")
        }
    }

    private var consentSection: some View {
        Section("Before you start") {
            Text("• iOS doesn't let keyboards read other apps' screens. You give Wingmate their message by copying it (📋) or typing it in the keyboard (✍️).")
            Text("• When you tap ✨ Suggest, that message, your draft and the replies you picked this session are sent to our server and Anthropic's Claude AI to write suggestions. Nothing is stored.")
            Text("• Suggestions are just ideas. You choose what to send.")
            Button("I understand and agree") {
                settings.consentGiven = true
                consent = true
            }
        }
    }

    private var setupSection: some View {
        Section {
            Label("Open Settings → General → Keyboard → Keyboards → Add New Keyboard → Wingmate", systemImage: "1.circle")
            Label("Tap Wingmate and turn on Allow Full Access (needed to reach the server and read what you copy)", systemImage: "2.circle")
            Label("In any chat, hold 🌐 and choose Wingmate", systemImage: "3.circle")
            Button("Open Settings") {
                if let url = URL(string: UIApplication.openSettingsURLString) {
                    UIApplication.shared.open(url)
                }
            }
        } header: {
            Text("Setup")
        }
    }

    private var styleSection: some View {
        Section {
            Picker("Default tone", selection: $tone) {
                ForEach(Tone.allCases) { Text($0.label).tag($0) }
            }
            .onChange(of: tone) { settings.tone = $0 }
            TextField("About me, e.g. 29, nurse, into bouldering", text: $aboutMe, axis: .vertical)
                .lineLimit(2...4)
                .onChange(of: aboutMe) { settings.aboutMe = $0 }
        } header: {
            Text("Your style")
        } footer: {
            Text("Helps suggestions stay true to you. \(aboutMe.count)/500")
        }
    }

    private var serverSection: some View {
        Section("Server") {
            TextField("Backend URL", text: $backendURL)
                .keyboardType(.URL)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .onChange(of: backendURL) { settings.backendURL = $0 }
            SecureField("App token", text: $appToken)
                .onChange(of: appToken) { settings.appToken = $0 }
        }
    }

    private var trySection: some View {
        Section {
            Text("Copy this, then switch to Wingmate below and tap 📋 Use copied:")
            Text("nearly died?? you can't just say that and not explain").italic()
                .textSelection(.enabled)
            TextField("Try the keyboard here", text: $testText, axis: .vertical)
        } header: {
            Text("Try it")
        }
    }
}
