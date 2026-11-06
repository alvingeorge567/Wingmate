# Wingmate: a dating reply keyboard

A mobile keyboard that reads the dating chat you're in and suggests 3 natural, human-sounding replies. Tap one to drop it into the message box, pick a tone (Playful, Flirty, Sincere, Curious), or tap ↻ for more.

```
backend/   TypeScript proxy that calls Claude (keeps the API key off the phone)
android/   Kotlin keyboard + screen reader + companion app
ios/       Swift keyboard extension + host app
shared/    API contract and JSON schema both apps follow
```

## How it gets the chat

| | Android | iOS |
|---|---|---|
| Reads the screen automatically | ✅ Accessibility service, only on tap and only in apps you allow | ❌ iOS doesn't allow this for any app |
| Copy a message, tap 📋 | ✅ (fallback) | ✅ |
| Type what they said (✍️) | – | ✅ |
| Uses your current draft | ✅ | ✅ |

On Android the reader figures out who sent what from the bubble position: left = them, right = you. It drops timestamps and read receipts and keeps the last 20 messages.

## 1. Backend

Requires Node 20+.

```bash
cd backend
cp .env.example .env      # then set ANTHROPIC_API_KEY and a random APP_TOKEN
npm install
npm test
npm run dev               # http://localhost:8787
```

Try it:

```bash
curl -s localhost:8787/v1/suggest -H "content-type: application/json" -H "x-app-token: $APP_TOKEN" -d '{"messages":[{"from":"them","text":"ok is that you on top of a mountain??"},{"from":"me","text":"haha yes, nearly died on the way down"},{"from":"them","text":"you cant just say that and not explain"}]}'
```

- Model: `claude-haiku-5-5` by default, for speed. Set `SUGGEST_MODEL=claude-sonnet-5-5` for better replies, which cost more and take longer.
- The prompt is in [backend/src/prompt.ts](backend/src/prompt.ts). It matches the other person's texting style, never invents facts about you, avoids manipulation, and always backs off gracefully if they say no.
- Chat text is never logged. For production, run it behind HTTPS. The rate limiter is in-memory, so swap in Redis if you run more than one instance.

## 2. Android

Open `android/` in Android Studio (Ladybug or newer). It downloads Gradle and the SDK, then you run the `app` configuration.

1. Start the backend on your computer. The emulator reaches it at `http://10.0.2.2:8787`, which is the default.
2. In the Wingmate app:
   - accept the disclosure;
   - enter the **App token** under Server;
   - follow the 3 setup steps (turn on the keyboard, switch to it, and optionally turn on the chat reader).
3. Tap **Try it in a test chat**, tap the message box, then tap **✨ Suggest**.

On a real phone, set the Backend URL to your server's HTTPS address.

From the command line (JDK 17+ on `JAVA_HOME`, SDK path in `android/local.properties`):

```bash
cd android && ./gradlew assembleDebug testDebugUnitTest
```

The APK lands in `android/app/build/outputs/apk/debug/app-debug.apk`.

**Play Store:** an app using the Accessibility API this way needs the in-app disclosure (included) and an Accessibility declaration in Play Console. Google reviews these strictly, so expect questions.

## 3. iOS

This needs a Mac with Xcode 15+.

```bash
brew install xcodegen
cd ios && xcodegen
open Wingmate.xcodeproj
```

1. Set your signing Team on both targets.
2. Change the App Group (`group.com.wingmate.shared`) in `project.yml` and `Shared/SharedSettings.swift` to one you own.
3. Run on a device or simulator, then follow the in-app setup. Turning on **Allow Full Access** is required.
