package com.ridingverse.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ridingverse.app.ui.theme.RidingVerseTheme
import com.ridingverse.app.ui.RidingVerseApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RidingVerseTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RidingVerseApp()
                }
            }
        }
    }
}
