package com.ridingverse.app.ui.screens

import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.ridingverse.app.ui.theme.RidingVerseAccent
import com.ridingverse.app.ui.theme.RidingVerseBackground
import com.ridingverse.app.ui.theme.RidingVerseCard
import com.ridingverse.app.ui.theme.RidingVerseMuted
import com.ridingverse.app.ui.theme.RidingVerseText

@Composable
fun MapScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RidingVerseBackground)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Route Map", color = RidingVerseText, fontSize = 22.sp)
            Button(
                onClick = { },
                colors = ButtonDefaults.buttonColors(containerColor = RidingVerseAccent, contentColor = Color.Black)
            ) {
                Text("Start Ride")
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = RidingVerseCard),
            shape = RoundedCornerShape(20.dp)
        ) {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = true
                        settings.allowContentAccess = true
                        settings.cacheMode = android.webkit.WebSettings.LOAD_DEFAULT
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("file:///android_asset/map.html")
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            RouteChip("Fastest", selected = true)
            RouteChip("Twisties", selected = false)
            RouteChip("Scenic", selected = false)
        }
    }
}

@Composable
private fun RouteChip(label: String, selected: Boolean) {
    Button(
        onClick = { },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) RidingVerseAccent else RidingVerseCard,
            contentColor = if (selected) Color.Black else RidingVerseText
        ),
        shape = RoundedCornerShape(18.dp)
    ) {
        Text(label)
    }
}
