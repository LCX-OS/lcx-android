package com.cleanx.lcx

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.cleanx.lcx.core.navigation.LcxNavHost
import com.cleanx.lcx.core.network.SessionExpiredInterceptor
import com.cleanx.lcx.core.session.SessionManager
import com.cleanx.lcx.core.theme.LcxTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var sessionExpiredInterceptor: SessionExpiredInterceptor
    @Inject lateinit var sessionManager: SessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LcxTheme {
                LcxNavHost(
                    sessionExpiredInterceptor = sessionExpiredInterceptor,
                    sessionManager = sessionManager,
                )
            }
        }
    }
}
