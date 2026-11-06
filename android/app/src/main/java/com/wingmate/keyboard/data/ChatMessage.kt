package com.wingmate.keyboard.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Sender {
    @SerialName("them") THEM,
    @SerialName("me") ME,
}

@Serializable
data class ChatMessage(
    @SerialName("from") val from: Sender,
    val text: String,
)
