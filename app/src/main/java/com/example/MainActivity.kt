package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.AppNavigation
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Enforce horizontal landscape orientation layout
        requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        
        // Proper edgeToEdge execution to extend views elegantly under notches
        enableEdgeToEdge()
        
        setContent {
            MyApplicationTheme {
                val viewModel: AppViewModel = viewModel()
                AppNavigation(viewModel = viewModel)
            }
        }
    }
}
