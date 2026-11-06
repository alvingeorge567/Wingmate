import Foundation

enum Sender: String, Codable {
    case them, me
}

struct ChatMessage: Codable, Equatable {
    let from: Sender
    let text: String
}

enum Tone: String, Codable, CaseIterable, Identifiable {
    case any, playful, flirty, sincere, curious
    var id: String { rawValue }

    var label: String {
        switch self {
        case .any: return "Mix"
        case .playful: return "😜 Playful"
        case .flirty: return "😏 Flirty"
        case .sincere: return "🙂 Sincere"
        case .curious: return "🤔 Curious"
        }
    }
}

struct SuggestRequest: Encodable {
    let messages: [ChatMessage]
    let draft: String?
    let tone: Tone
    let userProfile: String?
    let count: Int
}

struct Suggestion: Decodable, Identifiable, Equatable {
    let text: String
    let tone: String
    var id: String { tone + "|" + text }
}

struct SuggestResponse: Decodable {
    let suggestions: [Suggestion]
    let read: String
}
