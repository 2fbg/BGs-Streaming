package com.example

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.ui.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private var viewModelReference: AppViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Log all standard thread-level exceptions for clean diagnostics, then delegate to default handler
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("MK21_CRASH", "Uncaught exception on thread: ${thread.name}", throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }

        // Proper edgeToEdge execution to extend views elegantly under notches
        enableEdgeToEdge()
        
        setContent {
            val viewModel: AppViewModel = viewModel()
            viewModelReference = viewModel
            val useAmoledMode by viewModel.useAmoledMode.collectAsState()
            
            MyApplicationTheme(useAmoledMode = useAmoledMode) {
                AppNavigation(viewModel = viewModel)
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        viewModelReference?.setIsInPipMode(isInPictureInPictureMode)
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val viewModel = viewModelReference
        if (viewModel != null && viewModel.currentPlayingItem.value != null && !viewModel.isInPipMode.value) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                try {
                    val params = android.app.PictureInPictureParams.Builder().build()
                    enterPictureInPictureMode(params)
                } catch (e: Exception) {
                    Log.e("MainActivity", "Failed to auto-enter PiP mode", e)
                }
            }
        }
    }
}
