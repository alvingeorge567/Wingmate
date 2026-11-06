import SwiftUI

private enum Palette {
    static let background = Color(red: 0.12, green: 0.11, blue: 0.14)
    static let key = Color(red: 0.23, green: 0.21, blue: 0.26)
    static let special = Color(red: 0.17, green: 0.16, blue: 0.20)
    static let card = Color(red: 0.18, green: 0.16, blue: 0.22)
    static let accent = Color(red: 1.0, green: 0.36, blue: 0.54)
    static let muted = Color(red: 0.72, green: 0.69, blue: 0.77)
}

struct KeyboardRootView: View {
    @ObservedObject var model: KeyboardModel

    var body: some View {
        VStack(spacing: 6) {
            toolbar
            if model.target == .theirMessage {
                theirMessageBar
            }
            if let status = model.status {
                HStack(spacing: 8) {
                    if model.isLoading { ProgressView().scaleEffect(0.7) }
                    Text(status).font(.caption).foregroundColor(Palette.muted).lineLimit(2)
                    Spacer(minLength: 0)
                }
                .padding(.horizontal, 8)
            }
            if !model.suggestions.isEmpty {
                suggestionCards
            }
            KeysView(model: model)
        }
        .padding(.horizontal, 4)
        .padding(.vertical, 6)
        .background(Palette.background)
    }

    private var toolbar: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                Chip(title: "✨ Suggest", accent: true) { model.suggest() }
                if model.clipboardHasText {
                    Chip(title: "📋 Use copied") { model.suggest(usingClipboard: true) }
                }
                Chip(title: model.target == .theirMessage ? "✅ Done" : "✍️ Their msg") {
                    model.toggleTheirMessageMode()
                }
                ForEach(Tone.allCases) { tone in
                    Chip(title: tone.label, selected: model.tone == tone) { model.select(tone: tone) }
                }
                Chip(title: "🧹 New chat") { model.resetConversation() }
            }
            .padding(.horizontal, 4)
        }
    }

    private var theirMessageBar: some View {
        HStack {
            Text(model.theirMessage.isEmpty ? "Type what they said…" : model.theirMessage)
                .font(.subheadline)
                .foregroundColor(model.theirMessage.isEmpty ? Palette.muted : .white)
                .lineLimit(2)
            Spacer(minLength: 0)
        }
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 10).stroke(Palette.accent))
        .padding(.horizontal, 4)
    }

    private var suggestionCards: some View {
        VStack(alignment: .leading, spacing: 6) {
            ForEach(model.suggestions) { s in
                Button { model.pick(s) } label: {
                    VStack(alignment: .leading, spacing: 2) {
                        Text((Tone(rawValue: s.tone) ?? .any).label).font(.caption2).foregroundColor(Palette.muted)
                        Text(s.text).font(.subheadline).foregroundColor(.white).multilineTextAlignment(.leading)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 12).fill(Palette.card))
                }
                .buttonStyle(.plain)
            }
            HStack {
                Spacer()
                Chip(title: "↻ More") { model.suggest() }
                Chip(title: "✕ Hide") { model.clearSuggestions() }
            }
        }
        .padding(.horizontal, 4)
    }
}

private struct Chip: View {
    let title: String
    var accent = false
    var selected = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.footnote.weight(accent ? .bold : .regular))
                .foregroundColor(.white)
                .padding(.horizontal, 12)
                .padding(.vertical, 7)
                .background(
                    Capsule().fill(accent ? Palette.accent : (selected ? Palette.key : Palette.special))
                )
                .overlay(Capsule().stroke(selected ? Palette.accent : .clear, lineWidth: 1))
        }
        .buttonStyle(.plain)
    }
}

/// One key: label, relative width, look, and what it does.
private struct Key: Identifiable {
    let id: String
    let label: String
    var weight: CGFloat = 1
    var special = false
    let action: () -> Void
}

private struct KeysView: View {
    @ObservedObject var model: KeyboardModel

    private func letter(_ k: String) -> Key {
        Key(id: k, label: model.shifted && !model.symbols ? k.uppercased() : k) { model.type(k) }
    }

    private var rows: [[Key]] {
        let delete = Key(id: "del", label: "⌫", weight: 1.4, special: true) { model.delete() }
        var result: [[Key]]
        if model.symbols {
            result = [
                "1234567890".map { letter(String($0)) },
                ["@", "#", "$", "_", "&", "-", "+", "(", ")", "/"].map(letter),
                ["*", "\"", "'", ":", ";", "!", "?"].map(letter) + [delete],
            ]
        } else {
            let shift = Key(id: "shift", label: "⇧", weight: 1.4, special: !model.shifted) { model.shifted.toggle() }
            result = [
                "qwertyuiop".map { letter(String($0)) },
                "asdfghjkl".map { letter(String($0)) },
                [shift] + "zxcvbnm".map { letter(String($0)) } + [delete],
            ]
        }
        var bottom = [Key(id: "mode", label: model.symbols ? "ABC" : "123", weight: 1.5, special: true) { model.symbols.toggle() }]
        if model.needsGlobeKey {
            bottom.append(Key(id: "globe", label: "🌐", special: true) { model.switchKeyboard() })
        }
        bottom += [
            Key(id: "comma", label: ",") { model.type(",") },
            Key(id: "space", label: "space", weight: 4) { model.space() },
            Key(id: "dot", label: ".") { model.type(".") },
            Key(id: "enter", label: model.target == .theirMessage ? "✨" : "⏎", weight: 1.5, special: true) { model.enter() },
        ]
        result.append(bottom)
        return result
    }

    var body: some View {
        VStack(spacing: 8) {
            ForEach(Array(rows.enumerated()), id: \.offset) { index, row in
                KeyRow(keys: row, inset: !model.symbols && index == 1 ? 16 : 0)
            }
        }
    }
}

/// Lays out keys by weight across the available width.
private struct KeyRow: View {
    let keys: [Key]
    let inset: CGFloat
    private let spacing: CGFloat = 5

    var body: some View {
        GeometryReader { geo in
            let total = keys.reduce(0) { $0 + $1.weight }
            let available = geo.size.width - inset * 2 - spacing * CGFloat(max(keys.count - 1, 0))
            let unit = max(available / max(total, 1), 0)
            HStack(spacing: spacing) {
                ForEach(keys) { key in
                    Button(action: key.action) {
                        Text(key.label)
                            .font(key.label.count > 1 ? .footnote : .title3)
                            .foregroundColor(.white)
                            .frame(width: unit * key.weight, height: 42)
                            .background(RoundedRectangle(cornerRadius: 6).fill(key.special ? Palette.special : Palette.key))
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, inset)
        }
        .frame(height: 42)
    }
}
