package com.cleanx.lcx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cleanx.lcx.core.navigation.LcxNavHost
import com.cleanx.lcx.core.theme.LcxTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LcxTheme {
                LcxNavHost()
            }
        }
    }
}
