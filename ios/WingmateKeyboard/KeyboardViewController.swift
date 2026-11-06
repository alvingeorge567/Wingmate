import SwiftUI
import UIKit

/// Entry point of the keyboard extension. Hosts the SwiftUI keyboard and bridges it to the text field.
final class KeyboardViewController: UIInputViewController {
    private lazy var model = KeyboardModel(controller: self)
    private var host: UIHostingController<KeyboardRootView>?

    override func viewDidLoad() {
        super.viewDidLoad()
        let host = UIHostingController(rootView: KeyboardRootView(model: model))
        host.view.translatesAutoresizingMaskIntoConstraints = false
        host.view.backgroundColor = .clear
        addChild(host)
        view.addSubview(host.view)
        NSLayoutConstraint.activate([
            host.view.leadingAnchor.constraint(equalTo: view.leadingAnchor),
            host.view.trailingAnchor.constraint(equalTo: view.trailingAnchor),
            host.view.topAnchor.constraint(equalTo: view.topAnchor),
            host.view.bottomAnchor.constraint(equalTo: view.bottomAnchor),
        ])
        host.didMove(toParent: self)
        self.host = host
    }

    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        model.refresh()
    }

    override func textDidChange(_ textInput: UITextInput?) {
        super.textDidChange(textInput)
        model.updateAutoShift()
    }
}

/// State and actions for the keyboard. Lives on the main actor because it touches the text proxy and UI.
@MainActor
final class KeyboardModel: ObservableObject {
    enum InputTarget { case field, theirMessage }

    @Published var tone: Tone = .any
    @Published var suggestions: [Suggestion] = []
    @Published var status: String?
    @Published var isLoading = false
    @Published var shifted = true
    @Published var symbols = false
    @Published var clipboardHasText = false
    /// When `.theirMessage`, keys type into `theirMessage` instead of the app's text field.
    @Published var target: InputTarget = .field
    @Published var theirMessage = ""
    @Published var hasFullAccess = false

    /// Short memory of this keyboard session: messages they sent (pasted/typed) and replies I picked.
    private var history: [ChatMessage] = []
    private weak var controller: KeyboardViewController?
    private let settings = SharedSettings.shared
    private let client = SuggestionClient()
    private var task: Task<Void, Never>?

    init(controller: KeyboardViewController) {
        self.controller = controller
    }

    var needsGlobeKey: Bool { controller?.needsInputModeSwitchKey ?? true }

    func refresh() {
        tone = settings.tone
        hasFullAccess = controller?.hasFullAccess ?? false
        clipboardHasText = hasFullAccess && UIPasteboard.general.hasStrings
        updateAutoShift()
    }

    // MARK: Suggestions

    func suggest(usingClipboard: Bool = false) {
        guard hasFullAccess else {
            show(error: "Turn on Allow Full Access for Wingmate in Settings → General → Keyboard → Keyboards.")
            return
        }
        guard settings.consentGiven else {
            show(error: "Open the Wingmate app to finish setup first.")
            return
        }

        if usingClipboard, let clip = UIPasteboard.general.string?.trimmingCharacters(in: .whitespacesAndNewlines), !clip.isEmpty {
            addTheirMessage(String(clip.prefix(1000)))
        }
        if target == .theirMessage {
            commitTheirMessage()
        }
        guard !history.isEmpty else {
            show(error: "Copy their message and tap 📋, or tap ✍️ to type what they said.")
            return
        }

        let draft = controller?.textDocumentProxy.documentContextBeforeInput
        task?.cancel()
        isLoading = true
        status = "Thinking of replies…"
        suggestions = []
        let messages = history
        let tone = tone
        let about = settings.aboutMe
        task = Task { [weak self] in
            guard let self else { return }
            do {
                let res = try await client.suggest(messages: messages, draft: draft, tone: tone, aboutMe: about)
                guard !Task.isCancelled else { return }
                self.isLoading = false
                self.status = res.read.isEmpty ? nil : res.read
                self.suggestions = res.suggestions
            } catch {
                guard !Task.isCancelled else { return }
                self.show(error: error.localizedDescription)
            }
        }
    }

    func pick(_ suggestion: Suggestion) {
        guard let proxy = controller?.textDocumentProxy else { return }
        // Replace whatever was typed with the chosen reply.
        // The proxy only exposes nearby text, so clear in a few bounded passes (never loop forever).
        let afterCount = proxy.documentContextAfterInput?.count ?? 0
        if afterCount > 0 { proxy.adjustTextPosition(byCharacterOffset: afterCount) }
        for _ in 0..<5 {
            guard let before = proxy.documentContextBeforeInput, !before.isEmpty else { break }
            for _ in 0..<before.count { proxy.deleteBackward() }
        }
        proxy.insertText(suggestion.text)
        history.append(ChatMessage(from: .me, text: suggestion.text))
        trimHistory()
        clearSuggestions()
    }

    func clearSuggestions() {
        suggestions = []
        status = nil
        isLoading = false
    }

    func resetConversation() {
        history.removeAll()
        theirMessage = ""
        target = .field
        clearSuggestions()
    }

    func select(tone: Tone) { self.tone = tone }

    func toggleTheirMessageMode() {
        if target == .theirMessage {
            commitTheirMessage()
            target = .field
        } else {
            target = .theirMessage
            shifted = true
        }
    }

    private func commitTheirMessage() {
        let text = theirMessage.trimmingCharacters(in: .whitespacesAndNewlines)
        if !text.isEmpty { addTheirMessage(text) }
        theirMessage = ""
        target = .field
    }

    private func addTheirMessage(_ text: String) {
        if history.last != ChatMessage(from: .them, text: text) {
            history.append(ChatMessage(from: .them, text: text))
            trimHistory()
        }
    }

    private func trimHistory() {
        if history.count > 20 { history.removeFirst(history.count - 20) }
    }

    private func show(error: String) {
        isLoading = false
        suggestions = []
        status = "⚠️ " + error
    }

    // MARK: Typing

    func type(_ text: String) {
        let out = shifted && !symbols ? text.uppercased() : text
        switch target {
        case .field: controller?.textDocumentProxy.insertText(out)
        case .theirMessage: theirMessage += out
        }
        if shifted { shifted = false }
    }

    func space() {
        type(" ")
        updateAutoShift()
    }

    func delete() {
        switch target {
        case .field: controller?.textDocumentProxy.deleteBackward()
        case .theirMessage: if !theirMessage.isEmpty { theirMessage.removeLast() }
        }
        updateAutoShift()
    }

    func enter() {
        switch target {
        case .field: controller?.textDocumentProxy.insertText("\n")
        case .theirMessage: suggest()
        }
    }

    func switchKeyboard() { controller?.advanceToNextInputMode() }

    func updateAutoShift() {
        guard !symbols else { return }
        let before: String
        switch target {
        case .field: before = controller?.textDocumentProxy.documentContextBeforeInput ?? ""
        case .theirMessage: before = theirMessage
        }
        let trimmed = before.trimmingCharacters(in: .whitespaces)
        shifted = trimmed.isEmpty || trimmed.hasSuffix(".") || trimmed.hasSuffix("?") || trimmed.hasSuffix("!")
    }
}
