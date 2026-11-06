package com.wingmate.keyboard.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.inputmethod.InputMethodManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.wingmate.keyboard.data.DEFAULT_ALLOWED_APPS
import com.wingmate.keyboard.data.Settings
import com.wingmate.keyboard.data.Tone
import com.wingmate.keyboard.ime.DatingKeyboardService
import com.wingmate.keyboard.reader.ChatReaderService

class MainActivity : ComponentActivity() {
    /** Bumped in onResume so setup status re-checks after returning from system Settings. */
    private var resumeTick by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val settings = Settings(this)
        setContent {
            WingmateTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen(settings, resumeTick)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }
}

@Composable
fun WingmateTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF5C8A),
            background = Color(0xFF1E1B24),
            surface = Color(0xFF1E1B24),
        ),
        content = content,
    )
}

@Composable
private fun MainScreen(settings: Settings, resumeTick: Int) {
    var consent by remember { mutableStateOf(settings.consentGiven) }
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Wingmate", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Text(
            "A keyboard that reads the chat you're in and suggests natural replies.",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (!consent) {
            ConsentCard { settings.consentGiven = true; consent = true }
        } else {
            SetupSteps(resumeTick)
            PreferencesSection(settings)
        }
    }
}

@Composable
private fun ConsentCard(onAccept: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Before you start", style = MaterialTheme.typography.titleMedium)
            Text("• Wingmate uses Android's Accessibility service to read the messages visible on screen, only when you tap ✨ Suggest, and only in chat apps you allow.")
            Text("• Those messages (and your draft) are sent to our server and to Anthropic's Claude AI to write suggestions. They are not stored or used for anything else.")
            Text("• Wingmate never reads in the background, never reads passwords, and works without the reader too: just copy a message and tap ✨ Suggest.")
            Text("• Suggestions are just ideas. You choose what to send.")
            Button(onClick = onAccept, modifier = Modifier.fillMaxWidth()) { Text("I understand and agree") }
        }
    }
}

@Composable
private fun SetupSteps(resumeTick: Int) {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Reading resumeTick makes these re-evaluate each time the activity resumes.
    val keyboardEnabled = remember(resumeTick) { isKeyboardEnabled(context) }
    val keyboardSelected = remember(resumeTick) { isKeyboardSelected(context) }
    val readerEnabled = remember(resumeTick) { ChatReaderService.isRunning || isReaderEnabled(context) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Setup", style = MaterialTheme.typography.titleMedium)
            Step("1. Turn on Wingmate Keyboard", keyboardEnabled, "Open") {
                context.startActivity(Intent(AndroidSettings.ACTION_INPUT_METHOD_SETTINGS))
            }
            Step("2. Switch to it", keyboardSelected, "Choose") {
                context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            }
            Step("3. Turn on the chat reader (optional)", readerEnabled, "Open") {
                context.startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            Text(
                "In Accessibility settings, look under Downloaded apps → Wingmate chat reader.",
                style = MaterialTheme.typography.bodySmall,
            )
            HorizontalDivider()
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, TestChatActivity::class.java)) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Try it in a test chat") }
        }
    }
}

@Composable
private fun Step(title: String, done: Boolean, action: String, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(if (done) "✅" else "⬜", Modifier.padding(end = 8.dp))
        Text(title, Modifier.weight(1f))
        if (!done) TextButton(onClick = onClick) { Text(action) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesSection(settings: Settings) {
    var tone by remember { mutableStateOf(settings.tone) }
    var about by remember { mutableStateOf(settings.aboutMe) }
    var url by remember { mutableStateOf(settings.backendUrl) }
    var token by remember { mutableStateOf(settings.appToken) }
    var apps by remember { mutableStateOf(settings.allowedApps) }
    var customPkg by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Your style", style = MaterialTheme.typography.titleMedium)
            Text("Default tone", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tone.entries.forEach { t ->
                    FilterChip(
                        selected = t == tone,
                        onClick = { tone = t; settings.tone = t },
                        label = { Text(t.label) },
                    )
                }
            }
            OutlinedTextField(
                value = about,
                onValueChange = { about = it.take(500); settings.aboutMe = about },
                label = { Text("About me (optional)") },
                placeholder = { Text("e.g. 29, nurse, into bouldering and bad horror movies") },
                supportingText = { Text("Helps suggestions stay true to you. ${about.length}/500") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
            )
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Apps the reader may look at", style = MaterialTheme.typography.titleMedium)
            val known = DEFAULT_ALLOWED_APPS + (apps - DEFAULT_ALLOWED_APPS.keys).associateWith { it }
            known.forEach { (pkg, name) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = pkg in apps,
                        onCheckedChange = { on ->
                            apps = if (on) apps + pkg else apps - pkg
                            settings.allowedApps = apps
                        },
                    )
                    Text(name)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = customPkg,
                    onValueChange = { customPkg = it.trim() },
                    label = { Text("Add app package name") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    enabled = customPkg.matches(Regex("""[a-zA-Z][\w]*(\.[a-zA-Z][\w]*)+""")),
                    onClick = {
                        apps = apps + customPkg
                        settings.allowedApps = apps
                        customPkg = ""
                    },
                ) { Text("Add") }
            }
        }
    }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Server", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = url,
                onValueChange = { url = it; settings.backendUrl = it },
                label = { Text("Backend URL") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = token,
                onValueChange = { token = it; settings.appToken = it },
                label = { Text("App token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    Spacer(Modifier.height(24.dp))
}

private fun isKeyboardEnabled(context: Context): Boolean {
    val imm = context.getSystemService(InputMethodManager::class.java) ?: return false
    return imm.enabledInputMethodList.any { it.packageName == context.packageName }
}

private fun isKeyboardSelected(context: Context): Boolean {
    val current = AndroidSettings.Secure.getString(context.contentResolver, AndroidSettings.Secure.DEFAULT_INPUT_METHOD)
    val ours = ComponentName(context, DatingKeyboardService::class.java)
    return ComponentName.unflattenFromString(current ?: "") == ours
}

private fun isReaderEnabled(context: Context): Boolean {
    val enabled = AndroidSettings.Secure.getString(
        context.contentResolver,
        AndroidSettings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val ours = ComponentName(context, ChatReaderService::class.java)
    return enabled.split(':').any { ComponentName.unflattenFromString(it) == ours }
}
