package com.flydrop2p.flydrop2p.notification

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast

class SosCopyReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val lat = intent.getDoubleExtra("lat", Double.NaN)
        val lon = intent.getDoubleExtra("lon", Double.NaN)
        if (!lat.isNaN() && !lon.isNaN()) {
            val text = "$lat,$lon"
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("SOS Coordinates", text))
            Toast.makeText(context, "Coordinates copied: $text", Toast.LENGTH_SHORT).show()
        }
    }
}


