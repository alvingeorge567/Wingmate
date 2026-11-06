package com.wingmate.keyboard.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.wingmate.keyboard.data.ChatMessage
import com.wingmate.keyboard.data.Sender

/**
 * A fake dating chat to try the keyboard and screen reader without a real dating app.
 * Their bubbles are on the left, yours on the right, like a real chat app.
 */
class TestChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            WingmateTheme {
                Surface(Modifier.fillMaxSize()) { TestChat() }
            }
        }
    }
}

private val SAMPLE = listOf(
    ChatMessage(Sender.THEM, "ok your third photo… is that you on top of a mountain?? 🏔️"),
    ChatMessage(Sender.ME, "haha yes, Mount Rainier last summer. nearly died on the way down"),
    ChatMessage(Sender.THEM, "nearly died?? you can't just say that and not explain"),
)

@Composable
private fun TestChat() {
    val messages = remember { mutableStateListOf(*SAMPLE.toTypedArray()) }
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) { listState.animateScrollToItem(messages.lastIndex.coerceAtLeast(0)) }

    fun send(sender: Sender) {
        if (input.isBlank()) return
        messages += ChatMessage(sender, input.trim())
        input = ""
    }

    Column(Modifier.fillMaxSize().imePadding()) {
        Text(
            "Alex, 28",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(16.dp),
        )
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(messages) { Bubble(it) }
        }
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message") },
                modifier = Modifier.weight(1f),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { send(Sender.ME) }),
            )
            Column {
                TextButton(onClick = { send(Sender.ME) }) { Text("Send") }
                // Lets you play the other person to test different situations.
                TextButton(onClick = { send(Sender.THEM) }) { Text("As Alex") }
            }
        }
    }
}

@Composable
private fun Bubble(message: ChatMessage) {
    val mine = message.from == Sender.ME
    Box(Modifier.fillMaxWidth(), contentAlignment = if (mine) Alignment.CenterEnd else Alignment.CenterStart) {
        Text(
            message.text,
            color = Color.White,
            modifier = Modifier
                .widthIn(max = 280.dp)
                .background(
                    if (mine) Color(0xFFFF5C8A) else Color(0xFF3A3542),
                    RoundedCornerShape(16.dp),
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
