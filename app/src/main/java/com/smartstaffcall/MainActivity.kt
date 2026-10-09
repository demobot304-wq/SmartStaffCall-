package com.smartstaffcall

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    private var openCallId by mutableStateOf<String?>(null)

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* result not needed */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        openCallId = intent?.getStringExtra("callId")
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                val vm: AppViewModel = viewModel()
                AppRoot(vm, openCallId) { openCallId = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openCallId = intent.getStringExtra("callId")
    }
}
