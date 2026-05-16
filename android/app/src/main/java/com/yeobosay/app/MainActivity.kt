package com.yeobosay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.yeobosay.app.ui.call.CallRoute
import com.yeobosay.app.ui.theme.YeoboSayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            YeoboSayTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    CallRoute(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
