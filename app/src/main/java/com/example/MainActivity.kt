package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.SpendWiseApp
import com.example.ui.theme.SpendWiseTheme
import com.example.viewmodel.SpendWiseViewModel
import com.example.widget.SpendWiseBalanceWidgetProvider

class MainActivity : ComponentActivity() {

    private var openLogSignal by mutableIntStateOf(0)
    private var voiceTriggerSignal by mutableIntStateOf(0)
    private var externalExpensePrompt by mutableStateOf<Pair<Int, String>?>(null)
    private var externalPromptCounter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        handleIncomingIntent(intent)
        setContent {
            SpendWiseTheme {
                val spendWiseViewModel: SpendWiseViewModel = viewModel()
                SpendWiseApp(
                    viewModel = spendWiseViewModel,
                    openLogSignal = openLogSignal,
                    voiceTriggerSignal = voiceTriggerSignal,
                    externalExpensePrompt = externalExpensePrompt
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getBooleanExtra(SpendWiseBalanceWidgetProvider.EXTRA_OPEN_LOG_EXPENSE, false)) {
            openLogSignal++
            intent.removeExtra(SpendWiseBalanceWidgetProvider.EXTRA_OPEN_LOG_EXPENSE)
            return
        }

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            if (sharedText.isNotBlank()) {
                externalPromptCounter++
                externalExpensePrompt = externalPromptCounter to sharedText
            }
            intent.removeExtra(Intent.EXTRA_TEXT)
            return
        }

        val data = intent.data
        if (intent.action == Intent.ACTION_VIEW && data?.scheme == "spendwise") {
            when (data.host) {
                "voice" -> {
                    voiceTriggerSignal++
                }
                "log" -> {
                    val textParam = (data.getQueryParameter("text")
                        ?: data.getQueryParameter("prompt")
                        ?: data.getQueryParameter("q")
                        ?: data.getQueryParameter("expense"))
                        ?.trim()
                        .orEmpty()
                    if (textParam.isNotBlank()) {
                        externalPromptCounter++
                        externalExpensePrompt = externalPromptCounter to textParam
                    } else {
                        openLogSignal++
                    }
                }
            }
            intent.data = null
        }
    }
}
