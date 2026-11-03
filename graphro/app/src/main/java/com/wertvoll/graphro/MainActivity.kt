package com.wertvoll.graphro

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.wertvoll.graphro.ui.AppRoot
import com.wertvoll.graphro.ui.GraphroTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        val fromShare = savedInstanceState == null && handleIntent(intent)
        setContent {
            GraphroTheme {
                AppRoot(vm, playIntro = savedInstanceState == null && !fromShare)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        vm.checkClipboard(this)
    }

    override fun onPause() {
        super.onPause()
        vm.flushSave()
    }

    /** Returns true when the intent carried data to import. */
    private fun handleIntent(intent: Intent?): Boolean {
        intent ?: return false
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val stream: Uri? = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (stream != null) { vm.importUri(this, stream); return true }
                val text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
                if (!text.isNullOrBlank()) { vm.importText(text, "공유받은 내용"); return true }
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data
                if (uri != null) { vm.importUri(this, uri); return true }
            }
        }
        return false
    }
}
