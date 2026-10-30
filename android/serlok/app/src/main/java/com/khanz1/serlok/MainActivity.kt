package com.khanz1.serlok

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import com.khanz1.serlok.data.AppSettings
import com.khanz1.serlok.service.RoomEventBus
import com.khanz1.serlok.ui.screens.room.RoomAlertNotificationHelper

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppSettings.init(this)
        enableEdgeToEdge()
        forwardExtras(intent)
        setContent {
            SerlokApp()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        forwardExtras(intent)
    }

    private fun forwardExtras(intent: Intent?) {
        intent?.getStringExtra(RoomAlertNotificationHelper.EXTRA_FOCUS_USER_ID)?.let { userId ->
            RoomEventBus.emitFocus(userId)
        }
    }
}
