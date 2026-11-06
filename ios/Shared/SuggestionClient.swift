import Foundation

struct SuggestionError: LocalizedError {
    let message: String
    var errorDescription: String? { message }
}

/// Talks to the backend proxy described in /shared/api-contract.md.
struct SuggestionClient {
    var settings: SharedSettings = .shared
    var session: URLSession = {
        let config = URLSessionConfiguration.ephemeral // no disk cache of chat content
        config.timeoutIntervalForRequest = 25
        return URLSession(configuration: config)
    }()

    func suggest(
        messages: [ChatMessage],
        draft: String?,
        tone: Tone,
        aboutMe: String?,
        count: Int = 3
    ) async throws -> SuggestResponse {
        let base = settings.backendURL.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard let url = URL(string: base + "/v1/suggest") else {
            throw SuggestionError(message: "Server address looks wrong. Check the Wingmate app.")
        }
        let body = SuggestRequest(
            messages: messages.suffix(30).map { ChatMessage(from: $0.from, text: String($0.text.prefix(1000))) },
            draft: draft.flatMap { $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : String($0.prefix(1000)) },
            tone: tone,
            userProfile: aboutMe.flatMap { $0.isEmpty ? nil : String($0.prefix(500)) },
            count: count
        )

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(settings.appToken, forHTTPHeaderField: "X-App-Token")
        request.httpBody = try JSONEncoder().encode(body)

        let data: Data
        let response: URLResponse
        do {
            (data, response) = try await session.data(for: request)
        } catch {
            throw SuggestionError(message: "Can't reach the server. Check your connection.")
        }
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        switch status {
        case 200:
            do { return try JSONDecoder().decode(SuggestResponse.self, from: data) }
            catch { throw SuggestionError(message: "Got an unexpected reply from the server.") }
        case 401: throw SuggestionError(message: "App token rejected. Check the Wingmate app.")
        case 429: throw SuggestionError(message: "Slow down a little and try again.")
        default: throw SuggestionError(message: "Couldn't get suggestions (\(status)).")
        }
    }
}
