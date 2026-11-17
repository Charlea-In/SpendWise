package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ai.configureAppCheck
import com.example.ui.SpendWiseApp
import com.example.ui.theme.SpendWiseTheme
import com.example.viewmodel.SpendWiseViewModel
import com.example.widget.SpendWiseBalanceWidgetProvider

class MainActivity : ComponentActivity() {

    private var openLogSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        configureAppCheck(this, intent)
        enableEdgeToEdge()
        handleWidgetIntent(intent)
        setContent {
            SpendWiseTheme {
                val spendWiseViewModel: SpendWiseViewModel = viewModel()
                SpendWiseApp(
                    viewModel = spendWiseViewModel,
                    openLogSignal = openLogSignal
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleWidgetIntent(intent)
    }

    private fun handleWidgetIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(SpendWiseBalanceWidgetProvider.EXTRA_OPEN_LOG_EXPENSE, false) == true) {
            openLogSignal++
            intent.removeExtra(SpendWiseBalanceWidgetProvider.EXTRA_OPEN_LOG_EXPENSE)
        }
    }
}
