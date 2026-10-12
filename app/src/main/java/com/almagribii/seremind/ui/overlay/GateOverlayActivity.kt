package com.almagribii.seremind.ui.overlay

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.almagribii.seremind.MainActivity
import com.almagribii.seremind.ui.navigation.Screen
import com.almagribii.seremind.ui.theme.SeremindTheme

class GateOverlayActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val packageName = intent?.getStringExtra("package_name") ?: ""
        val appName = intent?.getStringExtra("app_name") ?: "Aplikasi"
        val delaySeconds = intent?.getIntExtra("delay_seconds", 5) ?: 5

        setContent {
            SeremindTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0f)
                ) {
                    GateOverlay(
                        packageName = packageName,
                        appName = appName,
                        delaySeconds = delaySeconds,
                        onCancel = {
                            val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                                addCategory(Intent.CATEGORY_HOME)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            startActivity(homeIntent)
                            finish()
                        },
                        onOpenAllowed = {
                            finish()
                        },
                        onWriteReflection = { prompt ->
                            val intent = Intent(this, MainActivity::class.java).apply {
                                putExtra("navigate_to", Screen.JournalEditor.createRoute(prompt))
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                            startActivity(intent)
                            finish()
                        }
                    )
                }
            }
        }
    }
}
