package com.ayane.dynamicisland

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ayane.dynamicisland.data.SettingsRepository
import com.ayane.dynamicisland.theme.AyaneTheme
import com.ayane.dynamicisland.ui.main.MainScreen
import com.ayane.dynamicisland.ui.main.MainScreenViewModel
import android.content.Intent
class MainActivity : ComponentActivity() {

    private val viewModel by viewModels<MainScreenViewModel>(
        factoryProducer = {
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return MainScreenViewModel(SettingsRepository(applicationContext)) as T
                }
            }
        }
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep the launcher screen independent from overlay/foreground-service startup.
        // The user starts the island explicitly with the in-app switch after granting permissions.
        viewModel.checkPermissions(this)
        if (intent.getBooleanExtra("EXTRA_SHOW_UPDATE", false)) {
            viewModel.checkForUpdates(this)
        }

        setContent {
            AyaneTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }


    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("EXTRA_SHOW_UPDATE", false)) {
            viewModel.checkForUpdates(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // Keep the launcher screen independent from overlay/foreground-service startup.
        // The user starts the island explicitly with the in-app switch after granting permissions.
        viewModel.checkPermissions(this)
        viewModel.refreshUpdateStateOnResume(this)
    }
}
