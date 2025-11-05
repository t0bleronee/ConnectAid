package com.flydrop2p.flydrop2p.network.model.sos

import kotlinx.serialization.Serializable

@Serializable
data class NetworkSosAlert(
    val senderId: Long,
    val durationMs: Long,
    val timestamp: Long,
    val latitude: Double? = null,
    val longitude: Double? = null
)


