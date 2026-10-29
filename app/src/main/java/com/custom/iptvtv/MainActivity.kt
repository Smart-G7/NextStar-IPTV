package com.custom.iptvtv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.custom.iptvtv.ui.IptvTheme
import com.custom.iptvtv.ui.Root

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = appState
        setContent { IptvTheme { Root(state) } }
    }
}
