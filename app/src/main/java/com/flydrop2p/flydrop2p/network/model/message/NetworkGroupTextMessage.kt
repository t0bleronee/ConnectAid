package com.flydrop2p.flydrop2p.network.model.message

import kotlinx.serialization.Serializable

@Serializable
data class NetworkGroupTextMessage(
    val group: String,
    val messageId: Long,
    val senderId: Long,
    val text: String,
    val timestamp: Long
)


